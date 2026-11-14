/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ControllerRegistrySyncRegressionChecks {
    private ControllerRegistrySyncRegressionChecks() {}
    private static DedicatedControllers.Definition entry(String name, boolean factory) {
        return new DedicatedControllers.Definition(name, name, 0xFFFFFF, factory);
    }
    public static void run() throws Exception {
        var local = List.of(entry("local_machine", false), entry("shared_machine", false));
        var server = List.of(entry("remote_machine", true), entry("shared_machine", true));
        var merged = ControllerRegistryCache.merge(local, server);
        require(ControllerRegistryCache.missing(server, local).size() == 3, "normal/factory missing IDs not detected");
        require(ControllerRegistryCache.missing(server, merged).isEmpty(), "restart registration plan still misses server IDs");
        require(ControllerRegistryCache.missing(local, merged).isEmpty(), "local identities were erased");
        var wire = ControllerRegistryCache.encode(server);
        require(ControllerRegistryCache.encode(ControllerRegistryCache.decode(wire)).equals(wire), "manifest JSON roundtrip");
        var temp = Files.createTempDirectory("mmc-controller-cache-test-");
        try {
            Path file = temp.resolve(ControllerRegistryCache.FILE_NAME);
            Path machine = temp.resolve("local-machine.json"), manifest = temp.resolve("controller-registry.json");
            Files.writeString(machine, "local machine file must stay intact");
            Files.writeString(manifest, "local tombstone manifest must stay intact");
            require(ControllerRegistryCache.store(file, server, local), "initial cache not saved");
            byte[] saved = Files.readAllBytes(file);
            var timestamp = Files.getLastModifiedTime(file);
            require(!ControllerRegistryCache.store(file, server, local) && Files.getLastModifiedTime(file).equals(timestamp), "unchanged cache rewritten");
            require(!ControllerRegistryCache.store(file, List.of(), local), "server removal discarded cached identities");
            require(Arrays.equals(saved, Files.readAllBytes(file)), "empty server snapshot erased cache");
            require(Files.readString(machine).equals("local machine file must stay intact")
                    && Files.readString(manifest).equals("local tombstone manifest must stay intact"), "author files overwritten");
            require(ControllerRegistryCache.read(file).size() == 2, "remote cache included local machine files");
            boolean refused = false;
            try { ControllerRegistryCache.store(file, List.of(entry("shared_machine_factory", false)), local); }
            catch (IllegalArgumentException expected) { refused = true; }
            require(refused && Arrays.equals(saved, Files.readAllBytes(file)), "conflicting identities corrupted cache");
            for (String invalid : List.of("{}", "[{\"registryname\":\"../escape\",\"has-factory\":false}]",
                    "[{\"registryname\":\"a\",\"has-factory\":\"true\"}]",
                    "[{\"registryname\":\"a\",\"has-factory\":false,\"parts\":[]}]",
                    "[{\"registryname\":\"a\",\"has-factory\":false},{\"registryname\":\"a\",\"has-factory\":true}]")) {
                refused = false;
                try { ControllerRegistryCache.decode(JsonParser.parseString(invalid)); }
                catch (RuntimeException expected) { refused = true; }
                require(refused, "invalid registration manifest accepted: " + invalid);
            }
        } finally {
            try (var files = Files.walk(temp)) {
                for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
        // Exercise multiple real login chunks without changing the live registry or any author file.
        var many = new ArrayList<DedicatedControllers.Definition>();
        for (int i = 0; i < 6500; i++) many.add(entry("remote_bulk_machine_" + i, true));
        byte[] bytes = ControllerRegistryCache.encode(many).toString().getBytes(StandardCharsets.UTF_8);
        require(bytes.length > DefinitionSyncPacket.CHUNK_BYTES, "multi-chunk fixture too small");
        var assembler = new DefinitionSyncAssembler();
        UUID transfer = UUID.randomUUID(); byte[] complete = null;
        for (int offset = 0; offset < bytes.length; offset += DefinitionSyncPacket.CHUNK_BYTES) {
            var chunk = new DefinitionSyncPacket(transfer, bytes.length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + DefinitionSyncPacket.CHUNK_BYTES)));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PktControllerRegistryLogin.encode(new PktControllerRegistryLogin(chunk), buffer);
                complete = assembler.accept(PktControllerRegistryLogin.decode(buffer).chunk());
                require(buffer.readableBytes() == 0, "login packet codec left bytes");
            } finally { buffer.release(); }
        }
        require(Arrays.equals(bytes, complete) && ControllerRegistryCache.decode(JsonParser.parseString(new String(complete, StandardCharsets.UTF_8))).size() == 6500,
                "bounded multi-chunk login manifest corrupted");
        require(PktControllerRegistryLogin.packets(true).isEmpty(), "integrated server needlessly sends login cache");
        var gather = NetworkRegistry.class.getDeclaredMethod("gatherLoginPayloads", NetworkDirection.class, boolean.class);
        gather.setAccessible(true);
        @SuppressWarnings("unchecked")
        var payloads = (List<NetworkRegistry.LoginPayload>) gather.invoke(null, NetworkDirection.LOGIN_TO_CLIENT, false);
        try {
            require(!payloads.isEmpty() && payloads.get(0).getMessageContext().equals(PktControllerRegistryLogin.CONTEXT),
                    "actual Forge login list did not put MMC cache ahead of registry snapshots");
            require(!payloads.get(0).needsResponse(), "identity cache incorrectly waits for an unsent login ACK");
        } finally { for (var payload : payloads) payload.getData().release(); }
        Modularmachinery.LOG.info("[MMC][controller-registry-sync] PASS: missing normal/factory identities, startup union, author-file isolation, no-op/removal preservation, conflict/invalid data rejection, bounded multi-chunk codec, integrated-server skip and actual Forge login ordering");
    }
    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError("Controller registry sync: " + message);
    }
}
