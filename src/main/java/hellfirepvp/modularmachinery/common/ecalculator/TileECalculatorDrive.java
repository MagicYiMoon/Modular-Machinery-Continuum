/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ECalculatorCellDrive. */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/** One physical crafting memory. No AE storage-cell inventory or recipe input is exposed here. */
public final class TileECalculatorDrive extends TileECalculatorPart {
    private ItemStack memory = ItemStack.EMPTY;
    private long generation;
    private boolean capabilitiesActive = true;
    private LazyOptional<IItemHandler> slot = createSlot();
    public TileECalculatorDrive(BlockPos pos, BlockState state) { super(ECalculatorRegistration.DRIVE_TILE, pos, state); }
    public boolean hasMemory() { return !memory.isEmpty(); }
    public ItemStack memorySnapshot() { return memory.copy(); }
    public boolean isTierSupported() { return owner() != null && memory.getItem() instanceof ItemECalculatorMemory cell && cell.specification().supportedBy(owner().tier()); }
    public long suppliedBytes() { return isActivePart() && isTierSupported() ? ((ItemECalculatorMemory)memory.getItem()).specification().bytes() : 0; }
    public boolean insertMemory(ItemStack input) {
        if (level == null || level.isClientSide || isRemoved() || hasMemory() || input.isEmpty() || !(input.getItem() instanceof ItemECalculatorMemory)) return false;
        memory = input.copyWithCount(1); changedMemory(); return true;
    }
    public ItemStack takeMemory() {
        if (level == null || level.isClientSide || isRemoved()) return ItemStack.EMPTY;
        ItemStack result = memory; memory = ItemStack.EMPTY;
        if (!result.isEmpty()) changedMemory();
        return result;
    }
    private void changedMemory() {
        setChanged(); updateOccupied();
        if (owner() != null) { owner().componentsChanged(); owner().refreshPartAppearances(); }
        else refreshAppearance();
        if (level != null) level.getLightEngine().checkBlock(worldPosition);
    }
    private void updateOccupied() {
        if (level instanceof net.minecraft.server.level.ServerLevel server && !isRemoved()) {
            var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            if (chunk == null || chunk.getBlockEntity(worldPosition) != this) return;
            var state = chunk.getBlockState(worldPosition);
            // onRemove takes the memory AFTER the section palette has installed the replacement.
            // Writing the tile's cached old state here would resurrect an empty drive over air/stone.
            if (state.getBlock() != getBlockState().getBlock()) return;
            int grade = memory.getItem() instanceof ItemECalculatorMemory cell ? cell.specification().ordinal() + 1 : 0;
            if (state.getValue(BlockECalculatorPart.MEMORY) != grade)
                level.setBlock(worldPosition, state.setValue(BlockECalculatorPart.MEMORY, grade), Block.UPDATE_CLIENTS);
        }
    }
    private LazyOptional<IItemHandler> createSlot() {
        long created = generation;
        return LazyOptional.of(() -> new IItemHandler() {
            private boolean valid() { return capabilitiesActive && created == generation && !isRemoved() && level != null && !level.isClientSide
                    && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == TileECalculatorDrive.this; }
            public int getSlots() { return 1; }
            public int getSlotLimit(int index) { return index == 0 ? 1 : 0; }
            public boolean isItemValid(int index, ItemStack stack) { return index == 0 && stack.getItem() instanceof ItemECalculatorMemory; }
            public ItemStack getStackInSlot(int index) { return valid() && index == 0 ? memorySnapshot() : ItemStack.EMPTY; }
            public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
                if (!valid() || !isItemValid(index, stack) || stack.isEmpty() || hasMemory()) return stack;
                if (!simulate && !insertMemory(stack)) return stack;
                return stack.copyWithCount(stack.getCount() - 1);
            }
            public ItemStack extractItem(int index, int amount, boolean simulate) {
                if (!valid() || index != 0 || amount <= 0 || !hasMemory()) return ItemStack.EMPTY;
                return simulate ? memorySnapshot() : takeMemory();
            }
        });
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return capabilitiesActive && !isRemoved() ? slot.cast() : LazyOptional.empty();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capabilitiesActive = false; generation++; slot.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); capabilitiesActive = true; slot = createSlot(); }
    @Override public void onLoad() { super.onLoad(); if (!capabilitiesActive) reviveCaps(); updateOccupied(); }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        memory = tag.contains("Memory", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Memory")) : ItemStack.EMPTY;
        // Appearance packets never call this method with a fake/empty inventory (see handleUpdateTag).
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if (hasMemory()) tag.put("Memory", memory.save(new CompoundTag())); }
    @Override public void onChunkUnloaded() { invalidateCaps(); super.onChunkUnloaded(); }
}
