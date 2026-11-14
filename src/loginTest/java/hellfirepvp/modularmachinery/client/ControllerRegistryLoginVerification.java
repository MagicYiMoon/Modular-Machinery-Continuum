/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in real remote-login driver; excluded from normal compilation and all distributable jars. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT)
public final class ControllerRegistryLoginVerification {
    private static int ticks, phase;
    private static long started;
    private static byte[] localFile, localManifest;
    private static final String REMOTE = "network_only_machine";
    private static Path config() { return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modularmachinery"); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        String mode = System.getProperty("modularmachinery.controllerLoginTest", "");
        if (mode.isEmpty() || phase == 99) return;
        try {
            if (started == 0) started = System.currentTimeMillis();
            require(System.currentTimeMillis() - started < 180_000, "remote login timed out; phase=" + phase);
            if (phase == 0) {
                if (mc.getOverlay() != null || mc.screen == null || ++ticks < 60) return;
                localFile = Files.readAllBytes(config().resolve("machinery/client_only_machine.json"));
                localManifest = Files.readAllBytes(config().resolve("controller-registry.json"));
                String address = System.getProperty("modularmachinery.controllerLoginAddress");
                phase = 1;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                        new ServerData("MMC isolated login test", address, false), false);
                Modularmachinery.LOG.info("[MMC][login-verification] connecting in {} mode", mode);
            } else if (mode.equals("restart")) {
                require(mc.level == null, "first connection unexpectedly entered without registering the missing blocks");
                if (!(mc.screen instanceof DisconnectedScreen)) return;
                var cache = ControllerRegistryCache.read(ControllerRegistryCache.path());
                require(cache.stream().anyMatch(e -> e.name().equals(REMOTE) && e.factory()), "missing server identities were not cached");
                require(DedicatedControllers.stack(REMOTE, false).isEmpty(), "test did not start with missing client identities");
                checkFiles();
                finish(mc, "first connection cached identities and disconnected for restart");
            } else if (phase == 1) {
                if (mc.screen instanceof DisconnectedScreen) throw new AssertionError("cached client was disconnected before joining");
                if (mc.player == null || mc.level == null || MachineReloadListener.getLoader().findByName(REMOTE) == null) return;
                require(!DedicatedControllers.stack(REMOTE, false).isEmpty() && !DedicatedControllers.stack(REMOTE, true).isEmpty(),
                        "cached startup did not register normal and factory controllers");
                require(MachineReloadListener.getLoader().findByName("client_only_machine") == null, "local-only definition leaked into remote server data");
                mc.player.connection.sendCommand("modularmachinery gui");
                phase = 2;
            } else if (phase == 2) {
                if (!(mc.player.containerMenu instanceof MenuMachineEditor menu)) return;
                var document = JsonParser.parseString(menu.snapshot()).getAsJsonObject();
                boolean listed = false;
                for (var entry : document.getAsJsonArray("machines"))
                    if (entry.getAsJsonObject().get("name").getAsString().equals(REMOTE)) listed = true;
                if (!listed) return;
                var machine = MachineReloadListener.getLoader().findByName(REMOTE);
                ModularmachineryNetwork.CHANNEL.sendToServer(new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(),
                        EditorSaveState.revision(menu.snapshot()), new PktMachineEditorControllerSettings(
                        REMOTE, 0x12ABCD, machine.isHasFactory(), true)));
                phase = 3;
            } else if (phase == 3) {
                var machine = MachineReloadListener.getLoader().findByName(REMOTE);
                if ((machine.getDefinedColor() & 0xFFFFFF) != 0x12ABCD || !machine.isRequiresBlueprint()) return;
                require(mc.player.containerMenu instanceof MenuMachineEditor, "editor unexpectedly closed during save");
                checkFiles();
                finish(mc, "cached startup joined with different machine files and extra client IDs; remote GUI edit synchronized without overwriting author files");
            }
        } catch (Throwable failure) {
            Modularmachinery.LOG.error("[MMC][login-verification] FAIL", failure);
            phase = 99;
            mc.stop();
        }
    }
    private static void checkFiles() throws Exception {
        require(Arrays.equals(localFile, Files.readAllBytes(config().resolve("machinery/client_only_machine.json"))), "local author machine was overwritten");
        require(Arrays.equals(localManifest, Files.readAllBytes(config().resolve("controller-registry.json"))), "login overwrote the local controller manifest");
        require(!Files.exists(config().resolve("machinery/" + REMOTE + ".json")), "server machine JSON was written into author files");
    }
    private static void finish(Minecraft mc, String detail) {
        Modularmachinery.LOG.info("[MMC][login-verification] PASS: {}", detail);
        phase = 99;
        mc.stop();
    }
    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
