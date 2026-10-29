/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class TileEnvironmentDetector extends TileMachineComponent {
    public TileEnvironmentDetector(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.ENVIRONMENT_DETECTOR, pos, state);
    }
    @Override public IOType getIOType() { return IOType.INPUT; }
}
