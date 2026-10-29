/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.ResourceAmountEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Bounded mana/Ember authoring carried only inside the revision-bound editor envelope. */
public record PktMachineEditorResourceAmount(String recipeId, int index, String requirementJson,
                                             String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorResourceAmount packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.requirementJson, 32767); buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks); buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorResourceAmount decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorResourceAmount(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorResourceAmount packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.requirementJson.length() > 32767)
                return "资源需求数据无效";
            var clean = ResourceAmountEditor.validate(JsonParser.parseString(packet.requirementJson).getAsJsonObject());
            if (!net.minecraftforge.fml.ModList.get().isLoaded(ResourceAmountEditor.modId(clean))) return "服务器未安装对应资源模组";
            if (ResourceAmountEditor.isAura(clean) && !hellfirepvp.modularmachinery.integration.naturesaura.AuraRegistration.editorTypes().contains(clean.get("aura-type").getAsString()))
                return "服务器未注册此灵气类型";
            return PktMachineEditorRequirementAdd.save(player, new PktMachineEditorRequirementAdd(
                    packet.recipeId, "", "", false, 1, 1, packet.newMachine, packet.ticks, packet.displayName), clean, packet.index);
        } catch (RuntimeException invalid) { return "资源需求无效：" + invalid.getMessage(); }
    }
}
