/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

/** The optional API bridge is entered only when Botania is installed. */
public final class BlockSparkManaProvider extends BlockMachineComponent {
    private final boolean input;
    public BlockSparkManaProvider(Properties properties, boolean input) { super(properties); this.input = input; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return net.minecraftforge.fml.ModList.get().isLoaded("botania")
                ? hellfirepvp.modularmachinery.integration.botania.ManaRegistration.createSpark(pos, state, input) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!input || level.isClientSide() || !net.minecraftforge.fml.ModList.get().isLoaded("botania")) return null;
        return (world, pos, current, tile) -> {
            if (tile instanceof hellfirepvp.modularmachinery.integration.botania.TileSparkManaProvider sensor) sensor.tick();
        };
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("botania")) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("message.modularmachinery.mana.requires_botania"), true);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        // Spark/wand items must receive useOn, including non-sneaking spark placement.
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide()) hellfirepvp.modularmachinery.integration.botania.ManaRegistration.describe(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.modularmachinery.spark_mana." + (input ? "input" : "output")));
    }
}
