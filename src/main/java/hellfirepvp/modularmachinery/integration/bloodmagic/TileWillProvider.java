/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.demonaura.WorldDemonWillHandler;

public final class TileWillProvider extends TileMachineComponent {
    private final boolean input;
    public TileWillProvider(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean input) {
        super(() -> type, pos, state); this.input = input;
    }
    @Override public IOType getIOType() { return input ? IOType.INPUT : IOType.OUTPUT; }
    public double amount(EnumDemonWillType type) { return WorldDemonWillHandler.getCurrentWill(level, worldPosition, type); }
    public double take(EnumDemonWillType type, double amount, boolean simulate) {
        return WorldDemonWillHandler.drainWill(level, worldPosition, type, amount, !simulate);
    }
    public double add(EnumDemonWillType type, double amount, boolean simulate) {
        return WorldDemonWillHandler.fillWill(level, worldPosition, type, amount, !simulate);
    }
}
