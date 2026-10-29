/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorAction;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Real envelope, source JSON, reload, isolation and ordinary/factory execution checks. */
public final class RecipeCopyRegressionChecks {
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String machine = "copy_probe_" + UUID.randomUUID().toString().replace("-", ""), id = machine + "_source";
        Path root = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        Path file = root.resolve(id + ".json"), machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(machine + ".json");
        Path otherFile = machineFile.resolveSibling(machine + "_other.json");
        var outputs = new ArrayList<Path>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "CopyProbe")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            Files.writeString(machineFile, """
                {"parts":[{"x":1,"elements":"modularmachinery:inputbus"},
                {"x":2,"elements":"modularmachinery:outputbus"},{"x":3,"elements":"modularmachinery:energyinputhatch"}]}
                """);
            Files.writeString(otherFile, Files.readString(machineFile));
            // Registry IDs are obtained from actual blocks; no legacy alias can mask an invalid fixture.
            var machineJson = read(machineFile); var parts = machineJson.getAsJsonArray("parts");
            var blocks = List.of(RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.energyInputHatch.get());
            for (int i=0;i<3;i++) parts.get(i).getAsJsonObject().addProperty("elements",net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blocks.get(i)).toString());
            Files.writeString(machineFile,machineJson.toString()); Files.writeString(otherFile,machineJson.toString());
            var source = JsonParser.parseString("""
                {"recipeTime":2,"displayName":"&6复制测试","priority":17,"parallelized":false,"loadJEI":false,
                "maxThreads":1,"threadName":"","cancelIfPerTickFails":true,"tooltips":["preserved tooltip"],
                "authorMeta":{"nested":[1,{"note":"retain unknown fields"}]},
                "requirements":[{"type":"modularmachinery:item","io-type":"input","item":"minecraft:coal","amount":1,"chance":1,"nbt":{"quality":">=3"}},
                {"type":"modularmachinery:item","io-type":"output","item":"minecraft:diamond","amount":2,"chance":1,"nbt":{"quality":"7"},"nbt-display":{"quality":"99"}},
                {"type":"modularmachinery:energy","io-type":"input","energyPerTick":7}]}
                """).getAsJsonObject();
            source.addProperty("machine",machine); source.addProperty("registryname",id);
            Files.writeString(file,source.toString()); MachineReloadListener.reloadNow();
            require(MachineReloadListener.getRecipeLoader().getRecipe(id)!=null,"fixture recipe loads");
            var menu = new MenuMachineEditor(41,player.getInventory(),PktMachineEditorRequest.snapshot()); player.containerMenu=menu;
            String target=id+"_copy"; Path targetFile=root.resolve(target+".json"); outputs.add(targetFile);
            var request=request(menu,copy(machine,id,target));
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {PktMachineEditorRequest.encode(request,buffer); request=PktMachineEditorRequest.decode(buffer);} finally {buffer.release();}
            var reply=PktMachineEditorRequest.process(player,request);
            require(reply.result().success() && reply.snapshot()!=null && reply.snapshot().equals(PktMachineEditorRequest.snapshot()),"copy envelope/reload/snapshot: "+reply.result().message());
            var expected=source.deepCopy();expected.remove("registryname");expected.addProperty("registryName",target);
            require(read(targetFile).equals(expected) && read(file).equals(source),"complete JSON preserved and original unchanged");
            require(!PktMachineEditorRequest.process(player,request).result().success(),"duplicate request accepted");
            save(player,copy(machine,id,target),false);
            for(String bad:List.of("../escape","sub/name","UPPER","trailing.","con","nul.json","com1.more","", "a".repeat(122)))
                save(player,copy(machine,id,bad),false);
            Path occupied=root.resolve(id+"_occupied.json"); outputs.add(occupied);Files.writeString(occupied,"occupied bytes");
            save(player,copy(machine,id,id+"_occupied"),false);require(Files.readString(occupied).equals("occupied bytes"),"existing file overwritten");Files.delete(occupied);
            outputs.add(root.resolve(id+"_cross.json"));save(player,copy(machine+"_other",id,id+"_cross"),true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id+"_cross").machine().equals(machine+"_other") && read(file).equals(source),"cross-machine copy ownership/source");
            checkExecution(level,origin.offset(0,0,32),machine+"_other",id+"_cross");
            var profile=RecipeCopyEditor.profile(MachineReloadListener.getLoader().findByName(machine+"_other"));
            for(String body:List.of("{\"selector-tag\":\"missing\"}","{\"type\":\"modularmachinery:interface_number_input\",\"interface\":\"missing\"}")) {
                var missing=source.deepCopy();missing.getAsJsonArray("requirements").add(JsonParser.parseString(body));
                boolean rejected=false;try{RecipeCopyEditor.validateReferences(missing,profile,id+"_new");}catch(IllegalArgumentException invalid){rejected=true;}require(rejected,"missing cross-machine reference accepted");
            }
            var pinned=source.deepCopy();pinned.addProperty("threadName","core");boolean rejected=false;
            try{RecipeCopyEditor.validateReferences(pinned,profile,id+"_new");}catch(IllegalArgumentException invalid){rejected=true;}require(rejected,"missing core thread accepted");
            var allowed=new JsonArray();allowed.add("another_recipe");profile.getAsJsonObject("coreThreads").add("core",allowed);rejected=false;
            try{RecipeCopyEditor.validateReferences(pinned,profile,id+"_new");}catch(IllegalArgumentException invalid){rejected=true;}require(rejected,"core whitelist accepted");
            allowed.add(id+"_new");RecipeCopyEditor.validateReferences(pinned,profile,id+"_new");
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"CopyDenied")) { @Override public boolean hasPermissions(int n){return false;} };
            denied.containerMenu=new MenuMachineEditor(41,denied.getInventory(),menu.snapshot());save(denied,copy(machine,id,id+"_denied"),false);
            for(var bad:List.of(new PktMachineEditorRequest(42,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),copy(machine,id,id+"_window")),
                    new PktMachineEditorRequest(41,UUID.randomUUID(),EditorSaveState.revision("stale"),copy(machine,id,id+"_stale"))))
                require(!PktMachineEditorRequest.process(player,bad).result().success(),"stale request accepted");
            Files.writeString(file,"{");save(player,copy(machine,id,id+"_malformed"),false);
            var external=source.deepCopy();external.addProperty("authorMeta","external edit");Files.writeString(file,external.toString());
            save(player,copy(machine,id,id+"_external"),false);require(read(file).equals(external),"external source was changed");
            Files.writeString(file,source.toString());reload(menu);
            checkExecution(level,origin,machine,target);
            save(player,new PktMachineEditorRecipeEdit(target,"independent",3),true);
            require(read(file).equals(source) && MachineReloadListener.getRecipeLoader().getRecipe(id).recipeTime()==2,"editing copy changed original");
            save(player,new PktMachineEditorAction(PktMachineEditorAction.DELETE_RECIPE,"",target,-1,"",false),true);
            require(!Files.exists(targetFile) && MachineReloadListener.getRecipeLoader().getRecipe(id)!=null,"deleting copy changed original");
            var multi=source.deepCopy();var owners=new JsonArray();owners.add(machine);owners.add(machine+"_other");multi.add("machine",owners);
            Files.writeString(file,multi.toString());reload(menu);
            String multiTarget=id+"_selected";outputs.add(root.resolve(multiTarget+".json"));save(player,copy(machine+"_other",id+"_sub_1",multiTarget),true);
            require(read(root.resolve(multiTarget+".json")).get("machine").getAsString().equals(machine+"_other")
                    && read(file).equals(multi) && MachineReloadListener.getRecipeLoader().getRecipe(id+"_sub_0")!=null
                    && MachineReloadListener.getRecipeLoader().getRecipe(id+"_sub_1")!=null,"multi-machine source duplicated as whole array");
            var template=new JsonObject();template.addProperty("machine",machine);template.addProperty("adapter","minecraft:furnace");
            Path adapter=root.resolve(id+".adapter.json");outputs.add(adapter);Files.writeString(adapter,template.toString());reload(menu);
            var loader=MachineReloadListener.getRecipeLoader();String generated=loader.getByMachine().get(machine).stream().filter(r->!loader.isSourceEditable(r.registryName())).findFirst().orElseThrow().registryName();
            save(player,copy(machine,generated,id+"_generated"),false);
            int scriptChecks=0;
            for(String recipeId:loader.getRecipeNames())if(String.valueOf(loader.getSourcePath(recipeId)).startsWith("CraftTweaker:")) {
                save(player,copy(loader.getRecipe(recipeId).machine(),recipeId,id+"_script"),false);scriptChecks++;break;
            }
            for(String suffix:List.of("_denied","_window","_stale","_malformed","_external","_generated","_script"))
                require(!Files.exists(root.resolve(id+suffix+".json")),"rejected copy created a file: "+suffix);
            var detached=RecipeCopyEditor.duplicate(source,machine,id+"_detached");detached.getAsJsonObject("authorMeta").addProperty("leak",true);
            require(!source.getAsJsonObject("authorMeta").has("leak"),"copy shares mutable metadata");
            Modularmachinery.LOG.info("[MMC][recipe-copy] PASS: real wire/file/reload/snapshot, full source/NBT/policy preservation, ordinary+factory output, independent edit/delete, selected multi-owner copy, ID/file/permission/stale/external/read-only guards, duplicate requests; script sources checked={}",scriptChecks);
        } finally {
            player.containerMenu=oldMenu; for(var p:outputs)Files.deleteIfExists(p);Files.deleteIfExists(file);Files.deleteIfExists(machineFile);Files.deleteIfExists(otherFile);MachineReloadListener.reloadNow();
        }
    }
    private static void checkExecution(ServerLevel level,BlockPos origin,String machineId,String recipeId) {
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var loader=MachineReloadListener.getRecipeLoader();var list=loader.getByMachine().get(machineId);
        var recipe=loader.getRecipe(recipeId);loader.getByMachine().put(machineId,List.of(recipe));
        try {
            for(boolean factory:List.of(false,true)) {
                var pos=origin.offset(factory?16:0,0,0);level.getChunkAt(pos);level.getChunkAt(pos.east(3));
                Block[] blocks={factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get(),RegistryBlocks.itemInputBus.get(),RegistryBlocks.itemOutputBus.get(),RegistryBlocks.energyInputHatch.get()};
                for(int i=0;i<4;i++){var p=pos.east(i);saved.put(p,level.getBlockState(p));level.setBlock(p,blocks[i].defaultBlockState(),3);}
                var tile=(TileController)level.getBlockEntity(pos);tile.setMachineName(machineId);
                var machine=MachineReloadListener.getLoader().findByName(machineId);
                require(MachineFormation.tryForm(level,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"copy fixture formation");tile.setFormed(true);
                var input=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.east())).getInventory();
                var output=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.east(2))).getInventory();
                var energy=((TileEnergyInputHatch)level.getBlockEntity(pos.east(3))).getEnergyStorage();
                var coal=new ItemStack(Items.COAL);coal.getOrCreateTag().putInt("quality",3);input.setStackInSlot(0,coal);energy.receiveEnergy(14,false);
                for(int i=0;i<160 && output.getStackInSlot(0).isEmpty();i++) {
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                    if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)tile);else MachineTicker.tick(level,pos,tile);
                }
                var result=output.getStackInSlot(0);require(result.is(Items.DIAMOND) && result.getCount()==2 && result.hasTag() && result.getTag().getInt("quality")==7
                        && input.getStackInSlot(0).isEmpty() && energy.getEnergyStored()==0,"copied ordinary/factory job or NBT/FE conservation");
            }
        } finally {saved.forEach((p,state)->level.setBlock(p,state,3));loader.getByMachine().put(machineId,list);}
    }
    private static PktMachineEditorAction copy(String machine,String source,String target){return new PktMachineEditorAction(PktMachineEditorAction.COPY_RECIPE,machine,source,-1,target,false);}
    private static PktMachineEditorRequest request(MenuMachineEditor menu,Object edit){return new PktMachineEditorRequest(menu.containerId,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit);}
    private static void save(FakePlayer player,Object edit,boolean expected){var result=PktMachineEditorRequest.process(player,request((MenuMachineEditor)player.containerMenu,edit));require(result.result().success()==expected,"copy save: "+result.result().message());}
    private static void reload(MenuMachineEditor menu){MachineReloadListener.reloadNow();menu.setSnapshot(PktMachineEditorRequest.snapshot());}
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Recipe copy: "+message);}
}
