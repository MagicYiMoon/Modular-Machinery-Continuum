/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;
import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.geo.ControllerModelAnimationEvent;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.common.machine.ScriptMachineEventRegistry;
import org.openzen.zencode.java.ZenCodeType;
import java.util.function.Consumer;

@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MMEvents")
public final class MMEventsFacade {
    private MMEventsFacade() { }
    private static <T extends MachineEvent> boolean register(String machineName, Class<T> type, Consumer<T> callback) {
        if (callback == null) return false;
        CraftTweakerAPI.apply(new com.blamejared.crafttweaker.api.action.base.IRuntimeAction() {
            @Override public boolean shouldApplyOn(com.blamejared.crafttweaker.api.zencode.IScriptLoadSource source, org.apache.logging.log4j.Logger logger) {
                return source.id().equals(com.blamejared.crafttweaker.api.CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID);
            }
            @Override public void apply() { ScriptMachineEventRegistry.register(machineName, type, callback); }
            @Override public String describe() { return "Register Modular Machinery " + type.getSimpleName() + " handler for " + machineName; }
            @Override public String systemName() { return "ModularMachinery"; }
        });
        return true;
    }
    @ZenCodeType.Method public static boolean onStructureFormed(String machine, Consumer<MachineStructureFormedEvent> cb) { return register(machine, MachineStructureFormedEvent.class, cb); }
    @ZenCodeType.Method public static boolean onStructureUpdate(String machine, Consumer<MachineStructureUpdateEvent> cb) { return register(machine, MachineStructureUpdateEvent.class, cb); }
    @ZenCodeType.Method public static boolean onMachinePreTick(String machine, Consumer<MachineTickEvent> cb) { return cb != null && register(machine, MachineTickEvent.class, e -> { if (e.phase == Phase.START) cb.accept(e); }); }
    @ZenCodeType.Method public static boolean onMachinePostTick(String machine, Consumer<MachineTickEvent> cb) { return cb != null && register(machine, MachineTickEvent.class, e -> { if (e.phase == Phase.END) cb.accept(e); }); }
    @ZenCodeType.Method @Deprecated public static boolean onMachineTick(String machine, Consumer<MachineTickEvent> cb) { return onMachinePostTick(machine, cb); }
    @ZenCodeType.Method public static boolean onSmartInterfaceUpdate(String machine, Consumer<SmartInterfaceUpdateEvent> cb) { return register(machine, SmartInterfaceUpdateEvent.class, cb); }
    @ZenCodeType.Method public static boolean onControllerGUIRender(String machine, Consumer<ControllerGUIRenderEvent> cb) { return register(machine, ControllerGUIRenderEvent.class, cb); }
    @ZenCodeType.Method public static boolean onControllerModelGet(String machine, Consumer<ControllerModelGetEvent> cb) { return register(machine, ControllerModelGetEvent.class, cb); }
    @ZenCodeType.Method public static boolean onControllerModelAnimation(String machineName, Consumer<ControllerModelAnimationEvent> callback) {
        if (callback == null) return false;
        var model = GeoMachineModelRegistry.get(machineName);
        if (model == null) throw new IllegalArgumentException("no GeckoLib model registered for machine: " + machineName);
        GeoMachineModelRegistry.addAnimationHandler(machineName, callback); return true;
    }
    @ZenCodeType.Method public static RecipeCheckEvent castToRecipeCheckEvent(MachineEvent e) { return e instanceof RecipeCheckEvent x ? x : null; }
    @ZenCodeType.Method public static RecipeStartEvent castToRecipeStartEvent(MachineEvent e) { return e instanceof RecipeStartEvent x ? x : null; }
    @ZenCodeType.Method public static RecipeTickEvent castToRecipeTickEvent(MachineEvent e) { return e instanceof RecipeTickEvent x ? x : null; }
    @ZenCodeType.Method public static RecipeFailureEvent castToRecipeFailureEvent(MachineEvent e) { return e instanceof RecipeFailureEvent x ? x : null; }
    @ZenCodeType.Method public static RecipeFinishEvent castToRecipeFinishEvent(MachineEvent e) { return e instanceof RecipeFinishEvent x ? x : null; }
    @ZenCodeType.Method public static MachineStructureFormedEvent castToMachineStructureFormedEvent(MachineEvent e) { return e instanceof MachineStructureFormedEvent x ? x : null; }
    @ZenCodeType.Method public static MachineStructureUpdateEvent castToMachineStructureUpdateEvent(MachineEvent e) { return e instanceof MachineStructureUpdateEvent x ? x : null; }
    @ZenCodeType.Method public static MachineTickEvent castToMachineTickEvent(MachineEvent e) { return e instanceof MachineTickEvent x ? x : null; }
    @ZenCodeType.Method public static ControllerModelGetEvent castToControllerModelGetEvent(MachineEvent e) { return e instanceof ControllerModelGetEvent x ? x : null; }
    @ZenCodeType.Method public static SmartInterfaceUpdateEvent castToSmartInterfaceUpdateEvent(MachineEvent e) { return e instanceof SmartInterfaceUpdateEvent x ? x : null; }
    @ZenCodeType.Method public static FactoryRecipeStartEvent castToFactoryRecipeStartEvent(MachineEvent e) { return e instanceof FactoryRecipeStartEvent x ? x : null; }
    @ZenCodeType.Method public static FactoryRecipeTickEvent castToFactoryRecipeTickEvent(MachineEvent e) { return e instanceof FactoryRecipeTickEvent x ? x : null; }
    @ZenCodeType.Method public static FactoryRecipeFailureEvent castToFactoryRecipeFailureEvent(MachineEvent e) { return e instanceof FactoryRecipeFailureEvent x ? x : null; }
    @ZenCodeType.Method public static FactoryRecipeFinishEvent castToFactoryRecipeFinishEvent(MachineEvent e) { return e instanceof FactoryRecipeFinishEvent x ? x : null; }
}
