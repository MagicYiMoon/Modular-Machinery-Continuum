/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EFabricatorPatternBus. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Physical single-pattern slots, independent of whether the structure has power or is assembled. */
public final class TileEFabricatorPatternBus extends TileEFabricatorPart implements hellfirepvp.modularmachinery.common.tiles.base.BreakInventoryProvider {
    private final ItemStack[] patterns = new ItemStack[EFabricatorSpecs.PATTERN_SLOTS];
    private CompoundTag opaque;
    private boolean active = true;
    private int generation;
    private long revision;
    private LazyOptional<IItemHandler> inventory = createInventory();
    public TileEFabricatorPatternBus(BlockPos pos, BlockState state) { super(pos, state); java.util.Arrays.fill(patterns, ItemStack.EMPTY); }
    public boolean readable() { return opaque == null; }
    public java.util.List<ItemStack> patternStacks() {
        return readable() ? java.util.Arrays.stream(patterns).filter(s -> !s.isEmpty()).map(ItemStack::copy).toList() : java.util.List.of();
    }
    public long patternRevision() { return revision; }
    public ItemStack patternAt(int slot) { return readable() && slot >= 0 && slot < patterns.length ? patterns[slot].copy() : ItemStack.EMPTY; }
    private void changedPatterns() { revision++; setChanged(); if (owner() != null) owner().patternsChanged(); }
    /** Only container code receives mutable stacks; automation continues to receive defensive copies. */
    public net.minecraft.world.Container menuInventory() {
        int expected = generation;
        return new net.minecraft.world.SimpleContainer(patterns.length) {
            private boolean available(int slot) { return slot >= 0 && slot < patterns.length && live(expected) && readable(); }
            @Override public ItemStack getItem(int slot) { return available(slot) ? patterns[slot] : ItemStack.EMPTY; }
            @Override public boolean isEmpty() { return !readable() || java.util.Arrays.stream(patterns).allMatch(ItemStack::isEmpty); }
            @Override public ItemStack removeItem(int slot, int amount) {
                if (!available(slot) || amount <= 0) return ItemStack.EMPTY;
                var result = patterns[slot].split(amount); if (!result.isEmpty()) changedPatterns(); return result;
            }
            @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
            @Override public void setItem(int slot, ItemStack stack) {
                if (!available(slot) || !stack.isEmpty() && (stack.getCount() != 1 || !encoded(stack))) return;
                patterns[slot] = stack.copy(); changedPatterns();
            }
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return available(slot) && encoded(stack); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public void setChanged() { if (live(expected) && readable()) changedPatterns(); }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return live(expected); }
            @Override public void clearContent() { if (live(expected) && readable()) { java.util.Arrays.fill(patterns, ItemStack.EMPTY); changedPatterns(); } }
        };
    }
    @Override public void unbind() { if (owner() != null) owner().patternsChanged(); super.unbind(); }
    /** Loot copies the entire inventory onto the bus item; the shared break hook must not drop it a second time. */
    @Override public IItemHandler getBreakInventory() { return null; }
    private boolean live(int expected) {
        if (!active || generation != expected || isRemoved() || !(level instanceof ServerLevel server)) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }
    private static boolean encoded(ItemStack stack) {
        return !stack.isEmpty() && net.minecraftforge.fml.ModList.get().isLoaded("ae2")
                && hellfirepvp.modularmachinery.integration.ae2.efabricator.EFabricatorPatterns.encoded(stack);
    }
    private LazyOptional<IItemHandler> createInventory() {
        int expected = generation;
        return LazyOptional.of(() -> new IItemHandler() {
            private boolean available(int slot) { return slot >= 0 && slot < patterns.length && live(expected) && readable(); }
            public int getSlots() { return patterns.length; }
            public int getSlotLimit(int slot) { return slot >= 0 && slot < patterns.length ? 1 : 0; }
            public boolean isItemValid(int slot, ItemStack stack) { return available(slot) && encoded(stack); }
            public ItemStack getStackInSlot(int slot) { return available(slot) ? patterns[slot].copy() : ItemStack.EMPTY; }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack) || !patterns[slot].isEmpty()) return stack;
                if (!simulate) { patterns[slot] = stack.copyWithCount(1); changedPatterns(); }
                return stack.copyWithCount(stack.getCount() - 1);
            }
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!available(slot) || amount <= 0 || patterns[slot].isEmpty()) return ItemStack.EMPTY;
                var result = patterns[slot].copy();
                if (!simulate) { patterns[slot] = ItemStack.EMPTY; changedPatterns(); }
                return result;
            }
        });
    }
    public CompoundTag patternData() {
        if (opaque != null) return opaque.copy();
        var tag = new CompoundTag(); tag.putInt("Version", 1); var list = new ListTag();
        for (int i = 0; i < patterns.length; i++) if (!patterns[i].isEmpty()) {
            var entry = new CompoundTag(); entry.putInt("Slot", i); entry.put("Item", patterns[i].save(new CompoundTag())); list.add(entry);
        }
        tag.put("Patterns", list); return tag;
    }
    public void restorePatternData(CompoundTag tag) {
        java.util.Arrays.fill(patterns, ItemStack.EMPTY); opaque = null;
        if (!tag.isEmpty()) try {
            if (tag.getInt("Version") != 1 || !tag.contains("Patterns", Tag.TAG_LIST)) throw new IllegalArgumentException("Unknown EF pattern format");
            var list = tag.getList("Patterns", Tag.TAG_COMPOUND);
            if (list.size() > patterns.length || list.size() != ((ListTag)tag.get("Patterns")).size()) throw new IllegalArgumentException("Invalid EF pattern list");
            for (var element : list) {
                var entry = (CompoundTag)element; int slot = entry.getInt("Slot"); var item = entry.getCompound("Item");
                var id = ResourceLocation.tryParse(item.getString("id"));
                if (!entry.contains("Slot", Tag.TAG_INT) || slot < 0 || slot >= patterns.length || !patterns[slot].isEmpty()
                        || id == null || !ForgeRegistries.ITEMS.containsKey(id) || item.getByte("Count") != 1)
                    throw new IllegalArgumentException("Invalid EF pattern slot/item");
                var stack = ItemStack.of(item); if (stack.isEmpty()) throw new IllegalArgumentException("Missing EF pattern item");
                patterns[slot] = stack;
            }
        } catch (RuntimeException failure) { java.util.Arrays.fill(patterns, ItemStack.EMPTY); opaque = tag.copy(); }
        changedPatterns();
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? (active && !isRemoved() ? inventory.cast() : LazyOptional.empty()) : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); active = false; generation++; inventory.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); active = true; generation++; inventory = createInventory(); }
    @Override public void onLoad() { super.onLoad(); if (!active) reviveCaps(); }
    @Override public void onChunkUnloaded() { invalidateCaps(); super.onChunkUnloaded(); }
    @Override public void load(CompoundTag tag) { super.load(tag); restorePatternData(tag.getCompound("PatternData")); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("PatternData", patternData()); }
}
