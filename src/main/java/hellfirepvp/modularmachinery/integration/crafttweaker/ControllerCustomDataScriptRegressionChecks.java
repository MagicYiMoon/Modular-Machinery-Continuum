/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.data.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import org.openzen.zencode.shared.LiteralSourceFile;

/** Actual mutable IData operations, followed by real ZenCode event callbacks and definition reload. */
public final class ControllerCustomDataScriptRegressionChecks {
    private ControllerCustomDataScriptRegressionChecks() { }
    public static void run(ServerLevel level, TileController tile, TileController other) throws Exception {
        checkViews(tile, other);
        checkScript(tile, other);
        Modularmachinery.LOG.info("[MMC][controller-data-script] PASS: real CT {} assignment/index/nested/list/merge, copies, save+sync, guards, instance isolation and reload retention",
                tile instanceof TileFactoryController ? "factory" : "ordinary");
    }
    private static void checkViews(TileController tile, TileController other) throws Exception {
        var recording = (ControllerCustomDataRegressionChecks.Recording) tile;
        recording.resetCounts(); IData data = tile.getCustomData();
        data.asMap(); data.getKeys(); data.toString(); data.copyInternal(); data.getAt("nested").asMap();
        require(recording.syncs() == 0 && recording.updates() == 0, "read-only IData access dirtied/synchronized controller");
        data.put("counter", new IntData(4));
        require(recording.syncs() == 1 && recording.updates() == 0, "direct write did not request data-only sync");
        data.put("counter", new IntData(4));
        require(recording.syncs() == 1, "equal write sent another synchronization");
        data.getAt("nested").setAt("value", new IntData(11));
        data.asMap().get("nested").put("from_map", new IntData(1));
        data.getAt("list").asList().get(0).put("value", new IntData(10));
        data.getAt("list").iterator().next().put("iterator", new IntData(2));
        var incoming = new MapData(); incoming.put("value", new IntData(20));
        data.getAt("list").add(incoming); incoming.put("value", new IntData(99));
        require(tile.getCustomDataTag().getList("list", Tag.TAG_COMPOUND).getCompound(1).getInt("value") == 20,
                "list append retained external IData alias");
        var nested = new MapData(); nested.put("value", new IntData(30));
        data.put("incoming", nested); nested.put("value", new IntData(99));
        require(tile.getCustomDataTag().getCompound("incoming").getInt("value") == 30, "map put retained external IData alias");
        data.getAt("ints").put("0", new IntData(7)); data.getAt("bytes").put("0", new ByteData((byte) 8));
        data.getAt("longs").put("0", new LongData(9L));
        data.getAt("ints").asIntArray()[0] = 100;
        data.getAt("bytes").asByteArray()[0] = 100;
        data.getAt("longs").asLongArray()[0] = 100;
        require(tile.getCustomDataTag().getIntArray("ints")[0] == 7 && tile.getCustomDataTag().getByteArray("bytes")[0] == 8
                && tile.getCustomDataTag().getLongArray("longs")[0] == 9, "typed array writes failed or primitive arrays leaked");
        var merge = new MapData(); merge.put("merged", new IntData(12)); data.merge(merge);
        data.getAt("list").remove(1); data.remove("incoming");
        require(tile.getCustomDataTag().getInt("merged") == 12 && !tile.getCustomDataTag().contains("incoming")
                && tile.getCustomDataTag().getList("list", Tag.TAG_COMPOUND).size() == 1, "merge/delete did not update live data");
        ((CompoundTag) data.getInternal()).putInt("counter", 99);
        data.copyInternal().put("counter", new IntData(99));
        require(tile.getCustomDataTag().getInt("counter") == 4, "internal/deep copy leaked mutable data");
        data.copy().put("counter", new IntData(5));
        require(tile.getCustomDataTag().getInt("counter") == 4, "script-visible copy did not detach mutable NBT");
        var detachedTile = new TileController(tile.getBlockPos(), hellfirepvp.modularmachinery.common.registry.RegistryBlocks.blockController.get().defaultBlockState());
        detachedTile.load(tile.getUpdateTag()); IData observation = detachedTile.getCustomData();
        observation.put("counter", new IntData(99));
        require(observation.getAt("counter").asInt() == 4 && detachedTile.getCustomDataTag().getInt("counter") == 4,
                "non-server observation accepted a world-data write");
        other.setCustomData(data); other.getCustomData().getAt("nested").put("value", new IntData(55));
        require(tile.getCustomDataTag().getCompound("nested").getInt("value") == 11, "assigning IData shared controller storage");
        var expected = tile.getCustomDataTag().copy(); int syncs = recording.syncs();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread writer = new Thread(() -> {
            try {
                data.put("counter", new IntData(90)); data.getAt("nested").put("value", new IntData(90));
                tile.setCustomData(new MapData()); tile.getCustomData().put("counter", new IntData(90));
            } catch (Throwable problem) { failure.set(problem); }
        }); writer.start(); writer.join();
        require(failure.get() == null && tile.getCustomDataTag().equals(expected) && recording.syncs() == syncs,
                "off-thread retained/fresh IData or setter changed live state");
        tile.setCustomDataTag(expected); data.put("stale", new IntData(1));
        require(!tile.getCustomDataTag().contains("stale"), "replaced root accepted stale view mutation");
        IData priorLoad = tile.getCustomData(); tile.load(tile.saveWithoutMetadata()); priorLoad.put("stale_load", new IntData(1));
        require(!tile.getCustomDataTag().contains("stale_load"), "pre-load view changed loaded state");
        IData rebound = tile.getCustomData(); tile.setMachineName(ControllerCustomDataRegressionChecks.TARGET + "_other");
        rebound.put("stale_binding", new IntData(1));
        require(!tile.getCustomDataTag().contains("stale_binding"), "old machine binding kept writable view");
        tile.setMachineName(ControllerCustomDataRegressionChecks.TARGET);
        try { tile.setCustomData(new IntData(1)); throw new AssertionError("scalar root accepted"); }
        catch (IllegalArgumentException expectedFailure) { }
        try { tile.setCustomData(null); throw new AssertionError("null root accepted"); }
        catch (IllegalArgumentException expectedFailure) { }
        IData removed = tile.getCustomData(); tile.setRemoved(); removed.put("removed", new IntData(1));
        require(!tile.getCustomDataTag().contains("removed"), "removed controller retained writable view");
        tile.clearRemoved(); tile.onLoad();
        var disk = tile.saveWithoutMetadata();
        require(disk.getCompound("customData").equals(tile.getCustomDataTag()), "in-place edits missing from saved NBT");
    }
    private static void checkScript(TileController tile, TileController other) throws Exception {
        boolean factory = tile instanceof TileFactoryController;
        String name = factory ? "controller_data_factory" : "controller_data_ordinary";
        String script = """
                import mods.modularmachinery.RecipeBuilder;
                import crafttweaker.api.data.IData;
                RecipeBuilder.newBuilder("%s", "%s", 20).%s((event) => {
                    if (!("count" in event.controller.customData.asMap())) {
                        event.controller.customData = {count: 0, nested: {value: 1}, entries: [{value: 2}]} as IData;
                    }
                    val data = event.controller.customData;
                    if ("count" in data.asMap()) {
                        data["count"] = (data["count"].asInt() + 1) as IData;
                    }
                    data["nested"]["value"] = 7 as IData;
                    data["entries"][0]["value"] = 8 as IData;
                    data["entries"].add({value: 9} as IData);
                    data.merge({merged: 10} as IData);
                    data.put("temporary", 1 as IData);
                    data.remove("temporary");
                    val snapshot = data.copy();
                    snapshot["count"] = 99 as IData;
                    if (event.controller.customData["count"].asInt() >= 1
                        && event.controller.customData["nested"]["value"].asInt() == 7
                        && event.controller.customData["entries"][0]["value"].asInt() == 8
                        && event.controller.customData["entries"][1]["value"].asInt() == 9
                        && event.controller.customData["merged"].asInt() == 10) {
                        event.preventProgressing("controller_data_compiled_ok");
                    }
                }).build();
                """.formatted(name, ControllerCustomDataRegressionChecks.TARGET, factory ? "addFactoryPreTickHandler" : "addPreTickHandler");
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(
                        List.of(new LiteralSourceFile("controller_custom_data_regression.zs", script)), configuration).execute();
            } catch (Throwable problem) { throw new Exception("controller customData script compilation failed", problem); }
            var recipe = new MachineRecipe(ControllerCustomDataRegressionChecks.TARGET, name, 20, List.of(), null, false, -1, "");
            var run = new RecipeCraftingContext(recipe); var otherBefore = other.getCustomDataTag().copy();
            if (factory) {
                var controller = (TileFactoryController) tile; controller.setFactoryCeiling(2, 1);
                var core = controller.claimThread(); core.beginRecipe(name, 20, 1); core.setRecipe(recipe); core.observeRuntimeRecipeContext(run);
                var otherThread = controller.claimThread();
                var event = new FactoryRecipeTickEvent(core, tile, Phase.START, run); event.postEvent();
                require(event.isPreventProgressing(), "compiled factory customData callback did not execute its assertions");
                core.finishRecipe();
                otherThread.beginRecipe(name, 20, 1); otherThread.setRecipe(recipe);
                var secondRun = new RecipeCraftingContext(recipe); otherThread.observeRuntimeRecipeContext(secondRun);
                var secondEvent = new FactoryRecipeTickEvent(otherThread, tile, Phase.START, secondRun); secondEvent.postEvent();
                require(secondEvent.isPreventProgressing() && tile.getCustomDataTag().getInt("count") == 2,
                        "different factory threads did not share the controller counter");
                otherThread.finishRecipe();
            } else {
                tile.beginRecipe(name, 20, 1); tile.observeRuntimeRecipeContext(run);
                var event = new RecipeTickEvent(tile, run, Phase.START); event.postEvent();
                require(event.isPreventProgressing(), "compiled ordinary customData callback did not execute its assertions");
                tile.finishRecipe();
            }
            require(tile.getCustomDataTag().getInt("count") == (factory ? 2 : 1) && other.getCustomDataTag().equals(otherBefore),
                    "compiled data did not persist after completion or leaked into another controller");
            var expected = tile.getCustomDataTag().copy();
            // Real CT prepare/run lifecycle (including registry cleanup) must not clear controller-owned NBT.
            var listener = new com.blamejared.crafttweaker.impl.script.ScriptReloadListener(
                    ((ServerLevel) tile.getLevel()).getServer().getServerResources().managers(), message -> { });
            var prepare = listener.getClass().getDeclaredMethod("prepareRun"); prepare.setAccessible(true);
            Object preparation = prepare.invoke(listener);
            require(tile.getCustomDataTag().equals(expected), "real script reload preparation erased controller data");
            var runMethod = preparation.getClass().getDeclaredMethod("run"); runMethod.setAccessible(true);
            ((com.blamejared.crafttweaker.api.zencode.scriptrun.IScriptRun) runMethod.invoke(preparation)).execute();
            require(tile.getCustomDataTag().equals(expected), "real script replay erased controller data");
            CraftTweakerAPI.getScriptRunManager().createScriptRun(List.of(), configuration).execute();
            require(tile.getCustomDataTag().equals(expected), "empty script execution erased controller data");
        } catch (Throwable problem) {
            if (problem instanceof Exception exception) throw exception;
            throw new Exception("controller customData script execution failed", problem);
        } finally {
            for (String fieldName : List.of("recipes", "handlers", "callbacks")) {
                var field = ScriptRecipeRegistry.class.getDeclaredField(fieldName); field.setAccessible(true);
                ((Map<?, ?>) field.get(null)).remove(name);
            }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
