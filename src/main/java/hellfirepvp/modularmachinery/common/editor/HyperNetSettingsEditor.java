/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetDefinition;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Changes only display-info and hypernet; unknown machine fields survive. */
public final class HyperNetSettingsEditor {
    private HyperNetSettingsEditor() {}
    public static JsonObject profile(MachineLoader loader,String name) {
        var raw=loader.snapshot().getAsJsonObject("machines").getAsJsonObject(name);
        var out=new JsonObject();
        out.add("display-info",loader.findByName(name).getDisplayInfo().json());
        out.add("hypernet",raw!=null&&raw.has("hypernet")?raw.get("hypernet").deepCopy():JsonNull.INSTANCE);
        return out;
    }
    public static JsonObject validate(JsonObject j) {
        for(var k:j.keySet())if(!java.util.Set.of("display-info","hypernet").contains(k))throw new IllegalArgumentException("未知设置："+k);
        if(!j.has("display-info")||!j.get("display-info").isJsonObject())throw new IllegalArgumentException("缺少等级说明对象");
        var result=new JsonObject();result.add("display-info",MachineDisplayInfo.parse(j.get("display-info")).json());
        if(!j.has("hypernet"))throw new IllegalArgumentException("缺少 hypernet 设置");
        if(!j.get("hypernet").isJsonNull())HyperNetDefinition.parse(j.get("hypernet"));
        result.add("hypernet",j.get("hypernet").deepCopy());return result;
    }
    public static void save(MachineLoader loader,String name,JsonObject values)throws java.io.IOException {
        var clean=validate(values);
        boolean terminal=!clean.get("hypernet").isJsonNull()&&HyperNetDefinition.parse(clean.get("hypernet")).role()==HyperNetDefinition.Role.TERMINAL;
        if(terminal&&!name.equals("hypernet_terminal"))throw new IllegalArgumentException("终端角色仅供 hypernet_terminal 独立终端定义使用");
        if(name.equals("hypernet_terminal")&&!terminal)throw new IllegalArgumentException("独立终端必须保留 terminal 角色");
        String source=loader.getSourcePath(name);
        if(source==null)throw new IllegalArgumentException("该机器没有可编辑的 JSON 源文件");
        var file=new java.io.File(source).getCanonicalFile().toPath();
        var root=ModDataHolder.INSTANCE.getMachineryDirectory().getCanonicalFile().toPath();
        if(!file.startsWith(root)||!Files.isRegularFile(file))throw new IllegalArgumentException("机器来源只读");
        var json=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
        var loaded=loader.snapshot().getAsJsonObject("machines").getAsJsonObject(name);
        // Compare the fields being edited as well as identity; unrelated script runtime fields are ignored.
        if(json.has("registryname")&&!name.equals(json.get("registryname").getAsString()))throw new IllegalArgumentException("机器文件身份已改变");
        for(var key:java.util.List.of("hypernet","display-info"))
            if(!java.util.Objects.equals(json.get(key),loaded.get(key)))throw new IllegalArgumentException("源文件已变化，请重载后重新编辑");
        json.add("display-info",clean.get("display-info"));
        if(clean.get("hypernet").isJsonNull())json.remove("hypernet");else json.add("hypernet",clean.get("hypernet"));
        var temp=file.resolveSibling(file.getFileName()+".mmc.tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(json),StandardCharsets.UTF_8);
        try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
