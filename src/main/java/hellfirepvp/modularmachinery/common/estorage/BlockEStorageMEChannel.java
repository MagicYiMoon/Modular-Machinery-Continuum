/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO BlockEStorageMEChannel, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockEStorageMEChannel extends BlockMachineComponent {
    public BlockEStorageMEChannel() { super(RegistryBlocks.componentProperties().strength(5, 10)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return net.minecraftforge.fml.ModList.get().isLoaded("ae2")
                ? hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageChannelFactory.create(pos, state)
                : new TileEStorageMEChannel(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != EStorageRegistration.CHANNEL_TILE.get() ? null :
                (world, pos, block, tile) -> ((TileEStorageMEChannel) tile).tick();
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.world.entity.player.Player player
                && level.getBlockEntity(pos) instanceof TileEStorageMEChannel channel) channel.setPlacingPlayer(player);
    }
}
