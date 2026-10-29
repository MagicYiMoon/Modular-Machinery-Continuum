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
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.PreviewCellDecision;
import hellfirepvp.modularmachinery.common.machine.StructureMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/**
 * Source BlockArrayPreviewRenderHelper: incomplete cells, 0.125 inset / 0.75 scale, depth disabled,
 * ONE_MINUS_DST_COLOR / DST_COLOR blending. The modern batch adapter applies that state after each
 * model or BER's RenderType setup, while preserving its shader, textures and model layers.
 *
 * <p>Renders after world compositing, like source RenderWorldLastEvent. Red errors and detached BERs
 * are port extensions. World light remains the existing 1.20.1 adaptation; source combined light was 0.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructurePreviewRenderer {
    private static final ProjectionViewFrame VIEW_FRAME = new ProjectionViewFrame();
    private static final WorldProjectionBlockRenderer BLOCK_RENDERER = new WorldProjectionBlockRenderer();
    private static final MultiBufferSource.BufferSource GHOST_BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static final WorldProjectionRenderTypes GHOST_TYPES = new WorldProjectionRenderTypes();
    private static final MultiBufferSource GHOST_OUTPUT = GHOST_TYPES.wrap(GHOST_BUFFERS);
    private static final float GHOST_INSET = 0.125F, GHOST_SCALE = 0.75F;

    private StructurePreviewRenderer() { }

    static void clearSceneRenderCache() {
        VIEW_FRAME.clear();
        BLOCK_RENDERER.clear();
        GHOST_TYPES.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            StructurePreviewHelper.reset();
            hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper.clientSelection = null;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        StructurePreviewHelper.reset();
        hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper.clientSelection = null;
    }

    @SubscribeEvent
    public static void onPreviewTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) StructurePreviewHelper.tickPreview();
    }

    /** Cancel before vanilla sends use packets, so fixing a projection never places/uses an item. */
    @SubscribeEvent
    public static void onPreviewUse(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && StructurePreviewHelper.isPendingPlacement()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            StructurePreviewHelper.placePreview();
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (StructurePreviewHelper.isPreviewing()) VIEW_FRAME.capture(event.getPoseStack());
            else VIEW_FRAME.clear();
            return;
        }
        // AFTER_PARTICLES can still target an uncomposited Fabulous buffer. Source destination-color
        // blending needs the completed world color, after glass, particles and weather have been combined.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        // Forge GameRenderer passes its local projection/bobbing stack here, not the world-view
        // argument passed to LevelRenderer. Using it loses camera rotation (especially visible in F5).
        // Consume a copy from this frame's world stage, retaining camera mods' roll and view effects.
        PoseStack pose = VIEW_FRAME.take();
        if (pose == null || !StructurePreviewHelper.isPreviewing()) return;
        var projection = StructurePreviewHelper.getDefinition();
        BlockPos anchor = StructurePreviewHelper.getRenderPos();
        Minecraft mc = Minecraft.getInstance();
        if (projection == null || anchor == null || mc.level == null) return;
        if (mc.player != null && PreviewCellDecision.isOutOfRange(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), anchor)) {
            StructurePreviewHelper.reset();
            return;
        }
        var scene = StructurePreviewHelper.getScene();
        if (scene == null) return;

        var camera = event.getCamera().getPosition();
        var facing = StructurePreviewHelper.getFacing();
        long sampleTick = StructurePreviewHelper.getSampleTick();
        Integer selectedLayer = StructurePreviewHelper.getRenderLayer();
        BlockState placeholder = hellfirepvp.modularmachinery.common.registry.RegistryBlocks.blockCasing.get().defaultBlockState();
        List<BlockPos> wrongCells = new ArrayList<>();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        boolean highlightedBatch = false;
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            // Source uses alpha 1: ghost visibility comes from its blend equation, not a half-alpha tint.
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (var entry : projection.cells().entrySet()) {
                BlockPos relative = entry.getKey();
                if (selectedLayer != null && relative.getY() != selectedLayer) continue;
                BlockPos world = StructureMatcher.toWorldOffset(anchor, facing, relative.getX(), relative.getY(), relative.getZ());
                var decision = PreviewCellDecision.decide(mc.level, world, entry.getValue(), facing, placeholder, sampleTick);
                if (decision.cell() == PreviewCellDecision.PreviewCell.MATCHED) continue;
                BlockState state = decision.state() == null ? placeholder : decision.state();
                // ColorModulator is read at draw time, so finish every private batch before changing it.
                if (decision.highlight() != highlightedBatch) {
                    GHOST_BUFFERS.endBatch();
                    highlightedBatch = decision.highlight();
                }
                if (decision.highlight()) {
                    RenderSystem.setShaderColor(1, 0, 0, 1);
                    wrongCells.add(world);
                } else {
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                }
                pose.pushPose();
                try {
                    pose.translate(world.getX() + GHOST_INSET, world.getY() + GHOST_INSET, world.getZ() + GHOST_INSET);
                    pose.scale(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE);
                    BLOCK_RENDERER.render(scene, world, state, pose, GHOST_OUTPUT,
                            LevelRenderer.getLightColor(mc.level, world), event.getPartialTick());
                } finally { pose.popPose(); }
            }
        } finally {
            pose.popPose();
            try { GHOST_BUFFERS.endBatch(); }
            finally {
                WorldProjectionRenderTypes.restoreWorldState();
                RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            }
        }
        renderErrorBounds(pose, camera, wrongCells);
    }

    /** Port extension: retain an unambiguous red boundary in addition to the source-style ghost. */
    private static void renderErrorBounds(PoseStack pose, net.minecraft.world.phys.Vec3 camera, List<BlockPos> cells) {
        if (cells.isEmpty()) return;
        float[] previousColor = RenderSystem.getShaderColor().clone();
        pose.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            pose.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = GHOST_BUFFERS.getBuffer(RenderType.lines());
            for (BlockPos world : cells) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(world).inflate(0.005D), 1.0F, 0.1F, 0.1F, 1.0F);
            }
        } finally {
            pose.popPose();
            try { GHOST_BUFFERS.endBatch(); }
            finally { RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]); }
        }
    }
}
