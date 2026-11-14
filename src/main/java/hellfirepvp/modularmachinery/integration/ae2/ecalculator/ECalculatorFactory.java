/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import hellfirepvp.modularmachinery.common.ecalculator.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Returning common types keeps optional AE inheritance out of always-loaded callers. */
public final class ECalculatorFactory {
    private ECalculatorFactory() { }
    public static TileECalculatorMEChannel channel(BlockPos pos, BlockState state) { return new ECalculatorMEChannelNode(pos, state); }
    public static ECalculatorCoreAccess core(TileECalculatorPart part, CompoundTag data) { return new ECalculatorTasks(part, data); }
}
