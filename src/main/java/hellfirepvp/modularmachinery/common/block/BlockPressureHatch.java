/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

/** The optional API bridge is entered only with PneumaticCraft installed. */
public final class BlockPressureHatch extends BlockMachineComponent {
    private final boolean input;
    public BlockPressureHatch(Properties properties, boolean input) { super(properties); this.input = input; }
    private static boolean available() { return net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft"); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return available() ? hellfirepvp.modularmachinery.integration.pneumatic.PressureRegistration.create(pos, state, input) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return available() ? (world, pos, current, tile) -> {
            if (tile instanceof hellfirepvp.modularmachinery.integration.pneumatic.TilePressureHatch hatch) hatch.tick();
        } : null;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moved) {
        super.neighborChanged(state, level, pos, block, from, moved);
        if (available() && level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.integration.pneumatic.TilePressureHatch hatch)
            hatch.neighborsChanged();
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!available()) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("message.modularmachinery.pressure.requires_mod"), true);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide()) hellfirepvp.modularmachinery.integration.pneumatic.PressureRegistration.open(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && available())
            hellfirepvp.modularmachinery.integration.pneumatic.PressureRegistration.dropUpgrades(level, pos);
        super.onRemove(state, level, pos, replacement, moved);
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.modularmachinery.pressure"));
    }
}
