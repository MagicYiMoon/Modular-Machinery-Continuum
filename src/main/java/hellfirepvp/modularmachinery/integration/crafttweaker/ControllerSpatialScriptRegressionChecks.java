/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.geo.*;
import hellfirepvp.modularmachinery.common.machine.ControllerSpatialRegressionChecks;
import hellfirepvp.modularmachinery.common.tiles.*;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Compile once, exercise the same CT14 native spatial types from server recipes and render callbacks. */
public final class ControllerSpatialScriptRegressionChecks {
    private static final String TARGET = ControllerSpatialRegressionChecks.TARGET;
    private ControllerSpatialScriptRegressionChecks() { }
    public static void prepare() throws Exception {
        GeoMachineModelRegistry.registerModel(TARGET, "mmc_test:geo/spatial.geo.json",
                "mmc_test:textures/spatial.png", "mmc_test:animations/spatial.animation.json");
        GeoMachineModelRegistry.bindMachine(TARGET, TARGET);
        String script = """
                import mods.modularmachinery.IMachineController;
                import mods.modularmachinery.RecipeBuilder;
                import mods.modularmachinery.MMEvents;
                import crafttweaker.api.util.math.BlockPos;
                public function spatialValid(controller as IMachineController) as string {
                    val original = new BlockPos(2, -3, 5);
                    val rotated = controller.rotateWithControllerFacing(original);
                    val facing = controller.facing.getName();
                    val state = controller.blockState;
                    if (!state.hasProperty("facing") || state.getPropertyValue("facing") != facing) return "facing=" + facing + "/" + state.getPropertyValue("facing");
                    if (rotated.y != -3 || original.x != 2 || original.z != 5) return "original/y";
                    if (facing == "north" && (rotated.x != 2 || rotated.z != 5)) return "north";
                    if (facing == "west" && (rotated.x != 5 || rotated.z != -2)) return "west";
                    if (facing == "south" && (rotated.x != -2 || rotated.z != -5)) return "south";
                    if (facing == "east" && (rotated.x != -5 || rotated.z != 2)) return "east";
                    val worldPos = controller.pos.offset(rotated);
                    if (worldPos.x != controller.pos.x + rotated.x || worldPos.y != controller.pos.y - 3
                        || worldPos.z != controller.pos.z + rotated.z) return "world-offset";
                    val altered = state.withProperty("facing", controller.facing.opposite.getName());
                    if (controller.blockState.getPropertyValue("facing") != facing
                        || altered.getPropertyValue("facing") == facing) return "derived-state";
                    return "ok";
                }
                public function spatialKey(controller as IMachineController) as string {
                    return "spatial:" + controller.pos.x + "," + controller.pos.y + "," + controller.pos.z
                        + ":" + controller.facing.getName() + ":" + controller.blockState.getPropertyValue("formed");
                }
                RecipeBuilder.newBuilder("%s", "%s", 20)
                    .addPreTickHandler((event) => {
                        event.preventProgressing(spatialKey(event.controller) + "|" + spatialValid(event.controller));
                    })
                    .addFactoryPreTickHandler((event) => {
                        event.preventProgressing(spatialKey(event.controller) + "|" + spatialValid(event.controller));
                    }).build();
                MMEvents.onControllerModelAnimation("%s", (event) => {
                    event.setAnimation(spatialKey(event.controller) + "|" + spatialValid(event.controller), false);
                });
                """.formatted(TARGET, TARGET, TARGET);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            CraftTweakerAPI.getScriptRunManager().createScriptRun(
                    List.of(new LiteralSourceFile("controller_spatial_regression.zs", script)), configuration).execute();
        } catch (Throwable failure) { throw new Exception("controller spatial ZenCode compilation failed", failure); }
    }
    public static void run(TileController tile, TileController observation) {
        var recipe = new MachineRecipe(TARGET, TARGET, 20, List.of(), null, false, -1, "");
        var context = new RecipeCraftingContext(recipe);
        var before = tile.getCustomDataTag().copy();
        String expected = key(tile) + "|ok";
        if (tile instanceof TileFactoryController factory) {
            factory.setFactoryCeiling(1, 1);
            var thread = factory.claimThread(); thread.beginRecipe(TARGET, 20, 1); thread.setRecipe(recipe);
            thread.observeRuntimeRecipeContext(context);
            var event = new FactoryRecipeTickEvent(thread, tile, Phase.START, context); event.postEvent();
            require(event.isPreventProgressing() && expected.equals(event.getFailureReason()), "factory script spatial fields differ: expected=" + expected + ", actual=" + event.getFailureReason());
            thread.finishRecipe();
        } else {
            tile.beginRecipe(TARGET, 20, 1); tile.observeRuntimeRecipeContext(context);
            var event = new RecipeTickEvent(tile, context, Phase.START); event.postEvent();
            require(event.isPreventProgressing() && expected.equals(event.getFailureReason()), "ordinary script spatial fields differ: expected=" + expected + ", actual=" + event.getFailureReason());
            tile.finishRecipe();
        }
        var animation = new ControllerModelAnimationEvent(observation, null, null); animation.postCrTEvent();
        require(animation.getAnimations().size() == 1 && expected.equals(animation.getAnimations().get(0).name()),
                "compiled render callback could not query detached synchronized controller spatial fields");
        require(tile.getCustomDataTag().equals(before), "read-only spatial callback modified controller NBT");
        Modularmachinery.LOG.info("[MMC][controller-spatial-script] PASS: {} {} recipe and render-snapshot CT queries", tile.getBlockState().getBlock(), tile.getFacing());
    }
    private static String key(TileController tile) {
        var pos = tile.getBlockPos();
        return "spatial:" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ":" + tile.getFacing().getName()
                + ":" + tile.getBlockState().getValue(hellfirepvp.modularmachinery.common.block.BlockController.FORMED);
    }
    public static void cleanup() throws Exception {
        for (String name : List.of("recipes", "handlers", "callbacks")) {
            var field = ScriptRecipeRegistry.class.getDeclaredField(name); field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(TARGET);
        }
        var handlers = GeoMachineModelRegistry.class.getDeclaredField("HANDLERS"); handlers.setAccessible(true);
        ((Map<?, ?>) handlers.get(null)).remove(TARGET);
        var failed = GeoMachineModelRegistry.class.getDeclaredField("FAILED_HANDLERS"); failed.setAccessible(true);
        ((Set<?>) failed.get(null)).remove(TARGET);
        var script = GeoMachineModelRegistry.class.getDeclaredField("SCRIPT"); script.setAccessible(true);
        Object definitions = script.get(null);
        for (String name : List.of("models", "bindings")) {
            var field = definitions.getClass().getDeclaredField(name); field.setAccessible(true);
            ((Map<?, ?>) field.get(definitions)).remove(TARGET);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
