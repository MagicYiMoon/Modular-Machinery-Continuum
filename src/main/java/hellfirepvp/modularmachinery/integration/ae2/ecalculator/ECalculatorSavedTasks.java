/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;

/** AE's readers skip missing keys/patterns. Validate first so dependency loss cannot silently erase a saved task. */
final class ECalculatorSavedTasks {
    private ECalculatorSavedTasks() { }
    static ListTag compounds(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) throw new IllegalArgumentException("missing task list: " + key);
        var list = (ListTag)tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("invalid task list: " + key);
        return list;
    }
    private static void stacks(CompoundTag tag, String key) {
        for (var value : compounds(tag, key)) {
            var entry = (CompoundTag)value;
            if (AEKey.fromTagGeneric(entry) == null || !entry.contains("#", Tag.TAG_LONG) || entry.getLong("#") < 0)
                throw new IllegalArgumentException("unreadable task stack: " + key);
        }
    }
    static void validate(CompoundTag logic, Level level) {
        stacks(logic, "inventory");
        if (!logic.contains("job")) return;
        if (!logic.contains("job", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid task job");
        var job = logic.getCompound("job");
        var output = GenericStack.readTag(job.getCompound("finalOutput"));
        long remaining = job.getLong("remainingAmount");
        if (output == null || output.amount() <= 0 || remaining <= 0 || remaining > output.amount())
            throw new IllegalArgumentException("invalid task output");
        var link = job.getCompound("link");
        if (!link.hasUUID("craftId") || !link.contains("req", Tag.TAG_BYTE) || link.getBoolean("req"))
            throw new IllegalArgumentException("invalid CPU task link");
        stacks(job, "waitingFor");
        for (var value : compounds(job, "tasks")) {
            var entry = (CompoundTag)value;
            var key = AEItemKey.fromTag(entry);
            if (key == null || PatternDetailsHelper.decodePattern(key, level) == null
                    || !entry.contains("#craftingProgress", Tag.TAG_LONG) || entry.getLong("#craftingProgress") < 0)
                throw new IllegalArgumentException("unreadable task pattern");
        }
    }
}
