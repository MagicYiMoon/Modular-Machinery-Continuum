/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.integration.AstralIntegration;
import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockAstralProvider extends BlockModularMagicPlaceholder {
    private final int kind;
    public BlockAstralProvider(Properties properties, int kind) {
        super(properties, kind == 0 ? "blockstarlightproviderinput" : kind == 1 ? "blockstarlightprovideroutput" : "blockconstellationprovider"); this.kind = kind;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return AstralIntegration.available() ? hellfirepvp.modularmachinery.integration.astral.AstralRegistration.create(pos, state, kind) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || kind == 2 || !AstralIntegration.available() ? null
                : (world, pos, block, tile) -> { if (tile instanceof TileEntityBase base) base.tick(); };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (AstralIntegration.available()) {
            // Let Astral's linking tool use its own selection and connection interaction.
            if (hellfirepvp.modularmachinery.integration.astral.AstralRegistration.isLinkingTool(player.getItemInHand(hand))) {
                if (!player.isShiftKeyDown()) return InteractionResult.PASS;
                if (!level.isClientSide()) hellfirepvp.modularmachinery.integration.astral.AstralRegistration.unlink(level, pos, player);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide()) hellfirepvp.modularmachinery.integration.astral.AstralRegistration.inspect(level, pos, player);
        } else if (!level.isClientSide()) player.displayClientMessage(Component.translatable("message.modularmachinery.starlight.requires_mod"), true);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
