/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO BlockEStorageController, KasumiNova and contributors.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockEStorageController extends BlockController {
    private final int tier;
    public BlockEStorageController(int tier) {
        super(RegistryBlocks.componentProperties().strength(20.0F, 2000.0F).noOcclusion()
                .lightLevel(state -> state.getValue(FORMED) ? 10 : 0), EStorageRegistration.machineName(tier));
        this.tier = tier;
    }
    public int tier() { return tier; }
    @Override public String getDescriptionId() { return "block.modularmachinery." + EStorageRegistration.machineName(tier); }
    @Override public net.minecraft.network.chat.MutableComponent getName() { return Component.translatable(getDescriptionId()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileEStorageController(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != EStorageRegistration.CONTROLLER_TILE.get() ? null :
                (world, pos, block, tile) -> ((TileEStorageController) tile).tick();
    }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || passesControllerInteraction(player, hand)) return InteractionResult.PASS;
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof TileEStorageController controller) {
            controller.refreshStructure();
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, ignored) -> hellfirepvp.modularmachinery.common.menu.MenuEStorageController.forController(id, inventory, controller),
                        getName()), buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
