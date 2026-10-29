/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputPort;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

/** A separate six-direction component; existing energy hatches retain their own behavior. */
public final class BlockEnergyOutputPort extends BlockMachineComponent {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty STATUS = IntegerProperty.create("status", 0, 2);
    public BlockEnergyOutputPort(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, STATUS); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileEnergyOutputPort(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && type == RegistryBlockEntities.ENERGY_OUTPUT_PORT.get()
                ? (world, pos, current, tile) -> ((TileEnergyOutputPort) tile).tick() : null;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moved) {
        super.neighborChanged(state, level, pos, block, from, moved);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEnergyOutputPort port) port.invalidateTarget();
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEnergyOutputPort port) {
            BlockPos target = port.targetPosition();
            player.displayClientMessage(Component.translatable("message.modularmachinery.energy_port",
                    port.getStoredEnergy(), port.getCapacity(), target == null ? "—" : target.toShortString()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.modularmachinery.energy_port", Config.energyOutputPortRange(), TileEnergyOutputPort.TRANSFER));
    }
}
