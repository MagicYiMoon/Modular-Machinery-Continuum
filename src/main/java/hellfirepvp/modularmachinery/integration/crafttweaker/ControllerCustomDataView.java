/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.data.IData;
import com.blamejared.crafttweaker.api.data.converter.tag.TagToDataConverter;
import com.blamejared.crafttweaker.api.data.visitor.DataVisitor;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** Mutable source-style IData view. Children share the write guard and mark only actual changes for saving. */
public final class ControllerCustomDataView implements IData {
    private final CompoundTag root;
    private final IData delegate;
    private final BooleanSupplier writable;
    private final Runnable changed;

    private ControllerCustomDataView(CompoundTag root, IData delegate, BooleanSupplier writable, Runnable changed) {
        this.root = root; this.delegate = delegate; this.writable = writable; this.changed = changed;
    }
    public static IData create(TileController controller) {
        // Client/animation and asynchronous reads get detached observations, never a mutable world alias.
        boolean writable = controller.getLevel() instanceof ServerLevel && controller.canChangeRuntimeModifiers();
        CompoundTag root = writable ? controller.getCustomDataTag() : controller.getCustomDataTag().copy();
        String machine = controller.getMachineName();
        return guarded(root, () -> controller.getLevel() instanceof ServerLevel && controller.canChangeRuntimeModifiers()
                && controller.getCustomDataTag() == root && Objects.equals(machine, controller.getMachineName()),
                controller::markNoUpdateSync);
    }
    /** Reuses the same nested IData guards for bus/item data; no second serialization format. */
    public static IData guarded(CompoundTag root, BooleanSupplier writable, Runnable changed) {
        return new ControllerCustomDataView(root, TagToDataConverter.convert(root), writable, changed);
    }
    private IData wrap(IData data) {
        if (data == null) return null;
        Tag tag = data.getInternal();
        return tag instanceof CompoundTag || tag instanceof CollectionTag<?>
                ? new ControllerCustomDataView(root, data, writable, changed) : data;
    }
    private IData mutate(Supplier<IData> action) {
        if (!writable.getAsBoolean()) return this;
        CompoundTag before = root.copy();
        try { return wrap(action.get()); }
        finally { if (!root.equals(before)) changed.run(); }
    }
    private static IData detached(IData value) { return value == null ? null : value.copyInternal(); }
    @Override public void put(String key, IData value) { mutate(() -> { delegate.put(key, detached(value)); return delegate; }); }
    @Override public void setAt(String key, IData value) { mutate(() -> { delegate.setAt(key, detached(value)); return delegate; }); }
    @Override public void remove(String key) { mutate(() -> { delegate.remove(key); return delegate; }); }
    @Override public void remove(int index) { mutate(() -> { delegate.remove(index); return delegate; }); }
    @Override public IData add(IData value) { return mutate(() -> delegate.add(detached(value))); }
    @Override public IData merge(IData value) { return mutate(() -> delegate.merge(detached(value))); }
    @Override public IData sub(IData value) { return mutate(() -> delegate.sub(detached(value))); }
    @Override public IData mul(IData value) { return mutate(() -> delegate.mul(detached(value))); }
    @Override public IData div(IData value) { return mutate(() -> delegate.div(detached(value))); }
    @Override public IData mod(IData value) { return mutate(() -> delegate.mod(detached(value))); }
    @Override public IData cat(IData value) { return mutate(() -> delegate.cat(detached(value))); }
    @Override public IData or(IData value) { return mutate(() -> delegate.or(detached(value))); }
    @Override public IData and(IData value) { return mutate(() -> delegate.and(detached(value))); }
    @Override public IData xor(IData value) { return mutate(() -> delegate.xor(detached(value))); }
    @Override public IData shl(IData value) { return mutate(() -> delegate.shl(detached(value))); }
    @Override public IData shr(IData value) { return mutate(() -> delegate.shr(detached(value))); }
    @Override public IData neg() { return wrap(delegate.neg()); }
    @Override public IData not() { return wrap(delegate.not()); }
    @Override public IData getAt(String key) { return wrap(delegate.getAt(key)); }
    @Override public IData getAt(int index) { return wrap(delegate.getAt(index)); }
    @Override public boolean contains(IData other) { return delegate.contains(other); }
    @Override public boolean equalTo(IData other) { return delegate.equalTo(other); }
    @Override public int compareTo(IData other) { return delegate.compareTo(other); }
    @Override public boolean asBool() { return delegate.asBool(); }
    @Override public byte asByte() { return delegate.asByte(); }
    @Override public short asShort() { return delegate.asShort(); }
    @Override public int asInt() { return delegate.asInt(); }
    @Override public long asLong() { return delegate.asLong(); }
    @Override public float asFloat() { return delegate.asFloat(); }
    @Override public double asDouble() { return delegate.asDouble(); }
    @Override public String asString() { return delegate.asString(); }
    @Override public String getAsString() { return delegate.getAsString(); }
    @Override public List<IData> asList() {
        var result = new ArrayList<IData>(); delegate.asList().forEach(value -> result.add(wrap(value))); return result;
    }
    @Override public Map<String, IData> asMap() {
        var result = new LinkedHashMap<String, IData>(); delegate.asMap().forEach((key, value) -> result.put(key, wrap(value))); return result;
    }
    @Override public byte[] asByteArray() { return delegate.asByteArray().clone(); }
    @Override public int[] asIntArray() { return delegate.asIntArray().clone(); }
    @Override public long[] asLongArray() { return delegate.asLongArray().clone(); }
    @Override public int length() { return delegate.length(); }
    @Override public Set<String> getKeys() { return new LinkedHashSet<>(delegate.getKeys()); }
    @Override public Iterator<IData> iterator() {
        var result = new ArrayList<IData>(); delegate.iterator().forEachRemaining(value -> result.add(wrap(value))); return result.iterator();
    }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public byte getId() { return delegate.getId(); }
    @Override public Tag getInternal() { return delegate.getInternal().copy(); }
    /** CT exports copy(), but not copyInternal(); give script authors a usable independent snapshot. */
    @Override public IData copy() { return delegate.copyInternal(); }
    @Override public IData copyInternal() { return delegate.copyInternal(); }
    @Override public <T> T accept(DataVisitor<T> visitor) { return delegate.copyInternal().accept(visitor); }
    @Override public IData.Type getType() { return delegate.getType(); }
    @Override public boolean isListable() { return delegate.isListable(); }
    @Override public boolean isMappable() { return delegate.isMappable(); }
    @Override public boolean equals(Object other) { return other instanceof IData data && equalTo(data); }
    @Override public int hashCode() { return delegate.hashCode(); }
    @Override public String toString() { return delegate.toString(); }
}
