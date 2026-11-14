/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.ecalculator.ECalculatorMonitor;
import hellfirepvp.modularmachinery.common.menu.MenuECalculatorController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public final class ECalculatorMonitorPackets {
    private ECalculatorMonitorPackets() { }
    /** Appended message: the existing menu snapshot/cancel payloads and discriminator IDs stay unchanged. */
    public record Statistics(int container, UUID session, hellfirepvp.modularmachinery.common.ecalculator.ECalculatorStatistics.Page data) {
        public static void encode(Statistics p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); p.data.write(b); }
        public static Statistics decode(FriendlyByteBuf b) { return new Statistics(b.readVarInt(), b.readUUID(), hellfirepvp.modularmachinery.common.ecalculator.ECalculatorStatistics.Page.read(b)); }
        public static void handle(Statistics p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.screen.ScreenECalculatorController.receive(p)));
            context.setPacketHandled(true);
        }
    }
    public record Update(int container, UUID session, ECalculatorMonitor.Snapshot data) {
        public static void encode(Update p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); p.data.write(b); }
        public static Update decode(FriendlyByteBuf b) { return new Update(b.readVarInt(), b.readUUID(), ECalculatorMonitor.Snapshot.read(b)); }
        public static void handle(Update p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.screen.ScreenECalculatorController.receive(p)));
            context.setPacketHandled(true);
        }
    }
    public record Cancel(int container, UUID session, UUID task) {
        public static void encode(Cancel p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); b.writeUUID(p.task); }
        public static Cancel decode(FriendlyByteBuf b) { return new Cancel(b.readVarInt(), b.readUUID(), b.readUUID()); }
        public static void handle(Cancel p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && player.containerMenu instanceof MenuECalculatorController menu && menu.containerId == p.container)
                    menu.cancel(player, p.session, p.task);
            });
            context.setPacketHandled(true);
        }
    }
}
