/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementResearch;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
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

/** Actual files, save envelope, snapshots, template drafts and oversized Unicode ID uploads. */
public final class ResearchGateEditorRegressionChecks {
    private ResearchGateEditorRegressionChecks(){}
    public static void run(ServerLevel level)throws Exception{
        String id="research_gate_"+UUID.randomUUID().toString().replace("-","");var files=new ArrayList<Path>();
        var research=HyperNetResearchEditor.directory();Files.createDirectories(research);
        var machine=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id+".json");files.add(machine);
        var recipe=ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id+".json");files.add(recipe);
        var adapterFile=recipe.resolveSibling(id+".adapter.json");files.add(adapterFile);
        var fresh=recipe.resolveSibling(id+"_new.json");files.add(fresh);var big=recipe.resolveSibling(id+"_big.json");files.add(big);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"GateEditor")){@Override public boolean hasPermissions(int n){return true;}};
        String a=id+"_a",c=id+"_b";Path second=research.resolve(id+"_b.json");files.add(second);
        try{
            var first=research.resolve(id+"_a.json");files.add(first);Files.writeString(first,definition(a));Files.writeString(second,definition(c));
            Files.writeString(machine,"{\"registryname\":\""+id+"\",\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\"}]}");
            Files.writeString(recipe,"{\"machine\":\""+id+"\",\"registryName\":\""+id+"\",\"recipeTime\":20,\"authorNote\":\"keep\",\"requirements\":[{\"type\":\"modularmachinery:item\",\"io-type\":\"input\",\"item\":\"minecraft:stone\",\"amount\":1}]}");
            MachineReloadListener.reloadNow();player.containerMenu=new MenuMachineEditor(94,player.getInventory(),PktMachineEditorRequest.snapshot());
            save(player,new PktMachineEditorResearchGate(id+"_new",-1,List.of(a),id,40,"研究配方"),true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id+"_new").requirements().get(0) instanceof RequirementResearch,"first requirement recipe missing");
            save(player,new PktMachineEditorResearchGate(id,-1,List.of(a,c),"",20,""),true);
            var loaded=MachineReloadListener.getRecipeLoader().getRecipe(id);var decoded=RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded,Map.of()));
            require(((RequirementResearch)decoded.requirements().get(1)).research().equals(List.of(a,c)),"client gate snapshot lost IDs");
            var disk=read(recipe);require(disk.get("authorNote").getAsString().equals("keep")&&disk.getAsJsonArray("requirements").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:stone"),"unrelated file fields changed");
            // Persist a real furnace adapter and check all expanded recipes inherit the gate.
            var adapter=new JsonObject();adapter.addProperty("adapter","minecraft:furnace");adapter.add("modifiers",new JsonArray());
            var gateRequirements=new JsonArray();gateRequirements.add(ResearchGateEditor.create(List.of(a,c)));adapter.add("requirements",gateRequirements);
            AdapterTemplateEditor.save(id,"",id,adapter);
            require(MachineReloadListener.getRecipeLoader().getByMachine().get(id).stream().filter(r->!r.registryName().equals(id)&&!r.registryName().equals(id+"_new")).anyMatch(r->r.requirements().stream().anyMatch(q->q instanceof RequirementResearch gate&&gate.research().equals(List.of(a,c)))),"adapter expansion lost research gate");
            menu(player).setSnapshot(PktMachineEditorRequest.snapshot());
            String before=Files.readString(recipe);
            for(var ids:List.of(List.<String>of(),List.of(a,a),List.of(id+"_unknown"),Collections.nCopies(65,a)))save(player,new PktMachineEditorResearchGate(id,1,ids,"",20,""),false);
            save(player,new PktMachineEditorResearchGate(id,0,List.of(a),"",20,""),false);save(player,new PktMachineEditorResearchGate(id,512,List.of(a),"",20,""),false);
            require(Files.readString(recipe).equals(before),"invalid gate edit wrote file");
            for(String key:List.of("chance","parallelizeUnaffected","triggerTime","selector-tag","perTick")){var invalid=ResearchGateEditor.create(List.of(a));invalid.addProperty(key,1);reject(()->ResearchGateEditor.validate(invalid),"unsupported gate option accepted");}
            var output=ResearchGateEditor.create(List.of(a));output.addProperty("io-type","output");reject(()->ResearchGateEditor.validate(output),"output gate accepted");
            var template=new JsonArray();template.add(ResearchGateEditor.create(List.of(a)));
            var edited=TemplateRequirementDraft.apply(TemplateRequirementDraft.decorate(template),new PktMachineEditorResearchGate("",0,List.of(a,c),"",20,""),new JsonObject());
            var decorated=TemplateRequirementDraft.decorate(edited).get(0).getAsJsonObject();require(ResearchGateEditor.ids(decorated).equals(List.of(a,c))&&!decorated.get("editableBatch").getAsBoolean()&&!decorated.get("editableSelector").getAsBoolean()&&!decorated.get("editableTrigger").getAsBoolean(),"template gate options incorrect");
            var external=read(recipe);external.addProperty("authorNote","external change");Files.writeString(recipe,external.toString());
            save(player,new PktMachineEditorResearchGate(id,1,List.of(a),"",20,""),false);require(read(recipe).equals(external),"disk change overwritten");
            reload(player);save(player,new PktMachineEditorResearchGate(id,1,List.of(c),"",20,""),true);
            Files.delete(second);HyperNetResearchRegistry.reload(research);menu(player).setSnapshot(PktMachineEditorRequest.snapshot());
            var adapterEntry=MachineReloadListener.getRecipeLoader().editorTemplates().asList().stream().map(JsonElement::getAsJsonObject).filter(j->j.get("path").getAsString().equals(id+".adapter.json")).findFirst().orElseThrow();
            AdapterTemplateEditor.save(id+".adapter.json",adapterEntry.get("digest").getAsString(),id,adapter);
            var badAdapter=adapter.deepCopy();badAdapter.getAsJsonArray("requirements").add(ResearchGateEditor.create(List.of(id+"_never_existed")));
            var currentEntry=MachineReloadListener.getRecipeLoader().editorTemplates().asList().stream().map(JsonElement::getAsJsonObject).filter(j->j.get("path").getAsString().equals(id+".adapter.json")).findFirst().orElseThrow();
            String adapterBefore=Files.readString(adapterFile);try{AdapterTemplateEditor.save(id+".adapter.json",currentEntry.get("digest").getAsString(),id,badAdapter);throw new AssertionError("new unknown template gate accepted");}catch(IllegalArgumentException expected){}
            require(Files.readString(adapterFile).equals(adapterBefore),"invalid template changed file");menu(player).setSnapshot(PktMachineEditorRequest.snapshot());
            save(player,new PktMachineEditorResearchGate(id,1,List.of(c,a),"",20,""),true); // retain pre-existing unknown ID
            save(player,new PktMachineEditorResearchGate(id,-1,List.of(c),"",20,""),false); // but cannot introduce one
            var response=HyperNetResearchEditorPackets.process(player,new HyperNetResearchEditorPackets.Query(94,UUID.randomUUID(),0,"",c,true));
            var summary=JsonParser.parseString(response.json()).getAsJsonObject();require(!summary.getAsJsonObject("summary").get("exists").getAsBoolean()&&!summary.has("definition"),"unknown summary not marked");
            var wire=new FriendlyByteBuf(Unpooled.buffer());try{var query=new HyperNetResearchEditorPackets.Query(94,UUID.randomUUID(),0,"",a,true);HyperNetResearchEditorPackets.Query.encode(query,wire);require(HyperNetResearchEditorPackets.Query.decode(wire).equals(query),"summary query codec");}finally{wire.release();}
            save(player,new PktMachineEditorResearchGate(id,1,List.of(a),"",20,""),true);
            // Maximal legal IDs exceed the vanilla C2S size. The real fragmented path must still save all 64.
            var longIds=new ArrayList<String>();for(int i=0;i<64;i++){String name=id+"_"+"研".repeat(180)+i;longIds.add(name);var p=research.resolve(id+"_long_"+i+".json");files.add(p);Files.writeString(p,definition(name));}
            reload(player);upload(player,new PktMachineEditorResearchGate(id+"_big",-1,longIds,id,20,"长 ID 门槛"));
            require(((RequirementResearch)MachineReloadListener.getRecipeLoader().getRecipe(id+"_big").requirements().get(0)).research().equals(longIds),"long IDs truncated");
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"DeniedGate")){@Override public boolean hasPermissions(int n){return false;}};denied.containerMenu=new MenuMachineEditor(94,denied.getInventory(),menu(player).snapshot());save(denied,new PktMachineEditorResearchGate(id,1,List.of(a),"",20,""),false);
            var request=new PktMachineEditorRequest(94,UUID.randomUUID(),EditorSaveState.revision(menu(player).snapshot()),new PktMachineEditorResearchGate(id,1,List.of(a),"",20,""));
            require(PktMachineEditorRequest.process(player,request).result().success()&&!PktMachineEditorRequest.process(player,request).result().success(),"duplicate gate request accepted");
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(95,UUID.randomUUID(),request.revision(),request.mutation())).result().success(),"wrong window accepted");
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(94,UUID.randomUUID(),"stale",request.mutation())).result().success(),"stale revision accepted");
            Modularmachinery.LOG.info("[MMC][research-gate-editor] PASS: actual create/append/replace, file and snapshot preservation, strict input-only semantics, known-ID validation and unknown retention/removal, summary codec, template draft and actual furnace expansion/unknown retention, 64 Unicode IDs through bounded chunks, invalid/permission/window/revision/dedupe/disk guards");
        }finally{for(var p:files)Files.deleteIfExists(p);MachineReloadListener.reloadNow();}
    }
    private static String definition(String id){return new HyperNetResearch(id,id,1,BigDecimal.TEN,BigDecimal.ONE,List.of(),List.of(),List.of(),false).json().toString();}
    private static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static MenuMachineEditor menu(FakePlayer p){return (MenuMachineEditor)p.containerMenu;}
    private static void reload(FakePlayer p){MachineReloadListener.reloadNow();menu(p).setSnapshot(PktMachineEditorRequest.snapshot());}
    private static void save(FakePlayer p,PktMachineEditorResearchGate edit,boolean success){
        // Size 65 is intentionally invalid before any wire allocation.
        if(edit.research().size()>64){reject(()->ResearchGateEditor.create(edit.research()),"65 research entries accepted");return;}
        var b=new FriendlyByteBuf(Unpooled.buffer());try{PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu(p).containerId,UUID.randomUUID(),EditorSaveState.revision(menu(p).snapshot()),edit),b);var reply=PktMachineEditorRequest.process(p,PktMachineEditorRequest.decode(b));require(reply.result().success()==success,reply.result().message());}finally{b.release();}
    }
    private static void upload(FakePlayer p,PktMachineEditorResearchGate edit){var id=UUID.randomUUID();var b=new FriendlyByteBuf(Unpooled.buffer());try{
        PktMachineEditorRequest.encode(new PktMachineEditorRequest(94,id,EditorSaveState.revision(menu(p).snapshot()),edit),b);int total=b.readableBytes();require(total>32767,"Unicode upload not large enough");
        for(int offset=0;offset<total;offset+=16384){byte[] part=new byte[Math.min(16384,total-offset)];b.readBytes(part);var wire=new FriendlyByteBuf(Unpooled.buffer());try{HyperNetResearchUpload.encode(new HyperNetResearchUpload(94,id,total,offset,part),wire);require(wire.readableBytes()<32767,"chunk exceeds vanilla limit");var reply=HyperNetResearchUpload.process(p,HyperNetResearchUpload.decode(wire));if(offset+part.length==total)require(reply!=null&&reply.result().success(),reply==null?"no upload commit":reply.result().message());else require(reply==null,"early upload commit");}finally{wire.release();}}
    }finally{b.release();}}
    private static void reject(Runnable r,String message){try{r.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){}}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Research gate editor: "+message);}
}
