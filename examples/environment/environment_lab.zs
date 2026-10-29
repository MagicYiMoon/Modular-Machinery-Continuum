// CraftTweaker 14。与 JSON 示例二选一，不要同时安装两份相同功能的配方。
import mods.modularmachinery.RecipeBuilder;
RecipeBuilder.newBuilder("environment_lab_script", "environment_lab", 100)
    .addItemInput(<item:minecraft:coal>)
    .addItemOutput(<item:minecraft:diamond>)
    .addEnvironmentDimensions("minecraft:overworld").setTag("sensor")
    .addEnvironmentTime(0, 12000).setTag("sensor")
    .build();

// 更多条件写法，按需加入构建链；同时加入的条件必须全部满足。
// .addEnvironmentAltitude(-64, 319).setTag("sensor")
// .addEnvironmentBiomes("minecraft:plains", "#minecraft:is_overworld").setTag("sensor")
// .addEnvironmentTime(23000, 1000).setTag("sensor")
// .addEnvironmentMoonPhases(0, 4).setTag("sensor")
// .addEnvironmentWeather("sunny").setTag("sensor")
