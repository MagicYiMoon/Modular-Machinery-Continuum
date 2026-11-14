/* SPDX-License-Identifier: GPL-3.0-only; source concept: NECO EFabricatorController.outputBuffer. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Private, bounded, long-count item buffer. Templates include NBT and serialized Forge capabilities. */
public final class EFabricatorOutputBuffer {
    public static final int MAX_TYPES = 4096;
    public record Entry(ItemStack item, long amount) {
        public Entry {
            if (item == null || item.isEmpty() || amount <= 0) throw new IllegalArgumentException("Invalid EF output");
            item = item.copyWithCount(1);
        }
        @Override public ItemStack item() { return item.copy(); }
    }
    private final LinkedHashMap<CompoundTag, Entry> entries = new LinkedHashMap<>();
    private static CompoundTag key(ItemStack item) { return item.copyWithCount(1).save(new CompoundTag()); }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public boolean isEmpty() { return entries.isEmpty(); }
    /** Commit all products and remainders together, or leave the buffer unchanged. */
    public boolean add(EFabricatorQueue.Work work) {
        var changes = new LinkedHashMap<CompoundTag, Entry>();
        var products = new ArrayList<ItemStack>(); products.add(work.output()); products.addAll(work.remaining());
        try {
            for (var stack : products) if (!stack.isEmpty()) {
                var key = key(stack); var old = changes.containsKey(key) ? changes.get(key) : entries.get(key);
                long amount = Math.addExact(old == null ? 0 : old.amount(), Math.multiplyExact((long)stack.getCount(), work.crafts()));
                changes.put(key, new Entry(stack, amount));
            }
        } catch (ArithmeticException failure) { return false; }
        long newTypes = changes.keySet().stream().filter(k -> !entries.containsKey(k)).count();
        if (entries.size() + newTypes > MAX_TYPES) return false;
        entries.putAll(changes); return true;
    }
    /** Called only with the actual amount accepted by ME; never removes an unaccepted remainder. */
    public void remove(Entry expected, long amount) {
        var key = key(expected.item()); var current = entries.get(key);
        if (current == null || current.amount() != expected.amount() || amount < 0 || amount > current.amount())
            throw new IllegalArgumentException("Stale EF output removal");
        if (amount == current.amount()) entries.remove(key);
        else if (amount > 0) entries.put(key, new Entry(current.item(), current.amount() - amount));
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Version", 1); var list = new ListTag();
        for (var entry : entries.values()) {
            var item = new CompoundTag(); item.put("Item", key(entry.item())); item.putLong("Amount", entry.amount()); list.add(item);
        }
        tag.put("Entries", list); return tag;
    }
    public static EFabricatorOutputBuffer load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.contains("Entries", Tag.TAG_LIST)) throw new IllegalArgumentException("Unknown EF output format");
        var list = tag.getList("Entries", Tag.TAG_COMPOUND);
        if (list.size() != ((ListTag)tag.get("Entries")).size() || list.size() > MAX_TYPES) throw new IllegalArgumentException("Invalid EF outputs");
        var result = new EFabricatorOutputBuffer();
        for (var raw : list) {
            var entry = (CompoundTag)raw;
            var stackTag = new CompoundTag(); stackTag.put("Item", entry.getCompound("Item")); stackTag.putInt("Count", 1);
            var item = EFabricatorQueue.readStack(stackTag, false); var key = key(item);
            if (!entry.contains("Amount", Tag.TAG_LONG) || entry.getLong("Amount") <= 0 || result.entries.containsKey(key))
                throw new IllegalArgumentException("Invalid EF output amount or duplicate");
            result.entries.put(key, new Entry(item, entry.getLong("Amount")));
        }
        return result;
    }
}
