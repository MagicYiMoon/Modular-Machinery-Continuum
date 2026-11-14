/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Section rebuilds use copied palettes, bypassing Level and LevelChunk lookups. */
@Mixin(RenderChunkRegion.class)
public abstract class MixinRenderChunkRegion {
    @Shadow @Final private Level level;
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void modularmachinery$hideBlock(BlockPos pos, CallbackInfoReturnable<BlockState> callback) {
        if (BlockModelHider.isHidden(level, pos)) callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void modularmachinery$hideFluid(BlockPos pos, CallbackInfoReturnable<FluidState> callback) {
        if (BlockModelHider.isHidden(level, pos)) callback.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
    }
}
