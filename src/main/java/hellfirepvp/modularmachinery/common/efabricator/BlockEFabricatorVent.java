/* SPDX-License-Identifier: GPL-3.0-only; NECO EF vent has no tile or independent cooling logic. */
package hellfirepvp.modularmachinery.common.efabricator;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

public final class BlockEFabricatorVent extends HorizontalDirectionalBlock {
    public BlockEFabricatorVent() {
        super(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion()); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection()); }
    @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s, Mirror m) { return s.setValue(FACING, m.mirror(s.getValue(FACING))); }
}
