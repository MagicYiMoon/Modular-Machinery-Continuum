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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.menu.SlotSemantics;
import hellfirepvp.modularmachinery.common.menu.MenuMEPatternProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Reads the pattern provider menu's OWN slot geometry, for the self-test's assertion (PLAN 712, extended in 737).
 *
 * <h2>Why this is a separate class rather than a method in the suite</h2>
 *
 * <p>It names {@code appeng.menu.SlotSemantics}, and a method body is VERIFIED WHEN ITS OWNING CLASS IS LINKED — so
 * a copy of this code inside {@code MachineSelfTest} would make the whole suite unloadable on the standalone
 * configuration, which has no AE2 at all. This is the project's fourth boundary (§452 method level, §478 registry
 * level, §481 client level, and here the SELF-TEST level), and the shape is the same one {@code MEBusTiles} uses:
 * the caller's body names a class that names no AE2 type, and THIS class is reached only from inside an AE2
 * guard.</p>
 *
 * <h2>What it returns, and what it deliberately does NOT decide</h2>
 *
 * <p>Slot COUNTS and each slot's own {@code x}/{@code y}, in slot order. The COMPARISON against upstream's numbers
 * and against the texture's wells happens in the suite, so this class carries no geometry rule of its own that could
 * drift from the one being asserted (§629: one reader per quantity).</p>
 *
 * <h2>Why the counts are fields and not {@code xy.length / 2}</h2>
 *
 * <p><b>Because the first version of this probe's consumer got that division wrong and the check failed for it.</b>
 * The check compared {@code playerRowXY().length} (an int[] of x,y PAIRS, so 54 for 27 slots) against the number of
 * slots the player has (27), read 54, and reported "the menu built 54 player row slot(s)" on a menu whose own census
 * said 27 — a WRONG VERDICT produced by a units mistake in the instrument, which is the defect class this project
 * has paid for repeatedly (§162/§163/§176: a check that measures its own tooling). The counts are therefore separate
 * components, computed from the lists, and nothing in the suite divides by two.</p>
 *
 * @param patternSlots   the number of slots in the {@code ENCODED_PATTERN} semantic
 * @param patternXY      the pattern slots' coordinates, as x,y pairs in slot order (length = 2 x patternSlots)
 * @param playerRowSlots the number of slots in the {@code PLAYER_INVENTORY} semantic
 * @param playerRowXY    those slots' coordinates, as x,y pairs in slot order
 * @param hotbarSlots    the number of slots in the {@code PLAYER_HOTBAR} semantic
 * @param hotbarXY       those slots' coordinates, as x,y pairs in slot order
 * @param storageSlots   the number of slots in the {@code STORAGE} semantic — the independent storage, PLAN 737
 * @param storageXY      those slots' coordinates, as x,y pairs in slot order
 * @param workMode       what the menu's data slot reports for the work mode, i.e. the number a SCREEN would draw from
 * @param workModeCount  how many constants the tile's own mode enum declares, read from the enum rather than written
 *                       here — a literal would make the check below agree with a stale count
 * @param totalSlots     every slot the menu built, including the player's
 * @param shape          a raw census of what was built, for a failure to be readable without re-running
 */
