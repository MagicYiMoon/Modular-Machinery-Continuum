/* SPDX-License-Identifier: GPL-3.0-only
 * Port of NovaEngineering-Core's EStorageCellInventory by KasumiNova and contributors.
 * Upstream: b37fbb53e4012d15baac2d95a8c1d4a32c62d6a8, modified for Forge 1.20.1.
 * Replaces 1.12 AE internals/reflection with the public AE2 StorageCell API.
 */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import hellfirepvp.modularmachinery.common.estorage.EStorageCellSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-thread-only ES matrix content. A host owns and caches one inventory per matrix.
 * The owner compound is the matrix's root tag, so persisting never removes name/config/upgrades.
 * Deliberately not registered with native ME drives: the source requires an ES host and tier check.
 */
public final class EStorageCellInventory implements StorageCell, hellfirepvp.modularmachinery.common.estorage.EStorageMatrixAccess {
    public static final String DATA_TAG = "MMCEStorage";
    private static final int FORMAT = 1;
    private final EStorageCellSpec spec;
    private final AEKeyType keyType;
    private final CompoundTag owner;
    private final ISaveProvider host;
    private final Map<AEKey, Long> contents = new LinkedHashMap<>();
    private long storedAmount;
    private boolean dirty;
    private boolean closed;
    private final java.util.function.BooleanSupplier allowed;
    private final EStorageCellConfiguration configuration;

    public EStorageCellInventory(EStorageCellSpec spec, CompoundTag owner, ISaveProvider host) {
        this(spec, owner, host, () -> true);
    }
    public EStorageCellInventory(EStorageCellSpec spec, CompoundTag owner, ISaveProvider host,
                                 java.util.function.BooleanSupplier allowed) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.host = host;
        this.allowed = Objects.requireNonNull(allowed, "allowed");
        this.keyType = EStorageKeySupport.type(spec);
        this.configuration = new EStorageCellConfiguration(spec, owner);
        load();
    }

    private void load() {
        if (!owner.contains(DATA_TAG)) return;
        if (!owner.contains(DATA_TAG, Tag.TAG_COMPOUND)) throw invalid("content must be a compound");
        CompoundTag data = owner.getCompound(DATA_TAG);
        if (!data.contains("Version", Tag.TAG_INT) || data.getInt("Version") != FORMAT)
            throw invalid("unsupported format");
        if (!data.getString("Tier").equals(spec.tier().name()) || !data.getString("Kind").equals(spec.kind().name()))
            throw invalid("matrix tier or resource type differs");
        if (!data.contains("Entries", Tag.TAG_LIST)) throw invalid("missing entry list");
        Tag raw = data.get("Entries");
        if (!(raw instanceof ListTag entries) || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
            throw invalid("entry list must contain compounds");
        if (entries.size() > spec.maxTypes()) throw invalid("too many resource types");
        // Validate the entire snapshot before a host mounts it. Never silently delete unknown keys.
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            AEKey key = entry.contains("Key", Tag.TAG_COMPOUND) ? AEKey.fromTagGeneric(entry.getCompound("Key")) : null;
            if (!EStorageKeySupport.accepts(spec, key)) throw invalid("unknown or wrong-channel resource at " + i);
            if (!entry.contains("Amount", Tag.TAG_LONG) || entry.getLong("Amount") <= 0)
                throw invalid("non-positive or incorrectly typed amount at " + i);
            if (contents.putIfAbsent(key, entry.getLong("Amount")) != null) throw invalid("duplicate resource at " + i);
            try { storedAmount = Math.addExact(storedAmount, entry.getLong("Amount")); }
            catch (ArithmeticException ex) { throw invalid("amount overflow"); }
        }
        if (storedAmount > spec.capacityForTypes(contents.size(), keyType.getAmountPerByte()))
            throw invalid("amount exceeds matrix capacity");
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Cannot open ES matrix; original NBT preserved: " + reason);
    }

    public EStorageCellSpec specification() { return spec; }
    public long storedAmount() { return storedAmount; }
    public int storedTypes() { return contents.size(); }
    public int filterTypes() { return configuration.filterTypes(); }
    public boolean isInverted() { return configuration.inverted(); }
    public long usedBytes() {
        long units = spec.unitsPerByte(keyType.getAmountPerByte());
        long dataBytes = storedAmount / units + (storedAmount % units == 0 ? 0 : 1);
        return (long) contents.size() * spec.bytesPerType() + dataBytes;
    }
    public long freeBytes() { return spec.totalBytes() - usedBytes(); }
    public long remainingAmount() {
        return spec.capacityForTypes(contents.size(), keyType.getAmountPerByte()) - storedAmount;
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!isOpen() || amount <= 0 || !EStorageKeySupport.accepts(spec, key)) return 0;
        if (!configuration.accepts(key)) return 0;
        boolean existing = contents.containsKey(key);
        if (!existing && contents.size() >= spec.maxTypes()) return 0;
        if (key instanceof AEItemKey item) {
            var nested = StorageCells.getCellInventory(item.toStack(), null);
            if (nested != null && !nested.canFitInsideCell()) return 0;
        }
        long limit = spec.capacityForTypes(contents.size() + (existing ? 0 : 1), keyType.getAmountPerByte());
        long accepted = Math.min(amount, Math.max(0, limit - storedAmount));
        if (accepted > 0 && mode == Actionable.MODULATE) {
            contents.merge(key, accepted, Math::addExact);
            storedAmount += accepted;
            changed();
        }
        return accepted;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!isOpen() || key == null || amount <= 0) return 0;
        long existing = contents.getOrDefault(key, 0L);
        long extracted = Math.min(amount, existing);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            if (extracted == existing) contents.remove(key);
            else contents.put(key, existing - extracted);
            storedAmount -= extracted;
            changed();
        }
        return extracted;
    }

    private void changed() {
        dirty = true;
        if (host == null) persist();
        else host.saveChanges();
    }

    @Override
    public void persist() {
        if (!dirty) return;
        CompoundTag data = new CompoundTag();
        data.putInt("Version", FORMAT);
        data.putString("Tier", spec.tier().name());
        data.putString("Kind", spec.kind().name());
        ListTag entries = new ListTag();
        for (var entry : contents.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.put("Key", entry.getKey().toTagGeneric());
            saved.putLong("Amount", entry.getValue());
            entries.add(saved);
        }
        data.put("Entries", entries);
        owner.put(DATA_TAG, data);
        dirty = false;
    }

    @Override public void close() { persist(); closed = true; }
    @Override public boolean isOpen() {
        if (!closed && !allowed.getAsBoolean()) close();
        return !closed;
    }
    @Override public void getAvailableStacks(KeyCounter out) { if (isOpen()) contents.forEach(out::add); }
    @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return isOpen() && EStorageKeySupport.accepts(spec, key) && configuration.accepts(key)
                && (contents.containsKey(key) || configuration.prefers(key));
    }
    @Override public Component getDescription() { return Component.translatable("storage.modularmachinery.estorage_matrix"); }
    @Override public boolean canFitInsideCell() { return contents.isEmpty(); }
    @Override public double getIdleDrain() { return spec.idleDrain(); }
    @Override public CellState getStatus() {
        if (contents.isEmpty()) return CellState.EMPTY;
        if (remainingAmount() == 0) return CellState.FULL;
        if (contents.size() == spec.maxTypes() || remainingAmount() <=
                (long) spec.bytesPerType() * spec.unitsPerByte(keyType.getAmountPerByte())) return CellState.TYPES_FULL;
        return CellState.NOT_EMPTY;
    }
}
