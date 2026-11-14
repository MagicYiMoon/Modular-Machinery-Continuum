/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;

/** Real wire/envelope/disk tests. Fixture must run in a disposable project-owned directory. */
public final class HyperNetResearchEditorRegressionChecks {
    private HyperNetResearchEditorRegressionChecks(){}
    public static void run(ServerLevel level)throws Exception {
        var root=HyperNetResearchEditor.directory();Files.createDirectories(root);
        String prefix="editor_"+UUID.randomUUID().toString().replace("-","");var paths=new ArrayList<Path>();
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ResearchEditor")){@Override public boolean hasPermissions(int n){return true;}};
        try {
            HyperNetResearchRegistry.reload(root);player.containerMenu=new MenuMachineEditor(91,player.getInventory(),PktMachineEditorRequest.snapshot());
            String main=PktMachineEditorRequest.snapshot();
            var one=research(prefix+"_a");one.addProperty("display-name","&b基础研究");save(player,"",one,true);paths.add(HyperNetResearchRegistry.loadedCatalog().files().get(one.get("id").getAsString()));
            require(!main.equals(PktMachineEditorRequest.snapshot()),"research revision absent from main snapshot");
            var tagged=one.deepCopy();var icon=new HyperNetResearchIcon("{id:'minecraft:potion',Count:32b,tag:{Potion:'minecraft:strong_healing',CustomPotionColor:3368703,CustomModelData:17,display:{Name:'{\"text\":\"NBT研究图标\"}'},Enchantments:[{id:'minecraft:unbreaking',lvl:2s}]},ForgeCaps:{'mmc:test':{value:7}}}");
            icon.put(tagged);save(player,one.get("id").getAsString(),tagged,true);
            var loadedIcon=HyperNetResearchRegistry.find(one.get("id").getAsString()).previewIcon();
            require(loadedIcon.equals(icon)&&loadedIcon.stack().getCount()==1&&loadedIcon.stack().getTag().getInt("CustomPotionColor")==3368703,"full preview disk reload");
            var fresh=loadedIcon.stack();fresh.getOrCreateTag().putInt("CustomModelData",99);fresh.setCount(8);
            require(loadedIcon.stack().getCount()==1&&loadedIcon.stack().getTag().getInt("CustomModelData")==17,"preview mutation leaked");
            var iconPage=HyperNetResearchEditorPackets.process(player,new HyperNetResearchEditorPackets.Query(91,UUID.randomUUID(),0,prefix,one.get("id").getAsString()));
            require(HyperNetResearchIcon.parse(JsonParser.parseString(iconPage.json()).getAsJsonObject().getAsJsonObject("definition")).equals(icon),"editor full icon detail");
            var summary=HyperNetResearchEditorPackets.process(player,new HyperNetResearchEditorPackets.Query(91,UUID.randomUUID(),0,"",one.get("id").getAsString(),true));
            require(HyperNetResearchIcon.parse(JsonParser.parseString(summary.json()).getAsJsonObject().getAsJsonObject("summary")).equals(icon),"research gate icon summary");
            for(var bad:List.of("{id:'INVALID ITEM'}","{id:'minecraft:potion',tag:3}","{id:'minecraft:potion',ForgeCaps:[]}","{id:'minecraft:potion',Count:0}","{id:'minecraft:paper',tag:{x:'"+"a".repeat(16384)+"'}}","{id:'minecraft:paper',tag:"+"{x:".repeat(65)+"1"+"}".repeat(65)+"}")){
                var brokenIcon=one.deepCopy();brokenIcon.remove("preview-item");brokenIcon.addProperty("preview-stack",bad);save(player,one.get("id").getAsString(),brokenIcon,false);
            }
            require(HyperNetResearchRegistry.find(one.get("id").getAsString()).previewIcon().equals(icon),"invalid icon overwrote valid source");
            var conflicting=tagged.deepCopy();conflicting.addProperty("preview-item","minecraft:paper");save(player,one.get("id").getAsString(),conflicting,false);
            var noItem=HyperNetResearchIcon.ofItem("missing_mod:removed_item");require(noItem.stack().is(net.minecraft.world.item.Items.PAPER)&&noItem.item().equals("missing_mod:removed_item"),"unknown item fallback mutates config");
            var notice=new HyperNetResearchPackets.Complete("icon_test","NBT Icon",icon);var iconWire=new FriendlyByteBuf(Unpooled.buffer());
            try{HyperNetResearchPackets.Complete.encode(notice,iconWire);require(HyperNetResearchPackets.Complete.decode(iconWire).equals(notice),"complete full icon wire");}finally{iconWire.release();}
            var row=new HyperNetResearchTerminal.Row("icon_test","NBT Icon",HyperNetResearchTerminal.State.AVAILABLE,"0","100","",icon);
            var snap=new HyperNetResearchTerminal.Snapshot(1,0,1,"",List.of(row),new HyperNetResearchTerminal.Detail(row,0,"1",List.of(),List.of(),List.of()),"","0",false,false,"",new HyperNetResearchTerminal.Network(0,false,0,0,0,0));
            iconWire=new FriendlyByteBuf(Unpooled.buffer());try{snap.write(iconWire);require(HyperNetResearchTerminal.Snapshot.read(iconWire).equals(snap),"terminal full icon wire");}finally{iconWire.release();}
            var oversizedIcon=new FriendlyByteBuf(Unpooled.buffer());try{oversizedIcon.writeVarInt(HyperNetResearchIcon.MAX_BYTES+1);reject(()->HyperNetResearchIcon.read(oversizedIcon),"oversized icon wire accepted");}finally{oversizedIcon.release();}
            Modularmachinery.LOG.info("[MMC][research-icon-server] PASS: full potion/model/enchantment/caps data disk and editor/summary/terminal/toast wire, size-one defensive copies, invalid/deep/large/conflicting data rejection and missing-item fallback");
            var two=research(prefix+"_b");two.getAsJsonArray("dependencies").add(one.get("id").getAsString());save(player,"",two,true);paths.add(HyperNetResearchRegistry.loadedCatalog().files().get(two.get("id").getAsString()));
            save(player,"",two,false); // duplicate ID
            var missing=research(prefix+"_missing");missing.getAsJsonArray("dependencies").add("nonexistent_research_"+prefix);save(player,"",missing,false);
            var cycle=one.deepCopy();cycle.getAsJsonArray("dependencies").add(two.get("id").getAsString());save(player,one.get("id").getAsString(),cycle,false);
            var rename=one.deepCopy();rename.addProperty("id",prefix+"_renamed");save(player,one.get("id").getAsString(),rename,false);
            require(HyperNetResearchRegistry.find(one.get("id").getAsString()).dependencies().isEmpty(),"rejected graph changed live catalog");
            // A full legal long definition must pass through <=16 KiB chunks, not vanilla's oversized C2S payload.
            var big=research(prefix+"_long");var lines=new JsonArray();for(int i=0;i<64;i++)lines.add("研究说明 "+i+" "+"A".repeat(900));big.add("descriptions",lines);big.add("unlock-descriptions",lines.deepCopy());
            upload(player,"",big);paths.add(HyperNetResearchRegistry.loadedCatalog().files().get(big.get("id").getAsString()));
            require(HyperNetResearchRegistry.find(big.get("id").getAsString()).descriptions().size()==64,"long definition truncated");
            for(int i=0;i<12;i++){var r=research(prefix+"_page"+i);save(player,"",r,true);paths.add(HyperNetResearchRegistry.loadedCatalog().files().get(r.get("id").getAsString()));}
            var query=new HyperNetResearchEditorPackets.Query(91,UUID.randomUUID(),999,prefix,"");
            var reply=HyperNetResearchEditorPackets.process(player,query);var page=JsonParser.parseString(reply.json()).getAsJsonObject();
            require(page.getAsJsonArray("rows").size()<=8&&page.get("page").getAsInt()==page.get("pages").getAsInt()-1&&!page.has("definition"),"bounded page");
            reply=HyperNetResearchEditorPackets.process(player,new HyperNetResearchEditorPackets.Query(91,UUID.randomUUID(),0,prefix,big.get("id").getAsString()));
            var wire=new FriendlyByteBuf(Unpooled.buffer());try{HyperNetResearchEditorPackets.Update.encode(reply,wire);require(HyperNetResearchEditorPackets.Update.decode(wire).json().equals(reply.json()),"full detail codec");}finally{wire.release();}
            require(HyperNetResearchEditorPackets.process(player,new HyperNetResearchEditorPackets.Query(92,UUID.randomUUID(),0,"",""))==null,"query wrong window");
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"DeniedResearch")){@Override public boolean hasPermissions(int n){return false;}};
            denied.containerMenu=new MenuMachineEditor(91,denied.getInventory(),PktMachineEditorRequest.snapshot());
            require(HyperNetResearchEditorPackets.process(denied,query)==null,"query permission");save(denied,"",research(prefix+"_denied"),false);
            var stale=new PktMachineEditorResearch(one.get("id").getAsString(),"stale",one.toString());require(!process(player,stale).result().success(),"stale catalog revision");
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(91,UUID.randomUUID(),"stale",stale)).result().success(),"stale main revision");
            var external=paths.get(0);String before=Files.readString(external);Files.writeString(external,before+"\n ");
            save(player,one.get("id").getAsString(),one,false);require(Files.readString(external).equals(before+"\n "),"external edit overwritten");
            HyperNetResearchRegistry.reload(root);menu(player).setSnapshot(PktMachineEditorRequest.snapshot());
            var broken=root.resolve(prefix+"_broken.json");Files.writeString(broken,"{");
            try{save(player,one.get("id").getAsString(),one,false);require(Files.readString(external).equals(before+"\n "),"malformed sibling caused write");}finally{Files.deleteIfExists(broken);}
            var m=menu(player);var token=UUID.randomUUID();require(m.acceptResearchUpload(token,4,0,new byte[]{1,2},0)==null,"early upload completion");
            reject(()->m.acceptResearchUpload(token,4,3,new byte[]{3},0),"out-of-order chunk accepted");
            reject(()->m.acceptResearchUpload(token,4,2,new byte[]{3,4},201),"expired upload accepted");
            reject(()->m.acceptResearchUpload(token,281*1024,0,new byte[]{1},0),"oversized upload accepted");
            // Unknown response tokens cannot replace the currently requested client page.
            m.beginResearchQuery(token);m.acceptResearchPage(UUID.randomUUID(),"bad");require(m.researchPage()==null,"stale response accepted");m.acceptResearchPage(token,"ok");require(m.researchPage().equals("ok"),"valid response rejected");
            Modularmachinery.LOG.info("[MMC][research-editor] PASS: canonical roundtrip, stable IDs, create/edit, full candidate graph, duplicate/missing/cycle/rename rejection, stale disk and malformed sibling preservation, revisions/permission/window, bounded query/detail codec, 120 KiB chunk upload, expiry/order/size/token guards");
        } finally {for(var path:paths)if(path!=null)Files.deleteIfExists(path);HyperNetResearchRegistry.reload(root);}
    }
    private static JsonObject research(String id){return new HyperNetResearch(id,id,1,BigDecimal.valueOf(100),BigDecimal.ONE,List.of(),List.of(),List.of(),false).json();}
    private static MenuMachineEditor menu(FakePlayer p){return (MenuMachineEditor)p.containerMenu;}
    private static PktMachineEditorRequest.Reply process(FakePlayer p,PktMachineEditorResearch edit){var m=menu(p);var b=new FriendlyByteBuf(Unpooled.buffer());try{PktMachineEditorRequest.encode(new PktMachineEditorRequest(m.containerId,UUID.randomUUID(),EditorSaveState.revision(m.snapshot()),edit),b);return PktMachineEditorRequest.process(p,PktMachineEditorRequest.decode(b));}finally{b.release();}}
    private static void save(FakePlayer p,String old,JsonObject json,boolean expected){var reply=process(p,new PktMachineEditorResearch(old,HyperNetResearchEditor.revision(),json.toString()));require(reply.result().success()==expected,reply.result().message());}
    private static void upload(FakePlayer p,String old,JsonObject json){var m=menu(p);var id=UUID.randomUUID();var b=new FriendlyByteBuf(Unpooled.buffer());try{
        PktMachineEditorRequest.encode(new PktMachineEditorRequest(m.containerId,id,EditorSaveState.revision(m.snapshot()),new PktMachineEditorResearch(old,HyperNetResearchEditor.revision(),json.toString())),b);
        int total=b.readableBytes();require(total>32767,"test upload too short");for(int offset=0;offset<total;offset+=16384){byte[] part=new byte[Math.min(16384,total-offset)];b.readBytes(part);var packet=new HyperNetResearchUpload(m.containerId,id,total,offset,part);var wire=new FriendlyByteBuf(Unpooled.buffer());try{
            HyperNetResearchUpload.encode(packet,wire);require(wire.readableBytes()<32767,"oversized C2S packet");var reply=HyperNetResearchUpload.process(p,HyperNetResearchUpload.decode(wire));
            if(offset+part.length==total)require(reply!=null&&reply.result().success(),"chunk save failed");else require(reply==null,"chunk committed early");
        }finally{wire.release();}}
    }finally{b.release();}}
    private static void reject(Runnable r,String message){try{r.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){}}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Research editor: "+message);}
}
