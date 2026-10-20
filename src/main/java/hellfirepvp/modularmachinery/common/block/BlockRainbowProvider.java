/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.integration.extrautilities.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockRainbowProvider extends BlockModularMagicPlaceholder {
    public BlockRainbowProvider(Properties properties) { super(properties, "blockrainbowprovider"); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return RainbowIntegration.available() ? RainbowRegistration.PROVIDER.get().create(pos, state) : null;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) player.displayClientMessage(Component.translatable(!RainbowIntegration.available()
                ? "message.modularmachinery.rainbow.requires_mod"
                : RainbowIntegration.isRunning(level, pos) ? "message.modularmachinery.rainbow.running" : "message.modularmachinery.rainbow.stopped"), true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
