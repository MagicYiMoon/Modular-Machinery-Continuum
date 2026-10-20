/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.geo.ControllerModelAnimationEvent;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.common.geo.MachineGeoModel;
import org.openzen.zencode.java.ZenCodeType;

import java.util.function.Consumer;

/** Static ZenCode modifiers corresponding to the source MachineModifier factory controls. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MachineModifier")
public final class MachineModifierFacade {
    private MachineModifierFacade() {}

    @ZenCodeType.Method
    public static boolean setMaxThreads(String machineName, int maxThreads) {
        if (maxThreads < 0) throw new IllegalArgumentException("maxThreads cannot be negative");
        applyFactoryChange(machineName, machine -> machine.setMaxThreads(maxThreads));
        return true;
    }

    @ZenCodeType.Method
    public static boolean addCoreThread(String machineName, FactoryRecipeThreadBuilder thread) {
        if (thread == null) throw new IllegalArgumentException("core thread cannot be null");
        String name = thread.getThreadName();
        var recipes = thread.getRecipeNames();
        var permanent = thread.getPermanentModifiers();
        var semiPermanent = thread.getSemiPermanentModifiers();
        applyFactoryChange(machineName, machine -> machine.addCoreThreadPreset(name, recipes, permanent, semiPermanent));
        return true;
    }

    /** Reload-safe action: client script replay must never mutate server machine definitions. */
    private static void applyFactoryChange(String machineName, Consumer<DynamicMachine> change) {
        com.blamejared.crafttweaker.api.CraftTweakerAPI.apply(
                new com.blamejared.crafttweaker.api.action.base.IRuntimeAction() {
                    @Override
                    public boolean shouldApplyOn(com.blamejared.crafttweaker.api.zencode.IScriptLoadSource source,
                                                 org.apache.logging.log4j.Logger logger) {
                        return source.id().equals(com.blamejared.crafttweaker.api.CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID);
                    }
                    @Override public void apply() { change.accept(machine(machineName)); }
                    @Override public String describe() { return "Configure Modular Machinery factory " + machineName; }
                    @Override public String systemName() { return "ModularMachinery"; }
                });
    }

    @ZenCodeType.Method
    public static boolean setMaxParallelism(String machineName, int maxParallelism) {
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism must be at least one");
        applyMachineChange(machineName, machine -> machine.setMaxParallelism(maxParallelism));
        return true;
    }

    @ZenCodeType.Method
    public static boolean setInternalParallelism(String machineName, int parallelism) {
        if (parallelism < 0) throw new IllegalArgumentException("parallelism cannot be negative");
        applyMachineChange(machineName, machine -> machine.setInternalParallelism(parallelism));
        return true;
    }

    @ZenCodeType.Method
    public static boolean addSmartInterfaceType(String machineName, SmartInterfaceTypeBuilder type) {
        if (type == null) throw new IllegalArgumentException("smart interface type cannot be null");
        var descriptor = type.value();
        applyMachineChange(machineName, machine -> {
            if (!machine.hasSmartInterfaceType(descriptor.getType())) machine.addSmartInterfaceType(descriptor);
        });
        return true;
    }

    /** Source-compatible client presentation modifier; the value is also carried in server snapshots. */
    @ZenCodeType.Method
    public static boolean setMachinePrefix(String machineName, String prefix) {
        if (prefix == null) throw new IllegalArgumentException("prefix cannot be null");
        applyMachineChange(machineName, machine -> machine.setPrefix(prefix));
        return true;
    }

    /**
     * Registers a GeckoLib model for a machine controller.  Paths use the normal
     * {@code namespace:path} resource syntax and are resolved on the client when rendered.
     */
    @ZenCodeType.Method
    public static boolean setMachineGeoModel(String machineName, String model, String texture, String animation) {
        machine(machineName);
        GeoMachineModelRegistry.registerModel(machineName, model, texture, animation);
        GeoMachineModelRegistry.bindMachine(machineName, machineName);
        return true;
    }

    /** Associates a machine with a model registered through {@code GeoMachineModel}. */
    @ZenCodeType.Method
    public static boolean setMachineGeoModel(String machineName, String modelName) {
        machine(machineName);
        GeoMachineModelRegistry.bindMachine(machineName, modelName);
        return true;
    }

    /** Source-compatible alias used by existing Modular Machinery scripts. */
    @ZenCodeType.Method
    public static boolean registerGeoMachineModel(String machineName, String model, String texture, String animation) {
        return setMachineGeoModel(machineName, model, texture, animation);
    }

    /** Adds one animation to the model's GeckoLib controller. */
    @ZenCodeType.Method
    public static boolean addGeoMachineAnimation(String machineName, String animationName, boolean loop) {
        MachineGeoModel model = GeoMachineModelRegistry.get(machineName);
        if (model == null) throw new IllegalArgumentException("no GeckoLib model registered for machine: " + machineName);
        model.addAnimation(animationName, loop);
        return true;
    }

    /**
     * Source-style callback entry point.  CraftTweaker supplies a ZenCode function which is
     * represented as a Java Consumer by CraftTweaker 14.
     */
    @ZenCodeType.Method
    public static boolean onControllerModelAnimation(String machineName,
                                                      Consumer<ControllerModelAnimationEvent> callback) {
        MachineGeoModel model = GeoMachineModelRegistry.get(machineName);
        if (model == null) throw new IllegalArgumentException("no GeckoLib model registered for machine: " + machineName);
        if (callback != null) GeoMachineModelRegistry.addAnimationHandler(machineName, callback);
        return true;
    }

    private static DynamicMachine machine(String machineName) {
        DynamicMachine machine = MachineReloadListener.getLoader().findByName(machineName);
        if (machine == null) throw new IllegalArgumentException("unknown Modular Machinery machine: " + machineName);
        return machine;
    }

    private static void applyMachineChange(String machineName, Consumer<DynamicMachine> change) {
        com.blamejared.crafttweaker.api.CraftTweakerAPI.apply(
                new com.blamejared.crafttweaker.api.action.base.IRuntimeAction() {
                    @Override public boolean shouldApplyOn(com.blamejared.crafttweaker.api.zencode.IScriptLoadSource source,
                                                           org.apache.logging.log4j.Logger logger) {
                        return source.id().equals(com.blamejared.crafttweaker.api.CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID);
                    }
                    @Override public void apply() { change.accept(machine(machineName)); }
                    @Override public String describe() { return "Configure Modular Machinery machine " + machineName; }
                    @Override public String systemName() { return "ModularMachinery"; }
                });
    }
}
