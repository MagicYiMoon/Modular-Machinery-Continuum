/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
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
/** Real authoring requests, source preservation, runtime codec and optional dependency guards. */
public final class BloodAuraEditorRegressionChecks {
    public static void run(ServerLevel level) throws Exception {
        var root=ModDataHolder.INSTANCE.getRecipeDirectory().toPath();String id="blood_aura_editor_"+UUID.randomUUID().toString().replace("-","");
        var machine=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id+".json");
        var files=new ArrayList<Path>();
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"BloodAuraEditor")){
            @Override public boolean hasPermissions(int n){return true;}
        };var old=player.containerMenu;
        try {
            Files.writeString(machine,"{\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\",\"selector-tag\":\"magic\"}]}");MachineReloadListener.reloadNow();
            var menu=new MenuMachineEditor(42,player.getInventory(),PktMachineEditorRequest.snapshot());player.containerMenu=menu;
            boolean blood=net.minecraftforge.fml.ModList.get().isLoaded("bloodmagic"),aura=net.minecraftforge.fml.ModList.get().isLoaded("naturesaura");
            var snap=JsonParser.parseString(menu.snapshot()).getAsJsonObject();
            require(snap.get("bloodMagicEditorAvailable").getAsBoolean()==blood && snap.get("auraEditorAvailable").getAsBoolean()==aura,"independent availability");
            var kinds=snap.getAsJsonArray("editorAuraTypes");require(aura ? !kinds.isEmpty():kinds.isEmpty(),"actual aura type list");
            String auraKind=aura?kinds.get(0).getAsString():"naturesaura:overworld";
            var examples=List.of(ResourceAmountEditor.life(false,1000,false),ResourceAmountEditor.will(false,1.25,"raw",0,100),ResourceAmountEditor.aura(false,100,auraKind,-300,500));
            for(int family=0;family<3;family++)for(boolean output:List.of(false,true)) {
                String recipe=id+"_"+family+(output?"_out":"_in");var file=root.resolve(recipe+".json");files.add(file);
                var initial=examples.get(family).deepCopy();initial.addProperty("io-type",output?"output":"input");
                boolean installed=family==2?aura:blood;
                save(player,new PktMachineEditorResourceAmount(recipe,-1,initial.toString(),id,20,"资源测试"),installed);
                if(!installed){require(!Files.exists(file),"absent dependency wrote file");continue;}
                var source=read(file);source.addProperty("authorNote","recipe");var original=req(source,0);
                original.addProperty("selector-tag","magic");original.addProperty("triggerTime",3);original.addProperty("triggerRepeatable",true);
                original.addProperty("authorNote","keep");if(family>0)original.addProperty("perTick",false);
                if(family!=1)original.addProperty("parallelizeUnaffected",true);
                if(family==1){original.addProperty("min",200d);original.addProperty("max",100d);} // independent bounds, min may exceed max.
                if(family==2){original.addProperty("min-aura",200);original.addProperty("max-aura",-100);} // independent signed bounds.
                Files.writeString(file,source.toString());reload(menu);
                var edited=family==0?ResourceAmountEditor.life(output,123,true):family==1?ResourceAmountEditor.will(output,2.125,"vengeful",7.5,9.25):ResourceAmountEditor.aura(output,123,auraKind,-200,-100);
                var expected=source.deepCopy();expected.getAsJsonArray("requirements").set(0,ResourceAmountEditor.merge(original,edited));
                save(player,edit(recipe,0,edited),true);require(read(file).equals(expected),"only edited fields change; inactive bound and metadata preserved");
                var runtime=MachineReloadListener.getRecipeLoader().getRecipe(recipe);var codec=RecipeSnapshotCodec.encode(runtime,Map.of());
                var normalized=ResourceAmountEditor.validate(req(codec,0));
                require(normalized.get("amount").equals(edited.get("amount")),"amount runtime");
                var decoded=RecipeSnapshotCodec.decode(codec);require(decoded.requirements().get(0).getClass()==runtime.requirements().get(0).getClass(),"runtime snapshot type/rate");
                if(family==0)require(normalized.get("perTick").getAsBoolean(),"LP rate runtime");
                String bound=family==1?output?"max":"min":output?"max-aura":"min-aura";
                if(family>0)require(normalized.get(bound).equals(edited.get(bound)),"active bound runtime");
                for(int policy:List.of(RequirementBatchEditor.FOLLOW,RequirementBatchEditor.DEFAULT,RequirementBatchEditor.EXEMPT)) {
                    String before=Files.readString(file);save(player,new PktMachineEditorRequirementBatch(recipe,0,policy),family!=1);
                    if(family==1)require(Files.readString(file).equals(before),"will batch unsupported");
                    else require(((Requirement.Parallelizable)MachineReloadListener.getRecipeLoader().getRecipe(recipe).requirements().get(0)).isParallelizeUnaffected()==(policy==RequirementBatchEditor.EXEMPT),"runtime batch policy");
                }
                if(family==0){save(player,edit(recipe,0,ResourceAmountEditor.life(output,Integer.MAX_VALUE,false)),true);save(player,edit(recipe,0,ResourceAmountEditor.life(output,0,true)),true);}
                if(family==1)for(String type:ResourceAmountEditor.WILL_TYPES)save(player,edit(recipe,0,ResourceAmountEditor.will(output,0,type,200,100)),true);
                if(family==2)for(var type:kinds)save(player,edit(recipe,0,ResourceAmountEditor.aura(output,0,type.getAsString(),Integer.MIN_VALUE,Integer.MAX_VALUE)),true);
                save(player,edit(recipe,-1,edited),true);require(read(file).getAsJsonArray("requirements").size()==2,"append");
                String before=Files.readString(file);
                for(String raw:List.of("null","[]","\"4\"","true","-1","1e400")) {var bad=edited.deepCopy();bad.add("amount",JsonParser.parseString(raw));save(player,edit(recipe,0,bad),false);}
                var bad=edited.deepCopy();bad.addProperty("io-type",output?"input":"output");save(player,edit(recipe,0,bad),false);
                bad=edited.deepCopy();bad.addProperty("chance",1);save(player,edit(recipe,0,bad),false);
                bad=edited.deepCopy();bad.addProperty("perTick",family==0?"true":"bad");save(player,edit(recipe,0,bad),false);
                if(family>0){bad=edited.deepCopy();bad.addProperty("perTick",true);save(player,edit(recipe,0,bad),false);}
                if(family==1){bad=edited.deepCopy();bad.addProperty("will-type","unknown");save(player,edit(recipe,0,bad),false);bad=edited.deepCopy();bad.addProperty(bound,-1);save(player,edit(recipe,0,bad),false);}
                if(family==2){bad=edited.deepCopy();bad.addProperty("aura-type","naturesaura:unregistered_editor_type");save(player,edit(recipe,0,bad),false);bad=edited.deepCopy();bad.addProperty(bound,1.5);save(player,edit(recipe,0,bad),false);}
                for(int index:List.of(-2,512,99))save(player,edit(recipe,index,edited),false);
                save(player,edit(recipe,0,family==0?ResourceAmountEditor.will(output,1,"raw",0,100):ResourceAmountEditor.life(output,1,false)),false);
                require(Files.readString(file).equals(before),"invalid edits changed file");
                var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"DeniedMagic")){@Override public boolean hasPermissions(int n){return false;}};
                denied.containerMenu=new MenuMachineEditor(42,denied.getInventory(),menu.snapshot());save(denied,edit(recipe,0,edited),false);
                require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(43,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(recipe,0,edited))).result().success(),"window guard");
                require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(42,UUID.randomUUID(),"stale",edit(recipe,0,edited))).result().success(),"revision guard");
                var external=read(file);external.addProperty("external",true);Files.writeString(file,external.toString());save(player,edit(recipe,0,edited),false);save(player,edit(recipe,-1,edited),false);require(read(file).equals(external),"external changes lost");reload(menu);
                String readonly=MachineReloadListener.getRecipeLoader().getRecipeNames().stream().filter(n->!MachineReloadListener.getRecipeLoader().isSourceEditable(n)).findFirst().orElseThrow();save(player,edit(readonly,-1,edited),false);
                var request=new PktMachineEditorRequest(42,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(recipe,0,edited));
                require(PktMachineEditorRequest.process(player,request).result().success(),"first request");require(!PktMachineEditorRequest.process(player,request).result().success(),"duplicate request");
            }
            Modularmachinery.LOG.info("[MMC][blood-aura-editor] PASS: bloodmagic={} aura={}; independent availability/types and absence rejection; installed families real wire/create/append/edit/reload/runtime codecs, LP rate/int bounds, five decimal will types/no batch, signed aura bounds/type registry, metadata/inactive bounds, generic batch policy, malformed/type/direction/rate/probability/permission/window/revision/dedupe/external/readonly guards",blood,aura);
        } finally {player.containerMenu=old;for(var file:files)Files.deleteIfExists(file);Files.deleteIfExists(machine);MachineReloadListener.reloadNow();}
    }
    private static PktMachineEditorResourceAmount edit(String id,int index,JsonObject json){return new PktMachineEditorResourceAmount(id,index,json.toString(),"",20,"");}
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static JsonObject req(JsonObject json,int index){return json.getAsJsonArray("requirements").get(index).getAsJsonObject();}
    private static void reload(MenuMachineEditor menu){MachineReloadListener.reloadNow();menu.setSnapshot(PktMachineEditorRequest.snapshot());}
    private static void save(FakePlayer player,Object action,boolean expected){
        var menu=(MenuMachineEditor)player.containerMenu;var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu.containerId,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),action),buf);
            var reply=PktMachineEditorRequest.process(player,PktMachineEditorRequest.decode(buf));require(reply.result().success()==expected,reply.result().message());
            if(expected)require(reply.snapshot()!=null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()),"reload before acknowledgement");
        }finally{buf.release();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError("Blood/Aura editor: "+message);}
}
