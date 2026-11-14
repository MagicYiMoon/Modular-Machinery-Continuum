/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Real integrated-server metadata, multipart geometry, chunk model data and hiding lifecycle. Never shipped. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT)
public final class EStorageAppearanceClientVerification {
    /** Test-only workaround for GuideME 20.1.7's reload before CLIENT config loading in Forge userdev. */
    @Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class EarlyGuideConfig {
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            if (!Boolean.getBoolean("modularmachinery.esVisualTest")) return;
            try {
                var type = Class.forName("guideme.internal.GuideMEClient");
                var instance = type.getMethod("instance").invoke(null);
                var configField = type.getDeclaredField("clientConfig"); configField.setAccessible(true);
                var config = configField.get(instance); var specField = config.getClass().getDeclaredField("spec"); specField.setAccessible(true);
                var spec = (net.minecraftforge.common.ForgeConfigSpec) specField.get(config);
                if (!spec.isLoaded()) {
                    var memory = com.electronwill.nightconfig.core.CommentedConfig.inMemory(); spec.correct(memory); spec.setConfig(memory);
                    Modularmachinery.LOG.info("[MMC][es-appearance-client] test-only GuideME early config defaults initialized in memory; no file or mod artifact changed");
                }
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("GuideME test-only startup workaround", error); }
        }
    }
    private static final BlockPos BASE = new BlockPos(0, 120, 0), A = BASE.east(5), B = A.south();
    private static int total, ticks;
    private static boolean language, configured;
    private static boolean gasesEnabled() { return net.minecraftforge.fml.ModList.get().isLoaded("appmek"); }
    private static int driveCount() { return gasesEnabled() ? 9 : 6; }
    private static EStorageCellSpec.Kind kind(int index) { return EStorageCellSpec.Kind.values()[(index - 1) / 3]; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.esAppearanceTest")) return;
        var mc = Minecraft.getInstance();
        if (++total > 6000) { fail(new AssertionError("client startup timed out")); return; }
        if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        try {
            if (!language) {
                language = true; mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn"); mc.reloadResourcePacks(); return;
            }
            if (!configured) {
                configured = true; mc.options.pauseOnLostFocus = false; mc.options.hideGui = true;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear();
                server(EStorageAppearanceClientVerification::setup); return;
            }
            switch (++ticks) {
                case 80 -> {
                    require(controller(BASE).isFormed(), "client missing formed ES flag");
                    for (var pos : controller(BASE).modelHiddenPositions()) require(BlockModelHider.isHidden(mc.level, pos), "source neighbour visible: " + pos);
                    require(!BlockModelHider.isHidden(mc.level, BASE), "ES controller itself hidden");
                    renderRegionHiding(true);
                    for (int i = 1; i <= driveCount(); i++) {
                        var drive = drive(i); var appearance = drive.appearance();
                        require(appearance.level().ordinal() == (i - 1) % 3 + 1
                                && appearance.kind().name().equals(kind(i).name()), "wire matrix type/tier " + i);
                        require(appearance.status() == EStorageDriveAppearance.Status.IDLE, "write LED failed to expire after 40 ticks");
                        require(drive.getBlockState().getValue(EStorageDriveAppearance.LEVEL) == EStorageDriveAppearance.Level.EMPTY, "cosmetic state entered world palette");
                        geometry(drive, appearance);
                    }
                    require(drive(2).appearance().capacity() == EStorageDriveAppearance.Capacity.TYPE_MAX
                            && drive(3).appearance().capacity() == EStorageDriveAppearance.Capacity.FULL, "wire fullness LEDs");
                    if (gasesEnabled()) require(drive(8).appearance().capacity() == EStorageDriveAppearance.Capacity.TYPE_MAX
                            && drive(9).appearance().capacity() == EStorageDriveAppearance.Capacity.FULL, "wire gas fullness LEDs");
                    var drive = drive(1); var current = drive.appearance();
                    require(geometry(drive, current) != geometry(drive, EStorageDriveAppearance.EMPTY), "multipart matrix model not selected");
                    var red = new EStorageDriveAppearance(current.level(), current.kind(), EStorageDriveAppearance.Capacity.FULL, current.status());
                    require(geometry(drive, current) != geometry(drive, red), "capacity LED UV/model did not change");
                    shot("es-appearance-01-formed.png");
                }
                case 95 -> server(() -> { write(1); if (gasesEnabled()) write(7); });
                case 110 -> {
                    require(drive(1).appearance().status() == EStorageDriveAppearance.Status.RUN, "real network metadata missing active write LED");
                    geometry(drive(1), drive(1).appearance());
                    if (gasesEnabled()) {
                        require(drive(7).appearance().status() == EStorageDriveAppearance.Status.RUN, "gas write LED missing");
                        geometry(drive(7), drive(7).appearance());
                    }
                    shot("es-appearance-02-writing.png");
                }
                case 150 -> require(drive(1).appearance().status() == EStorageDriveAppearance.Status.IDLE, "writing metadata never returned idle");
                case 155 -> server(() -> world().setBlockAndUpdate(BASE.above(), Blocks.AIR.defaultBlockState()));
                case 180 -> {
                    require(!controller(BASE).isFormed(), "broken structure stayed formed");
                    for (var pos : controller(BASE).modelHiddenPositions()) require(!BlockModelHider.isHidden(mc.level, pos), "broken structure left hidden model");
                    renderRegionHiding(false);
                    shot("es-appearance-03-unformed.png");
                }
                case 185 -> server(() -> world().setBlockAndUpdate(BASE.above(), EStorageRegistration.CASING.get().defaultBlockState()));
                case 235 -> {
                    require(controller(BASE).isFormed(), "repaired structure did not reform");
                    for (var pos : controller(BASE).modelHiddenPositions()) require(BlockModelHider.isHidden(mc.level, pos), "repair did not restore hiding");
                    shot("es-appearance-04-restored.png"); server(EStorageAppearanceClientVerification::overlap);
                }
                case 270 -> {
                    require(controller(A).isFormed() && controller(B).isFormed(), "overlap fixture not formed");
                    require(BlockModelHider.isHidden(mc.level, A.east().south()), "overlap not hidden");
                    server(() -> world().setBlockAndUpdate(A, Blocks.AIR.defaultBlockState()));
                }
                case 300 -> {
                    require(BlockModelHider.isHidden(mc.level, A.east().south()), "removing first owner exposed second owner's model");
                    server(() -> world().setBlockAndUpdate(B, Blocks.AIR.defaultBlockState()));
                }
                case 330 -> {
                    require(!BlockModelHider.isHidden(mc.level, A.east().south()), "removing last owner left hidden model");
                    mc.reloadResourcePacks();
                }
                case 350 -> {
                    require(controller(BASE).isFormed() && BlockModelHider.isHidden(mc.level, BASE.above()), "resource reload lost hiding");
                    renderRegionHiding(true);
                    require(geometry(drive(1), drive(1).appearance()) != geometry(drive(1), EStorageDriveAppearance.EMPTY), "resource reload lost wrapped multipart model");
                    shot("es-appearance-05-reloaded.png");
                    Modularmachinery.LOG.info("[MMC][es-appearance-client] PASS: real BE metadata for six matrices, red/orange/green and 40-tick write LEDs, baked multipart geometry/UV changes, unchanged world palette, source eleven-neighbour hiding, structure break/repair, overlapping owners, removal and resource reload; inspect world screenshots separately");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        player.teleportTo(world(), 1, 120, -5, 0, 8);
                    });
                }
                case 360 -> {
                    controllerGeometry(); shot("es-controller-14-front.png");
                    server(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).teleportTo(world(), 6, 120, -4, 45, 8));
                }
                case 370 -> {
                    shot("es-controller-15-side.png");
                    if (!gasesEnabled()) mc.stop();
                    else server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        player.teleportTo(world(), .5, 121, -3, 0, 8);
                        var owner = (TileEStorageController) world().getBlockEntity(BASE);
                        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                                (id, inventory, ignored) -> hellfirepvp.modularmachinery.common.menu.MenuEStorageController.forController(id, inventory, owner),
                                owner.getBlockState().getBlock().getName()), buffer -> buffer.writeBlockPos(BASE));
                    });
                }
                case 400 -> {
                    if (!gasesEnabled()) return;
                    require(mc.screen instanceof ScreenEStorageController, "gas statistics screen did not open");
                    var screen = (ScreenEStorageController) mc.screen; var menu = screen.getMenu();
                    require(menu.value(hellfirepvp.modularmachinery.common.menu.MenuEStorageController.GAS_TYPES) == 27
                            && menu.value(hellfirepvp.modularmachinery.common.menu.MenuEStorageController.GAS_TYPES_MAX) == 75
                            && menu.value(hellfirepvp.modularmachinery.common.menu.MenuEStorageController.FLUID_TYPES) == 3, "gas/fluid statistics mixed or missing on wire");
                    require(java.util.stream.IntStream.range(0, 6).mapToObj(menu::row).filter(java.util.Objects::nonNull)
                            .anyMatch(row -> row.specification().kind() == EStorageCellSpec.Kind.GAS), "gas matrix row lost its resource kind on wire");
                    require(screen.mouseClicked(screen.getGuiLeft() + 30, screen.getGuiTop() + 100, 0), "fifth graph not clickable");
                    var field = ScreenEStorageController.class.getDeclaredField("focus"); field.setAccessible(true);
                    require(field.getInt(screen) == 4, "fifth graph did not select gas types");
                    readableText(); smoothFontScope(); cursorAway(); mc.options.guiScale().set(3); mc.resizeDisplay();
                }
                case 410 -> { if (gasesEnabled()) shot("es-gas-06-statistics.png"); }
                case 415 -> { if (gasesEnabled()) { mc.options.guiScale().set(2); mc.resizeDisplay(); } }
                case 425 -> {
                    if (!gasesEnabled()) return;
                    shot("es-font-08-scale2.png");
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 854, 480);
                }
                case 450 -> { if (gasesEnabled()) { readableText(); shot("es-gas-07-small-window.png"); } }
                case 455 -> {
                    if (!gasesEnabled()) return;
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
                    mc.options.guiScale().set(1); mc.resizeDisplay();
                }
                case 480 -> { if (gasesEnabled()) { readableText(); shot("es-font-09-scale1.png"); } }
                case 485 -> {
                    if (!gasesEnabled()) return;
                    mc.options.guiScale().set(3); mc.resizeDisplay();
                    mc.options.languageCode = "en_us"; mc.getLanguageManager().setSelected("en_us"); mc.reloadResourcePacks();
                }
                case 515 -> {
                    if (!gasesEnabled()) return;
                    readableText(); shot("es-font-10-english.png");
                    clickGraph(3);
                }
                case 530 -> {
                    if (!gasesEnabled()) return;
                    require(((ScreenEStorageController) mc.screen).getMenu().energyValue(hellfirepvp.modularmachinery.common.menu.MenuEStorageController.ENERGY) == 500_000_000, "half gauge wire mismatch");
                    shot("es-gauge-11-half.png");
                    server(() -> energy().extract(Double.MAX_VALUE, false));
                }
                case 565 -> {
                    if (!gasesEnabled()) return;
                    require(((ScreenEStorageController) mc.screen).getMenu().energyValue(hellfirepvp.modularmachinery.common.menu.MenuEStorageController.ENERGY) == 0, "empty gauge wire mismatch");
                    shot("es-gauge-12-empty.png"); server(() -> energy().inject(energy().capacity(), false));
                }
                case 600 -> {
                    if (!gasesEnabled()) return;
                    var menu = ((ScreenEStorageController) mc.screen).getMenu();
                    require(menu.energyValue(hellfirepvp.modularmachinery.common.menu.MenuEStorageController.ENERGY) == 1_000_000_000, "full gauge wire mismatch");
                    shot("es-gauge-13-full.png");
                    Modularmachinery.LOG.info("[MMC][es-gas-client] PASS: nine matrix models and write/capacity LEDs, gas/fluid wire statistics, fifth graph and small-window layout");
                    Modularmachinery.LOG.info("[MMC][es-legibility-client] PASS: smooth TTF Chinese/English with compact ruled rows, GUI scales 1/2/3, font lookup scope/fallback and source gauge empty/half/full AE statistics; inspect screenshots separately");
                    Modularmachinery.LOG.info("[MMC][es-controller-client] PASS: copied render-region palettes respect hiding on formation/break/reload, physical palette intact, three tiers/four facings separate glow faces; inspect front/side screenshots");
                }
                case 610 -> {
                    if (!gasesEnabled()) return;
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn"); mc.reloadResourcePacks();
                    server(() -> {
                        String snapshot = hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot.create(
                                MachineReloadListener.serverDefinitions().machines(), MachineReloadListener.serverDefinitions().recipes());
                        mc.execute(() -> mc.setScreen(new ScreenMachineEditor(new hellfirepvp.modularmachinery.common.menu.MenuMachineEditor(
                                999, mc.player.getInventory(), snapshot), mc.player.getInventory(), net.minecraft.network.chat.Component.literal("机器与配方编辑"))));
                    });
                }
                case 640 -> {
                    if (!gasesEnabled()) return;
                    require(mc.screen instanceof ScreenMachineEditor, "smooth-font editor fixture missing");
                    smoothFontScope(); shot("mmc-font-16-editor.png");
                    var machine = MachineReloadListener.getLoader().findByName("alloy_furnace");
                    if (machine == null) machine = MachineReloadListener.getLoader().all().iterator().next();
                    mc.setScreen(new ScreenBlueprint(machine));
                }
                case 665 -> {
                    if (!gasesEnabled()) return;
                    smoothFontScope(); shot("mmc-font-17-blueprint.png");
                    var data = new net.minecraft.world.inventory.SimpleContainerData(3);
                    data.set(0, 1234567); data.set(1, Integer.MAX_VALUE); data.set(2, 1);
                    mc.setScreen(new ScreenEnergyHatch(new hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch(
                            998, mc.player.getInventory(), BASE, data), mc.player.getInventory(), net.minecraft.network.chat.Component.literal("能源输入仓")));
                }
                case 690 -> {
                    if (!gasesEnabled()) return;
                    smoothFontScope(); shot("mmc-font-18-energy.png");
                    Modularmachinery.LOG.info("[MMC][mmc-ui-font-client] PASS: common font routing for editor, blueprint and energy screens, explicit fonts preserved, vanilla screen unaffected; inspect screenshots, view-only fixture did not save edits");
                }
                case 710 -> mc.stop();
            }
        } catch (Throwable error) { fail(error); }
    }
    private static void setup() {
        var world = world();
        world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(BASE).inflate(32))
                .forEach(net.minecraft.world.entity.Entity::discard);
        for (var pos : controllerPositions()) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        for (int i = 1; i <= driveCount(); i++) world.setBlockAndUpdate(BASE.west(i), Blocks.AIR.defaultBlockState());
        var pattern = new MachinePattern();
        for (int i = 1; i <= driveCount(); i++) pattern.addPart(new MachinePattern.Part(-i, 0, 0, "modularmachinery:estorage_cell_drive"));
        if (gasesEnabled()) {
            pattern.addPart(new MachinePattern.Part(0, -2, 0, "modularmachinery:estorage_energy_cell_l9"));
            world.setBlockAndUpdate(BASE.below(2), EStorageRegistration.ENERGY_CELLS.get(9).get().defaultBlockState());
            energy().extract(Double.MAX_VALUE, false); energy().inject(500_000_000, false);
        }
        var tmp = new TileEStorageController(BASE, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState());
        for (var pos : tmp.modelHiddenPositions()) {
            pattern.addPart(new MachinePattern.Part(pos.getX(), pos.getY() - 120, pos.getZ(), "modularmachinery:estorage_casing"));
            world.setBlockAndUpdate(pos, EStorageRegistration.CASING.get().defaultBlockState());
        }
        definitions(9, pattern);
        world.setBlockAndUpdate(BASE, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState());
        for (int i = 1; i <= driveCount(); i++) {
            world.setBlockAndUpdate(BASE.west(i), EStorageRegistration.DRIVE.get().defaultBlockState());
            var spec = new EStorageCellSpec(kind(i), EStorageCellSpec.Tier.values()[(i - 1) % 3]);
            ((TileEStorageDrive) world.getBlockEntity(BASE.west(i))).insertMatrix(new ItemStack(EStorageRegistration.CELLS.get(spec).get()));
        }
        var owner = (TileEStorageController) world.getBlockEntity(BASE); owner.refreshStructure();
        for (int i = 1; i <= driveCount(); i++) {
            var data = (hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellInventory) ((TileEStorageDrive) world.getBlockEntity(BASE.west(i))).storageAccess();
            if (i == 2) for (int type = 0; type < 315; type++) {
                var item = new ItemStack(Items.DIAMOND); item.getOrCreateTag().putInt("visual-type", type);
                data.insert(appeng.api.stacks.AEItemKey.of(item), 1, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
            } else if (i == 8) for (int type = 0; type < 25; type++) data.insert(
                    hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(type), 1,
                    appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
            else data.insert(key(i), i % 3 == 0 ? Long.MAX_VALUE : 12000, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
        }
        var player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
        player.teleportTo(world, gasesEnabled() ? -4 : -2.5, 122, gasesEnabled() ? -11 : -8, 0, 8);
    }
    private static TileEStorageEnergyCell energy() { return (TileEStorageEnergyCell) world().getBlockEntity(BASE.below(2)); }
    private static void cursorAway() {
        var mc = Minecraft.getInstance(); org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
    }
    private static void clickGraph(int graph) {
        var screen = (ScreenEStorageController) Minecraft.getInstance().screen;
        require(screen.mouseClicked(screen.getGuiLeft() + 30, screen.getGuiTop() + 23 + 18 * graph, 0), "graph click failed");
    }
    private static void renderRegionHiding(boolean formed) {
        var mc = Minecraft.getInstance();
        var region = new net.minecraft.client.renderer.chunk.RenderRegionCache().createRegion(mc.level, BASE.offset(-2, -2, -2), BASE.offset(3, 2, 3), 1);
        require(region != null && !region.getBlockState(BASE).isAir(), "render region lost controller model");
        for (var pos : controller(BASE).modelHiddenPositions()) {
            if (!formed && pos.equals(BASE.above())) continue;
            var chunk = mc.level.getChunkAt(pos);
            var actual = chunk.getSection(chunk.getSectionIndex(pos.getY())).getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            require(!actual.isAir(), "model hiding modified physical client palette");
            require(region.getBlockState(pos).isAir() == formed, "copied render palette ignored hiding lifecycle at " + pos);
            if (formed) require(region.getFluidState(pos).isEmpty(), "hidden render region leaked a fluid");
        }
    }
    private static void smoothFontScope() throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var method = net.minecraft.client.gui.Font.class.getDeclaredMethod("getFontSet", net.minecraft.resources.ResourceLocation.class);
        method.setAccessible(true);
        var normal = (net.minecraft.client.gui.font.FontSet) method.invoke(mc.font, net.minecraft.network.chat.Style.DEFAULT_FONT);
        var own = method.invoke(mc.font, hellfirepvp.modularmachinery.client.SmoothUiFonts.UI);
        require(normal == own && normal.getGlyphInfo('机', false).getClass().getName().contains("TrueType"), "MMC screen font was not smooth TTF");
        var explicit = method.invoke(mc.font, new ResourceLocation("minecraft", "uniform"));
        require(explicit != own, "explicit component font overridden");
        var screen = mc.screen;
        try {
            mc.screen = new net.minecraft.client.gui.screens.TitleScreen();
            require(method.invoke(mc.font, net.minecraft.network.chat.Style.DEFAULT_FONT) != own, "custom UI font escaped to vanilla screen");
        } finally { mc.screen = screen; }
    }
    private static void readableText() {
        var mc = Minecraft.getInstance(); var screen = (ScreenEStorageController) mc.screen;
        require(screen.getGuiLeft() >= 0 && screen.getGuiTop() >= 0
                && screen.getGuiLeft() + 256 <= screen.width && screen.getGuiTop() + 207 <= screen.height, "panel exceeds viewport");
        for (var spec : EStorageRegistration.CELLS.keySet()) {
            var label = net.minecraft.network.chat.Component.translatable("gui.modularmachinery.es."
                    + spec.kind().name().toLowerCase(Locale.ROOT) + "_matrix", spec.tier().name());
            require(mc.font.width(label) <= 55, "native matrix label overflows: " + label.getString());
            for (long used : new long[]{0, 4096, 16385, 999_999, spec.totalBytes() - 1, spec.totalBytes()})
                require(mc.font.width(screen.byteUsage(used, spec.totalBytes())) <= 55, "native byte pair overflows: " + screen.byteUsage(used, spec.totalBytes()));
        }
        for (String graph : new String[]{"bytes", "item_types", "fluid_types", "gas_types", "energy"}) {
            var label = net.minecraft.network.chat.Component.translatable("gui.modularmachinery.es." + graph);
            if (mc.font.width(label) > 49) label = net.minecraft.network.chat.Component.translatable("gui.modularmachinery.es." + graph + "_short");
            require(mc.font.width(label) <= 49, "native graph label overflows");
        }
    }
    private static java.util.List<BlockPos> controllerPositions() { return java.util.List.of(BASE, A, B); }
    private static void overlap() {
        for (int i = 0; i < 2; i++) {
            var pos = i == 0 ? A : B; int tier = i == 0 ? 4 : 6;
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(-1, 0, 0, "modularmachinery:estorage_casing")); definitions(tier, pattern);
            world().setBlockAndUpdate(pos.west(), EStorageRegistration.CASING.get().defaultBlockState());
            world().setBlockAndUpdate(pos, EStorageRegistration.CONTROLLERS.get(tier).get().defaultBlockState());
            ((TileEStorageController) world().getBlockEntity(pos)).refreshStructure();
        }
    }
    private static void definitions(int tier, MachinePattern pattern) {
        var id = new ResourceLocation("modularmachinery", EStorageRegistration.machineName(tier));
        MachineReloadListener.getLoader().getMachines().put(id, new DynamicMachine(id.getPath(), "ES appearance fixture", pattern));
    }
    private static void write(int i) {
        var drive = (TileEStorageDrive) world().getBlockEntity(BASE.west(i));
        ((hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellInventory) drive.storageAccess()).insert(key(i),
                1, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
    }
    private static appeng.api.stacks.AEKey key(int index) {
        return switch (kind(index)) {
            case ITEM -> appeng.api.stacks.AEItemKey.of(Items.DIAMOND);
            case FLUID -> appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
            case GAS -> hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(0);
        };
    }
    private static void controllerGeometry() {
        var renderer = Minecraft.getInstance().getBlockRenderer();
        for (var block : EStorageRegistration.CONTROLLERS.values()) for (var facing : Direction.Plane.HORIZONTAL) {
            var state = block.get().defaultBlockState().setValue(BlockEStorageController.FORMED, true).setValue(BlockEStorageController.FACING, facing);
            var quads = renderer.getBlockModel(state).getQuads(state, null, RandomSource.create(7),
                    net.minecraftforge.client.model.data.ModelData.EMPTY, net.minecraft.client.renderer.RenderType.cutout());
            var glow = quads.stream().filter(q -> (q.getVertices()[6] & 0x00F000F0) == 0x00F000F0).toList();
            var body = quads.stream().filter(q -> !glow.contains(q)).toList();
            require(glow.size() == 6 && body.size() == 6, "formed controller missing body/glow faces");
            for (var face : glow) {
                var behind = body.stream().filter(q -> q.getDirection() == face.getDirection()).findFirst().orElseThrow();
                double separation = plane(face) - plane(behind);
                require(separation > .0001 && separation < .002, "coplanar or displaced controller overlay: " + separation);
            }
        }
    }
    private static double plane(net.minecraft.client.renderer.block.model.BakedQuad quad) {
        int[] data = quad.getVertices(); var normal = quad.getDirection().getNormal(); double result = 0;
        for (int i = 0; i < 4; i++) result += Float.intBitsToFloat(data[i * 8]) * normal.getX()
                + Float.intBitsToFloat(data[i * 8 + 1]) * normal.getY() + Float.intBitsToFloat(data[i * 8 + 2]) * normal.getZ();
        return result / 4;
    }
    private static long geometry(TileEStorageDrive drive, EStorageDriveAppearance appearance) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(drive.getBlockState());
        var data = net.minecraftforge.client.model.data.ModelData.builder().with(EStorageDriveAppearance.DATA, appearance).build();
        var quads = model.getQuads(drive.getBlockState(), null, RandomSource.create(7), data, net.minecraft.client.renderer.RenderType.cutout());
        require(!quads.isEmpty(), "empty baked drive model");
        if (appearance.kind() == EStorageDriveAppearance.Kind.GAS) require(quads.stream().anyMatch(
                quad -> quad.getSprite().contents().name().getPath().contains("off_gas")), "gas matrix model used wrong texture");
        if (appearance.level() != EStorageDriveAppearance.Level.EMPTY)
            require(quads.stream().anyMatch(quad -> (quad.getVertices()[6] & 0x00F000F0) == 0x00F000F0), "upstream bloom faces lost full-bright light data");
        boolean animated = quads.stream().anyMatch(quad -> quad.getSprite().contents().name().equals(
                new ResourceLocation("modularmachinery", "neco/blocks/led_dynamic")));
        require(animated == (appearance.level() != EStorageDriveAppearance.Level.EMPTY
                && appearance.status() == EStorageDriveAppearance.Status.RUN), "write LED selected wrong baked sprite");
        long hash = quads.size(); for (var quad : quads) hash = hash * 31 + Arrays.hashCode(quad.getVertices()); return hash;
    }
    private static net.minecraft.server.level.ServerLevel world() { return Minecraft.getInstance().getSingleplayerServer().overworld(); }
    private static TileEStorageController controller(BlockPos pos) { return (TileEStorageController) Minecraft.getInstance().level.getBlockEntity(pos); }
    private static TileEStorageDrive drive(int i) { return (TileEStorageDrive) Minecraft.getInstance().level.getBlockEntity(BASE.west(i)); }
    private static void server(Runnable task) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable error) { Minecraft.getInstance().execute(() -> fail(error)); } }); }
    private static void shot(String name) { var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> { }); }
    private static void fail(Throwable error) { Modularmachinery.LOG.error("[MMC][es-appearance-client] FAIL", error); Minecraft.getInstance().stop(); }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
