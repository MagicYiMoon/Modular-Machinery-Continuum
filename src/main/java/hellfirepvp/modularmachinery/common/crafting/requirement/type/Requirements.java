/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers the requirement types this mod ships, and is the place an addon's registration belongs beside.
 *
 * <h2>Why registration is idempotent and lazy rather than a static block</h2>
 *
 * <p>{@link #registerBuiltIns()} is called by the recipe loader before it consults the registry, and calling it twice does
 * nothing the second time. That is deliberate for two reasons:
 *
 * <ul>
 *   <li><b>Class-loading order cannot leave the registry empty.</b> A static initialiser runs when the class is first
 *       touched; if the loader consulted the registry without touching this class first, it would find nothing and report
 *       every built-in type as unknown -- a failure that would look like a parsing bug and be one line away from a fix
 *       nobody could see.</li>
 *   <li><b>The loader's own reload path can call it again harmlessly.</b> Recipe reloads are frequent, and a registration
 *       that logged or threw on every reload would be noise.</li>
 * </ul>
 *
 * <p><b>An addon registers into {@link RequirementTypeRegistry} directly.</b> There is nothing to unlock first: that was the
 * whole point of PLAN section 374, where the port's loader could only report the types it did not implement because the
 * three-case switch that replaced them could not be extended from outside its own class.
 */
public final class Requirements {

    private static boolean registered = false;

    private Requirements() {
    }

    /**
     * Registers this mod's built-in requirement types, once.
     *
     * <p>Safe to call from anywhere and any number of times. It is called by the loader rather than from a static block so
     * that the registry can never be consulted before its built-ins are in it.
     */
    public static synchronized void registerBuiltIns() {
        if (registered) {
            return;
        }
        registered = true;

        register(EnergyRequirementType.NAME, new EnergyRequirementType());
        register(ItemRequirementType.NAME, new ItemRequirementType());
        register(ItemDurabilityRequirementType.NAME, new ItemDurabilityRequirementType());
        register(IngredientArrayRequirementType.NAME, new IngredientArrayRequirementType());
        register(CatalystRequirementType.NAME, new CatalystRequirementType());
        register(FluidRequirementType.NAME, new FluidRequirementType());
        register(FluidPerTickRequirementType.NAME, new FluidPerTickRequirementType());
        // Upstream's key is "interface_number_input" (RequirementTypesMM.KEY_INTERFACE_NUMBER_INPUT), so the
        // JSON spelling matches the id a pack already knows from upstream's own registry (§PLAN 663).
        register(InterfaceNumberRequirementType.NAME, new InterfaceNumberRequirementType());
        if (hellfirepvp.modularmachinery.common.integration.AstralIntegration.available()) {
            register("starlight", new hellfirepvp.modularmachinery.integration.astral.RequirementStarlight.Type());
            register("constellation", new hellfirepvp.modularmachinery.integration.astral.RequirementConstellation.Type());
        }
        if (hellfirepvp.modularmachinery.integration.extrautilities.RainbowIntegration.available())
            register("rainbow", new hellfirepvp.modularmachinery.integration.extrautilities.RequirementRainbow.Type());
        if (net.minecraftforge.fml.ModList.get().isLoaded("botania")) {
            register("mana", new hellfirepvp.modularmachinery.integration.botania.ManaRequirementType());
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("naturesaura")) {
            register("aura", new hellfirepvp.modularmachinery.integration.naturesaura.AuraRequirementType());
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("bloodmagic")) {
            register("lifeessence", new hellfirepvp.modularmachinery.integration.bloodmagic.LifeEssenceRequirementType());
            register("will", new hellfirepvp.modularmachinery.integration.bloodmagic.WillRequirementType());
        }
        // Keep the Mekanism class out of the core linkage path when the optional mod is absent.
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            try {
                RequirementType gas = (RequirementType) Class.forName(
                        "hellfirepvp.modularmachinery.integration.mekanism.GasRequirementType")
                        .getDeclaredConstructor().newInstance();
                register("gas", gas);
                RequirementType gasPerTick = (RequirementType) Class.forName(
                        "hellfirepvp.modularmachinery.integration.mekanism.GasPerTickRequirementType")
                        .getDeclaredConstructor().newInstance();
                register("gas_pertick", gasPerTick);
            } catch (ReflectiveOperationException ex) {
                Modularmachinery.LOG.warn("[MMCE] Mekanism is present but gas requirement registration failed", ex);
            }
        }
    }

    /**
     * Registers one type under {@code modularmachinery:<name>}.
     *
     * <p>The name is qualified rather than bare because the JSON writes it qualified -- {@code "type":
     * "modularmachinery:item"} -- and a registry keyed on one spelling while the files use another is how a working type
     * comes to be reported as unknown.
     */
    public static void register(String name, RequirementType type) {
        ResourceLocation id = ResourceLocation.tryParse(Modularmachinery.MODID + ":" + name);
        if (id == null) {
            Modularmachinery.LOG.warn("[MMCE] requirement type name '{}' is not a valid resource location; skipped", name);
            return;
        }
        RequirementTypeRegistry.register(id, type);
    }
}
