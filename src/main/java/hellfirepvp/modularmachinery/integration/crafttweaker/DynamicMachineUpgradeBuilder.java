/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.data.IData;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.SimpleDynamicMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.UpgradeType;
import net.minecraft.nbt.CompoundTag;
import org.openzen.zencode.java.ZenCodeType;

/** CraftTweaker facade for source-compatible dynamic upgrades. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.DynamicMachineUpgradeBuilder")
public final class DynamicMachineUpgradeBuilder {
    private final SimpleDynamicMachineUpgrade upgrade;

    private DynamicMachineUpgradeBuilder(SimpleDynamicMachineUpgrade upgrade) {
        this.upgrade = upgrade;
    }

    @ZenCodeType.Method
    public static DynamicMachineUpgradeBuilder newBuilder(String name, String localizedName,
                                                           float level, int maxStack) {
        if (RegistryMachineUpgrade.getPrototype(name) != null) {
            throw new IllegalArgumentException("upgrade '" + name + "' is already registered");
        }
        if (maxStack < 1) throw new IllegalArgumentException("maxStack must be at least one");
        return new DynamicMachineUpgradeBuilder(new SimpleDynamicMachineUpgrade(
                new UpgradeType(name, localizedName, level, maxStack)));
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder addDescriptions(String... descriptions) {
        if (descriptions != null) for (String description : descriptions) upgrade.describe(description);
        return this;
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder setDescriptionHandler(DynamicUpgradeDescriptionHandler handler) {
        if (handler == null) throw new IllegalArgumentException("description handler cannot be null");
        upgrade.setDescriptionHandler(handler::apply);
        return this;
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder setBusGUIDescriptionHandler(DynamicUpgradeDescriptionHandler handler) {
        if (handler == null) throw new IllegalArgumentException("bus GUI description handler cannot be null");
        upgrade.setBusGUIDescriptionHandler(handler::apply);
        return this;
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder addBusGUIDescriptions(String... descriptions) {
        if (descriptions != null) for (String description : descriptions) upgrade.describeBus(description);
        return this;
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder addCompatibleMachines(String... machineNames) {
        addMachines(machineNames, true);
        return this;
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder addIncompatibleMachines(String... machineNames) {
        addMachines(machineNames, false);
        return this;
    }

    private void addMachines(String[] names, boolean compatible) {
        if (names == null) return;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            var machine = MachineReloadListener.getLoader().findByName(name.contains(":")
                    ? name.substring(name.indexOf(':') + 1) : name);
            if (machine == null) throw new IllegalArgumentException("unknown Modular Machinery machine: " + name);
            if (compatible) upgrade.getType().addCompatibleMachine(machine.getRegistryName());
            else upgrade.getType().addIncompatibleMachine(machine.getRegistryName());
        }
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder setCustomData(IData data) {
        if (data == null || !(data.getInternal() instanceof CompoundTag tag)) {
            throw new IllegalArgumentException("customData must be a compound IData value");
        }
        upgrade.setCustomData(tag);
        return this;
    }

    @ZenCodeType.Method
    public DynamicMachineUpgradeBuilder addRecipeCheckHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeCheckEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addPreRecipeCheckHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeCheckEvent.class, handler, e -> e.phase == Phase.START);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addPostRecipeCheckHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeCheckEvent.class, handler, e -> e.phase == Phase.END);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addRecipeStartHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeStartEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addRecipePreTickHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeTickEvent.class, handler, e -> e.phase == Phase.START);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addRecipePostTickHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeTickEvent.class, handler, e -> e.phase == Phase.END);
    }
    @ZenCodeType.Method @Deprecated public DynamicMachineUpgradeBuilder addRecipeTickHandler(UpgradeEventHandler handler) {
        return addRecipePostTickHandler(handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addRecipeFailureHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeFailureEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addRecipeFinishHandler(UpgradeEventHandler handler) {
        return addHandler(RecipeFinishEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addFactoryRecipeStartHandler(UpgradeEventHandler handler) {
        return addHandler(FactoryRecipeStartEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addFactoryRecipePreTickHandler(UpgradeEventHandler handler) {
        return addHandler(FactoryRecipeTickEvent.class, handler, e -> e.phase == Phase.START);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addFactoryRecipePostTickHandler(UpgradeEventHandler handler) {
        return addHandler(FactoryRecipeTickEvent.class, handler, e -> e.phase == Phase.END);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addFactoryRecipeFailureHandler(UpgradeEventHandler handler) {
        return addHandler(FactoryRecipeFailureEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addFactoryRecipeFinishHandler(UpgradeEventHandler handler) {
        return addHandler(FactoryRecipeFinishEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addMachinePreTickHandler(UpgradeEventHandler handler) {
        return addHandler(MachineTickEvent.class, handler, e -> e.phase == Phase.START);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addMachinePostTickHandler(UpgradeEventHandler handler) {
        return addHandler(MachineTickEvent.class, handler, e -> e.phase == Phase.END);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addStructureFormedHandler(UpgradeEventHandler handler) {
        return addHandler(MachineStructureFormedEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addStructureUpdateHandler(UpgradeEventHandler handler) {
        return addHandler(MachineStructureUpdateEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addControllerGUIRenderHandler(UpgradeEventHandler handler) {
        return addHandler(ControllerGUIRenderEvent.class, handler);
    }
    @ZenCodeType.Method public DynamicMachineUpgradeBuilder addSmartInterfaceUpdateHandler(UpgradeEventHandler handler) {
        return addHandler(SmartInterfaceUpdateEvent.class, handler);
    }

    @ZenCodeType.Method
    public void buildAndRegister() {
        RegistryMachineUpgrade.registerPrototype(upgrade);
    }

    @ZenCodeType.Method
    public SimpleDynamicMachineUpgrade getUpgrade() {
        return upgrade;
    }

    private <E extends MachineEvent> DynamicMachineUpgradeBuilder addHandler(Class<E> type, UpgradeEventHandler handler) {
        if (handler == null) throw new IllegalArgumentException("upgrade event callback cannot be null");
        upgrade.addEventHandler(type, (event, instance) -> handler.handle(event, instance));
        return this;
    }

    private <E extends MachineEvent> DynamicMachineUpgradeBuilder addHandler(Class<E> type, UpgradeEventHandler handler,
                                                                               java.util.function.Predicate<E> filter) {
        if (handler == null) throw new IllegalArgumentException("upgrade event callback cannot be null");
        upgrade.addEventHandler(type, (event, instance) -> {
            if (filter.test(event)) handler.handle(event, instance);
        });
        return this;
    }
}
