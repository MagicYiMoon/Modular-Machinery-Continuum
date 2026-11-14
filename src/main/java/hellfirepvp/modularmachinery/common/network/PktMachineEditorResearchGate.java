/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;
import hellfirepvp.modularmachinery.common.editor.ResearchGateEditor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Carried only by the existing permission/window/revision checked authoring envelope. */
public record PktMachineEditorResearchGate(String recipeId,int index,List<String> research,String newMachine,int ticks,String displayName){
    public static void encode(PktMachineEditorResearchGate p,FriendlyByteBuf b){
        b.writeUtf(p.recipeId,256);b.writeVarInt(p.index);if(p.research.size()>64)throw new IllegalArgumentException("Too many research IDs");b.writeVarInt(p.research.size());p.research.forEach(id->b.writeUtf(id,256));b.writeUtf(p.newMachine,256);b.writeVarInt(p.ticks);b.writeUtf(p.displayName,200);
    }
    public static PktMachineEditorResearchGate decode(FriendlyByteBuf b){String id=b.readUtf(256);int index=b.readVarInt(),n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Too many research IDs");var ids=new ArrayList<String>();for(int i=0;i<n;i++)ids.add(b.readUtf(256));return new PktMachineEditorResearchGate(id,index,List.copyOf(ids),b.readUtf(256),b.readVarInt(),b.readUtf(200));}
    static String apply(ServerPlayer player,PktMachineEditorResearchGate p){try{
        if(p.index< -1||p.index>=512)throw new IllegalArgumentException("研究需求位置无效");
        var clean=ResearchGateEditor.create(p.research);com.google.gson.JsonObject original=null;
        if(p.index>=0){var source=MachineReloadListener.getRecipeLoader().getRecipeSource(p.recipeId);
            if(source==null||p.index>=source.getAsJsonArray("requirements").size())throw new IllegalArgumentException("研究需求已失效，请重新打开编辑器");
            original=source.getAsJsonArray("requirements").get(p.index).getAsJsonObject();ResearchGateEditor.validate(original);
        }
        ResearchGateEditor.validateKnown(original,clean);
        return PktMachineEditorRequirementAdd.save(player,new PktMachineEditorRequirementAdd(p.recipeId,"","",false,1,1,p.newMachine,p.ticks,p.displayName),clean,p.index);
    }catch(RuntimeException e){return "研究门槛无效："+e.getMessage();}}
}
