import mods.modularmachinery.MMEvents;
import crafttweaker.api.util.math.BlockPos;
import crafttweaker.api.data.IData;

// Requires an existing author-created example_machine; does not create a machine or recipe.
// Structure updates are server events, avoiding repeated work in every recipe tick.
MMEvents.onStructureUpdate("example_machine", (event) => {
    val controller = event.controller;
    // Source pattern coordinates: NORTH is the identity. This is the controller's front.
    val relative = controller.rotateWithControllerFacing(new BlockPos(0, 0, -1));
    val worldPos = controller.pos.offset(relative);
    val data = controller.customData;
    data["example.front"] = {x: worldPos.x, y: worldPos.y, z: worldPos.z} as IData;
    data["example.facing"] = controller.facing.getName() as IData;
});

// The same read queries are available in a registered GeckoLib animation callback.
// The callback must be registered after its model has been registered and bound.
// Client animation callbacks should only read; do not write controller.customData there.
