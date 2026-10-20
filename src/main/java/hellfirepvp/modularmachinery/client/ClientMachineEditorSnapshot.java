/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client;

import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reassembles the editor document sent after the small vanilla open-menu packet. */
public final class ClientMachineEditorSnapshot {
    private static final Map<UUID, Assembly> PENDING = new HashMap<>();

    private ClientMachineEditorSnapshot() {}

    public static void receiveResult(hellfirepvp.modularmachinery.common.network.PktMachineEditorResult packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof MenuMachineEditor menu
                && menu.containerId == packet.containerId()) menu.saves().receive(packet.result());
    }

    public static void receive(ModularmachineryNetwork.PktMachineEditorSnapshot packet) {
        if (packet.totalBytes() > ModularmachineryNetwork.PktMachineEditorSnapshot.MAX_SNAPSHOT_BYTES) return;
        Assembly assembly = PENDING.computeIfAbsent(packet.transfer(), ignored ->
                new Assembly(packet.containerId(), packet.totalBytes()));
        if (assembly.containerId != packet.containerId() || assembly.totalBytes != packet.totalBytes()) {
            PENDING.remove(packet.transfer());
            return;
        }
        int chunk = packet.offset() / ModularmachineryNetwork.PktMachineEditorSnapshot.CHUNK_BYTES;
        if (!assembly.received.get(chunk)) {
            System.arraycopy(packet.data(), 0, assembly.data, packet.offset(), packet.data().length);
            assembly.received.set(chunk);
            assembly.receivedBytes += packet.data().length;
        }
        if (assembly.receivedBytes == assembly.totalBytes) {
            applyOrKeepPending(packet.transfer(), assembly);
        }
    }

    /** Called by the screen once the client-side menu has been constructed. */
    public static void menuOpened(MenuMachineEditor menu) {
        PENDING.entrySet().removeIf(entry -> {
            Assembly assembly = entry.getValue();
            if (assembly.containerId != menu.containerId || assembly.receivedBytes != assembly.totalBytes) {
                return false;
            }
            menu.acceptSnapshot(entry.getKey(), new String(assembly.data, StandardCharsets.UTF_8));
            return true;
        });
    }

    private static void applyOrKeepPending(UUID transfer, Assembly assembly) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.containerMenu instanceof MenuMachineEditor menu
                && menu.containerId == assembly.containerId) {
            menu.acceptSnapshot(transfer, new String(assembly.data, StandardCharsets.UTF_8));
            PENDING.remove(transfer);
        }
    }

    private static final class Assembly {
        private final int containerId;
        private final int totalBytes;
        private final byte[] data;
        private final BitSet received = new BitSet();
        private int receivedBytes;

        private Assembly(int containerId, int totalBytes) {
            this.containerId = containerId;
            this.totalBytes = totalBytes;
            this.data = new byte[totalBytes];
        }
    }
}
