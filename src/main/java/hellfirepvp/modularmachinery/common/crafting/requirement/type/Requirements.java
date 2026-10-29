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

        register("environment", new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnvironment.Type());
        if (net.minecraftforge.fml.ModList.get().isLoaded("embers")) {
            var ember = new hellfirepvp.modularmachinery.integration.embers.RequirementEmber.Type();
            register("ember", ember);
            RequirementTypeRegistry.register(new ResourceLocation("gugu-utils", "ember"), ember);
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft")) {
            register("temperature", new hellfirepvp.modularmachinery.integration.pneumatic.RequirementTemperature.Type());
            for (boolean rate : java.util.List.of(false, true)) {
                String name = rate ? "compressed_air_pertick" : "compressed_air";
                var type = new hellfirepvp.modularmachinery.integration.pneumatic.RequirementCompressedAir.Type(rate);
                register(name, type);
                RequirementTypeRegistry.register(new ResourceLocation("gugu-utils", name), type);
            }
        }
        register(EnergyRequirementType.NAME, new EnergyRequirementType());
        register(ItemRequirementType.NAME, new ItemRequirementType());
        register(ItemDurabilityRequirementType.NAME, new ItemDurabilityRequirementType());
        register(IngredientArrayRequirementType.NAME, new IngredientArrayRequirementType());
        register(CatalystRequirementType.NAME, new CatalystRequirementType());
        register(FluidRequirementType.NAME, new FluidRequirementType());
        register(FluidPerTickRequirementType.NAME, new FluidPerTickRequirementType());
        // Upstream's key is "interface_number_input" (RequirementTypesMM.KEY_INTERFACE_NUMBER_INPUT), so the
         
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
