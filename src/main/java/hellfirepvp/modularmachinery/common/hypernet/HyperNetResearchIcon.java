/* SPDX-License-Identifier: GPL-3.0-only
 * Full single-count preview stacks follow NECO ResearchCognitionData, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.JsonObject;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;

/** Immutable serialized stack. Never exposes a shared mutable ItemStack or instantiates items while parsing configuration. */
public record HyperNetResearchIcon(String snbt) {
    public static final int MAX_BYTES=16*1024;
    public HyperNetResearchIcon {
        bounded(snbt);
        try {
            var tag=TagParser.parseTag(snbt);
            for(var key:tag.getAllKeys())if(!java.util.Set.of("id","Count","tag","ForgeCaps").contains(key))throw new IllegalArgumentException("Unknown preview stack field: "+key);
            if(!tag.contains("id",Tag.TAG_STRING)||tag.getString("id").length()>256||ResourceLocation.tryParse(tag.getString("id"))==null)throw new IllegalArgumentException("Invalid research preview item");
            if(tag.contains("Count")&&(!tag.contains("Count",Tag.TAG_ANY_NUMERIC)||!Double.isFinite(tag.getDouble("Count"))||tag.getDouble("Count")<=0))throw new IllegalArgumentException("Invalid preview count");
            for(var key:java.util.List.of("tag","ForgeCaps"))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException(key+" must be a compound");
            tag.putByte("Count",(byte)1); // Upstream previews always have size one.
            snbt=tag.toString();bounded(snbt);
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("Invalid preview stack SNBT: "+e.getMessage(),e);}
    }
    private static void bounded(String text){
        if(text==null||text.length()>MAX_BYTES||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Preview stack exceeds 16 KiB");
        // Bound recursion BEFORE invoking vanilla's recursive SNBT parser; ignore braces inside strings.
        int depth=0;char quote=0;boolean escape=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='\''||c=='"'){quote=c;continue;}
            if(c=='{'||c=='['){if(++depth>64)throw new IllegalArgumentException("Preview stack nesting exceeds 64");}
            else if(c=='}'||c==']')depth--;
        }
    }
    public static HyperNetResearchIcon ofItem(String id){
        if(id==null||id.length()>256||ResourceLocation.tryParse(id)==null)throw new IllegalArgumentException("Invalid research preview item");
        var tag=new CompoundTag();tag.putString("id",id);return new HyperNetResearchIcon(tag.toString());
    }
    public static HyperNetResearchIcon ofStack(ItemStack stack){
        if(stack==null||stack.isEmpty())throw new IllegalArgumentException("Empty preview stack");
        var copy=stack.copy();copy.setCount(1);return new HyperNetResearchIcon(copy.save(new CompoundTag()).toString());
    }
    private CompoundTag data(){try{return TagParser.parseTag(snbt);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalStateException(e);}}
    public String item(){return data().getString("id");}
    /** Fresh stack on every call. Unknown/removed items retain their config but render as paper. */
    public ItemStack stack(){
        var tag=data();var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("id")));
        if(item==null||item==Items.AIR)return new ItemStack(Items.PAPER);
        var stack=ItemStack.of(tag);return stack.isEmpty()?new ItemStack(Items.PAPER):stack;
    }
    public static HyperNetResearchIcon parse(JsonObject j){
        if(j.has("preview-stack")){
            if(j.has("preview-item"))throw new IllegalArgumentException("Use either preview-item or preview-stack");
            if(!j.get("preview-stack").isJsonPrimitive()||!j.getAsJsonPrimitive("preview-stack").isString())throw new IllegalArgumentException("preview-stack must be SNBT text");
            return new HyperNetResearchIcon(j.get("preview-stack").getAsString());
        }
        if(!j.has("preview-item"))return ofItem("minecraft:paper");
        if(!j.get("preview-item").isJsonPrimitive()||!j.getAsJsonPrimitive("preview-item").isString())throw new IllegalArgumentException("preview-item must be a string");
        return ofItem(j.get("preview-item").getAsString());
    }
    public void put(JsonObject j){
        j.remove("preview-item");j.remove("preview-stack");
        var tag=data();if(tag.contains("tag")||tag.contains("ForgeCaps"))j.addProperty("preview-stack",snbt);else j.addProperty("preview-item",tag.getString("id"));
    }
    public void write(FriendlyByteBuf b){b.writeByteArray(snbt.getBytes(StandardCharsets.UTF_8));}
    public static HyperNetResearchIcon read(FriendlyByteBuf b){
        int size=b.readVarInt();if(size<0||size>MAX_BYTES||size>b.readableBytes())throw new IllegalArgumentException("Invalid preview payload size");
        byte[] bytes=new byte[size];b.readBytes(bytes);
        try{return new HyperNetResearchIcon(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}
        catch(java.nio.charset.CharacterCodingException e){throw new IllegalArgumentException("Invalid preview UTF-8",e);}
    }
}
