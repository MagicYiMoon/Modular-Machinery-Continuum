/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.*;
import java.util.*;
/** Pure detached adapter requirements. These edits cannot write ordinary recipes or send packets. */
public final class TemplateRequirementDraft {
    private TemplateRequirementDraft(){}
    public static JsonArray apply(JsonArray source,Object edit,JsonObject owner){
        var result=clean(source);int index=-1;JsonObject value=null;
        if(edit instanceof PktMachineEditorRequirementAdd p)value=BasicRequirementType.fromKey(p.type()).create(p.resourceId(),p.output(),p.amount(),p.chance());
        else if(edit instanceof PktMachineEditorItem p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorIngredientGroup p){index=p.index();value=JsonParser.parseString(p.groupJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorEnvironment p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorPneumatic p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorResourceAmount p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorSmartInterface p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorFluidTick p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorDurability p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorFuel p){index=p.index();value=JsonParser.parseString(p.requirementJson()).getAsJsonObject();}
        else if(edit instanceof PktMachineEditorRequirementNbt p){index=p.index();value=RequirementNbtEditor.merge(result.get(index).getAsJsonObject(),p.dataMode(),p.data(),p.displayMode(),p.display());return replace(result,index,value);}
        else if(edit instanceof PktMachineEditorSelector p){index=p.index();value=SelectorBindingEditor.merge(result.get(index).getAsJsonObject(),p.tag(),strings(owner,"selectorTags"));return replace(result,index,value);}
        else if(edit instanceof PktMachineEditorRequirementBatch p){index=p.index();value=RequirementBatchEditor.merge(result.get(index).getAsJsonObject(),p.policy());return replace(result,index,value);}
        else if(edit instanceof PktMachineEditorEdit p){index=p.requirementIndex;var old=result.get(index).getAsJsonObject();value=old.deepCopy();String key=RequirementResourceSelection.key(old);if(!p.itemId.isEmpty())value=RequirementResourceSelection.replace(value,p.itemId);value.addProperty(old.has("energyPerTick")?"energyPerTick":"amount",p.amount);if(!old.has("energyPerTick"))value.addProperty("chance",p.chance);RecipeLoader.validateEditorRequirement(value);return replace(result,index,value);}
        else if(edit instanceof PktMachineEditorAction p && p.action==PktMachineEditorAction.DELETE_REQUIREMENT){result.remove(p.requirementIndex);return result;}
        else throw new IllegalArgumentException("模板仅编辑追加需求，不修改机器或生成配方");
        var old=index<0?null:result.get(index).getAsJsonObject();
        if(IngredientGroupEditor.isGroup(value))value=IngredientGroupEditor.merge(old,value);
        else if(DurabilityEditor.isDurability(value))value=DurabilityEditor.merge(old,value);
        else if(ItemRequirementEditor.isItem(value))value=ItemRequirementEditor.merge(old,value);
        else if(EnvironmentEditor.isEnvironment(value))value=EnvironmentEditor.merge(old,value);
        else if(PneumaticEditor.isPneumatic(value))value=PneumaticEditor.merge(old,value);
        else if(ResourceAmountEditor.isResource(value))value=ResourceAmountEditor.merge(old,value);
        else if(SmartInterfaceEditor.isSmart(value))value=SmartInterfaceEditor.merge(old,value,strings(owner,"smartInterfaceTypes"));
        else if(FluidTickEditor.isRate(value))value=FluidTickEditor.merge(old,value);
        else if(FuelEditor.isFuel(value))value=FuelEditor.merge(old,value);
        else if(old!=null){var merged=old.deepCopy();value.entrySet().forEach(e->merged.add(e.getKey(),e.getValue()));value=merged;}
        RecipeLoader.validateEditorRequirement(value);if(index<0)result.add(value);else result.set(index,value);return result;
    }
    private static JsonArray replace(JsonArray a,int i,JsonObject j){RecipeLoader.validateEditorRequirement(j);a.set(i,j);return a;}
    private static List<String> strings(JsonObject j,String key){var r=new ArrayList<String>();if(j.has(key))j.getAsJsonArray(key).forEach(v->r.add(v.getAsString()));return r;}
    public static JsonArray clean(JsonArray requirements){var result=requirements.deepCopy();for(var entry:result){var j=entry.getAsJsonObject();for(String key:List.of("editableSelector","editableNbt","editableBatch","editableTrigger","batchPolicy"))j.remove(key);}return result;}
    public static JsonArray decorate(JsonArray requirements){var result=clean(requirements);for(var entry:result){var j=entry.getAsJsonObject();var runtime=RecipeLoader.validateEditorRequirement(j);j.addProperty("editableSelector",runtime instanceof SelectorRequirement);j.addProperty("editableNbt",RequirementNbtEditor.supports(runtime));j.addProperty("editableBatch",RequirementBatchEditor.supports(runtime));j.addProperty("editableTrigger",false);j.addProperty("batchPolicy",RequirementBatchEditor.policy(j));}return result;}
}
