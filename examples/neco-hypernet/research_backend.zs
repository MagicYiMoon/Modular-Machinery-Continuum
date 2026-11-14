import mods.modularmachinery.FactoryRecipeThread;
import mods.modularmachinery.MachineModifier;
import mods.modularmachinery.RecipeBuilder;

// Optional backend example: also install hn_sample_database.json and research/basic_materials.json.
// research_station.zs adds the station core; hypernet_terminal.json enables the independent player terminal.
// This does NOT grant research automatically.
// The designated database core runs continuously; it does not finish every 20 ticks.
// Native input FE multiplier: 1 + recognized completed research count * 0.1.
// In-progress entries do not add fees. Do not duplicate this multiplier in scripts.
// Integer FE rounding applies; use a larger base than 1 to observe small changes.
MachineModifier.setMaxThreads("hn_sample_database", 0);
FactoryRecipeThread.createCoreThread("novaeng.hypernet.data_forwarding_processor")
    .addRecipe("hn_database_idle").addToMachine("hn_sample_database");
RecipeBuilder.newBuilder("hn_database_idle", "hn_sample_database", 20)
    .addEnergyPerTickInput(1).setThreadName("novaeng.hypernet.data_forwarding_processor").build();

RecipeBuilder.newBuilder("hn_research_locked_demo", "hn_sample_node", 100)
    .requireResearch("basic_materials")
    .addItemInput(<item:minecraft:iron_ingot>).addItemOutput(<item:minecraft:iron_nugget> * 9).build();
