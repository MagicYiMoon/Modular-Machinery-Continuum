/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.registry;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Client-only registration cache. Contains identities, never remote machine files or scripts. */
public final class ControllerRegistryCache {
    public static final String FILE_NAME = "server-controller-cache.json";
    public static final int MAX_ENTRIES = 16384;
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private ControllerRegistryCache() {}

    public static Path path() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modularmachinery").resolve(FILE_NAME);
    }

    public static JsonArray encode(Collection<DedicatedControllers.Definition> definitions) {
        var array = new JsonArray();
        for (var definition : definitions) {
            var object = new JsonObject();
            object.addProperty("registryname", definition.name());
            object.addProperty("has-factory", definition.factory());
            array.add(object);
        }
        return array;
    }

    public static List<DedicatedControllers.Definition> decode(JsonElement json) {
        if (!json.isJsonArray() || json.getAsJsonArray().size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid controller registry size");
        var entries = new TreeMap<String, DedicatedControllers.Definition>();
        for (var element : json.getAsJsonArray()) {
            var object = element.getAsJsonObject();
            if (object.size() != 2 || !object.has("registryname") || !object.has("has-factory")
                    || !object.get("registryname").isJsonPrimitive() || !object.get("registryname").getAsJsonPrimitive().isString()
                    || !object.get("has-factory").isJsonPrimitive() || !object.get("has-factory").getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Invalid controller registry entry");
            var entry = DedicatedControllers.parse(object);
            if (entry.name().length() > 256 || entries.putIfAbsent(entry.name(), entry) != null)
                throw new IllegalArgumentException("Duplicate or oversized controller identity: " + entry.name());
        }
        validateIdentities(entries.values());
        return List.copyOf(entries.values());
    }

    public static List<DedicatedControllers.Definition> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > MAX_BYTES)
            throw new IOException("Invalid controller registry cache: " + file);
        return decode(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
    }

    /** Merge identities monotonically: keeping other servers' entries preserves local saved worlds. */
    public static List<DedicatedControllers.Definition> merge(Collection<DedicatedControllers.Definition> local,
                                                              Collection<DedicatedControllers.Definition> remote) {
        var merged = new TreeMap<String, DedicatedControllers.Definition>();
        for (var entries : List.of(local, remote)) for (var entry : entries) {
            var old = merged.get(entry.name());
            merged.put(entry.name(), old == null ? entry : new DedicatedControllers.Definition(
                    old.name(), old.displayName(), old.color(), old.factory() || entry.factory()));
        }
        if (merged.size() > MAX_ENTRIES) throw new IllegalArgumentException("Controller registry cache is full");
        validateIdentities(merged.values());
        return List.copyOf(merged.values());
    }

    /** Detect a normal foo_factory controller colliding with foo's factory controller before saving. */
    public static void validateIdentities(Collection<DedicatedControllers.Definition> entries) {
        var ids = new HashSet<String>();
        for (var entry : entries) {
            if (!ids.add(entry.name() + "_controller")
                    || entry.factory() && !ids.add(entry.name() + "_factory_controller"))
                throw new IllegalArgumentException("Conflicting controller identity: " + entry.name());
        }
    }

    public static synchronized boolean store(Path file, Collection<DedicatedControllers.Definition> remote,
                                              Collection<DedicatedControllers.Definition> registered) throws IOException {
        var old = read(file);
        var merged = merge(old, remote);
        // Registered local blocks remain authoritative in this process. Never write a cache which
        // would make the next startup ambiguous across two different machine identities.
        merge(registered, merged);
        String json = encode(merged).toString();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Controller registry cache exceeds 4 MiB");
        if (Files.exists(file) && encode(old).equals(encode(merged))) return false;
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "controller-cache-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        return true;
    }

    public static List<String> missing(Collection<DedicatedControllers.Definition> remote,
                                        Collection<DedicatedControllers.Definition> registered) {
        var available = new HashMap<String, DedicatedControllers.Definition>();
        registered.forEach(entry -> available.put(entry.name(), entry));
        var missing = new ArrayList<String>();
        for (var entry : remote) {
            var local = available.get(entry.name());
            if (local == null) missing.add(entry.name() + "_controller");
            if (entry.factory() && (local == null || !local.factory())) missing.add(entry.name() + "_factory_controller");
        }
        return List.copyOf(missing);
    }

    public static void cachePlaySnapshot(JsonElement json) {
        try {
            var entries = decode(json);
            if (store(path(), entries, DedicatedControllers.registrationDefinitions()))
                Modularmachinery.LOG.info("[MMC][controller-registry-sync] cached {} server machine identities", entries.size());
        } catch (IOException | RuntimeException failure) {
            Modularmachinery.LOG.error("[MMC] Could not cache server controller identities; runtime definitions are still usable", failure);
        }
    }
}
