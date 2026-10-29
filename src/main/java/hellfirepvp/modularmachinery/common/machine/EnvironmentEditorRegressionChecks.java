/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
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
/** Real editor wire, disk, reload and runtime parser share the same environment rules. */
public final class EnvironmentEditorRegressionChecks {
    public static void run(ServerLevel level)throws Exception {
        String id="env_editor_"+UUID.randomUUID().toString().replace("-","");
        var root=ModDataHolder.INSTANCE.getRecipeDirectory().toPath();var file=root.resolve(id+".json");
        var owner=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id+".json");
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"EnvEditor")){@Override public boolean hasPermissions(int n){return true;}};
        var old=player.containerMenu;
        try {
            Files.writeString(owner,"{\"parts\":[{\"x\":1,\"elements\":\"modularmachinery:blockenvironmentdetector\",\"selector-tag\":\"sensor\"}]}");
            var json=JsonParser.parseString("{\"recipeTime\":2,\"authorNote\":{\"keep\":1},\"requirements\":[]}").getAsJsonObject();json.addProperty("machine",id);json.addProperty("registryName",id);
            var first=condition("{\"condition\":\"altitude\",\"min\":-64,\"max\":319}");first.addProperty("selector-tag","sensor");first.addProperty("authorNote","keep");json.getAsJsonArray("requirements").add(first);
            Files.writeString(file,json.toString());MachineReloadListener.reloadNow();var menu=new MenuMachineEditor(44,player.getInventory(),PktMachineEditorRequest.snapshot());player.containerMenu=menu;
            for(String body:List.of("{\"condition\":\"time\",\"min\":23000,\"max\":1000}","{\"condition\":\"dimensions\",\"values\":[\"minecraft:overworld\",\"missing:custom\"]}","{\"condition\":\"biomes\",\"values\":[\"minecraft:plains\",\"#minecraft:is_forest\"]}","{\"condition\":\"moonphase\",\"values\":[0,4,7]}","{\"condition\":\"weather\",\"weather\":\"snowing\"}","{\"condition\":\"altitude\",\"min\":-64,\"max\":-1}")) {
                var edit=condition(body);var before=read(file);var expected=before.deepCopy();expected.getAsJsonArray("requirements").set(0,EnvironmentEditor.merge(before.getAsJsonArray("requirements").get(0).getAsJsonObject(),edit));
                save(player,new PktMachineEditorEnvironment(id,0,edit.toString(),"",2,""),true);require(read(file).equals(expected),"condition metadata preserved");
                require(MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0) instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnvironment,"actual runtime requirement");
            }
            String before=Files.readString(file);
            for(String body:List.of("{\"condition\":\"altitude\",\"min\":2,\"max\":1}","{\"condition\":\"time\",\"min\":0,\"max\":24000}","{\"condition\":\"altitude\",\"min\":1.5,\"max\":3}","{\"condition\":\"moonphase\",\"values\":[8]}","{\"condition\":\"biomes\",\"values\":[\"plains\"]}","{\"condition\":\"dimensions\",\"values\":[\"#minecraft:overworld\"]}","{\"condition\":\"dimensions\",\"values\":[]}","{\"condition\":\"weather\",\"weather\":\"hail\"}"))save(player,new PktMachineEditorEnvironment(id,0,condition(body).toString(),"",2,""),false);
            var output=first.deepCopy();output.addProperty("io-type","output");save(player,new PktMachineEditorEnvironment(id,0,output.toString(),"",2,""),false);
            var chance=first.deepCopy();chance.addProperty("chance",1);save(player,new PktMachineEditorEnvironment(id,0,chance.toString(),"",2,""),false);
            require(Files.readString(file).equals(before),"invalid data wrote source");
            var external=read(file);external.addProperty("authorNote","external");Files.writeString(file,external.toString());save(player,new PktMachineEditorEnvironment(id,0,first.toString(),"",2,""),false);require(read(file).equals(external),"external edit overwritten");menu.setSnapshot(PktMachineEditorRequest.snapshot());MachineReloadListener.reloadNow();menu.setSnapshot(PktMachineEditorRequest.snapshot());
            save(player,new PktMachineEditorEnvironment(id,-1,first.toString(),"",2,""),true);require(read(file).getAsJsonArray("requirements").size()==2,"append");
            Modularmachinery.LOG.info("[MMC][environment-editor] PASS: actual bounded wire/disk/reload, all six conditions, metadata, negative heights and midnight, biome tags and unknown valid IDs, invalid types/ranges/IDs/direction/chance and external-file guard");
        }finally{player.containerMenu=old;Files.deleteIfExists(file);Files.deleteIfExists(owner);MachineReloadListener.reloadNow();}
    }
    private static JsonObject condition(String body){var j=JsonParser.parseString(body).getAsJsonObject();j.addProperty("type","modularmachinery:environment");j.addProperty("io-type","input");return j;}
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static void save(FakePlayer p,Object edit,boolean expected){var m=(MenuMachineEditor)p.containerMenu;var request=new PktMachineEditorRequest(m.containerId,UUID.randomUUID(),EditorSaveState.revision(m.snapshot()),edit);var buf=new FriendlyByteBuf(Unpooled.buffer());try{PktMachineEditorRequest.encode(request,buf);request=PktMachineEditorRequest.decode(buf);}finally{buf.release();}var reply=PktMachineEditorRequest.process(p,request);require(reply.result().success()==expected,reply.result().message());}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
