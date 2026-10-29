import mods.modularmachinery.RecipeBuilder;
import mods.modularmachinery.RecipeModifierBuilder;

// Requires an author-created example_machine with the corresponding item/energy IO.
// Add these handlers to your own builder; do not redeclare an existing recipe ID.
val halfEnergy = RecipeModifierBuilder.create("modularmachinery:energy", "input", 0.5, 1, false).build();
val doubleOutput = RecipeModifierBuilder.create("modularmachinery:item", "output", 2.0, 1, false).build();

RecipeBuilder.newBuilder("example_runtime_modifiers", "example_machine", 100)
    .addItemInput(<item:minecraft:gold_ingot>)
    .addItemOutput(<item:minecraft:gold_nugget> * 9)
    .addEnergyPerTickInput(100)
    .addPreTickHandler((event) => {
        val thread = event.recipeThread;
        if (thread != null && thread.valid) {
            // Same key replaces the previous value; it does not compound each tick.
            thread.addModifier("example.energy", halfEnergy);
        }
    })
    .addFactoryPreTickHandler((event) => {
        val thread = event.factoryRecipeThread;
        if (thread != null && thread.valid) {
            // Only this run receives the output bonus. Cleared after completion.
            thread.addModifier("example.yield", doubleOutput);
            // All currently existing factory threads receive the energy bonus.
            // Threads created later do not inherit this particular broadcast.
            event.controller.addModifier("example.broadcast", halfEnergy);
        }
    }).build();

// Other calls available inside an event callback:
// thread.addPermanentModifier("example.persistent", halfEnergy);
// thread.hasPermanentModifier("example.persistent");
// thread.removePermanentModifier("example.persistent");
// thread.removeModifier("example.energy");
// event.controller.removeModifier("example.broadcast");
// event.controller.addPermanentModifier("example.global", halfEnergy);
// event.controller.hasModifier("example.global");  // checks controller-global scope
// event.controller.removePermanentModifier("example.global");
