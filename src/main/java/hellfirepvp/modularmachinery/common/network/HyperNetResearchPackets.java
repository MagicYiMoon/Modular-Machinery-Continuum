/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchTerminal;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetResearch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public final class HyperNetResearchPackets {
    private HyperNetResearchPackets(){}
    public record Update(int container,UUID session,HyperNetResearchTerminal.Snapshot data){
        public static void encode(Update p,FriendlyByteBuf b){b.writeVarInt(p.container);b.writeUUID(p.session);p.data.write(b);}
        public static Update decode(FriendlyByteBuf b){return new Update(b.readVarInt(),b.readUUID(),HyperNetResearchTerminal.Snapshot.read(b));}
        public static void handle(Update p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->hellfirepvp.modularmachinery.client.screen.ScreenHyperNetResearch.receive(p)));c.setPacketHandled(true);}
    }
    /** Self-contained: research definitions need not be installed on the receiving client. */
    public record Complete(String id,String name,hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchIcon previewIcon){
        public Complete(String id,String name,String item){this(id,name,hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchIcon.ofItem(item));}
        public String previewItem(){return previewIcon.item();}
        public Complete {
            hellfirepvp.modularmachinery.common.hypernet.HyperNetResearch.checkedId(id);
            if(name==null||name.length()>256||previewIcon==null)
                throw new IllegalArgumentException("Invalid research completion notice");
        }
        public static void encode(Complete p,FriendlyByteBuf b){b.writeUtf(p.id,256);b.writeUtf(p.name,256);p.previewIcon.write(b);}
        public static Complete decode(FriendlyByteBuf b){return new Complete(b.readUtf(256),b.readUtf(256),hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchIcon.read(b));}
        public static void handle(Complete p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->hellfirepvp.modularmachinery.client.screen.ResearchCompleteToast.receive(p)));c.setPacketHandled(true);}
    }
    /** 0=query/select/page, 1=start selected, 2=cancel the observed task, 3=creative OP start with full points. Identity comes from the connection. */
    public record Request(int container,UUID session,long token,int action,int page,String query,String selected){
        public Request {
            if(token<1||action<0||action>3||page<0||page>455||query==null||query.length()>128||selected==null||selected.length()>256
                    ||query.codePoints().anyMatch(Character::isISOControl)||selected.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid research request");
        }
        public static void encode(Request p,FriendlyByteBuf b){b.writeVarInt(p.container);b.writeUUID(p.session);b.writeVarLong(p.token);b.writeVarInt(p.action);b.writeVarInt(p.page);b.writeUtf(p.query,128);b.writeUtf(p.selected,256);}
        public static Request decode(FriendlyByteBuf b){return new Request(b.readVarInt(),b.readUUID(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readUtf(128),b.readUtf(256));}
        public static void handle(Request p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{var player=c.getSender();
            if(player!=null&&player.containerMenu instanceof MenuHyperNetResearch menu&&menu.containerId==p.container)menu.request(player,p);
        });c.setPacketHandled(true);}
    }
}
