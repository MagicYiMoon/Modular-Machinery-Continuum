/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.SmartInterfaceEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** -1 appends a numeric condition, nonnegative indices edit an existing numeric condition. */
public record PktMachineEditorSmartInterface(String recipeId, int index, String requirementJson,
                                             String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorSmartInterface packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.requirementJson, 32767); buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks); buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorSmartInterface decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorSmartInterface(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorSmartInterface packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.requirementJson.length() > 32767)
                return "智能接口条件数据无效";
            var clean = SmartInterfaceEditor.validate(JsonParser.parseString(packet.requirementJson).getAsJsonObject());
            return PktMachineEditorRequirementAdd.save(player, new PktMachineEditorRequirementAdd(
                    packet.recipeId, "", "", false, 1, 1, packet.newMachine, packet.ticks, packet.displayName), clean, packet.index);
        } catch (RuntimeException invalid) { return "智能接口条件无效：" + invalid.getMessage(); }
    }
}
