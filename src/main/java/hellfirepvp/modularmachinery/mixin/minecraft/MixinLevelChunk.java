/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
 */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunk render sections read directly from {@link LevelChunk#getBlockState(BlockPos)} instead of
 * going through {@code Level#getBlockState}.  The Level mixin therefore cannot be the only hide
 * hook: depending on the renderer and rebuild path, a formed component could still be baked into
 * a section.  Returning air at this lower read boundary keeps the client chunk mesh consistent
 * with the world-level hook while leaving server-side reads untouched through BlockModelHider's
 * client-side guard.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void modularmachinery$hideFormedComponent(BlockPos position,
                                                       CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (BlockModelHider.isHidden(chunk.getLevel(), position)) {
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
