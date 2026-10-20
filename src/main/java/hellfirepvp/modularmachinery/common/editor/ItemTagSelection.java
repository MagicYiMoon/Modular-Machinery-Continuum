/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** The editor uses the runtime's existing tag:namespace:path item selector. */
public final class ItemTagSelection {
    private ItemTagSelection() {}

    public static boolean isTag(JsonObject requirement) {
        return requirement != null && requirement.has("item") && requirement.get("item").isJsonPrimitive()
                && requirement.get("item").getAsString().startsWith("tag:");
    }

    public static ResourceLocation id(String selector) {
        return selector != null && selector.startsWith("tag:") ? ResourceLocation.tryParse(selector.substring(4)) : null;
    }

    public static List<Item> members(ResourceLocation id) {
        if (id == null) return List.of();
        return BuiltInRegistries.ITEM.getTag(ItemTags.create(id))
                .map(tag -> tag.stream().map(holder -> holder.value()).filter(item -> item != Items.AIR).toList())
                .orElse(List.of());
    }

    public static void validate(String selector, boolean output) {
        if (output) throw new IllegalArgumentException("物品标签只用于输入；输出请选择具体物品");
        ResourceLocation id = id(selector);
        if (id == null) throw new IllegalArgumentException("无效的物品标签 ID");
        if (members(id).isEmpty()) throw new IllegalArgumentException("物品标签不存在或不含可用物品");
    }

    public static ItemStack representative(String selector) {
        // A stable example icon; never replace the selector with this concrete item on save.
        return members(id(selector)).stream()
                .min(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** @ searches the tag namespace; # searches the tag ID; plain terms also search member names. */
    public static boolean matches(String query, ResourceLocation id, Supplier<List<String>> memberNames) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<String> names = null;
        if (normalized.isEmpty()) return true;
        for (String term : normalized.split("\\s+")) {
            if (term.startsWith("@")) {
                if (term.length() == 1 || !id.getNamespace().contains(term.substring(1))) return false;
            } else if (term.startsWith("#")) {
                if (term.length() == 1 || !id.toString().contains(term.substring(1))) return false;
            } else if (!id.toString().contains(term)) {
                if (names == null) names = memberNames.get().stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();
                if (names.stream().noneMatch(name -> name.contains(term))) return false;
            }
        }
        return true;
    }
}
