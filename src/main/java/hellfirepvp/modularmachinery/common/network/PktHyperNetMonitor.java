/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.hypernet.HyperNetMonitor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** One bounded page, addressed to a particular open monitor session. */
public record PktHyperNetMonitor(int container,UUID session,HyperNetMonitor.Snapshot data) {
    public static void encode(PktHyperNetMonitor p,FriendlyByteBuf b){b.writeVarInt(p.container);b.writeUUID(p.session);p.data.write(b);}
    public static PktHyperNetMonitor decode(FriendlyByteBuf b){return new PktHyperNetMonitor(b.readVarInt(),b.readUUID(),HyperNetMonitor.Snapshot.read(b));}
    public static void handle(PktHyperNetMonitor p,Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->hellfirepvp.modularmachinery.client.screen.ScreenHyperNetMonitor.receive(p)));context.setPacketHandled(true);
    }
}
