/* SPDX-License-Identifier: GPL-3.0-only
 * ES insertion/removal adapted from NECO EStorageEventHandler, KasumiNova and contributors.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockEStorageDrive extends BlockMachineComponent {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    public BlockEStorageDrive() {
        super(RegistryBlocks.componentProperties().strength(20.0F, 2000.0F).noOcclusion().lightLevel(state -> state.getValue(OCCUPIED) ? 6 : 2));
        registerDefaultState(EStorageDriveAppearance.EMPTY.renderState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OCCUPIED, false)));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED, EStorageDriveAppearance.LEVEL, EStorageDriveAppearance.KIND, EStorageDriveAppearance.CAPACITY, EStorageDriveAppearance.STATUS);
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide || type != EStorageRegistration.DRIVE_TILE.get() ? null : (world, pos, block, tile) -> ((TileEStorageDrive) tile).tick();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileEStorageDrive(pos, state); }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof TileEStorageDrive drive)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() && drive.hasMatrix()) {
            player.setItemInHand(hand, drive.takeMatrix());
            player.displayClientMessage(Component.translatable("message.es.removed"), false);
        } else if (held.getItem() instanceof ItemEStorageCell && drive.insertMatrix(held)) {
            // Moving, not copying, the matrix also applies in creative to preserve its inventory identity.
            held.shrink(1);
            player.displayClientMessage(Component.translatable(drive.isTierSupported() ? "message.es.inserted" : "message.es.inserted_inactive"), false);
        } else return InteractionResult.PASS;
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level world, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && !world.isClientSide && world.getBlockEntity(pos) instanceof TileEStorageDrive drive) {
            ItemStack matrix = drive.takeMatrix();
            if (!matrix.isEmpty()) Block.popResource(world, pos, matrix);
            drive.unbind();
        }
        super.onRemove(state, world, pos, replacement, moving);
    }
}
