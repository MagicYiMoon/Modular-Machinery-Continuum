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
package hellfirepvp.modularmachinery.client;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.screen.ScreenEnergyHatch;
import hellfirepvp.modularmachinery.client.screen.ScreenFactoryController;
import hellfirepvp.modularmachinery.client.screen.ScreenFluidHatch;
import hellfirepvp.modularmachinery.client.screen.ScreenItemBus;
import hellfirepvp.modularmachinery.client.screen.ScreenParallelController;
import hellfirepvp.modularmachinery.client.screen.ScreenSmartInterface;
import hellfirepvp.modularmachinery.client.screen.ScreenUpgradeBus;
import hellfirepvp.modularmachinery.client.screen.ScreenMachineController;
import hellfirepvp.modularmachinery.client.screen.ScreenGroupInputConfig;
import hellfirepvp.modularmachinery.client.screen.ScreenMachineEditor;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.client.geo.ControllerGeoRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup: binds each menu type to its screen.
 *
 * <p><b>1.12.2 → 1.20.1.</b> This is the half of the GUI system that is <b>not registered</b>. Upstream's
 * {@code IGuiHandler} returned the {@code GuiScreen} from the same numeric id that produced the
 * container, so client and server halves could not drift. On 1.20.1 the menu is a registry object while
 * the screen is bound here by {@code MenuScreens.register}, and <b>nothing enforces that the two
 * lists stay in step</b>: a menu without a binding compiles, registers, opens on the server, and then
 * throws on the client when it tries to display it.
 *
 * <p>Hence the explicit note: <b>every entry in {@link RegistryMenus} needs a line here.</b>
 *
 * <p>Annotated with {@link Dist#CLIENT} so the class is not even loaded on a dedicated server, which is
 * required — {@code MenuScreens} and the screen classes reference client-only types, and touching them
 * server-side is a hard crash rather than a soft failure.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private ClientSetup() {
    }

    /** Registers the shared GeckoLib renderer for both controller block-entity types. */
    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RegistryBlockEntities.ENERGY_OUTPUT_PORT.get(), context -> new hellfirepvp.modularmachinery.client.world.EnergyPortRenderer());
        event.registerBlockEntityRenderer(RegistryBlockEntities.CONTROLLER.get(), context -> new ControllerGeoRenderer());
        event.registerBlockEntityRenderer(RegistryBlockEntities.FACTORY_CONTROLLER.get(), context -> new ControllerGeoRenderer());
    }

    /**
     * GeckoLib registers its own cache listener, but MMCE still needs a post-reload invalidation
     * signal for controller instances that survived the resource reload.  The listener is client
     * only and deliberately does not touch GeckoLib's private maps; GeckoLib remains the owner of
     * model and animation loading.
     */
    @SubscribeEvent
    public static void registerClientReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(net.minecraft.server.packs.resources.ResourceManager resources,
                                   net.minecraft.util.profiling.ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, net.minecraft.server.packs.resources.ResourceManager resources,
                                 net.minecraft.util.profiling.ProfilerFiller profiler) {
                hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry.markClientResourcesReloaded();
                hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper.clearScene();
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft.level != null) {
                    // Rebuild block entity render sections so a controller whose model/texture was
                    // changed by a resource pack is drawn immediately without waiting for a chunk
                    // to unload and load again.
                    minecraft.levelRenderer.allChanged();
                }
            }
        });
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Enqueued rather than called directly: FMLClientSetupEvent runs on the mod-loading worker, and
        // MenuScreens.register touches a client-side map that must be populated on the render thread.
        event.enqueueWork(() -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft"))
                hellfirepvp.modularmachinery.integration.pneumatic.ScreenPressureHatch.register();
            if (net.minecraftforge.fml.ModList.get().isLoaded("botania"))
                hellfirepvp.modularmachinery.integration.botania.SparkManaClient.register();
            if (net.minecraftforge.fml.ModList.get().isLoaded("bloodmagic"))
                hellfirepvp.modularmachinery.integration.bloodmagic.ScreenLifeEssence.register();
            // Each name is recorded as its screen is registered, so the log line CANNOT disagree with the
            // registrations. It used to be a hand-written string listing four menus, which stayed correct
            // for as long as four existed and then reported "4 bound" while eight registrations sat above
            // it -- output that reads exactly like four screens failing to bind.
            //
            // A hand-maintained list beside a registration list is a checklist that will drift; nothing can
            // catch the disagreement, because the string is not derived from anything. This version is.
            java.util.List<String> bound = new java.util.ArrayList<>();

            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.MACHINE_CONTROLLER.get(), ScreenMachineController::new);
            bound.add("machine_controller");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.ITEM_BUS.get(), ScreenItemBus::new);
            bound.add("item_bus");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.ENERGY_HATCH.get(), ScreenEnergyHatch::new);
            bound.add("energy_hatch");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.FLUID_HATCH.get(), ScreenFluidHatch::new);
            bound.add("fluid_hatch");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.PARALLEL_CONTROLLER.get(), ScreenParallelController::new);
            bound.add("parallel_controller");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.UPGRADE_BUS.get(), ScreenUpgradeBus::new);
            bound.add("upgrade_bus");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.FACTORY_CONTROLLER.get(), ScreenFactoryController::new);
            bound.add("factory_controller");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.SMART_INTERFACE.get(), ScreenSmartInterface::new);
            bound.add("smart_interface");
        net.minecraft.client.gui.screens.MenuScreens.register(
                RegistryMenus.GROUP_INPUT_CONFIG.get(), ScreenGroupInputConfig::new);
        bound.add("group_input_config");
            net.minecraft.client.gui.screens.MenuScreens.register(
                    RegistryMenus.MACHINE_EDITOR.get(), ScreenMachineEditor::new);
            bound.add("machine_editor");

            // The ME screens. GUARDED, and the guard must call OUT to ClientSetupME rather than name the
            // screen here: a method body is verified when its class LINKS, so an inline reference would
             
            if (net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
                ClientSetupME.register(bound);
            }

             
            //
            // models/item/<block>.json has always pointed at the NORMAL tier, and every tier is the same
            // item, so all seven stacks rendered identically -- reported as "创造标签里面一模一样的".
            //
            // The tiers cannot be told apart by a single numeric predicate: ItemOverrides CLAMPS its
            // predicate to [0,1], so an eight-way ordinal has nowhere to go. Instead each tier gets its
            // OWN mutually-exclusive predicate, true only for a stack whose BlockStateTag says that tier.
            //
            // And no BakedModel is needed. Vanilla resolves these overrides itself, so this is one
            // registration and a JSON entry per tier -- which is what 1.20.1 replaced 1.12.2 metadata with.
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.itemInputBus.get(), SIZES_ITEM_BUS);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.itemOutputBus.get(), SIZES_ITEM_BUS);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.fluidInputHatch.get(), SIZES_FLUID);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.fluidOutputHatch.get(), SIZES_FLUID);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.energyInputHatch.get(), SIZES_ENERGY);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.energyOutputHatch.get(), SIZES_ENERGY);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.emberInput.get(),
                    java.util.Arrays.stream(hellfirepvp.modularmachinery.common.block.prop.EmberHatchSize.values())
                            .map(hellfirepvp.modularmachinery.common.block.prop.EmberHatchSize::getSerializedName).toArray(String[]::new));
            // The casing is NOT in the bus table: its values are plain/vent/firebox/gearbox/reinforced/
            // circuitry, and registering it with the bus sizes created predicates nothing could match.
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.blockCasing.get(),
                    SIZES_CASING);
            // §731: the two `type=` families. They had NO table here at all, so neither item could ever select
            // a tier model -- and both item models had no overrides either, which is why every one of the ten
            // stacks rendered as the NORMAL model while its PLACED block rendered correctly from the blockstate.
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.parallelController.get(),
                    TYPES_PARALLEL_CONTROLLER);
            registerTierPredicates(hellfirepvp.modularmachinery.common.registry.RegistryItems.upgradeBus.get(),
                    TYPES_UPGRADE_BUS);

            Modularmachinery.LOG.info("[MMCE] client menus bound ({}): {}",
                    bound.size(), String.join(", ", bound));
        });
    }

    /**
     * Opens the blueprint preview. <b>Client-only, and it must stay in this class.</b>
     *
     * <p>This lives here rather than in {@code ItemBlueprint} because of a hard requirement of Forge's
     * {@code RuntimeDistCleaner}: a method whose <b>body</b> references a client class pulls that class in
     * when its owning class is loaded, even if the body only ever runs inside
     * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)}.
     *
     * <p>That is not theoretical — it broke the dedicated server. {@code ItemBlueprint.use}/{@code useOn}
     * originally called {@code Minecraft.getInstance().setScreen(new ScreenBlueprint(machine))} inside the
     * dist-guarded lambda, which compiled into a synthetic method <b>on {@code ItemBlueprint}</b>. The
     * server loads {@code ItemBlueprint} (to register the item) and failed at startup with:
     *
     * <pre>
     *   Attempted to load class net/minecraft/client/gui/screens/Screen
     *   for invalid dist DEDICATED_SERVER
     *   at RegistryItems.&lt;clinit&gt;
     * </pre>
     *
     * <p>Moving the body here fixes it, because the lambda in {@code ItemBlueprint} now references only
     * <b>this</b> class by name, and {@code invokedynamic} resolves that lazily — the client class is loaded
     * when the lambda runs, which on a server it never does.
     */

    /** The bus tiers, in declaration order. The names must be the blockstate's serialised values. */
    private static final String[] SIZES_ITEM_BUS = {
            "tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous"
    };

    /**
     * The casing's values, which are a DIFFERENT SET from the bus sizes.
     *
     * <p>Registering the casing with the bus table was the defect: it created predicates named
     * {@code tier_tiny}, {@code tier_big} and so on, none of which a casing stack's tag ever contains -- the
     * casing writes {@code casing=reinforced}, not {@code size=reinforced}. No predicate matched, so the casing
     * fell through to its base model, and the client saw the held reinforced casing render as a plain one while
     * the PLACED block rendered correctly (its model comes from the blockstate and consults no predicate).
     */
    private static final String[] SIZES_CASING = {
            "plain", "vent", "firebox", "gearbox", "reinforced", "circuitry"
    };

    /** Energy hatches have an extra `ultimate` step and no `vacuum` -- see {@code EnergyHatchData}. */
    private static final String[] SIZES_ENERGY = {
            "tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "ultimate"
    };

    









 
    private static final String[] SIZES_FLUID = {
            "tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "vacuum"
    };

    











 
    private static final String[] TYPES_PARALLEL_CONTROLLER = {
            "normal", "reinforced", "elite", "super", "ultimate"
    };

    /** The upgrade bus's five tiers -- {@code type=} over {@code UpgradeBusData}. See the controller's table. */
    private static final String[] TYPES_UPGRADE_BUS = {
            "normal", "reinforced", "elite", "super", "ultimate"
    };

    /**
     * Registers one item-model predicate per tier of {@code item}.
     *
     * <p>Each predicate is true only for a stack whose {@code BlockStateTag} names that tier, so they are
     * mutually exclusive and their ORDER IN THE MODEL JSON cannot matter. That is deliberate: vanilla stops
     * at the first override whose predicate matches, so an ordered list would be a rule to remember, and a
     * set of exclusive ones is a rule that cannot be got wrong.
     *
     * <p>The predicate reads the tag rather than the placed block, because the item is what is on screen:
     * an unplaced stack has no world state to consult.
     */
    private static void registerTierPredicates(net.minecraft.world.item.Item item, String[] tiers) {
        for (String tier : tiers) {
            net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation(
                    Modularmachinery.MODID, "tier_" + tier);
            net.minecraft.client.renderer.item.ItemProperties.register(item, id,
                    (stack, level, entity, seed) -> {
                        net.minecraft.nbt.CompoundTag tag = stack.getTagElement("BlockStateTag");
                        if (tag == null) {
                            return 0.0F;
                        }
                        // THE PROPERTY NAMES COME FROM BlockMachineComponent's ONE LIST, not from a copy here
                         
                        // `size=big`, `casing=reinforced` and `type=elite`, and those are the strings the tag
                        // holds.
                        //
                        // THE COPY THAT USED TO BE HERE IS WHY THIS FAMILY WAS UNREACHABLE: it named `size` and
                        // `casing` only. The buses and hatches write `size` and the casing writes `casing`, so
                        // those three families worked; the controllers, the upgrade bus and the smart interface
                        // write `type`, so EVERY one of their tiers fell through to the base model -- and the
                        // client reported exactly that shape twice, for the casing ("强化机器外壳手上模型和放下来
                        // 模型不一样") and then for these tiers, because the placed model comes from the
                        // blockstate and never consults a predicate at all.
                        for (String property : hellfirepvp.modularmachinery.common.block.BlockMachineComponent
                                .VARIANT_PROPERTY_NAMES) {
                            if (tier.equals(tag.getString(property))) {
                                return 1.0F;
                            }
                        }
                        return 0.0F;
                    });
        }
    }
    public static void openBlueprintPreview(hellfirepvp.modularmachinery.common.machine.DynamicMachine machine) {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new hellfirepvp.modularmachinery.client.screen.ScreenBlueprint(machine));
    }
}
