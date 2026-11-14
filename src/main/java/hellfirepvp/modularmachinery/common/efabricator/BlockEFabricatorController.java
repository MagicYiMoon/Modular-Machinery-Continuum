/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO BlockEFabricatorController. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public final class BlockEFabricatorController extends BlockController {
    private final EFabricatorSpecs.Tier tier;
    public BlockEFabricatorController(EFabricatorSpecs.Tier tier) {
        super(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion()
                .lightLevel(state -> state.getValue(FORMED) ? 10 : 0), EFabricatorRegistration.machineName(tier)); this.tier = tier;
    }
    public EFabricatorSpecs.Tier tier() { return tier; }
    @Override public String getDescriptionId() { return "block.modularmachinery." + EFabricatorRegistration.machineName(tier); }
    @Override public net.minecraft.network.chat.MutableComponent getName() { return Component.translatable(getDescriptionId()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileEFabricatorController(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && stack.hasTag() && level.getBlockEntity(pos) instanceof TileEFabricatorController controller)
            controller.restoreProductionData(stack.getTag().getCompound("EfProduction"));
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != EFabricatorRegistration.CONTROLLER_TILE.get() ? null : (world, pos, block, tile) -> ((TileEFabricatorController)tile).tick();
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || passesControllerInteraction(player, hand)) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileEFabricatorController controller) {
            controller.refreshStructure();
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                var session = java.util.UUID.randomUUID();
                net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inventory, ignored) -> hellfirepvp.modularmachinery.common.menu.MenuEFabricatorController.forController(id, inventory, controller, session),
                        getName()), buffer -> { buffer.writeBlockPos(pos); buffer.writeUUID(session); });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) { tooltip.add(Component.translatable("tooltip.ef.development")); }
}
