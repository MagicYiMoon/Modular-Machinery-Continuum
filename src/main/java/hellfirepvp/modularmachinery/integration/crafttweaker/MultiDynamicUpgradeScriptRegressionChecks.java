/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.upgrade.*;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Actual CT nested itemData operations on two types attached to the same physical item. */
public final class MultiDynamicUpgradeScriptRegressionChecks {
    private MultiDynamicUpgradeScriptRegressionChecks() { }
    public static void run(TileController controller, TileUpgradeBus bus) throws Exception {
        String target = MultiDynamicUpgradeRegressionChecks.TARGET;
        String script = """
                import mods.modularmachinery.MMEvents;
                import mods.modularmachinery.SimpleDynamicMachineUpgrade;
                import crafttweaker.api.data.IData;
                MMEvents.onMachinePreTick("%s", (event) => {
                    val alphas = event.controller.getMachineUpgrade("%s");
                    val betas = event.controller.getMachineUpgrade("%s");
                    if (alphas.length != 3 || betas.length != 3) return;
                    val alpha = alphas[0] as SimpleDynamicMachineUpgrade;
                    val beta = betas[0] as SimpleDynamicMachineUpgrade;
                    val old = alpha.itemData;
                    alpha.itemData = {nested: {value: 111 as IData}, list: [1 as IData, 2 as IData]};
                    old["stale"] = 90 as IData;
                    beta.itemData = {nested: {value: 222 as IData}};
                    alpha.itemData["nested"]["value"] = 113 as IData;
                    beta.itemData["nested"]["value"] = 223 as IData;
                    alpha.itemData["list"][0] = 3 as IData;
                    val copy = alpha.itemData.copy();
                    copy["nested"]["value"] = 999 as IData;
                    alpha.decrementItemDurability(4);
                    beta.decrementItemDurability(6);
                    val againA = event.controller.getMachineUpgrade("%s")[0] as SimpleDynamicMachineUpgrade;
                    val againB = event.controller.getMachineUpgrade("%s")[0] as SimpleDynamicMachineUpgrade;
                    if (alpha.valid && beta.valid && againA.itemData["nested"]["value"].asInt() == 113
                        && againB.itemData["nested"]["value"].asInt() == 223
                        && againA.itemData["list"][0].asInt() == 3 && !("stale" in againA.itemData.asMap())
                        && !("list" in againB.itemData.asMap())) {
                        event.controller.customData["multi_upgrade_script_ok"] = 1 as IData;
                    }
                });
                """.formatted(target, MultiDynamicUpgradeRegressionChecks.A, MultiDynamicUpgradeRegressionChecks.B,
                MultiDynamicUpgradeRegressionChecks.A, MultiDynamicUpgradeRegressionChecks.B);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        int before = bus.getUpgradeInventory().getStackInSlot(0).getDamageValue();
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(List.of(new LiteralSourceFile("multi_dynamic_regression.zs", script)), configuration).execute();
            } catch (Throwable failure) { throw new Exception("multi dynamic ZenCode compilation failed", failure); }
            new MachineTickEvent(controller, Phase.START).postEvent();
            require(controller.getCustomDataTag().getInt("multi_upgrade_script_ok") == 1, "compiled typed itemData/old-root/copy checks failed");
            require(bus.getUpgradeInventory().getStackInSlot(0).getDamageValue() == before + 15,
                    "native and CT wear did not accumulate on the same item");
            var first = (SimpleDynamicMachineUpgrade) controller.getMachineUpgrade(MultiDynamicUpgradeRegressionChecks.A)[0];
            var retainedData = first.getItemData(); var disk = bus.saveWithoutMetadata(); bus.load(disk);
            retainedData.put("after_reload", new com.blamejared.crafttweaker.api.data.IntData(1));
            var alpha = (DynamicMachineUpgrade) controller.getMachineUpgrade(MultiDynamicUpgradeRegressionChecks.A)[0];
            var beta = (DynamicMachineUpgrade) controller.getMachineUpgrade(MultiDynamicUpgradeRegressionChecks.B)[0];
            require(!alpha.getItemDataTag().contains("after_reload") && alpha.getItemDataTag().getCompound("nested").getInt("value") == 113
                    && beta.getItemDataTag().getCompound("nested").getInt("value") == 223,
                    "compiled typed data did not survive reload independently or old IData remained writable");
            Modularmachinery.LOG.info("[MMC][multi-dynamic-script] PASS: real CT {} same-item type isolation, nested/list/copy/old-root data, cumulative wear and reload", controller instanceof TileFactoryController ? "factory" : "ordinary");
        } finally {
            var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(target);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
