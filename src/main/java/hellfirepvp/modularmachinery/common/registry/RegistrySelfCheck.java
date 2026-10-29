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
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.registry;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.List;

/**
 * Startup self-check for the mod's registrations.
 *
 * <p>Exists because "no warnings in the log" is weak evidence that registration actually worked: a
 * {@code DeferredRegister} that is never attached to the mod event bus registers <b>nothing</b>, and
 * does so <b>silently</b>. This logs the concrete registry keys that survive the round trip, so a
 * missing registration shows up as a missing line rather than being inferred from an absence of
 * errors. It is cheap (one pass over each registry during common setup) and runs on both client and
 * dedicated server, which also proves the registration classes are safe server-side.
 *
 * <p>Silence it with {@code -Dmodularmachinery.quietRegistryLog=true}.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RegistrySelfCheck {

    private RegistrySelfCheck() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // AE2's upgrade registry is optional. Keep the integration behind the same class-load firewall as
        // the ME block entities so a standalone MMCE server remains AE2-free.
        if (net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            hellfirepvp.modularmachinery.common.integration.MEUpgradeIntegration.register();
        }

        // THE UPGRADE ITEMS ARE PAIRED WITH THEIR UPGRADES HERE (audit row 37), and this is the EARLIEST point at
        // which that pairing is legal.
        //
        // Why not at registration: `UpgradeItems.register` runs inside `RegistryItems`' static initialiser, i.e.
        // while the mod's classes are still loading and BEFORE `RegisterEvent`. A `RegistryObject` exists from the
        // moment it is created but is not BOUND until that event runs, and Forge's `RegistryObject#get` throws
        // `NullPointerException: Registry Object not present: …` in that window rather than returning null. Doing
         
        //
        // Why here and not in `RegisterEvent`: `RegistryItems.ITEMS` is attached to the bus from the mod
        // constructor and this class is a `@Mod.EventBusSubscriber` for the MOD bus, so FMLCommonSetupEvent is
        // guaranteed to run after every registration event on that same bus — and it is where the rest of this
        // class's round trip already happens, so the pairing is checked by the same pass that reports the mod's
        // registered items.
        String upgradeProblems = hellfirepvp.modularmachinery.common.upgrade.UpgradeItems.bindUpgrades();
        if (!upgradeProblems.isEmpty()) {
            Modularmachinery.LOG.error("[MMCE] upgrade items did not bind to their upgrades: {}", upgradeProblems);
        }

        if (Boolean.getBoolean("modularmachinery.quietRegistryLog")) {
            return;
        }
        List<ResourceLocation> blocks = listOurKeys(ForgeRegistries.BLOCKS);
        List<ResourceLocation> items = listOurKeys(ForgeRegistries.ITEMS);
        Modularmachinery.LOG.info("[MMCE] {} block(s) registered: {}", blocks.size(), blocks);
        Modularmachinery.LOG.info("[MMCE] {} item(s) registered: {}", items.size(), items);

        long magicBlocks = blocks.stream().filter(id -> "modularmagic".equals(id.getNamespace())).count();
        Modularmachinery.LOG.info("[MMCE] of which {} use the modularmagic provider namespace (optional integrations vary by provider)",
                magicBlocks);
    }

    private static List<ResourceLocation> listOurKeys(IForgeRegistry<?> registry) {
        // Forge's registries rather than BuiltInRegistries: on 1.20.1 the vanilla static registry
        // fields are deprecated in favour of the level-scoped RegistryAccess, whereas the Forge
        // registry view is stable and is what a mod should be reading at setup time anyway.
        //
        // Both namespaces are reported. The modularmagic providers register under their own
        // namespace (so existing pack references resolve), which means a modularmachinery-only check
        // would silently fail to notice if they stopped registering.
        return registry.getKeys().stream()
                .filter(id -> Modularmachinery.MODID.equals(id.getNamespace())
                        || "modularmagic".equals(id.getNamespace()))
                .sorted()
                .toList();
    }
}
