/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import hellfirepvp.modularmachinery.common.util.nbt.NbtMatchingHelper;
import hellfirepvp.modularmachinery.common.util.nbt.NbtPattern;
import hellfirepvp.modularmachinery.common.util.nbt.NbtCompoundPattern;
import hellfirepvp.modularmachinery.common.util.nbt.NbtListPattern;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;

/** Immutable structure-part NBT: a subset pattern for matching and concrete data for preview/build. */
public final class StructureNbt {
    private final String matchingSource;
    private final String previewSource;
    private final NbtPattern matching;
    private final CompoundTag preview;

    private StructureNbt(String matchingSource, String previewSource, NbtPattern matching, CompoundTag preview) {
        this.matchingSource = matchingSource;
        this.previewSource = previewSource;
        this.matching = matching;
        this.preview = preview == null ? null : preview.copy();
    }

    public static StructureNbt read(JsonObject part) {
        if (!part.has("nbt") && !part.has("preview-nbt")) return null;
        NbtPattern matching = null;
        CompoundTag preview = null;
        String matchingSource = null;
        String previewSource = null;
        if (part.has("nbt")) {
            matchingSource = part.get("nbt").toString();
            if (part.get("nbt").isJsonObject()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("nbt", part.get("nbt").deepCopy());
                matching = RecipeNbt.read(wrapper).pattern();
            } else {
                // Older construction-tool exports use literal SNBT, not the recipe pattern language.
                // Vanilla parsing preserves numeric types, arrays and literal strings verbatim.
                CompoundTag literal = readLiteral(part, "nbt");
                matching = literalPattern(literal);
            }
        }
        if (part.has("preview-nbt")) {
            previewSource = part.get("preview-nbt").toString();
            if (part.get("preview-nbt").isJsonObject()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("nbt", part.get("preview-nbt").deepCopy());
                preview = RecipeNbt.read(wrapper).output();
            } else preview = readLiteral(part, "preview-nbt");
        }
        return new StructureNbt(matchingSource, previewSource, matching, preview);
    }

    private static NbtPattern literalPattern(Tag expected) {
        if (expected instanceof CompoundTag compound) {
            var keys = new java.util.LinkedHashMap<String, NbtPattern>();
            for (String key : compound.getAllKeys()) keys.put(key, literalPattern(compound.get(key)));
            return new NbtCompoundPattern(keys);
        }
        if (expected instanceof ListTag list) {
            var entries = new java.util.ArrayList<NbtPattern>();
            for (Tag entry : list) entries.add(literalPattern(entry));
            // Use the source mod's list containment rules, including wildcard empty lists and
            // consumption of each matched entry. An exported empty bus must still accept items.
            return new NbtListPattern(entries);
        }
        Tag value = expected.copy();
        return value::equals;
    }

    private static CompoundTag readLiteral(JsonObject part, String key) {
        var value = part.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new JsonParseException("The ComponentType '" + key + "' expects a JSON object or compound SNBT string");
        try { return TagParser.parseTag(value.getAsString()); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new JsonParseException("Invalid structure '" + key + "' SNBT: " + invalid.getMessage(), invalid);
        }
    }

    public NbtPattern matching() { return matching; }
    public CompoundTag preview() { return preview == null ? null : preview.copy(); }
    public boolean matches(CompoundTag actual) { return NbtMatchingHelper.matchNbtCompound(matching, actual); }
    public boolean hasMatching() { return matching != null; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof StructureNbt that)) return false;
        return java.util.Objects.equals(matchingSource, that.matchingSource)
                && java.util.Objects.equals(previewSource, that.previewSource);
    }
    @Override public int hashCode() { return java.util.Objects.hash(matchingSource, previewSource); }
}
