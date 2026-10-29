/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.modifier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/** Source thread scopes, plus remembered defaults so preset polling cannot erase live script edits. */
public final class RuntimeModifierState {
    private final Map<String, RecipeModifier> permanent = new LinkedHashMap<>(), semi = new LinkedHashMap<>();
    private final Map<String, RecipeModifier> presetPermanent = new LinkedHashMap<>(), presetSemi = new LinkedHashMap<>();
    public Map<String, RecipeModifier> permanent() { return Collections.unmodifiableMap(permanent); }
    public Map<String, RecipeModifier> semi() { return Collections.unmodifiableMap(semi); }
    public boolean put(boolean persistent, String key, RecipeModifier modifier) {
        if (key == null || key.isBlank() || modifier == null) return false;
        Map<String, RecipeModifier> map = persistent ? permanent : semi;
        if (same(map.get(key), modifier)) return false;
        map.put(key, modifier); return true;
    }
    public boolean remove(boolean persistent, String key) { return (persistent ? permanent : semi).remove(key) != null; }
    public boolean clearSemi() { boolean changed = !semi.isEmpty(); semi.clear(); return changed; }
    public void clear() { permanent.clear(); semi.clear(); presetPermanent.clear(); presetSemi.clear(); }
    public List<RecipeModifier> active() {
        var result = new ArrayList<>(semi.values()); result.addAll(permanent.values()); return result;
    }
    public boolean configure(Map<String, RecipeModifier> newPermanent, Map<String, RecipeModifier> newSemi) {
        boolean changed = reconcile(permanent, presetPermanent, newPermanent);
        return reconcile(semi, presetSemi, newSemi) || changed;
    }
    private static boolean reconcile(Map<String, RecipeModifier> active, Map<String, RecipeModifier> previous,
                                     Map<String, RecipeModifier> requested) {
        Map<String, RecipeModifier> next = requested == null ? Map.of() : requested;
        boolean changed = false;
        for (var entry : previous.entrySet()) {
            if (!next.containsKey(entry.getKey()) && same(active.get(entry.getKey()), entry.getValue())) {
                active.remove(entry.getKey()); changed = true;
            }
        }
        for (var entry : next.entrySet()) {
            String key = entry.getKey(); RecipeModifier value = entry.getValue();
            if (key == null || key.isBlank() || value == null || same(previous.get(key), value)) continue;
            if (!active.containsKey(key) || same(active.get(key), previous.get(key))) {
                active.put(key, value); changed = true;
            }
        }
        // Even empty scopes are remembered: completed/removed defaults must not be reinstalled every tick.
        previous.clear(); previous.putAll(next); return changed;
    }
    public static boolean same(RecipeModifier a, RecipeModifier b) {
        return a == b || a != null && b != null && a.serialize().equals(b.serialize());
    }
    public void write(CompoundTag tag) {
        writeMap(tag, "permanentModifiers", permanent); writeMap(tag, "semiPermanentModifiers", semi);
        writeMap(tag, "presetPermanentModifiers", presetPermanent); writeMap(tag, "presetSemiPermanentModifiers", presetSemi);
    }
    public void read(CompoundTag tag) {
        clear(); readMap(tag, "permanentModifiers", permanent); readMap(tag, "semiPermanentModifiers", semi);
        readMap(tag, "presetPermanentModifiers", presetPermanent); readMap(tag, "presetSemiPermanentModifiers", presetSemi);
    }
    private static void writeMap(CompoundTag tag, String name, Map<String, RecipeModifier> map) {
        var list = new ListTag();
        map.forEach((key, value) -> { var entry = new CompoundTag(); entry.putString("key", key);
            entry.put("modifier", value.serialize()); list.add(entry); });
        tag.put(name, list);
    }
    private static void readMap(CompoundTag tag, String name, Map<String, RecipeModifier> map) {
        for (Tag value : tag.getList(name, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value; var modifier = RecipeModifier.deserialize(entry.getCompound("modifier"));
            if (!entry.getString("key").isBlank() && modifier != null) map.put(entry.getString("key"), modifier);
        }
    }
}
