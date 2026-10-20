/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Runtime state shared by source-compatible dynamic upgrades. */
public abstract class DynamicMachineUpgrade extends MachineUpgrade {
    /** Reserved stack tag for data owned by a dynamic upgrade. */
    public static final String ITEM_DATA_TAG = "modularmachinery_upgrade_item_data";

    protected int busInventoryIndex = -1;
    protected boolean valid = true;
    protected ItemStack parentStack = ItemStack.EMPTY;
    protected TileUpgradeBus parentBus;
    protected CompoundTag itemData = new CompoundTag();

    protected DynamicMachineUpgrade(UpgradeType type) {
        super(type);
    }

    /** Copy with the item stack that supplied this upgrade, as in the source dynamic path. */
    public abstract DynamicMachineUpgrade copy(ItemStack owner);

    @Override
    public final MachineUpgrade copy() {
        return copy(ItemStack.EMPTY);
    }

    public ItemStack getParentStack() {
        return parentStack.copy();
    }

    public DynamicMachineUpgrade setParentStack(ItemStack parentStack) {
        this.parentStack = parentStack == null ? ItemStack.EMPTY : parentStack.copy();
        return this;
    }

    public int getBusInventoryIndex() {
        return busInventoryIndex;
    }

    public DynamicMachineUpgrade setBusInventoryIndex(int index) {
        this.busInventoryIndex = index;
        return this;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isInvalid() {
        return !valid;
    }

    public void validate() {
        valid = true;
    }

    public void invalidate() {
        valid = false;
    }

    public CompoundTag getItemDataTag() {
        return itemData.copy();
    }

    public void setItemDataTag(CompoundTag data) {
        itemData = data == null ? new CompoundTag() : data.copy();
        syncItemDataToBus();
    }

    public void readItemNBT(CompoundTag tag) {
        itemData = tag == null ? new CompoundTag() : tag.copy();
    }

    public CompoundTag writeItemNBT() {
        return itemData.copy();
    }

    /** Binds the dynamic instance to its live bus slot after the registry creates it. */
    public DynamicMachineUpgrade bindBus(TileUpgradeBus bus, int slot, ItemStack owner) {
        parentBus = bus;
        busInventoryIndex = slot;
        setParentStack(owner);
        return this;
    }

    protected void syncItemDataToBus() {
        if (parentBus == null || busInventoryIndex < 0 || busInventoryIndex >= parentBus.getUpgradeInventory().getSlots()) {
            return;
        }
        ItemStack updated = parentStack.copy();
        updated.getOrCreateTag().put(ITEM_DATA_TAG, writeItemNBT());
        parentStack = updated.copy();
        parentBus.setUpgradeStack(busInventoryIndex, updated);
    }

    /** Source-compatible durability consumption for a dynamic upgrade item. */
    public void decrementItemDurability(int durability) {
        if (!valid || parentBus == null || busInventoryIndex < 0 || parentStack.isEmpty()
                || !parentStack.isDamageableItem() || durability <= 0) {
            return;
        }
        int next = parentStack.getDamageValue() + durability;
        if (next >= parentStack.getMaxDamage()) {
            parentBus.setUpgradeStack(busInventoryIndex, ItemStack.EMPTY);
            invalidate();
            return;
        }
        ItemStack updated = parentStack.copy();
        updated.setDamageValue(next);
        parentStack = updated.copy();
        parentBus.setUpgradeStack(busInventoryIndex, updated);
    }
}
