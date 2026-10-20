/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.SimpleMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.SimpleDynamicMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.DynamicMachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.UpgradeType;
import hellfirepvp.modularmachinery.common.event.machine.MachineTickEvent;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression checks for the CraftTweaker upgrade builder's declarative replacement. */
public final class UpgradeRegressionChecks {
    private UpgradeRegressionChecks() {}

    public static void run() {
        String name = "regression_ct_upgrade";
        UpgradeType type = new UpgradeType(name, "Regression Upgrade", 1.0F, 3);
        type.addCompatibleMachine("regression_machine");
        require(type.isCompatible("regression_machine") && !type.isCompatible("other_machine"),
                "upgrade compatible-machine allow-list is not enforced");
        SimpleMachineUpgrade upgrade = SimpleMachineUpgrade.of(type)
                .scale("modularmachinery:item", IOType.INPUT, RecipeModifier.OPERATION_MULTIPLY,
                        2.0F, false, true)
                .duration(0.5F);
        upgrade.setBusGUIDescriptionHandler(instance -> new String[]{
                "mode=" + instance.getCustomData().getString("mode")});
        CompoundTag custom = new CompoundTag();
        custom.putString("mode", "boost");
        upgrade.setCustomData(custom);
        require(upgrade.copy().getCustomData().getString("mode").equals("boost"),
                "upgrade copy lost customData");
        require(upgrade.copy().getBusGUIDescriptions().contains("mode=boost"),
                "upgrade copy lost dynamic bus description handler");
        SimpleMachineUpgrade keyed = SimpleMachineUpgrade.of(
                new UpgradeType("regression_keyed_upgrade", "Keyed Upgrade", 1.0F, 3));
        keyed.scale("same_key", "modularmachinery:item", IOType.INPUT,
                RecipeModifier.OPERATION_MULTIPLY, 2.0F, false, true);
        require(keyed.keyedRequirementsModifiers().containsKey("same_key")
                        && keyed.requirementsModifiers().size() == 1,
                "keyed upgrade modifier was not retained");
        AtomicInteger callbackCount = new AtomicInteger();
        upgrade.addEventHandler(MachineTickEvent.class, (event, installed) -> callbackCount.incrementAndGet());
        var copied = upgrade.copy();
        require(copied.eventHandlers(MachineTickEvent.class).size() == 1,
                "upgrade copy lost its event callback");
        copied.eventHandlers(MachineTickEvent.class).get(0).accept(new MachineTickEvent(null, Phase.START), copied);
        require(callbackCount.get() == 1, "copied upgrade event callback did not execute");
        CompoundTag encoded = upgrade.writeNBT();
        SimpleMachineUpgrade restored = SimpleMachineUpgrade.of(type);
        restored.readNBT(encoded);
        require(restored.getCustomData().getString("mode").equals("boost")
                && encoded != restored.getCustomData(), "upgrade customData NBT roundtrip failed");
        RegistryMachineUpgrade.registerPrototype(upgrade);
        RegistryMachineUpgrade.addSupportedItem(new ItemStack(Items.DIAMOND));
        ItemStack tagged = RegistryMachineUpgrade.addUpgradeToStack(new ItemStack(Items.DIAMOND), name);
        require(RegistryMachineUpgrade.supportsUpgrade(tagged), "supported item did not accept a tagged upgrade");
        require(RegistryMachineUpgrade.upgradesFor(tagged).size() == 1,
                "tagged item did not resolve exactly one upgrade");
        require(RegistryMachineUpgrade.upgradesFor(tagged).get(0).durationMultiplier() == 0.5F,
                "tagged item resolved the wrong upgrade prototype");
        ItemStack supportedWithOtherTag = new ItemStack(Items.DIAMOND);
        supportedWithOtherTag.getOrCreateTag().putString("pack_marker", "different");
        require(RegistryMachineUpgrade.supportsUpgrade(supportedWithOtherTag),
                "supported item matching incorrectly compared unrelated NBT");
        ItemStack forgedUnsupported = RegistryMachineUpgrade.addUpgradeToStack(new ItemStack(Items.EMERALD), name);
        require(!RegistryMachineUpgrade.supportsUpgrade(forgedUnsupported)
                        && RegistryMachineUpgrade.upgradesFor(forgedUnsupported).isEmpty(),
                "an upgrade NBT tag on an unregistered item bypassed the item binding");
        ItemStack damagedMatch = new ItemStack(Items.IRON_PICKAXE);
        damagedMatch.setDamageValue(3);
        RegistryMachineUpgrade.addFixedUpgrade(damagedMatch, upgrade);
        ItemStack sameDamageOtherTag = new ItemStack(Items.IRON_PICKAXE);
        sameDamageOtherTag.setDamageValue(3);
        sameDamageOtherTag.getOrCreateTag().putString("pack_marker", "ignored");
        ItemStack differentDamage = new ItemStack(Items.IRON_PICKAXE);
        differentDamage.setDamageValue(4);
        require(RegistryMachineUpgrade.supportsUpgrade(sameDamageOtherTag)
                        && !RegistryMachineUpgrade.supportsUpgrade(differentDamage),
                "fixed upgrade matching did not follow item-plus-damage semantics");
        String dynamicName = "regression_dynamic_upgrade";
        SimpleDynamicMachineUpgrade dynamic = new SimpleDynamicMachineUpgrade(
                new UpgradeType(dynamicName, "Dynamic Regression Upgrade", 1.0F, 2));
        dynamic.setDescriptionHandler(instance -> new String[]{
                "mode=" + instance.getItemDataTag().getString("mode")});
        RegistryMachineUpgrade.registerPrototype(dynamic);
        RegistryMachineUpgrade.addSupportedItem(new ItemStack(Items.GOLD_INGOT));
        ItemStack dynamicStack = RegistryMachineUpgrade.addUpgradeToStack(
                new ItemStack(Items.GOLD_INGOT), dynamicName);
        dynamicStack.getOrCreateTag().put(DynamicMachineUpgrade.ITEM_DATA_TAG, new CompoundTag());
        dynamicStack.getTag().getCompound(DynamicMachineUpgrade.ITEM_DATA_TAG).putString("mode", "dynamic");
        var dynamicCopy = RegistryMachineUpgrade.upgradesFor(dynamicStack).stream()
                .filter(SimpleDynamicMachineUpgrade.class::isInstance)
                .map(SimpleDynamicMachineUpgrade.class::cast).findFirst().orElseThrow();
        require(dynamicCopy.getDescriptions().contains("mode=dynamic"),
                "dynamic upgrade did not read item data or evaluate its description handler");
        RegistryMachineUpgrade.clearScriptRegistrations();
        require(RegistryMachineUpgrade.getPrototype(name) == null,
                "script upgrade survived the CraftTweaker reload clear");
        require(RegistryMachineUpgrade.getPrototype(dynamicName) == null,
                "dynamic script upgrade survived the CraftTweaker reload clear");
        require(!RegistryMachineUpgrade.supportsUpgrade(sameDamageOtherTag),
                "script fixed binding survived the CraftTweaker reload clear");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
