/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.FuelEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Authenticated editor operation; -1 adds a fuel budget, other indices replace one. */
public record PktMachineEditorFuel(String recipeId, int index, String requirementJson,
                                   String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorFuel packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.requirementJson, 32767); buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks); buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorFuel decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorFuel(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorFuel packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.requirementJson.length() > 32767) return "燃料需求数据无效";
            var requirement = FuelEditor.validate(JsonParser.parseString(packet.requirementJson).getAsJsonObject());
            return PktMachineEditorRequirementAdd.save(player, new PktMachineEditorRequirementAdd(
                    packet.recipeId, "", "", false, 1, 1, packet.newMachine, packet.ticks, packet.displayName), requirement, packet.index);
        } catch (RuntimeException invalid) { return "燃料需求无效：" + invalid.getMessage(); }
    }
}
