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
