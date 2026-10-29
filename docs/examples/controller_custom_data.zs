import mods.modularmachinery.RecipeBuilder;
import crafttweaker.api.data.IData;

// Requires an author-created example_machine with item input/output buses.
// Attach these handlers to your existing recipe builder; do not redeclare its ID.
RecipeBuilder.newBuilder("example_custom_data", "example_machine", 100)
    .addItemInput(<item:minecraft:gold_ingot>)
    .addItemOutput(<item:minecraft:gold_nugget> * 9)
    .addFinishHandler((event) => {
        val data = event.controller.customData;
        // Keep unrelated data owned by other callbacks.
        if (!("example.crafts" in data.asMap())) {
            data["example.crafts"] = 0 as IData;
        }
        data["example.crafts"] = (data["example.crafts"].asInt() + 1) as IData;
    })
    .addFactoryFinishHandler((event) => {
        // All threads belonging to this factory share this controller counter.
        val data = event.controller.customData;
        if (!("example.crafts" in data.asMap())) {
            data["example.crafts"] = 0 as IData;
        }
        data["example.crafts"] = (data["example.crafts"].asInt() + 1) as IData;
        val thread = event.factoryRecipeThread;
        if (thread != null) {
            data["example.last_thread"] = thread.threadName as IData;
        }
    }).build();

// Inside a callback, take an independent snapshot with:
// val historical = event.controller.customData.copy();
// To clear the entire controller data deliberately:
// event.controller.customData = {} as IData;
