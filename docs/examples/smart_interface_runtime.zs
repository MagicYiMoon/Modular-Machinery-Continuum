import mods.modularmachinery.MMEvents;

// Requires an existing example_machine declaring the numeric smart-interface type "mode".
// Only clamps published/player-edited values; does not create a machine or recipe.
MMEvents.onSmartInterfaceUpdate("example_machine", (event) => {
    val data = event.newData;
    if (data != null && data.interfaceType == "mode") {
        if (data.value < (0 as float)) data.value = 0 as float;
        if (data.value > (2 as float)) data.value = 2 as float;
    }
});

// Query after formation; the same getter works inside ordinary/factory recipe callbacks.
MMEvents.onStructureFormed("example_machine", (event) => {
    val mode = event.controller.getSmartInterfaceData("mode");
    if (mode != null && mode.parentMachineName == "example_machine") {
        // mode.pos is the CONTROLLER position, not the interface block position.
        // This example deliberately chooses the initial mode when the structure forms.
        mode.value = 1 as float;
    }
});

// event.controller.smartInterfaceDataList lists each loaded bound provider's primary datum.
// Duplicate interface types remain separate values; getSmartInterfaceData(type) returns the first.
// Factory threads share their controller's providers. For per-thread state use another mechanism.
