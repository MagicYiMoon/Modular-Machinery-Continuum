/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** Verifies the source-style per-tick lifecycle is called around one server tick. */
public final class PerTickLifecycleRegressionChecks {
    private PerTickLifecycleRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        Probe probe = new Probe();
        MachineRecipe recipe = new MachineRecipe("per_tick_lifecycle", "probe", 4, List.of(probe),
                null, false, -1, "");
        MachinePattern pattern = new MachinePattern();
        DynamicMachine machine = new DynamicMachine("per_tick_lifecycle", "Per tick lifecycle", pattern);
        MachineContext context = new MachineContext(level, origin, net.minecraft.core.Direction.NORTH,
                machine, new MachineVariables(), pattern);
        String failure = MachineTicker.processPerTick(recipe, recipe.requirements(), context, 1, false);
        if (failure != null || probe.starts != 1 || probe.calls != 1 || probe.resets != 2) {
            throw new IllegalStateException("per-tick lifecycle was not called in source order: start="
                    + probe.starts + ", call=" + probe.calls + ", reset=" + probe.resets + ", failure=" + failure);
        }
        probe.setTriggerTime(5);
        var delayedPerTick = MachineTicker.activeRequirementsAtTick(new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe),
                List.of(probe), 0);
        if (!delayedPerTick.contains(probe)) {
            throw new IllegalStateException("source per-tick requirement was incorrectly delayed by triggerTime");
        }
        var ordinary = new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem(
                hellfirepvp.modularmachinery.common.machine.IOType.INPUT, "minecraft:coal", 1);
        ordinary.setTriggerTime(5);
        var ordinaryRun = new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(
                new MachineRecipe("per_tick_trigger", "ordinary", 4, List.of(ordinary), null, false, -1, ""));
        if (MachineTicker.activeRequirementsAtTick(ordinaryRun, List.of(ordinary), 0).contains(ordinary)
                || !MachineTicker.activeRequirementsAtTick(ordinaryRun, List.of(ordinary), 5).contains(ordinary)) {
            throw new IllegalStateException("ordinary triggerTime did not gate the requirement at the source tick");
        }
    }

    private static final class Probe implements Requirement.PerTick {
        int starts;
        int calls;
        int resets;
        int triggerTime;

        @Override public int getTriggerTime() { return triggerTime; }
        @Override public void setTriggerTime(int tick) { triggerTime = Math.max(0, tick); }

        @Override public IOType getIOType() { return IOType.INPUT; }
        @Override public boolean canStart(MachineContext context) { return true; }
        @Override public String typeId() { return "modularmachinery:regression_per_tick"; }
        @Override public void startIOTick(MachineContext context, float durationMultiplier) { starts++; }
        @Override public String resetIOTick(MachineContext context) { resets++; return null; }
        @Override public String doIOTick(MachineContext context, int parallelism) { calls++; return null; }
    }
}
