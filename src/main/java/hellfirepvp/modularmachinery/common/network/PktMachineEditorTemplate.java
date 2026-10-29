/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public record PktMachineEditorTemplate(String path,String digest,String machine,String json){
    public static void encode(PktMachineEditorTemplate p,FriendlyByteBuf b){b.writeUtf(p.path,256);b.writeUtf(p.digest,64);b.writeUtf(p.machine,256);b.writeUtf(p.json,32767);}
    public static PktMachineEditorTemplate decode(FriendlyByteBuf b){return new PktMachineEditorTemplate(b.readUtf(256),b.readUtf(64),b.readUtf(256),b.readUtf(32767));}
    static String apply(ServerPlayer player,PktMachineEditorTemplate p){try{AdapterTemplateEditor.save(p.path,p.digest,p.machine,RequirementNbtEditor.parseObject(p.json,32767,8192));return null;}catch(Exception e){return "模板保存失败："+e.getMessage();}}
}
