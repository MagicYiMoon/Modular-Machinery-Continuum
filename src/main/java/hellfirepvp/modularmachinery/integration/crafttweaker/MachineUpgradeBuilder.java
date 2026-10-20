/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.SimpleMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.UpgradeType;
import org.openzen.zencode.java.ZenCodeType;
import net.minecraft.nbt.CompoundTag;
import com.blamejared.crafttweaker.api.data.IData;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.event.recipe.*;

import java.util.HashSet;
import java.util.Set;

/**
 * ZenCode facade for the upstream MachineUpgradeBuilder. Declarative modifiers and duration use the same data path
 * as JSON and shipped upgrades; upgrade-scoped event callbacks are retained as live server-thread consumers and are
 * dispatched before ordinary machine callbacks.
 */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MachineUpgradeBuilder")
public final class MachineUpgradeBuilder {
    private final SimpleMachineUpgrade upgrade;
    private final Set<String> modifierKeys = new HashSet<>();

    private MachineUpgradeBuilder(SimpleMachineUpgrade upgrade) {
        this.upgrade = upgrade;
    }

    @ZenCodeType.Method
    public static MachineUpgradeBuilder newBuilder(String name, String localizedName,
                                                    float level, int maxStack) {
        if (RegistryMachineUpgrade.getPrototype(name) != null) {
            throw new IllegalArgumentException("upgrade '" + name + "' is already registered");
        }
        if (maxStack < 1) throw new IllegalArgumentException("maxStack must be at least one");
        return new MachineUpgradeBuilder(new SimpleMachineUpgrade(
                new UpgradeType(name, localizedName, level, maxStack)));
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addDescriptions(String... descriptions) {
        if (descriptions != null) {
            for (String description : descriptions) if (description != null) upgrade.describe(description);
        }
        return this;
    }

    /** Adds the source-compatible dynamic description shown on the upgrade bus detail screen. */
    @ZenCodeType.Method
    public MachineUpgradeBuilder setBusGUIDescriptionHandler(MachineUpgradeDescriptionHandler handler) {
        if (handler == null) throw new IllegalArgumentException("bus GUI description handler cannot be null");
        upgrade.setBusGUIDescriptionHandler(handler::apply);
        return this;
    }

    /** Adds fixed lines shown on the upgrade bus detail screen. */
    @ZenCodeType.Method
    public MachineUpgradeBuilder addBusGUIDescriptions(String... descriptions) {
        if (descriptions != null) {
            for (String description : descriptions) if (description != null) upgrade.describeBus(description);
        }
        return this;
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addCompatibleMachines(String... machineNames) {
        addMachines(machineNames, true);
        return this;
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addIncompatibleMachines(String... machineNames) {
        addMachines(machineNames, false);
        return this;
    }

    private void addMachines(String[] names, boolean compatible) {
        if (names == null) return;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            var machine = MachineReloadListener.getLoader().findByName(name.contains(":")
                    ? name.substring(name.indexOf(':') + 1) : name);
            if (machine == null) {
                throw new IllegalArgumentException("unknown Modular Machinery machine: " + name);
            }
            if (compatible) upgrade.getType().addCompatibleMachine(machine.getRegistryName());
            else upgrade.getType().addIncompatibleMachine(machine.getRegistryName());
        }
    }

    /** Adds the same declarative modifier used by machine JSON. */
    @ZenCodeType.Method
    public MachineUpgradeBuilder addModifier(boolean stackAble, String modifierKey,
                                              RecipeModifier modifier) {
        if (modifier == null) throw new IllegalArgumentException("modifier cannot be null");
        // modifierKey is retained for source compatibility and diagnostics. The port's modifier list is data,
        // so duplicate keys are harmless and the effect is applied once per declared modifier.
        if (modifierKey == null || modifierKey.isBlank()) {
            throw new IllegalArgumentException("modifier key cannot be blank");
        }
        if (!modifierKeys.add(modifierKey)) return this;
        upgrade.scale(modifierKey, modifier.getTarget(), modifier.getIOTarget(), modifier.getOperation(),
                modifier.getModifier(), modifier.isChance(), stackAble);
        return this;
    }

    /** Primitive overload convenient in ZenCode scripts without constructing a Java RecipeModifier. */
    @ZenCodeType.Method
    public MachineUpgradeBuilder addModifier(boolean stackAble, String modifierKey, String target,
                                              String io, float multiplier, int operation, boolean affectChance) {
        IOType ioType = "output".equalsIgnoreCase(io) ? IOType.OUTPUT : IOType.INPUT;
        if (!"input".equalsIgnoreCase(io) && !"output".equalsIgnoreCase(io)) {
            throw new IllegalArgumentException("modifier io must be input or output");
        }
        return addModifier(stackAble, modifierKey,
                new RecipeModifier(target, ioType, multiplier, operation, affectChance));
    }

    // Upgrade-scoped callbacks mirror the source MachineUpgradeBuilder. The callback receives the concrete event
    // as MachineEvent; MMEvents.castTo* methods provide the source-compatible typed conversion in ZenCode.
    @ZenCodeType.Method @Deprecated
    public MachineUpgradeBuilder addRecipeCheckHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeCheckEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addPreRecipeCheckHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeCheckEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.START);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addPostRecipeCheckHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeCheckEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.END);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addRecipeStartHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeStartEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addRecipePreTickHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeTickEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.START);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addRecipePostTickHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeTickEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.END);
    }

    @ZenCodeType.Method @Deprecated
    public MachineUpgradeBuilder addRecipeTickHandler(UpgradeEventHandler handler) {
        return addRecipePostTickHandler(handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addRecipeFailureHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeFailureEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addRecipeFinishHandler(UpgradeEventHandler handler) {
        return addEventHandler(RecipeFinishEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addFactoryRecipeStartHandler(UpgradeEventHandler handler) {
        return addEventHandler(FactoryRecipeStartEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addFactoryRecipePreTickHandler(UpgradeEventHandler handler) {
        return addEventHandler(FactoryRecipeTickEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.START);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addFactoryRecipePostTickHandler(UpgradeEventHandler handler) {
        return addEventHandler(FactoryRecipeTickEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.END);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addFactoryRecipeFailureHandler(UpgradeEventHandler handler) {
        return addEventHandler(FactoryRecipeFailureEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addFactoryRecipeFinishHandler(UpgradeEventHandler handler) {
        return addEventHandler(FactoryRecipeFinishEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addMachinePreTickHandler(UpgradeEventHandler handler) {
        return addEventHandler(MachineTickEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.START);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addMachinePostTickHandler(UpgradeEventHandler handler) {
        return addEventHandler(MachineTickEvent.class, handler,
                event -> event.phase == hellfirepvp.modularmachinery.common.event.machine.Phase.END);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addStructureFormedHandler(UpgradeEventHandler handler) {
        return addEventHandler(MachineStructureFormedEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addStructureUpdateHandler(UpgradeEventHandler handler) {
        return addEventHandler(MachineStructureUpdateEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addControllerGUIRenderHandler(UpgradeEventHandler handler) {
        return addEventHandler(ControllerGUIRenderEvent.class, handler);
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder addSmartInterfaceUpdateHandler(UpgradeEventHandler handler) {
        return addEventHandler(SmartInterfaceUpdateEvent.class, handler);
    }

    private <E extends MachineEvent> MachineUpgradeBuilder addEventHandler(Class<E> eventType,
                                                                             UpgradeEventHandler handler) {
        if (handler == null) throw new IllegalArgumentException("upgrade event callback cannot be null");
        upgrade.addEventHandler(eventType, (event, instance) -> handler.handle(event, instance));
        return this;
    }

    private <E extends MachineEvent> MachineUpgradeBuilder addEventHandler(Class<E> eventType,
                                                                             UpgradeEventHandler handler,
                                                                             java.util.function.Predicate<E> filter) {
        if (handler == null) throw new IllegalArgumentException("upgrade event callback cannot be null");
        upgrade.addEventHandler(eventType, (event, instance) -> {
            if (filter.test(event)) handler.handle(event, instance);
        });
        return this;
    }

    @ZenCodeType.Method
    public MachineUpgradeBuilder duration(float multiplier) {
        if (!(multiplier > 0.0F)) throw new IllegalArgumentException("duration multiplier must be positive");
        upgrade.duration(multiplier);
        return this;
    }

    /** Stores serializable upgrade data on the upgrade bus, matching upstream SimpleMachineUpgrade.customData. */
    @ZenCodeType.Method
    public MachineUpgradeBuilder setCustomData(IData data) {
        if (data == null || !(data.getInternal() instanceof CompoundTag tag))
            throw new IllegalArgumentException("customData must be a compound IData value");
        upgrade.setCustomData(tag);
        return this;
    }

    @ZenCodeType.Method
    public void buildAndRegister() {
        RegistryMachineUpgrade.registerPrototype(upgrade);
    }

    @ZenCodeType.Method
    public SimpleMachineUpgrade getUpgrade() {
        return upgrade;
    }
}
