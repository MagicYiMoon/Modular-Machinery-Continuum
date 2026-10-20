/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;

/**
 * The requirement type ids an upgrade can target, in one place.
 *
 * <p>Each is the string {@code Requirement#typeId()} returns for that requirement, and the same string a recipe's
 * own {@code modifiers} entry writes in its {@code "target"} field — so an upgrade's target and a machine's target
 * are spelled identically and a reader can compare them side by side. They are copied here as constants rather than
 * reached through the requirement classes because an upgrade registration does not otherwise need to load an
 * item/fluid/energy requirement — and because a typo in a literal string inside a registration is exactly the kind
 * of mistake that would produce an upgrade that does nothing while looking installed.
 *
 * <p>Upstream's own equivalent is a {@code RequirementType} object handed to {@code RecipeModifier}'s constructor;
 * this port's {@code RecipeModifier} takes the type ID as a string instead, and its javadoc records why (the
 * registry upstream keys its JSON deserialiser with has no job here).
 */
public final class UpgradeTargets {

    /** {@code RequirementItem.TYPE_ID} — items in and out. */
    public static final String ITEM = "modularmachinery:item";

    /** {@code RequirementFluid.TYPE_ID} — fluids in and out. */
    public static final String FLUID = "modularmachinery:fluid";

    /** {@code RequirementEnergy.TYPE_ID} — FE per tick. */
    public static final String ENERGY = "modularmachinery:energy";

    /** {@code RequirementInterfaceNumInput.TYPE_ID} — a smart interface's published number. */
    public static final String INTERFACE_NUMBER_INPUT = "modularmachinery:interface_number_input";

    /** {@link RecipeModifier#OPERATION_ADD} — a shorter name at the call site. */
    public static final int ADD = RecipeModifier.OPERATION_ADD;

    /** {@link RecipeModifier#OPERATION_MULTIPLY} — a shorter name at the call site. */
    public static final int MULTIPLY = RecipeModifier.OPERATION_MULTIPLY;

    private UpgradeTargets() {
    }
}
