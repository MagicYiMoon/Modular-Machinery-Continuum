import mods.modularmachinery.MachineUpgradeBuilder;
import mods.modularmachinery.MachineUpgradeHelper;
import mods.modularmachinery.FactoryRecipeThread;
import mods.modularmachinery.MachineModifier;
import mods.modularmachinery.RecipeBuilder;

// Demonstration values only. These vanilla items retain their other uses.
MachineUpgradeBuilder.newBuilder("hn_example_cpu", "Example CPU", 1.0f, 2)
    .addCompatibleMachines("hn_sample_processor").buildAndRegister();
MachineUpgradeBuilder.newBuilder("hn_example_gpu", "Example GPU", 1.0f, 1)
    .addCompatibleMachines("hn_sample_processor").buildAndRegister();
MachineUpgradeBuilder.newBuilder("hn_example_ram", "Example RAM", 1.0f, 2)
    .addCompatibleMachines("hn_sample_processor").buildAndRegister();
MachineUpgradeHelper.addFixedUpgrade(<item:minecraft:diamond>, "hn_example_cpu");
MachineUpgradeHelper.addFixedUpgrade(<item:minecraft:emerald>, "hn_example_gpu");
MachineUpgradeHelper.addFixedUpgrade(<item:minecraft:redstone>, "hn_example_ram");

MachineModifier.setMaxThreads("hn_sample_center", 0);
MachineModifier.setMaxThreads("hn_sample_processor", 0);
FactoryRecipeThread.createCoreThread("novaeng.hypernet.data_forwarding_processor")
    .addRecipe("hn_center_idle").addToMachine("hn_sample_center");
FactoryRecipeThread.createCoreThread("novaeng.hypernet.data_processor.compute_scheduler")
    .addRecipe("hn_processor_idle").addToMachine("hn_sample_processor");

// Core running cost is separate from actual module load consumption.
RecipeBuilder.newBuilder("hn_center_idle", "hn_sample_center", 200)
    .addEnergyPerTickInput(1).setThreadName("novaeng.hypernet.data_forwarding_processor").build();
RecipeBuilder.newBuilder("hn_processor_idle", "hn_sample_processor", 200)
    .addEnergyPerTickInput(1).setThreadName("novaeng.hypernet.data_processor.compute_scheduler").build();

// 2 computation points per advancing tick, multiplied by the actual recipe batch.
// This overload pauses without discarding progress when the network is unavailable.
RecipeBuilder.newBuilder("hn_consumer_demo", "hn_sample_node", 100)
    .addItemInput(<item:minecraft:cobblestone>).addItemOutput(<item:minecraft:gravel>)
    .addEnergyPerTickInput(1).requireComputationPoint(2.0).build();

// Explicit opt-in: computation failure follows the machine's failure action and
// the recipe's ordinary cancel-on-per-tick-failure policy.
RecipeBuilder.newBuilder("hn_consumer_failure_demo", "hn_sample_node", 100)
    .addItemInput(<item:minecraft:dirt>).addItemOutput(<item:minecraft:clay_ball>)
    .addEnergyPerTickInput(1).requireComputationPoint(2.0, true).build();

// Independent preset threads keep maintenance available when the core is stopped.
FactoryRecipeThread.createCoreThread("novaeng.hypernet.data_processor.radiator")
    .addRecipe("hn_cooling_demo").addToMachine("hn_sample_processor");
FactoryRecipeThread.createCoreThread("novaeng.hypernet.maintenance_controller")
    .addRecipe("hn_repair_demo").addToMachine("hn_sample_center");
RecipeBuilder.newBuilder("hn_cooling_demo", "hn_sample_processor", 1)
    .addItemInput(<item:minecraft:snowball>).addItemOutput(<item:minecraft:clay_ball>)
    .addEnergyPerTickInput(1).addHyperNetCooling(20)
    .setThreadName("novaeng.hypernet.data_processor.radiator").build();
RecipeBuilder.newBuilder("hn_repair_demo", "hn_sample_center", 100)
    .addItemInput(<item:minecraft:iron_ingot>).addItemOutput(<item:minecraft:iron_nugget>)
    .addEnergyPerTickInput(1).addHyperNetCircuitRepair(100)
    .setThreadName("novaeng.hypernet.maintenance_controller").build();
