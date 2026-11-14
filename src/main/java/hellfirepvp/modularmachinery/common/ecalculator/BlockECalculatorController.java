/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO BlockECalculatorController. */
package hellfirepvp.modularmachinery.common.ecalculator;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockECalculatorController extends BlockController {
    private final int tier;
    public BlockECalculatorController(int tier) {
        super(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion()
                .lightLevel(state -> state.getValue(FORMED) ? 12 : 4), ECalculatorRegistration.machineName(tier));
        this.tier = tier;
    }
    public int tier() { return tier; }
    @Override public String getDescriptionId() { return "block.modularmachinery." + ECalculatorRegistration.machineName(tier); }
    @Override public net.minecraft.network.chat.MutableComponent getName() { return Component.translatable(getDescriptionId()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TileECalculatorController(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ECalculatorRegistration.CONTROLLER_TILE.get() ? null : (world, pos, block, tile) -> ((TileECalculatorController)tile).tick();
    }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || passesControllerInteraction(player, hand)) return InteractionResult.PASS;
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof TileECalculatorController host) {
            host.refreshStructure();
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                var session = java.util.UUID.randomUUID();
                net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inventory, ignored) -> hellfirepvp.modularmachinery.common.menu.MenuECalculatorController.forController(id, inventory, host, session),
                        getName()), buffer -> { buffer.writeBlockPos(pos); buffer.writeUUID(session); });
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
