/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Private to the player with the matching bus menu open; no client-side upgrade registry is required. */
public record UpgradeBusInfoPacket(int window, BlockPos pos, List<Component> lines) {
    public static final int MAX_LINES = 128;

    public static void encode(UpgradeBusInfoPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.window);
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.lines.size());
        packet.lines.forEach(buffer::writeComponent);
    }

    public static UpgradeBusInfoPacket decode(FriendlyByteBuf buffer) {
        int window = buffer.readVarInt();
        BlockPos pos = buffer.readBlockPos();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) throw new IllegalArgumentException("Invalid upgrade bus line count");
        List<Component> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) lines.add(buffer.readComponent());
        return new UpgradeBusInfoPacket(window, pos, List.copyOf(lines));
    }

    public static void handle(UpgradeBusInfoPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> hellfirepvp.modularmachinery.client.screen.ScreenUpgradeBus.receive(packet)));
        context.setPacketHandled(true);
    }
}
