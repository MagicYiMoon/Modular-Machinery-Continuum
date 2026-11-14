/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EF component blocks. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import java.util.List;

public class BlockEFabricatorPart extends BlockMachineComponent {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final EFabricatorSpecs.Part specification;
    public BlockEFabricatorPart(EFabricatorSpecs.Part specification) {
        super(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion()); this.specification = specification;
        var state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        if (state.hasProperty(EFabricatorAppearance.STATUS)) state = state.setValue(EFabricatorAppearance.STATUS, EFabricatorAppearance.OFF);
        registerDefaultState(state);
    }
    public static BlockEFabricatorPart create(EFabricatorSpecs.Part specification) {
        return specification.kind() == EFabricatorSpecs.Kind.WORKER ? new BlockEFabricatorPart(specification) {
            @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
                super.createBlockStateDefinition(b); b.add(EFabricatorAppearance.STATUS);
            }
        } : new BlockEFabricatorPart(specification);
    }
    @Override public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        if (specification.kind() == EFabricatorSpecs.Kind.PARALLEL) return 10;
        if (specification.kind() != EFabricatorSpecs.Kind.WORKER) return 0;
        if (world instanceof net.minecraft.server.level.ServerLevel server) return EFabricatorAppearance.serverLight(server, pos);
        if (world instanceof net.minecraft.world.level.chunk.ChunkAccess chunk && chunk.getWorldForge() instanceof net.minecraft.server.level.ServerLevel server)
            return EFabricatorAppearance.serverLight(server, pos);
        BlockEntity tile = world.getBlockEntity(pos);
        return tile instanceof TileEFabricatorPart part && part.appearance() != EFabricatorAppearance.OFF ? 10 : 0;
    }
    public EFabricatorSpecs.Part specification() { return specification; }
    @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        var item = player.getItemInHand(hand).getItem();
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || specification.kind() != EFabricatorSpecs.Kind.PATTERN_BUS
                || item instanceof hellfirepvp.modularmachinery.common.item.ItemConstructTool || item == Items.STICK || player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (player instanceof net.minecraft.server.level.ServerPlayer server && level.getBlockEntity(pos) instanceof TileEFabricatorPatternBus bus) EFabricatorMenus.bus(server, bus);
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s, Mirror m) { return s.setValue(FACING, m.mirror(s.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return EFabricatorRegistration.createPart(pos, state); }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return !level.isClientSide && specification.kind() == EFabricatorSpecs.Kind.CHANNEL && type == EFabricatorRegistration.PART_TILE.get()
                ? (world, pos, block, tile) -> ((TileEFabricatorMEChannel)tile).tick() : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.world.entity.player.Player player
                && level.getBlockEntity(pos) instanceof TileEFabricatorMEChannel channel) channel.setPlacingPlayer(player);
        if (!level.isClientSide && stack.hasTag()) {
            if (level.getBlockEntity(pos) instanceof TileEFabricatorWorker worker) worker.restoreWorkData(stack.getTag().getCompound("EfWorkData"));
            else if (level.getBlockEntity(pos) instanceof TileEFabricatorPatternBus bus) bus.restorePatternData(stack.getTag().getCompound("EfPatternData"));
        }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !level.isClientSide && level.getBlockEntity(pos) instanceof TileEFabricatorPart part) part.unbind();
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        if (specification.kind() == EFabricatorSpecs.Kind.WORKER) tooltip.add(Component.translatable("tooltip.ef.worker", EFabricatorSpecs.BASE_QUEUE, EFabricatorSpecs.ENERGY_CACHE));
        if (specification.kind() == EFabricatorSpecs.Kind.PATTERN_BUS) tooltip.add(Component.translatable("tooltip.ef.patterns", EFabricatorSpecs.PATTERN_SLOTS));
        if (specification.tier() != null) tooltip.add(Component.translatable("tooltip.ef.parallel", specification.tier().parallel));
        tooltip.add(Component.translatable("tooltip.ef.development"));
    }
}
