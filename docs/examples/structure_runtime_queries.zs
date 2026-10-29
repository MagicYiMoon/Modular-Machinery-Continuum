import mods.modularmachinery.MMEvents;
import crafttweaker.api.data.IData;

// Use an existing machine definition. Replace example_machine and the dynamic pattern name line.
// All block scans should normally run on structure update, not every recipe tick.
MMEvents.onStructureUpdate("example_machine", (event) => {
    val controller = event.controller;
    if (controller.formedMachineName == null) return;

    val line = controller.getDynamicPattern("line");
    if (line != null) {
        controller.customData["measured_segments"] = line.size as IData;
        // line.name, line.minSize and line.maxSize are also available.
        // line.facing follows the source's actual output: uppercase enum strings, e.g. NORTH or UP.
    }

    val stoneCount = controller.getBlocksInPattern("minecraft:stone");
    controller.customData["stone_cells"] = stoneCount as IData;
    val exactStoneCount = controller.getBlocksInPattern(<blockstate:minecraft:stone>);
    val itemStoneCount = controller.getBlocksInPattern(<item:minecraft:stone>);
    val selectedCount = controller.getBlocksInPattern((state) => state == <blockstate:minecraft:stone>);
    // String block IDs accept all states; BlockState and block items select an exact/default state.
    // Item counts and NBT do not select block entity data. A non-block item matches no cells.

    for offset in controller.getBlockPosInPattern("minecraft:stone") {
        val worldPos = controller.pos.offset(offset);
        // offset is already rotated. Do not call rotateWithControllerFacing(offset) again.
        // These queries observe positions; they do not alter or place blocks.
    }
});
