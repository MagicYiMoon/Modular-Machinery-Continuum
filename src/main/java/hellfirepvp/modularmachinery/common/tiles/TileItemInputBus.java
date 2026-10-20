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
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Item input bus — the machine's inbound item buffer.
 *
 * <p>1.12.2 had a no-arg constructor and read its size from block metadata
 * ({@code ((BlockBus) getBlockType()).getSizeFromMeta(...)}). Metadata is gone, and the
 * {@code BlockEntityType} factory is only handed {@code (pos, state)} — so the size is resolved from
 * the blockstate, which is where the variant lives on 1.20.1.
 */
public class TileItemInputBus extends TileItemBus {

    public TileItemInputBus(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.ITEM_INPUT_BUS, pos, state, IOType.INPUT, slotsOf(state));
    }

    /**
     * Pulls from every accepted neighbour, once per random 5..60 tick interval (PLAN section 370).
     *
     * <p>Upstream's {@code doRestrictedTick} called {@code inputFromExternal} per neighbour after its own guard and
     * face walk; those six rules now live in {@link TileItemBus#forEachNeighbourHandler} so this bus and the output bus
     * cannot drift apart on the ones that are easy to get wrong -- most of all that <b>buses do not chain</b>.
     *
     * @param level the level, always server-side when this is reached
     * @param pos this bus's position
     */
    public void step(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        forEachNeighbourHandler(this::inputFromExternal);
    }

    /**
     * Moves what it can from one neighbour's handler into this bus's inventory.
     *
     * <p>The bounds differ on purpose and both are upstream's: {@code maxCanExtract} is capped by the EXTERNAL stack's
     * own {@code getMaxStackSize} as well as its count, so a stack of 64 unstackable-to-1 items does not try to move 64.
     *
     * <p>Two branches, and the second is the one that is easy to omit: an EMPTY internal slot takes the whole
     * extraction, while a slot holding the SAME item only takes what fits before its own limit. Dropping the second
     * branch leaves a bus that fills one slot per pass and looks like it is working.
     */
    private void inputFromExternal(net.minecraftforge.items.IItemHandler external) {
        for (int externalSlot = 0; externalSlot < external.getSlots(); externalSlot++) {
            net.minecraft.world.item.ItemStack externalStack = external.getStackInSlot(externalSlot);
            if (externalStack.isEmpty()) {
                continue;
            }
            for (int internalSlot = 0; internalSlot < inventory.getSlots(); internalSlot++) {
                net.minecraft.world.item.ItemStack internalStack = inventory.getStackInSlot(internalSlot);
                int maxCanExtract = Math.min(externalStack.getCount(), externalStack.getMaxStackSize());

                if (internalStack.isEmpty()) {
                    net.minecraft.world.item.ItemStack extracted =
                            external.extractItem(externalSlot, maxCanExtract, false);
                    if (!extracted.isEmpty()) {
                        inventory.setStackInSlot(internalSlot, extracted);
                    }
                } else if (net.minecraft.world.item.ItemStack.isSameItemSameTags(internalStack, externalStack)) {
                    int capacity = Math.min(inventory.getSlotLimit(internalSlot),
                            internalStack.getMaxStackSize()) - internalStack.getCount();
                    if (capacity <= 0) {
                        continue;
                    }
                    int wanted = Math.min(maxCanExtract, capacity);
                    net.minecraft.world.item.ItemStack extracted =
                            external.extractItem(externalSlot, wanted, false);
                    if (!extracted.isEmpty()) {
                        internalStack.grow(extracted.getCount());
                        inventory.setStackInSlot(internalSlot, internalStack);
                    }
                }
            }
        }
    }
}
