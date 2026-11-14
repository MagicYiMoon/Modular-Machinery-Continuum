/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuEStorageController;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real integrated server, native menu packets and rendered screenshots. Never shipped. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT)
public final class EStorageClientVerification {
    private static int ticks, total;
    private static boolean configured, languageSwitched;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.esVisualTest") || Boolean.getBoolean("modularmachinery.esAppearanceTest")) return;
        var mc = Minecraft.getInstance();
        if (++total > 6000) { Modularmachinery.LOG.error("[MMC][es-client] FAIL: startup timed out"); mc.stop(); return; }
        if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        try {
            if (EStorageCompletionClientVerification.active()) { EStorageCompletionClientVerification.tick(); return; }
            if (!languageSwitched) {
                languageSwitched = true;
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                mc.reloadResourcePacks();
                return;
            }
            if (!configured) {
                configured = true; mc.options.pauseOnLostFocus = false;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.getToasts().clear();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
                mc.getSingleplayerServer().execute(() -> setup(mc));
                return;
            }
            if (!(mc.screen instanceof ScreenEStorageController)) {
                if (total > 1800) throw new AssertionError("ES menu never opened"); return;
            }
            ticks++;
            switch (ticks) {
                case 40 -> {
                    require(menu().value(MenuEStorageController.MATRICES) == 9 && menu().value(MenuEStorageController.PAGES) == 2, "live server stats missing");
                    require(menu().energyValue(MenuEStorageController.ENERGY) == 987654321.125, "fractional energy wire mismatch");
                    bounds(); shot("es-01-scale3-overview.png"); click(110, net.minecraftforge.fml.ModList.get().isLoaded("appmek") ? 101 : 84);
                    textures();
                }
                case 60 -> { shot("es-02-energy.png"); screen().mouseScrolled(screen().getGuiLeft() + 200, screen().getGuiTop() + 60, -1); }
                case 85 -> {
                    require(menu().value(MenuEStorageController.PAGE) == 1 && menu().value(MenuEStorageController.ROWS) == 3, "real wheel page action failed");
                    shot("es-03-last-page.png"); mc.options.guiScale().set(2); mc.resizeDisplay();
                }
                case 110 -> { bounds(); shot("es-04-scale2.png"); org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 854, 480); }
                case 140 -> {
                    bounds(); shot("es-05-small-window.png");
                    Modularmachinery.LOG.info("[MMC][es-client] PASS: actual integrated server menu, 9 matrices/2 pages, fractional AE sync, graph click, wheel page request/response, scales 3/2, 854x480 window and source inventory bounds; inspect screenshots separately");
                    if (Boolean.getBoolean("modularmachinery.esFinishVisualTest")) EStorageCompletionClientVerification.begin();
                }
                case 160 -> mc.stop();
            }
        } catch (Throwable failure) { Modularmachinery.LOG.error("[MMC][es-client] FAIL", failure); mc.stop(); }
    }
    private static void setup(Minecraft mc) {
        try {
            var server = mc.getSingleplayerServer(); var world = server.overworld();
            var player = server.getPlayerList().getPlayers().get(0); var pos = new BlockPos(0, 120, 0);
            for (int i = 0; i <= 10; i++) world.setBlockAndUpdate(pos.east(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var pattern = new MachinePattern();
            for (int i = 1; i <= 9; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "modularmachinery:estorage_cell_drive"));
            pattern.addPart(new MachinePattern.Part(10, 0, 0, "modularmachinery:estorage_energy_cell_l9"));
            var id = new ResourceLocation("modularmachinery", EStorageRegistration.machineName(9));
            MachineReloadListener.getLoader().getMachines().put(id, new DynamicMachine(id.getPath(), "ES visual fixture", pattern));
            world.setBlockAndUpdate(pos, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH));
            for (int i = 1; i <= 9; i++) world.setBlockAndUpdate(pos.east(i), EStorageRegistration.DRIVE.get().defaultBlockState());
            world.setBlockAndUpdate(pos.east(10), EStorageRegistration.ENERGY_CELLS.get(9).get().defaultBlockState());
            var owner = (TileEStorageController) world.getBlockEntity(pos); owner.refreshStructure();
            for (int i = 1; i <= 9; i++) {
                var kind = net.minecraftforge.fml.ModList.get().isLoaded("appmek") ? EStorageCellSpec.Kind.values()[(i-1)/3]
                        : i % 3 == 0 ? EStorageCellSpec.Kind.FLUID : EStorageCellSpec.Kind.ITEM;
                var spec = new EStorageCellSpec(kind, EStorageCellSpec.Tier.values()[i % 3]);
                var drive = (TileEStorageDrive) world.getBlockEntity(pos.east(i));
                drive.insertMatrix(new ItemStack(EStorageRegistration.CELLS.get(spec).get()));
                owner.refreshStructure();
                var data = (hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellInventory) drive.storageAccess();
                appeng.api.stacks.AEKey key = spec.kind() == EStorageCellSpec.Kind.GAS ? hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(0)
                        : spec.kind() == EStorageCellSpec.Kind.ITEM ? appeng.api.stacks.AEItemKey.of(Items.DIAMOND) : appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
                data.insert(key, i * 30_000_000L, appeng.api.config.Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty());
            }
            ((TileEStorageEnergyCell) world.getBlockEntity(pos.east(10))).inject(987654321.125, false);
            player.teleportTo(world, .5, 121, .5, 180, 0);
            net.minecraftforge.network.NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (window, inventory, ignored) -> MenuEStorageController.forController(window, inventory, owner),
                    Component.literal("ES S9")), buffer -> buffer.writeBlockPos(pos));
        } catch (Throwable failure) { Modularmachinery.LOG.error("[MMC][es-client] FAIL: server setup", failure); mc.execute(mc::stop); }
    }
    private static ScreenEStorageController screen() { return (ScreenEStorageController) Minecraft.getInstance().screen; }
    private static MenuEStorageController menu() { return screen().getMenu(); }
    private static void click(int x, int y) { screen().mouseClicked(screen().getGuiLeft() + x, screen().getGuiTop() + y, 0); }
    private static void bounds() {
        var screen = screen();
        require(screen.getGuiLeft() >= 0 && screen.getGuiTop() >= 0
                && screen.getGuiLeft() + MenuEStorageController.WIDTH <= screen.width
                && screen.getGuiTop() + MenuEStorageController.HEIGHT <= screen.height, "panel outside screen");
        for (var slot : menu().slots) require(slot.x >= 0 && slot.y >= 0 && slot.x + 16 < MenuEStorageController.WIDTH && slot.y + 16 < MenuEStorageController.HEIGHT, "slot outside panel");
    }
    private static void shot(String name) { var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), result -> Modularmachinery.LOG.info("[MMC][es-client] {}", result.getString())); }
    private static void textures() {
        var mc = Minecraft.getInstance();
        var atlas = mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        var resources = mc.getResourceManager().listResources("textures/neco", id -> id.getPath().endsWith(".png")
                && (id.getPath().startsWith("textures/neco/blocks/") || id.getPath().startsWith("textures/neco/items/")));
        require(resources.size() >= 40, "ES texture resources missing");
        for (var file : resources.keySet()) {
            var path = file.getPath();
            var sprite = new ResourceLocation(file.getNamespace(), path.substring(9, path.length() - 4));
            require(atlas.apply(sprite).contents().name().equals(sprite), "ES sprite absent from block atlas: " + sprite);
        }
        Modularmachinery.LOG.info("[MMC][es-client] atlas PASS: {} ES block/item sprites resolve in the actual baked atlas", resources.size());
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
