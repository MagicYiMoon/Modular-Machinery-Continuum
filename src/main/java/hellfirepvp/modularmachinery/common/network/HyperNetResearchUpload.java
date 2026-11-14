/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

/** C2S vanilla custom payloads have a 32 KiB limit. Reassemble only inside the authorized menu. */
public record HyperNetResearchUpload(int window,UUID token,int total,int offset,byte[] bytes){
    public static void send(PktMachineEditorRequest request){
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {PktMachineEditorRequest.encode(request,b);int total=b.readableBytes();if(total>280*1024)throw new IllegalArgumentException("研究保存请求超过上限");
            for(int offset=0;offset<total;offset+=16384){byte[] part=new byte[Math.min(16384,total-offset)];b.readBytes(part);ModularmachineryNetwork.CHANNEL.sendToServer(new HyperNetResearchUpload(request.containerId(),request.requestId(),total,offset,part));}
        }finally{b.release();}
    }
    public static void encode(HyperNetResearchUpload p,FriendlyByteBuf b){b.writeVarInt(p.window);b.writeUUID(p.token);b.writeVarInt(p.total);b.writeVarInt(p.offset);if(p.bytes.length>16384)throw new IllegalArgumentException("Research part too large");b.writeByteArray(p.bytes);}
    public static HyperNetResearchUpload decode(FriendlyByteBuf b){return new HyperNetResearchUpload(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readByteArray(16384));}
    public static PktMachineEditorRequest.Reply process(net.minecraft.server.level.ServerPlayer player,HyperNetResearchUpload p){
        if(!(player.containerMenu instanceof MenuMachineEditor m)||m.containerId!=p.window||!player.hasPermissions(2))return null;
        var bytes=m.acceptResearchUpload(p.token,p.total,p.offset,p.bytes,player.level().getGameTime());if(bytes==null)return null;
        var b=new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try{var request=PktMachineEditorRequest.decode(b);
            if(b.isReadable()||request.containerId()!=p.window||!request.requestId().equals(p.token)||!(request.mutation() instanceof PktMachineEditorResearch||request.mutation() instanceof PktMachineEditorResearchGate))throw new IllegalArgumentException("Invalid research envelope");
            return PktMachineEditorRequest.process(player,request);
        }finally{b.release();}
    }
    public static void handle(HyperNetResearchUpload p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
        var player=c.getSender();if(player==null)return;
        try{var reply=process(player,p);if(reply==null)return;
            if(reply.snapshot()!=null)PktMachineEditorRequest.sendSnapshot(player,p.window,p.token,reply.snapshot());
            ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply.result());
        }catch(Exception e){ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new PktMachineEditorResult(p.window,p.token,false,"研究上传失败，请重新打开编辑器"));}
    });c.setPacketHandled(true);}
}
