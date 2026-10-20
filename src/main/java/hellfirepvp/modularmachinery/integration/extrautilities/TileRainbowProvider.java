/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.extrautilities;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class TileRainbowProvider extends TileMachineComponent {
    public TileRainbowProvider(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(() -> type, pos, state); }
    @Override public IOType getIOType() { return IOType.INPUT; }
    public boolean isRunning() { return RainbowIntegration.isRunning(level, worldPosition); }
}
