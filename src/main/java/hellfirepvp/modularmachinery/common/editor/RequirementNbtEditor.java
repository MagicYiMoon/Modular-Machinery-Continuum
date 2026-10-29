/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;
import com.google.gson.*;
import com.google.gson.stream.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.util.nbt.*;
import java.io.StringReader;
import java.util.*;
/** Field-only NBT edits. Empty text removes a field; an explicit {} remains an explicit compound. */
public final class RequirementNbtEditor {
    public static final int MAX_TEXT=8192;
    private RequirementNbtEditor(){}
    public static boolean supports(Requirement r){return r instanceof RequirementItem || r instanceof RequirementFluid || r instanceof RequirementFluidPerTick;}
    public static JsonElement parse(int mode,String text){
        if(mode<0 || mode>1 || text==null || text.length()>MAX_TEXT)throw new IllegalArgumentException("NBT 格式或长度无效，最多 8192 字符");
        if(text.isBlank())return null;
        bounded(text);
        if(mode==1)return new JsonPrimitive(text);
        return parseObject(text,MAX_TEXT,2048);
    }
    /** Shared strict parser; templates have a larger document budget than one NBT field. */
    public static JsonObject parseObject(String text,int maxText,int maxNodes){
        if(text==null||text.isBlank()||text.length()>maxText)throw new IllegalArgumentException("JSON 长度无效，最多 "+maxText+" 字符");
        try(var reader=new JsonReader(new StringReader(text))){
            reader.setLenient(false);walk(reader,0,new int[]{0},maxNodes);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("JSON 后存在额外内容");
        }catch(Exception invalid){throw new IllegalArgumentException("JSON 无效："+invalid.getMessage());}
        var result=JsonParser.parseString(text);
        if(!result.isJsonObject())throw new IllegalArgumentException("JSON 的根必须为对象／复合标签");
        return result.getAsJsonObject();
    }
    private static void walk(JsonReader r,int depth,int[] nodes,int maxNodes)throws java.io.IOException{
        if(depth>48 || ++nodes[0]>maxNodes)throw new IllegalArgumentException("JSON 最多 48 层／"+maxNodes+" 个节点");
        switch(r.peek()){
            case BEGIN_OBJECT -> {r.beginObject();var keys=new HashSet<String>();while(r.hasNext()){if(!keys.add(r.nextName()))throw new IllegalArgumentException("JSON 含重复键");walk(r,depth+1,nodes,maxNodes);}r.endObject();}
            case BEGIN_ARRAY -> {r.beginArray();while(r.hasNext())walk(r,depth+1,nodes,maxNodes);r.endArray();}
            case STRING,NUMBER -> r.nextString();case BOOLEAN -> r.nextBoolean();case NULL -> r.nextNull();
            default -> throw new IllegalArgumentException("JSON 值无效");
        }
    }
    private static void bounded(String text){
        int depth=0,nodes=0;char quote=0;boolean escape=false;
        for(char c:text.toCharArray()){
            if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
            if(c=='\'' || c=='"'){quote=c;continue;}
            if(c=='{' || c=='['){if(++depth>48)throw new IllegalArgumentException("NBT 嵌套不能超过 48 层");}
            if(c=='}' || c==']')depth--;
            if(c==':' || c==',')if(++nodes>2048)throw new IllegalArgumentException("NBT 节点过多");
        }
    }
    public static JsonObject merge(JsonObject source,int dataMode,String data,int displayMode,String display){
        var result=source.deepCopy();if(dataMode!=-1)put(result,"nbt",parse(dataMode,data));if(displayMode!=-1)put(result,"nbt-display",parse(displayMode,display));
        var parsed=RecipeNbt.read(result);
        if("input".equals(result.get("io-type").getAsString()))validPattern(parsed.pattern());
        return result;
    }
    private static void validPattern(NbtPattern p){
        if(p instanceof NbtPatternString str && !str.hasValidPattern())throw new IllegalArgumentException("输入 NBT 含无效正则表达式");
        if(p instanceof NbtCompoundPattern c)c.keys().values().forEach(RequirementNbtEditor::validPattern);
        if(p instanceof NbtListPattern l)l.entries().forEach(RequirementNbtEditor::validPattern);
    }
    private static void put(JsonObject result,String key,JsonElement value){if(value==null)result.remove(key);else result.add(key,value);}
    public static String text(JsonObject source,String key){return !source.has(key)?"":source.get(key).isJsonObject()?new GsonBuilder().setPrettyPrinting().create().toJson(source.get(key)):source.get(key).getAsString();}
    public static int mode(JsonObject source,String key){return source.has(key) && source.get(key).isJsonPrimitive()?1:0;}
}
