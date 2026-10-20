/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** PLAY_TO_CLIENT only; bounded chunks stay below Forge's custom payload limit. */
public record DefinitionSyncPacket(UUID transfer, int totalBytes, int offset, byte[] data) {
    public static final int CHUNK_BYTES = 256 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;

    public DefinitionSyncPacket {
        if (totalBytes < 1 || totalBytes > MAX_SNAPSHOT_BYTES || offset < 0
                || offset >= totalBytes || offset % CHUNK_BYTES != 0
                || data.length != Math.min(CHUNK_BYTES, totalBytes - offset)) {
            throw new IllegalArgumentException("Invalid machine definition chunk");
        }
    }

    public static void encode(DefinitionSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.transfer);
        buffer.writeVarInt(packet.totalBytes);
        buffer.writeVarInt(packet.offset);
        buffer.writeByteArray(packet.data);
    }

    public static DefinitionSyncPacket decode(FriendlyByteBuf buffer) {
        return new DefinitionSyncPacket(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(CHUNK_BYTES));
    }

    public static void handle(DefinitionSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> hellfirepvp.modularmachinery.client.ClientDefinitionSync.receive(packet)));
        context.setPacketHandled(true);
    }
}
