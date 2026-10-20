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

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * {@code modularmachinery:blockmeiteminputbus} -- keeps a CONFIGURED set of items available to the machine.
 *
 * <h2>This is a sustaining buffer, not a one-way pull</h2>
 *
 * <p>PLAN section 399 read upstream's five rules, and they are worth restating because the class name invites the wrong design.
 * Per slot, the CONFIG inventory (what the bus is asked to keep) is compared with the buffer (what the machine reads):</p>
 *
 * <ol>
 *   <li><b>config empty, buffer non-empty</b> -- push the buffer's contents INTO the network. It gives back what it was not asked
 *       to hold.</li>
 *   <li><b>config set, buffer does not match</b> -- and only if the buffer is empty OR its contents fit ENTIRELY into the network,
 *       extract the configured item and fill the slot.</li>
 *   <li><b>config matches, counts equal</b> -- nothing.</li>
 *   <li><b>config count &gt; buffer count</b> -- top the slot up from the network, and reset {@code failureCounter[slot]}.</li>
 *   <li><b>config count &lt; buffer count</b> -- push the excess back.</li>
 * </ol>
 *
 * <p><b>Rules 1 and 5 both INSERT.</b> A class named "input" that never calls insert is the one-way-pull mistake: items the machine
 * did not ask for would accumulate forever, and nothing would top a consumed stack back up.</p>
 *
 * <h2>And rule 2's condition prevents silent item loss</h2>
 *
 * <p>It fills the slot only when the old contents fit entirely into the network. <b>A port that skipped that test would overwrite
 * the buffer whenever the configured item changed, and the previous stack would go nowhere.</b> That is why the check is written as
 * an explicit precondition rather than folded into the branch.</p>
 *
 * <h2>What the port does not need that upstream does</h2>
 *
 * <p>Upstream converts between {@code ItemStack} and {@code IAEItemStack} in two directions and caches the results in a static
 * {@code WeakHashMap}. <b>This port never converts BACK from an AE key</b>: everything it extracts is named by the config
 * inventory, so the {@code ItemStack} is already in hand, and everything it inserts goes through
 * {@link AEItemKey#of(ItemStack)}. That removes the whole cache and one direction of conversion.
 */
public class MEItemInputBus extends MEItemBus implements SettingsTransfer {

    @Override
    public hellfirepvp.modularmachinery.common.machine.IOType getIOType() {
        return hellfirepvp.modularmachinery.common.machine.IOType.INPUT;
    }

    private static final String CONFIG_TAG_KEY = "configInventory";

    /**
     * The buffer's slot count, <b>upstream's own number</b>: {@code MEItemInputBus.buildInventory} reads
     * {@code int size = 16} (upstream U36) and builds {@code IOInventory(this, slotIDs, new int[]{})} from it.
     *
     * <p><b>PUBLIC AND TWO CONSTANTS RATHER THAN ONE, WHICH IS THE FIX ITSELF.</b> The port used to state a single
     * {@code MEBusTiles.SLOTS = 27} for BOTH directions and BOTH halves of this class, and every consequence followed
     * from that one number: the buffer was eleven slots longer than upstream's, the machine-facing capability exposed
     * those eleven, and nothing could fill them (see {@link #isFillable}). Constant <b>variables</b> are inlined at
     * their reference sites, so the self-test reads these two numbers without loading this class -- which it must not
     * do on a server without AE2.</p>
     */
    public static final int STORAGE_SLOTS = 16;

    /**
     * The configuration grid's slot count -- upstream U57, the same 16 as the buffer and <b>the same sixteen cells in
     * the same order</b>: {@code buildConfigInventory()} builds a second {@code IOInventory} of 16 and marks all of
     * them as its misc (configuration) slots, so slot {@code i} of the grid configures buffer slot {@code i}.
     *
     * <p>That correspondence is the whole contract between the two halves, and it is why an index that exists in the
     * buffer and not here is a slot the bus can never top up.</p>
     */
    public static final int CONFIG_SLOTS = 16;

    /** What this bus is asked to keep available. Same slot count as the buffer, and slot i configures buffer slot i. */
    private final ItemStackHandler configInventory;

    /**
     * @param type  the block entity type, as a supplier -- see {@code MEMachineComponent}'s constructor
     * @param pos   this block's position
     * @param state this block's state
     */
    public MEItemInputBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        // The two counts come from the constants above rather than from a parameter: a caller that passed a number
        // could pass 27 (it did), and then this class's two halves would disagree with upstream and with the menu.
        super(type, pos, state, STORAGE_SLOTS);
        this.configInventory = new ItemStackHandler(CONFIG_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                // A config change must make the slot re-examined, which is what marks the bus dirty.
                synchronized (MEItemInputBus.this) {
                    changedSlots[slot] = true;
                }
                setChanged();
            }
        };
    }

    public ItemStackHandler getConfigInventory() {
        return configInventory;
    }

    /**
     * @return whether slot {@code i} of the buffer is one this bus can actually move items through
     *
     * <h2>Why the fill path asks this instead of comparing an index to one number</h2>
     *
     * <p><b>Slot {@code i} of the buffer is filled from slot {@code i} of the configuration, so a buffer slot with no
     * configuration slot behind it is a slot the bus can never top up.</b> The capability ({@code MEItemBus
     * #getCapability}) exposes the WHOLE buffer to the machine, while {@link #doBusWork} can only fill what the config
     * grid covers -- and when those two disagree, the extra slots are reachable by the machine, are emptied by rule 1
     * the moment they hold anything (nothing is configured, so whatever is there is handed back to the network), and
     * are never refilled. That is the defect this method exists to make unrepresentable: <b>the predicate the fill loop
     * uses is the same predicate a check can ask, so the two answers cannot drift apart.</b></p>
     *
     * <p>Written as {@code slot < inventory.getSlots() && slot < configInventory.getSlots()} rather than against a
     * literal, so it stays true if either half's count is ever changed -- and turns FALSE at once if they are changed
     * to disagree.</p>
     */
    public boolean isFillable(int slot) {
        return slot >= 0 && slot < inventory.getSlots() && slot < configInventory.getSlots();
    }

    /** @return how many slots the machine can reach through the item-handler capability, which is the whole buffer */
    public int getExposedSlots() {
        return inventory.getSlots();
    }

    /**
     * The node's visual representation in AE2's own debug views.
     *
     * <p>Read from this block entity's own state rather than from {@code RegistryMEBlocks}: the registry holds a deferred
     * reference, and reaching through it here would couple a tile to registration order for a value the blockstate already
     * answers. Upstream returns the same thing by the same reasoning -- it asks its own block.
     */
    @Override
    public ItemStack getVisualItemStack() {
        return new ItemStack(getBlockState().getBlock());
    }

    @Override
    protected void doBusWork(int[] slots) {
        MEStorage network = getNetworkStorage();
        if (network == null) {
            // No grid -- leave every slot dirty so the next pass retries rather than treating this as success.
            for (int slot : slots) {
                markSlotFailed(slot);
            }
            return;
        }
        IActionSource source = IActionSource.ofMachine(this);

        for (int slot : slots) {
            // EVERY INDEX THE CAPABILITY EXPOSES MUST PASS THIS, or the machine can reach a slot this loop will never
            // refill -- see isFillable, which is the one statement of that rule.
            if (!isFillable(slot)) {
                continue;
            }
            ItemStack configured = configInventory.getStackInSlot(slot);
            ItemStack buffered = inventory.getStackInSlot(slot);

            // RULE 1: nothing configured, something held -- give it back.
            if (configured.isEmpty()) {
                if (buffered.isEmpty()) {
                    markSlotClean(slot);
                    continue;
                }
                ItemStack leftover = insertToNetwork(network, source, buffered, slot);
                if (leftover.getCount() < buffered.getCount()) {
                    inventory.setStackInSlot(slot, leftover);
                }
                if (leftover.isEmpty()) {
                    markSlotClean(slot);
                } else {
                    markSlotFailed(slot);
                }
                continue;
            }

            // RULE 2: the buffer does not match what is configured. Fill it -- but ONLY once the old contents are gone.
            if (!ItemStack.isSameItemSameTags(configured, buffered)) {
                if (buffered.isEmpty()) {
                    fillSlotFromNetwork(network, source, configured, slot);
                    continue;
                }
                // The precondition: whichever order the configured item changed in, the buffer must empty FIRST. Writing over
                // it would destroy the stack -- see the class javadoc.
                ItemStack leftover = insertToNetwork(network, source, buffered, slot);
                if (leftover.isEmpty()) {
                    inventory.setStackInSlot(slot, ItemStack.EMPTY);
                    fillSlotFromNetwork(network, source, configured, slot);
                } else {
                    // Could not hand it all over; keep what is left and try again next pass instead of overwriting.
                    inventory.setStackInSlot(slot, leftover);
                    markSlotFailed(slot);
                }
                continue;
            }

            // RULE 3.
            if (configured.getCount() == buffered.getCount()) {
                markSlotClean(slot);
                continue;
            }

            // RULE 4: top up.
            if (configured.getCount() > buffered.getCount()) {
                int wanted = configured.getCount() - buffered.getCount();
                ItemStack missing = wanted > 0
                        ? copyWithSize(configured, wanted)
                        : ItemStack.EMPTY;
                long moved = extractFromNetwork(network, source, missing);
                if (moved > 0) {
                    inventory.setStackInSlot(slot, copyWithSize(buffered, (int) (buffered.getCount() + moved)));
                    // Upstream clears the failure counter here, not just the dirty flag: a slot that just succeeded has earned
                    // an immediate retry if it fails again.
                    synchronized (this) {
                        failureCounter[slot] = 0;
                    }
                } else {
                    markSlotFailed(slot);
                    continue;
                }
            } else {
                // RULE 5: excess back to the network.
                int excess = buffered.getCount() - configured.getCount();
                ItemStack leftover = insertToNetwork(network, source, copyWithSize(buffered, excess), slot);
                if (leftover.isEmpty()) {
                    inventory.setStackInSlot(slot, copyWithSize(configured, configured.getCount()));
                } else {
                    markSlotFailed(slot);
                    continue;
                }
            }
            markSlotClean(slot);
        }
    }

    /** Fills a slot from the network with the configured item, honouring the "old contents gone first" precondition. */
    private void fillSlotFromNetwork(MEStorage network, IActionSource source, ItemStack configured, int slot) {
        long moved = extractFromNetwork(network, source, configured);
        if (moved > 0) {
            inventory.setStackInSlot(slot, copyWithSize(configured, (int) moved));
            markSlotClean(slot);
        } else {
            markSlotFailed(slot);
        }
    }

    /**
     * @return how many items the network gave up
     */
    private long extractFromNetwork(MEStorage network, IActionSource source, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return 0L;
        }
        return network.extract(AEItemKey.of(wanted), wanted.getCount(), Actionable.MODULATE, source);
    }

    /**
     * Pushes a stack into the network.
     *
     * @return what the network did NOT take; empty when everything went
     */
    private ItemStack insertToNetwork(MEStorage network, IActionSource source, ItemStack stack, int slot) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long accepted = network.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
        if (accepted <= 0) {
            return stack;
        }
        int remaining = (int) (stack.getCount() - accepted);
        return remaining <= 0 ? ItemStack.EMPTY : copyWithSize(stack, remaining);
    }

    /** @return the network's storage, or null when this bus is not connected or the grid is not active */
    @Nullable
    private MEStorage getNetworkStorage() {
        if (!getMainNode().isActive()) {
            return null;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        IStorageService storage = grid.getService(IStorageService.class);
        return storage == null ? null : storage.getInventory();
    }

    private static ItemStack copyWithSize(ItemStack template, int size) {
        if (size <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = template.copy();
        copy.setCount(size);
        return copy;
    }

    /**
     * THE MEMORY CARD'S OTHER END: this bus's configuration grid, in a tag the card can hold (audit row 43).
     *
     * <pre>
     *   U K/tile/MEItemInputBus.java:299-314
     *       private static final String CONFIG_TAG_KEY = "configInventory";
     *       public NBTTagCompound downloadSettings() {
     *           NBTTagCompound tag = new NBTTagCompound();
     *           tag.setTag(CONFIG_TAG_KEY, configInventory.writeNBT());
     *           return tag;
     *       }
     *       public void uploadSettings(NBTTagCompound settings) {
     *           readConfigInventoryNBT(settings.getCompoundTag(CONFIG_TAG_KEY));
     *           try { proxy.getTick().alertDevice(proxy.getNode()); } catch (GridAccessException e) { ... }
     *       }
     * </pre>
     *
     * <p><b>The key is upstream's OWN string and this class already used it</b> — the port's
     * {@code CONFIG_TAG_KEY} is {@code "configInventory"}, which is both upstream's constant (U28) and this bus's
     * save key. So the tag a memory card carries and the tag a save file carries are the same tag, and a card
     * written by upstream would load here.</p>
     *
     * <p><b>Deviation (rule A0), and it is the only one in this pair:</b> upstream wakes the grid node with
     * {@code alertDevice} after an upload. <b>That call does not exist on AE2 15.4.11</b> — verified with
     * {@code javap} against the mapped jar: {@code appeng.api.networking.IGridNode} has no {@code alertDevice}, and
     * neither does {@code IManagedGridNode} (whose whole surface is flags, power, tag name, grid colour, services
     * and {@code getNode}/{@code ifPresent}). Upstream was written against AE2 1.12's API, where the ticking service
     * was woken by hand. On 15.x the port's equivalent is the bus's OWN wake-up path: every slot is marked dirty,
     * which is what {@link MEItemBus#hasChangedSlots()} reads, and the next {@code tickingRequest} returns
     * {@link appeng.api.networking.ticking.TickRateModulation#FASTER} because of it. The node's ticking request is
     * declared never-sleeping ({@code new TickingRequest(5, 120, false, true)}), so the restored configuration is
     * acted on within one ticking interval rather than on the same tick.</p>
     */
    @Override
    public CompoundTag downloadSettings() {
        CompoundTag tag = new CompoundTag();
        tag.put(CONFIG_TAG_KEY, configInventory.serializeNBT());
        return tag;
    }

    @Override
    public void uploadSettings(CompoundTag settings) {
        // Upstream's getCompoundTag returns an EMPTY tag for a missing key, and readConfigInventoryNBT of an empty
        // tag writes nothing -- so a card carrying no configuration for this bus leaves the bus as it was, rather
        // than clearing it. That behaviour is kept: a copy that wiped the target's configuration when the card had
        // none would be a data-loss bug reached by right-clicking.
        if (!settings.contains(CONFIG_TAG_KEY)) {
            return;
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(CONFIG_TAG_KEY, settings.getCompound(CONFIG_TAG_KEY));
        // The SAME clamped load the save path uses, so a card written by a 27-slot build of this bus cannot resize
        // the live handler out from under changedSlots/failureCounter (see MEItemBus#loadInventoryClamped).
        loadInventoryClamped(configInventory, wrapper, CONFIG_TAG_KEY);

        // Upstream's alertDevice, replaced by the bus's own dirty marks -- see the javadoc above.
        synchronized (this) {
            for (int slot = 0; slot < changedSlots.length; slot++) {
                changedSlots[slot] = true;
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(CONFIG_TAG_KEY, configInventory.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // The CONFIGURATION half goes through the same clamped load as the buffer: a save written when this half was 27
        // slots large would otherwise come back 27 slots large (Forge ItemStackHandler L186-L188) while the screen can
        // only ever show CONFIG_SLOTS of them.
        loadInventoryClamped(configInventory, tag, CONFIG_TAG_KEY);
    }
}
