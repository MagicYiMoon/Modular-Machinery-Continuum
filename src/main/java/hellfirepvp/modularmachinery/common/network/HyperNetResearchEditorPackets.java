/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.HyperNetResearchEditor;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** One page and at most one full definition; no unbounded research catalog in the main menu snapshot. */
public final class HyperNetResearchEditorPackets {
    private HyperNetResearchEditorPackets(){}
    public record Query(int window,UUID token,int page,String search,String selected,boolean summaryOnly) {
        public Query(int window,UUID token,int page,String search,String selected){this(window,token,page,search,selected,false);}
        public static void encode(Query p,FriendlyByteBuf b){b.writeVarInt(p.window);b.writeUUID(p.token);b.writeVarInt(p.page);b.writeUtf(p.search,128);b.writeUtf(p.selected,256);b.writeBoolean(p.summaryOnly);}
        public static Query decode(FriendlyByteBuf b){return new Query(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readUtf(128),b.readUtf(256),b.readBoolean());}
        public static void handle(Query p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{var player=c.getSender();if(player==null)return;var reply=process(player,p);if(reply!=null)ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),reply);});c.setPacketHandled(true);}
    }
    public record Update(int window,UUID token,String json) {
        public static void encode(Update p,FriendlyByteBuf b){byte[] bytes=p.json.getBytes(StandardCharsets.UTF_8);if(bytes.length>512*1024)throw new IllegalArgumentException("Research page too large");b.writeVarInt(p.window);b.writeUUID(p.token);b.writeByteArray(bytes);}
        public static Update decode(FriendlyByteBuf b){return new Update(b.readVarInt(),b.readUUID(),new String(b.readByteArray(512*1024),StandardCharsets.UTF_8));}
        public static void handle(Update p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->receive(p)));c.setPacketHandled(true);}
    }
    private static void receive(Update p){var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof MenuMachineEditor m&&m.containerId==p.window)m.acceptResearchPage(p.token,p.json);}
    public static Update process(ServerPlayer player,Query p){
        if(!(player.containerMenu instanceof MenuMachineEditor m)||m.containerId!=p.window||!player.hasPermissions(2))return null;
        var j=new JsonObject();j.addProperty("revision",HyperNetResearchEditor.revision());var catalog=HyperNetResearchRegistry.loadedCatalog();
        if(catalog==null){j.addProperty("error","研究目录加载失败，请修复文件并重载");return new Update(p.window,p.token,j.toString());}
        if(p.summaryOnly){var r=catalog.definitions().get(p.selected);var row=new JsonObject();row.addProperty("id",p.selected);row.addProperty("exists",r!=null);row.addProperty("name",r==null?p.selected:r.displayName());row.addProperty("icon",r==null?"minecraft:barrier":r.previewItem());if(r!=null)r.previewIcon().put(row);j.add("summary",row);return new Update(p.window,p.token,j.toString());}
        var terms=p.search.toLowerCase(Locale.ROOT).trim().split("\\s+");
        var entries=catalog.definitions().values().stream().filter(r->{String text=(r.id()+" "+r.displayName()).toLowerCase(Locale.ROOT);return Arrays.stream(terms).allMatch(text::contains);}).sorted(Comparator.comparing(HyperNetResearch::id)).toList();
        int pages=Math.max(1,(entries.size()+7)/8),page=Math.max(0,Math.min(p.page,pages-1));
        j.addProperty("page",page);j.addProperty("pages",pages);j.addProperty("total",entries.size());var rows=new JsonArray();
        for(int i=page*8;i<Math.min(entries.size(),page*8+8);i++){var r=entries.get(i);var row=new JsonObject();row.addProperty("id",r.id());row.addProperty("name",r.displayName());row.addProperty("icon",r.previewItem());r.previewIcon().put(row);rows.add(row);}j.add("rows",rows);
        if(!p.selected.isEmpty()){var selected=catalog.definitions().get(p.selected);if(selected==null)j.addProperty("error","研究已不存在，请刷新列表");else j.add("definition",selected.json());}
        return new Update(p.window,p.token,j.toString());
    }
}
