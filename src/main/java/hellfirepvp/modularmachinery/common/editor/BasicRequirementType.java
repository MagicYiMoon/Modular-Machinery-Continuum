/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** The three basic requirements the visual editor can author. Validation is shared with the server. */
public enum BasicRequirementType {
    ITEM("item", "物品"), FLUID("fluid", "流体"), ENERGY("energy", "能量");

    public final String key;
    public final String label;

    BasicRequirementType(String key, String label) { this.key = key; this.label = label; }

    public static BasicRequirementType fromKey(String key) {
        for (var type : values()) if (type.key.equals(key)) return type;
        throw new IllegalArgumentException("不支持的需求类型");
    }

    public JsonObject create(String id, boolean output, int amount, float chance) {
        if (amount < 1 || amount > 2_000_000_000) throw new IllegalArgumentException("数量必须为 1～2000000000");
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("概率必须为 0～1");
        if (this == ITEM && amount > 64) throw new IllegalArgumentException("普通物品需求数量为 1～64，可通过批量倍率放大");
        JsonObject json = new JsonObject();
        json.addProperty("type", "modularmachinery:" + key);
        json.addProperty("io-type", output ? "output" : "input");
        if (this == ENERGY) {
            json.addProperty("energyPerTick", amount);
        } else if (this == ITEM && id != null && id.startsWith("tag:")) {
            ItemTagSelection.validate(id, output);
            json.addProperty(key, id);
            json.addProperty("amount", amount);
            json.addProperty("chance", chance);
        } else {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) throw new IllegalArgumentException("无效的资源 ID");
            if (this == ITEM && (!BuiltInRegistries.ITEM.containsKey(location) || BuiltInRegistries.ITEM.get(location) == Items.AIR))
                throw new IllegalArgumentException("物品不存在");
            if (this == FLUID && (!BuiltInRegistries.FLUID.containsKey(location) || BuiltInRegistries.FLUID.get(location) == Fluids.EMPTY))
                throw new IllegalArgumentException("流体不存在");
            json.addProperty(key, id);
            json.addProperty("amount", amount);
            json.addProperty("chance", chance);
        }
        return json;
    }
}
