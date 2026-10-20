import mods.modularmachinery.RecipeBuilder;
import mods.modularmachinery.FactoryRecipeThread;
import mods.modularmachinery.MachineModifier;
import mods.modularmachinery.RecipeModifierBuilder;

// Zero disables ordinary dynamic threads; named core threads still run.
MachineModifier.setMaxThreads("mmce_core_fixture", 0);
val core = FactoryRecipeThread.createCoreThread("ore")
    .addRecipe("mmce_ct_core_a")
    .addRecipe("mmce_ct_core_b");
core.addPermanentModifier("double_input", RecipeModifierBuilder.create("modularmachinery:item", "input", 2.0, 1, false).build());
// Declare the complete recipe set before attaching the thread.
MachineModifier.addCoreThread("mmce_core_fixture", core);
FactoryRecipeThread.createCoreThread("empty").addToMachine("mmce_core_fixture");

RecipeBuilder.newBuilder("mmce_ct_core_a", "mmce_core_fixture", 20)
    .addItemInput(<item:minecraft:coal>)
    .addItemOutput(<item:minecraft:diamond>)
    .setThreadName("ore")
    .build();
RecipeBuilder.newBuilder("mmce_ct_core_b", "mmce_core_fixture", 20)
    .addItemInput(<item:minecraft:charcoal>)
    .addItemOutput(<item:minecraft:emerald>)
    .setThreadName("ore")
    .build();
