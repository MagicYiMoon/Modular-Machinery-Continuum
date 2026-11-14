/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.efabricator.EFabricatorMonitor;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public final class EFabricatorMonitorPackets {
    private EFabricatorMonitorPackets() { }
    public record Update(int container, UUID session, EFabricatorMonitor.Snapshot data) {
        public static void encode(Update p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); p.data.write(b); }
        public static Update decode(FriendlyByteBuf b) { return new Update(b.readVarInt(), b.readUUID(), EFabricatorMonitor.Snapshot.read(b)); }
        public static void handle(Update p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.screen.ScreenEFabricatorController.receive(p))); context.setPacketHandled(true);
        }
    }
    public record Action(int container, UUID session, int action, boolean enabled) {
        public static void encode(Action p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); b.writeVarInt(p.action); b.writeBoolean(p.enabled); }
        public static Action decode(FriendlyByteBuf b) { return new Action(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readBoolean()); }
        public static void handle(Action p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && player.containerMenu instanceof MenuEFabricatorController menu && menu.containerId == p.container)
                    menu.action(player, p.session, p.action, p.enabled);
            }); context.setPacketHandled(true);
        }
    }
}
