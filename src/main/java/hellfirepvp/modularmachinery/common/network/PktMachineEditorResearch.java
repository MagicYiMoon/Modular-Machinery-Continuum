/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.HyperNetResearchEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;

/** Only reachable through the existing permission/window/revision checked save envelope. */
public record PktMachineEditorResearch(String originalId,String catalogRevision,String definition) {
    public static void encode(PktMachineEditorResearch p,FriendlyByteBuf b){b.writeUtf(p.originalId,256);b.writeUtf(p.catalogRevision,64);byte[] bytes=p.definition.getBytes(StandardCharsets.UTF_8);if(bytes.length>256*1024)throw new IllegalArgumentException("Research too large");b.writeByteArray(bytes);}
    public static PktMachineEditorResearch decode(FriendlyByteBuf b){return new PktMachineEditorResearch(b.readUtf(256),b.readUtf(64),new String(b.readByteArray(256*1024),StandardCharsets.UTF_8));}
    static String apply(ServerPlayer p,PktMachineEditorResearch edit){try{HyperNetResearchEditor.save(edit.originalId,edit.catalogRevision,JsonParser.parseString(edit.definition).getAsJsonObject());return null;}catch(Exception e){return "研究保存失败："+e.getMessage();}}
}
