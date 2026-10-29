import mods.modularmachinery.MMEvents;
import mods.modularmachinery.SimpleMachineUpgrade;
import mods.modularmachinery.SimpleDynamicMachineUpgrade;
import crafttweaker.api.data.IData;

// Requires your existing example_machine and upgrade declarations.
// Replace these type names with the names passed to YOUR upgrade builders.
MMEvents.onStructureFormed("example_machine", (event) => {
    val controller = event.controller;
    controller.customData["has_frame"] = controller.hasModifierReplacement("reinforced_frame") as IData;

    val fixed = controller.getMachineUpgrade("my_static_upgrade");
    if (fixed.length > 0) {
        val upgrade = fixed[0] as SimpleMachineUpgrade;
        // stackSize is the effective capped count, not an inventory modification.
        controller.customData["upgrade_count"] = upgrade.stackSize as IData;
        upgrade.customData["seen"] = true as IData;
    }

    val dynamic = controller.getMachineUpgrade("my_dynamic_upgrade");
    for entry in dynamic {
        val upgrade = entry as SimpleDynamicMachineUpgrade;
        if (upgrade.valid) {
            // itemData follows this source slot's item. customData belongs to the bus/type.
            upgrade.itemData["seen"] = true as IData;
            // Optional author-defined wear: upgrade.decrementItemDurability(1);
        }
    }
});

// controller.foundModifiers lists matched structure conditions, not runtime modifier keys.
// Use explicit MultiBlockModifierBuilder names for stable name comparisons.
// Runtime queries return empty on clients/offline previews/asynchronous callbacks.
