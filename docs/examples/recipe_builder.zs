// 示例，需要 CraftTweaker 14；不要与同名 JSON 配方同时注册。
import mods.modularmachinery.RecipeBuilder;

RecipeBuilder.newBuilder("ct_example_diamond", "alloy_furnace", 200)
    .addItemInput(<item:minecraft:coal> * 32)
    .addItemOutput(<item:minecraft:diamond>)
    .addEnergyPerTickInput(100)
    .build();

RecipeBuilder.newBuilder("ct_example_fluid", "alloy_furnace", 100)
    .addInput(<tag:items:forge:ingots/iron> * 2)
    .setParallelizeUnaffected(true)
    .addFluidInput(<fluid:minecraft:water> * 1000)
    .addItemOutput(<item:minecraft:iron_nugget> * 18)
    .setChance(0.5)
    .addEnergyPerTickInput(50)
    .setMaxThreads(1)
    .build();

// 需要另外安装 Mekanism，按需取消注释。
// RecipeBuilder.newBuilder("ct_example_gas", "alloy_furnace", 100)
//     .addGasInput("mekanism:hydrogen", 1000)
//     .addGasPerTickInput("mekanism:oxygen", 5)
//     .addItemOutput(<item:minecraft:diamond>)
//     .build();
