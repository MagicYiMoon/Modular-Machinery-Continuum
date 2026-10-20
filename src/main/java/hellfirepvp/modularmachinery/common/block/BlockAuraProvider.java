/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fml.ModList;

public final class BlockAuraProvider extends BlockModularMagicPlaceholder {
    private final boolean input;
    public BlockAuraProvider(Properties properties, boolean input) {
        super(properties, "blockauraprovider" + (input ? "input" : "output")); this.input = input;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModList.get().isLoaded("naturesaura")
                ? hellfirepvp.modularmachinery.integration.naturesaura.AuraRegistration.create(pos, state, input) : null;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (ModList.get().isLoaded("naturesaura")) hellfirepvp.modularmachinery.integration.naturesaura.AuraRegistration.inspect(level, pos, player);
            else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.modularmachinery.aura.required"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
