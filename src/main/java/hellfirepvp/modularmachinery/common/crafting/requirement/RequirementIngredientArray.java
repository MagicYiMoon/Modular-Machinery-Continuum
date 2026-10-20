/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.*;

/** Ordered alternatives. Each parallel copy must be paid by one complete alternative. */
public class RequirementIngredientArray extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement.Parallelizable {
    public record Candidate(RequirementItem item, int minAmount, int maxAmount, float weight) {
        public Candidate {
            if (item == null || minAmount < 0 || maxAmount < minAmount || !Float.isFinite(weight) || weight < 0)
                throw new IllegalArgumentException("Invalid ingredient-array candidate");
        }
        public int rollAmount(boolean deterministic) {
            if (minAmount == maxAmount) return minAmount;
            return amountAt(deterministic ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong((long)maxAmount - minAmount + 1));
        }
        public int amountAt(long offset) {
            if (offset < 0 || offset > (long)maxAmount - minAmount) throw new IllegalArgumentException("Amount offset outside range");
            return (int)(minAmount + offset);
        }
        /** Ore dictionary outputs used one representative. Use a stable registry order for modern tags. */
        public RequirementItem outputItem() {
            if (item.getTag() == null) return item;
            var tags = net.minecraftforge.registries.ForgeRegistries.ITEMS.tags();
            var representative = tags == null ? null : tags.getTag(item.getTag()).stream()
                    .min(java.util.Comparator.comparing(i -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i).toString())).orElse(null);
            return representative == null ? item : new RequirementItem(IOType.OUTPUT,
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(representative).toString(), item.getAmount()).withNbt(item.getNbt());
        }
    }
    private final List<Candidate> candidates;
    private final List<RequirementItem> ingredients;
    private final float chance;
    private boolean unaffected;
    private int parallelism = 1;
    private final Map<Integer, hellfirepvp.modularmachinery.common.crafting.ItemRequirementChecker> candidateCheckers = new HashMap<>();
    private final Map<Integer, List<hellfirepvp.modularmachinery.common.crafting.ItemRequirementModifier>> candidateModifiers = new HashMap<>();

    public RequirementIngredientArray(List<RequirementItem> ingredients, float chance) {
        this(ingredients.stream().map(i -> new Candidate(i, i.getAmount(), i.getAmount(), 1F)).toList(), chance,
                IOType.INPUT);
    }
    public RequirementIngredientArray(List<Candidate> candidates, float chance, IOType io) {
        if (candidates.isEmpty() || candidates.stream().anyMatch(c -> c.item().getIOType() != io
                || c.item().getConsumeDurability() != 0))
            throw new IllegalArgumentException("Ingredient array requires ordinary item candidates");
        if (!Float.isFinite(chance) || chance < 0 || chance > 1)
            throw new IllegalArgumentException("Invalid ingredient array chance");
        if (io == IOType.OUTPUT && chance > 0 && candidates.stream().noneMatch(c -> c.weight() > 0))
            throw new IllegalArgumentException("Random output requires a positive total weight");
        this.candidates = List.copyOf(candidates);
        this.ingredients = this.candidates.stream().map(Candidate::item).toList();
        this.chance = chance;
    }
    public List<Candidate> getCandidates() { return candidates; }
    public void setCandidateChecker(int index, hellfirepvp.modularmachinery.common.crafting.ItemRequirementChecker checker) {
        if (index >= 0 && index < candidates.size() && checker != null) candidateCheckers.put(index, checker);
    }
    public void addCandidateModifier(int index, hellfirepvp.modularmachinery.common.crafting.ItemRequirementModifier modifier) {
        if (index >= 0 && index < candidates.size() && modifier != null)
            candidateModifiers.computeIfAbsent(index, ignored -> new ArrayList<>()).add(modifier);
    }
    public boolean matchesCandidate(MachineContext context, int index, net.minecraft.world.item.ItemStack stack) {
        if (index < 0 || index >= candidates.size()) return false;
        var candidate = candidates.get(index);
        var required = modifiedCandidateInputStack(context, index, candidate.item().getAmount());
        boolean baseMatch = required.isEmpty()
                ? candidate.item().matches(context, stack)
                : required.getItem() == stack.getItem()
                && (required.getTag() == null || net.minecraft.world.item.ItemStack.isSameItemSameTags(required, stack));
        if (!baseMatch) return false;
        var checker = candidateCheckers.get(index);
        if (checker == null) return true;
        try {
            var entity = context == null || context.getLevel() == null ? null : context.getLevel().getBlockEntity(context.getControllerPos());
            var controller = entity instanceof hellfirepvp.modularmachinery.common.tiles.TileController tile ? tile : null;
            return checker.test(controller, stack.copy());
        } catch (Throwable failure) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[MMCE] ingredient-array checker failed", failure);
            return false;
        }
    }
    public net.minecraft.world.item.ItemStack modifiedCandidateInputStack(MachineContext context, int index, int amount) {
        if (index < 0 || index >= candidates.size()) return net.minecraft.world.item.ItemStack.EMPTY;
        var base = candidates.get(index).item().asStack();
        if (base.isEmpty()) return base;
        base.setCount(Math.max(1, amount));
        return applyCandidateModifiers(context, index, base);
    }

    public net.minecraft.world.item.ItemStack modifiedCandidateOutputStack(MachineContext context, int index,
                                                                            net.minecraft.world.item.ItemStack base) {
        if (base == null || base.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        var one = base.copyWithCount(1);
        return applyCandidateModifiers(context, index, one);
    }

    /** Compatibility alias for callers written before input/output modifier stacks were split. */
    public net.minecraft.world.item.ItemStack modifiedCandidateStack(MachineContext context, int index,
                                                                       net.minecraft.world.item.ItemStack base) {
        return modifiedCandidateOutputStack(context, index, base);
    }

    private net.minecraft.world.item.ItemStack applyCandidateModifiers(MachineContext context, int index,
                                                                         net.minecraft.world.item.ItemStack base) {
        var modifiers = candidateModifiers.get(index);
        if (modifiers == null || modifiers.isEmpty() || base.isEmpty()) return base;
        var entity = context == null || context.getLevel() == null ? null : context.getLevel().getBlockEntity(context.getControllerPos());
        var controller = entity instanceof hellfirepvp.modularmachinery.common.tiles.TileController tile ? tile : null;
        var result = base.copy();
        for (var modifier : modifiers) {
            try { var updated = modifier.apply(controller, result.copy()); if (updated != null) result = updated.copy(); }
            catch (Throwable failure) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[MMCE] ingredient-array modifier failed", failure);
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
        }
        return result;
    }

    public long inputAmount(MachineContext context, int index, int requestedAmount) {
        // A tag has no single concrete stack. Its empty display stack must not make the
        // input free; matchesCandidate still checks each real stack against the tag.
        if (index >= 0 && index < candidates.size() && candidates.get(index).item().getTag() != null)
            return Math.max(0, requestedAmount);
        var stack = modifiedCandidateInputStack(context, index, requestedAmount);
        return stack.isEmpty() || stack.getCount() <= 0 ? 0L : stack.getCount();
    }

    public long outputAmount(MachineContext context, int index, int requestedAmount) {
        var stack = modifiedCandidateOutputStack(context, index, candidates.get(index).outputItem().asStack());
        return stack.isEmpty() || stack.getCount() <= 0 ? 0L : (long) requestedAmount * stack.getCount();
    }

    public long countCandidateSpareRoom(MachineContext context, int index) {
        if (index < 0 || index >= candidates.size()) return 0L;
        var stack = modifiedCandidateOutputStack(context, index, candidates.get(index).outputItem().asStack());
        if (stack.isEmpty()) return 0L;
        long total = 0;
        for (var handler : context.forSelector(getSelectorTag()).capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                MachineContext.ComponentKind.OUTPUT)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var remainder = handler.insertItem(slot, stack.copyWithCount(Integer.MAX_VALUE), true);
                total += (long) Integer.MAX_VALUE - remainder.getCount();
            }
        }
        return total;
    }

    /** Reserve capacity for an already selected candidate using its transformed output stack. */
    public long reserveCandidateOutput(MachineContext context, int index, long demand,
                                        java.util.IdentityHashMap<net.minecraftforge.items.IItemHandler,
                                                java.util.Map<Integer, net.minecraft.world.item.ItemStack>> reserved) {
        if (index < 0 || index >= candidates.size() || demand <= 0) return 0;
        var product = modifiedCandidateOutputStack(context, index, candidates.get(index).outputItem().asStack());
        if (product.isEmpty()) return 0;
        long remaining = demand;
        var staged = new java.util.IdentityHashMap<net.minecraftforge.items.IItemHandler,
                java.util.Map<Integer, net.minecraft.world.item.ItemStack>>();
        for (var handler : context.forSelector(getSelectorTag()).capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                MachineContext.ComponentKind.OUTPUT)) {
            var existing = reserved.getOrDefault(handler, java.util.Map.of());
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                var prior = existing.getOrDefault(slot, net.minecraft.world.item.ItemStack.EMPTY);
                if (!prior.isEmpty() && !net.minecraft.world.item.ItemStack.isSameItemSameTags(prior, product)) continue;
                int offer = (int) Math.min(Integer.MAX_VALUE, remaining + prior.getCount());
                var rest = handler.insertItem(slot, product.copyWithCount(offer), true);
                long take = Math.min(remaining, Math.max(0L, (long) offer - rest.getCount() - prior.getCount()));
                if (take > 0) {
                    staged.computeIfAbsent(handler, ignored -> new java.util.HashMap<>())
                            .put(slot, product.copyWithCount(prior.getCount() + (int) take));
                    remaining -= take;
                }
            }
        }
        if (remaining == 0) staged.forEach((handler, slots) ->
                reserved.computeIfAbsent(handler, ignored -> new java.util.HashMap<>()).putAll(slots));
        return demand - remaining;
    }
    public List<RequirementItem> getIngredients() { return ingredients; }
    public float getChance() { return chance; }
    @Override public IOType getIOType() { return candidates.get(0).item().getIOType(); }
    @Override public String typeId() { return "modularmachinery:ingredient_array_input"; }
    @Override public boolean canStart(MachineContext context) {
        if (getIOType() == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        if (getIOType() == IOType.OUTPUT) return canEmit(context, 1);
        return ItemInputPlan.plan(context, List.of(this), 1, false).valid();
    }
    @Override public int getMaxParallelism(MachineContext context, int maximum, List<Requirement> siblings) {
        if (getIOType() == IOType.OUTPUT && isIgnoreOutputCheck()) return maximum;
        var inputs = new ArrayList<Requirement>(siblings == null ? List.of() : siblings);
        if (!inputs.contains(this)) inputs.add(this);
        int low = 0, high = Math.max(0, maximum);
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (getIOType() == IOType.OUTPUT
                    ? hellfirepvp.modularmachinery.common.tiles.MachineTicker.outputAllocationFailure(context, inputs, mid) == null
                    : ItemInputPlan.plan(context, inputs, mid, false).valid()) low = mid;
            else high = mid - 1;
        }
        return low;
    }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int value) { if (!unaffected) parallelism = value; }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean value) { unaffected = value; if (value) parallelism = 1; }
    @Override public Requirement scaled(float value, int operation, IOType io, boolean affectChance) {
        if (io != null && io != getIOType()) return this;
        var scaled = affectChance ? candidates.stream().map(c -> new Candidate(c.item(), c.minAmount(), c.maxAmount(),
                Math.max(0, Math.min(Float.MAX_VALUE, Requirement.apply(c.weight(), value, operation))))).toList() : candidates.stream().map(c -> new Candidate(
                (RequirementItem)c.item().scaled(value, operation, io, false),
                Math.min(scale(c.minAmount(), value, operation), scale(c.maxAmount(), value, operation)),
                Math.max(scale(c.minAmount(), value, operation), scale(c.maxAmount(), value, operation)), c.weight())).toList();
        var copy = new RequirementIngredientArray(scaled,
                affectChance ? Math.max(0, Math.min(1, Requirement.apply(chance, value, operation))) : chance, getIOType());
        copy.setParallelizeUnaffected(unaffected);
        copy.setParallelism(parallelism);
        copy.candidateCheckers.putAll(candidateCheckers);
        candidateModifiers.forEach((index, values) -> copy.candidateModifiers.put(index, new ArrayList<>(values)));
        return copySelectorTo(copy);
    }
    @Override public String describe() { return "Any complete input from " + ingredients.stream().map(RequirementItem::describe).toList(); }

    private static int scale(int amount, float value, int operation) { return Math.max(0, Math.round(Requirement.apply(amount, value, operation))); }
    public Candidate select(double unitRoll) {
        if (!Double.isFinite(unitRoll) || unitRoll < 0 || unitRoll >= 1) throw new IllegalArgumentException("Roll must be in [0,1)");
        double total = 0;
        for (Candidate c : candidates) total += c.weight();
        if (total <= 0) return null;
        double roll = unitRoll * total;
        for (Candidate c : candidates) { if ((roll -= c.weight()) < 0) return c; }
        return candidates.stream().filter(c -> c.weight() > 0).reduce((a, b) -> b).orElse(null);
    }
    public Candidate chooseCandidate() { return select(Boolean.getBoolean("modularmachinery.selfTest") ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextDouble()); }
    public Candidate chooseCandidate(MachineContext context, int parallelism) {
        if (getIOType() == IOType.OUTPUT && isIgnoreOutputCheck()) return chooseCandidate();
        var fitting = java.util.stream.IntStream.range(0, candidates.size())
                .filter(i -> candidates.get(i).weight() > 0
                        && countCandidateSpareRoom(context, i) >= outputAmount(context, i, candidates.get(i).maxAmount())
                        * (unaffected ? 1 : Math.max(1, parallelism)))
                .mapToObj(candidates::get).toList();
        if (fitting.isEmpty()) return null;
        double total = fitting.stream().mapToDouble(Candidate::weight).sum();
        double roll = Boolean.getBoolean("modularmachinery.selfTest") ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextDouble() * total;
        for (Candidate c : fitting) if ((roll -= c.weight()) < 0) return c;
        return fitting.get(fitting.size() - 1);
    }
    @Override public boolean canEmit(MachineContext context, int parallelism) {
        if (getIOType() == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        if (getIOType() != IOType.OUTPUT) return canStart(context);
        if (chance <= 0) return true;
        return java.util.stream.IntStream.range(0, candidates.size()).filter(i -> candidates.get(i).weight() > 0).allMatch(i ->
                countCandidateSpareRoom(context, i) >= outputAmount(context, i, candidates.get(i).maxAmount())
                        * (unaffected ? 1 : Math.max(1, parallelism)));
    }
}
