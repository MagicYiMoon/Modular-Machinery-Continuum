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

/**
 * Draws the authoring selection: <b>the cuboid between the two corners</b>, plus the blocks inside it that
 * the export will actually carry.
 *
 * <p><b>Ported from upstream {@code hellfirepvp.modularmachinery.client.util.SelectionBoxRenderHelper}
 * (86 lines)</b>, which was read in full. Its draw half is upstream L35-L56: on the last render event,
 * return unless a player exists, take the held stack (main hand, else off hand), return unless it is the
 * construct tool, and then draw the selection — filtered to positions within a squared radius of 32
 * blocks, which is upstream's {@code pos.distanceSq(player.getPosition()) <= 1024}.
 *
 * <h2>What changed when the selection became two corners (PLAN 655.1, 655.2)</h2>
 *
 * <p><b>The Refoxed has no renderer at all.</b> Its {@code MultiblockDetectorItem} carries no rendering
 * code; its interface is a three-line tooltip, so "box selection" there means <i>two corners define a
 * cuboid</i>, not <i>a cuboid is drawn</i>. That is the reason this class is kept rather than replaced:
 * there is nothing in the Refoxed for it to conflict with, and showing the volume is strictly more than
 * showing two coordinates. What changed is what it draws: the cuboid first, and the corner block on its
 * own while only one is marked.
 *
 * <p><b>Why the cuboid matters more than the old per-position outlines did.</b> With per-block
 * accumulation the boxes <i>were</i> the selection — one box per clicked block, and the set of them was
 * the whole answer. Now the volume between the corners is the selection and it includes cells nobody
 * clicked, so a player who saw only the two corners would have no way to tell how far the box reaches,
 * or which of the blocks inside it will end up in the file (air is skipped — see
 * {@code PlayerStructureSelectionHelper.selectionAt}). Both of those are drawn below.
 *
 * <h2>Why the interior is drawn at all, and why it is bounded</h2>
 *
 * <p>The interior outlines are the export's contents, not decoration: a block that is there will be a
 * part of the machine, and a cell of air will not. That is the one thing a wireframe cannot say.
 * <b>It is capped at {@link #MAX_OUTLINED_CELLS}</b> because the volume is now unbounded by anything the
 * player's clicks imply — this runs every frame, and walking a million cells per frame to draw blocks
 * nobody can see through each other would be a frame-time bug rather than feedback. Past the cap the
 * cuboid is still drawn, which is the part that says how big the selection is.
 *
 * <h2>What it reuses rather than writing again</h2>
 *
 * <p><b>The render stage, the camera translation and the buffer are {@link StructurePreviewRenderer}'s,
 * deliberately.</b> This port has had exactly one in-world renderer since §181, and the two things it
 * draws — a previewed machine's cells and an author's selection — are the same kind of thing at the same
 * stage. A second implementation of "translate by the camera, get {@code RenderType.lines()}, batch it"
 * would be a second place for the {@code pushPose}/{@code -cam} convention to be got wrong; §296 is the
 * round where getting it wrong drew every ghost at the world origin and a player standing at the machine
 * saw nothing at all. The cuboid itself is {@code LevelRenderer.renderLineBox}, which is vanilla's own
 * box outline and needs no shape building — the corners of a big box would otherwise cost a
 * {@code VoxelShape} allocation per frame for geometry that is twelve lines.
 *
 * <p><b>The two purges are {@link StructurePreviewRenderer}'s as well, and that is the more important
 * reuse.</b> Upstream cleared the client selection from two places — {@code ClientDisconnectionFromServerEvent}
 * (upstream L73-L77) and {@code WorldEvent.Unload} (upstream L79-L84) — and this port already has exactly
 * those two, with the dist check that a dedicated server needs, because {@code StructurePreviewHelper}
 * had the same leak: three statics that survived a world change, so a preview started in one world could
 * render in the next (§350-§361). Adding a third and fourth subscriber for the same two events would
 * leave two places to fix the next time either is wrong. So the clears are in that class's handlers,
 * next to the preview's own, and this class has none.
 *
 * <h2>Unverified, and named rather than implied</h2>
 *
 * <p><b>This port's harness is a dedicated server and cannot render.</b> The class compiles, it is
 * dist-gated, and its geometry comes from the two corners rather than from a guessed cube — but whether
 * the box appears where a player expects is a question only a client can answer, and it is recorded as
 * such rather than asserted. Nothing in the self-test can reach this class.
 */
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
            // Absolute coordinates, because the pose stack is translated only by the CAMERA (PLAN 296) --
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
