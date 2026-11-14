/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorPatternSearch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public final class EFabricatorPatternPackets {
    private EFabricatorPatternPackets() { }
    public record Update(int container, UUID session, EFabricatorPatternView.Snapshot data) {
        public static void encode(Update p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); p.data.write(b); }
        public static Update decode(FriendlyByteBuf b) { return new Update(b.readVarInt(), b.readUUID(), EFabricatorPatternView.Snapshot.read(b)); }
        public static void handle(Update p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get(); c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.screen.ScreenEFabricatorPatternSearch.receive(p))); c.setPacketHandled(true);
        }
    }
    public record Query(int container, UUID session, EFabricatorPatternView.Query query) {
        public static void encode(Query p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); p.query.write(b); }
        public static Query decode(FriendlyByteBuf b) { return new Query(b.readVarInt(), b.readUUID(), EFabricatorPatternView.Query.read(b)); }
        public static void handle(Query p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get(); c.enqueueWork(() -> { var player = c.getSender();
                if (player != null && player.containerMenu instanceof MenuEFabricatorPatternSearch menu && menu.containerId == p.container) menu.query(player, p.session, p.query);
            }); c.setPacketHandled(true);
        }
    }
    public record Action(int container, UUID session, int action, long token, int row) {
        public static void encode(Action p, FriendlyByteBuf b) { b.writeVarInt(p.container); b.writeUUID(p.session); b.writeVarInt(p.action); b.writeVarLong(p.token); b.writeVarInt(p.row); }
        public static Action decode(FriendlyByteBuf b) { return new Action(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarLong(), b.readVarInt()); }
        public static void handle(Action p, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get(); c.enqueueWork(() -> { var player = c.getSender();
                if (player != null && player.containerMenu instanceof MenuEFabricatorPatternSearch menu && menu.containerId == p.container) menu.action(player, p.session, p.action, p.token, p.row);
            }); c.setPacketHandled(true);
        }
    }
}
