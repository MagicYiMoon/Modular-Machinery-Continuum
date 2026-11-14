/* SPDX-License-Identifier: GPL-3.0-only
 * Item-to-module binding follows NECO ServerModuleRegistry, b37fbb53.
 * Bounded, atomic server JSON catalogs are an MMC adaptation.
 */
package hellfirepvp.modularmachinery.common.hypernet.server;

import com.google.gson.*;
import com.google.gson.stream.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class ServerModuleRegistry {
    public static final int MAX_FILES = 1024, MAX_FILE_BYTES = 16 * 1024, MAX_BATCH_BYTES = 4 * 1024 * 1024;
    /** Immutable pinned catalog. A future menu must close on catalog replacement. */
    public static final class Catalog {
        private final Map<ResourceLocation, ServerModuleDefinition> definitions, byItem;
        private final String revision;
        private final boolean valid;
        private Catalog(Collection<ServerModuleDefinition> values, String revision, boolean valid) {
            if (values.size() > MAX_FILES) throw new IllegalArgumentException("Too many server modules");
            var ids = new LinkedHashMap<ResourceLocation, ServerModuleDefinition>();
            var items = new HashMap<ResourceLocation, ServerModuleDefinition>();
            for (var definition : values) {
                if (ids.putIfAbsent(definition.id(), definition) != null)
                    throw new IllegalArgumentException("Duplicate server module ID: " + definition.id());
                if (items.putIfAbsent(definition.item(), definition) != null)
                    throw new IllegalArgumentException("Ambiguous server module item: " + definition.item());
            }
            definitions = Collections.unmodifiableMap(ids); byItem = Map.copyOf(items);
            this.revision = Objects.requireNonNull(revision); this.valid = valid;
        }
        public Map<ResourceLocation, ServerModuleDefinition> definitions() { return definitions; }
        public String revision() { return revision; }
        public boolean valid() { return valid; }
        public ServerModuleDefinition find(ItemStack stack) {
            return stack.isEmpty() ? null : byItem.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }
        public ServerAssemblyLayout.Kind kind(ItemStack stack) {
            var definition = find(stack); return definition == null ? null : definition.type().kind();
        }
        public ServerAssemblyInventory open(ItemStack server) {
            if (!valid) throw new IllegalStateException("Server module catalog is invalid");
            return new ServerAssemblyInventory(server, this::kind);
        }
    }
    private static volatile Catalog loaded = new Catalog(List.of(), "unloaded", false);
    public static Catalog current() { return loaded; }
    /** Used by author validation and isolated fixtures; publishes nothing. */
    public static Catalog validate(Collection<ServerModuleDefinition> values) {
        return new Catalog(values, "validated", true);
    }
    public static Catalog readCatalog(Path directory) throws IOException {
        Files.createDirectories(directory);
        var root = directory.toRealPath();
        final List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .limit(MAX_FILES + 1L).sorted().toList();
        }
        if (files.size() > MAX_FILES) throw new IllegalArgumentException("Too many server module files");
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        var values = new ArrayList<ServerModuleDefinition>(); int total = 0;
        for (var file : files) {
            var real = file.toRealPath();
            if (!real.startsWith(root)) throw new IllegalArgumentException("Server module source outside directory: " + file);
            final byte[] bytes;
            try (var stream = Files.newInputStream(real)) { bytes = stream.readNBytes(MAX_FILE_BYTES + 1); }
            if (bytes.length > MAX_FILE_BYTES || (total += bytes.length) > MAX_BATCH_BYTES)
                throw new IllegalArgumentException("Server module JSON exceeds size limit: " + file);
            try {
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                values.add(ServerModuleDefinition.parse(parse(text)));
            } catch (Exception invalid) { throw new IllegalArgumentException("Invalid server module file: " + file, invalid); }
            byte[] name = root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(name.length).array()); digest.update(name);
            digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
        }
        return new Catalog(values, HexFormat.of().formatHex(digest.digest()), true);
    }
    /** Invalid replacement closes the catalog, retaining physical items and files for recovery. */
    public static boolean reload(Path directory) {
        try {
            var candidate = readCatalog(directory); loaded = candidate;
            Modularmachinery.LOG.info("[MMC] loaded {} server module definitions", candidate.definitions().size());
            return true;
        } catch (Exception failure) {
            loaded = new Catalog(List.of(), "invalid", false);
            Modularmachinery.LOG.error("[MMC] invalid server module catalog; new server assembly disabled", failure);
            return false;
        }
    }
    /** Strict bounded parser, including duplicate-key rejection before building a JSON tree. */
    public static JsonObject parse(String text) throws IOException {
        if (text.length() > MAX_FILE_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Server module JSON exceeds 16 KiB");
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            var element = read(reader, 0);
            if (!element.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Expected one server module JSON object");
            return element.getAsJsonObject();
        }
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IllegalArgumentException("Server module JSON nesting exceeds 16");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); var object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate server module JSON field: " + name);
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); var array = new JsonArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Invalid server module JSON token");
        };
    }
    private ServerModuleRegistry() { }
}
