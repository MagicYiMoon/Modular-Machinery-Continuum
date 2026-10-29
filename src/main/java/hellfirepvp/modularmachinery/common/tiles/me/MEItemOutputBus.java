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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * {@code modularmachinery:blockmeitemoutputbus} -- hands the machine's products to the ME network.
 *
 * <h2>Much simpler than the input side, and for a reason</h2>
 *
 * <p>There is no config inventory here: an output bus has nothing to maintain, it empties itself. Per slot the pass is take the
 * stack out, offer it to the network, put back whatever the network would not accept.</p>
 *
 * <h2>And one upstream line that DESTROYS items, which the port does not copy</h2>
 *
 * <p>Upstream's loop is:</p>
 *
 * <pre>
 * ItemStack extracted = inventory.extractItem(slot, stack.getCount(), false);   // taken OUT of the slot
 * IAEItemStack aeStack = channel.createStack(extracted);
 * if (aeStack == null) {
 *     continue;                                                                 // ...and never put back
 * }
 * </pre>
 *
 * <p><b>That {@code continue} voids the stack.</b> It has already been removed from the inventory, so skipping the insert loses it
 * with nothing logged. The port handles the same case by marking the slot FAILED and leaving the contents where they were --
 * see {@link #doBusWork}. <b>Whether an unencodable stack can occur in practice depends on the network, and the correct handling
 * does not.</b> It is the same silent item loss that §§399.3 and 400.3 guarded the input side against; this is the one place it is
 * upstream's own.</p>
 *
 * <h2>And one field whose meaning differs between the two directions</h2>
 *
 * <p>{@code failureCounter} on the INPUT side is a countdown: set to a delay on failure, zeroed on success, and consulted before
 * re-offering a slot. Here upstream uses it as an ASCENDING no-progress counter capped at 10. <b>Same name, same array, two
 * meanings</b> -- which is why this class zeroes or increments it explicitly rather than relying on the base's helpers for the
 * semantics.</p>
 *
 * <h2>{@code configuredStackSize}: what upstream's one int actually does (audit row 11)</h2>
 *
 * <p>Read at the body rather than inferred from the name:</p>
 *
 * <pre>
 *   MEItemOutputBus                                              (upstream)
 *     U24  private int configuredStackSize;
 *     U26-U43 buildInventory:  size = 36; configuredStackSize = Integer.MAX_VALUE;
 *                              inv.setStackLimit(this.configuredStackSize, slotIDs)
 *     U143 getConfiguredStackSize()          -> the field
 *     U147 setConfiguredStackSize(int size)  -> Math.max(1, size); applyStackSizeToInventory(); markForUpdate()
 *     U153 applyStackSizeToInventory()       -> setStackLimit(configuredStackSize, EVERY slot)
 *     U164 readCustomNBT  / U174 writeCustomNBT  -> NBT key "configuredStackSize" (absent = leave as is)
 *     U181 downloadSettings / U188 uploadSettings -> the same value through the memory card
 *
 *   and in the GUI: ContainerMEItemOutputBusStackSize C14-C21 (@GuiSync(0) int stackSize, seeded from the tile,
 *   written back in C40-C46 onContainerClosed), GuiMEItemOutputBusStackSize's eight +/-1/10/100/1000 buttons, and
 *   GuiMEItemOutputBus's wrench button that switches to that second GUI.
 * </pre>
 *
 * <p><b>So it is a per-bus CAP on how much one slot may hold</b>, applied to every slot of the buffer, persisted, and
 * editable from the output bus's screen. Its effect is on the direction the machine writes: a smaller cap means the
 * machine's output allocation sees less room per slot, and a larger one lets a single slot carry network quantities --
 * which is the same freedom the input bus has unconditionally ({@code setStackLimit(Integer.MAX_VALUE)}).</p>
 *
 * <p><b>THE PORT HAS TO SET IT DIFFERENTLY AND THAT IS A MECHANISM DIFFERENCE, NOT AN INTENT ONE:</b> upstream calls
 * {@code setStackLimit(value, slotIDs)} because its {@code IOInventory} stores a limit PER SLOT; Forge's
 * {@code ItemStackHandler} has no such setter, so this class's handler answers {@link #slotLimit()} for every slot
 * instead (see {@link MEItemBus#createInventory}). One int either way, and no slot list to keep in step with the
 * inventory's size -- which is the class of defect rows 10 and 11 are about.</p>
 */
public class MEItemOutputBus extends MEItemBus implements SettingsTransfer {

    @Override
    public hellfirepvp.modularmachinery.common.machine.IOType getIOType() {
        return hellfirepvp.modularmachinery.common.machine.IOType.OUTPUT;
    }

    /**
     * The buffer's slot count, <b>upstream's own number</b>: {@code MEItemOutputBus.buildInventory} reads
     * {@code int size = 36} (upstream U28) and builds {@code IOInventory(this, new int[0], slotIDs)} from it.
     *
     * <p><b>It is deliberately NOT the input bus's number.</b> The port used to give both directions
     * {@code MEBusTiles.SLOTS = 27}, which is 25% less room to hand products to the network than upstream's output bus
     * has -- the direction a machine stalls on, because a full output bus holds a finished craft at its duration
     * (upstream's own reason for the larger number). See {@code MEItemInputBus#STORAGE_SLOTS} for the other half of
     * the same defect.</p>
     */
    public static final int SLOTS = 36;

    /**
     * What one slot may hold until a player says otherwise: upstream's own {@code Integer.MAX_VALUE}, set inside
     * {@code buildInventory} (U35) before the limit is applied (U36).
     *
     * <p>Public so the screen's opening state and the self-test read the same number as the tile.</p>
     */
    public static final int DEFAULT_STACK_SIZE = Integer.MAX_VALUE;

     
    public static final String STACK_SIZE_TAG_KEY = "configuredStackSize";

    /**
     * How much one slot may hold. <b>0 is not a value it can hold</b>: the field starts at the default and every write
     * clamps to at least 1 ({@link #setConfiguredStackSize}), because a slot limit of 0 would make the bus accept
     * nothing at all -- a machine whose output has nowhere to go, with no error printed anywhere.
     */
    private int configuredStackSize = DEFAULT_STACK_SIZE;

    /**
     * @param type  the block entity type, as a supplier -- see {@code MEMachineComponent}'s constructor
     * @param pos   this block's position
     * @param state this block's state
     */
    public MEItemOutputBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        // The count comes from the constant above rather than from a parameter, for the reason the input bus records:
        // a caller CAN pass the wrong number (it passed 27), and this class has no second half to disagree with.
        super(type, pos, state, SLOTS);
    }

    @Override
    public ItemStack getVisualItemStack() {
        return new ItemStack(getBlockState().getBlock());
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>The one override of this hook in the mod, and it is upstream's own line:</b>
     * {@code inv.setStackLimit(this.configuredStackSize, slotIDs)} (U36). The handler asks this method for EVERY slot,
     * so {@code applyStackSizeToInventory} (U153) and its slot list have no port equivalent to write -- one number,
     * read where it is used, instead of the same number copied into 36 slot entries that can drift from it.</p>
     */
    @Override
    protected int slotLimit() {
        return Math.max(1, configuredStackSize);
    }

    /** @return how much one slot of this bus may hold; see the class javadoc for what upstream does with it */
    public int getConfiguredStackSize() {
        return configuredStackSize;
    }

    /**
     * Sets the cap, in upstream's own steps: {@code Math.max(1, size)} then apply then mark the tile (U147-U151).
     *
     * <p>The "apply" step is implicit here -- {@link #slotLimit()} IS the applied value -- and {@code setChanged()} is
     * what upstream's {@code markForUpdate()} does on 1.20.1. <b>The clamp is not decoration:</b> a client can send any
     * int, and a limit of 0 or negative makes an output bus that accepts nothing.</p>
     */
    public void setConfiguredStackSize(int size) {
        this.configuredStackSize = Math.max(1, size);
        setChanged();
    }

    @Override
    public net.minecraft.nbt.CompoundTag downloadSettings() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt(STACK_SIZE_TAG_KEY, configuredStackSize);
        return tag;
    }

    @Override
    public void uploadSettings(net.minecraft.nbt.CompoundTag settings) {
        if (settings != null && settings.contains(STACK_SIZE_TAG_KEY, net.minecraft.nbt.Tag.TAG_INT)) {
            setConfiguredStackSize(settings.getInt(STACK_SIZE_TAG_KEY));
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        // Upstream's writeCustomNBT (U174-U179), same key. Written unconditionally: an absent key means "keep this
        // bus's default", which is right for an old save and wrong for a bus the player has configured.
        tag.putInt(STACK_SIZE_TAG_KEY, configuredStackSize);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains(STACK_SIZE_TAG_KEY, net.minecraft.nbt.Tag.TAG_INT)) {
            // Through the SETTER, so a save carrying a 0 or a negative value (a hand-edited file, or a build between
            // the two) cannot produce a bus that accepts nothing. Upstream reads the raw int here (U167-U169) and lets
            // applyStackSizeToInventory install it -- including a 0.
            setConfiguredStackSize(tag.getInt(STACK_SIZE_TAG_KEY));
        }
    }

    @Override
    protected void doBusWork(int[] slots) {
        MEStorage network = getNetworkStorage();
        if (network == null) {
            for (int slot : slots) {
                markSlotFailed(slot);
            }
            return;
        }
        IActionSource source = IActionSource.ofMachine(this);

        for (int slot : slots) {
            if (slot < 0 || slot >= inventory.getSlots()) {
                continue;
            }
            ItemStack buffered = inventory.getStackInSlot(slot);
            if (buffered.isEmpty()) {
                markSlotClean(slot);
                continue;
            }

            AEItemKey key = AEItemKey.of(buffered);
            if (key == null) {
                // THE UPSTREAM BUG, NOT COPIED. Upstream has already extracted the stack at this point and `continue`s,
                // which destroys it. Leaving it in the slot and marking the slot failed keeps the item and retries -- and if it
                // is genuinely unencodable, it stays visible in the bus instead of vanishing.
                markSlotFailed(slot);
                continue;
            }

            long accepted = network.insert(key, buffered.getCount(), Actionable.MODULATE, source);

            if (accepted <= 0) {
                // Nothing moved. Ascending no-progress counter, capped -- upstream's shape, and the cap is what makes the
                // FASTER/SLOWER decision below meaningful rather than a busy loop.
                synchronized (this) {
                    failureCounter[slot] = Math.min(failureCounter[slot] + 1, 10);
                }
                markSlotFailed(slot);
                continue;
            }

            int remaining = (int) (buffered.getCount() - accepted);
            inventory.setStackInSlot(slot, remaining <= 0 ? ItemStack.EMPTY : copyWithSize(buffered, remaining));
            synchronized (this) {
                failureCounter[slot] = 0;
            }
            if (remaining <= 0) {
                markSlotClean(slot);
            } else {
                // Partial: something moved, so it is not a failure -- but the slot still has contents and must be retried.
                markSlotFailed(slot);
            }
        }
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
}
