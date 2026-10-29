/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Runtime state shared by source-compatible dynamic upgrades. */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.DynamicMachineUpgrade")
public abstract class DynamicMachineUpgrade extends MachineUpgrade {
    /** Reserved stack tag for data owned by a dynamic upgrade. */
    public static final String ITEM_DATA_TAG = "modularmachinery_upgrade_item_data";
    /** Source capability data is keyed by upgrade type; the old flat tag remains readable. */
    public static final String ITEM_DATA_BY_TYPE_TAG = "modularmachinery_upgrade_item_data_by_type";

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
        if (runtimeAccess != null) return ((DynamicMachineUpgrade) runtimeAccess.source).getParentStack();
        if (parentBus != null) {
            ItemStack current = parentBus.getDynamicUpgradeStack(this);
            if (current != null) return current;
        }
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
        if (runtimeAccess != null) return runtimeAccess.canWrite();
        return valid;
    }

    public boolean isInvalid() {
        return !isValid();
    }

    public void validate() {
        valid = true;
    }

    public void invalidate() {
        valid = false;
    }

    public CompoundTag getItemDataTag() {
        if (runtimeAccess != null) return ((DynamicMachineUpgrade) runtimeAccess.source).getItemDataTag();
        return itemData.copy();
    }

    public void setItemDataTag(CompoundTag data) {
        if (runtimeAccess != null) {
            if (runtimeAccess.canWrite()) {
                var source = (DynamicMachineUpgrade) runtimeAccess.source;
                if (!source.itemData.equals(data == null ? new CompoundTag() : data)) {
                    source.setItemDataTag(data);
                    runtimeAccess.bus.markNoUpdateSync();
                }
            }
            return;
        }
        itemData = data == null ? new CompoundTag() : data.copy();
        syncItemDataToBus();
    }

    protected com.blamejared.crafttweaker.api.data.IData runtimeItemData() {
        var access = runtimeAccess; var source = (DynamicMachineUpgrade) access.source; var root = source.itemData;
        return hellfirepvp.modularmachinery.integration.crafttweaker.ControllerCustomDataView.guarded(root,
                () -> access.canWrite() && source.itemData == root,
                () -> { source.syncItemDataToBus(); access.bus.markNoUpdateSync(); });
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
        if (parentBus != null) parentBus.updateDynamicUpgrade(this, 0);
    }

    /** Source-compatible durability consumption for a dynamic upgrade item. */
    public void decrementItemDurability(int durability) {
        if (runtimeAccess != null) {
            if (runtimeAccess.canWrite()) {
                var source = (DynamicMachineUpgrade) runtimeAccess.source;
                ItemStack before = source.getParentStack();
                source.decrementItemDurability(durability);
                if (!ItemStack.matches(before, source.getParentStack()) || !source.valid) runtimeAccess.bus.markNoUpdateSync();
            }
            return;
        }
        if (valid && parentBus != null && durability > 0) parentBus.updateDynamicUpgrade(this, durability);
    }
}
