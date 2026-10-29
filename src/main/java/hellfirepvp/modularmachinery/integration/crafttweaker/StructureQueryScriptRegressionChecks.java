/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Compiles real overloaded selectors, predicates and nullable dynamic observations. */
public final class StructureQueryScriptRegressionChecks {
    private StructureQueryScriptRegressionChecks() { }
    public static void run(TileController tile) throws Exception {
        cleanup(); String target = StructureQueryRegressionChecks.TARGET;
        String script = """
                import mods.modularmachinery.MMEvents;
                import mods.modularmachinery.IMachineController;
                import crafttweaker.api.data.IData;
                public function queryCheck(c as IMachineController) as bool {
                    val line = c.getDynamicPattern("line");
                    val optional = c.getDynamicPattern("optional");
                    if (line == null || optional == null || c.getDynamicPattern("missing") != null) return false;
                    if (line.name != "line" || line.size != 2 || line.minSize != 1 || line.maxSize != 4
                        || line.facing != "%s" || optional.size != 0 || optional.facing != "UP") return false;
                    if (c.getBlocksInPattern("minecraft:stone") != 2 || c.getBlocksInPattern(<item:minecraft:stone>) != 2
                        || c.getBlocksInPattern(<blockstate:minecraft:stone>) != 2
                        || c.getBlocksInPattern((state) => state == <blockstate:minecraft:stone>) != 2) return false;
                    if (c.getBlocksInPattern((state) => true) != 8 || c.getBlocksInPattern("minecraft:air") != 0
                        || c.getBlocksInPattern(<item:minecraft:stick>) != 0) return false;
                    val positions = c.getBlockPosInPattern("minecraft:stone");
                    if (positions.length != 2 || c.getBlockPosInPattern(<item:minecraft:stone>).length != 2
                        || c.getBlockPosInPattern(<blockstate:minecraft:stone>).length != 2
                        || c.getBlockPosInPattern((state) => state == <blockstate:minecraft:stone>).length != 2) return false;
                    val world = c.pos.offset(positions[0]);
                    return positions[0].x == %d && positions[0].y == 0 && positions[0].z == %d
                        && world.x == c.pos.x + %d && world.z == c.pos.z + %d;
                }
                MMEvents.onStructureFormed("%s", (event) => {
                    if (queryCheck(event.controller)) event.controller.customData["structure_formed_ok"] = 1 as IData;
                });
                MMEvents.onStructureUpdate("%s", (event) => {
                    val c = event.controller;
                    if (c.formedMachineName == null) {
                        if (c.getDynamicPattern("line") == null && c.getBlocksInPattern("minecraft:stone") == 0)
                            c.customData["structure_unformed_ok"] = 1 as IData;
                    } else if (queryCheck(c)) c.customData["structure_update_ok"] = 1 as IData;
                });
                """;
        var offset = tile.getBlockPosInPattern("minecraft:stone")[0];
        script = script.formatted(StructureQueryRegressionChecks.sourceMatchFacing(tile.getFacing()), offset.getX(), offset.getZ(), offset.getX(), offset.getZ(),
                target, target);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            CraftTweakerAPI.getScriptRunManager().createScriptRun(List.of(new LiteralSourceFile("structure_queries_regression.zs", script)), configuration).execute();
        } catch (Throwable failure) { throw new Exception("structure query ZenCode compilation failed", failure); }
        tile.setFormed(false);
        var machine = MachineReloadListener.getLoader().findByName(target);
        var attempt = MachineFormation.tryForm((net.minecraft.server.level.ServerLevel) tile.getLevel(), tile.getBlockPos(), tile.getFacing(), machine, MachineReloadListener.getLoader());
        require(attempt.formed(), "CT reformation fixture failed"); tile.setFormed(true);
        require(tile.getCustomDataTag().getInt("structure_formed_ok") == 1 && tile.getCustomDataTag().getInt("structure_update_ok") == 1
                && tile.getCustomDataTag().getInt("structure_unformed_ok") == 1, "real compiled structure callbacks did not observe correct results");
        Modularmachinery.LOG.info("[MMC][structure-query-script] PASS: {} {} dynamic metadata, all selectors, predicates, relative positions and formed/update/unformed events",
                tile instanceof TileFactoryController ? "factory" : "ordinary", tile.getFacing());
        cleanup();
    }
    public static void cleanup() throws Exception {
        var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).remove(StructureQueryRegressionChecks.TARGET);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
