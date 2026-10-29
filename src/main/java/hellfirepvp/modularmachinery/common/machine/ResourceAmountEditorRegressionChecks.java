/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Actual authoring envelope/file/reload tests with and without each optional resource provider. */
public final class ResourceAmountEditorRegressionChecks {
    private ResourceAmountEditorRegressionChecks() {}
    public static void run(ServerLevel level) throws Exception {
        String id="resource_editor_"+UUID.randomUUID().toString().replace("-","");
        var dir=ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var manaFile=dir.resolve(id+"_mana.json"); var emberFile=dir.resolve(id+"_ember.json");
        var machineFile=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id+".json");
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ResourceEditor")) {
            @Override public boolean hasPermissions(int n){return true;}
        };
        var old=player.containerMenu;
        try {
            Files.writeString(machineFile,"{\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\",\"selector-tag\":\"resources\"}]}");
            MachineReloadListener.reloadNow();
            var menu=new MenuMachineEditor(42,player.getInventory(),PktMachineEditorRequest.snapshot());player.containerMenu=menu;
            boolean mana=net.minecraftforge.fml.ModList.get().isLoaded("botania"),ember=net.minecraftforge.fml.ModList.get().isLoaded("embers");
            var snapshot=JsonParser.parseString(menu.snapshot()).getAsJsonObject();
            require(snapshot.get("manaEditorAvailable").getAsBoolean()==mana && snapshot.get("emberEditorAvailable").getAsBoolean()==ember,"independent server availability");
            for(boolean isMana:List.of(true,false)) {
                boolean installed=isMana?mana:ember; String recipe=id+(isMana?"_mana":"_ember");var file=isMana?manaFile:emberFile;
                var clean=isMana?ResourceAmountEditor.mana(false,1000,false):ResourceAmountEditor.ember(12.5);
                save(player,new PktMachineEditorResourceAmount(recipe,-1,clean.toString(),id,80,"资源测试"),installed);
                if(!installed){require(!Files.exists(file),"absent dependency wrote file");continue;}
                checkLoaded(recipe,0,clean);
                var source=read(file);source.addProperty("authorNote","recipe metadata");var req=req(source,0);
                req.addProperty("selector-tag","resources");req.addProperty("parallelizeUnaffected",true);req.addProperty("triggerTime",7);
                req.addProperty("triggerRepeatable",true);req.addProperty("ignoreOutputCheck",false);req.addProperty("authorNote","requirement metadata");
                if(!isMana){req.addProperty("type","gugu-utils:ember");req.addProperty("perTick",false);}
                Files.writeString(file,source.toString());reload(menu);
                var edit=isMana?ResourceAmountEditor.mana(false,250,true):ResourceAmountEditor.ember(123.125);
                String before=Files.readString(file);var merged=ResourceAmountEditor.merge(req,edit);
                require(Files.readString(file).equals(before),"cancel/detached draft touched disk");
                save(player,edit(recipe,0,edit),true);source.getAsJsonArray("requirements").set(0,merged);
                require(read(file).equals(source),"only amount/rate fields changed; full metadata and Ember alias survive");checkLoaded(recipe,0,edit);
                for (int policy : List.of(RequirementBatchEditor.FOLLOW, RequirementBatchEditor.DEFAULT, RequirementBatchEditor.EXEMPT)) {
                    var previous=read(file); var expected=previous.deepCopy();
                    expected.getAsJsonArray("requirements").set(0,RequirementBatchEditor.merge(req(previous,0),policy));
                    save(player,new PktMachineEditorRequirementBatch(recipe,0,policy),true);
                    require(read(file).equals(expected),"batch panel only changes its policy field");
                    var loaded=MachineReloadListener.getRecipeLoader().getRecipe(recipe).requirements().get(0);
                    require(RequirementBatchEditor.supports(loaded) && ((hellfirepvp.modularmachinery.common.crafting.Requirement.Parallelizable)loaded).isParallelizeUnaffected()==(policy==RequirementBatchEditor.EXEMPT),"actual runtime batch policy");
                }
                if(isMana) {
                    save(player,edit(recipe,0,ResourceAmountEditor.mana(false,Integer.MAX_VALUE,false)),true);checkLoaded(recipe,0,ResourceAmountEditor.mana(false,Integer.MAX_VALUE,false));
                    save(player,edit(recipe,-1,ResourceAmountEditor.mana(true,10,false)),true);checkLoaded(recipe,1,ResourceAmountEditor.mana(true,10,false));
                    save(player,edit(recipe,1,ResourceAmountEditor.mana(true,0,true)),true);checkLoaded(recipe,1,ResourceAmountEditor.mana(true,0,true));
                    save(player,edit(recipe,0,ResourceAmountEditor.mana(false,0,true)),true);
                    // Missing perTick defaults to false and can be explicitly enabled/disabled.
                    source=read(file);req(source,0).remove("perTick");Files.writeString(file,source.toString());reload(menu);
                    save(player,edit(recipe,0,ResourceAmountEditor.mana(false,1,true)),true);checkLoaded(recipe,0,ResourceAmountEditor.mana(false,1,true));
                } else {
                    save(player,edit(recipe,0,ResourceAmountEditor.ember(0)),true);checkLoaded(recipe,0,ResourceAmountEditor.ember(0));
                    save(player,edit(recipe,0,ResourceAmountEditor.ember(1e100)),true);checkLoaded(recipe,0,ResourceAmountEditor.ember(1e100));
                    save(player,edit(recipe,0,ResourceAmountEditor.ember(0.125)),true);checkLoaded(recipe,0,ResourceAmountEditor.ember(0.125));
                }
                before=Files.readString(file);
                for(String raw:List.of("null","[]","\"1\"","true","-1","1e400")) {
                    var bad=clean.deepCopy();bad.add(isMana?"amount":"ember",JsonParser.parseString(raw));save(player,edit(recipe,0,bad),false);
                }
                for(String key:List.of("perTick","parallelizeUnaffected"))for(String raw:List.of("null","1","\"true\"")) {
                    var bad=clean.deepCopy();bad.add(key,JsonParser.parseString(raw));save(player,edit(recipe,0,bad),false);
                }
                for(String key:List.of("type","io-type",isMana?"amount":"ember")){var bad=clean.deepCopy();bad.remove(key);save(player,edit(recipe,0,bad),false);}
                var bad=clean.deepCopy();bad.addProperty("chance",1);save(player,edit(recipe,0,bad),false);
                bad=clean.deepCopy();bad.addProperty("io-type","output");save(player,edit(recipe,0,bad),false);
                bad=clean.deepCopy();if(isMana)bad.addProperty("amount",1.5);else bad.addProperty("perTick",true);save(player,edit(recipe,0,bad),false);
                save(player,edit(recipe,0,isMana?ResourceAmountEditor.ember(1):ResourceAmountEditor.mana(false,1,false)),false);
                for(int i:List.of(-2,512,99))save(player,edit(recipe,i,clean),false);
                for(String payload:List.of("{}","[]","{"))save(player,new PktMachineEditorResourceAmount(recipe,0,payload,"",20,""),false);
                var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ResourceDenied")){
                    @Override public boolean hasPermissions(int n){return false;}
                };denied.containerMenu=new MenuMachineEditor(42,denied.getInventory(),menu.snapshot());save(denied,edit(recipe,0,clean),false);
                require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(43,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(recipe,0,clean))).result().success(),"wrong window");
                require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(42,UUID.randomUUID(),"stale",edit(recipe,0,clean))).result().success(),"stale revision");
                require(Files.readString(file).equals(before),"invalid and unauthorized edits touched disk");
                source=read(file);source.addProperty("externalEdit",true);Files.writeString(file,source.toString());
                save(player,edit(recipe,0,clean),false);save(player,edit(recipe,-1,clean),false);require(read(file).equals(source),"stale disk guards edit and append");reload(menu);
                String readonly=MachineReloadListener.getRecipeLoader().getRecipeNames().stream().filter(n->!MachineReloadListener.getRecipeLoader().isSourceEditable(n)).findFirst().orElseThrow();
                save(player,edit(readonly,-1,clean),false);
                var request=new PktMachineEditorRequest(42,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(recipe,0,clean));
                require(PktMachineEditorRequest.process(player,request).result().success(),"initial save");require(!PktMachineEditorRequest.process(player,request).result().success(),"duplicate save");
            }
            if (mana || ember)
                Modularmachinery.LOG.info("[MMC][resource-amount-editor] PASS: mana={} ember={}; installed providers tested with actual bounded wire/create/append/edit/reload/runtime snapshots, integer mana IO/rate/zero/int-max, decimal Ember/zero/scientific/legacy alias, metadata/cancel and malformed/IO/rate/family/stale/readonly/permission/window/revision/dedupe rejection; absent providers reject without files",mana,ember);
            else Modularmachinery.LOG.info("[MMC][resource-amount-editor] PASS: mana=false ember=false; no optional class linkage, independent feature flags and actual bounded wire/server absence rejection without creating recipe files");
        } finally {player.containerMenu=old;Files.deleteIfExists(manaFile);Files.deleteIfExists(emberFile);Files.deleteIfExists(machineFile);MachineReloadListener.reloadNow();}
    }
    private static PktMachineEditorResourceAmount edit(String id,int index,JsonObject j){return new PktMachineEditorResourceAmount(id,index,j.toString(),"",20,"");}
    private static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static JsonObject req(JsonObject j,int i){return j.getAsJsonArray("requirements").get(i).getAsJsonObject();}
    private static void reload(MenuMachineEditor m){MachineReloadListener.reloadNow();m.setSnapshot(PktMachineEditorRequest.snapshot());}
    private static void checkLoaded(String id,int i,JsonObject expected){
        var recipe=MachineReloadListener.getRecipeLoader().getRecipe(id);var json=RecipeSnapshotCodec.encode(recipe,Map.of());
        require(ResourceAmountEditor.validate(req(json,i)).equals(expected),"loaded resource semantics");
        var decoded=RecipeSnapshotCodec.decode(json);require(decoded.requirements().get(i).getClass()==recipe.requirements().get(i).getClass(),"rate survives snapshot decode");
    }
    private static void save(FakePlayer p,Object edit,boolean success){
        var m=(MenuMachineEditor)p.containerMenu;var b=new FriendlyByteBuf(Unpooled.buffer());
        try{PktMachineEditorRequest.encode(new PktMachineEditorRequest(m.containerId,UUID.randomUUID(),EditorSaveState.revision(m.snapshot()),edit),b);
            var reply=PktMachineEditorRequest.process(p,PktMachineEditorRequest.decode(b));require(reply.result().success()==success,"save result: "+reply.result().message());
            if(success)require(reply.snapshot()!=null && m.snapshot().equals(PktMachineEditorRequest.snapshot()),"server reload precedes confirmation");
        }finally{b.release();}
    }
    private static void require(boolean c,String m){if(!c)throw new AssertionError("Resource editor: "+m);}
}
