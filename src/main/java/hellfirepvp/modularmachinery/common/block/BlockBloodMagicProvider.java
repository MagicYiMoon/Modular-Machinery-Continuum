/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fml.ModList;

/** Optional API references are reached only with Blood Magic installed. */
public final class BlockBloodMagicProvider extends BlockModularMagicPlaceholder {
    private final boolean life, input;
    public BlockBloodMagicProvider(Properties properties, boolean life, boolean input) {
        super(properties, "block" + (life ? "lifeessence" : "will") + "provider" + (input ? "input" : "output"));
        this.life = life; this.input = input;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("bloodmagic")
                ? hellfirepvp.modularmachinery.integration.bloodmagic.BloodMagicRegistration.create(pos, state, life, input) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !life || !ModList.get().isLoaded("bloodmagic")) return null;
        return (world, pos, block, tile) -> {
            if (tile instanceof hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase component) component.tick();
        };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (ModList.get().isLoaded("bloodmagic"))
                hellfirepvp.modularmachinery.integration.bloodmagic.BloodMagicRegistration.open(level, pos, player);
            else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.modularmachinery.bloodmagic.required"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
