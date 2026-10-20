/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.client;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.network.DefinitionSyncPacket;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;

/** Client-thread assembly; publish both registries together only after the entire snapshot parses. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientDefinitionSync {
    private static final hellfirepvp.modularmachinery.common.network.DefinitionSyncAssembler ASSEMBLER =
            new hellfirepvp.modularmachinery.common.network.DefinitionSyncAssembler();

    private ClientDefinitionSync() {}

    public static void receive(DefinitionSyncPacket packet) {
        try {
            byte[] complete = ASSEMBLER.accept(packet);
            if (complete != null) {
                var json = JsonParser.parseString(new String(complete, StandardCharsets.UTF_8)).getAsJsonObject();
                MachineLoader machines = MachineLoader.fromSnapshot(json.getAsJsonObject("definitions"));
                RecipeLoader recipes = RecipeLoader.fromSnapshot(json.getAsJsonArray("recipes"), machines);
                GeoMachineModelRegistry.installSnapshot(json.has("geoModels")
                        ? json.getAsJsonArray("geoModels") : null);
                // A definition reload can change the selected model while the block entity stays
                // loaded.  Rebuild render sections immediately so the new model is visible without
                // a chunk reload; the GeckoLib resource listener handles baked resource changes.
                if (net.minecraft.client.Minecraft.getInstance().level != null) {
                    net.minecraft.client.Minecraft.getInstance().levelRenderer.allChanged();
                }
                MachineReloadListener.installClientDefinitions(new MachineReloadListener.Definitions(machines, recipes));
                Modularmachinery.LOG.info("[MMCE] synchronized {} machines and {} recipes from server",
                        machines.getMachines().size(), recipes.getRecipeNames().size());
            }
        } catch (RuntimeException failure) {
            ASSEMBLER.reset();
            Modularmachinery.LOG.error("[MMCE] invalid server definitions; keeping previous client view", failure);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ASSEMBLER.reset();
        MachineReloadListener.clearClientDefinitions();
        GeoMachineModelRegistry.clearClientSnapshot();
        BlockModelHider.clear();
    }
}
