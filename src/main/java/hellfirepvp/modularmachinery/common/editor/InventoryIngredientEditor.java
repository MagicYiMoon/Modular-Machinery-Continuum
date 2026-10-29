/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;
import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
/** Copies authoring data only. Never removes inventory items or grants their contents. */
public final class InventoryIngredientEditor {
    private InventoryIngredientEditor(){}
    public static JsonObject data(ItemStack stack,boolean output){
        var result=new JsonObject();if(!stack.hasTag())return result;
        var compound=stack.getTag();result.add("nbt",convert(compound,!output,0));
        var context=result.deepCopy();context.addProperty("io-type",output?"output":"input");
        IngredientGroupEditor.copyNbt(context,new JsonObject(),output);
        var parsed=hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.read(result);
        if(output&&!compound.equals(parsed.output())||!output&&!parsed.matches(compound))throw new IllegalArgumentException("此 NBT 无法用配方模式无损表示，请关闭“带入 NBT”后选择");
        return result;
    }
    private static JsonElement convert(Tag tag,boolean input,int depth){
        if(depth>48)throw new IllegalArgumentException("物品 NBT 超过 48 层，请关闭带入 NBT");
        if(tag instanceof CompoundTag compound){var j=new JsonObject();for(String key:compound.getAllKeys())j.add(key,convert(compound.get(key),input,depth+1));return j;}
        if(tag instanceof ListTag list){var j=new JsonArray();for(Tag entry:list)j.add(convert(entry,input,depth+1));return j;}
        if(tag instanceof StringTag str)return new JsonPrimitive(input?java.util.regex.Pattern.quote(str.getAsString()):str.getAsString());
        if(tag instanceof NumericTag)return new JsonPrimitive(tag.toString());
        throw new IllegalArgumentException("物品含配方模式不支持的 NBT 数组，请关闭“带入 NBT”后选择");
    }
}
