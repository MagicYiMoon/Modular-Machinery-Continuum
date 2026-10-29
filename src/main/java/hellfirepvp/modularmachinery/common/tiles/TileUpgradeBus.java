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

import hellfirepvp.modularmachinery.common.block.BlockUpgradeBus;
import hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.DynamicMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;











































 
public class TileUpgradeBus extends TileMachineComponent {

    private int slots;
    private ItemStackHandler upgradeInventory;

    /**
     * The upgrades installed RIGHT NOW, one entry per type, in slot order.
     *
     * <p>Upstream's field is {@code Map<UpgradeType, MachineUpgrade> foundUpgrades} plus a per-slot map of dynamic
     * upgrades (U47-U48). Static upgrades are keyed by type because an upgrade's identity IS its type, which is also
     * why two identical static items merge instead of appearing twice; dynamic upgrades remain in the slot map so
     * their parent item and itemData are never merged.
     */
    private final Map<String, MachineUpgrade> foundUpgrades = new LinkedHashMap<>();
    /** Dynamic upgrades stay associated with their source slot, as in the source mod. */
    private final Map<Integer, List<DynamicMachineUpgrade>> foundDynamicUpgrades = new LinkedHashMap<>();
    /** Data belongs to the bus/type, so it survives replacing the item stack in a slot. */
    private final Map<String, CompoundTag> customUpgradeData = new LinkedHashMap<>();

    /** Set by {@link #refreshUpgrades} whenever the map above is rebuilt, so a reader can skip a no-op refresh. */
    private int upgradeRevision;
    /** Direct NBT/packet writes may bypass ItemStackHandler's listener; the next reader repairs that state once. */
    private boolean upgradesDirty = true;
    /** Internal dynamic-item writes must not run the external inventory-change rebuild synchronously. */
    private boolean suppressUpgradeRefresh;

