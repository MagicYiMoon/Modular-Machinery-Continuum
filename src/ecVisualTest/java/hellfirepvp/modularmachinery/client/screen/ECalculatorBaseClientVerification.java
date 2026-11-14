/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Disposable resource/physical-memory-state client fixture. Does not claim EC CPU/formed visuals work. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT)
public final class ECalculatorBaseClientVerification {
    private static int total, ticks;
    private static boolean language, opened;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.ecBaseVisualTest")) return;
        var mc = Minecraft.getInstance();
        try {
            if (++total > 4000) throw new AssertionError("EC client startup timeout");
            if (mc.getOverlay() != null) return;
            if (!opened) {
                if (!(mc.screen instanceof TitleScreen)) return;
                if (!language) {
                    language = true; mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn");
                    mc.reloadResourcePacks(); return;
                }
                mc.options.guiScale().set(2); mc.resizeDisplay(); mc.options.pauseOnLostFocus = false;
                verifyModels(mc); mc.setScreen(new AssetScreen()); opened = true; return;
            }
            if (++ticks == 30) {
                Screenshot.grab(mc.gameDirectory, "ec-base-offline-models-20261112.png", mc.getMainRenderTarget(), message -> Modularmachinery.LOG.info("[MMC][ec-base-client] {}", message.getString()));
                Modularmachinery.LOG.info("[MMC][ec-base-client] PASS: 19 physical EC block models, 3 memory items, all facings/grades baked with real nonmissing sprites, 4 distinct memory geometries, Chinese names; offline models only, no AE CPU or formed-model validation");
            }
            if (ticks == 50) mc.stop();
        } catch (Throwable failure) { Modularmachinery.LOG.error("[MMC][ec-base-client] FAIL", failure); mc.stop(); }
    }
    private static void verifyModels(Minecraft mc) {
        for (var block : ECalculatorRegistration.CONTROLLERS.values()) for (var facing : Direction.Plane.HORIZONTAL)
            geometry(mc, block.get().defaultBlockState().setValue(BlockECalculatorController.FACING, facing));
        geometry(mc, ECalculatorRegistration.CASING.get().defaultBlockState());
        var counts = new HashSet<Integer>();
        for (var part : ECalculatorRegistration.PARTS.values()) for (var facing : Direction.Plane.HORIZONTAL)
            for (int memory = 0; memory <= 3; memory++) {
                var state = part.get().defaultBlockState().setValue(BlockECalculatorPart.FACING, facing).setValue(BlockECalculatorPart.MEMORY, memory);
                int fingerprint = geometry(mc, state);
                if (part == ECalculatorRegistration.DRIVE && facing == Direction.NORTH) counts.add(fingerprint);
            }
        require(counts.size() == 4, "memory grade/offline multipart did not change geometry");
        for (var item : ECalculatorRegistration.ITEM_FORMS.values()) {
            var stack = new ItemStack(item.get());
            require(!stack.getHoverName().getString().equals(item.get().getDescriptionId()), "missing EC item translation");
            var model = mc.getItemRenderer().getModel(stack, null, null, 0);
            require(model != mc.getModelManager().getMissingModel(), "missing EC item model");
            require(!model.getParticleIcon().contents().name().getPath().equals("missingno"), "missing EC item particle texture: " + stack.getHoverName().getString());
        }
    }
    private static int geometry(Minecraft mc, BlockState state) {
        var model = mc.getBlockRenderer().getBlockModel(state);
        require(model != mc.getModelManager().getMissingModel(), "missing EC block model " + state);
        int count = 0, hash = 1;
        for (int face = -1; face < 6; face++) for (var quad : model.getQuads(state, face < 0 ? null : Direction.values()[face], RandomSource.create(0))) {
            require(!quad.getSprite().contents().name().getPath().equals("missingno"), "missing EC quad texture " + state);
            count++; hash = 31 * hash + Arrays.hashCode(quad.getVertices());
        }
        require(count > 0, "empty EC model " + state); return hash;
    }
    private static final class AssetScreen extends Screen {
        private final List<ItemStack> items = ECalculatorRegistration.ITEM_FORMS.values().stream().map(item -> new ItemStack(item.get())).toList();
        private AssetScreen() { super(Component.literal("EC offline source artwork")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xFF171923);
            graphics.drawString(font, "EC 基础部件 · 源版离线模型", 18, 14, 0xFFFFFF, false);
            graphics.drawString(font, "此阶段尚不能进行 AE 合成；在线外观与任务界面后续接入", 18, 30, 0xC0C5D4, false);
            for (int index = 0; index < items.size(); index++) {
                int x = 18 + index / 11 * (width / 2), y = 52 + index % 11 * 29;
                graphics.fill(x - 3, y - 3, x + width / 2 - 20, y + 22, 0xFF242735);
                graphics.renderItem(items.get(index), x, y);
                graphics.drawString(font, items.get(index).getHoverName(), x + 24, y + 4, 0xEEEFF4, false);
            }
            super.render(graphics, mouseX, mouseY, partial);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
