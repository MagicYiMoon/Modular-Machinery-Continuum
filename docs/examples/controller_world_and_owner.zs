import mods.modularmachinery.MMEvents;

// Requires an existing machine. Replace example_machine with its registered name.
MMEvents.onStructureUpdate("example_machine", (event) => {
    val controller = event.controller;
    val world = controller.world;
    if (world == null) return;

    println("Machine dimension: " + world.dimension.path);
    println("World game time: " + world.gameTime);
    val ownerId = controller.ownerUUID;
    if (ownerId != null) println("Owner UUID: " + ownerId);
    val ownerName = controller.ownerName;
    if (ownerName != null) println("Cached owner name: " + ownerName);
    val player = controller.ownerPlayer;
    if (player != null) println("Online owner UUID: " + player.getStringUUID());

    // CT14 exposes the native Level API. Read only loaded positions.
    if (world.isLoaded(controller.pos)) {
        val controllerState = world.getBlockState(controller.pos);
    }
    // This example observes only. To change a chosen, loaded position in a server callback:
    // world.setBlockAndUpdate(chosenPos, <blockstate:minecraft:stone>);
    // Do not keep Level or Player in persistent data or use them from asynchronous callbacks.
});
