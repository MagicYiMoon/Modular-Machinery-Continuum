/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.world;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import hellfirepvp.modularmachinery.common.block.BlockEnergyOutputPort;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputPort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;

/** Uses the supplied pose/buffer; no global GL state or client-side world ray scans. */
public final class EnergyPortRenderer implements BlockEntityRenderer<TileEnergyOutputPort> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("modularmachinery", "textures/effect/energy_beam.png");
    @Override public void render(TileEnergyOutputPort tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int distance = tile.visualDistance();
        if (distance <= 1 || tile.getLevel() == null) return;
        Direction facing = tile.getBlockState().getValue(BlockEnergyOutputPort.FACING);
        boolean running = tile.visualRate() > 0;
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(switch (facing) {
            case UP -> Axis.XP.rotationDegrees(0);
            case DOWN -> Axis.XP.rotationDegrees(180);
            case NORTH -> Axis.XP.rotationDegrees(-90);
            case SOUTH -> Axis.XP.rotationDegrees(90);
            case EAST -> Axis.ZP.rotationDegrees(-90);
            case WEST -> Axis.ZP.rotationDegrees(90);
        });
        // Beam starts at the port's front surface and ends at the receiver's near surface.
        float scroll = running ? -((tile.getLevel().getGameTime() % 1000 + partialTick) / 15f) : 0;
        var buffer = buffers.getBuffer(RenderType.beaconBeam(TEXTURE, true));
        for (int n = 0; n < 4; n++) {
            pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(45 * n));
            var matrix = pose.last(); float width = running ? .13f : .08f;
            vertex(buffer, matrix, -width, .5f, 0, 0, scroll, running);
            vertex(buffer, matrix, width, .5f, 0, 1, scroll, running);
            vertex(buffer, matrix, width, distance - .5f, 0, 1, scroll + distance / 2f, running);
            vertex(buffer, matrix, -width, distance - .5f, 0, 0, scroll + distance / 2f, running);
            pose.popPose();
        }
        pose.popPose();
        if (running) {
            pose.pushPose(); pose.translate(.5, 1.3, .5);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-.015f, -.015f, .015f);
            String text = formatRate(tile.visualRate()); var font = Minecraft.getInstance().font;
            font.drawInBatch(text, -font.width(text) / 2f, 0, 0xFFE3F5FF, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0x50000000, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
    private static void vertex(VertexConsumer buffer, PoseStack.Pose matrix, float x, float y, float z, float u, float v, boolean running) {
        buffer.vertex(matrix.pose(), x, y, z).color(running ? 130 : 110, running ? 210 : 135, 255, running ? 190 : 75)
                .uv(u, v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(matrix.normal(), 0, 0, 1).endVertex();
    }
    public static String formatRate(int rate) {
        String unit = rate >= 1_000_000_000 ? "G" : rate >= 1_000_000 ? "M" : rate >= 1000 ? "k" : "";
        double divisor = unit.equals("G") ? 1e9 : unit.equals("M") ? 1e6 : unit.equals("k") ? 1e3 : 1;
        return (unit.isEmpty() ? Integer.toString(rate) : String.format(Locale.ROOT, "%.2f", rate / divisor)) + unit + " FE/t";
    }
    @Override public boolean shouldRenderOffScreen(TileEnergyOutputPort tile) { return tile.visualDistance() > 1; }
    @Override public int getViewDistance() { return 256; }
}
