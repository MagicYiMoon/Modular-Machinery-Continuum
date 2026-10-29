import mods.modularmachinery.RecipeBuilder;

RecipeBuilder.newBuilder("ember_lab_ct", "ember_lab", 100)
    .addItemInput(<item:minecraft:coal>)
    .addItemOutput(<item:minecraft:diamond>)
    .addEmberInput(12.25).setTag("ember_in")
    .build();
