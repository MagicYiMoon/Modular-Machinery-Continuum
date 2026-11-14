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
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import hellfirepvp.modularmachinery.common.tiles.base.MEMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Base for the Applied Energistics 2 item buses: a buffer the machine reads and writes, wired to the ME network.
 *
 * <h2>The two behaviours taken from upstream, neither of which is implied by "an ME version of a bus"</h2>
 *
 * <h3>1. UNLIMITED stacks per slot</h3>
 *
 * <p>Upstream's line is <code>inventory.setStackLimit(Integer.MAX_VALUE, slotIDs)</code>. **An ME bus holds network QUANTITIES,
 * not 64-capped stacks** -- a single input slot can legitimately hold thousands of an item because the network handed them over at
 * once. The port's inventory overrides {@code getSlotLimit} to match, and the constant looks like a typo without this sentence.
 *
 * <h3>2. The 100-tick full re-check</h3>
 *
 * <pre>
 * changedSlots[]     a slot marked dirty by the inventory's own change callback
 * failureCounter[]   how many ticks remain before a FAILED slot is retried
 * lastFullCheckTick  when every slot was last examined regardless of dirtiness
 * </pre>
 *
 * <p>{@link #getNeedUpdateSlots} returns only dirty slots normally, and EVERY slot once per 100 ticks. **The periodic full pass is
 * the point:** the cheap path notices a slot the bus itself touched, and the full pass catches a slot that changed while the bus was
 * not looking -- a chunk reload, or a neighbour that filled up and then emptied. **Without it a bus can stop pushing forever and
 * nothing would report it.**
 *
 * <p>And {@code failureCounter} exists so a push that FAILED is retried after a delay rather than being marked clean and forgotten.
 * **A bus that cleared the dirty flag on the first failure would silently stop moving that stack.**
 *
 * <h2>Why this does not extend the ordinary {@code TileItemBus}</h2>
 *
 * <p>Upstream's {@code MEItemBus} extends {@code MEMachineComponent}, not {@code TileItemBus}, and the port keeps that. The two
 * hierarchies answer different questions -- one owns a grid node, the other owns a machine-facing buffer -- and Java's single
 * inheritance means a class cannot do both.
 *
 * <p><b>This is why the {@code slotLimit()} hook added to {@code TileItemBus} in PLAN section 396 is NOT on this path</b>, and that
 * is recorded rather than glossed: the ME buses carry their own inventory here, exactly as upstream's do. The hook remains for a
 * future ME bus that wants the ordinary buffer's bookkeeping, and it is harmless where it is.
 */
public abstract class MEItemBus extends MEMachineComponent implements IGridTickable {

    /** How often every slot is re-examined regardless of whether it was marked dirty. Upstream's constant. */
    protected static final long FULL_CHECK_INTERVAL = 100L;

    /** How many ticks a slot waits after a failed transfer before it is offered again. Upstream's shape, port's constant. */
    protected static final int FAILURE_BACKOFF_TICKS = 20;

    protected final ItemStackHandler inventory;

    /** Set by the inventory's change callback; cleared as slots are offered. Guarded by {@code this}. */
    protected final boolean[] changedSlots;

    /** Ticks remaining before each slot is retried after a failure. Indexed the same as {@code changedSlots}. */
    protected final int[] failureCounter;

    private long lastFullCheckTick = 0L;

    /** True for the duration of a transfer pass, so a slot changed BY the pass is not re-marked as externally changed. */
    protected boolean inTick = false;

    /**
     * How many buffer slots this bus owns. <b>Held so that a load can be checked against it</b>, and NOT read from the
     * inventory, because the inventory is the thing that can come back the wrong size -- see
     * {@link #loadInventoryClamped}.
     */
    protected final int slots;

    protected MEItemBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state,
                        int slots) {
        super(type, pos, state);
        this.slots = slots;
        this.inventory = createInventory(slots);
        this.changedSlots = new boolean[slots];
        this.failureCounter = new int[slots];
        // Node configuration, so it is safe before create() runs in onLoad -- and it is what makes the network call
        // tickingRequest at all.
        getMainNode().addService(IGridTickable.class, this);
    }

    /**
     * What one slot of this bus may hold.
     *
     * <p>{@code Integer.MAX_VALUE} is upstream's own line -- {@code inventory.setStackLimit(Integer.MAX_VALUE, slotIDs)}
     * on the INPUT bus -- and the class javadoc above says why: an ME bus holds network QUANTITIES, not 64-capped
     * stacks. <b>Overridden in ONE place, by the output bus, because upstream gives that direction a settable cap</b>
     * ({@code configuredStackSize}, upstream U24-U36/U147): the hook exists so the cap can be read where it is used
     * rather than copied into a per-slot limit list that can drift from the inventory's size.</p>
     */
    protected int slotLimit() {
        return Integer.MAX_VALUE;
    }

    /**
     * @param size the slot count for this bus's tier
     * @return the inventory, UNCAPPED per slot -- see the class javadoc, and {@link #slotLimit} for the one direction
     *         whose cap is a setting
     */
    protected ItemStackHandler createInventory(int size) {
        return new ItemStackHandler(size) {
            @Override
            public int getSlotLimit(int slot) {
                // Upstream's setStackLimit(Integer.MAX_VALUE, ...). Everything else about this handler is Forge's default; the
                // machine reads and writes it through the ITEM_HANDLER capability either way.
                return slotLimit();
            }

            @Override
            protected void onContentsChanged(int slot) {
                synchronized (MEItemBus.this) {
                    // A change made BY a transfer pass is not an external change and must not re-arm the slot the pass is
                    // currently working on -- that would make every pass re-offer the same slot forever.
                    if (!inTick) {
                        changedSlots[slot] = true;
                    }
                }
                setChanged();
            }
        };
    }

    /** @return the buffer, for a subclass's transfer code */
    public ItemStackHandler getInternalInventory() {
        return inventory;
    }

    /**
     * The slots worth offering on this pass.
     *
     * @return slot indices: every slot once per {@link #FULL_CHECK_INTERVAL} ticks, otherwise the ones marked dirty whose
     *         failure backoff has elapsed
     */
    protected synchronized int[] getNeedUpdateSlots() {
        long now = level == null ? 0L : level.getGameTime();

        if (lastFullCheckTick + FULL_CHECK_INTERVAL < now) {
            lastFullCheckTick = now;
            int[] all = new int[inventory.getSlots()];
            for (int i = 0; i < all.length; i++) {
                all[i] = i;
            }
            // Ageing the failure counters happens here, once per full pass, rather than every tick: a counter is only
            // consulted when the slot is offered, so decrementing it more often would just cost time.
            for (int i = 0; i < failureCounter.length; i++) {
                if (failureCounter[i] > 0) {
                    failureCounter[i]--;
                }
            }
            return all;
        }

        java.util.List<Integer> needed = new java.util.ArrayList<>(changedSlots.length);
        for (int i = 0; i < changedSlots.length; i++) {
            if (changedSlots[i] && failureCounter[i] <= 0) {
                needed.add(i);
            }
        }
        int[] result = new int[needed.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = needed.get(i);
        }
        return result;
    }

    /** Marks a slot's transfer as failed, so it is retried after the backoff instead of being forgotten. */
    protected synchronized void markSlotFailed(int slot) {
        if (slot >= 0 && slot < failureCounter.length) {
            failureCounter[slot] = FAILURE_BACKOFF_TICKS;
            // Left dirty ON PURPOSE: the slot still has something to move, it just needs to wait.
            changedSlots[slot] = true;
        }
    }

    /** Marks a slot's buffer as settled, so it is not offered again until something changes it. */
    protected synchronized void markSlotClean(int slot) {
        if (slot >= 0 && slot < changedSlots.length) {
            changedSlots[slot] = false;
        }
    }

    /** @return whether any slot holds anything */
    public boolean hasItem() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** @return whether any slot is marked dirty; a subclass uses this to ask the grid to tick it sooner */
    public boolean hasChangedSlots() {
        for (boolean changed : changedSlots) {
            if (changed) {
                return true;
            }
        }
        return false;
    }

    private final TickingRequest tickingRequest =
            new TickingRequest(5, 120, false, true);

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 5..120 ticks, never sleeping (the buffer's contents are the machine's business, not the grid's), and alertable so a
        // subclass that marks a slot dirty can wake the node instead of waiting out the interval.
        return tickingRequest;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        int[] slots = getNeedUpdateSlots();
        if (slots.length == 0) {
            // Nothing dirty and the full pass is not due. IDLE rather than SLEEP: the bus must still be woken by an external
            // change, and a sleeping device is not.
            return TickRateModulation.IDLE;
        }
        inTick = true;
        try {
            doBusWork(slots);
        } finally {
            inTick = false;
        }
        return hasChangedSlots() ? TickRateModulation.FASTER : TickRateModulation.SAME;
    }

    /**
     * Moves items between this buffer and the grid, for the given slots.
     *
     * @param slots the slots {@link #getNeedUpdateSlots} selected; a subclass marks each one clean or failed through
     *              {@link #markSlotClean} / {@link #markSlotFailed}
     */
    protected abstract void doBusWork(int[] slots);

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        loadInventoryClamped(inventory, tag, "inventory");
    }

    /**
     * Restores one of this bus's inventories from NBT, <b>clamped to that handler's own slot count</b>.
     *
     * <h2>Why a bare {@code deserializeNBT} is a hazard here, stated as the two failures it produces</h2>
     *
     * <pre>
     *   Forge 47.3.0  ItemStackHandler.deserializeNBT (sources jar, L186-L188):
     *       setSize(nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : stacks.size());
     *
     *   this class    load(...)  ->  loadInventoryClamped(inventory, tag, "inventory")
     * </pre>
     *
     * <p><b>The size is taken from the SAVED tag, so a handler read from a tag with a different {@code Size} comes
     * back with THAT size</b> -- which the project had already measured once, for {@code TileUpgradeBus} (see that
     * class's javadoc and PLAN 275). On the ME item buses the same line is worse than a wrong grid, because this
     * class's parallel arrays are sized by the CONSTRUCTOR's count and never by the handler:</p>
     *
     * <pre>
     *   inventory        ItemStackHandler(slots)      <- resized by the tag
     *   changedSlots     new boolean[slots]           <- NOT resized
     *   failureCounter   new int[slots]               <- NOT resized
     * </pre>
     *
     * <p>So a 27-slot tag read by a 16-slot bus produced (a) {@code onContentsChanged(26)} writing past
     * {@code changedSlots} -- an {@link ArrayIndexOutOfBoundsException} the first time the network or the machine
     * touched one of the extra slots -- and (b) {@code ItemStackHandler.getStackInSlot} THROWING
     * {@code "Slot 26 not in valid range - [0,16)"} from any reader that iterated the handler it was handed. The
     * mismatched handler is not a cosmetic problem; it is a crash with two entry points.</p>
     *
     * <h2>Why CLAMPING rather than REBUILDING, which is what the other two repairs in this project do</h2>
     *
     * <p>{@code TileItemBus.getInventory} and {@code TileUpgradeBus.getUpgradeInventory} both rebuild a handler whose
     * size disagrees, and both accept losing its contents. That is right where the count comes from a BLOCK STATE: a
     * rebuild re-derives it from the block and the tile is correct afterwards. <b>Here the count is a constant of the
     * class</b> -- there is no state to re-derive it from -- so a rebuild would always be needed for an old save and
     * would always throw away every stack in the bus. What is done instead keeps the items that still have a home and
     * drops only the ones in slots the bus no longer has ({@code Size} is rewritten, out-of-range {@code Items} are
     * filtered out), which is exactly the shape a fresh save of the new geometry would have.</p>
     *
     * <p><b>The dropped slots are logged rather than silently vanished</b>, because "my bus lost its contents" with
     * nothing in the log is the report this project has had to answer before.</p>
     *
     * @param handler the handler to fill, whose current slot count is the authority
     * @param parent  the tag that carries {@code key}
     * @param key     the inventory's own key, e.g. {@code "inventory"}
     */
    protected void loadInventoryClamped(ItemStackHandler handler, net.minecraft.nbt.CompoundTag parent, String key) {
        if (!parent.contains(key)) {
            return;
        }
        net.minecraft.nbt.CompoundTag saved = parent.getCompound(key);
        int expected = handler.getSlots();
        if (!saved.contains("Size", net.minecraft.nbt.Tag.TAG_INT) || saved.getInt("Size") == expected) {
            // The saved size is this bus's own, or absent (in which case deserializeNBT keeps the current size).
            // Nothing to repair, so the tag goes through untouched -- including every field this class does not know.
            handler.deserializeNBT(saved);
            return;
        }

        int dropped = 0;
        net.minecraft.nbt.CompoundTag normalized = saved.copy();
        normalized.putInt("Size", expected);
        net.minecraft.nbt.ListTag items = normalized.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag kept = new net.minecraft.nbt.ListTag();
        for (int index = 0; index < items.size(); index++) {
            net.minecraft.nbt.CompoundTag item = items.getCompound(index);
            int slot = item.getInt("Slot");
            if (slot >= 0 && slot < expected) {
                kept.add(item);
            } else {
                dropped++;
            }
        }
        normalized.put("Items", kept);
        handler.deserializeNBT(normalized);
        if (dropped > 0) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                    "[MMCE] ME bus at {} was saved with {} slot(s) and {} slot(s) is what this bus has: {} stack(s) in"
                            + " the removed slots were dropped, the rest were kept",
                    worldPosition, saved.getInt("Size"), expected, dropped);
        }
    }

    @Override
    public @NotNull <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            @NotNull net.minecraftforge.common.capabilities.Capability<T> capability,
            @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
        if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            // The machine reads and writes this buffer through the handler, exactly as it does for an ordinary bus.
            return net.minecraftforge.common.util.LazyOptional.of(() -> inventory).cast();
        }
        return super.getCapability(capability, side);
    }
}
