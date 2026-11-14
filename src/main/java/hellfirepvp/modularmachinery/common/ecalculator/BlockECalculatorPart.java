/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EC component blocks. */
package hellfirepvp.modularmachinery.common.ecalculator;

import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public class BlockECalculatorPart extends BlockMachineComponent {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty MEMORY = IntegerProperty.create("memory", 0, 3);
    private final ECalculatorSpecs.Part specification;
    public BlockECalculatorPart(ECalculatorSpecs.Part specification) {
        super(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion());
        this.specification = specification;
        registerDefaultState(ECalculatorAppearance.OFF.renderState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MEMORY, 0)));
    }
    public ECalculatorSpecs.Part specification() { return specification; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, MEMORY); }
    /** Kind-specific definitions avoid a status/link/tier Cartesian product on unrelated blocks. */
    public static BlockECalculatorPart create(ECalculatorSpecs.Part spec) {
        return switch (spec.kind()) {
            case THREAD, HYPER_THREAD -> new BlockECalculatorPart(spec) {
                @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ECalculatorAppearance.STATUS); }
            };
            case PARALLEL -> new BlockECalculatorPart(spec) {
                @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ECalculatorAppearance.ONLINE); }
            };
            case DRIVE -> new BlockECalculatorPart(spec) {
                @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ECalculatorAppearance.ONLINE, ECalculatorAppearance.LINK); }
            };
            case TAIL -> new BlockECalculatorPart(spec) {
                @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ECalculatorAppearance.FORMED); }
            };
            case TRANSMITTER -> new BlockECalculatorPart(spec) {
                @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(ECalculatorAppearance.LINK, ECalculatorAppearance.TIER); }
            };
            default -> new BlockECalculatorPart(spec);
        };
    }
    @Override public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter world, BlockPos pos) {
        BlockEntity tile;
        if (world instanceof net.minecraft.server.level.ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            tile = chunk == null ? null : chunk.getBlockEntity(pos);
        } else tile = world.getBlockEntity(pos);
        var appearance = tile instanceof TileECalculatorPart part ? part.appearance() : ECalculatorAppearance.OFF;
        return appearance.light(specification.kind(), state.getValue(MEMORY));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return specification.kind() == ECalculatorSpecs.Kind.DRIVE ? new TileECalculatorDrive(pos, state) : ECalculatorRegistration.createPart(pos, state); }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return !level.isClientSide && specification.kind() == ECalculatorSpecs.Kind.CHANNEL && type == ECalculatorRegistration.PART_TILE.get()
                ? (world, pos, block, tile) -> ((TileECalculatorMEChannel)tile).tick() : null;
    }
    private boolean core() { return specification.kind() == ECalculatorSpecs.Kind.THREAD || specification.kind() == ECalculatorSpecs.Kind.HYPER_THREAD; }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || specification.kind() != ECalculatorSpecs.Kind.DRIVE) return InteractionResult.PASS;
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof TileECalculatorDrive drive)) return InteractionResult.PASS;
        var held = player.getItemInHand(hand);
        if (held.isEmpty() && drive.hasMemory()) {
            player.setItemInHand(hand, drive.takeMemory());
            player.displayClientMessage(Component.translatable("message.ec.removed"), false);
        } else if (held.getItem() instanceof ItemECalculatorMemory && drive.insertMemory(held)) {
            held.shrink(1); // Creative moves the same memory; it must not duplicate attached NBT.
            player.displayClientMessage(Component.translatable(drive.isTierSupported() ? "message.ec.inserted" : "message.ec.inserted_inactive"), false);
        } else return InteractionResult.PASS;
        return InteractionResult.CONSUME;
    }
    @Override public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide && placer instanceof Player player && world.getBlockEntity(pos) instanceof TileECalculatorMEChannel channel)
            channel.setPlacingPlayer(player);
        if (!world.isClientSide && core() && world.getBlockEntity(pos) instanceof TileECalculatorPart part && stack.hasTag())
            part.restoreBackend(stack.getTag().getCompound("EcBackendData"));
    }
    @Override public void onRemove(BlockState state, Level world, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && !world.isClientSide && world.getBlockEntity(pos) instanceof TileECalculatorPart part) {
            if (part instanceof TileECalculatorDrive drive) {
                var memory = drive.takeMemory(); if (!memory.isEmpty()) Block.popResource(world, pos, memory);
            } else if (core()) {
                var stack = new ItemStack(this);
                var backend = part.backendSnapshot();
                if (!backend.isEmpty()) stack.getOrCreateTag().put("EcBackendData", backend);
                Block.popResource(world, pos, stack);
            }
            part.unbind();
        }
        super.onRemove(state, world, pos, replacement, moving);
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter world, List<Component> tooltip, TooltipFlag flag) {
        if (specification.normalSlots() + specification.hyperSlots() > 0)
            tooltip.add(Component.translatable("tooltip.ec.slots", specification.normalSlots(), specification.hyperSlots()));
        if (specification.parallelism() > 0) tooltip.add(Component.translatable("tooltip.ec.parallel", specification.parallelism()));
        tooltip.add(Component.translatable("tooltip.ec.development"));
    }
}
