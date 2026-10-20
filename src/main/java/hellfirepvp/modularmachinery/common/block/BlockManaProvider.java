/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Source IDs remain placeable when Botania is absent. Optional API types stay behind the bridge. */
public final class BlockManaProvider extends BlockModularMagicPlaceholder {
    private final boolean input;
    public BlockManaProvider(Properties properties, boolean input) {
        super(properties, input ? "blockmanaproviderinput" : "blockmanaprovideroutput"); this.input = input;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return net.minecraftforge.fml.ModList.get().isLoaded("botania")
                ? hellfirepvp.modularmachinery.integration.botania.ManaRegistration.create(pos, state, input) : null;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("botania"))
                hellfirepvp.modularmachinery.integration.botania.ManaRegistration.describe(level, pos, player);
            else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.modularmachinery.mana.requires_botania"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
