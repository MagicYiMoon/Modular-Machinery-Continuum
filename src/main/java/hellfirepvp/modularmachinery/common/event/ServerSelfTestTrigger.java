/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.MachineSelfTest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Runs {@link MachineSelfTest} after the server is up, when explicitly enabled.
 *
 * <p>Placed at {@code ServerStartedEvent} because the test needs a fully loaded level and the machine
 * and recipe definitions loaded (which happens on the reload that precedes it). Running it any earlier
 * would test against empty registries and prove nothing.
 *
 * <p>Opt-in via {@code -Dmodularmachinery.selfTest=true}: the test places blocks in the world and is
 * therefore not something to run uninvited on someone's save.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerSelfTestTrigger {

    
















 
    private static void stopAfterSuite(ServerStartedEvent event) {
        try {
            event.getServer().halt(false);
        } catch (Throwable failureToStop) {
            Modularmachinery.LOG.warn("[MMCE][selftest] could not stop the server after the suite: {}"
                    + " -- the gradle timeout will end the run as before", failureToStop.toString());
        }
    }

    private ServerSelfTestTrigger() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!MachineSelfTest.isEnabled()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            Modularmachinery.LOG.error("[MMCE][selftest] no overworld available");
            return;
        }
        // A flat area far from spawn, at a height clear of terrain, so the structure is not obstructed
        // by whatever generated. Coordinates are fixed rather than searched for: the test must be
        // reproducible, and a search would make failures hard to reproduce.
        BlockPos origin = new BlockPos(0, 100, 0);
        Modularmachinery.LOG.info("[MMCE][selftest] running at {} in {}",
                origin.toShortString(), level.dimension().location());
        try {
            boolean regression = hellfirepvp.modularmachinery.common.machine.PortRegressionChecks.run(
                    level, origin.offset(256, 0, 256));
            boolean passed = MachineSelfTest.run(level, origin);
            // Second machine at a separate origin, so the two structures cannot interfere. Its shell is
            // the wildcard variable `casings_all` and its inputs come from its own recipe definition,
            // neither of which the alloy_furnace case exercises.
            boolean second = MachineSelfTest.runCase(level, origin.offset(64, 0, 0),
                    "iron_centrifuge", "iron_centrifuge_centrifuge_grass", 1);
            Modularmachinery.LOG.info("[MMCE][selftest] OVERALL: {}",
                    passed && second && regression ? "PASS" : "FAIL (" + (passed ? "" : "case1 ")
                            + (second ? "" : "case2 ") + (regression ? "" : "regression") + ")");
        } catch (Exception e) {
            Modularmachinery.LOG.error("[MMCE][selftest] threw", e);
        } finally {
            stopAfterSuite(event);
        }
    }
}
