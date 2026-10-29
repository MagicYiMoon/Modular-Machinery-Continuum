/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's isolated world-scene blueprint preview.
 */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.geo.ControllerGeoRenderer;
import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.base.ColorableMachineTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;
import java.util.*;

/** A read-only scene. Its block entities are never attached to, registered in, or ticked by ClientLevel. */
public final class BlueprintPreviewScene implements BlockAndTintGetter, BlueprintMaterialState.Source, AutoCloseable {
    private final DynamicMachine machine;
    private final BlueprintSceneDefinition definition;
    private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
    private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
    private final ControllerGeoRenderer geoRenderer = new ControllerGeoRenderer();
    // Never drain unrelated GUI/world geometry using the preview's camera, scissor or depth state.
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(65536));
    private final Set<Block> failedEntities = new HashSet<>();
    private final Set<Block> failedRenderers = new HashSet<>();
    private final Set<Block> failedFallbacks = new HashSet<>();
    private BlueprintPreviewLevel previewLevel;
    private long lastCycle = Long.MIN_VALUE, modelRevision = Long.MIN_VALUE;
    private boolean formed;
    private long revision;

    public BlueprintPreviewScene(DynamicMachine machine, MachinePattern pattern) {
        this(machine, new BlueprintSceneDefinition(machine, pattern,
                MachineReloadListener.getLoader().getVariables(), MachineReloadListener.getLoader().getResolver()));
    }

    public BlueprintPreviewScene(DynamicMachine machine, BlueprintSceneDefinition definition) {
        this.machine = machine;
        this.definition = definition;
    }

    public BlueprintSceneDefinition definition() { return definition; }
    public MultiBufferSource.BufferSource buffers() { return buffers; }
    @Override public long revision() { return revision; }
    @Override public ItemStack materialStack(BlockPos pos, BlockState state) {
        return BlueprintPreviewDetails.materialStack(this, pos, state);
    }
    @Override public List<ItemStack> materialStacks(BlockPos pos) { return BlueprintPreviewDetails.materialStacks(this, pos); }

    public void update(long tick, boolean simulateFormed) {
        var client = Minecraft.getInstance();
        if (previewLevel == null && client.level != null) {
            previewLevel = new BlueprintPreviewLevel(client.level,
                    client.player == null ? BlockPos.ZERO : client.player.blockPosition(), states, entities);
            entities.values().forEach(entity -> entity.setLevel(previewLevel));
        }
        // Pausing replaceable candidates must not freeze a banner's waving animation.
        if (previewLevel != null) previewLevel.setPreviewTime(client.level == null ? tick : client.level.getGameTime());
        if (lastCycle == tick / 40 && formed == simulateFormed && modelRevision == GeoMachineModelRegistry.revision()) return;
        if (modelRevision != GeoMachineModelRegistry.revision()) {
            failedEntities.clear();
            failedRenderers.clear();
            failedFallbacks.clear();
        }
        boolean rebuildEntities = formed != simulateFormed || modelRevision != GeoMachineModelRegistry.revision();
        lastCycle = tick / 40;
        modelRevision = GeoMachineModelRegistry.revision();
        formed = simulateFormed;
        states.clear();
        // Reuse unchanged entities so animations do not restart each time the candidate cycle advances.
        Map<BlockPos, BlockEntity> previous = rebuildEntities ? Map.of() : new HashMap<>(entities);
        entities.clear();
        states.putAll(definition.sampleStates(tick, formed));
        states.forEach((pos, state) -> {
            var cell = definition.cells().get(pos);
            BlockEntity entity = previous.get(pos);
            if (entity == null || !entity.getBlockState().equals(state)) {
                entity = createEntity(pos, state, cell.part());
            }
            if (entity != null) {
                if (formed && entity instanceof ColorableMachineTile colorable) colorable.setMachineColor(machine.getDefinedColor());
                entities.put(pos, entity);
            }
        });
        revision++;
    }

    private BlockEntity createEntity(BlockPos pos, BlockState state, MachinePattern.Part part) {
        if (failedEntities.contains(state.getBlock())) return null;
        try {
            if (!pos.equals(BlockPos.ZERO) && previewLevel != null) {
                return previewLevel.createBlockEntity(pos, state, part.previewNbt());
            }
            BlockEntity entity = pos.equals(BlockPos.ZERO) ? new PreviewController(state)
                    : state.getBlock() instanceof EntityBlock block ? block.newBlockEntity(pos, state) : null;
            // This is a private Level, never ClientLevel; do not register the entity or invoke onLoad/tickers.
            if (entity != null && previewLevel != null) entity.setLevel(previewLevel);
            if (entity != null && part.previewNbt() != null) entity.load(part.previewNbt().copy());
            return entity;
        } catch (RuntimeException | LinkageError failure) {
            if (failedEntities.add(state.getBlock())) Modularmachinery.LOG.warn(
                    "[MMCE] cannot create detached blueprint block entity for {}", state, failure);
            return null;
        }
    }

    public List<BlockPos> visiblePositions(boolean allLayers, int layer) {
        return BlueprintSceneDefinition.visiblePositions(states, allLayers, layer, formed, machine.isHideComponentsWhenFormed());
    }

    public void render(PoseStack pose, MultiBufferSource.BufferSource buffers, boolean allLayers,
                       int layer, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        BlockAndTintGetter renderView = allLayers ? this : new BlueprintLayerView(this, layer);
        for (BlockPos pos : visiblePositions(allLayers, layer)) {
            BlockState state = getBlockState(pos);
            pose.pushPose();
            try {
                pose.translate(pos.getX(), pos.getY(), pos.getZ());
                renderModel(pos, state, pose, buffers, renderView);
                BlockEntity entity = entities.get(pos);
                boolean rendered = entity != null && !(entity instanceof TileController)
                        && renderBlockEntity(entity, partialTick, pose, buffers);
                // World-aware BERs preserve facing, connections and preview NBT. Item rendering is only a fallback.
                if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED && !rendered) {
                    renderItemFallback(state, pose, buffers);
                }
            } finally {
                pose.popPose();
            }
            if (!state.getFluidState().isEmpty()) {
                pose.pushPose();
                try {
                    pose.translate(pos.getX() - (pos.getX() & 15), pos.getY() - (pos.getY() & 15),
                            pos.getZ() - (pos.getZ() & 15));
                    // Terrain render types use chunk shader uniforms, which are stale inside a GUI.
                    RenderType fluidType = PreviewFluidVertexConsumer.renderType(state.getFluidState());
                    mc.getBlockRenderer().renderLiquid(pos, renderView,
                            new PreviewFluidVertexConsumer(buffers.getBuffer(fluidType), pose.last()),
                            state, state.getFluidState());
                } finally { pose.popPose(); }
            }
        }
        if (formed && (allLayers || layer == 0) && entities.get(BlockPos.ZERO) instanceof TileController controller) {
            geoRenderer.render(controller, partialTick, copyPose(pose), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        buffers.endBatch();
    }

    private void renderModel(BlockPos pos, BlockState state, PoseStack pose, MultiBufferSource buffers,
                             BlockAndTintGetter renderView) {
        if (state.getRenderShape() != RenderShape.MODEL) return;
        pose.pushPose();
        try {
            var offset = state.getOffset(renderView, pos);
            pose.translate(offset.x, offset.y, offset.z);
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            ModelData data = entities.containsKey(pos) ? entities.get(pos).getModelData() : ModelData.EMPTY;
            data = model.getModelData(renderView, pos, state, data);
            RandomSource random = RandomSource.create(42);
            for (RenderType layerType : model.getRenderTypes(state, random, data)) {
                VertexConsumer consumer = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layerType, true));
                for (Direction side : Direction.values()) {
                    BlockPos neighbor = pos.relative(side);
                    if (!Block.shouldRenderFace(state, renderView, pos, side, neighbor)) continue;
                    random.setSeed(42);
                    renderQuads(pose, consumer, model.getQuads(state, side, random, data, layerType), state, pos);
                }
                random.setSeed(42);
                renderQuads(pose, consumer, model.getQuads(state, null, random, data, layerType), state, pos);
            }
        } finally { pose.popPose(); }
    }

    private <T extends BlockEntity> boolean renderBlockEntity(T entity, float partialTick,
                                                            PoseStack pose, MultiBufferSource buffers) {
        Block block = entity.getBlockState().getBlock();
        if (failedRenderers.contains(block) || !entity.getType().isValid(entity.getBlockState())) return false;
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) return false;
        try {
            // Bypass world-camera distance culling and light lookup; the blueprint supplies its own transform and light.
            renderer.render(entity, partialTick, copyPose(pose), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (failedRenderers.add(block)) Modularmachinery.LOG.warn(
                    "[MMCE] cannot render blueprint block entity for {}; using item fallback where available", block, failure);
            return false;
        }
    }

    private void renderItemFallback(BlockState state, PoseStack pose, MultiBufferSource buffers) {
        if (failedFallbacks.contains(state.getBlock())) return;
        // A third-party renderer may also be used by the block's item, so guard this fallback separately.
        try {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, copyPose(pose), buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException | LinkageError failure) {
            if (failedFallbacks.add(state.getBlock())) Modularmachinery.LOG.warn(
                    "[MMCE] cannot render blueprint fallback for {}", state, failure);
        }
    }

    public static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    @Override public void close() {
        if (previewLevel != null) previewLevel.close();
        entities.clear();
        states.clear();
    }

    private void renderQuads(PoseStack pose, VertexConsumer consumer, List<BakedQuad> quads,
                             BlockState state, BlockPos pos) {
        for (BakedQuad quad : quads) {
            int color = 0xFFFFFF;
            if (quad.isTinted()) {
                color = formed && quad.getTintIndex() == 1 && (getBlockEntity(pos) instanceof ColorableMachineTile
                        || state.getBlock() instanceof BlockMachineComponent)
                        ? machine.getDefinedColor()
                        : Minecraft.getInstance().getBlockColors().getColor(state, this, pos, quad.getTintIndex());
            }
            consumer.putBulkData(pose.last(), quad, ((color >> 16) & 255) / 255F,
                    ((color >> 8) & 255) / 255F, (color & 255) / 255F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    @Override public BlockEntity getBlockEntity(BlockPos pos) { return entities.get(pos); }
    @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getHeight() { return 4096; }
    @Override public int getMinBuildHeight() { return -2048; }
    @Override public float getShade(Direction side, boolean shade) { return 1; }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }
    @Override public int getRawBrightness(BlockPos pos, int darkness) { return 15; }
    @Override public LevelLightEngine getLightEngine() { return previewLevel == null ? null : previewLevel.getLightEngine(); }
    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return previewLevel == null ? 0xFFFFFF : previewLevel.getBlockTint(pos, resolver);
    }

    private final class PreviewController extends TileController implements ColorableMachineTile {
        private PreviewController(BlockState state) { super(BlockPos.ZERO, state); }
        @Override public boolean isBlueprintPreview() { return true; }
        @Override public boolean isFormed() { return formed; }
        @Override public String getMachineName() { return machine.getRegistryName(); }
        @Override public int getMachineColor() { return machine.getDefinedColor(); }
        @Override public void setMachineColor(int color) { }
        @Override public void setFormed(boolean value) { } // Never fire gameplay formation events from a preview.
        @Override public double getTick(Object object) {
            return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        }
    }
}
