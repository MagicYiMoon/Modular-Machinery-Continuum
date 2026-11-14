/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementResearch;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.ResearchRequirementType;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchRegistry;
import hellfirepvp.modularmachinery.common.machine.IOType;
import java.util.*;

/** A nonconsuming start gate, never a probabilistic, batched or output resource. */
public final class ResearchGateEditor {
    private ResearchGateEditor(){}
    public static boolean isResearch(JsonObject j){return j!=null&&j.has("type")&&j.get("type").isJsonPrimitive()&&j.get("type").getAsString().equals("modularmachinery:research");}
    public static JsonObject create(List<String> ids){
        var gate=new RequirementResearch(ids);var j=new JsonObject();j.addProperty("type",gate.typeId());j.addProperty("io-type","input");var a=new JsonArray();ids.forEach(a::add);j.add("research",a);return j;
    }
    public static JsonObject validate(JsonObject source){
        if(!isResearch(source)||!source.has("io-type")||!source.get("io-type").isJsonPrimitive()||!source.get("io-type").getAsString().equals("input"))throw new IllegalArgumentException("研究门槛仅支持输入");
        var j=source.deepCopy();for(var key:List.of("editableSelector","editableNbt","editableBatch","editableTrigger","batchPolicy"))j.remove(key);
        try {
            var gate=(RequirementResearch)new ResearchRequirementType().createRequirement(IOType.INPUT,j);return create(gate.research());
        } catch (JsonParseException e) { throw new IllegalArgumentException(e.getMessage(),e); }
    }
    public static List<String> ids(JsonObject j){var result=new ArrayList<String>();validate(j).getAsJsonArray("research").forEach(e->result.add(e.getAsString()));return List.copyOf(result);}
    public static JsonObject merge(JsonObject original,JsonObject edited){if(original!=null)validate(original);return validate(edited);}
    public static void validateKnown(JsonObject original,JsonObject edited){
        validateKnownIds(original==null?List.of():ids(original),edited);
    }
    public static void validateKnownIds(Collection<String> original,JsonObject edited){
        if(HyperNetResearchRegistry.loadedCatalog()==null)throw new IllegalArgumentException("研究目录无效，请先修复并重载");
        var retained=new HashSet<>(original);
        for(var id:ids(edited))if(!retained.contains(id)&&HyperNetResearchRegistry.find(id)==null)throw new IllegalArgumentException("研究不存在："+id);
    }
}
