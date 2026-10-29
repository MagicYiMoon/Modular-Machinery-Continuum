/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;
import com.google.gson.*;
/** Validated ordinary item draft, including optional bounded NBT imported from an inventory stack. */
public final class ItemRequirementEditor {
    private ItemRequirementEditor(){}
    public static boolean isItem(JsonObject j){return j!=null&&new JsonPrimitive("modularmachinery:item").equals(j.get("type"))&&!DurabilityEditor.isDurability(j)&&!FuelEditor.isFuel(j);}
    public static JsonObject validate(JsonObject j){
        if(!isItem(j)||!j.has("io-type")||!java.util.Set.of("input","output").contains(j.get("io-type").getAsString()))throw new IllegalArgumentException("普通物品需求无效");
        int amount;try{if(!j.getAsJsonPrimitive("amount").isNumber())throw new ArithmeticException();amount=j.get("amount").getAsBigDecimal().intValueExact();}catch(RuntimeException e){throw new IllegalArgumentException("物品数量须为整数");}
        float chance=j.has("chance")?j.get("chance").getAsFloat():1;
        var clean=BasicRequirementType.ITEM.create(j.get("item").getAsString(),j.get("io-type").getAsString().equals("output"),amount,chance);
        IngredientGroupEditor.copyNbt(j,clean,"output".equals(j.get("io-type").getAsString()));return clean;
    }
    public static JsonObject merge(JsonObject source,JsonObject edited){var clean=validate(edited);if(source==null)return clean;validate(source);
        if(!source.get("io-type").equals(clean.get("io-type")))throw new IllegalArgumentException("不能改变需求输入输出方向");
        var result=source.deepCopy();for(String key:java.util.List.of("item","amount","chance"))result.add(key,clean.get(key));IngredientGroupEditor.copyNbt(clean,result,"output".equals(clean.get("io-type").getAsString()));return result;
    }
}
