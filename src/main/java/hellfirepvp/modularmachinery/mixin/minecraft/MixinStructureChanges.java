/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.common.machine.MachineStructureChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes command, explosion and piston writes, regardless of Level#setBlock notification flags. */
@Mixin(LevelChunk.class)
public abstract class MixinStructureChanges {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void modularmachinery$structureChanged(BlockPos pos, BlockState state, boolean moving,
                                                  CallbackInfoReturnable<BlockState> callback) {
        BlockState previous = callback.getReturnValue();
        if (previous != null && previous != state
                && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level)
            MachineStructureChanges.blockChanged(level, pos);
    }
}
