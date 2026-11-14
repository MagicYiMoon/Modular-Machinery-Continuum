/* SPDX-License-Identifier: GPL-3.0-only
 * Physical research storage adapted from NECO old.Database, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import net.minecraft.nbt.*;
import java.math.BigDecimal;
import java.util.*;

/** Detached bounded ledger. Invalid/future/foreign data stays untouched and cannot accept writes. */
final class HyperNetDatabaseState {
    private final String machine;
    private final int capacity;
    private final Set<String> completed = new LinkedHashSet<>();
    private final Map<String,BigDecimal> progress = new LinkedHashMap<>();
    private boolean valid = true;
    private Map<String,HyperNetResearch> countedCatalog;
    private int completedCount;
    HyperNetDatabaseState(String machine,int capacity,Tag raw) {
        this.machine = machine; this.capacity = capacity;
        if (raw == null) return;
        try {
            if (!(raw instanceof CompoundTag tag) || !tag.contains("version",Tag.TAG_INT) || tag.getInt("version") != 1
                    || !tag.getString("machine").equals(machine) || !tag.contains("completed",Tag.TAG_LIST)
                    || !tag.contains("progress",Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid database identity/format");
            if (!tag.getAllKeys().equals(Set.of("version","machine","completed","progress"))) throw new IllegalArgumentException("Unknown database format fields");
            var list = tag.get("completed");
            if (!(list instanceof ListTag ids) || !ids.isEmpty() && ids.getElementType() != Tag.TAG_STRING) throw new IllegalArgumentException("Invalid completed list");
            var amounts = tag.getCompound("progress");
            if ((long)ids.size() + amounts.size() > HyperNetResearchRegistry.MAX_RESEARCH) throw new IllegalArgumentException("Oversized database");
            for (var item : ids) if (!completed.add(HyperNetResearch.checkedId(item.getAsString()))) throw new IllegalArgumentException("Duplicate research record");
            for (var id : amounts.getAllKeys()) {
                HyperNetResearch.checkedId(id);
                if (!amounts.contains(id,Tag.TAG_STRING) || completed.contains(id) || amounts.getString(id).length() > 1200) throw new IllegalArgumentException("Invalid progress entry");
                var amount = new BigDecimal(amounts.getString(id));
                if (!HyperNetResearch.supportedProgress(amount)) throw new IllegalArgumentException("Invalid research progress");
                progress.put(id,amount);
            }
        } catch (RuntimeException e) { valid = false; completed.clear(); progress.clear(); }
    }
    boolean valid() { return valid; }
    int used() { return completed.size()+progress.size(); }
    /** Unknown records stay stored, but upstream only charges for recognized completed research. */
    int completedCount() {
        var catalog=HyperNetResearchRegistry.snapshot();
        if(countedCatalog!=catalog){completedCount=0;if(valid)for(var id:completed)if(catalog.containsKey(id))completedCount++;countedCatalog=catalog;}
        return completedCount;
    }
    boolean has(String id) { return valid && HyperNetResearchRegistry.find(id) != null && completed.contains(id); }
    BigDecimal progress(String id) { return valid && HyperNetResearchRegistry.find(id) != null ? progress.get(id) : null; }
    boolean room(String id) { return valid && !completed.contains(id) && (progress.containsKey(id) || used() < capacity); }
    boolean write(String id,BigDecimal amount) {
        var definition = HyperNetResearchRegistry.find(id);
        if (definition == null || !HyperNetResearch.supportedProgress(amount) || amount.compareTo(definition.requiredPoints()) > 0
                || !room(id)
                || amount.compareTo(progress.getOrDefault(id,BigDecimal.ZERO)) < 0) return false;
        progress.put(id,amount); return true;
    }
    boolean complete(String id) {
        var definition = HyperNetResearchRegistry.find(id);
        if (definition == null || !valid) return false;
        if (has(id)) return true;
        var points = progress.get(id);
        if (points == null || points.compareTo(definition.requiredPoints()) < 0) return false;
        progress.remove(id); completed.add(id); countedCatalog=null; return true;
    }
    boolean clearProgress(String id) { return valid && progress.remove(id) != null; }
    CompoundTag save() {
        if (!valid) throw new IllegalStateException("Cannot replace invalid database data");
        var tag = new CompoundTag(); tag.putInt("version",1); tag.putString("machine",machine);
        var ids = new ListTag(); completed.forEach(id -> ids.add(StringTag.valueOf(id))); tag.put("completed",ids);
        var amounts = new CompoundTag(); progress.forEach((id,n) -> amounts.putString(id,n.toString())); tag.put("progress",amounts); return tag;
    }
}
