/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.FluidTickEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Authenticated continuous fluid operation; -1 appends, other indices edit a rate of the same direction. */
public record PktMachineEditorFluidTick(String recipeId, int index, String requirementJson,
                                       String newMachine, int ticks, String displayName) {
    public static void encode(PktMachineEditorFluidTick packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index);
        buffer.writeUtf(packet.requirementJson, 32767); buffer.writeUtf(packet.newMachine, 256);
        buffer.writeVarInt(packet.ticks); buffer.writeUtf(packet.displayName, 200);
    }
    public static PktMachineEditorFluidTick decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorFluidTick(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(32767),
                buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(200));
    }
    static String apply(ServerPlayer player, PktMachineEditorFluidTick packet) {
        try {
            if (packet.index < -1 || packet.index >= 512 || packet.requirementJson.length() > 32767) return "持续流体数据无效";
            var clean = FluidTickEditor.validate(JsonParser.parseString(packet.requirementJson).getAsJsonObject());
            return PktMachineEditorRequirementAdd.save(player, new PktMachineEditorRequirementAdd(
                    packet.recipeId, "", "", false, 1, 1, packet.newMachine, packet.ticks, packet.displayName), clean, packet.index);
        } catch (RuntimeException invalid) { return "持续流体需求无效：" + invalid.getMessage(); }
    }
}
