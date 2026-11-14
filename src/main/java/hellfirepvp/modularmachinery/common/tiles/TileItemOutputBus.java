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
 * Item output bus — the machine's outbound item buffer.
 *
 * <p>Rejects external insertion (see {@code TileItemBus#createInventory}) so output slots cannot be
 * used as free storage; the machine writes into them directly.
 */
public class TileItemOutputBus extends TileItemBus {

    public TileItemOutputBus(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.ITEM_OUTPUT_BUS, pos, state, IOType.OUTPUT, slotsOf(state));
    }

    /**
     * Pushes to every accepted neighbour, once per random 5..60 tick interval (PLAN section 372).
     *
     * <p>The neighbour walk is inherited -- see {@link TileItemBus#forEachNeighbourHandler} for the six rules it carries,
     * including that buses do not chain. Only the body differs between this bus and the input bus.
     */
    public void step(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        forEachNeighbourHandler(this::outputToExternal);
    }

    /**
     * Moves what it can from this bus's inventory into one neighbour's handler.
     *
     * <p><b>One rule here has no counterpart on the input side, and it is the reason this body was read from upstream
     * rather than derived by inverting the input bus:</b> a neighbour slot that is already full is SKIPPED BEFORE the
     * insert is attempted. Without it, every pass would offer a stack to a slot that cannot take it -- which is not a
     * correctness bug, but it is the difference between a bus that notices its destination is full and one that keeps
     * asking.
     *
     * <p>The external slot's limit comes from {@code external.getSlotLimit(slot)}, not from the item, because a
     * neighbour may impose a smaller limit than the item's own stack size.
     *
     * <p>Insertion is attempted with {@code simulate=false} and the returned remainder is kept IN the internal slot, so
     * a partial insert leaves the rest for the next pass rather than dropping it.
     */
    private void outputToExternal(net.minecraftforge.items.IItemHandler external) {
        for (int externalSlot = 0; externalSlot < external.getSlots(); externalSlot++) {
            net.minecraft.world.item.ItemStack externalStack = external.getStackInSlot(externalSlot);
            int slotLimit = external.getSlotLimit(externalSlot);
            if (!externalStack.isEmpty() && externalStack.getCount() >= slotLimit) {
                continue;
            }
            for (int internalSlot = 0; internalSlot < inventory.getSlots(); internalSlot++) {
                net.minecraft.world.item.ItemStack internalStack = inventory.getStackInSlot(internalSlot);
                if (internalStack.isEmpty()) {
                    continue;
                }
                net.minecraft.world.item.ItemStack remainder =
                        external.insertItem(externalSlot, internalStack, false);
                if (remainder.getCount() == internalStack.getCount()) {
                    // nothing was accepted at this neighbour slot; try the next internal slot
                    continue;
                }
                inventory.setStackInSlot(internalSlot, remainder);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
    }
}
