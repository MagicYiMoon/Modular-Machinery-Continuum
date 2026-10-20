/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.helper;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import java.util.*;

/** Plans the whole fluid tick before touching storage, including overlapping NBT input filters. */
public final class FluidTickIO {
    private FluidTickIO() {}
    public record Plan(String failure, FluidInputPlan inputs,
                       Map<TileFluidTank, FluidStack> fills) {
        public boolean execute() {
            if (failure != null) return false;
            if (!inputs.execute()) return false;
            for (var entry : fills.entrySet()) {
                if (entry.getKey().produceInternal(entry.getValue(), FluidAction.EXECUTE)
                        != entry.getValue().getAmount()) return false;
            }
            return true;
        }
    }

    public static Plan plan(MachineContext context, List<Requirement> requirements, int parallelism) {
        context = context.forSelector(null);
        var states = new LinkedHashMap<TileFluidTank, FluidStack>();
        var fills = new LinkedHashMap<TileFluidTank, FluidStack>();
        var inputRequirements = new ArrayList<Requirement>();
        for (Requirement requirement : requirements) {
            if (requirement instanceof RequirementFluidPerTick rate && rate.getIOType() == IOType.INPUT) {
                // Copy so the rate's selector/exemption can be applied without modifying its shared definition.
                var fluid = new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid(
                        IOType.INPUT, rate.fluid().getRawId(), rate.fluid().getAmount()).withNbt(rate.fluid().getNbt());
                fluid.setSelectorTag(rate.getSelectorTag());
                fluid.setParallelizeUnaffected(rate.isParallelizeUnaffected());
                inputRequirements.add(fluid);
            }
        }
        FluidInputPlan inputs = FluidInputPlan.plan(context, inputRequirements, parallelism);
        if (!inputs.valid()) return new Plan("craftcheck.failure.fluid.input", inputs, Map.of());
        for (var tank : context.internalFluidTanks()) states.put(tank, tank.getTank().getFluid().copy());
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementFluidPerTick rate)) continue;
            if (rate.getIOType() == IOType.INPUT) continue;
            var selectedTanks = context.forSelector(rate.getSelectorTag()).internalFluidTanks();
            var fluid = rate.fluid();
            String failure = rate.getIOType() == IOType.INPUT
                    ? "craftcheck.failure.fluid.input" : "craftcheck.failure.fluid.output.space";
            long demand = (long) fluid.getAmount() * (rate.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            if (!fluid.isResolvable() || demand > Integer.MAX_VALUE) return new Plan(failure, inputs, Map.of());
            int remaining = (int) demand;
            for (var entry : states.entrySet()) {
                if (remaining <= 0) break;
                TileFluidTank tank = entry.getKey();
                if (!selectedTanks.contains(tank)) continue;
                if (tank.getIOType() != rate.getIOType()) continue;
                FluidStack stored = entry.getValue();
                {
                    FluidStack product = fluid.asStack(remaining);
                    if (!stored.isEmpty() && !stored.isFluidEqual(product)) continue;
                    int room = tank.produceInternal(fluid.asStack(Integer.MAX_VALUE), FluidAction.SIMULATE)
                            - fills.getOrDefault(tank, FluidStack.EMPTY).getAmount();
                    int take = Math.min(remaining, Math.max(0, room));
                    if (take <= 0) continue;
                    add(fills, tank, product, take);
                    entry.setValue(new FluidStack(product, stored.getAmount() + take));
                    remaining -= take;
                }
            }
            if (remaining > 0) return new Plan(failure, inputs, Map.of());
        }
        return new Plan(null, inputs, fills);
    }

    private static void add(Map<TileFluidTank, FluidStack> map, TileFluidTank tank, FluidStack type, int amount) {
        map.put(tank, new FluidStack(type, amount + map.getOrDefault(tank, FluidStack.EMPTY).getAmount()));
    }
}
