/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnvironment;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;

public final class EnvironmentScriptRegressionChecks {
    private EnvironmentScriptRegressionChecks() { }
    public static void run() {
        var builder = RecipeBuilder.newBuilder("environment_builder", "alloy_furnace", 20)
                .addEnvironmentDimensions("minecraft:overworld").addEnvironmentBiomes("#minecraft:is_overworld")
                .addEnvironmentAltitude(-64, 319).addEnvironmentTime(23000, 1000).addEnvironmentMoonPhases(0,4)
                .addEnvironmentWeather("sunny").setTag("sensor");
        var decoded = RecipeSnapshotCodec.decode(builder.snapshot());
        if (decoded.requirements().size() != 6 || !(decoded.requirements().get(5) instanceof RequirementEnvironment)
                || !"sensor".equals(decoded.requirements().get(5).getSelectorTag())) throw new AssertionError("environment CT builder snapshot");
        var script = com.blamejared.crafttweaker.api.CraftTweakerAPI.getScriptsDirectory().resolve("mmc_environment_fixture.zs");
        if (java.nio.file.Files.exists(script)) {
            var recipe = MachineReloadListener.getRecipeLoader().getRecipe("mmc_environment_fixture");
            if (recipe == null || recipe.requirements().stream().filter(RequirementEnvironment.class::isInstance).count() != 6)
                throw new AssertionError("environment ZenCode fixture did not register all six conditions");
        }
    }
}
