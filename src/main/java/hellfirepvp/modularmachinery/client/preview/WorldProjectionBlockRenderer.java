/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Forge 1.20.1 world-aware ghost model and block entity rendering.
 */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.WorldProjectionScene;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.data.ModelData;
import java.util.*;

/** Special renderers receive detached entities with preview NBT, projected neighbors and absolute world positions. */
final class WorldProjectionBlockRenderer {
    private WorldProjectionScene owner;
    private final Set<Block> failedModels = new HashSet<>(), failedEntities = new HashSet<>(), failedFallbacks = new HashSet<>();

    void render(WorldProjectionScene scene, BlockPos pos, BlockState state, PoseStack pose,
                MultiBufferSource buffers, int light, float partialTick) {
        if (owner != scene) {
            owner = scene;
            failedModels.clear(); failedEntities.clear(); failedFallbacks.clear();
        }
        var mc = Minecraft.getInstance();
        BlockEntity entity = scene.level().getBlockEntity(pos);
        if (state.getRenderShape() == RenderShape.MODEL && !failedModels.contains(state.getBlock())) {
            try {
                var model = mc.getBlockRenderer().getBlockModel(state);
                ModelData data = entity == null ? ModelData.EMPTY : entity.getModelData();
                data = model.getModelData(scene.level(), pos, state, data);
                mc.getBlockRenderer().renderSingleBlock(state, copyPose(pose), buffers, light,
                        OverlayTexture.NO_OVERLAY, data, null);
            } catch (RuntimeException | LinkageError failure) {
                if (failedModels.add(state.getBlock())) Modularmachinery.LOG.warn(
                        "[MMCE] cannot render world projection model for {}", state, failure);
            }
        }
        boolean rendered = entity != null && !(entity instanceof TileController)
                && renderEntity(entity, partialTick, pose, buffers, light);
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED && !rendered) {
            renderFallback(scene, pos, state, pose, buffers, light);
        }
    }

    private <T extends BlockEntity> boolean renderEntity(T entity, float partialTick, PoseStack pose,
                                                       MultiBufferSource buffers, int light) {
        Block block = entity.getBlockState().getBlock();
        if (failedEntities.contains(block) || !entity.getType().isValid(entity.getBlockState())) return false;
        try {
            var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
            if (renderer == null) return false;
            // Supply the ghost transform directly; the dispatcher would cull using the live world camera.
            renderer.render(entity, partialTick, copyPose(pose), buffers, light, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (failedEntities.add(block)) Modularmachinery.LOG.warn(
                    "[MMCE] cannot render detached world projection entity for {}; using item fallback", block, failure);
            return false;
        }
    }

    private void renderFallback(WorldProjectionScene scene, BlockPos pos, BlockState state,
                                PoseStack pose, MultiBufferSource buffers, int light) {
        if (failedFallbacks.contains(state.getBlock())) return;
        try {
            ItemStack stack = new ItemStack(state.getBlock());
            if (stack.isEmpty()) return;
            var part = scene.part(pos);
            if (part != null && part.previewNbt() != null) stack.addTagElement("BlockEntityTag", part.previewNbt());
            IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack, ItemDisplayContext.NONE,
                    copyPose(pose), buffers, light, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException | LinkageError failure) {
            if (failedFallbacks.add(state.getBlock())) Modularmachinery.LOG.warn(
                    "[MMCE] cannot render world projection item fallback for {}", state, failure);
        }
    }

    void clear() {
        owner = null;
        failedModels.clear(); failedEntities.clear(); failedFallbacks.clear();
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }
}
