/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.helper;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFuel;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.*;
import java.util.*;

/** Simulates item consumption and tool uses together, so overlapping requirements cannot spend one tool twice. */
public final class ItemInputPlan {
    private record Slot(IItemHandler handler, int index) {
        // Equal-valued wrappers can still own different inventories.
        @Override public boolean equals(Object other) {
            return other instanceof Slot slot && handler == slot.handler && index == slot.index;
        }
        @Override public int hashCode() { return 31 * System.identityHashCode(handler) + index; }
    }
    private final Map<Slot, ItemStack> before = new LinkedHashMap<>();
    private final Map<Slot, ItemStack> after = new LinkedHashMap<>();
    private final Set<Slot> rewritten = new HashSet<>();
    private boolean valid = true;

    private ItemInputPlan() {}
    public boolean valid() { return valid; }

    public static ItemInputPlan plan(MachineContext context, List<Requirement> requirements, int parallelism,
                                    boolean rollChance) {
        context = context.forSelector(null);
        var plan = new ItemInputPlan();
        for (var handler : context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT)) {
            for (int i = 0; i < handler.getSlots(); i++) {
                var slot = new Slot(handler, i);
                plan.before.put(slot, handler.getStackInSlot(i).copy());
                plan.after.put(slot, handler.getStackInSlot(i).copy());
            }
        }
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementItem item) || item.getIOType() != IOType.INPUT) continue;
            if (rollChance && item.getConsumeDurability() > 0 && !item.isGuaranteed()
                    && java.util.concurrent.ThreadLocalRandom.current().nextFloat() >= item.getChance()) continue;
            long remaining = item.inputAmount(context) * (item.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            if (!plan.take(item, remaining, handlers(context, item))) { plan.valid = false; return plan; }
        }
        // Reserve mandatory items first, then spend whole copies of the ordered alternatives.
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementIngredientArray array) || array.getIOType() != IOType.INPUT) continue;
            if (array instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst catalyst
                    && !catalyst.isActive()) continue;
            if (rollChance && java.util.concurrent.ThreadLocalRandom.current().nextFloat() >= array.getChance()) continue;
            long remaining = array.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism);
            var allowed = handlers(context, array);
            for (int candidateIndex = 0; candidateIndex < array.getCandidates().size(); candidateIndex++) {
                var candidate = array.getCandidates().get(candidateIndex);
                var item = candidate.item();
                int amount = rollChance ? candidate.rollAmount(Boolean.getBoolean("modularmachinery.selfTest")) : candidate.maxAmount();
                long modifiedAmount = array.inputAmount(context, candidateIndex, amount);
                if (remaining <= 0) break;
                if (amount == 0 || modifiedAmount <= 0) { remaining = 0; break; }
                long available = 0;
                for (var entry : plan.after.entrySet()) {
                    if (!allowed.contains(entry.getKey().handler())) continue;
                    if (!array.matchesCandidate(context, candidateIndex, entry.getValue())) continue;
                    var slot = entry.getKey();
                    available += Math.min(entry.getValue().getCount(), slot.handler()
                            .extractItem(slot.index(), entry.getValue().getCount(), true).getCount());
                }
                long copies = Math.min(remaining, available / modifiedAmount);
                if (!plan.take(item, copies * modifiedAmount, allowed, array, candidateIndex, context)) { plan.valid = false; return plan; }
                remaining -= copies;
            }
            if (remaining > 0) { plan.valid = false; return plan; }
        }
        // Broad fuel matches run after explicit ingredients and alternatives.
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementFuel fuel)) continue;
            if (rollChance && java.util.concurrent.ThreadLocalRandom.current().nextFloat() >= fuel.getChance()) continue;
            long time = (long) fuel.getTime() * (fuel.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            if (!plan.takeFuel(fuel, time, handlers(context, fuel))) { plan.valid = false; return plan; }
        }
        return plan;
    }

    public static boolean requiresPlan(List<Requirement> requirements) {
        return requirements.stream().anyMatch(r -> r instanceof RequirementFuel || r instanceof RequirementIngredientArray array && array.getIOType() == IOType.INPUT
                && (!(array instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst catalyst) || catalyst.isActive())
                || r instanceof RequirementItem item && item.getIOType() == IOType.INPUT
                && (item.getConsumeDurability() > 0 || item.getSelectorTag() != null || item.hasItemModifiers()));
    }

    private static Set<IItemHandler> handlers(MachineContext context, Requirement requirement) {
        Set<IItemHandler> allowed = Collections.newSetFromMap(new IdentityHashMap<>());
        allowed.addAll(context.forSelector(requirement.getSelectorTag())
                .capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT));
        return allowed;
    }

    private boolean takeFuel(RequirementFuel fuel, long remaining, Set<IItemHandler> allowed) {
        for (var entry : after.entrySet()) {
            if (remaining <= 0) break;
            var slot = entry.getKey();
            if (!allowed.contains(slot.handler())) continue;
            ItemStack stack = entry.getValue();
            int burn = RequirementFuel.burnTime(stack);
            if (burn <= 0 || !fuel.getNbt().matches(stack.getTag())) continue;
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                // As upstream, stacked container fuels are not consumed. Preserve the container in its slot.
                if (stack.getCount() != 1 || !(slot.handler() instanceof IItemHandlerModifiable)
                        || slot.handler().extractItem(slot.index(), 1, true).getCount() != 1) continue;
                entry.setValue(remainder.copy());
                rewritten.add(slot);
                remaining -= burn;
            } else {
                int requested = (int) Math.min(stack.getCount(), (remaining + burn - 1) / burn);
                int take = Math.min(requested, slot.handler().extractItem(slot.index(), requested, true).getCount());
                stack.shrink(take);
                remaining -= (long) take * burn;
            }
        }
        return remaining <= 0;
    }

    private boolean take(RequirementItem item, long remaining, Set<IItemHandler> allowed) {
        return take(item, remaining, allowed, null, -1, null);
    }
    private boolean take(RequirementItem item, long remaining, Set<IItemHandler> allowed,
                         RequirementIngredientArray array, int candidateIndex, MachineContext context) {
        for (var entry : after.entrySet()) {
            if (remaining <= 0) break;
            var slot = entry.getKey();
            if (!allowed.contains(slot.handler())) continue;
            ItemStack stack = entry.getValue();
            if (array != null ? !array.matchesCandidate(context, candidateIndex, stack) : !item.matches(context, stack)) continue;
            if (item.getConsumeDurability() > 0) {
                if (!(slot.handler() instanceof IItemHandlerModifiable)) continue;
                long uses = Math.min(remaining, availableUses(stack, item.getConsumeDurability()));
                if (uses == 0) continue;
                entry.setValue(applyUses(stack, uses, item.getConsumeDurability()));
                rewritten.add(slot);
                remaining -= uses;
            } else {
                int requested = (int) Math.min(remaining, stack.getCount());
                int take = Math.min(requested, slot.handler().extractItem(slot.index(), requested, true).getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    public boolean execute() {
        if (!valid) return false;
        // Revalidate before any mutation; inventory setters notify the owning tile normally.
        for (var entry : before.entrySet()) {
            var slot = entry.getKey();
            if (!ItemStack.matches(entry.getValue(), slot.handler().getStackInSlot(slot.index()))) return false;
        }
        for (var entry : after.entrySet()) {
            var slot = entry.getKey();
            if (ItemStack.matches(before.get(slot), entry.getValue())) continue;
            if (rewritten.contains(slot)) {
                ((IItemHandlerModifiable) slot.handler()).setStackInSlot(slot.index(), entry.getValue().copy());
            } else {
                int take = before.get(slot).getCount() - entry.getValue().getCount();
                if (slot.handler().extractItem(slot.index(), take, false).getCount() != take) return false;
            }
        }
        return true;
    }

    public static long availableUses(ItemStack stack, int damagePerUse) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0 || damagePerUse <= 0) return 0;
        long first = Math.max(1L, ((long) stack.getMaxDamage() - stack.getDamageValue() + damagePerUse - 1) / damagePerUse);
        long fresh = ((long) stack.getMaxDamage() + damagePerUse - 1) / damagePerUse;
        return first + (stack.getCount() - 1L) * fresh;
    }

    private static ItemStack applyUses(ItemStack original, long uses, int damagePerUse) {
        ItemStack stack = original.copy();
        long first = Math.max(1L, ((long) stack.getMaxDamage() - stack.getDamageValue() + damagePerUse - 1) / damagePerUse);
        if (uses < first) {
            stack.setDamageValue((int) (stack.getDamageValue() + uses * damagePerUse));
            return stack;
        }
        uses -= first;
        long fresh = ((long) stack.getMaxDamage() + damagePerUse - 1) / damagePerUse;
        long broken = 1 + uses / fresh;
        if (broken >= stack.getCount()) return ItemStack.EMPTY;
        stack.shrink((int) broken);
        stack.setDamageValue((int) ((uses % fresh) * damagePerUse));
        return stack;
    }
}
