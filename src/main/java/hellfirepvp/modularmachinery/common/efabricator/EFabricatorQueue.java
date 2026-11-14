/* SPDX-License-Identifier: GPL-3.0-only
 * Source concept: NECO EFabricatorWorker.CraftingQueue/CraftWork, KasumiNova and contributors.
 * New bounded codec; this is not a 1.12 save-data importer.
 */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** FIFO counts crafts, not batches. Unit outputs/remainders avoid lossy proportional batch splits. */
public final class EFabricatorQueue {
    public record Work(int crafts, ItemStack output, List<ItemStack> remaining) {
        public Work {
            if (crafts < 1 || output == null || output.isEmpty() || remaining == null || remaining.size() > 9)
                throw new IllegalArgumentException("Invalid EF work");
            output = output.copy(); remaining = remaining.stream().map(ItemStack::copy).toList();
        }
        @Override public ItemStack output() { return output.copy(); }
        @Override public List<ItemStack> remaining() { return remaining.stream().map(ItemStack::copy).toList(); }
        public long outputAmount() { return (long)output.getCount() * crafts; }
        public Work withCrafts(int amount) { return new Work(amount, output, remaining); }
    }
    private final Deque<Work> entries = new ArrayDeque<>();
    private int size;
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    /** FIFO work-entry illustrations, not craft-count expansion or copies of private stack NBT. */
    List<ItemStack> productIcons() {
        var icons = new ArrayList<ItemStack>();
        for (var work : entries) {
            icons.add(new ItemStack(work.output.getItem()));
            if (icons.size() == 33) break; // Source TOP: current entry plus at most 32 queued entries.
        }
        return List.copyOf(icons);
    }
    public Work peek(int maximumCrafts) {
        var first = entries.peekFirst();
        return first == null || maximumCrafts <= 0 ? null : first.withCrafts(Math.min(first.crafts(), maximumCrafts));
    }
    public int offer(Work work, int capacity) {
        if (capacity < 0 || capacity > EFabricatorSpecs.MAX_QUEUE) throw new IllegalArgumentException("Invalid EF capacity");
        int accepted = Math.min(work.crafts, Math.max(0, capacity - size));
        if (accepted > 0) { entries.addLast(work.withCrafts(accepted)); size += accepted; }
        return accepted;
    }
    public Work poll(int maximumCrafts) {
        if (maximumCrafts <= 0 || entries.isEmpty()) return null;
        var first = entries.removeFirst(); int taken = Math.min(maximumCrafts, first.crafts);
        if (taken < first.crafts) entries.addFirst(first.withCrafts(first.crafts - taken));
        size -= taken; return first.withCrafts(taken);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Version", 1); var list = new ListTag();
        for (var work : entries) {
            var entry = new CompoundTag(); entry.putInt("Crafts", work.crafts); entry.put("Output", writeStack(work.output));
            var remaining = new ListTag(); for (var stack : work.remaining) remaining.add(writeStack(stack));
            entry.put("Remaining", remaining); list.add(entry);
        }
        tag.put("Entries", list); return tag;
    }
    public static EFabricatorQueue load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.contains("Entries", Tag.TAG_LIST)) throw new IllegalArgumentException("Unknown EF queue format");
        var raw = tag.getList("Entries", Tag.TAG_COMPOUND);
        if (raw.size() != ((ListTag)tag.get("Entries")).size() || raw.size() > EFabricatorSpecs.MAX_QUEUE) throw new IllegalArgumentException("Invalid EF queue entries");
        var queue = new EFabricatorQueue();
        for (var element : raw) {
            var entry = (CompoundTag)element; int crafts = entry.getInt("Crafts");
            if (crafts <= 0 || crafts > EFabricatorSpecs.MAX_QUEUE - queue.size || !entry.contains("Remaining", Tag.TAG_LIST))
                throw new IllegalArgumentException("Invalid EF craft count");
            var rest = entry.getList("Remaining", Tag.TAG_COMPOUND);
            if (rest.size() != ((ListTag)entry.get("Remaining")).size() || rest.size() > 9) throw new IllegalArgumentException("Invalid EF remainders");
            var remaining = new ArrayList<ItemStack>(); for (var stack : rest) remaining.add(readStack((CompoundTag)stack, true));
            var work = new Work(crafts, readStack(entry.getCompound("Output"), false), remaining);
            queue.entries.addLast(work); queue.size += crafts;
        }
        return queue;
    }
    static CompoundTag writeStack(ItemStack stack) {
        var tag = new CompoundTag(); if (stack.isEmpty()) return tag;
        tag.put("Item", stack.copyWithCount(1).save(new CompoundTag())); tag.putInt("Count", stack.getCount()); return tag;
    }
    static ItemStack readStack(CompoundTag tag, boolean emptyAllowed) {
        if (tag.isEmpty() && emptyAllowed) return ItemStack.EMPTY;
        var itemTag = tag.getCompound("Item"); var id = ResourceLocation.tryParse(itemTag.getString("id"));
        if (!tag.contains("Count", Tag.TAG_INT) || tag.getInt("Count") <= 0 || id == null || !ForgeRegistries.ITEMS.containsKey(id))
            throw new IllegalArgumentException("Unknown EF item or quantity");
        var stack = ItemStack.of(itemTag);
        if (stack.isEmpty()) throw new IllegalArgumentException("Missing EF item");
        stack.setCount(tag.getInt("Count")); return stack;
    }
}
