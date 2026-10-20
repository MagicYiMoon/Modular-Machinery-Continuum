/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Source-compatible optional item catalyst.  A catalyst is an input only when a matching
 * stack is present; when it is present its modifiers are added to this run's context.
 */
public class RequirementCatalyst extends RequirementIngredientArray {
    private final List<RecipeModifier> modifiers = new ArrayList<>();
    private final List<String> tooltips = new ArrayList<>();
    private boolean active;

    public RequirementCatalyst(List<Candidate> candidates, float chance) {
        super(candidates, chance, IOType.INPUT);
        setParallelizeUnaffected(true);
    }

    public RequirementCatalyst(RequirementItem item) {
        this(List.of(new Candidate(item, item.getAmount(), item.getAmount(), 1F)), 1F);
    }

    public void addModifier(RecipeModifier modifier) { if (modifier != null) modifiers.add(modifier); }
    public List<RecipeModifier> getModifiers() { return List.copyOf(modifiers); }
    public void addTooltip(String tooltip) { if (tooltip != null) tooltips.add(tooltip); }
    public List<String> getTooltips() { return List.copyOf(tooltips); }

    /** True only for the current resolved recipe run. */
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Probe without changing the optional requirement into a mandatory one. */
    public boolean detect(MachineContext context) {
        // The source mod's chance is checked in startCrafting, when the catalyst is
        // actually consumed.  It does not prevent detection or the conditional
        // modifiers from being applied.  In particular, chance=0 means "never
        // consume this catalyst", not "disable its effect".
        boolean previous = active;
        active = true;
        try { return super.canStart(context); }
        finally { active = previous; }
    }

    @Override public boolean canStart(MachineContext context) { return !active || super.canStart(context); }
    @Override public boolean canFinish(MachineContext context) { return !active || super.canFinish(context); }
    @Override public boolean canEmit(MachineContext context, int parallelism) { return true; }
    @Override public int getMaxParallelism(MachineContext context, int maximum, List<Requirement> siblings) {
        return active ? maximum : maximum;
    }
    @Override public String typeId() { return "modularmachinery:catalyst"; }
    @Override public String describe() {
        return "Catalyst (optional): " + getIngredients().stream().map(RequirementItem::describe).toList();
    }

    @Override public Requirement scaled(float value, int operation, IOType io, boolean affectChance) {
        if (io != null && io != IOType.INPUT) return this;
        List<Candidate> scaled = new ArrayList<>();
        for (Candidate candidate : getCandidates()) {
            // Source deepCopyModified applies amount and chance modifiers separately.
            RequirementItem item = affectChance ? candidate.item() : (RequirementItem) candidate.item().scaled(value, operation, io, false);
            int min = affectChance ? candidate.minAmount() : scale(candidate.minAmount(), value, operation);
            int max = affectChance ? candidate.maxAmount() : scale(candidate.maxAmount(), value, operation);
            float weight = affectChance ? Math.max(0F, Requirement.apply(candidate.weight(), value, operation))
                    : candidate.weight();
            scaled.add(new Candidate(item, Math.min(min, max), Math.max(min, max), weight));
        }
        float chance = affectChance ? Math.max(0F, Math.min(1F, Requirement.apply(getChance(), value, operation))) : getChance();
        RequirementCatalyst copy = new RequirementCatalyst(scaled, chance);
        copy.setParallelizeUnaffected(isParallelizeUnaffected());
        copy.setParallelism(getParallelism());
        copy.modifiers.addAll(modifiers);
        copy.tooltips.addAll(tooltips);
        copy.setActive(active);
        return copySelectorTo(copy);
    }

    private static int scale(int amount, float value, int operation) {
        return Math.max(0, Math.round(Requirement.apply(amount, value, operation)));
    }
}
