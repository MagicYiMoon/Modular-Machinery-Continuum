/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuSmartInterface;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktSmartInterfaceUpdate;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Real CT getters, value setter and event payloads, including the UI packet and formation producer paths. */
public final class SmartInterfaceRuntimeScriptRegressionChecks {
    private SmartInterfaceRuntimeScriptRegressionChecks() { }
    public static void run(TileController tile, TileSmartInterface iface) throws Exception {
        boolean factory = tile instanceof TileFactoryController;
        String target = SmartInterfaceRuntimeRegressionChecks.TARGET;
        String recipeName = "smart_runtime_" + (factory ? "factory" : "ordinary");
        String script = """
                import mods.modularmachinery.RecipeBuilder;
                import mods.modularmachinery.MMEvents;
                import mods.modularmachinery.SmartInterfaceData;
                import crafttweaker.api.data.IData;
                RecipeBuilder.newBuilder("%s", "%s", 20).%s((event) => {
                    val current = event.controller.getSmartInterfaceData("mode");
                    val missing = event.controller.getSmartInterfaceData("absent");
                    val all = event.controller.smartInterfaceDataList;
                    if (current != null && missing == null && all.length == 3
                        && current.interfaceType == "mode" && current.parentMachineName == "%s"
                        && current.pos.x == event.controller.pos.x && current.pos.y == event.controller.pos.y
                        && current.pos.z == event.controller.pos.z && all[1].interfaceType == "voltage") {
                        current.value = 4.5 as float;
                        val again = event.controller.getSmartInterfaceData("mode");
                        if (again != null && again.value == (4.5 as float)) event.preventProgressing("smart_runtime_compiled_ok");
                    }
                }).build();
                MMEvents.onSmartInterfaceUpdate("%s", (event) => {
                    val data = event.newData;
                    if (data != null && data.interfaceType == "mode" && data.parentMachineName == "%s"
                        && data.pos.x == event.controller.pos.x && data.pos.y == event.controller.pos.y
                        && data.pos.z == event.controller.pos.z && event.interfacePos.x == data.pos.x + 1) {
                        data.value = 7.5 as float;
                        val custom = event.controller.customData;
                        if (!("updates" in custom.asMap())) custom["updates"] = 0 as IData;
                        custom["updates"] = (custom["updates"].asInt() + 1) as IData;
                    }
                });
                """.formatted(recipeName, target, factory ? "addFactoryPreTickHandler" : "addPreTickHandler", target, target, target);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(
                        List.of(new LiteralSourceFile("smart_runtime_regression.zs", script)), configuration).execute();
            } catch (Throwable failure) { throw new Exception("smart interface ZenCode compilation failed", failure); }
            var recipe = new MachineRecipe(target, recipeName, 20, List.of(), null, false, -1, "");
            var context = new RecipeCraftingContext(recipe);
            if (factory) {
                var controller = (TileFactoryController) tile; controller.setFactoryCeiling(2, 1);
                var first = controller.claimThread(); first.beginRecipe(recipeName, 20, 1); first.setRecipe(recipe);
                var other = controller.claimThread();
                first.observeRuntimeRecipeContext(context);
                var event = new FactoryRecipeTickEvent(first, tile, Phase.START, context); event.postEvent();
                require(event.isPreventProgressing(), "factory CT query/setter did not run"); first.finishRecipe();
                other.beginRecipe(recipeName, 20, 1); other.setRecipe(recipe);
                var next = new RecipeCraftingContext(recipe); other.observeRuntimeRecipeContext(next);
                var second = new FactoryRecipeTickEvent(other, tile, Phase.START, next); second.postEvent();
                require(second.isPreventProgressing() && tile.getSmartInterfaceData("mode").getValue() == 4.5F, "factory threads did not share provider data");
                other.finishRecipe();
            } else {
                tile.beginRecipe(recipeName, 20, 1); tile.observeRuntimeRecipeContext(context);
                var event = new RecipeTickEvent(tile, context, Phase.START); event.postEvent();
                require(event.isPreventProgressing(), "ordinary CT query/setter did not run"); tile.finishRecipe();
            }
            var requirement = new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementInterfaceNumInput("mode", 4.5F);
            var machine = MachineReloadListener.getLoader().findByName(target);
            var worldContext = new MachineContext((ServerLevel) tile.getLevel(), tile.getBlockPos(), tile.getFacing(), machine, new MachineVariables(), machine.getPattern());
            require(requirement.canStart(worldContext), "script edit did not reach actual numeric requirement");
            var level = (ServerLevel) tile.getLevel(); var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SmartRuntime"));
            var oldMenu = player.containerMenu;
            try {
                player.setPos(iface.getBlockPos().getX() + 0.5, iface.getBlockPos().getY() + 0.5, iface.getBlockPos().getZ() + 0.5);
                player.containerMenu = new MenuSmartInterface(72, player.getInventory(), iface.getBlockPos());
                require(PktSmartInterfaceUpdate.apply(level, player, iface.getBlockPos(), "mode", 5), "real UI packet rejected fixture");
                require(tile.getCustomDataTag().getInt("updates") == 1 && iface.provideComponent().getMachineData("mode").getNumber() == 7.5,
                        "event.newData edit failed, or setter recursively posted an update");
            } finally { player.containerMenu = oldMenu; }
            tile.setFormed(false); iface.provideComponent().removeMachineData("mode");
            iface.provideComponent().addMachineData(target, target, machine.getSmartInterfaceType("mode"), true);
            require(tile.getCustomDataTag().getInt("updates") == 2 && iface.provideComponent().getMachineData("mode").getNumber() == 7.5,
                    "compiled publication callback could not normalize data before formation");
            tile.setFormed(true);
            var disk = iface.saveWithoutMetadata(); iface.load(disk);
            require(tile.getSmartInterfaceData("mode").getValue() == 7.5F, "script-edited provider value did not survive reload");
            Modularmachinery.LOG.info("[MMC][smart-runtime-script] PASS: real CT {} queries/arrays/value, shared factory data, UI+publication newData, nonrecursive normalization and reload", factory ? "factory" : "ordinary");
        } finally {
            for (String name : List.of("recipes", "handlers", "callbacks")) {
                var field = ScriptRecipeRegistry.class.getDeclaredField(name); field.setAccessible(true);
                ((Map<?, ?>) field.get(null)).remove(recipeName);
            }
            var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(target);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
