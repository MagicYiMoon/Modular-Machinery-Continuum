import mods.modularmachinery.RecipeBuilder;

// Author-provided machine; replace this name and the IO to suit your structure.
// Use this handler on your own builder rather than redeclaring an existing recipe ID.
RecipeBuilder.newBuilder("example_runtime_queries", "example_machine", 100)
    .addItemInput(<item:minecraft:gold_ingot>)
    .addItemOutput(<item:minecraft:gold_nugget> * 9)
    .addFactoryStartHandler((event) => {
        val thread = event.factoryRecipeThread;
        val active = event.activeRecipe;
        val context = event.context;
        if (thread != null && active != null && context != null) {
            print("Thread: " + thread.threadName
                + ", recipe: " + active.registryName
                + ", duration: " + active.totalTick
                + ", batch: " + active.parallelism);
            for description in context.inputDescriptions {
                print(description);
            }
            for otherThread in event.controller.recipeThreadList {
                val otherRecipe = otherThread.activeRecipe;
                if (otherThread.valid && otherRecipe != null) {
                    print("Occupied: " + otherThread.threadName + " -> " + otherRecipe.registryName);
                }
            }
        }
    })
    .addFinishHandler((event) => {
        val finished = event.activeRecipe;
        if (finished != null) {
            print("Finished ordinary recipe: " + finished.registryName + ", ticks: " + finished.tick);
        }
    }).build();