    public TileUpgradeBus(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.UPGRADE_BUS, pos, state);
        this.slots = tierOf(state).getUpgradeSlots();
        this.upgradeInventory = newUpgradeInventory(this.slots);
    }

    /**
     * The tier the block at this tile's position declares.
     *
     * <p>Falls back to {@link UpgradeBusData#NORMAL} for a state that is not an upgrade bus at all — a tile
     * whose block has already been removed, which is the same fallback {@code TileItemBus.slotsOf} makes.</p>
     */
    private static UpgradeBusData tierOf(BlockState state) {
        return state.getBlock() instanceof BlockUpgradeBus ? state.getValue(BlockUpgradeBus.TYPE)
                : UpgradeBusData.NORMAL;
    }

    /** The live state, read from the level; see the class javadoc and {@code TileItemBus} L262-L276. */
    private BlockState liveState() {
        return level != null ? level.getBlockState(worldPosition) : getBlockState();
    }

    private ItemStackHandler newUpgradeInventory(int size) {
        return new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (suppressUpgradeRefresh) return;
                // THE ONE HOOK THAT MAKES A SLOT MEAN SOMETHING (upstream TileUpgradeBus:63, which sets the same
                // listener on its IOInventory: `inventory.setListener(this::onUpgradeInventoryChanged)`). Without
                // it an item would sit in a slot, be saved, be seen by the menu, and change nothing -- which is the
                // defect this unit closes. A hopper insert, a menu click and a player's shift-click all arrive
                // here, and so does a block break/place, because all of them write through this handler.
                refreshUpgrades();
            }
        };
    }

    /** @return the tier the block declares, which is the ONLY source of this bus's slot count */
    public UpgradeBusData getTier() {
        return tierOf(liveState());
    }

    /** @return how many upgrade slots the block's tier declares; see {@link #getTier()} */
    public int getSlots() {
        return getTier().getUpgradeSlots();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The size is reconciled against the tier on every read, so a handler that came back from NBT with
     * the wrong {@code Size} (see the class javadoc) or one left behind by a block whose tier changed is
     * corrected before any slot index — the menu's, a hopper's, or the capability's — is resolved against it.
     */
    public IItemHandler getUpgradeInventory() {
        int expected = getSlots();
        if (upgradeInventory == null || upgradeInventory.getSlots() != expected) {
            slots = expected;
            upgradeInventory = newUpgradeInventory(expected);
            setChanged();
            // A REBUILD WIPES THE SLOTS, so the installed set must be rebuilt with it rather than left describing
            // items that are no longer there.
            refreshUpgrades();
        }
        return upgradeInventory;
    }

    /** Updates one live upgrade slot for a dynamic upgrade callback (durability or itemData mutation). */
    public void setUpgradeStack(int slot, ItemStack stack) {
        IItemHandler handler = getUpgradeInventory();
        if (slot < 0 || slot >= handler.getSlots()) return;
        ItemStack updated = stack == null ? ItemStack.EMPTY : stack;
        suppressUpgradeRefresh = true;
        try {
            upgradeInventory.setStackInSlot(slot, updated);
        } finally {
            suppressUpgradeRefresh = false;
        }
        // Every type in this slot observes the current item, without rebuilding or invalidating siblings.
        if (updated.isEmpty()) {
            List<DynamicMachineUpgrade> removed = foundDynamicUpgrades.remove(slot);
            if (removed != null) removed.forEach(dynamic -> { dynamic.setParentStack(ItemStack.EMPTY); dynamic.invalidate(); });
            rebuildStaticUpgrades();
        } else {
            foundDynamicUpgrades.getOrDefault(slot, List.of()).forEach(dynamic -> dynamic.setParentStack(updated));
        }
        upgradesDirty = false;
        upgradeRevision++;
        setChanged();
    }

    /** Null means this instance no longer owns the slot; returned stacks never expose mutable inventory aliases. */
    public ItemStack getDynamicUpgradeStack(DynamicMachineUpgrade source) {
        if (level instanceof net.minecraft.server.level.ServerLevel server && !server.getServer().isSameThread()) return null;
        IItemHandler inventory = getUpgradeInventory();
        int slot = source.getBusInventoryIndex();
        if (slot < 0 || slot >= inventory.getSlots()
                || foundDynamicUpgrades.getOrDefault(slot, List.of()).stream().noneMatch(installed -> installed == source)) return null;
        return inventory.getStackInSlot(slot).copy();
    }

    /** Serializes all types from the current slot, then applies wear to that same current item. */
    public void updateDynamicUpgrade(DynamicMachineUpgrade source, int durability) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !server.getServer().isSameThread()
                || isRemoved() || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this || !source.isValid()) return;
        ItemStack current = getDynamicUpgradeStack(source);
        if (current == null || current.isEmpty() || durability < 0 || durability > 0 && !current.isDamageableItem()) return;
        int slot = source.getBusInventoryIndex();
        long damage = (long) current.getDamageValue() + durability;
        if (durability > 0 && damage >= current.getMaxDamage()) {
            setUpgradeStack(slot, ItemStack.EMPTY);
            return;
        }
        ItemStack updated = RegistryMachineUpgrade.preserveUpgradeIdentities(current);
        CompoundTag byType = updated.getOrCreateTag().getCompound(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG);
        for (DynamicMachineUpgrade sibling : foundDynamicUpgrades.getOrDefault(slot, List.of()))
            byType.put(sibling.getType().getName(), sibling.writeItemNBT());
        updated.getOrCreateTag().put(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG, byType);
        if (durability > 0) updated.setDamageValue((int) damage);
        if (!ItemStack.matches(current, updated)) setUpgradeStack(slot, updated);
    }

    /** A broken mixed item must lose its static effects, while other slots' dynamic handles remain valid. */
    private void rebuildStaticUpgrades() {
        foundUpgrades.forEach((key, upgrade) -> customUpgradeData.put(key, upgrade.writeNBT()));
        foundUpgrades.clear();
        for (int slot = 0; slot < upgradeInventory.getSlots(); slot++) {
            ItemStack stack = upgradeInventory.getStackInSlot(slot);
            for (MachineUpgrade upgrade : RegistryMachineUpgrade.upgradesFor(stack)) {
                if (upgrade instanceof DynamicMachineUpgrade) continue;
                MachineUpgrade existing = foundUpgrades.get(upgrade.getType().getName());
                if (existing != null) { existing.incrementStackSize(upgrade.getStackSize() + stack.getCount() - 1); continue; }
                CompoundTag saved = customUpgradeData.get(upgrade.getType().getName());
                if (saved != null) upgrade.setCustomData(saved);
                upgrade.incrementStackSize(stack.getCount() - 1);
                foundUpgrades.put(upgrade.getType().getName(), upgrade);
            }
        }
    }

    // =============================================================================================
    // THE INSTALLED-UPGRADE STATE (audit row 37)
    // =============================================================================================

    /**
     * Rebuilds {@link #foundUpgrades} from the inventory.
     *
     * <p>Upstream's counterpart is {@code onUpgradeInventoryChanged(int changedSlot)} (U94-U119): it clears
     * {@code foundUpgrades}, walks every slot, skips an empty slot or one whose item is not in
     * {@code RegistryUpgrade}, reads the stack's upgrade capability, and either merges into the existing entry for
     * that type or adds a new one. <b>Four of those five steps are kept literally</b>; the fifth — the capability
     * read — is replaced by {@link RegistryMachineUpgrade#upgradeFor(ItemStack)}, for the reason
     * {@code MachineUpgradeItem}'s javadoc records at length (upstream's capability was attached by CraftTweaker
     * and nothing else, and this port has no script engine).
     *
     * <p><b>The STACK MERGE is upstream's and is not cosmetic.</b> {@code updateUpgrades} (U138-U154) increments the
     * existing entry's stack size rather than adding a second entry, because an upgrade's identity is its type: two
     * speed items are ONE speed upgrade of size two, which is what lets a stackable upgrade scale its effect. The
     * port also keeps upstream's {@code parentStack.getCount() - 1} (U150): a stack of three items is one installed
     * upgrade plus two extra, so {@code stackSize} is three and not four.
     */
    public void refreshUpgrades() {
        foundUpgrades.forEach((key, upgrade) -> customUpgradeData.put(key, upgrade.writeNBT()));
        foundDynamicUpgrades.values().forEach(upgrades -> upgrades.forEach(DynamicMachineUpgrade::invalidate));
        foundUpgrades.clear();
        foundDynamicUpgrades.clear();
        IItemHandler handler = upgradeInventory;
        if (handler == null) {
            upgradeRevision++;
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            // An empty slot and a non-upgrade item are the SAME answer here (upstream U99-U106): nothing is
            // installed from it. Distinguishing them would be a state nobody can observe.
            List<MachineUpgrade> upgrades = RegistryMachineUpgrade.upgradesFor(stack);
            for (MachineUpgrade upgrade : upgrades) {
                if (upgrade instanceof DynamicMachineUpgrade dynamic) {
                    dynamic.bindBus(this, slot, stack);
                    CompoundTag saved = customUpgradeData.get(dynamic.getType().getName());
                    if (saved != null) dynamic.setCustomData(saved);
                    foundDynamicUpgrades.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(dynamic);
                    // The source implementation deliberately does not merge dynamic upgrades into the ordinary
                    // type map: each slot retains its own parent stack, itemData, durability and valid flag.
                    continue;
                }
                MachineUpgrade existing = foundUpgrades.get(upgrade.getType().getName());
                if (existing != null) {
                    existing.incrementStackSize(upgrade.getStackSize() + stack.getCount() - 1);
                    continue;
                }
                CompoundTag saved = customUpgradeData.get(upgrade.getType().getName());
                if (saved != null) upgrade.setCustomData(saved);
                else customUpgradeData.put(upgrade.getType().getName(), upgrade.writeNBT());
                upgrade.incrementStackSize(stack.getCount() - 1);
                foundUpgrades.put(upgrade.getType().getName(), upgrade);
            }
        }
        upgradeRevision++;
        upgradesDirty = false;
    }

    /**
     * @return every installed upgrade, in slot order — IMMUTABLE, because every caller is a reader
     *
     * <p><b>WHY THE READER HAS A DIRTY REPAIR.</b> {@code onContentsChanged} is the normal writer, but it does not
     * fire for every route into the handler: {@code ItemStackHandler#deserializeNBT} writes the stacks directly
     * during {@code load}, and a client-side handler can be filled from a packet. Such paths set {@code upgradesDirty}
     * and the next reader performs one repair pass. Stable reads then keep the same dynamic instances, matching the
     * source bus's per-slot lifecycle instead of recreating them every tick.
     */
    public List<MachineUpgrade> getInstalledUpgrades() {
        if (upgradeInventory != null && upgradesDirty) {
            refreshUpgrades();
        }
        List<MachineUpgrade> result = new ArrayList<>(foundUpgrades.values());
        foundDynamicUpgrades.values().forEach(result::addAll);
        return List.copyOf(result);
    }

    /** @return the installed upgrade of a type by its name, or {@code null} */
    public MachineUpgrade getInstalledUpgrade(String typeName) {
        getInstalledUpgrades();
        MachineUpgrade staticUpgrade = foundUpgrades.get(typeName);
        if (staticUpgrade != null) return staticUpgrade;
        for (List<DynamicMachineUpgrade> upgrades : foundDynamicUpgrades.values()) {
            for (DynamicMachineUpgrade upgrade : upgrades) {
                if (upgrade.getType().getName().equals(typeName)) return upgrade;
            }
        }
        return null;
    }

    /**
     * @param machineName the registry name of the machine asking, or {@code null} for "no machine known"
     * @return the installed upgrades that may act on that machine
     *
     * <p>The compatibility filter is applied HERE rather than in the ticker, matching upstream's one place for it
     * ({@code UpgradeBusProvider.getUpgrades(TileMultiblockMachineController)} U251-U273, which asks
     * {@code type.isCompatible(foundMachine)} once per type while building its map). Keeping it here means a
     * reader cannot forget it, and that the answer a bus gives about itself does not depend on who is asking.
     */
    public List<MachineUpgrade> getUpgradesFor(String machineName) {
        List<MachineUpgrade> out = new ArrayList<>();
        for (MachineUpgrade upgrade : getInstalledUpgrades()) {
            if (upgrade.getType().isCompatible(machineName)) {
                out.add(upgrade);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** @return a count that changes whenever the installed set is rebuilt, for a caller that caches across ticks */
    public int getUpgradeRevision() {
        return upgradeRevision;
    }

    /** @return a one-line description of the installed set, for a log line or a check's detail */
    public String describeUpgrades() {
        List<MachineUpgrade> installed = getInstalledUpgrades();
        if (installed.isEmpty()) {
            return "no upgrades installed in " + getSlots() + " slot(s)";
        }
        StringBuilder text = new StringBuilder();
        for (MachineUpgrade upgrade : installed) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(upgrade.getType().getName()).append('x').append(upgrade.getStackSize());
        }
        return text.toString();
    }

    /**
     * Returns the source GUI's detail lines for the upgrades currently installed on this bus.
     * The map is keyed by upgrade type name so a client screen can render the same per-upgrade section without
     * exposing mutable bus-owned upgrade instances.
     */
    public Map<String, List<String>> getUpgradeGuiDescriptions(String machineName) {
        Map<String, List<String>> descriptions = new LinkedHashMap<>();
        for (MachineUpgrade upgrade : getUpgradesFor(machineName)) {
            descriptions.put(upgrade.getType().getName(), List.copyOf(upgrade.getBusGUIDescriptions()));
        }
        return Collections.unmodifiableMap(descriptions);
    }

    /**
     * Returns the formed machine currently owning this bus, or {@code null} when the bus is unbound.
     * The source GUI shows this binding above the upgrade descriptions and uses it to explain incompatible
     * upgrades.  The controller position is already synchronized by {@link TileMachineComponent}, so this lookup
     * is valid on both sides without storing a second machine name on the bus.
     */
    @Nullable
    public hellfirepvp.modularmachinery.common.tiles.TileController getBoundController() {
        if (level == null || getControllerPos() == null) return null;
        return level.getBlockEntity(getControllerPos()) instanceof TileController controller ? controller : null;
    }

    /** @return the bound machine's registry name, or {@code null} when this bus is not bound to a controller */
    @Nullable
    public String getBoundMachineName() {
        TileController controller = getBoundController();
        return controller == null ? null : controller.getMachineName();
    }

    /** Returns the installed type's custom data, or an empty compound when no such upgrade is installed. */
    public CompoundTag getUpgradeCustomData(String typeName) {
        MachineUpgrade upgrade = getInstalledUpgrade(typeName);
        return upgrade == null ? new CompoundTag() : upgrade.getCustomData();
    }

    /** Updates bus-owned custom data and persists it with the tile. */
    public void setUpgradeCustomData(String typeName, CompoundTag data) {
        if (typeName == null || typeName.isBlank()) throw new IllegalArgumentException("upgrade type is blank");
        customUpgradeData.put(typeName, data == null ? new CompoundTag() : data.copy());
        MachineUpgrade upgrade = foundUpgrades.get(typeName);
        if (upgrade != null && !upgrade.getCustomData().equals(data == null ? new CompoundTag() : data)) upgrade.setCustomData(data);
        foundDynamicUpgrades.values().forEach(upgrades -> upgrades.stream()
                .filter(dynamic -> dynamic.getType().getName().equals(typeName))
                .filter(dynamic -> !dynamic.getCustomData().equals(data == null ? new CompoundTag() : data))
                .forEach(dynamic -> dynamic.setCustomData(data)));
        setChanged();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // THE REPAIRING GETTER, not the field: the capability is the path automation uses, so a handler
            // larger than the tier must not be reachable here either -- that is the "a hidden slot still
            // accepts a hopper insert" half of the report.
            IItemHandler handler = getUpgradeInventory();
            return LazyOptional.of(() -> handler).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        foundUpgrades.clear();
        foundDynamicUpgrades.clear();
        upgradesDirty = true;
        customUpgradeData.clear();
        if (tag.contains("upgradeCustomData", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag data = tag.getCompound("upgradeCustomData");
            for (String key : data.getAllKeys()) customUpgradeData.put(key, data.getCompound(key).copy());
        }
        if (tag.contains("upgrades")) {
            upgradeInventory.deserializeNBT(tag.getCompound("upgrades"));
            // deserializeNBT RESIZES this handler from the tag's own "Size" (Forge L188). Nothing is done
            // here on purpose: the reconciliation lives in getUpgradeInventory, which is what every reader
            // goes through -- including a tile that was placed before this fix existed.
            //
            // THE INSTALLED SET IS REBUILT HERE, and it is the ONE place the listener cannot cover:
            // ItemStackHandler#deserializeNBT writes its stacks array directly, so onContentsChanged never fires
            // and a bus restored from a save would otherwise report an empty install set while holding items.
            // Upstream has the same line for the same reason (readCustomNBT ends with
            // `onUpgradeInventoryChanged(-1)`, U210).
            refreshUpgrades();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("upgrades", upgradeInventory.serializeNBT());
        foundUpgrades.forEach((key, upgrade) -> customUpgradeData.put(key, upgrade.writeNBT()));
        foundDynamicUpgrades.values().forEach(upgrades -> upgrades.forEach(upgrade ->
                customUpgradeData.put(upgrade.getType().getName(), upgrade.writeNBT())));
        CompoundTag data = new CompoundTag();
        customUpgradeData.forEach((key, value) -> data.put(key, value.copy()));
        if (!data.isEmpty()) tag.put("upgradeCustomData", data);
    }
}
