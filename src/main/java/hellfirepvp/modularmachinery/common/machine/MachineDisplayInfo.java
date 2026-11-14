/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Author-facing labels only. Never used as a recipe or module permission. */
public record MachineDisplayInfo(String tier, List<String> tooltips) {
    public static final MachineDisplayInfo EMPTY = new MachineDisplayInfo("", List.of());
    public MachineDisplayInfo {
        tooltips = List.copyOf(tooltips);
        if (tier == null || tier.length() > 64 || tier.contains("\n") || tooltips.size() > 8
                || tooltips.stream().anyMatch(s -> s.length() > 200 || s.contains("\n")))
            throw new IllegalArgumentException("等级最多64字，说明最多8行、每行200字");
    }
    public static MachineDisplayInfo parse(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("display-info 必须是对象");
        var j=value.getAsJsonObject();
        for(var k:j.keySet()) if(!Set.of("tier","tooltips").contains(k)) throw new IllegalArgumentException("未知提示字段："+k);
        String tier=j.has("tier")?text(j.get("tier")):"";
        var lines=new ArrayList<String>();
        if(j.has("tooltips")) {
            if(!j.get("tooltips").isJsonArray())throw new IllegalArgumentException("tooltips 必须为文本数组");
            for(var e:j.getAsJsonArray("tooltips"))lines.add(text(e));
        }
        return new MachineDisplayInfo(tier,lines);
    }
    private static String text(JsonElement e){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("提示必须为文本");return e.getAsString();}
    public JsonObject json(){var j=new JsonObject();j.addProperty("tier",tier);var a=new JsonArray();tooltips.forEach(a::add);j.add("tooltips",a);return j;}
    public void append(List<Component> lines) {
        if(!tier.isBlank())lines.add(Component.translatable("tooltip.modularmachinery.machine_tier",MachineNameFormatting.display(tier)));
        tooltips.forEach(s->lines.add(Component.literal(MachineNameFormatting.display(s))));
    }
}
