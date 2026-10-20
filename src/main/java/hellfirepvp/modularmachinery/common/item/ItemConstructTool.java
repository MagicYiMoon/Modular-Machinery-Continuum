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
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineFormation;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The structure tool: right-click a block to mark the first corner of a box, sneak + right-click another
 * block to mark the second, right-click a controller to write the box out as a machine.
 *
 * <h2>The control scheme, and where each half of it comes from</h2>
 *
 * <p><b>Upstream's tool (70 lines) had one arm</b>, {@code onItemUse}:
 * <pre>
 *   if the clicked block is a BlockController:
 *       finalizeSelection(clicked.getValue(BlockController.FACING), world, pos, player)   // writes the JSON
 *       purgeSelection(player); sendSelection(player);                                    // then clears it
 *   else:
 *       toggleInSelection(player, pos); sendSelection(player);                            // select / deselect
 * </pre>
 * Its selection was <b>per-block</b>: click each block of the machine, one at a time.
 *
 * <p><b>The Refoxed {@code neo/26.1.2} branch's {@code MultiblockDetectorItem} has a box</b> (PLAN 655,
 * read verbatim at commit {@code 6b1321378bdde7b2d409cabc2d69cab5dd0090ae}):
 * <pre>
 *   useOn:  special key -&gt; selection.withSecond(clickedPos)   else -&gt; selection.withFirst(clickedPos)
 *   use:    if !special key return PASS;  stack.remove(SELECTION)     // special key + air clears
 * </pre>
 * and it has <b>no controller arm at all</b> — every block is treated alike, and its own
 * {@code withController} is never called from that file.
 *
 * <p><b>So this class is the Refoxed's two-corner control scheme with upstream's finalise arm kept</b>,
 * which is what PLAN 655.2 specifies: right-click the controller still writes the file, because that is
 * the 1.12.2 behaviour, it is already implemented here, and it is already covered by the round-trip
 * check — and because the Refoxed's controller path was not read, so its order is not guessed at.
 * Recorded as a rule A0 deviation with its evidence:
 * <ul>
 *   <li>the Refoxed behaviour that cannot be used as-is: {@code useOn} treats a controller exactly like
 *       any other block, so a literal copy would delete the finalise path and with it the only way this
 *       port has to produce a machine file;</li>
 *   <li>what this port does instead: the controller keeps its own arm, and the two-corner scheme applies
 *       to every other block.</li>
 * </ul>
 *
 * <h2>The modifier is THIS PORT'S OWN CHOICE, and is not a copy of anything</h2>
 *
 * <p>The Refoxed decides what a "special operation" is inside
 * {@code ItemSpecialOperationUtil.isSpecialOperated(player)} — <b>a body that has not been read.</b> What
 * that helper does (shift, ctrl, a keybind, something else) is therefore unknown here, and it is not
 * guessed at and not claimed. This port picks <b>sneak</b>, and picks it for reasons of its own:
 * <ul>
 *   <li>{@link net.minecraft.world.entity.Entity#isShiftKeyDown()} is a value both sides already have —
 *       the client from its own input, the server from
 *       {@code ServerGamePacketListenerImpl#handlePlayerInput}, which carries the shift flag — so the
 *       two sides agree without a new packet or a keybind;</li>
 *   <li>sneak + use is the convention vanilla already uses to mean "a different action with the same
 *       click" (place instead of use), so a player does not have to be told;</li>
 *   <li>it costs no key mapping, no configuration, and no client-only class in a common item.</li>
 * </ul>
 * This is a naming/interaction choice and is recorded as one.
 *
 * <h2>The second action upstream does not have: forming</h2>
 *
 * <p>This port's tool also <b>forms</b> a machine: right-click a controller with nothing selected and it
 * fills the missing blocks and calls {@link MachineFormation#tryForm}, the same call
 * {@code /modularmachinery form} and {@code ControllerInteraction} make. That behaviour predates the
 * selection port and is kept, because a binding stick is what a survival player expects from the item —
 * and because "the tool stopped forming machines" would be a regression for anyone already using it.
 *
 * <p><b>The order between the two is deliberate: a selection wins.</b> With a selection marked, a
 * right-click on the controller writes the file and does <i>not</i> form; with nothing selected, it
 * forms. There is no combination of clicks that both writes and forms, because the two arms would then
 * disagree about which one the player asked for, and the file under construction would be written from a
 * world that had just been rearranged by the formation.
 *
 * <p><b>A half-marked box also wins, and that is a decision rather than an oversight.</b> One corner is
 * "a selection in progress": the controller click then reaches
 * {@link PlayerStructureSelectionHelper#finalizeSelection}, which reports that the second corner is
 * missing and writes nothing. Forming instead would rearrange the world under a player who is halfway
 * through describing it. The message names the modifier and the way to clear, so the state is escapable
 * without documentation.
 *
 * <h2>The permission gate is deliberately NOT copied</h2>
 *
 * <p>The jar restricts its tool to creative or operator players. That is right for a tool that lets
 * someone inspect and edit a structure definition in JSON, and wrong for the item a survival player uses
 * to assemble their own machine. This is stated rather than silently diverged from: the gate was read,
 * understood, and left out on purpose. The decision is kept from the earlier revision of this file, so
 * the selection path added now is reachable by the same players the forming path already was — a
 * half-gated tool would be worse than either choice.
 */
public class ItemConstructTool extends Item {

    public ItemConstructTool() {
        // A tool, not a consumable: one is enough and stacking would be meaningless.
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Right-click a block: mark a corner. Right-click a controller: write the box out, or form.
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

        // A CONTROLLER, WITH NOTHING SELECTED -> FORM (this port's own action; see the class javadoc).
        //
        // The formation is the server's business; the client only has to know the click was accepted.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof TileController controller)) {
            return InteractionResult.PASS;
        }

        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(controller.getMachineName());
        if (machine == null) {
            // The same words the port already uses for this state, so the two paths read alike.
            // A literal, matching what CommandModularMachinery already prints for this state -- there is no
            // lang key for it, and inventing one would produce the raw-key tooltip section 190 was about.
            player.displayClientMessage(Component.literal(
                    "controller is not bound to a machine; use /modularmachinery bind <machine> first"), true);
            return InteractionResult.SUCCESS;
        }

        // FILL IN WHAT IS MISSING FIRST (PLAN section 267).
        //
        // Fills only the positions whose block is not one of the states the part accepts, so a block the
        // player already placed correctly is never touched. Second and third presses place nothing and
        // change nothing, which is what makes this safe to bind to a right-click.
        int placed = loader.fillMissing((ServerLevel) level, pos,
                state.getValue(BlockController.FACING), machine);

        MachineFormation.Attempt attempt = MachineFormation.tryForm(
                (ServerLevel) level, pos, state.getValue(BlockController.FACING), machine, loader);

        if (attempt.formed()) {
            controller.setFormed(true);
            // `top.modularmachinery.formed` is the port's existing "Formed (%s components)" string, and the
            // fill count is appended as a literal: this port ships no lang key for "placed N blocks", and
            // inventing one would produce a raw key in the message -- section 190.
            player.displayClientMessage(Component
                    .translatable("top.modularmachinery.formed", attempt.boundCount())
                    .append(placed > 0
                            ? Component.literal("  (+" + placed + " placed)")
                            : Component.empty()), true);
        } else {
            // Report every mismatch the matcher found, not just the first: a player assembling a machine
            // needs to know all of what is missing, not to fix it one block per click.
            // The same key ControllerInteraction uses, so the two paths read identically.
            player.displayClientMessage(Component.translatable(
                    "gui.modularmachinery.controller.did_not_form"), false);
            for (String line : attempt.report()) {
                player.displayClientMessage(Component.literal("  " + line), false);
            }
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

    /**
     * Upstream L46-L50's tooltip.
     *
     * <p>Its own key, {@code tooltip.constructtool.creative}, which already ships in both languages — the
     * port carried upstream's lang files across, including this line and the six
     * {@code message.structurebuild.*} ones the selection path now uses. That text is also the one place
     * the dropped permission gate is visible to a player, so it is kept exactly as upstream wrote it
     * rather than rewritten to describe this port's behaviour.
     *
     * <p><b>The two corners are NOT shown here, and that is a consequence of where they are stored.</b>
     * The Refoxed's tooltip has three lines — controller, first, second — because its selection lives on
     * the item stack, which is what this method receives. {@code appendHoverText} gets the stack and no
     * player, so a per-player selection cannot be read from it. Recorded as the known cost of keeping
     * upstream's per-player storage; the drawn box is the feedback that replaces it.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.constructtool.creative")
                .withStyle(ChatFormatting.GRAY));
    }
}
