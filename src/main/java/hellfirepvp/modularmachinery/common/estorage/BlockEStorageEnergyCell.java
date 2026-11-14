/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO BlockEStorageEnergyCell, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public final class BlockEStorageEnergyCell extends BlockMachineComponent {
    public enum Status implements StringRepresentable {
        EMPTY, LOW, MID, HIGH, FULL;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Status fromFraction(double fill) {
            return fill >= .9 ? FULL : fill >= .7 ? HIGH : fill >= .5 ? MID : fill >= .05 ? LOW : EMPTY;
        }
    }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    private final double capacity;
    public BlockEStorageEnergyCell(double capacity) {
        super(RegistryBlocks.componentProperties().strength(20, 2000).lightLevel(state -> state.getValue(STATUS).ordinal() * 2));
        this.capacity = capacity;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, Status.EMPTY));
    }
    public double capacity() { return capacity; }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter world,
            java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flags) {
        super.appendHoverText(stack, world, tooltip, flags);
        double saved = stack.hasTag() ? stack.getTag().getDouble("energyStored") : 0;
        double stored = Double.isFinite(saved) ? Math.max(0, Math.min(capacity, saved)) : 0;
        var numbers = java.text.NumberFormat.getNumberInstance(java.util.Locale.ROOT);
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.modularmachinery.estorage_energy",
                numbers.format(stored), numbers.format(capacity)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, STATUS); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileEStorageEnergyCell(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != EStorageRegistration.ENERGY_TILE.get() ? null : (world, pos, block, tile) -> ((TileEStorageEnergyCell) tile).tick();
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && stack.hasTag() && level.getBlockEntity(pos) instanceof TileEStorageEnergyCell cell) cell.loadEnergy(stack.getTag());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof TileEStorageEnergyCell cell)
            Block.popResource(level, pos, cell.takeDrop());
        super.onRemove(state, level, pos, replacement, moving);
    }
}
