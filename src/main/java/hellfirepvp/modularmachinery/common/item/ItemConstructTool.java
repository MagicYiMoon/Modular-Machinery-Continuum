/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.item;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Selects two corners and exports the world structure as a machine definition.
 * Plain block clicks mark the first corner; sneak-clicks mark the second; a controller click
 * exports a completed selection. Sneak-clicking air clears the selection.
 * This authoring tool never places or forms a structure; automatic assembly belongs to the
 * configured assembly item (a stick by default).
 */
public class ItemConstructTool extends Item {

    public ItemConstructTool() {
        // A tool, not a consumable: one is enough and stacking would be meaningless.
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Right-click a block: mark a corner. Right-click a controller: export the selected box.
     *
     * <p>The tool has exactly two arms here and one more in {@link #use}, which is the Refoxed's shape
     * (that item has no left-click arm either) with upstream's controller branch added.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (context.getHand() != InteractionHand.MAIN_HAND || player == null) {
            return InteractionResult.PASS;
        }

        BlockState state = level.getBlockState(pos);

        // NOT A CONTROLLER -> MARK A CORNER. Upstream's else branch (L62-L65), with the modifier
        // deciding which corner -- the Refoxed's useOn, and the reason this arm reaches the client too
        // is that it is the ONLY feedback the player gets: `sendSelection` sends the new pair back, and
        // a box nobody can see is indistinguishable from a broken tool.
        if (!(state.getBlock() instanceof BlockController)) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    PlayerStructureSelectionHelper.setSecondCorner(player, pos);
                } else {
                    PlayerStructureSelectionHelper.setFirstCorner(player, pos);
                }
                PlayerStructureSelectionHelper.sendSelection(player);
            }
            // SUCCESS on both sides, matching ControllerInteraction's own comment about this: the client
            // must act on the click too, or it swings the arm and runs a second, local prediction.
            return InteractionResult.SUCCESS;
        }

        // A CONTROLLER, WITH A SELECTION -> WRITE THE FILE (upstream L57-L61).
        //
        // The selection is read from the helper rather than passed in, because that is where it lives and
        // where `finalizeSelection` reads it from — upstream's signature is (facing, world, pos, player)
        // and it re-reads the map itself, so passing a copy here would create a second source for the one
        // thing this call exists to consume.
        if (hasSelection(level, player)) {
            if (!level.isClientSide) {
                boolean wrote = PlayerStructureSelectionHelper.finalizeSelection(
                        state.getValue(BlockController.FACING), level, pos, player);
                if (wrote) {
                    // Upstream L60-L61, in upstream's order: the server's map is dropped and the client
                    // is told the selection is now empty, so the box disappears with the file. ONLY on a
                    // write: a refused box (one corner, or too large) must keep its corners, or the
                    // player has to re-click them to fix a number.
                    PlayerStructureSelectionHelper.purgeSelection(player);
                }
                PlayerStructureSelectionHelper.sendSelection(player);
            }
            return InteractionResult.SUCCESS;
        }

        // No selection is an authoring error, never a request to alter the world.
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.structurebuild.empty"), false);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Sneak + right-click on air clears the selection; a plain right-click on air does nothing.
     *
     * <p>The Refoxed's {@code use} arm, semantics for semantics: {@code if (!isSpecialOperated(player))
     * return PASS;} and then the selection is removed. Two things about it are this port's own. The
     * modifier is sneak (see the class javadoc — {@code ItemSpecialOperationUtil} is unread). And the
     * removal is <b>{@link PlayerStructureSelectionHelper#purgeSelection} plus a sync</b> rather than a
     * component removed from a stack, because the selection is per-player here; the sync is what makes
     * the client's box disappear, and without it the player would be left looking at a box that is no
     * longer selected.
     *
     * <p><b>SUCCESS, not PASS, on the modifier arm</b> — and on both sides, which is the convention the
     * other arms already follow: the client must act on the click too, or it swings the arm and runs a
     * second, local prediction. The non-modifier arm returns PASS deliberately: an air click with the
     * tool is not this item's business, and swallowing it would make the tool feel like it ate the click.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            PlayerStructureSelectionHelper.purgeSelection(player);
            PlayerStructureSelectionHelper.sendSelection(player);
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * Whether this player has anything marked, <b>asked of the side that owns the state</b>.
     *
     * <p>Upstream's arm tested the map on the server and nothing on the client, so its client branch read
     * a map the sync packet never fills and was dead code. Both sides now answer the same question from
     * what they actually have: the server from {@code activeSelectionMap}, the client from the mirror the
     * packet writes. That is what makes "a selection wins" true on the client as well, which is the half
     * that decides whether the player sees the click accepted.
     */
    private static boolean hasSelection(Level level, Player player) {
        if (!level.isClientSide) {
            return !PlayerStructureSelectionHelper.selectionOf(player).isEmpty();
        }
        PlayerStructureSelectionHelper.StructureSelection client =
                PlayerStructureSelectionHelper.clientSelection;
        return client != null && !client.isEmpty();
    }

    /** Describes only the retained selection/export actions. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        for (String key : List.of("tooltip.constructtool.creative", "tooltip.constructtool.corners",
                "tooltip.constructtool.export", "tooltip.constructtool.clear")) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
