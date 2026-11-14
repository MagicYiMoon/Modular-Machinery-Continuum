/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-side owner and lifecycle table for survival assembly jobs. */
public final class MachineAssemblyManager {
    private record Key(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                       BlockPos pos) { }

    private static final Map<Key, MachineAssembly> JOBS = new LinkedHashMap<>();

    private MachineAssemblyManager() { }

    public static boolean contains(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        return JOBS.containsKey(new Key(level.dimension(), pos));
    }

    public static void add(net.minecraft.server.level.ServerLevel level, BlockPos pos, MachineAssembly assembly) {
        MachineAssembly previous = JOBS.put(new Key(level.dimension(), pos), assembly);
        if (previous != null && previous != assembly) previous.close();
    }

    public static List<MachineAssembly> forPlayer(ServerPlayer player) {
        List<MachineAssembly> result = new ArrayList<>();
        for (MachineAssembly assembly : JOBS.values()) {
            // The source stores the player reference in the job.  Jobs are only ticked while their owner is online,
            // and logout removes them below; the manager therefore needs no second owner index.
            if (assembly.owner() == player) result.add(assembly);
        }
        return result;
    }

    public static void remove(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        MachineAssembly removed = JOBS.remove(new Key(level.dimension(), pos));
        if (removed != null) removed.close();
    }

    public static void removePlayer(ServerPlayer player) {
        JOBS.entrySet().removeIf(entry -> {
            if (entry.getValue().owner() != player) return false;
            entry.getValue().close();
            return true;
        });
    }

    public static void tick(ServerPlayer player) {
        var iterator = JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            MachineAssembly assembly = entry.getValue();
            if (assembly.owner() != player) continue;
            if (!assembly.controllerValid()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.assembly.tip.cancelled"), false);
                assembly.close();
                iterator.remove();
                continue;
            }
            assembly.tick();
            if (assembly.isFinished()) {
                assembly.close();
                iterator.remove();
            }
        }
    }
}
