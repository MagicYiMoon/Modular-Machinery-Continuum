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

/** Keeps block IDs available without loading any of the three optional gas-network APIs. */
public final class BlockMEGasBus extends BlockMachineComponent {
    private final boolean input;
    public BlockMEGasBus(Properties properties, boolean input) { super(properties); this.input = input; }
    public static boolean available() {
        return ModList.get().isLoaded("ae2") && ModList.get().isLoaded("mekanism") && ModList.get().isLoaded("appmek");
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return available() ? hellfirepvp.modularmachinery.integration.appmek.GasBusRegistration.create(pos, state, input) : null;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                            InteractionHand hand, BlockHitResult hit) {
        if (!available()) {
            if (!level.isClientSide()) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.modularmachinery.gas_bus.dependencies"), true);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) hellfirepvp.modularmachinery.integration.appmek.GasBusRegistration.open(level, pos, player, hand);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
