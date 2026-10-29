import mods.modularmachinery.DynamicMachineUpgradeBuilder;
import mods.modularmachinery.MachineUpgradeHelper;
import mods.modularmachinery.SimpleDynamicMachineUpgrade;
import mods.modularmachinery.MMEvents;
import crafttweaker.api.data.IData;

// Requires your existing example_machine with an upgrade bus. Does not create a machine.
DynamicMachineUpgradeBuilder.newBuilder("module_alpha", "Alpha Module", 1.0f, 1)
    .addCompatibleMachines("example_machine")
    .addDescriptions("Stores its own item data.")
    .buildAndRegister();
DynamicMachineUpgradeBuilder.newBuilder("module_beta", "Beta Module", 1.0f, 1)
    .addCompatibleMachines("example_machine")
    .addDescriptions("Uses the same physical item, with separate data.")
    .buildAndRegister();

MachineUpgradeHelper.addFixedUpgrade(<item:minecraft:diamond_pickaxe>, "module_alpha");
MachineUpgradeHelper.addFixedUpgrade(<item:minecraft:diamond_pickaxe>, "module_beta");

MMEvents.onStructureFormed("example_machine", (event) => {
    for entry in event.controller.getMachineUpgrade("module_alpha") {
        val alpha = entry as SimpleDynamicMachineUpgrade;
        if (alpha.valid) alpha.itemData["mode"] = 1 as IData;
    }
    for entry in event.controller.getMachineUpgrade("module_beta") {
        val beta = entry as SimpleDynamicMachineUpgrade;
        if (beta.valid) beta.itemData["mode"] = 2 as IData;
    }
});

// Both modes persist on the same item under different type keys.
// In an author-chosen recipe callback, alpha.decrementItemDurability(1) followed by
// beta.decrementItemDurability(2) consumes 3 durability from that same item.
// If it breaks, both handles become invalid; re-query after replacing or reloading the item.
