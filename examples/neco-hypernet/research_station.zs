import mods.modularmachinery.FactoryRecipeThread;
import mods.modularmachinery.MachineModifier;
import mods.modularmachinery.RecipeBuilder;

// Also install hn_sample_research_station.json, the database example and a research definition.
// Install hypernet_terminal.json and use the independent HyperNet terminal to assign/cancel tasks.
MachineModifier.setMaxThreads("hn_sample_research_station", 0);
FactoryRecipeThread.createCoreThread("novaeng.hypernet.research_station.hypercol")
    .addRecipe("hn_station_work").addToMachine("hn_sample_research_station");
RecipeBuilder.newBuilder("hn_station_work", "hn_sample_research_station", 100)
    .addEnergyPerTickInput(1).setThreadName("novaeng.hypernet.research_station.hypercol").build();
