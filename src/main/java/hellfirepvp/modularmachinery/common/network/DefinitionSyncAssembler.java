/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.network;

import java.util.UUID;

/** Ordered, bounded assembly. A newer first chunk abandons the incomplete older transfer. */
public final class DefinitionSyncAssembler {
    private UUID transfer;
    private byte[] pending;
    private int received;

    public byte[] accept(DefinitionSyncPacket packet) {
        if (packet.offset() == 0) {
            transfer = packet.transfer();
            pending = new byte[packet.totalBytes()];
            received = 0;
        }
        if (pending == null || !packet.transfer().equals(transfer)
                || pending.length != packet.totalBytes() || received != packet.offset()) {
            reset();
            throw new IllegalArgumentException("Out-of-order machine definition snapshot");
        }
        System.arraycopy(packet.data(), 0, pending, received, packet.data().length);
        received += packet.data().length;
        if (received != pending.length) return null;
        byte[] complete = pending;
        reset();
        return complete;
    }

    public void reset() {
        transfer = null;
        pending = null;
        received = 0;
    }
}
