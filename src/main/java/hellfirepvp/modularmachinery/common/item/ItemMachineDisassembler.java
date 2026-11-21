/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.item;

import hellfirepvp.modularmachinery.common.assembly.MachineDisassembly;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class ItemMachineDisassembler extends Item {
    public ItemMachineDisassembler() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || context.getHand() != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller))
            return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && !player.getCooldowns().isOnCooldown(this)) {
            player.getCooldowns().addCooldown(this, 10);
            MachineDisassembly.run(server, controller);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        // The disassembler handles ordinary machine materials, up to diamond tool requirements.
        return Items.DIAMOND_PICKAXE.isCorrectToolForDrops(state)
                || Items.DIAMOND_AXE.isCorrectToolForDrops(state)
                || Items.DIAMOND_SHOVEL.isCorrectToolForDrops(state)
                || Items.DIAMOND_HOE.isCorrectToolForDrops(state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.modularmachinery.disassembler.use"));
        tooltip.add(Component.translatable("tooltip.modularmachinery.disassembler.destination"));
    }
}
