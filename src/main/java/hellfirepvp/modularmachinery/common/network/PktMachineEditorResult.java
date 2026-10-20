/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record PktMachineEditorResult(int containerId, UUID requestId, boolean success, String message) {
    public static void encode(PktMachineEditorResult packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeUUID(packet.requestId);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message, 400);
    }
    public static PktMachineEditorResult decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorResult(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean(), buffer.readUtf(400));
    }
    public EditorSaveState.Result result() { return new EditorSaveState.Result(requestId, success, message); }
    public static void handle(PktMachineEditorResult packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                hellfirepvp.modularmachinery.client.ClientMachineEditorSnapshot.receiveResult(packet)));
        context.setPacketHandled(true);
    }
}
