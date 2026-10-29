import mods.modularmachinery.MMEvents;

// Requires an existing machine; replace example_machine with its registered name.
// This runnable example only observes. To grant a fixed delta after each formation, enable:
// MMEvents.onStructureFormed("example_machine", event => {
//     event.controller.extraThreadCount = 2;
// });
// The delta affects factory dynamic slots. Ordinary controllers always report 0.

MMEvents.onMachinePreTick("example_machine", event => {
    val c = event.controller;
    if (c.ticksExisted % 20 == 0) {
        println("Controller lifetime ticks: " + c.ticksExisted);
        println("Extra dynamic slots: " + c.extraThreadCount);
    }
});

// Assignment replaces the delta: do not add 2 on every tick.
// A confirmed structure failure resets the delta; paid recipes survive a capacity reduction.
// Lifetime is local to the loaded entity and is not recipe progress or persisted world time.
