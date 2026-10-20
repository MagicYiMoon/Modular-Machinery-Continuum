/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.DurabilityEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Sent only in the authenticated editor envelope. Index -1 adds; other indices edit. */
public record PktMachineEditorDurability(String recipeId, int index, String requirementJson,
                                        String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorDurability packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.requirementJson, 32767); buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks); buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorDurability decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorDurability(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorDurability packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.requirementJson.length() > 32767) return "耐久需求数据无效";
            var requirement = DurabilityEditor.validate(JsonParser.parseString(packet.requirementJson).getAsJsonObject());
            var target = new PktMachineEditorRequirementAdd(packet.recipeId, "", "", false, 1, 1,
                    packet.newMachine, packet.ticks, packet.displayName);
            return PktMachineEditorRequirementAdd.save(player, target, requirement, packet.index);
        } catch (RuntimeException invalid) { return "耐久需求无效：" + invalid.getMessage(); }
    }
}
