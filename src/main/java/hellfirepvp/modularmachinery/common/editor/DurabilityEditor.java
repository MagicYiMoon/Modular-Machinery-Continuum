/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.List;

/** Ordinary source item.consumeDurability input; alias types are retained when editing. */
public final class DurabilityEditor {
    public static final String ALIAS = "modularmachinery:item_durability";
    private DurabilityEditor() {}

    public static boolean isDurability(JsonObject json) {
        if (json == null || !json.has("type")) return false;
        String type = json.get("type").getAsString();
        if (ALIAS.equals(type)) return true;
        if (!"modularmachinery:item".equals(type) || !json.has("consumeDurability")) return false;
        try { return json.get("consumeDurability").getAsBigDecimal().signum() != 0; }
        catch (RuntimeException invalid) { return true; }
    }

    public static boolean damageable(Item item) {
        var stack = item.getDefaultInstance();
        return stack.isDamageableItem() && stack.getMaxDamage() > 0;
    }

    public static boolean usableTag(ResourceLocation tag) {
        return ItemTagSelection.members(tag).stream().anyMatch(DurabilityEditor::damageable);
    }

    public static JsonObject create(String resource, int uses, int damage, float chance) {
        var result = BasicRequirementType.ITEM.create(resource, false, uses, chance);
        if (damage < 1 || damage > 2_000_000_000) throw new IllegalArgumentException("每次耐久消耗必须为 1～2000000000");
        if (resource.startsWith("tag:")) {
            if (!usableTag(ItemTagSelection.id(resource))) throw new IllegalArgumentException("标签内没有可消耗耐久的物品");
        } else if (!damageable(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(resource)))) {
            throw new IllegalArgumentException("请选择可消耗耐久的工具或物品");
        }
        result.addProperty("consumeDurability", damage);
        return result;
    }

    public static net.minecraft.world.item.ItemStack representative(String resource) {
        return ItemTagSelection.members(ItemTagSelection.id(resource)).stream().filter(DurabilityEditor::damageable)
                .min(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .map(net.minecraft.world.item.ItemStack::new).orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    public static boolean canEdit(JsonObject json) {
        try { validate(json); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    public static JsonObject validate(JsonObject json) {
        if (!isDurability(json) || !json.has("io-type") || !"input".equals(json.get("io-type").getAsString())
                || json.has("nbt") || json.has("nbt-display") || !json.has("item")
                || !json.get("item").isJsonPrimitive() || !json.getAsJsonPrimitive("item").isString())
            throw new IllegalArgumentException("仅支持无 NBT 的耐久输入；高级条件请修改源文件");
        if (json.has("chance") && (!json.get("chance").isJsonPrimitive() || !json.getAsJsonPrimitive("chance").isNumber()))
            throw new IllegalArgumentException("扣耐久概率必须为 0～1 的数字");
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1F;
        return create(json.get("item").getAsString(), integer(json, "amount", 1), integer(json, "consumeDurability", 0), chance);
    }

    public static JsonObject merge(JsonObject source, JsonObject edited) {
        var clean = validate(edited);
        if (source != null && !canEdit(source)) throw new IllegalArgumentException("源需求不是普通耐久输入，或包含高级条件，请修改源文件");
        if (source != null && ItemTagSelection.isTag(source) != ItemTagSelection.isTag(clean))
            throw new IllegalArgumentException("请保持具体工具或标签类型，更改类型请另建需求");
        var result = source == null ? clean : source.deepCopy();
        for (String key : List.of("item", "amount", "chance", "consumeDurability")) result.add(key, clean.get(key));
        return result;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        try {
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException();
            return json.get(key).getAsBigDecimal().intValueExact();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("使用次数和每次耐久消耗必须为整数"); }
    }
}
