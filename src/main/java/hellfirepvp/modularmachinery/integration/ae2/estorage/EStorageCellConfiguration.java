/* SPDX-License-Identifier: GPL-3.0-only
 * NECO EStorageCell two-slot/fuzzy contract and AE2 partition semantics.
 */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.*;
import appeng.api.stacks.*;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.util.prioritylist.IPartitionList;
import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Immutable configuration captured when the physical matrix is mounted. Never rewrites caller NBT. */
final class EStorageCellConfiguration {
    private final IPartitionList filter;
    private final IncludeExclude mode;
    EStorageCellConfiguration(EStorageCellSpec spec, CompoundTag owner) {
        // UpgradeInventories loads native AE item NBT. Use a small detached stack so its
        // getOrCreateTag / load callbacks cannot mutate the matrix or copy its contents.
        var stack = new ItemStack(EStorageRegistration.CELLS.get(spec).get());
        var tag = new CompoundTag();
        if (owner.contains("upgrades")) tag.put("upgrades", owner.get("upgrades").copy());
        stack.setTag(tag);
        var upgrades = UpgradeInventories.forItem(stack, 2);
        mode = upgrades.isInstalled(AEItems.INVERTER_CARD) ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        var builder = IPartitionList.builder();
        if (spec.kind() == EStorageCellSpec.Kind.ITEM && upgrades.isInstalled(AEItems.FUZZY_CARD))
            builder.fuzzyMode(fuzzyMode(owner));
        if (owner.contains("list")) {
            Tag raw = owner.get("list");
            if (!(raw instanceof ListTag list) || list.size() > 63
                    || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                throw invalid();
            for (int i = 0; i < list.size(); i++) {
                var entry = list.getCompound(i);
                if (entry.isEmpty()) continue;
                var key = AEKey.fromTagGeneric(entry);
                // Unknown filters must not silently turn into an unrestricted empty list.
                if (!EStorageKeySupport.accepts(spec, key)) throw invalid();
                builder.add(key);
            }
        }
        filter = builder.build();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Cannot open ES matrix; original NBT preserved: invalid partition filter");
    }
    static FuzzyMode fuzzyMode(CompoundTag tag) {
        if (tag != null) try { return FuzzyMode.valueOf(tag.getString("FuzzyMode")); }
        catch (IllegalArgumentException ignored) { }
        return FuzzyMode.IGNORE_ALL;
    }
    boolean accepts(AEKey key) { return filter.matchesFilter(key, mode); }
    boolean prefers(AEKey key) { return mode == IncludeExclude.WHITELIST && !filter.isEmpty() && filter.isListed(key); }
    int filterTypes() { int count = 0; for (var ignored : filter.getItems()) count++; return count; }
    boolean inverted() { return mode == IncludeExclude.BLACKLIST; }
}
