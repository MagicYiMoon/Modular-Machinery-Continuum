/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in actual wire/save/reload checks, including adapters that fail after publication. */
public final class AdapterTemplateEditorRegressionChecks {
    public static void run(ServerLevel level)throws Exception {
        String id="template_editor_"+UUID.randomUUID().toString().replace("-","");
        Path root=ModDataHolder.INSTANCE.getRecipeDirectory().toPath(),machines=ModDataHolder.INSTANCE.getMachineryDirectory().toPath();
        var files=new ArrayList<Path>();var allowed=new AtomicBoolean(true);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"TemplateEditor")){
            @Override public boolean hasPermissions(int n){return allowed.get();}
        };
        var old=player.containerMenu;var adapterId=new ResourceLocation("mmc_test",id);
        var conflict=new AtomicBoolean(false);Path failed=root.resolve(id+"_failed.adapter.json");files.add(failed);
        RecipeAdapter adapter=new RecipeAdapter(adapterId){
            @Override public Collection<MachineRecipe> createRecipesFor(String machine,List<RecipeModifier> mods,List<Requirement> reqs){
                if(!mods.isEmpty()&&mods.get(0).getModifier()==13F){
                    if(conflict.get())try{Files.writeString(failed,"external author bytes");}catch(Exception e){throw new IllegalStateException(e);}
                    throw new IllegalStateException("intentional template regression adapter failure");
                }
                return List.of(); // A legitimate empty import must remain saveable, with a report.
            }
        };
        RecipeAdapterRegistry.register(adapterId,adapter);
        try{
            for(String name:List.of(id,id+"_other")){
                Path p=machines.resolve(name+".json");files.add(p);
                Files.writeString(p,"{\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\""+(name.equals(id)?",\"selector-tag\":\"sensor\"":"")+"}]}");
            }
            MachineReloadListener.reloadNow();
            Path target=root.resolve(id+".adapter.json");files.add(target);
            var edit=new JsonObject();edit.addProperty("adapter",adapterId.toString());edit.add("modifiers",new JsonArray());edit.add("requirements",new JsonArray());
            save(player,new PktMachineEditorTemplate(id,"",id,edit.toString()),true);
            require(entry(target).get("expanded").getAsBoolean()&&entry(target).get("imported").getAsInt()==0,"empty adapter falsely rejected");
            String created=Files.readString(target);
            save(player,new PktMachineEditorTemplate(id,"",id,edit.toString()),false);
            require(Files.readString(target).equals(created),"create overwrote existing template");

            var source=JsonParser.parseString(created).getAsJsonObject();var owners=new JsonArray();owners.add(id);owners.add(id+"_other");source.add("machine",owners);
            source.addProperty("authorNote","preserve");source.addProperty("priority",17);Files.writeString(target,source.toString());MachineReloadListener.reloadNow();
            edit.addProperty("adapter","minecraft:furnace");
            save(player,update(target,id,edit),true);
            var saved=JsonParser.parseString(Files.readString(target)).getAsJsonObject();
            require(saved.get("machine").equals(owners)&&saved.get("authorNote").getAsString().equals("preserve")&&saved.get("priority").getAsInt()==17,"unrelated fields or machine array lost");
            require(entry(target).get("imported").getAsInt()>0,"furnace import generated nothing");
            String good=Files.readString(target);
            var tagged=BasicRequirementType.ITEM.create("minecraft:diamond",false,1,1F);tagged.addProperty("selector-tag","sensor");edit.getAsJsonArray("requirements").add(tagged);
            save(player,update(target,id,edit),false);require(Files.readString(target).equals(good),"second owner's missing selector ignored");edit.getAsJsonArray("requirements").remove(0);
            var external=saved.deepCopy();external.addProperty("external","keep this edit");Files.writeString(target,external.toString());
            save(player,update(target,id,edit),false);require(JsonParser.parseString(Files.readString(target)).equals(external),"disk edit overwritten");Files.writeString(target,good);MachineReloadListener.reloadNow();
            save(player,new PktMachineEditorTemplate(target.getFileName().toString(),"outdated",id,edit.toString()),false);

            var legacy=saved.deepCopy();legacy.getAsJsonArray("modifiers").add(JsonParser.parseString("{\"target\":\"modularmachinery:duration\",\"io\":\"input\",\"multiplier\":\"1\",\"operation\":\"1\",\"extensionMeta\":\"keep\"}"));
            Files.writeString(target,legacy.toString());MachineReloadListener.reloadNow();
            save(player,update(target,id,legacy),true);
            require(JsonParser.parseString(Files.readString(target)).getAsJsonObject().getAsJsonArray("modifiers").equals(legacy.getAsJsonArray("modifiers")),"unchanged legacy modifier was normalized or rejected");
            Files.writeString(target,good);MachineReloadListener.reloadNow();

            var large=edit.deepCopy();large.addProperty("note","x".repeat(9000));
            save(player,update(target,id,large),true); // Full document >8192 is valid, not silently truncated.
            String stable=Files.readString(target);
            var invalid=new ArrayList<String>();invalid.add(large.toString().replaceFirst("\\{","{\"adapter\":\"minecraft:furnace\","));
            invalid.add(large+" {}");invalid.add("{\"deep\":"+"[".repeat(60)+"0"+"]".repeat(60)+",\"padding\":\""+"x".repeat(9000)+"\"}");
            invalid.add("{\"padding\":\""+"x".repeat(32768)+"\"}");invalid.add("{adapter:'minecraft:furnace'}");invalid.add("[]");
            invalid.add("{\"nodes\":["+"0,".repeat(8200)+"0]}");
            var bad=edit.deepCopy();bad.addProperty("adapter","missing:adapter");invalid.add(bad.toString());
            bad=edit.deepCopy();bad.getAsJsonArray("requirements").add(JsonParser.parseString("{\"type\":\"missing:req\",\"io-type\":\"input\"}"));invalid.add(bad.toString());
            for(String operation:List.of("0.5","2"))invalid.add("{\"adapter\":\"minecraft:furnace\",\"requirements\":[],\"modifiers\":[{\"target\":\"modularmachinery:item\",\"io\":\"input\",\"multiplier\":1,\"operation\":"+operation+"}]}");
            invalid.add("{\"adapter\":\"minecraft:furnace\",\"requirements\":[],\"modifiers\":[{\"target\":\"modularmachinery:item\",\"io\":\"input\",\"multiplier\":1e400,\"operation\":1}]}");
            for(String json:invalid){var p=update(target,id,edit);save(player,new PktMachineEditorTemplate(p.path(),p.digest(),id,json),false);require(Files.readString(target).equals(stable),"invalid request changed file");}

            var mutation=update(target,id,edit);var menu=open(player);var request=new PktMachineEditorRequest(menu.containerId,UUID.randomUUID(),"stale",mutation);
            require(!PktMachineEditorRequest.process(player,request).result().success(),"stale snapshot accepted");
            request=new PktMachineEditorRequest(menu.containerId+1,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),mutation);
            require(!PktMachineEditorRequest.process(player,request).result().success(),"wrong window accepted");
            allowed.set(false);save(player,mutation,false);allowed.set(true);
            menu=open(player);request=new PktMachineEditorRequest(menu.containerId,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),mutation);
            require(PktMachineEditorRequest.process(player,request).result().success(),"valid update failed");
            require(!PktMachineEditorRequest.process(player,request).result().success(),"duplicate template request accepted");

            var failing=edit.deepCopy();failing.addProperty("adapter",adapterId.toString());failing.getAsJsonArray("modifiers").add(JsonParser.parseString("{\"target\":\"modularmachinery:item\",\"io\":\"input\",\"multiplier\":13,\"operation\":1}"));
            String before=Files.readString(target);var reply=save(player,update(target,id,failing),false);
            require(reply.result().message().contains("已撤销")&&Files.readString(target).equals(before)&&entry(target).get("expanded").getAsBoolean(),"failed update not restored/reloaded");
            save(player,new PktMachineEditorTemplate(id+"_failed","",id,failing.toString()),false);require(!Files.exists(failed),"failed create left a file");
            conflict.set(true);reply=save(player,new PktMachineEditorTemplate(id+"_failed","",id,failing.toString()),false);
            require(reply.result().message().contains("外部")&&Files.readString(failed).equals("external author bytes"),"rollback overwrote concurrent edit");
            try(var listing=Files.list(root)){require(listing.noneMatch(p->p.getFileName().toString().startsWith("template-")&&p.toString().endsWith(".mmce.tmp")),"temporary files leaked");}
            Modularmachinery.LOG.info("[MMC][adapter-template-editor] PASS: real wire/envelope, create/update and metadata/multiple owners, empty/furnace expansion, disk/revision/window/permission/dedup guards, >8192 strict JSON, invalid modifier/requirement rejection, failed create/update rollback and concurrent-edit preservation");
        }finally{
            player.containerMenu=old;RecipeAdapterRegistry.unregister(adapterId,adapter);
            for(Path p:files)Files.deleteIfExists(p);MachineReloadListener.reloadNow();
        }
    }
    private static MenuMachineEditor open(FakePlayer player){var menu=new MenuMachineEditor(47,player.getInventory(),PktMachineEditorRequest.snapshot());player.containerMenu=menu;return menu;}
    private static JsonObject entry(Path path){for(var e:MachineReloadListener.getRecipeLoader().editorTemplates())if(e.getAsJsonObject().get("path").getAsString().equals(path.getFileName().toString()))return e.getAsJsonObject();throw new AssertionError("template missing: "+path);}
    private static PktMachineEditorTemplate update(Path path,String owner,JsonObject edit){return new PktMachineEditorTemplate(path.getFileName().toString(),entry(path).get("digest").getAsString(),owner,edit.toString());}
    private static PktMachineEditorRequest.Reply save(FakePlayer player,PktMachineEditorTemplate mutation,boolean success){
        var menu=open(player);var request=new PktMachineEditorRequest(menu.containerId,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),mutation);
        // Oversized input cannot be encoded by the protocol; still exercise authoritative validation directly.
        if(mutation.json().length()<=32767){var buf=new FriendlyByteBuf(Unpooled.buffer());try{PktMachineEditorRequest.encode(request,buf);request=PktMachineEditorRequest.decode(buf);}finally{buf.release();}}
        var reply=PktMachineEditorRequest.process(player,request);require(reply.result().success()==success,reply.result().message());return reply;
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
