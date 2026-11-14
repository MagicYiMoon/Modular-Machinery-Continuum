/* SPDX-License-Identifier: GPL-3.0-only
 * Physical server assembly adapts NECO ModularServer, TileModularServerAssembler,
 * SlotConditionItemHandler and AssemblySlotManager, KasumiNova and contributors,
 * revision b37fbb53. The bounded, versioned 1.20.1 persistence is MMC code.
 */
package hellfirepvp.modularmachinery.common.hypernet.server;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerAssemblyLayout.*;

/**
 * Server-thread assembly state owned by one future assembler session, not a live
 * HyperNet producer. Never exposes mutable stacks or an IItemHandlerModifiable.
 * The tile must persist stack() after a successful edit and invalidate old sessions
 * when its server item changes; this object must not be exported as a capability.
 */
public final class ServerAssemblyInventory {
    public static final int VERSION = 1, MAX_NBT_BYTES = 256 * 1024, MAX_DEPTH = 32;
    private static final String VERSION_KEY = "mmcAssemblyVersion";
    private record Value(CompoundTag raw, ItemStack stack) { }
    public record Missing(Address slot, Address dependency, boolean cooling) { }
    private final ItemStack original;
    private final CompoundTag originalData;
    private final Function<ItemStack, Kind> classifier;
    private final Map<Address, Value> contents = new HashMap<>();
    private final EnumMap<Bank, IItemHandler> handlers = new EnumMap<>(Bank.class);
    private long revision;

