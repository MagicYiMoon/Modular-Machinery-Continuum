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
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.item.ItemConstructTool;
import hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;































































 
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SelectionBoxRenderer {

    /** Upstream L49's radius, squared: {@code distanceSq(...) <= 1024} is 32 blocks. */
    private static final double RENDER_RADIUS_SQUARED = 1024.0;

    /**
     * How many cells of a box are outlined per frame, beyond which only the cuboid is drawn.
     *
     * <p>4096 is a 16x16x16 box — larger than any machine this port's content ships, and small enough
     * that a per-frame walk of it is not measurable. The number is this port's own; upstream had no cap
     * because it drew one outline per block a player had clicked, and a player does not click 4096 blocks.
     */
    private static final long MAX_OUTLINED_CELLS = 4096L;

    private SelectionBoxRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Same stage as the preview, and for the same reason: AFTER_PARTICLES is the point Forge
        // documents for extra world-space geometry, after the world is drawn and before the HUD.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // THE TOOL GATE IS UPSTREAM'S (L41-L45): main hand first, and the off hand only when the main
        // hand is empty. Holding it is what makes the box appear at all, so a player with a selection
        // and a different item in hand is not drawn over.
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            held = player.getOffhandItem();
        }
        if (held.isEmpty() || !(held.getItem() instanceof ItemConstructTool)) {
            return;
        }

        PlayerStructureSelectionHelper.StructureSelection selection =
                PlayerStructureSelectionHelper.clientSelection;
        if (selection == null || selection.isEmpty()) {
            return;
        }
        BlockPos first = selection.getFirstCorner();
        BlockPos second = selection.getSecondCorner();

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        if (first == null || second == null) {
            // ONE CORNER IS A REACHABLE STATE -- the modifier arm can be used first, and the Refoxed's
            // `withSecond` allows exactly the same. Drawing that corner is what tells the player the click
            // registered, and it is the old per-block outline reused for the one block involved.
            outlineBlock(poseStack, lines, mc, first != null ? first : second);
        } else {
            BlockPos min = PlayerStructureSelectionHelper.boxMin(first, second);
            BlockPos max = PlayerStructureSelectionHelper.boxMax(first, second);
            // The +1 is what makes this a box of BLOCKS rather than a box of block centres: a corner at
            // x=4 and a corner at x=6 select the three columns 4,5,6, so the far face is at 7.0.
            AABB box = new AABB(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
             
            // the same convention StructurePreviewRenderer's parts use, and the one it once got wrong.
            if (box.distanceToSqr(Vec3.atCenterOf(player.getOnPos())) <= RENDER_RADIUS_SQUARED) {
                LevelRenderer.renderLineBox(poseStack, lines, box, 1.0F, 1.0F, 1.0F, 1.0F);
            }
            outlineInterior(poseStack, lines, mc, player, min, max);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Outlines the blocks inside the box that the export will carry.
     *
     * <p>Skipped while there are no cells marked — which cannot happen here, the caller has a corner —
     * and skipped entirely past {@link #MAX_OUTLINED_CELLS}. The cells come from the same
     * {@code boxMin}/{@code boxMax} pair the cuboid was drawn from, so the outlines cannot describe a
     * different volume than the wireframe around them.
     */
    private static void outlineInterior(PoseStack poseStack, VertexConsumer lines, Minecraft mc,
                                        LocalPlayer player, BlockPos min, BlockPos max) {
        if (PlayerStructureSelectionHelper.boxVolume(min, max) > MAX_OUTLINED_CELLS) {
            return;
        }
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    // Same radius rule as the single-corner case, and the same one upstream used: a
                    // selection the player has walked far away from is not drawn.
                    if (pos.distSqr(player.getOnPos()) > RENDER_RADIUS_SQUARED) {
                        continue;
                    }
                    if (mc.level.getBlockState(pos).isAir()) {
                        // AIR IS NOT A PART, and the outline is what says so: the export reads a block
                        // state per cell and writes only the cells that hold something. See
                        // PlayerStructureSelectionHelper.selectionAt.
                        continue;
                    }
                    // Dimmer than the cuboid, so the boundary stays readable through the contents.
                    outlineBlock(poseStack, lines, mc, pos, 1.0F, 1.0F, 1.0F, 0.35F);
                }
            }
        }
    }

    /** Draws one block's own outline at full white — the colour upstream used for the selection. */
    private static void outlineBlock(PoseStack poseStack, VertexConsumer lines, Minecraft mc,
                                     BlockPos pos) {
        outlineBlock(poseStack, lines, mc, pos, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws one block's own outline.
     *
     * <p>EACH BLOCK'S OWN SHAPE, for the reason §296 gives about the ghost: a fixed cube would be wrong
     * for every non-cube block, and the positions here are whatever the author's box contains — slabs,
     * stairs, fences, iron bars, all of them. `true` is upstream's {@code drawWhiteOutlineCubes}: the
     * shape's full outline.
     */
    private static void outlineBlock(PoseStack poseStack, VertexConsumer lines, Minecraft mc,
                                     BlockPos pos, float red, float green, float blue, float alpha) {
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.empty());
        if (shape.isEmpty()) {
            return;
        }
        LevelRenderer.renderVoxelShape(poseStack, lines, shape,
                pos.getX(), pos.getY(), pos.getZ(),
                red, green, blue, alpha, true);
    }
}
