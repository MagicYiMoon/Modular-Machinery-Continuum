/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.ItemRequirementEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Ordinary item drafts, including inventory NBT, inside the revision-bound editor envelope. */
public record PktMachineEditorItem(String recipeId, int index, String requirementJson,
                                             String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorItem packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.requirementJson, 32767); buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks); buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorItem decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorItem(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorItem packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.requirementJson.length() > 32767)
                return "物品需求数据无效";
            var clean = ItemRequirementEditor.validate(JsonParser.parseString(packet.requirementJson).getAsJsonObject());
            return PktMachineEditorRequirementAdd.save(player, new PktMachineEditorRequirementAdd(
                    packet.recipeId, "", "", false, 1, 1, packet.newMachine, packet.ticks, packet.displayName), clean, packet.index);
        } catch (RuntimeException invalid) { return "物品需求无效：" + invalid.getMessage(); }
    }
}
