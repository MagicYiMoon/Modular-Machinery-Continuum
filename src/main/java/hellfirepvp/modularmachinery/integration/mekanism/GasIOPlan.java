/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;

/** Reserves gas once per hatch, including selectors and the shared fluid/gas output space. */
public final class GasIOPlan implements BatchIORequirement.Plan {
    private record Transfer(GasTankAccess hatch, RequirementGas requirement, long amount) {}
    private final List<Transfer> transfers = new ArrayList<>();
    private final IOType direction;
    private String failure;
    private GasIOPlan(IOType direction) { this.direction = direction; }
    @Override public boolean valid() { return failure == null; }
    public String failure() { return failure; }

    public static GasIOPlan inputs(MachineContext context, List<Requirement> requirements, int copies) {
        return plan(context, requirements, copies, IOType.INPUT);
    }
    public static GasIOPlan outputs(MachineContext context, List<Requirement> requirements, int copies) {
        return plan(context, requirements, copies, IOType.OUTPUT);
    }
    private static GasIOPlan plan(MachineContext context, List<Requirement> requirements, int copies, IOType direction) {
        var plan = new GasIOPlan(direction);
        var reservedGas = new IdentityHashMap<GasTankAccess, GasStack>();
        var reservedFluid = new IdentityHashMap<IFluidHandler, Map<Integer, FluidStack>>();
        // Fluid outputs are emitted before gas outputs. Reserve their cells through the same fluid allocator.
        if (direction == IOType.OUTPUT) {
            for (Requirement r : requirements) {
                if (!(r instanceof RequirementFluid fluid) || fluid.getIOType() != IOType.OUTPUT) continue;
                long demand = (long) fluid.getAmount() * (fluid.isParallelizeUnaffected() ? 1 : Math.max(1, copies));
                if (demand > Integer.MAX_VALUE || RequirementFluid.outputRoomFor(context.forSelector(fluid.getSelectorTag()),
                        fluid.asStack((int) demand), (int) demand, reservedFluid) < demand) {
                    if (fluid.isGuaranteed()) { plan.failure = "craftcheck.failure.fluid.output.space"; return plan; }
                }
            }
        }
        for (Requirement r : requirements) {
            if (!(r instanceof RequirementGas gas) || gas.getIOType() != direction) continue;
            if (!gas.isResolvable()) { plan.failure = gas.describeFailure(context); return plan; }
            // Optional outputs are rolled at emission time. Lack of room must not hold a craft for a
            // result that may never be produced, and failed allocation must leave no stale reservations.
            if (direction == IOType.OUTPUT && !gas.isGuaranteed() && gas.getChance() <= 0F) continue;
            int transferStart = plan.transfers.size();
            var reservedBefore = new IdentityHashMap<GasTankAccess, GasStack>(reservedGas);
            long remaining = gas.demand(copies);
            for (GasTankAccess hatch : tanks(context.forSelector(gas.getSelectorTag()), direction)) {
                if (hatch instanceof TileFluidTank hybrid) {
                    if (!hybrid.getTank().isEmpty()) continue;
                    if (direction == IOType.OUTPUT && !reservedFluid.getOrDefault(
                            context.internalFluidView(hybrid.getBlockPos(), hybrid), Map.of()).isEmpty()) continue;
                }
                GasStack stored = MekanismGasBridge.read(hatch);
                GasStack prior = reservedGas.getOrDefault(hatch, GasStack.EMPTY);
                if (!prior.isEmpty() && !gas.matches(prior)) continue;
                long room;
                if (direction == IOType.INPUT) {
                    if (!gas.matches(stored)) continue;
                    room = stored.getAmount() - prior.getAmount();
                } else {
                    if (!stored.isEmpty() && !gas.matches(stored)) continue;
                    room = hatch.getGasCapacity() - stored.getAmount() - prior.getAmount();
                }
                long take = Math.min(remaining, Math.max(0L, room));
                if (take > 0) {
                    plan.transfers.add(new Transfer(hatch, gas, take));
                    reservedGas.put(hatch, gas.asStack(prior.getAmount() + take));
                    remaining -= take;
                }
                if (remaining <= 0) break;
            }
            if (remaining > 0) {
                if (direction == IOType.OUTPUT && !gas.isGuaranteed()) {
                    plan.transfers.subList(transferStart, plan.transfers.size()).clear();
                    reservedGas.clear();
                    reservedGas.putAll(reservedBefore);
                    continue;
                }
                plan.failure = gas.describeFailure(context); return plan;
            }
        }
        return plan;
    }
    private static List<GasTankAccess> tanks(MachineContext context, IOType direction) {
        var tanks = new ArrayList<GasTankAccess>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<GasTankAccess, Boolean>());
        for (var pos : context.getComponentPositions()) {
            var tile = context.getLevel().getBlockEntity(pos);
            if (!(tile instanceof MachineComponentTile component) || component.getIOType() != direction) continue;
            if (tile instanceof GasTankProvider provider) {
                for (GasTankAccess tank : provider.gasTanks()) if (seen.add(tank)) tanks.add(tank);
            } else if (tile instanceof GasTankAccess tank && seen.add(tank)) tanks.add(tank);
        }
        return tanks;
    }
    @Override public boolean execute() {
        if (!valid()) return false;
        var rolls = new IdentityHashMap<RequirementGas, Boolean>();
        var totals = new IdentityHashMap<GasTankAccess, GasStack>();
        for (Transfer t : transfers) {
            if (direction == IOType.INPUT && !rolls.computeIfAbsent(t.requirement(), r -> r.roll())) continue;
            GasStack old = totals.getOrDefault(t.hatch(), GasStack.EMPTY);
            totals.put(t.hatch(), t.requirement().asStack(old.getAmount() + t.amount()));
        }
        for (var entry : totals.entrySet()) {
            var handler = MekanismGasBridge.handler(entry.getKey(), true);
            GasStack expected = entry.getValue();
            if (direction == IOType.INPUT) {
                GasStack stored = handler.getChemicalInTank(0);
                if (!stored.isTypeEqual(expected) || stored.getAmount() < expected.getAmount()) return false;
            } else if (!handler.insertChemical(0, expected, Action.SIMULATE).isEmpty()) return false;
        }
        for (var entry : totals.entrySet()) {
            var handler = MekanismGasBridge.handler(entry.getKey(), true);
            if (direction == IOType.INPUT) handler.extractChemical(0, entry.getValue().getAmount(), Action.EXECUTE);
            else handler.insertChemical(0, entry.getValue(), Action.EXECUTE);
        }
        return true;
    }
}
