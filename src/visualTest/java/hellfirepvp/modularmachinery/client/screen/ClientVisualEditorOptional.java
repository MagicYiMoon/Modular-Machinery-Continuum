/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Disposable authoring fixture: real controls and saves with optional dependencies present or absent. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorOptional {
    private record Spec(String id,String label,boolean output) {}
    private record Step(String name,Runnable action) {}
    private static final Deque<Step> steps=new ArrayDeque<>();
    private static final List<Spec> specs=new ArrayList<>();
    private static int ticks,delay,waiting,captureDelay;private static String capture;
    private static boolean started,pnc,mana,ember,blood,aura;
    private static JsonArray templateExpected;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.editorOptionalVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(captureDelay>0&&--captureDelay==0)Screenshot.grab(mc.gameDirectory,capture,mc.getMainRenderTarget(),r->{});
        ticks++;
        try{
            if(ticks==20)mc.player.connection.sendCommand("modularmachinery gui");
            if(!started){if(ticks<40)return;if(!(mc.screen instanceof ScreenMachineEditor)){require(++waiting<600,"editor never opened");return;}started=true;prepare();}
            if(delay>0){delay--;return;}
            if(mc.screen instanceof ScreenMachineEditor editor&&editor.getMenu().saves().pending()){require(++waiting<1200,"save timeout");return;}
            Step step=steps.pollFirst();if(step==null){mc.stop();return;}
            try{step.action.run();}catch(Throwable failure){throw new AssertionError(step.name,failure);}delay=8;
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][optional-editor-client] FAIL tick {}",ticks,failure);mc.stop();}
    }
    private static void prepare(){
        pnc=loaded("pneumaticcraft");mana=loaded("botania");ember=loaded("embers");blood=loaded("bloodmagic");aura=loaded("naturesaura");
        var root=snapshot();
        for(var flag:Map.of("pneumaticEditorAvailable",pnc,"manaEditorAvailable",mana,"emberEditorAvailable",ember,"bloodMagicEditorAvailable",blood,"auraEditorAvailable",aura).entrySet())
            require(root.get(flag.getKey()).getAsBoolean()==flag.getValue(),"server availability mismatch: "+flag.getKey());
        if(aura)require(!root.getAsJsonArray("editorAuraTypes").isEmpty(),"server aura types missing");
        for(boolean out:List.of(false,true)){
            if(mana)specs.add(new Spec("mana","Botania 魔力",out));
            if(ember&&!out)specs.add(new Spec("ember","Embers 余烬",false));
            if(blood){specs.add(new Spec("lifeessence","生命源质 LP",out));specs.add(new Spec("will","恶魔意志",out));}
            if(aura)specs.add(new Spec("aura","自然灵气",out));
            if(pnc){specs.add(new Spec("compressed_air","压缩空气",out));if(!out)specs.add(new Spec("temperature","持续温度条件",false));}
        }
        add("ordinary input availability",()->availability(false));add("close input availability",()->press("取消"));
        add("ordinary output availability",()->availability(true));add("close output availability",()->press("取消"));
        for(int i=0;i<specs.size();i++){
            final int index=i;var spec=specs.get(i);
            add("normal add "+spec,()->{open(spec);shot("optional-normal-"+spec.id+(spec.output?"-out":"-in")+".png");});
            add("normal save "+spec,()->{press("保存");require(s().getMenu().saves().pending()&&!button("保存").active,"pending save not locked");});
            add("normal acknowledgement "+spec,()->{var reqs=owned().getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonArray("requirements");require(reqs.size()==index+2,"normal save missing or duplicated");check(reqs.get(reqs.size()-1).getAsJsonObject(),spec);});
        }
        add("normal source preservation",()->{var source=read("000_optional.json");require(source.get("authorNote").getAsString().equals("optional combo fixture"),"normal source metadata lost");});
        add("open import template",()->{press("导入模板");field("模板文件 ID").setValue("000_optional_import");press("追加需求（0）");});
        add("template input availability",()->availability(false));add("close template input availability",()->press("取消"));
        add("template output availability",()->availability(true));add("close template output availability",()->press("取消"));
        for(var spec:specs){
            add("template add "+spec,()->open(spec));
            add("template local save "+spec,()->{
                String before=s().getMenu().snapshot();press("保存");require(!s().getMenu().saves().pending()&&s().getMenu().snapshot().equals(before),"detached draft mutated server snapshot");
                var reqs=currentRequirements();check(reqs.get(reqs.size()-1).getAsJsonObject(),spec);
                require(!Files.exists(file("000_optional_import.adapter.json")),"detached template wrote a file");
            });
        }
        add("template draft resize",()->{
            var mc=Minecraft.getInstance();templateExpected=hellfirepvp.modularmachinery.common.network.TemplateRequirementDraft.clean(currentRequirements());
            mc.options.guiScale().set(2);mc.resizeDisplay();bounds();require(currentRequirements().size()==specs.size(),"template resize lost requirements");shot("optional-template-draft.png");
        });
        add("template return",()->press("完成追加需求"));
        add("template publish",()->{require(button("追加需求（"+specs.size()+"）").active,"template return lost draft");press("保存并生成");});
        add("template authoritative result",()->{
            var source=read("000_optional_import.adapter.json");require(source.getAsJsonArray("requirements").equals(templateExpected),"template saved source differs from draft");
            JsonObject entry=null;for(var e:snapshot().getAsJsonArray("adapterTemplates"))if(e.getAsJsonObject().get("path").getAsString().equals("000_optional_import.adapter.json"))entry=e.getAsJsonObject();
            require(entry!=null&&entry.get("expanded").getAsBoolean()&&entry.get("imported").getAsInt()>0,"optional template did not generate recipes");
            var generated=owned().getAsJsonArray("recipes").asList().stream().map(JsonElement::getAsJsonObject).filter(r->!r.get("name").getAsString().equals("000_optional")).findFirst().orElseThrow();
            for(var spec:specs)require(generated.getAsJsonArray("requirements").asList().stream().anyMatch(r->matches(r.getAsJsonObject(),spec)),"generated recipe lost "+spec);
            shot("optional-template-saved.png");
        });
        add("result",()->Modularmachinery.LOG.info("[MMC][optional-editor-client] PASS: PNC={}, Botania={}, Embers={}, BloodMagic={}, Aura={}; {} input/output families through actual normal and detached-template widgets, rate/bounds, availability and direction gates, pending guards, authoritative saves/reloads, metadata, resize and generated recipe requirements",pnc,mana,ember,blood,aura,specs.size()));
        add("exit",()->Minecraft.getInstance().stop());
    }
    private static void availability(boolean output){
        invoke("openTypePicker",boolean.class,output);
        require(visible("气动／温度")==pnc,"PNC entry availability");
        require(visible("魔法资源")== (mana||blood||aura||ember&&!output),"resource entry availability");
        if(visible("魔法资源")){
            press("魔法资源");
            for(var option:Map.of("Botania 魔力",mana,"Embers 余烬",ember&&!output,"生命源质 LP",blood,"恶魔意志",blood,"自然灵气",aura).entrySet())require(visible(option.getKey())==option.getValue(),"resource tab availability: "+option.getKey());
            bounds();
        }
        shot(output?"optional-output-tabs.png":"optional-input-tabs.png");
    }
    private static void open(Spec spec){
        invoke("openTypePicker",boolean.class,spec.output);
        if(spec.id.equals("compressed_air")||spec.id.equals("temperature")){
            press("气动／温度");require(visible("持续温度条件")==!spec.output,"temperature output exposed");
            if(spec.id.equals("temperature")){press("持续温度条件");field("最低温度 K").setValue("300");field("最高温度 K").setValue("900");}
            else{field("空气用量").setValue("19");if(!spec.output)field("压力阈值 bar").setValue("-0.5");else require(!visibleField("压力阈值 bar"),"air output pressure exposed");pressPrefix("单次：");}
        }else{
            press("魔法资源");if(button(spec.label).active)press(spec.label);
            String title=switch(spec.id){case "mana"->"魔力用量";case "ember"->"余烬用量";case "lifeessence"->"LP 用量";case "will"->"意志用量";default->"灵气用量";};
            field(title).setValue(spec.id.equals("ember")||spec.id.equals("will")?"2.125":"17");
            if(spec.id.equals("mana")||spec.id.equals("lifeessence"))pressPrefix("单次：");
            if(spec.id.equals("will")){pressPrefix("意志类型：");field("资源阈值").setValue(spec.output?"50":"7.5");}
            if(spec.id.equals("aura"))field("资源阈值").setValue(spec.output?"-100":"-200");
        }
        bounds();require(button("保存").active,"valid optional requirement cannot save");
    }
    private static boolean matches(JsonObject req,Spec spec){return req.get("type").getAsString().equals("modularmachinery:"+spec.id)&&req.get("io-type").getAsString().equals(spec.output?"output":"input");}
    private static void check(JsonObject req,Spec spec){
        require(matches(req,spec),"wrong resource direction/type: "+spec);
        if(spec.id.equals("temperature")){require(req.get("min").getAsInt()==300&&req.get("max").getAsInt()==900,"temperature bounds lost");return;}
        require(req.get(spec.id.equals("compressed_air")?"air":spec.id.equals("ember")?"ember":"amount").getAsDouble()==(spec.id.equals("ember")||spec.id.equals("will")?2.125:spec.id.equals("compressed_air")?19:17),"resource amount lost");
        if(List.of("mana","lifeessence","compressed_air").contains(spec.id))require(req.get("perTick").getAsBoolean(),"resource rate lost");
        if(spec.id.equals("will"))require(req.get("will-type").getAsString().equals("corrosive")&&req.get(spec.output?"max":"min").getAsDouble()==(spec.output?50:7.5),"will kind/bounds lost");
        if(spec.id.equals("aura"))require(req.get(spec.output?"max-aura":"min-aura").getAsInt()==(spec.output?-100:-200),"aura signed bounds lost");
    }
    private static boolean loaded(String id){return ModList.get().isLoaded(id);}
    private static void add(String name,Runnable action){steps.addLast(new Step(name,action));}
    private static ScreenMachineEditor s(){return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static JsonObject snapshot(){return JsonParser.parseString(s().getMenu().snapshot()).getAsJsonObject();}
    private static JsonObject owned(){for(var m:snapshot().getAsJsonArray("machines"))if(m.getAsJsonObject().get("name").getAsString().equals("000_optional"))return m.getAsJsonObject();throw new AssertionError("fixture missing");}
    private static JsonArray currentRequirements(){return (JsonArray)invoke("recipeRequirements",null,null);}
    private static Object invoke(String name,Class<?> type,Object value){try{var method=type==null?ScreenMachineEditor.class.getDeclaredMethod(name):ScreenMachineEditor.class.getDeclaredMethod(name,type);method.setAccessible(true);return type==null?method.invoke(s()):method.invoke(s(),value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Button button(String text){return s().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("missing button: "+text));}
    private static boolean visible(String text){return s().children().stream().anyMatch(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals(text));}
    private static EditBox field(String text){return s().children().stream().filter(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(EditBox)w).findFirst().orElseThrow(()->new AssertionError("missing field: "+text));}
    private static boolean visibleField(String text){return s().children().stream().anyMatch(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals(text));}
    private static void pressPrefix(String prefix){var b=s().children().stream().filter(w->w instanceof Button btn&&btn.visible&&btn.getMessage().getString().startsWith(prefix)).map(w->(Button)w).findFirst().orElseThrow();press(b);}
    private static void press(String text){press(button(text));}private static void press(Button b){require(b.active,"inactive button: "+b.getMessage().getString());s().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);s().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void bounds(){for(var child:s().children())if(child instanceof AbstractWidget w&&w.visible)require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=s().width&&w.getY()+w.getHeight()<=s().height,"widget outside viewport: "+w.getMessage().getString());}
    private static Path file(String name){return hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(name);}
    private static JsonObject read(String name){try{return JsonParser.parseString(Files.readString(file(name))).getAsJsonObject();}catch(Exception e){throw new IllegalStateException(e);}}
    private static void shot(String name){capture=name;captureDelay=3;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
