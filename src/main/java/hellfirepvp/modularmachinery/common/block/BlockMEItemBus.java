/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The block form of the Applied Energistics 2 item buses, in both directions.
 *
 * <h2>What upstream's version does, read from the bodies rather than the signatures (rule A)</h2>
 *
 * <p>Upstream's {@code BlockMEItemInputBus} (135 lines) and {@code BlockMEItemOutputBus} (114 lines) both extend
 * {@code BlockMEItemBus} (14 lines), which extends {@code BlockMEMachineComponent} (77 lines), which extends
 * {@code BlockMachineComponent}. <b>The port's {@code BlockMEPlaceholder} already extends that same class</b>, so no new
 * abstraction is needed and the base is the right one.</p>
 *
 * <p><b>{@code createTileEntity} upstream is ONE LINE -- {@code return new MEItemInputBus();}.</b> There is no
 * per-direction logic in it at all, which is why this class needs one method and a direction flag rather than two
 * classes. On 1.20.1 the tile must be constructed with its type and position, which is what the {@code Supplier}
 * constructor of §403 exists for.</p>
 *
 * <p><b>{@code onBlockPlacedBy} upstream restores a bus's inventory AND its config from the placed item's NBT</b>, so a
 * bus that is broken and replaced keeps its contents. That is a real behaviour and it is NOT ported here, deliberately
 * and with the difference recorded: §364's {@code playerDestroy} spills the contents as loose items rather than writing
 * them into the dropped block item, so the port's break-and-replace does not round-trip.</p>
 *
 * <p><b>And the ~40 lines per direction of {@code breakBlock}/{@code getDrops}/{@code dropBlockAsItemWithChance} need no
 * port at all</b> (rule B): they are inherited from {@code BlockMachineComponent.playerDestroy}, which drains the block's
 * {@code ITEM_HANDLER} through the capability and therefore cannot miss a slot. Upstream's
 * {@code dropBlockAsItemWithChance} body is in fact EMPTY -- lines 62-63 are {@code { }}.</p>
 *
 * <h2>Why this replaces the placeholder for these two names only</h2>
 *
 * <p>The other six ME blocks stay {@code BlockMEPlaceholders}: {@code BlockMEPlaceholder} references no AE2 type, which is
 * precisely what lets {@code RegistryMEBlocks} register all eight unconditionally. <b>This class is the exception, and the
 * exception is contained</b> -- see {@link #newBlockEntity} for how, and §404 for the crash that happens when it is not.</p>
 */
public class BlockMEItemBus extends BlockMEMachineComponent {

    /** Which of the two ME item buses this block is. Chosen at registration, fixed per block. */
    public enum Direction {
        INPUT,
        OUTPUT
    }

    private final Direction direction;

    public BlockMEItemBus(Properties properties, Direction direction) {
        super(properties);
        this.direction = direction;
    }

    public Direction getBusDirection() {
        return direction;
    }

    /**
     * Creates the grid-connected tile, or {@code null} when AE2 is absent.
     *
     * <p><b>THE GUARD MOVED TO {@link BlockMEMachineComponent} (PLAN section 429).</b> It used to be here, and an identical copy lived
     * in {@code BlockMEFluidBus} -- two copies of the one decision that §§404 and §411 each cost a round to get wrong. The base class
     * now owns it and makes {@code newBlockEntity} final, so a subclass cannot forget it.</p>
     */
    @Nullable
    @Override
    protected BlockEntity createTile(BlockPos pos, BlockState state) {
        // Through MEBusTiles, and the indirection is the whole point (PLAN section 413).
        //
        // An earlier version constructed the tile INLINE with a switch expression, and the standalone configuration rejected it:
        //
        //     NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost
        //
        // because a class file's method bodies NAME the types they use, and the JVM's VERIFIER resolves those names when the
        // class is LINKED -- not when the method runs. Checking operand-stack assignability requires loading the types, so an
        // inline reference is needed at load time however lazy the call site looks.
        //
        // MEBusTiles is now the only class whose methods name an ME tile, and nothing loads IT until this call executes -- which
        // the guard above prevents without AE2. A guard inside this class could not have worked, because this class's own
        // method bodies would already have named the AE2-dependent types before any guard ran.
        return hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles.create(
                direction == Direction.INPUT, pos, state);
    }

    /**
     * The ME buses have no ticker of their own to install: their work is driven by the grid through
     * {@code IGridTickable}, which {@code MEItemBus} registers as a node service. Returning the inherited
     * {@code null} is therefore correct rather than an omission.
     */

    /**
     * Opens the bus's configuration GUI — the port of upstream's {@code BlockMEItemInputBus.onBlockActivated}
     * (U33-U53), in 1.20.1's shape.
     *
     * <h2>ON A SERVER WITHOUT AE2 THIS METHOD USED TO THROW — measured, then fixed (PLAN 690)</h2>
     *
     * <p><b>The finding, first measured by the self-test in §686:</b> invoking this method linked {@code MEBusTiles},
     * whose own bodies name {@code MEItemInputBus}/{@code MEItemOutputBus} — tiles whose hierarchy implements AE2's
     * {@code IInWorldGridNodeHost} — and the click died with
     * <b>{@code NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost}</b>. §686's check could not call this
     * method at all and was rewritten to read this class file instead, and the finding was recorded here rather than
     * fixed.</p>
     *
     * <p><b>The AE2 guard in {@code BlockMEMachineComponent.newBlockEntity} protects TILE CREATION, and this is a
     * SECOND unconditional entry point</b> — the same miss §483 fixed for {@code BlockMEPatternProvider}, one block
     * over. Fixed the same way: the guard at the top of the body, before the indirection is touched, returning
     * {@code PASS} so the block is inert rather than swallowing the click.</p>
     *
     * <p>§690's check performs this click for real and is the instrument that FAILED on the shipped code and passes
     * now; the negative-control line, verbatim, is the detail
     * {@code AE2 present=false; ... THE CLICK THREW java.lang.NoClassDefFoundError:
     * appeng/api/networking/IInWorldGridNodeHost}.</p>
     *
     * <h2>THE DEFECT THIS CLOSES, AND WHAT KIND OF DEFECT IT WAS</h2>
     *
     * <p><b>This is not a regression that the FLUID bus's GUI introduced — it is a port that was never written.</b>
     * The evidence is upstream's own class shape against the port's: upstream has TWO block classes
     * ({@code BlockMEItemInputBus} U32-U53 opens {@code GuiType.ME_ITEM_INPUT_BUS} and
     * {@code BlockMEItemOutputBus} U26-U47 opens {@code GuiType.ME_ITEM_OUTPUT_BUS}), each with its OWN
     * {@code onBlockActivated}; the port has ONE {@code BlockMEItemBus} carrying a {@link Direction} flag and no
     * {@code use} at all. So there was no method for a later stage to have deleted: {@code Direction} replaced a
     * class split, not an open path, and §665.5(b)-(e) records that the item bus's screen was already known-missing
     * while the fluid bus's was being built.</p>
     *
     * <p><b>And the fluid stage could not have taken it away:</b> {@code BlockMEItemBus} gained its
     * {@code Direction} enum and its three-argument constructor in §427, when the fluid classes were SPLIT BACK OUT
     * of it under rule A0 — a change that moved code away from this class, and the log's §665 file list does not
     * name this file at all.</p>
     *
     * <h2>What is ported, and what is deliberately not</h2>
     *
     * <p><b>Upstream's body is TWO steps</b> (U38-U51): a memory-card settings transfer
     * ({@code handleSettingsTransfer}, {@code BlockMEMachineComponent} U57-U76) and then {@code playerIn.openGui}.
     * <b>The memory-card step is NOT ported</b> — the mod's {@code SettingsTransfer} feature does not exist anywhere
     * in this port, which this class already recorded for upstream's {@code onBlockPlacedBy} NBT restore and §665.5(d)
     * recorded for the fluid bus's identical branch. The GUI step is ported, and it is the whole of what a right-click
     * does here.</p>
     *
     * <p><b>The GUI is opened for the INPUT direction only, and that is a stated gap rather than an oversight:</b>
     * upstream's output bus has its own screen (its container declares only the {@code SlotDisabled} grid — an output
     * bus has nothing to configure), and the port has neither that menu nor that screen, so an output bus still cannot
     * be opened. That is the state this class was in for BOTH directions, one direction less.</p>
     *
     * <p><b>The open step itself lives in {@link MEBusTiles} rather than here, and the reason is §413's rule rather
     * than tidiness.</b> A class file's method bodies NAME the types they use and the verifier resolves them when the
     * class LINKS — so a body here naming {@code MenuMEItemInputBus} would make the MENU a load-time dependency of
     * this block, and the menu names {@code MEItemInputBus}, whose hierarchy implements AE2's
     * {@code IInWorldGridNodeHost}. That is the {@code NoClassDefFoundError} of §404 reached one class further out.
     * {@code MEBusTiles} is already the one class whose methods name the ME tiles and is loaded only from behind the
     * AE2 guard, so the call goes there — and this body names nothing but that helper plus vanilla types.</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // THE GUARD §429's FINAL newBlockEntity CANNOT PROVIDE, and the same miss §483 fixed one block over (PLAN 690).
        //
        // newBlockEntity guards TILE CREATION; `use` is a SECOND unconditional entry point, and this one has to guard
        // itself. The block is registered in both configurations (all 8 ME blocks are, by design), so a right-click
        // reaches this method on a server without AE2 -- and the body below names MEBusTiles, whose OWN method bodies
        // name MEItemInputBus/MEItemOutputBus, whose hierarchy implements AE2's IInWorldGridNodeHost. Resolving
        // MEBusTiles therefore links it, verifying it resolves those tiles, and the click threw:
        //
        //     java.lang.NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost
        //
        // Measured twice: once by §686's first check, which had to be rewritten to read this class file instead of
        // calling this method, and again by §690's check, which performs the click. The check goes BEFORE the
        // indirection is touched, because loading MEBusTiles is itself the failure and no guard inside it can run.
        //
        // PASS, not SUCCESS: with AE2 absent the block is INERT -- nothing opened, nothing consumed -- which is
        // exactly what BlockMEPatternProvider.use returns in the same situation and the same degradation §429's
        // javadoc describes ("the block places, has no block entity"). A consumed click would additionally swallow
        // the item the player was holding.
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            return InteractionResult.PASS;
        }
        if (direction == Direction.INPUT) {
            // THE MEMORY CARD STEP RUNS FIRST, AND IT CAN CONSUME THE CLICK (audit row 43).
            //
            // Upstream's order is exactly this: BlockMEItemInputBus.onBlockActivated (U38-U51) performs the
            // settings transfer and only reaches playerIn.openGui when the transfer did NOT handle the click. The
            // port had the order to build from and nothing to put in front of the menu -- §690 ported the GUI step
            // alone -- so this line is the missing half rather than a reordering.
            //
            // Returned SUCCESS on a handled transfer: the click is spent on the card, and a menu opening over it
            // would be the port inventing a behaviour upstream does not have. When the transfer declines (no card,
            // or a card naming another component), the menu opens below exactly as before.
            if (hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles.handleItemBusSettingsTransfer(
                    level, pos, player, hand, state.getBlock().getDescriptionId())) {
                return InteractionResult.SUCCESS;
            }
            // The return value is deliberately ignored: a client-side click, a non-server player and a missing block
            // entity all mean "there is no menu to open", and none of them may fall through to a vanilla interaction.
            hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles.openItemInputBusMenu(level, pos, player);
        } else {
            // The output bus owns the same AE2 memory-card contract now that its configured stack cap is a
            // SettingsTransfer provider. Keep the upstream order: card transfer consumes the click before the GUI.
            if (hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles.handleItemBusSettingsTransfer(
                    level, pos, player, hand, state.getBlock().getDescriptionId())) {
                return InteractionResult.SUCCESS;
            }
            hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles.openItemOutputBusMenu(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }
}
