/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.upgrade.DynamicMachineUpgrade;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Actual ZenCode controller queries, subtype casts and nested upgrade data/durability writes. */
public final class UpgradeRuntimeScriptRegressionChecks {
    private UpgradeRuntimeScriptRegressionChecks() { }
    public static void run(TileController tile, TileUpgradeBus a, TileUpgradeBus c) throws Exception {
        String target = UpgradeRuntimeRegressionChecks.TARGET;
        String script = """
                import mods.modularmachinery.MMEvents;
                import mods.modularmachinery.SimpleMachineUpgrade;
                import mods.modularmachinery.SimpleDynamicMachineUpgrade;
                import crafttweaker.api.data.IData;
                MMEvents.onMachinePreTick("%s", (event) => {
                    val found = event.controller.foundModifiers;
                    val upgrades = event.controller.getMachineUpgrade("%s");
                    val dynamic = event.controller.getMachineUpgrade("%s");
                    if (found.length != 2 || !event.controller.hasModifierReplacement("gold_pair")
                        || !event.controller.hasMachineUpgrade("%s") || upgrades.length != 1
                        || upgrades[0].stackSize != 3 || dynamic.length != 2
                        || event.controller.getMachineUpgrade("missing").length != 0
                        || event.controller.hasMachineUpgrade("missing")) return;
                    val fixed = upgrades[0] as SimpleMachineUpgrade;
                    val oldData = fixed.customData;
                    fixed.customData = {script: 11 as IData, child: {value: 1 as IData}};
                    oldData["stale"] = 1 as IData;
                    if ("stale" in fixed.customData.asMap()) return;
                    fixed.customData["child"]["value"] = 12 as IData;
                    val first = dynamic[0] as SimpleDynamicMachineUpgrade;
                    val second = dynamic[1] as SimpleDynamicMachineUpgrade;
                    first.itemData["script"] = 21 as IData;
                    first.customData["shared"] = 22 as IData;
                    first.decrementItemDurability(3);
                    val again = event.controller.getMachineUpgrade("%s")[0] as SimpleMachineUpgrade;
                    val after = event.controller.getMachineUpgrade("%s")[1] as SimpleDynamicMachineUpgrade;
                    if (first.valid && second.valid && again.customData["child"]["value"].asInt() == 12
                        && after.customData["shared"].asInt() == 22 && !("script" in after.itemData.asMap())) {
                        event.controller.customData["upgrade_script_ok"] = 1 as IData;
                    }
                });
                """.formatted(target, UpgradeRuntimeRegressionChecks.STATIC, UpgradeRuntimeRegressionChecks.DYNAMIC,
                UpgradeRuntimeRegressionChecks.STATIC, UpgradeRuntimeRegressionChecks.STATIC, UpgradeRuntimeRegressionChecks.DYNAMIC);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        int firstDamage = c.getUpgradeInventory().getStackInSlot(0).getDamageValue();
        int secondDamage = c.getUpgradeInventory().getStackInSlot(1).getDamageValue();
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(List.of(new LiteralSourceFile("upgrade_runtime_regression.zs", script)), configuration).execute();
            } catch (Throwable failure) { throw new Exception("upgrade runtime ZenCode compilation failed", failure); }
            new MachineTickEvent(tile, Phase.START).postEvent();
            require(tile.getCustomDataTag().getInt("upgrade_script_ok") == 1, "compiled upgrade queries/casts/data callback did not finish");
            require(a.getUpgradeCustomData(UpgradeRuntimeRegressionChecks.STATIC).getInt("script") == 11
                    && a.getUpgradeCustomData(UpgradeRuntimeRegressionChecks.STATIC).getCompound("child").getInt("value") == 12,
                    "compiled customData assignment/nested edits did not persist");
            require(c.getUpgradeInventory().getStackInSlot(0).getDamageValue() == firstDamage + 5
                    && c.getUpgradeInventory().getStackInSlot(1).getDamageValue() == secondDamage + 2,
                    "compiled dynamic upgrade did not preserve per-slot durability");
            var disk = c.saveWithoutMetadata(); c.load(disk);
            require(((DynamicMachineUpgrade) tile.getMachineUpgrade(UpgradeRuntimeRegressionChecks.DYNAMIC)[0]).getItemDataTag().getInt("script") == 21,
                    "compiled itemData did not survive save/load");
            Modularmachinery.LOG.info("[MMC][upgrade-runtime-script] PASS: real CT {} queries, subtype casts, nested bus/item data, shared bus/type data and per-slot durability", tile instanceof TileFactoryController ? "factory" : "ordinary");
        } finally {
            var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(target);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
