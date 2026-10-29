/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;
/** Editor's actual source writes drive ordinary/factory input matching and concrete output data. */
public final class RequirementNbtEditorRegressionChecks {
    public static void run(ServerLevel level,BlockPos origin)throws Exception{
        String id="nbt_editor_"+UUID.randomUUID().toString().replace("-","");var root=ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file=root.resolve(id+".json");var machineFile=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id+".json");
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"NbtEditor")){@Override public boolean hasPermissions(int n){return true;}};var old=player.containerMenu;
        try{
            var blocks=List.of(RegistryBlocks.itemInputBus.get(),RegistryBlocks.itemOutputBus.get(),RegistryBlocks.fluidInputHatch.get(),RegistryBlocks.fluidOutputHatch.get());
            var machine=new JsonObject();var parts=new JsonArray();for(int i=0;i<4;i++){var part=new JsonObject();part.addProperty("x",i+1);part.addProperty("elements",net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blocks.get(i)).toString());part.addProperty("selector-tag","nbt");parts.add(part);}machine.add("parts",parts);Files.writeString(machineFile,machine.toString());
            var source=JsonParser.parseString("""
                {"recipeTime":2,"parallelized":false,"authorNote":{"keep":[1,2]},"requirements":[
                {"type":"modularmachinery:item","io-type":"input","item":"minecraft:coal","amount":1,"chance":1},
                {"type":"modularmachinery:item","io-type":"output","item":"minecraft:diamond","amount":2,"chance":1},
                {"type":"modularmachinery:fluid","io-type":"input","fluid":"minecraft:water","amount":100},
                {"type":"modularmachinery:fluid","io-type":"output","fluid":"minecraft:lava","amount":50},
                {"type":"modularmachinery:fluid_pertick","io-type":"input","fluid":"minecraft:water","amount":10},
                {"type":"modularmachinery:fluid_pertick","io-type":"output","fluid":"minecraft:lava","amount":5}]}
                """).getAsJsonObject();source.addProperty("machine",id);source.addProperty("registryName",id);
            for(var req:source.getAsJsonArray("requirements")){req.getAsJsonObject().addProperty("selector-tag","nbt");req.getAsJsonObject().addProperty("authorNote","preserve");req.getAsJsonObject().addProperty("parallelizeUnaffected",true);}
            Files.writeString(file,source.toString());MachineReloadListener.reloadNow();var menu=new MenuMachineEditor(42,player.getInventory(),PktMachineEditorRequest.snapshot());player.containerMenu=menu;
            String[] data={"{\"quality\":\">=3\",\"name\":\"Coal|Carbon\"}","{quality:7}","{\"purity\":\">=2.0d\"}","{grade:5}","{purity:>=2.0d}","{grade:5}"};
            for(int i=0;i<6;i++){
                int mode=i%2==0?0:1;if(i==4)mode=1;
                String display=i==1?"{quality:99}":i==3 || i==5?"{grade:88}":"";
                var expected=read(file);expected.getAsJsonArray("requirements").set(i,RequirementNbtEditor.merge(req(expected,i),mode,data[i],1,display));
                save(player,edit(id,i,mode,data[i],1,display),true);require(read(file).equals(expected),"only NBT fields change");
                var recipe=MachineReloadListener.getRecipeLoader().getRecipe(id);var runtime=recipe.requirements().get(i);require(RequirementNbtEditor.supports(runtime),"supported runtime");
                var snap=RecipeSnapshotCodec.encode(recipe,Map.of());var decoded=RecipeSnapshotCodec.decode(snap);require(decoded.requirements().get(i).getClass()==runtime.getClass(),"snapshot class");
            }
            checkExecution(level,origin,id);
            // Preserve data representation when only display is changed; empty source strings are also kept.
            String before=Files.readString(file);save(player,edit(id,1,-1,"",0,"{\"quality\":77}"),true);require(req(read(file),1).get("nbt").equals(new JsonPrimitive("{quality:7}")),"pattern representation changed");
            save(player,edit(id,1,-1,"",0,""),true);require(!req(read(file),1).has("nbt-display"),"display removal");
            save(player,edit(id,1,0,"{}",-1,""),true);require(req(read(file),1).get("nbt").isJsonObject(),"explicit empty object lost");
            save(player,edit(id,1,0,"",-1,""),true);require(!req(read(file),1).has("nbt"),"data removal");
            var emptyString=read(file);req(emptyString,0).addProperty("nbt","");Files.writeString(file,emptyString.toString());reload(menu);save(player,edit(id,0,-1,"",0,"{}"),true);require(req(read(file),0).get("nbt").getAsString().isEmpty(),"untouched empty pattern string removed");
            before=Files.readString(file);
            for(String invalid:List.of("[]","null","5","{\"a\":null}","{\"a\":1,\"a\":2}","{\"a\":1,}","{bare:1}","{\"a\":[1,\"x\"]}","{\"s\":\"[\"}"))save(player,edit(id,0,0,invalid,-1,""),false);
            for(String invalid:List.of("{a:1","[1]","{a:1} trailing","{x:[[[[["))save(player,edit(id,0,1,invalid,-1,""),false);
            save(player,edit(id,0,0,"{\"a\":".repeat(50)+"1"+"}".repeat(50),-1,""),false);
            save(player,edit(id,0,2,"{}",-1,""),false);save(player,edit(id,-1,0,"{}",-1,""),false);save(player,edit(id,512,0,"{}",-1,""),false);
            require(Files.readString(file).equals(before),"invalid saves changed source");
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"DeniedNbt")){@Override public boolean hasPermissions(int n){return false;}};denied.containerMenu=new MenuMachineEditor(42,denied.getInventory(),menu.snapshot());save(denied,edit(id,0,0,"{}",-1,""),false);
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(43,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(id,0,0,"{}",-1,""))).result().success(),"window guard");
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(42,UUID.randomUUID(),"stale",edit(id,0,0,"{}",-1,""))).result().success(),"revision guard");
            var external=read(file);external.addProperty("external",true);Files.writeString(file,external.toString());save(player,edit(id,0,0,"{}",-1,""),false);require(read(file).equals(external),"external change lost");reload(menu);
            var request=new PktMachineEditorRequest(42,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(id,0,0,"{}",-1,""));require(PktMachineEditorRequest.process(player,request).result().success(),"first request");require(!PktMachineEditorRequest.process(player,request).result().success(),"duplicate request");
            String readonly=MachineReloadListener.getRecipeLoader().getRecipeNames().stream().filter(n->!MachineReloadListener.getRecipeLoader().isSourceEditable(n)).findFirst().orElseThrow();save(player,edit(readonly,0,0,"{}",-1,""),false);
            var sourceNbt=read(file);var durability=JsonParser.parseString("{\"type\":\"modularmachinery:item_durability\",\"io-type\":\"input\",\"item\":\"minecraft:iron_pickaxe\",\"amount\":1,\"consumeDurability\":1}");sourceNbt.getAsJsonArray("requirements").add(durability);Files.writeString(file,sourceNbt.toString());reload(menu);save(player,edit(id,6,0,"{\"quality\":\">=1\"}",-1,""),true);
            require(!RequirementNbtEditor.supports(new RequirementEnergy(IOType.INPUT,1)),"FE NBT unsupported");
            Modularmachinery.LOG.info("[MMC][nbt-editor] PASS: real bounded wire/source-only field save/reload/codecs, object and pattern formats, display separation, input numeric+regex+fluid matching, ordinary/factory actual item and one-shot/rate fluid payments/output tags, clear vs explicit empty, untouched representation/metadata, durability, invalid JSON/pattern/regex/type/depth/list/permission/window/revision/dedupe/external/readonly guards");
        }finally{player.containerMenu=old;Files.deleteIfExists(file);Files.deleteIfExists(machineFile);MachineReloadListener.reloadNow();}
    }
    private static void checkExecution(ServerLevel level,BlockPos origin,String id){
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();var loader=MachineReloadListener.getRecipeLoader();var recipe=loader.getRecipe(id);var previous=loader.getByMachine().put(id,List.of(recipe));
        try{for(boolean factory:List.of(false,true)){
            var pos=origin.offset(factory?16:0,0,0);level.getChunkAt(pos);level.getChunkAt(pos.east(4));
            Block[] blocks={factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get(),RegistryBlocks.itemInputBus.get(),RegistryBlocks.itemOutputBus.get(),RegistryBlocks.fluidInputHatch.get(),RegistryBlocks.fluidOutputHatch.get()};
            for(int i=0;i<5;i++){var p=pos.east(i);saved.put(p,level.getBlockState(p));level.setBlock(p,blocks[i].defaultBlockState(),3);}
            var tile=(TileController)level.getBlockEntity(pos);tile.setMachineName(id);var machine=MachineReloadListener.getLoader().findByName(id);require(MachineFormation.tryForm(level,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"structure");tile.setFormed(true);
            var input=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.east())).getInventory();var output=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.east(2))).getInventory();
            var fluidIn=(TileFluidTank)level.getBlockEntity(pos.east(3));var fluidOut=(TileFluidTank)level.getBlockEntity(pos.east(4));
            var context=new MachineContext(level,pos,Direction.NORTH,machine,new MachineVariables(),machine.getPattern());
            var coal=new ItemStack(Items.COAL);coal.getOrCreateTag().putInt("quality",2);coal.getOrCreateTag().putString("name","Carbon");input.setStackInSlot(0,coal);require(!recipe.requirements().get(0).canStart(context),"bad numeric input matched");
            coal.getOrCreateTag().putInt("quality",3);coal.getOrCreateTag().putString("name","Wood");require(!recipe.requirements().get(0).canStart(context),"bad regex matched");coal.getOrCreateTag().putString("name","carbon");input.setStackInSlot(0,coal);require(recipe.requirements().get(0).canStart(context),"valid case-insensitive input failed");
            var water=new FluidStack(net.minecraft.world.level.material.Fluids.WATER,120);water.getOrCreateTag().putDouble("purity",1);fluidIn.getTank().setFluid(water);require(!recipe.requirements().get(2).canStart(context),"bad fluid matched");water.getOrCreateTag().putDouble("purity",2);fluidIn.getTank().setFluid(water);
            for(int i=0;i<160 && output.getStackInSlot(0).isEmpty();i++){((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)tile);else MachineTicker.tick(level,pos,tile);}
            var product=output.getStackInSlot(0);require(product.is(Items.DIAMOND) && product.getCount()==2 && product.getTag().getInt("quality")==7 && input.getStackInSlot(0).isEmpty(),"actual item output vs display");
            require(fluidIn.getTank().isEmpty() && fluidOut.getTank().getFluidAmount()==60 && fluidOut.getTank().getFluid().getTag().getInt("grade")==5,"actual once/rate fluid output vs display and conservation");
        }}finally{saved.forEach((p,state)->level.setBlock(p,state,3));loader.getByMachine().put(id,previous);}
    }
    private static PktMachineEditorRequirementNbt edit(String id,int index,int mode,String data,int displayMode,String display){return new PktMachineEditorRequirementNbt(id,index,mode,data,displayMode,display);}
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static JsonObject req(JsonObject j,int i){return j.getAsJsonArray("requirements").get(i).getAsJsonObject();}
    private static void reload(MenuMachineEditor m){MachineReloadListener.reloadNow();m.setSnapshot(PktMachineEditorRequest.snapshot());}
    private static void save(FakePlayer p,Object edit,boolean expected){var m=(MenuMachineEditor)p.containerMenu;var b=new FriendlyByteBuf(Unpooled.buffer());try{PktMachineEditorRequest.encode(new PktMachineEditorRequest(m.containerId,UUID.randomUUID(),EditorSaveState.revision(m.snapshot()),edit),b);var result=PktMachineEditorRequest.process(p,PktMachineEditorRequest.decode(b));require(result.result().success()==expected,"save: "+result.result().message());if(expected)require(result.snapshot()!=null && m.snapshot().equals(PktMachineEditorRequest.snapshot()),"ack before reload");}finally{b.release();}}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("NBT editor: "+message);}
}
