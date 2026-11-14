/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import java.nio.file.*;
import java.util.*;

/** Server-owned config catalog. A malformed batch closes all gates, without altering database NBT. */
public final class HyperNetResearchRegistry {
    public static final int MAX_RESEARCH = 4096;
    private static Map<String,HyperNetResearch> definitions = Map.of();
    public record Catalog(Map<String,HyperNetResearch> definitions,Map<String,Path> files,String revision) {}
    private static Catalog loaded;
    public static Catalog loadedCatalog(){return loaded;}
    private HyperNetResearchRegistry() {}
    /** A bounded, fully validated disk batch, also used before any editor write. */
    public static Catalog readCatalog(Path directory) throws java.io.IOException {
        Files.createDirectories(directory);var root=directory.toRealPath();List<Path> files;
        try(var paths=Files.walk(root)) {
            files=paths.filter(p->Files.isRegularFile(p)&&p.toString().endsWith(".json")).limit(MAX_RESEARCH+1L).sorted().toList();
        }
        if(files.size()>MAX_RESEARCH)throw new IllegalArgumentException("Too many research files");
        var parsed=new ArrayList<HyperNetResearch>();var sources=new LinkedHashMap<String,Path>();
        final java.security.MessageDigest digest;
        try {digest=java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        for(var file:files) {
            var real=file.toRealPath();if(!real.startsWith(root))throw new IllegalArgumentException("Research source outside directory");
            if(Files.size(real)>256*1024)throw new IllegalArgumentException("Research file exceeds 256 KiB: "+file);
            byte[] bytes=Files.readAllBytes(real);if(bytes.length>256*1024)throw new IllegalArgumentException("Research file exceeds 256 KiB");
            var research=HyperNetResearch.parse(JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            parsed.add(research);sources.put(research.id(),real);
            byte[] name=root.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(name.length).array());digest.update(name);
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
        }
        return new Catalog(validate(parsed),Collections.unmodifiableMap(sources),java.util.HexFormat.of().formatHex(digest.digest()));
    }
    public static HyperNetResearch find(String id) { return definitions.get(id); }
    public static Map<String,HyperNetResearch> snapshot() { return definitions; }
    public static Map<String,HyperNetResearch> validate(Collection<HyperNetResearch> values) {
        if (values.size() > MAX_RESEARCH) throw new IllegalArgumentException("Too many research definitions");
        var map = new LinkedHashMap<String,HyperNetResearch>();
        for (var v : values) if (map.putIfAbsent(v.id(),v) != null) throw new IllegalArgumentException("Duplicate research: " + v.id());
        var remaining = new HashMap<String,Integer>(); var dependents = new HashMap<String,List<String>>(); var ready = new ArrayDeque<String>();
        for (var v : values) {
            remaining.put(v.id(),v.dependencies().size());
            if (v.dependencies().isEmpty()) ready.add(v.id());
            for (var dep : v.dependencies()) {
                if (!map.containsKey(dep)) throw new IllegalArgumentException("Unknown research dependency: " + dep + " in " + v.id());
                dependents.computeIfAbsent(dep,k -> new ArrayList<>()).add(v.id());
            }
        }
        int count = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove(); count++;
            for (var next : dependents.getOrDefault(id,List.of())) if (remaining.compute(next,(k,n) -> n-1) == 0) ready.add(next);
        }
        if (count != map.size()) throw new IllegalArgumentException("Research dependency cycle");
        return Collections.unmodifiableMap(map);
    }
    public static void reload(Path directory) {
        definitions = Map.of();loaded=null;
        try {
            loaded=readCatalog(directory);definitions=loaded.definitions();
            Modularmachinery.LOG.info("[MMC] loaded {} HyperNet research definitions",definitions.size());
        } catch (Exception e) {
            Modularmachinery.LOG.error("[MMC] invalid HyperNet research catalog; research gates are closed",e);
        }
    }
}
