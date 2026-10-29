/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Optional-mod absence plus actual envelope, disk, reload and runtime snapshot authoring checks. */
public final class PneumaticEditorRegressionChecks {
    private PneumaticEditorRegressionChecks() {}
    public static void run(ServerLevel level) throws Exception {
        String id = "pneumatic_editor_" + UUID.randomUUID().toString().replace("-", "");
        var dir = ModDataHolder.INSTANCE.getRecipeDirectory().toPath(); var file = dir.resolve(id + ".json");
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PNCEditor")) {
            @Override public boolean hasPermissions(int n) { return true; }
        };
        var old = player.containerMenu;
        try {
            boolean installed = net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft");
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\",\"selector-tag\":\"air\"}]}");
            MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(41, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            require(JsonParser.parseString(menu.snapshot()).getAsJsonObject().get("pneumaticEditorAvailable").getAsBoolean() == installed, "availability follows server");
            var first = new PktMachineEditorPneumatic(id, -1, PneumaticEditor.air(false, 0, -0.5F, true).toString(), id, 80, "气动编辑测试");
            save(player, first, installed);
            if (!installed) {
                require(!Files.exists(file), "absent PNC cannot write unusable recipe");
                validateBounds();
                Modularmachinery.LOG.info("[MMC][pneumatic-editor-regression] PASS: no-PNC classloading, capability flag, wire/server rejection without file and mod-neutral bounds");
                return;
            }
            require(req(read(file),0).equals(PneumaticEditor.air(false,0,-0.5F,true)), "new pressure-only recipe");
            var source=read(file);source.addProperty("authorNote","keep recipe");
            var air=req(source,0);air.addProperty("selector-tag","air");air.addProperty("parallelizeUnaffected",true);
            air.addProperty("triggerTime",7);air.addProperty("triggerRepeatable",true);air.addProperty("authorNote","keep air");
            Files.writeString(file,source.toString());reload(menu);
            var edited=PneumaticEditor.air(false,100,2,false);
            save(player,edit(id,0,edited),true);
            source.getAsJsonArray("requirements").set(0,PneumaticEditor.merge(air,edited));
            require(read(file).equals(source),"only air fields change; selector/batch/trigger/unknown metadata survive");
            checkLoaded(id,0,edited);
            for (int policy : List.of(RequirementBatchEditor.FOLLOW,RequirementBatchEditor.EXEMPT)) {
                save(player,new PktMachineEditorRequirementBatch(id,0,policy),true);
                var loaded=MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0);
                require(RequirementBatchEditor.supports(loaded) && ((hellfirepvp.modularmachinery.common.crafting.Requirement.Parallelizable)loaded).isParallelizeUnaffected()==(policy==RequirementBatchEditor.EXEMPT),"air batch panel policy reaches runtime");
            }
            // Legacy aliases force rate even if false; editing canonicalizes the type to really disable it.
            for(String alias:List.of("modularmachinery:compressed_air_pertick","gugu-utils:compressed_air_pertick","gugu-utils:compressed_air")) {
                source=read(file);req(source,0).addProperty("type",alias);req(source,0).addProperty("perTick",true);
                Files.writeString(file,source.toString());reload(menu);
                save(player,edit(id,0,edited),true);checkLoaded(id,0,edited);
            }
            save(player,edit(id,0,PneumaticEditor.air(false,Integer.MAX_VALUE,-1,true)),true);
            checkLoaded(id,0,PneumaticEditor.air(false,Integer.MAX_VALUE,-1,true));
            save(player,edit(id,-1,PneumaticEditor.air(true,100,0,false)),true);
            save(player,edit(id,1,PneumaticEditor.air(true,20,0,true)),true);
            checkLoaded(id,1,PneumaticEditor.air(true,20,0,true));
            save(player,edit(id,-1,PneumaticEditor.temperature(400,600)),true);
            checkLoaded(id,2,PneumaticEditor.temperature(400,600));
            save(player,new PktMachineEditorRequirementBatch(id,2,RequirementBatchEditor.EXEMPT),false);
            source=read(file);req(source,2).remove("min");Files.writeString(file,source.toString());reload(menu);
            save(player,edit(id,2,PneumaticEditor.temperature(500,500)),true);checkLoaded(id,2,PneumaticEditor.temperature(500,500));
            source=read(file);req(source,2).remove("max");Files.writeString(file,source.toString());reload(menu);
            save(player,edit(id,2,PneumaticEditor.temperature(0,Integer.MAX_VALUE)),true);checkLoaded(id,2,PneumaticEditor.temperature(0,Integer.MAX_VALUE));
            String before=Files.readString(file);
            validateBounds();
            for(String key:List.of("air","pressure","perTick","io-type","type")) for(String invalid:List.of("null","[]","\"1\"")) {
                var bad=edited.deepCopy();bad.add(key,JsonParser.parseString(invalid));save(player,edit(id,0,bad),false);
            }
            for (String key : List.of("air", "perTick", "pressure")) {
                var invalid=edited.deepCopy();invalid.add(key,JsonParser.parseString(key.equals("air") ? "1.5" : key.equals("perTick") ? "1" : "1e100"));
                save(player,edit(id,0,invalid),false);
            }
            var bad=edited.deepCopy();bad.addProperty("io-type","output");save(player,edit(id,0,bad),false);
            save(player,edit(id,0,PneumaticEditor.temperature(1,2)),false);
            save(player,edit(id,2,edited),false);
            for(int index:List.of(-2,3,512))save(player,edit(id,index,edited),false);
            for(String payload:List.of("{}","[]","{"))save(player,new PktMachineEditorPneumatic(id,0,payload,"",20,""),false);
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"PNCDenied")) {
                @Override public boolean hasPermissions(int n){return false;}
            };
            denied.containerMenu=new MenuMachineEditor(41,denied.getInventory(),menu.snapshot());save(denied,edit(id,0,edited),false);
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(42,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(id,0,edited))).result().success(),"wrong window");
            require(!PktMachineEditorRequest.process(player,new PktMachineEditorRequest(41,UUID.randomUUID(),"stale",edit(id,0,edited))).result().success(),"stale revision");
            require(Files.readString(file).equals(before),"invalid/permission/window/revision writes leave source intact");
            source=read(file);source.addProperty("externalEdit",true);Files.writeString(file,source.toString());
            save(player,edit(id,0,edited),false);save(player,edit(id,-1,edited),false);
            require(read(file).equals(source),"disk-only change protects both edit and append");reload(menu);
            String readonly=MachineReloadListener.getRecipeLoader().getRecipeNames().stream().filter(n->!MachineReloadListener.getRecipeLoader().isSourceEditable(n)).findFirst().orElseThrow();
            save(player,edit(readonly,-1,edited),false);
            var request=new PktMachineEditorRequest(41,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),edit(id,0,edited));
            require(PktMachineEditorRequest.process(player,request).result().success(),"initial request");
            require(!PktMachineEditorRequest.process(player,request).result().success(),"duplicate request");
            Modularmachinery.LOG.info("[MMC][pneumatic-editor-regression] PASS: new/append/replace, actual bounded wire and reload, air input/output one-shot/rate/zero/vacuum/int-max, Kelvin ranges/exact/unbounded defaults, alias rate disable, metadata, invalid/family/IO/stale/readonly/permission/window/revision/dedupe rejection");
        } finally {
            player.containerMenu=old;Files.deleteIfExists(file);Files.deleteIfExists(machineFile);MachineReloadListener.reloadNow();
        }
    }
    private static void validateBounds() {
        for(String json:List.of(
                "{\"type\":\"modularmachinery:temperature\",\"io-type\":\"output\",\"min\":0}",
                "{\"type\":\"modularmachinery:temperature\",\"io-type\":\"input\",\"min\":0.5}",
                "{\"type\":\"modularmachinery:temperature\",\"io-type\":\"input\",\"min\":-1}",
                "{\"type\":\"modularmachinery:temperature\",\"io-type\":\"input\",\"min\":2,\"max\":1}",
                "{\"type\":\"modularmachinery:temperature\",\"io-type\":\"input\",\"max\":2147483648}")) {
            try{PneumaticEditor.validate(JsonParser.parseString(json).getAsJsonObject());throw new AssertionError("bad temp accepted");}catch(IllegalArgumentException expected){}
        }
        for(float pressure:new float[]{-1.01F,25.01F,Float.NaN,Float.POSITIVE_INFINITY}) {
            try{PneumaticEditor.air(false,0,pressure,false);throw new AssertionError("bad pressure accepted");}catch(IllegalArgumentException expected){}
        }
        require(PneumaticEditor.validate(PneumaticEditor.air(false,0,-1,true)).get("air").getAsInt()==0,"zero/vacuum accepted");
    }
    private static void checkLoaded(String id,int i,JsonObject expected) {
        var loader=MachineReloadListener.getRecipeLoader();var recipe=loader.getRecipe(id);
        var snapshot=hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec.encode(recipe,Map.of());
        require(PneumaticEditor.validate(snapshot.getAsJsonArray("requirements").get(i).getAsJsonObject()).equals(expected),"loaded runtime and snapshot semantics");
        var roundtrip=hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec.decode(snapshot);
        require(roundtrip.requirements().get(i).getClass()==recipe.requirements().get(i).getClass(),"air rate runtime class survives wire");
    }
    private static PktMachineEditorPneumatic edit(String id,int i,JsonObject value){return new PktMachineEditorPneumatic(id,i,value.toString(),"",20,"");}
    private static JsonObject read(Path f)throws Exception{return JsonParser.parseString(Files.readString(f)).getAsJsonObject();}
    private static JsonObject req(JsonObject j,int i){return j.getAsJsonArray("requirements").get(i).getAsJsonObject();}
    private static void reload(MenuMachineEditor menu){MachineReloadListener.reloadNow();menu.setSnapshot(PktMachineEditorRequest.snapshot());}
    private static void save(FakePlayer player,Object mutation,boolean success){
        var menu=(MenuMachineEditor)player.containerMenu;var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu.containerId,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),mutation),buffer);
            var reply=PktMachineEditorRequest.process(player,PktMachineEditorRequest.decode(buffer));
            require(reply.result().success()==success,"unexpected save result: "+reply.result().message());
            if(success)require(reply.snapshot()!=null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()),"authoritative save acknowledgement");
        }finally{buffer.release();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError("PNC editor: "+message);}
}
