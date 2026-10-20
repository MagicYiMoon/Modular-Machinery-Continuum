/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes hidden formed-machine components appear as air to the client chunk renderer. */
@Mixin(Level.class)
public abstract class MixinLevel {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void modularmachinery$hideFormedComponent(BlockPos position,
                                                       CallbackInfoReturnable<BlockState> callback) {
        Level level = (Level) (Object) this;
        if (BlockModelHider.isHidden(level, position)) callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