public record MEPatternProviderProbe(int patternSlots, int[] patternXY,
                                     int playerRowSlots, int[] playerRowXY,
                                     int hotbarSlots, int[] hotbarXY,
                                     int storageSlots, int[] storageXY,
                                     int workMode, int workModeCount,
                                     int totalSlots, String shape) {

    /**
     * Builds the REAL menu over the block at {@code pos} and reads what it produced.
     *
     * <p>The menu is constructed directly rather than through {@code player.openMenu}: the open path ends in
     * {@code sendAllDataToRemote()}, and the suite's fake player has a connection that discards packets
     * (§657.2(c)) — the fluid bus's check reports that as a harness limit, and this check has no reason to walk into
     * it, because the slot list is built in the CONSTRUCTOR on both sides.</p>
     *
     * @return the geometry, or {@code null} when no pattern provider stands at {@code pos} — which the caller must
     *         report as a failure rather than as a skip
     */
    public static MEPatternProviderProbe of(ServerPlayer player, BlockPos pos) {
        TileMEPatternProvider tile = player.level().getBlockEntity(pos) instanceof TileMEPatternProvider provider
                ? provider : null;
        if (tile == null) {
            return null;
        }
        MenuMEPatternProvider menu = new MenuMEPatternProvider(0, player.getInventory(), pos);
        // THE DATA SLOTS ARE FILLED BY `broadcastChanges`, AND `addDataSlots` ONLY ALLOCATES THEM.
        //
        // This is the one piece of measurement hygiene this probe needs, and getting it wrong is the defect this whole
        // round is about: `AbstractContainerMenu.addDataSlots(data)` does NOT read `data` -- it appends a placeholder
        // `DataSlot` per index whose value starts at 0 (measured: `addDataSlot(DataSlot.standalone())` repeated
        // `getCount()` times), and the values arrive on the first `broadcastChanges`, which vanilla's
        // `ServerPlayer.tick` calls once per tick. A probe that skipped this line would read `workModeOrdinal() == 0`
        // for EVERY mode -- i.e. it would report DEFAULT for a provider set to CRAFTING_LOCK_MODE, which is a check
        // passing for the wrong reason in the exact direction that hides a broken enum. Calling it once here is the
        // same call the server makes, and the probe deliberately makes no other one.
        menu.broadcastChanges();
        List<Slot> patterns = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        List<Slot> rows = menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
        List<Slot> hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
        List<Slot> storage = menu.getSlots(SlotSemantics.STORAGE);
        // THE WORK MODE IS READ FROM THE ENUM, NOT FROM A LITERAL (rule: "a literal that happens to be a valid value is
        // still the wrong value"). The count comes from `values().length` so that a sixth mode added upstream makes this
        // probe report six rather than making a check below pass against a five it remembered.
        int modeCount = hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting.values().length;
        return new MEPatternProviderProbe(patterns.size(), pairs(patterns),
                rows.size(), pairs(rows),
                hotbar.size(), pairs(hotbar),
                storage.size(), pairs(storage),
                menu.workModeOrdinal(), modeCount,
                menu.slots.size(), shape(menu, patterns, rows, hotbar, storage));
    }

    /**
     * <b>Drives one work-mode change through the real packet path and reports everything a check needs.</b>
     *
     * <h2>Why this is here and NOT in the suite, which is where it was written first</h2>
     *
     * <p>The first version of PLAN 737's mode check built the menu, made it the player's open menu and called
     * {@code PktMEPatternProviderAction.apply} <b>inside {@code MachineSelfTest}</b>, guarded by an
     * {@code isLoaded("ae2")} branch. It compiled and the INTEGRATED configuration passed. The STANDALONE one died with
     * <pre>
     *   java.lang.RuntimeException: Cannot find class appeng/menu/AEBaseMenu
     *     at cpw.mods.modlauncher.TransformerClassWriter.computeHierarchyFromFile(TransformerClassWriter.java:161)
     *     ... at ServerSelfTestTrigger.onServerStarted(ServerSelfTestTrigger.java:73)
     * </pre>
     * and the trace is the lesson: <b>ModLauncher computes stack-map frames for a class WHILE TRANSFORMING IT, and a
     * frame merge between two of its own local variable types forces the verifier to resolve their common
     * supertype</b> -- {@code AEBaseMenu}, for a local holding a {@code MenuMEPatternProvider}. The guard is irrelevant
     * to that: it protects the CALL, and the frame is computed for the whole METHOD at load time. This port's earlier
     * boundary rules (§452 method level, §478 registry level, §481 client level, §712 self-test level) are all the same
     * rule at different granularities, and this is the strictest form of it yet: <b>a type may not even appear in a
     * LOCAL VARIABLE of a class the standalone server loads, guard or no guard.</b></p>
     *
     * <p>So the menu-touching half lives here, where {@code appeng.menu.SlotSemantics} already lives for exactly this
     * reason -- this class is reached only from inside an AE2 guard, and IT is allowed to have AE2-typed locals.</p>
     *
     * <h2>What it reports, and why each field is a measurement rather than a derived statement</h2>
     *
     * <p>Every value below is either read off the tile, off the menu the block's own open path builds, or off a
     * freshly loaded copy of the update tag. Nothing is computed from anything else here, so the check that consumes it
     * can fail for exactly one reason at a time.</p>
     *
     * @param refusedWithoutMenu whether the packet was refused while the player had NO menu open for this block -- the
     *                           authorization rule, asserted as a negative
     * @param silentRefusal      whether that refusal left the mode where it was, read before and after the call
     * @param beforeOrdinal      the tile's mode before the refused call -- the value the refusal had to leave alone
     * @param afterRefusalOrdinal the tile's mode immediately AFTER the refused call and BEFORE the authorized one, so
     *                           "the refusal moved nothing" is a statement about two real readings of the same moment
     *                           rather than about a value that could not have changed
     * @param openForThisBlock   whether the menu recognised itself as the menu for this position
     * @param applied            whether the authorized call reported success
     * @param targetOrdinal      the mode the check asked for, so a caller never has to reconstruct it
     * @param afterTileOrdinal   the tile's mode after the authorized call -- the value the server holds
     * @param throughDataSlots   the mode the MENU reports, i.e. what a screen would draw from
     * @param afterLoadOrdinal   the mode a tile loaded from the live tile's own {@code getUpdateTag()} reports, which is
     *                           what a client's {@code onDataPacket} does
     * @param savedTagOrdinal    the mode inside that same tag, read back through the enum's own reader -- not through
     *                           {@code getByte}, so a raw byte and a mode cannot be confused
     * @param nextOrdinal        the enum's own next mode after {@code afterTileOrdinal}
     */
    public record WorkModeProbe(boolean refusedWithoutMenu, boolean silentRefusal, int beforeOrdinal,
                                int afterRefusalOrdinal, boolean openForThisBlock, boolean applied, int targetOrdinal,
                                int afterTileOrdinal, int throughDataSlots, int afterLoadOrdinal, int savedTagOrdinal,
                                int nextOrdinal) {

        /**
         * Runs the sequence once, against the block at {@code pos}.
         *
         * @param target the mode to ask for. <b>The caller must pass a mode the provider is NOT in</b> -- a set to the
         *               value already held passes on a handler that does nothing, which is the failure this whole
         *               record exists to make impossible to hide
         * @return the measurements, or {@code null} when no pattern provider stands at {@code pos}
         */
        @org.jetbrains.annotations.Nullable
        public static WorkModeProbe of(ServerPlayer player, BlockPos pos, WorkModeSetting target,
                                       java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<?>> type,
                                       net.minecraft.world.level.block.state.BlockState state) {
            if (!(player.level().getBlockEntity(pos) instanceof TileMEPatternProvider provider)) {
                return null;
            }
            int before = provider.getWorkMode().ordinal();

            // THE REFUSAL FIRST, so it is measured before a menu exists to authorise the call.
            boolean refused = !hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork
                    .PktMEPatternProviderAction.apply((net.minecraft.server.level.ServerLevel) player.level(), player,
                            pos, hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork
                                    .PktMEPatternProviderAction.SET_WORK_MODE, target.ordinal());
            int afterRefusal = provider.getWorkMode().ordinal();
            boolean silent = afterRefusal == before;

            // And then the real thing, through the menu the block's own open path builds.
            MenuMEPatternProvider menu = new MenuMEPatternProvider(0, player.getInventory(), pos);
            boolean openForThisBlock = MenuMEPatternProvider.isOpenFor(menu, pos);
            var previousMenu = player.containerMenu;
            player.containerMenu = menu;
            boolean applied = hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork
                    .PktMEPatternProviderAction.apply((net.minecraft.server.level.ServerLevel) player.level(), player,
                            pos, hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork
                                    .PktMEPatternProviderAction.SET_WORK_MODE, target.ordinal());
            int afterTile = provider.getWorkMode().ordinal();

            // The window's own lane: `broadcastChanges` is what the server calls every tick, and without it the data
            // slot a screen reads is still its allocated 0 -- which would make this field agree with DEFAULT for every
            // mode. See the note in `of(ServerPlayer, BlockPos)`.
            menu.broadcastChanges();
            int throughDataSlots = menu.workModeOrdinal();
            player.containerMenu = previousMenu;

            // The save: the live tile's own update tag, loaded into a fresh tile (a client's onDataPacket), and the
            // same tag's mode read back through the enum's reader rather than as a raw byte.
            TileMEPatternProvider clientCopy = new TileMEPatternProvider(type, pos, state);            net.minecraft.nbt.CompoundTag tag = provider.getUpdateTag();
            clientCopy.load(tag);
            int afterLoad = clientCopy.getWorkMode().ordinal();
            int savedTagMode = WorkModeSetting.fromOrdinal(tag.getByte("workMode")).ordinal();

            return new WorkModeProbe(refused, silent, before, afterRefusal, openForThisBlock, applied, target.ordinal(),
                    afterTile, throughDataSlots, afterLoad, savedTagMode, WorkModeSetting.nextOrdinal(afterTile));
        }
    }

    /**
     * A raw census of what the menu actually built: the semantic lists' sizes, how many DISTINCT slot objects each
     * holds, and the first and last coordinate of each.
     *
     * <p>Added because the check's first run on the integrated configuration failed with numbers that could not all
     * be true at once, and this line is what told the two possible causes apart: a semantic list holding the same
     * slot twice (distinct = half) versus a list holding slots that were really placed twice (distinct = size). It
     * printed 27/27 and 9/9 — the MENU was right and the instrument was wrong, which is why it stays.</p>
     */
    private static String shape(MenuMEPatternProvider menu, List<Slot> patterns, List<Slot> rows,
                                List<Slot> hotbar, List<Slot> storage) {
        return "slots=" + menu.slots.size()
                + " pattern=" + patterns.size() + "/distinct " + distinct(patterns)
                + " rows=" + rows.size() + "/distinct " + distinct(rows)
                + " hotbar=" + hotbar.size() + "/distinct " + distinct(hotbar)
                + " storage=" + storage.size() + "/distinct " + distinct(storage)
                + "; rows first " + at(rows, 0) + " last " + at(rows, rows.size() - 1)
                + "; hotbar first " + at(hotbar, 0) + " last " + at(hotbar, hotbar.size() - 1)
                + "; storage first " + at(storage, 0) + " last " + at(storage, storage.size() - 1);
    }

    private static int distinct(List<Slot> slots) {
        java.util.Set<Slot> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        seen.addAll(slots);
        return seen.size();
    }

    private static String at(List<Slot> slots, int index) {
        if (index < 0 || index >= slots.size()) {
            return "n/a";
        }
        Slot slot = slots.get(index);
        return "(" + slot.x + "," + slot.y + ")";
    }

    private static int[] pairs(List<Slot> slots) {
        int[] out = new int[slots.size() * 2];
        for (int i = 0; i < slots.size(); i++) {
            out[i * 2] = slots.get(i).x;
            out[i * 2 + 1] = slots.get(i).y;
        }
        return out;
    }
}
