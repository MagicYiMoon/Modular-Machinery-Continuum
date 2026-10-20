/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.helper;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Checks each possible random product in writer order, without rolling during retry ticks. */
public final class ItemOutputPlan {
    private ItemOutputPlan() {}
    private static final int MAX_BRANCHES = 4096;
    private record SlotKey(IItemHandler handler, int slot) {
        @Override public boolean equals(Object other) { return other instanceof SlotKey key && handler == key.handler && slot == key.slot; }
        @Override public int hashCode() { return 31 * System.identityHashCode(handler) + slot; }
    }
    private record StackKey(net.minecraft.world.item.Item item, int count, net.minecraft.nbt.CompoundTag tag) {}
    private static Map<SlotKey, StackKey> key(IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> state) {
        var result = new HashMap<SlotKey, StackKey>();
        state.forEach((handler, slots) -> slots.forEach((slot, stack) ->
                result.put(new SlotKey(handler, slot), new StackKey(stack.getItem(), stack.getCount(), stack.getTag()))));
        return result;
    }
    private static IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> copy(
            IdentityHashMap<IItemHandler, Map<Integer, ItemStack>> source) {
        var result = new IdentityHashMap<IItemHandler, Map<Integer, ItemStack>>();
        source.forEach((handler, slots) -> result.put(handler, new HashMap<>(slots)));
        return result;
    }
    public static String failure(MachineContext context, List<Requirement> outputs, int parallelism) {
        var states = new ArrayList<IdentityHashMap<IItemHandler, Map<Integer, ItemStack>>>();
        states.add(new IdentityHashMap<>());
        for (Requirement r : outputs) {
            if (r.getIOType() != IOType.OUTPUT) continue;
            if (r.isIgnoreOutputCheck()) continue;
            var next = new LinkedHashMap<Map<SlotKey, StackKey>, IdentityHashMap<IItemHandler, Map<Integer, ItemStack>>>();
            if (r instanceof RequirementItem item && item.isResolvable()) {
                long demand = item.outputAmount(context) * (item.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
                for (var state : states) {
                    if (!item.isGuaranteed()) next.put(key(state), state);
                    var branch = copy(state);
                    if (demand > 0 && item.reserveOutput(context, demand, branch) < demand && item.isGuaranteed())
                        return "craftcheck.failure.item.output.space";
                    next.put(key(branch), branch);
                    if (next.size() > MAX_BRANCHES) return "craftcheck.failure.item.output.combinations";
                }
            } else if (r instanceof RequirementIngredientArray array && array.getChance() > 0) {
                var alternatives = array.getCandidates().stream().filter(c -> c.weight() > 0).toList();
                for (var state : states) {
                    if (array.getChance() < 1) next.put(key(state), state);
                    for (var c : alternatives) {
                        var branch = copy(state);
                        int candidateIndex = array.getCandidates().indexOf(c);
                        long demand = array.outputAmount(context, candidateIndex, c.maxAmount())
                                * (array.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
                        if (demand <= 0 || array.reserveCandidateOutput(context, candidateIndex, demand, branch) >= demand)
                            next.put(key(branch), branch);
                        if (next.size() > MAX_BRANCHES) return "craftcheck.failure.item.output.combinations";
                    }
                }
                if (next.isEmpty() && array.getChance() >= 1) return "craftcheck.failure.item.output.space";
            }
            if (!next.isEmpty()) states = new ArrayList<>(next.values());
        }
        return null;
    }
}
