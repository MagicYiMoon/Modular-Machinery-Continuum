/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Real envelope, files, reload and client snapshot, not a second implementation of the form. */
public final class HyperNetEditorRegressionChecks {
    private HyperNetEditorRegressionChecks(){}
    public static void run(ServerLevel level)throws Exception {
        String id="hypernet_editor_"+UUID.randomUUID().toString().replace("-","");
        var machineFile=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id+".json");
        var recipeFile=ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id+".json");
        var p=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"HyperNetEditor")){
            @Override public boolean hasPermissions(int n){return true;}
        };var original=p.containerMenu;
        try {
            Files.writeString(machineFile,"{\"registryname\":\""+id+"\",\"authorNote\":\"keep me\",\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\"}]}");
            MachineReloadListener.reloadNow();p.containerMenu=new MenuMachineEditor(61,p.getInventory(),PktMachineEditorRequest.snapshot());
            var config=HyperNetSettingsEditor.profile(MachineReloadListener.getLoader(),id);
            config.add("display-info",JsonParser.parseString("{\"tier\":\"&bT3\",\"tooltips\":[\"&6作者说明\",\"能力由参数决定\"]}"));
            config.add("hypernet",JsonParser.parseString("{\"role\":\"center\",\"carrying\":5000,\"max-connections\":8,\"connection-card\":\"minecraft:paper\",\"circuit\":{\"capacity\":200,\"min-wear\":0,\"max-wear\":2,\"wear-chance\":0.2}}"));
            config.getAsJsonObject("hypernet").addProperty("fair-scheduling",true);
            save(p,new PktMachineEditorHyperNet(id,config.toString()),true);
            var machine=MachineReloadListener.getLoader().findByName(id);
            require(machine.getHyperNetDefinition().fairScheduling()&&machine.getHyperNetDefinition().carrying()==5000&&machine.getDisplayInfo().tier().equals("&bT3"),"saved fields");
            var copy=MachineLoader.fromSnapshot(MachineReloadListener.getLoader().snapshot()).findByName(id);
            require(copy.getDisplayInfo().equals(machine.getDisplayInfo())&&copy.getHyperNetDefinition().equals(machine.getHyperNetDefinition()),"client machine snapshot");
            var lines=new ArrayList<net.minecraft.network.chat.Component>();HyperNetTooltips.append(copy,lines);
            require(lines.stream().anyMatch(c->c.getString().contains("作者说明")),"tooltip text");
            require(read(machineFile).get("authorNote").getAsString().equals("keep me")&&read(machineFile).getAsJsonArray("parts").size()==1,"unrelated fields preserved");
            var processor=JsonParser.parseString("{\"role\":\"processor\",\"modules\":[{\"upgrade\":\"sample_cpu\",\"kind\":\"cpu\",\"capacity\":4,\"energy\":12,\"display-info\":{\"tier\":\"T2\",\"tooltips\":[\"cpu note\"]}}],\"thermal\":{\"overheat-threshold\":100,\"heat-distribution\":2}}" );
            config.add("hypernet",processor);save(p,new PktMachineEditorHyperNet(id,config.toString()),true);
            require(MachineReloadListener.getLoader().findByName(id).getHyperNetDefinition().modules().get(0).displayInfo().tier().equals("T2"),"module info");
            for(String type:List.of("computation","hypernet_cooling","hypernet_circuit_repair")){
                var j=new JsonObject();j.addProperty("type","modularmachinery:"+type);j.addProperty("io-type","input");j.addProperty("amount",type.equals("computation")?1.25:12);
                if(!type.equals("computation"))j.addProperty("amount",12);
                if(type.equals("computation"))j.addProperty("triggerFailure",true);
                save(p,new PktMachineEditorResourceAmount(id,-1,j.toString(),Files.exists(recipeFile)?"":id,80,"算力编辑测试"),true);
                var source=read(recipeFile);int index=source.getAsJsonArray("requirements").size()-1;
                source.getAsJsonArray("requirements").get(index).getAsJsonObject().addProperty("parallelizeUnaffected",true);
                Files.writeString(recipeFile,source.toString());MachineReloadListener.reloadNow();menu(p).setSnapshot(PktMachineEditorRequest.snapshot());
                j.addProperty("amount",type.equals("computation")?2.5:20);if(!type.equals("computation"))j.addProperty("amount",20);
                save(p,new PktMachineEditorResourceAmount(id,index,j.toString(),"",80,""),true);
                require(read(recipeFile).getAsJsonArray("requirements").get(index).getAsJsonObject().get("parallelizeUnaffected").getAsBoolean(),"batch retained");
                var recipe=MachineReloadListener.getRecipeLoader().getRecipe(id);require(RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe,Map.of())).requirements().size()==index+1,"recipe snapshot");
                var bad=j.deepCopy();bad.addProperty("io-type","output");save(p,new PktMachineEditorResourceAmount(id,index,bad.toString(),"",80,""),false);
                bad=j.deepCopy();bad.addProperty("amount",-1);save(p,new PktMachineEditorResourceAmount(id,index,bad.toString(),"",80,""),false);
                if(!type.equals("computation")){bad=j.deepCopy();bad.addProperty("amount",1.5);save(p,new PktMachineEditorResourceAmount(id,index,bad.toString(),"",80,""),false);}
            }
            for(var value:List.of("\"true\"","1","null")) {
                var invalid=com.google.gson.JsonParser.parseString("{\"role\":\"center\",\"carrying\":1,\"max-connections\":1,\"fair-scheduling\":"+value+"}");
                try { HyperNetDefinition.parse(invalid);throw new AssertionError("non-boolean fairness accepted"); } catch(IllegalArgumentException expected) { }
            }
            try { HyperNetDefinition.parse(JsonParser.parseString("{\"role\":\"node\",\"fair-scheduling\":false}"));throw new AssertionError("node fairness accepted"); } catch(IllegalArgumentException expected) { }
            var retainedConfig=config.deepCopy();
            for(String role:List.of("database","research_station")){
                var n=new JsonObject();n.addProperty("role",role);n.addProperty(role.equals("database")?"storage-capacity":"max-tech-level",role.equals("database")?128:3.5);
                config.add("hypernet",n);save(p,new PktMachineEditorHyperNet(id,config.toString()),true);
                var def=MachineReloadListener.getLoader().findByName(id).getHyperNetDefinition();require(role.equals("database")?def.storageCapacity()==128:def.maxTechLevel()==3.5,"research role settings");
            }
            config.add("hypernet",JsonParser.parseString("{\"role\":\"terminal\"}"));save(p,new PktMachineEditorHyperNet(id,config.toString()),false);
            config=retainedConfig;save(p,new PktMachineEditorHyperNet(id,config.toString()),true);
            String before=Files.readString(machineFile);
            var bad=config.deepCopy();bad.getAsJsonObject("display-info").addProperty("tier","x".repeat(65));save(p,new PktMachineEditorHyperNet(id,bad.toString()),false);
            bad=config.deepCopy();bad.getAsJsonObject("hypernet").getAsJsonObject("thermal").addProperty("overheat-threshold",0);save(p,new PktMachineEditorHyperNet(id,bad.toString()),false);
            bad=config.deepCopy();bad.addProperty("parts","attempt");save(p,new PktMachineEditorHyperNet(id,bad.toString()),false);
            require(!PktMachineEditorRequest.process(p,new PktMachineEditorRequest(61,UUID.randomUUID(),"stale",new PktMachineEditorHyperNet(id,config.toString()))).result().success(),"stale revision");
            require(!PktMachineEditorRequest.process(p,new PktMachineEditorRequest(62,UUID.randomUUID(),EditorSaveState.revision(menu(p).snapshot()),new PktMachineEditorHyperNet(id,config.toString()))).result().success(),"window guard");
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"HyperNetDenied")){@Override public boolean hasPermissions(int n){return false;}};
            denied.containerMenu=new MenuMachineEditor(61,denied.getInventory(),menu(p).snapshot());save(denied,new PktMachineEditorHyperNet(id,config.toString()),false);
            require(Files.readString(machineFile).equals(before),"invalid write");
            var changed=read(machineFile);changed.getAsJsonObject("display-info").addProperty("tier","external");Files.writeString(machineFile,changed.toString());
            save(p,new PktMachineEditorHyperNet(id,config.toString()),false);require(read(machineFile).equals(changed),"external changes overwritten");
            MachineReloadListener.reloadNow();menu(p).setSnapshot(PktMachineEditorRequest.snapshot());
            config.add("hypernet",JsonNull.INSTANCE);save(p,new PktMachineEditorHyperNet(id,config.toString()),true);
            require(MachineReloadListener.getLoader().findByName(id).getHyperNetDefinition()==null&&!read(machineFile).has("hypernet"),"disable saved");
            var request=new PktMachineEditorRequest(61,UUID.randomUUID(),EditorSaveState.revision(menu(p).snapshot()),new PktMachineEditorHyperNet(id,config.toString()));
            require(PktMachineEditorRequest.process(p,request).result().success()&&!PktMachineEditorRequest.process(p,request).result().success(),"dedupe");
            Modularmachinery.LOG.info("[MMC][hypernet-editor] PASS: bounded wire, center/processor/disable, tier/module info, file preservation, client snapshots, computation/cooling/repair create/append/edit, batch, invalid/permission/window/revision/dedupe/stale-disk guards");
        }finally{p.containerMenu=original;Files.deleteIfExists(machineFile);Files.deleteIfExists(recipeFile);MachineReloadListener.reloadNow();}
    }
    private static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static MenuMachineEditor menu(FakePlayer p){return (MenuMachineEditor)p.containerMenu;}
    private static void save(FakePlayer p,Object mutation,boolean expected){var m=menu(p);var b=new FriendlyByteBuf(Unpooled.buffer());try{
        PktMachineEditorRequest.encode(new PktMachineEditorRequest(m.containerId,UUID.randomUUID(),EditorSaveState.revision(m.snapshot()),mutation),b);
        var result=PktMachineEditorRequest.process(p,PktMachineEditorRequest.decode(b));require(result.result().success()==expected,"save: "+result.result().message());
        if(expected)require(result.snapshot()!=null&&m.snapshot().equals(PktMachineEditorRequest.snapshot()),"ack before reload");
    }finally{b.release();}}
    private static void require(boolean v,String m){if(!v)throw new AssertionError("HyperNet editor: "+m);}
}
