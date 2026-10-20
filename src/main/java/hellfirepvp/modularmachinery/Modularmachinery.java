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
package hellfirepvp.modularmachinery;

import com.mojang.logging.LogUtils;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryCreativeTabs;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Modular Machinery: Community Edition — Forge 1.20.1 entrypoint.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream 2.3.2 used the legacy lifecycle: an attribute-form
 * {@code @Mod(modid=..., version=..., acceptedMinecraftVersions=...)}, a pair of
 * {@code @Mod.EventHandler} methods bound to {@code FMLPreInitializationEvent} /
 * {@code FMLInitializationEvent} / {@code FMLPostInitializationEvent} / {@code FMLLoadCompleteEvent},
 * a {@code @SidedProxy} for client/server splitting, and named {@code @Instance} fields. None of that
 * exists on 1.20.1:
 *
 * <ul>
 *   <li>The annotation is single-argument; metadata lives in {@code mods.toml}.</li>
 *   <li>There are no FML lifecycle events — initialisation happens in this constructor.</li>
 *   <li>{@code @SidedProxy} is replaced by {@code DistExecutor} / {@code @OnlyIn}.</li>
 *   <li>{@code @Instance} and {@code @ObjectHolder} injection are replaced by explicit references
 *       to {@code DeferredRegister} results.</li>
 * </ul>
 *
 * <p>The mod event bus is phase-scoped: {@link net.minecraftforge.registries.DeferredRegister}s must
 * be attached here, in the constructor, or their contents silently never register.
 */
@Mod(Modularmachinery.MODID)
public class Modularmachinery {

    public static final String MODID = "modularmachinery";
    public static final Logger LOG = LogUtils.getLogger();

    public Modularmachinery() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // THE CONFIG SPEC, before anything that reads it (PLAN section 291).
        //
        // Registered here rather than in a client setup, because the spec is COMMON: a dedicated server needs it
        // to exist even though only the client reads the colour today. `Config.SPEC` also BUILDS the spec in its
        // class initialiser, so this line is what guarantees the default is parsed before any colour handler can
        // ask for it.
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                hellfirepvp.modularmachinery.common.data.Config.SPEC);

        hellfirepvp.modularmachinery.common.registry.DedicatedControllers.register();
        modBus.addListener(hellfirepvp.modularmachinery.common.registry.ControllerResourcePack::addPacks);

        // --- Registration: every holder must be attached, or it silently registers nothing. ---
        RegistryBlocks.BLOCKS.register(modBus);
        if (hellfirepvp.modularmachinery.common.integration.AstralIntegration.available())
            hellfirepvp.modularmachinery.integration.astral.AstralRegistration.register(modBus);
        if (hellfirepvp.modularmachinery.integration.extrautilities.RainbowIntegration.available())
            hellfirepvp.modularmachinery.integration.extrautilities.RainbowRegistration.register(modBus);
        if (net.minecraftforge.fml.ModList.get().isLoaded("botania"))
            hellfirepvp.modularmachinery.integration.botania.ManaRegistration.register(modBus);
        if (net.minecraftforge.fml.ModList.get().isLoaded("naturesaura"))
            hellfirepvp.modularmachinery.integration.naturesaura.AuraRegistration.register(modBus);
        if (net.minecraftforge.fml.ModList.get().isLoaded("bloodmagic"))
            hellfirepvp.modularmachinery.integration.bloodmagic.BloodMagicRegistration.register(modBus);
        RegistryItems.ITEMS.register(modBus);
        RegistryBlockEntities.BLOCK_ENTITIES.register(modBus);
        RegistryCreativeTabs.CREATIVE_TABS.register(modBus);

        // ModularMagic provider placeholders. These register under the *modularmagic* namespace (see
        // RegistryModularMagicBlocks) so that addon and structure JSONs naming
        // modularmagic:blockmanaproviderinput and friends keep resolving, but they are still driven by
        // this mod's event bus.
        RegistryModularMagicBlocks.BLOCKS.register(modBus);
        RegistryModularMagicItems.ITEMS.register(modBus);
        RegistryMenus.MENUS.register(modBus);
        RegistryMEBlocks.BLOCKS.register(modBus);
        // §169: the item forms. Without this line the ITEMS DeferredRegister never fires and the eight ME
        // blocks stay itemless and unplaceable -- which is exactly the state §169 found them in.
        RegistryMEBlocks.ITEMS.register(modBus);

        // THE AE2 BLOCK ENTITY TYPES, GUARDED IN THE CALLER (PLAN section 405).
        //
        // These CANNOT go in RegistryBlockEntities: that register is subscribed unconditionally, its class would then be loaded
        // on every server, and loading it would reach an ME tile whose hierarchy includes AE2's IInWorldGridNodeHost. On a server
        // without AE2 that is a NoClassDefFoundError during RegisterEvent -- the mod fails to load, not a missing feature.
        //
        // The guard must be HERE rather than inside RegistryMEBlockEntities, because CALLING that class's method is what loads
        // it. The JVM resolves a class reference at the instruction that uses it, so an untaken branch never loads it.
        // RegistryMEBlockEntities.register() repeats the check as a second line of defence.
        if (net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.register(modBus);
            if (hellfirepvp.modularmachinery.common.block.BlockMEGasBus.available())
                hellfirepvp.modularmachinery.integration.appmek.GasBusRegistration.register(modBus);
            // The SAME guard, for the THIRD firewall boundary (PLAN section 478): a MENU whose constructor names an
            // AE2 type. RegistryMenus is loaded unconditionally, so the pattern provider's menu cannot live there --
            // registering it there would put an AE2-adjacent type in an always-loaded class and kill a server without
            // AE2 at REGISTRY time, which is exactly what sections 404 and 411 already cost.
            hellfirepvp.modularmachinery.common.registry.RegistryMEMenus.register(modBus);
        }

        // Forge networking needs no registry object, so this is a plain call. It must happen
        // during construction, before any screen can try to send.
        ModularmachineryNetwork.register();

        // The authoring tool's one game-bus handler: dropping a disconnecting player's selection.
        // Registered explicitly rather than by @EventBusSubscriber, for the reason that method's own
        // javadoc gives -- nothing in the class is otherwise reachable from this constructor's called
        // graph, so an annotation would be a promise the scan does not have to keep.
        hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper
                .register(net.minecraftforge.common.MinecraftForge.EVENT_BUS);

        // Remaining Forge event integrations are subscribers on their owning classes rather than
        // constructor calls.  Keeping these registrations beside their implementation avoids a
        // second, hand-maintained list that can silently drift:
        //
        //   MachineReloadListener       -> AddReloadListenerEvent
        //   CommandRegistration         -> RegisterCommandsEvent
        //   ControllerSecurityEvents     -> block break/place protection
        //   ClientSetup (CLIENT/MOD bus) -> menu screens, colours and BERs
        //
        // The constructor therefore only attaches DeferredRegisters and installs the network and
        // selection handlers that cannot be discovered through those subscribers.
    }
}
