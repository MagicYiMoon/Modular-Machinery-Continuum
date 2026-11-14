/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.ControllerRegistryCache;
import hellfirepvp.modularmachinery.common.registry.DedicatedControllers;
import io.netty.util.AttributeKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import org.apache.commons.lang3.tuple.Pair;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Sent before Forge registry snapshots; missing identities are cached for the next client startup. */
public record PktControllerRegistryLogin(DefinitionSyncPacket chunk) {
    public static final String CONTEXT = "MMC controller identities";
    private static final AttributeKey<DefinitionSyncAssembler> ASSEMBLER = AttributeKey.valueOf("modularmachinery:controller_registry_login");

    public PktControllerRegistryLogin {
        if (chunk.totalBytes() > ControllerRegistryCache.MAX_BYTES)
            throw new IllegalArgumentException("Controller registry exceeds 4 MiB");
    }

    public static List<Pair<String, PktControllerRegistryLogin>> packets(boolean local) {
        if (local) return List.of(); // An integrated server already shares its client's frozen registries.
        byte[] bytes = ControllerRegistryCache.encode(DedicatedControllers.registrationDefinitions()).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ControllerRegistryCache.MAX_BYTES) throw new IllegalArgumentException("Controller registry exceeds 4 MiB");
        var packets = new ArrayList<Pair<String, PktControllerRegistryLogin>>();
        UUID transfer = UUID.randomUUID();
        for (int offset = 0; offset < bytes.length; offset += DefinitionSyncPacket.CHUNK_BYTES) {
            var chunk = new DefinitionSyncPacket(transfer, bytes.length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + DefinitionSyncPacket.CHUNK_BYTES)));
            packets.add(Pair.of(CONTEXT, new PktControllerRegistryLogin(chunk)));
        }
        return List.copyOf(packets);
    }

    public static void encode(PktControllerRegistryLogin packet, FriendlyByteBuf buffer) {
        DefinitionSyncPacket.encode(packet.chunk, buffer);
    }
    public static PktControllerRegistryLogin decode(FriendlyByteBuf buffer) {
        return new PktControllerRegistryLogin(DefinitionSyncPacket.decode(buffer));
    }

    public static void handle(PktControllerRegistryLogin packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.setPacketHandled(true);
        // Deliberately complete this small, bounded file write on the connection's network thread.
        // Scheduling it later could let Forge reject a registry snapshot before the cache is saved.
        try {
            var attr = context.attr(ASSEMBLER);
            if (attr.get() == null) attr.set(new DefinitionSyncAssembler());
            byte[] bytes = attr.get().accept(packet.chunk);
            if (bytes == null) return;
            attr.set(null);
            var remote = ControllerRegistryCache.decode(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
            var registered = DedicatedControllers.registrationDefinitions();
            ControllerRegistryCache.store(ControllerRegistryCache.path(), remote, registered);
            var missing = ControllerRegistryCache.missing(remote, registered);
            Modularmachinery.LOG.info("[MMC][controller-registry-sync] received {} machine identities before Forge registry sync; missing={}", remote.size(), missing);
            if (!missing.isEmpty()) {
                context.getNetworkManager().disconnect(Component.translatable("message.modularmachinery.controller_registry_restart", missing.size()));
            }
        } catch (Exception failure) {
            context.attr(ASSEMBLER).set(null);
            Modularmachinery.LOG.error("[MMC] Cannot prepare controller registrations for this server", failure);
            context.getNetworkManager().disconnect(Component.translatable("message.modularmachinery.controller_registry_failed"));
        }
    }
}
