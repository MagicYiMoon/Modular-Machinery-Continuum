/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.helper;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import java.util.*;

/** Shared reservations for ordinary fluid hatches and ME caches, before any input is removed. */
public final class FluidInputPlan {
    private record Draw(IFluidHandler handler, FluidStack stack) {}
    private final List<Draw> draws = new ArrayList<>();
    private boolean valid = true;
    private FluidInputPlan() {}
    public boolean valid() { return valid; }

    public static boolean required(List<Requirement> requirements) {
        return requirements.stream().anyMatch(r -> r instanceof RequirementFluid fluid
                && fluid.getIOType() == IOType.INPUT);
    }

    public static FluidInputPlan plan(MachineContext context, List<Requirement> requirements, int parallelism) {
        FluidInputPlan plan = new FluidInputPlan();
        var reservedByHandler = new IdentityHashMap<IFluidHandler, Map<String, Integer>>();
        for (Requirement r : requirements) {
            if (!(r instanceof RequirementFluid fluid) || fluid.getIOType() != IOType.INPUT) continue;
            long remaining = (long) fluid.getAmount() * (fluid.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            if (!fluid.isResolvable() || remaining > Integer.MAX_VALUE) { plan.valid = false; return plan; }
            for (IFluidHandler handler : context.forSelector(fluid.getSelectorTag()).internalFluidInputs()) {
                var reservedForHandler = reservedByHandler.computeIfAbsent(handler, ignored -> new HashMap<>());
                for (int tank = 0; tank < handler.getTanks() && remaining > 0; tank++) {
                    FluidStack stored = handler.getFluidInTank(tank);
                    if (stored.isEmpty() || !fluid.matches(stored)) continue;
                    String key = stored.getFluid().toString() + "|" + String.valueOf(stored.getTag());
                    int reserved = reservedForHandler.getOrDefault(key, 0);
                    FluidStack availableStack = handler.drain(new FluidStack(stored, Integer.MAX_VALUE), FluidAction.SIMULATE);
                    int take = (int) Math.min(remaining, Math.max(0, availableStack.getAmount() - reserved));
                    if (take > 0) {
                        Draw prior = plan.draws.stream().filter(d -> d.handler() == handler && d.stack().isFluidEqual(stored))
                                .findFirst().orElse(null);
                        if (prior == null) plan.draws.add(new Draw(handler, new FluidStack(stored, take)));
                        else prior.stack().grow(take);
                        reservedForHandler.put(key, reserved + take);
                        remaining -= take;
                    }
                }
                if (remaining <= 0) break;
            }
            if (remaining > 0) { plan.valid = false; return plan; }
        }
        return plan;
    }

    public boolean execute() {
        if (!valid) return false;
        for (Draw draw : draws)
            if (draw.handler().drain(draw.stack(), FluidAction.SIMULATE).getAmount() != draw.stack().getAmount()) return false;
        for (Draw draw : draws)
            if (draw.handler().drain(draw.stack(), FluidAction.EXECUTE).getAmount() != draw.stack().getAmount()) return false;
        return true;
    }
}
