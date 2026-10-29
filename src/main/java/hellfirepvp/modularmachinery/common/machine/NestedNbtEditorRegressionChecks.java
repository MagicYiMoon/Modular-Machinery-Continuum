/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;
public final class NestedNbtEditorRegressionChecks {
    public static void run(ServerLevel level)throws Exception {
        String id="nested_nbt_"+UUID.randomUUID().toString().replace("-","");var file=ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id+".json");
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"NestedNbt")){@Override public boolean hasPermissions(int n){return true;}};var old=player.containerMenu;
        try {
            var source=JsonParser.parseString("""
                {"recipeTime":2,"requirements":[
                {"type":"modularmachinery:ingredient_array_input","io-type":"input","nbt":"{quality:>=3}","items":[{"item":"minecraft:coal","amount":1,"note":"first"},{"item":"minecraft:charcoal","amount":1,"nbt":{"quality":">=5"},"nbt-display":{"quality":99},"note":"second"}]},
                {"type":"modularmachinery:ingredient_array_input","io-type":"output","nbt":{"grade":7},"items":[{"item":"minecraft:diamond","amount":1},{"item":"minecraft:emerald","amount":1,"nbt":"{grade:9}","nbt-display":"{grade:99}"}]},
                {"type":"modularmachinery:catalyst","io-type":"input","chance":0,"nbt":{"quality":">=3"},"items":[{"item":"minecraft:coal","amount":1}],"modifiers":[],"tooltips":["keep"]}]}
                """).getAsJsonObject();source.addProperty("registryName",id);source.addProperty("machine",MachineReloadListener.getLoader().all().iterator().next().getRegistryName());
            Files.writeString(file,source.toString());MachineReloadListener.reloadNow();player.containerMenu=new MenuMachineEditor(45,player.getInventory(),PktMachineEditorRequest.snapshot());
            for(int index=0;index<3;index++) {
                var snapshot=snapshot(player,id,index);var original=source.getAsJsonArray("requirements").get(index).getAsJsonObject();
                require(snapshot.get("nbt").equals(original.get("nbt")),"group default source representation");
                for(int i=0;i<original.getAsJsonArray("items").size();i++) for(String key:List.of("nbt","nbt-display")) require(Objects.equals(snapshot.getAsJsonArray("items").get(i).getAsJsonObject().get(key),original.getAsJsonArray("items").get(i).getAsJsonObject().get(key)),"candidate source representation/inheritance");
                var draft=IngredientGroupEditor.draft(snapshot);var expected=IngredientGroupEditor.merge(original,draft);save(player,id,index,draft,true);require(read(file).getAsJsonArray("requirements").get(index).equals(expected),"unchanged nested roundtrip");
            }
            var recipe=MachineReloadListener.getRecipeLoader().getRecipe(id);var input=(RequirementIngredientArray)recipe.requirements().get(0);
            var coal=new ItemStack(Items.COAL);coal.getOrCreateTag().putInt("quality",2);require(!input.getIngredients().get(0).matches(coal),"default matching");coal.getTag().putInt("quality",3);require(input.getIngredients().get(0).matches(coal),"default valid");
            var charcoal=new ItemStack(Items.CHARCOAL);charcoal.getOrCreateTag().putInt("quality",4);require(!input.getIngredients().get(1).matches(charcoal),"candidate override matching");charcoal.getTag().putInt("quality",5);require(input.getIngredients().get(1).matches(charcoal),"candidate valid override");
            var output=(RequirementIngredientArray)recipe.requirements().get(1);require(output.getIngredients().get(0).asStack().getTag().getInt("grade")==7 && output.getIngredients().get(1).asStack().getTag().getInt("grade")==9,"actual output vs display");
            var draft=IngredientGroupEditor.draft(snapshot(player,id,0));var first=draft.getAsJsonArray("items").remove(0);draft.getAsJsonArray("items").add(first);
            draft.getAsJsonArray("items").get(0).getAsJsonObject().remove("nbt");save(player,id,0,draft,true);
            var saved=read(file).getAsJsonArray("requirements").get(0).getAsJsonObject();require(saved.getAsJsonArray("items").get(0).getAsJsonObject().get("note").getAsString().equals("second") && !saved.getAsJsonArray("items").get(0).getAsJsonObject().has("nbt"),"reorder/remove field identity");
            input=(RequirementIngredientArray)MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0);charcoal.getTag().putInt("quality",3);require(input.getIngredients().get(0).matches(charcoal),"removed candidate inherits default");
            draft=IngredientGroupEditor.draft(snapshot(player,id,0));var bad=draft.deepCopy();bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("nbt","{name:[}");String before=Files.readString(file);save(player,id,0,bad,false);require(Files.readString(file).equals(before),"invalid nested pattern wrote source");
            Modularmachinery.LOG.info("[MMC][nested-nbt-editor] PASS: actual source/reload, groups/random outputs/catalysts, inherited and overridden input matching, real output vs display NBT, representation, reorder identity, removal inheritance and invalid-data guard");
        }finally{player.containerMenu=old;Files.deleteIfExists(file);MachineReloadListener.reloadNow();}
    }
    private static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static JsonObject snapshot(FakePlayer p,String id,int index){for(var m:JsonParser.parseString(((MenuMachineEditor)p.containerMenu).snapshot()).getAsJsonObject().getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals(id))return r.getAsJsonObject().getAsJsonArray("requirements").get(index).getAsJsonObject();throw new AssertionError("missing recipe");}
    private static void save(FakePlayer p,String id,int index,JsonObject j,boolean expected){var m=(MenuMachineEditor)p.containerMenu;var reply=PktMachineEditorRequest.process(p,new PktMachineEditorRequest(m.containerId,UUID.randomUUID(),EditorSaveState.revision(m.snapshot()),new PktMachineEditorIngredientGroup(id,index,j.toString(),"",2,"")));require(reply.result().success()==expected,reply.result().message());}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
