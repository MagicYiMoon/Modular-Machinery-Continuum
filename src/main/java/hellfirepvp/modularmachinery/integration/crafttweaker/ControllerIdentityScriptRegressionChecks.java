/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.machine.ScriptMachineEventRegistry;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.world.level.block.Blocks;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Real native Level and Player exports, including same-thread world reads and reversible writes. */
public final class ControllerIdentityScriptRegressionChecks {
    private ControllerIdentityScriptRegressionChecks() { }
    public static void run(TileController tile, String target, UUID owner, String ownerName, boolean online) throws Exception {
        String marker = online ? "identity_online_ok" : "identity_offline_ok";
        String script = """
                import mods.modularmachinery.MMEvents;
                import crafttweaker.api.data.IData;
                MMEvents.onMachinePreTick("%s", (event) => {
                    val c = event.controller;
                    val world = c.world;
                    if (world == null || world.dimension.path != "%s" || world.gameTime < 0
                        || c.ownerUUID != "%s" || c.ownerName != "%s") return;
                    val player = c.ownerPlayer;
                    if (%s) {
                        if (player == null || player.getStringUUID() != "%s") return;
                    } else if (player != null) return;
                    val sample = c.pos.offset(1, 0, 0);
                    val markerPos = c.pos.offset(0, 2, 0);
                    if (!world.isLoaded(sample) || !world.isLoaded(markerPos)) return;
                    if (world.getBlockState(sample) != <blockstate:minecraft:stone>) return;
                    world.setBlockAndUpdate(markerPos, <blockstate:minecraft:diamond_block>);
                    if (world.getBlockState(markerPos) == <blockstate:minecraft:diamond_block>)
                        c.customData["%s"] = 1 as IData;
                });
                """.formatted(target, tile.getLevel().dimension().location().getPath(), owner, ownerName, online, owner, marker);
        var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
        var handlers = (Map<?, ?>) field.get(null); Object previous = handlers.remove(target);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        var markerPos = tile.getBlockPos().offset(0, 2, 0); var previousBlock = tile.getLevel().getBlockState(markerPos);
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(List.of(new LiteralSourceFile("controller_identity_regression.zs", script)), configuration).execute();
            } catch (Throwable failure) { throw new Exception("controller identity ZenCode compilation failed", failure); }
            new MachineTickEvent(tile, Phase.START).postEvent();
            require(tile.getCustomDataTag().getInt(marker) == 1 && tile.getLevel().getBlockState(markerPos).is(Blocks.DIAMOND_BLOCK),
                    "compiled native world/owner callback failed in " + tile.getLevel().dimension().location() + " online=" + online);
            require(tile.getOwner().equals(owner), "script queries changed controller ownership");
            Modularmachinery.LOG.info("[MMC][controller-identity-script] PASS: {} {} {} online={} native Level read/write, cached owner and Player export",
                    tile.getBlockState().getBlock(), tile instanceof TileFactoryController ? "factory" : "ordinary",
                    tile.getLevel().dimension().location(), online);
        } finally {
            tile.getLevel().setBlock(markerPos, previousBlock, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            handlers.remove(target);
            if (previous != null) restoreHandler(handlers, target, previous);
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreHandler(Map handlers, String key, Object value) { handlers.put(key, value); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
