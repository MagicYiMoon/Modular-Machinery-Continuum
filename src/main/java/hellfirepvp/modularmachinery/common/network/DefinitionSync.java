/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Login and reload use the same server-authoritative snapshot, including expanded adapters. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DefinitionSync {
    private DefinitionSync() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) MachineReloadListener.refreshRecipes();
        send(event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer()));
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        MachineReloadListener.refreshRecipes();
    }

    public static void sendToAll(MinecraftServer server) {
        send(server.getPlayerList().getPlayers());
    }

    public static JsonObject snapshot(MachineReloadListener.Definitions definitions) {
        JsonObject json = new JsonObject();
        json.add("definitions", definitions.machines().snapshot());
        json.add("recipes", definitions.recipes().snapshot());
        json.add("geoModels", GeoMachineModelRegistry.snapshot());
        return json;
    }

    private static void send(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        try {
            byte[] bytes = snapshot(MachineReloadListener.serverDefinitions()).toString()
                    .getBytes(StandardCharsets.UTF_8);
            if (bytes.length > DefinitionSyncPacket.MAX_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException("Machine definitions exceed 16 MiB network limit");
            }
            UUID transfer = UUID.randomUUID();
            for (int offset = 0; offset < bytes.length; offset += DefinitionSyncPacket.CHUNK_BYTES) {
                var packet = new DefinitionSyncPacket(transfer, bytes.length, offset,
                        Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + DefinitionSyncPacket.CHUNK_BYTES)));
                for (ServerPlayer player : players) {
                    ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
        } catch (RuntimeException failure) {
            Modularmachinery.LOG.error("[MMCE] could not synchronize machine definitions", failure);
        }
    }
}
