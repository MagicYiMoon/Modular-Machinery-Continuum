/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterRegistry;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
/** Edits generators rather than their read-only expanded recipes. */
public final class AdapterTemplateEditor {
    private AdapterTemplateEditor(){}
    public static JsonObject merge(JsonObject source,JsonObject edit){
        var adapter=ResourceLocation.tryParse(edit.get("adapter").getAsString());
        if(adapter==null||RecipeAdapterRegistry.get(adapter)==null)throw new IllegalArgumentException("请选择服务器已注册的适配器");
        var mods=edit.getAsJsonArray("modifiers");var reqs=edit.getAsJsonArray("requirements");
        if(mods==null||mods.size()>32||reqs==null||reqs.size()>128)throw new IllegalArgumentException("模板最多 32 个修改器／128 个追加需求");
        for(var mod:mods){
            if(!mod.isJsonObject())throw new IllegalArgumentException("模板修改器必须为对象");
            var raw=mod.getAsJsonObject();var parsed=RecipeModifier.deserialize(raw);
             
            if(parsed!=null&&source.has("modifiers")&&source.getAsJsonArray("modifiers").contains(raw))continue;
            if(parsed==null||!Float.isFinite(parsed.getModifier())||ResourceLocation.tryParse(parsed.getTarget())==null
                    ||!raw.getAsJsonPrimitive("multiplier").isNumber()||!raw.getAsJsonPrimitive("operation").isNumber()
                    ||raw.get("operation").getAsBigDecimal().intValueExact()!=parsed.getOperation()
                    ||raw.has("affectChance")&&!raw.getAsJsonPrimitive("affectChance").isBoolean())
                throw new IllegalArgumentException("模板修改器无效：须使用有限数值和 0／1 整数运算");
        }
        for(var req:reqs){if(!req.isJsonObject())throw new IllegalArgumentException("追加需求必须为对象");RecipeLoader.validateEditorRequirement(req.getAsJsonObject());}
        var result=source.deepCopy();result.addProperty("adapter",adapter.toString());result.add("modifiers",mods.deepCopy());result.add("requirements",reqs.deepCopy());return result;
    }
    public static void save(String path,String digest,String machine,JsonObject edit)throws Exception {
        var recipes=MachineReloadListener.getRecipeLoader();var root=ModDataHolder.INSTANCE.getRecipeDirectory().getCanonicalFile().toPath();
        boolean creating=digest.isEmpty();JsonObject source;Path target;
        if(creating){RecipeCopyEditor.validateId(path);if(MachineReloadListener.getLoader().findByName(machine)==null)throw new IllegalArgumentException("目标机器不存在");
            target=root.resolve(path+".adapter.json");if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("模板文件已存在");source=new JsonObject();source.addProperty("machine",machine);
        }else{
            JsonObject loaded=null;for(var entry:recipes.editorTemplates())if(entry.getAsJsonObject().get("path").getAsString().equals(path))loaded=entry.getAsJsonObject();
            if(loaded==null||!loaded.get("digest").getAsString().equals(digest))throw new IllegalArgumentException("模板已变化，请重新打开编辑器");
            source=loaded.getAsJsonObject("source").deepCopy();target=root.resolve(path).normalize();
            if(!target.startsWith(root)||!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||!target.toRealPath().equals(target)||!JsonParser.parseString(Files.readString(target)).equals(source))throw new IllegalArgumentException("模板源文件已在磁盘上变化或为链接，未覆盖修改");
        }
        var result=merge(source,edit);var owners=result.get("machine");var names=new ArrayList<String>();if(owners.isJsonArray())owners.getAsJsonArray().forEach(n->names.add(n.getAsString()));else names.add(owners.getAsString());
        // References introduced in a template affect every owner and every generated recipe.
        for(String owner:names){var def=MachineReloadListener.getLoader().findByName(owner);if(def==null)throw new IllegalArgumentException("模板所属机器不存在："+owner);
            var profile=RecipeCopyEditor.profile(def);for(var requirement:result.getAsJsonArray("requirements")){
                var req=requirement.getAsJsonObject();boolean unchanged=source.has("requirements")&&source.getAsJsonArray("requirements").contains(req);if(unchanged)continue;
                var check=new JsonObject();var list=new JsonArray();list.add(req);check.add("requirements",list);RecipeCopyEditor.validateReferences(check,profile,"");
            }
        }
        String content=new GsonBuilder().setPrettyPrinting().create().toJson(result),before=creating?null:Files.readString(target);
        if(!creating&&!JsonParser.parseString(before).equals(source))throw new IllegalArgumentException("模板源文件已在磁盘上变化，未覆盖修改");
        Path temp=Files.createTempFile(root,"template-",".mmce.tmp");boolean published=false;
        try{
            Files.writeString(temp,content);
            if(creating)Files.move(temp,target);
            else{
                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||!target.toRealPath().equals(target)||!Files.readString(target).equals(before))throw new IllegalArgumentException("模板源文件在保存前变化，未覆盖修改");
                replace(temp,target);
            }
            published=true;MachineReloadListener.reloadNow();
            String relative=root.relativize(target).toString().replace('\\','/');boolean loaded=false;
            for(var entry:MachineReloadListener.getRecipeLoader().editorTemplates()){
                var actual=entry.getAsJsonObject();if(actual.get("path").getAsString().equals(relative)&&actual.getAsJsonObject("source").equals(result)&&actual.get("expanded").getAsBoolean())loaded=true;
            }
            if(!loaded)throw new IllegalArgumentException("模板未能完整重新加载");
            if(MachineEditorSnapshot.create(MachineReloadListener.getLoader(),MachineReloadListener.getRecipeLoader()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    >hellfirepvp.modularmachinery.common.menu.MenuMachineEditor.MAX_SNAPSHOT_BYTES)
                throw new IllegalArgumentException("生成结果超过编辑器同步上限，请缩小导入范围");
        }catch(Exception e){
            if(published){
                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||!target.toRealPath().equals(target)||!Files.readString(target).equals(content))
                    throw new java.io.IOException("保存后文件被外部修改，未覆盖外部内容；请检查源文件。原错误："+e.getMessage(),e);
                try{if(creating)Files.delete(target);else{Files.writeString(temp,before);replace(temp,target);}MachineReloadListener.reloadNow();}
                catch(Exception rollback){e.addSuppressed(rollback);throw new java.io.IOException("模板保存失败且撤销失败，请检查源文件："+e.getMessage(),e);}
                throw new java.io.IOException("模板保存失败，已撤销本次文件修改："+e.getMessage(),e);
            }
            throw e;
        }finally{Files.deleteIfExists(temp);}
    }
    private static void replace(Path temp,Path target)throws java.io.IOException{
        try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
    }
}
