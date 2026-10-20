/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.IngredientGroupEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Only sent inside the window/revision-bound editor envelope. Index -1 adds; other indices edit. */
public record PktMachineEditorIngredientGroup(String recipeId, int index, String groupJson,
                                              String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorIngredientGroup packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256);
        buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.groupJson, 32767);
        buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks);
        buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorIngredientGroup decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorIngredientGroup(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorIngredientGroup packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.groupJson.length() > 32767)
                return "物品组或催化剂数据无效";
            var group = IngredientGroupEditor.validate(JsonParser.parseString(packet.groupJson).getAsJsonObject());
            var target = new PktMachineEditorRequirementAdd(packet.recipeId, "", "", false, 1, 1,
                    packet.newMachine, packet.ticks, packet.displayName);
            return PktMachineEditorRequirementAdd.save(player, target, group, packet.index);
        } catch (RuntimeException invalid) {
            return "物品组或催化剂无效：" + invalid.getMessage();
        }
    }
}
