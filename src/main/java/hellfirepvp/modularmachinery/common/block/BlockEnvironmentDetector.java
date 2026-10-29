/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.tiles.TileEnvironmentDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** Passive sensor: conditions are evaluated by the recipe, not by a separate block ticker. */
public final class BlockEnvironmentDetector extends BlockMachineComponent {
    public BlockEnvironmentDetector(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEnvironmentDetector(pos, state);
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.modularmachinery.environment_detector"));
    }
}