    /** Unknown/future/malformed assembly data is rejected before any item can be edited. */
    public ServerAssemblyInventory(ItemStack server, Function<ItemStack, Kind> classifier) {
        if (server.isEmpty() || server.getCount() != 1)
            throw new IllegalArgumentException("Assembly requires exactly one server item");
        this.classifier = Objects.requireNonNull(classifier);
        // Inspect the original tag before ItemStack.copy/save can recursively copy it.
        if (server.getTag() != null) checkBounds(server.getTag());
        checkBounds(server.save(new CompoundTag()));
        original = server.copy();
        var tag = server.getTag();
        if (tag != null && tag.contains("server") && !tag.contains("server", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid server assembly compound");
        originalData = tag == null ? new CompoundTag() : tag.getCompound("server").copy();
        if (!originalData.isEmpty()) {
            if (!originalData.contains(VERSION_KEY, Tag.TAG_INT) || originalData.getInt(VERSION_KEY) != VERSION)
                throw new IllegalArgumentException("Unsupported server assembly format; original item must be retained");
            read();
        }
        for (var bank : Bank.values()) handlers.put(bank, new Handler(bank));
    }
    public IItemHandler inventory(Bank bank) { return Objects.requireNonNull(handlers.get(bank)); }
    public long revision() { return revision; }
    public boolean occupied(Address address) { slot(address); return contents.containsKey(address); }
    public ItemStack get(Address address) {
        slot(address); var value = contents.get(address);
        return value == null ? ItemStack.EMPTY : value.stack().copy();
    }
    /** Missing item IDs retain their complete raw tag, block replacement, and grant no capability. */
    public boolean unresolved(Address address) {
        var value = contents.get(address);
        return value != null && value.stack().isEmpty();
    }
    private Kind kind(Address address) {
        var stack = get(address);
        return stack.isEmpty() ? null : classifier.apply(stack);
    }
    public boolean installed(Address address) { return occupied(address) && kind(address) == slot(address).kind(); }
    public boolean available(Address address) {
        for (var dependency : slot(address).dependencies()) if (!installed(dependency)) return false;
        return true;
    }
    public boolean mayInsert(Address address, ItemStack stack) {
        if (stack.isEmpty() || occupied(address) || !available(address)) return false;
        try {
            if (stack.getTag() != null) checkBounds(stack.getTag());
            checkBounds(stack.save(new CompoundTag()));
        } catch (IllegalArgumentException rejected) { return false; }
        return classifier.apply(stack.copy()) == slot(address).kind();
    }
    public boolean mayExtract(Address address) {
        if (!occupied(address) || unresolved(address)) return false;
        for (var dependent : dependents(address)) if (installed(dependent)) return false;
        return true;
    }
    /** Stable bank/index ordering; soft cooling dependencies report a fault without disabling the slot. */
    public List<Address> activeModules() {
        var active = new ArrayList<Address>();
        for (var definition : slots()) if (available(definition.address()) && installed(definition.address()))
            active.add(definition.address());
        return List.copyOf(active);
    }
    public List<Missing> missingDependencies() {
        var missing = new ArrayList<Missing>();
        for (var definition : slots()) if (installed(definition.address())) {
            for (var dependency : definition.dependencies()) if (!installed(dependency))
                missing.add(new Missing(definition.address(), dependency, false));
            for (var cooling : definition.cooling()) if (!installed(cooling))
                missing.add(new Missing(definition.address(), cooling, true));
        }
        return List.copyOf(missing);
    }
    /** Complete physical item snapshot: custom name, tags, ForgeCaps and opaque server properties survive. */
    public ItemStack stack() { return encode(contents); }
    private ItemStack encode(Map<Address, Value> candidate) {
        var data = originalData.copy(); data.putInt(VERSION_KEY, VERSION);
        for (var bank : Bank.values()) {
            var inventory = data.contains(bank.nbtKey(), Tag.TAG_COMPOUND)
                    ? data.getCompound(bank.nbtKey()).copy() : new CompoundTag();
            inventory.putInt("Size", bank.size());
            var items = new ListTag();
            for (int i = 0; i < bank.size(); i++) {
                var value = candidate.get(new Address(bank, i));
                if (value == null) continue;
                var item = value.raw().copy(); item.putInt("Slot", i); items.add(item);
            }
            inventory.put("Items", items); data.put(bank.nbtKey(), inventory);
        }
        var result = original.copy(); result.getOrCreateTag().put("server", data);
        checkBounds(result.save(new CompoundTag()));
        return result;
    }
    private void read() {
        for (var bank : Bank.values()) {
            if (!originalData.contains(bank.nbtKey(), Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Missing assembly inventory: " + bank);
            var inventory = originalData.getCompound(bank.nbtKey());
            if (!inventory.contains("Size", Tag.TAG_INT) || inventory.getInt("Size") != bank.size()
                    || !inventory.contains("Items", Tag.TAG_LIST))
                throw new IllegalArgumentException("Invalid assembly inventory shape: " + bank);
            var list = (ListTag) inventory.get("Items");
            if ((!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) || list.size() > bank.size())
                throw new IllegalArgumentException("Invalid assembly items: " + bank);
            for (int i = 0; i < list.size(); i++) {
                var raw = list.getCompound(i).copy();
                if (!raw.contains("Slot", Tag.TAG_INT)) throw new IllegalArgumentException("Missing assembly slot");
                var address = new Address(bank, raw.getInt("Slot"));
                raw.remove("Slot");
                if (!raw.contains("id", Tag.TAG_STRING) || ResourceLocation.tryParse(raw.getString("id")) == null
                        || raw.getString("id").equals("minecraft:air")
                        || !raw.contains("Count", Tag.TAG_BYTE) || raw.getByte("Count") != 1
                        || (raw.contains("tag") && !raw.contains("tag", Tag.TAG_COMPOUND))
                        || (raw.contains("ForgeCaps") && !raw.contains("ForgeCaps", Tag.TAG_COMPOUND)))
                    throw new IllegalArgumentException("Invalid assembly item stack");
                var decoded = ItemStack.of(raw);
                if (contents.put(address, new Value(raw, decoded)) != null)
                    throw new IllegalArgumentException("Duplicate assembly slot");
            }
        }
    }
    private void commit(Map<Address, Value> candidate) {
        // Validate the whole result before changing any slot or advancing the revision.
        encode(candidate);
        contents.clear(); contents.putAll(candidate); revision++;
    }
    private final class Handler implements IItemHandler {
        private final Bank bank;
        Handler(Bank bank) { this.bank = bank; }
        private Address at(int slot) { return new Address(bank, slot); }
        @Override public int getSlots() { return bank.size(); }
        @Override public ItemStack getStackInSlot(int slot) { return get(at(slot)); }
        @Override public int getSlotLimit(int slot) { at(slot); return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return mayInsert(at(slot), stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var address = at(slot);
            if (!mayInsert(address, stack)) return stack;
            var one = stack.copyWithCount(1);
            var candidate = new HashMap<>(contents);
            candidate.put(address, new Value(one.save(new CompoundTag()), one));
            // Oversized module data is refused without consuming it, including during simulation.
            try {
                if (simulate) encode(candidate); else commit(candidate);
            } catch (IllegalArgumentException rejected) { return stack; }
            return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var address = at(slot);
            if (amount <= 0 || !mayExtract(address)) return ItemStack.EMPTY;
            var result = get(address);
            if (!simulate) {
                var candidate = new HashMap<>(contents); candidate.remove(address); commit(candidate);
            }
            return result;
        }
    }
    private record Node(Tag tag, int depth) { }
    private static void checkBounds(CompoundTag tag) {
        var nodes = new ArrayDeque<Node>(); nodes.add(new Node(tag, 0));
        int visited = 0;
        while (!nodes.isEmpty()) {
            var current = nodes.removeLast();
            if (++visited > MAX_NBT_BYTES || current.depth() > MAX_DEPTH)
                throw new IllegalArgumentException("Server assembly NBT is too complex");
            if (current.tag() instanceof CompoundTag compound) for (var key : compound.getAllKeys())
                nodes.add(new Node(Objects.requireNonNull(compound.get(key)), current.depth() + 1));
            else if (current.tag() instanceof ListTag list) for (var child : list)
                nodes.add(new Node(child, current.depth() + 1));
        }
        try {
            NbtIo.write(tag, new DataOutputStream(new OutputStream() {
                private int bytes;
                @Override public void write(int value) throws IOException { if (++bytes > MAX_NBT_BYTES) throw new IOException("Assembly NBT size limit"); }
                @Override public void write(byte[] data, int offset, int length) throws IOException {
                    if (length > MAX_NBT_BYTES - bytes) throw new IOException("Assembly NBT size limit");
                    bytes += length;
                }
            }));
        } catch (IOException tooLarge) { throw new IllegalArgumentException("Server assembly NBT exceeds size limit", tooLarge); }
    }
}
