/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.util.nbt;

import com.google.gson.*;
import net.minecraft.nbt.*;

import java.util.LinkedHashMap;
import java.util.ArrayList;

/** Immutable recipe NBT: a matching tree, concrete output, and independent display data. */
public final class RecipeNbt {
    public static final RecipeNbt EMPTY = new RecipeNbt(null, null);
    private final NbtPattern pattern;
    private final CompoundTag output;
    private final CompoundTag display;
    private final JsonElement source;
    private final JsonElement displaySource;

    private RecipeNbt(JsonElement source, JsonElement displaySource) {
        this.source = source == null ? null : source.deepCopy();
        this.displaySource = displaySource == null ? null : displaySource.deepCopy();
        pattern = source == null ? null : parse(source);
        output = pattern == null ? null : (CompoundTag) materialize(pattern);
        display = displaySource == null ? output : (CompoundTag) materialize(parse(displaySource));
    }

    public static RecipeNbt read(JsonObject json) {
        return new RecipeNbt(json.get("nbt"), json.get("nbt-display"));
    }

    public static RecipeNbt ofPattern(NbtPattern pattern) {
        return pattern == null ? EMPTY : new RecipeNbt(toJson(pattern), null);
    }

    public void write(JsonObject json) {
        json.remove("nbt");
        json.remove("nbt-display");
        if (source != null) json.add("nbt", source.deepCopy());
        if (displaySource != null) json.add("nbt-display", displaySource.deepCopy());
    }

    public NbtPattern pattern() { return pattern; }
    public CompoundTag output() { return output == null ? null : output.copy(); }
    public CompoundTag display() { return display == null ? null : display.copy(); }
    public boolean matches(CompoundTag tag) { return NbtMatchingHelper.matchNbtCompound(pattern, tag); }

    private static NbtPattern parse(JsonElement json) {
        NbtPattern result;
        if (json.isJsonObject()) result = fromJson(json);
        else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            result = NbtPatternParser.parse(json.getAsString());
        } else throw new JsonParseException("Recipe NBT must be an object or compound pattern string");
        if (!(result instanceof NbtCompoundPattern)) throw new JsonParseException("Recipe NBT root must be a compound");
        return result;
    }

    private static NbtPattern fromJson(JsonElement json) {
        if (json.isJsonObject()) {
            var children = new LinkedHashMap<String, NbtPattern>();
            json.getAsJsonObject().entrySet().forEach(e -> children.put(e.getKey(), fromJson(e.getValue())));
            return new NbtCompoundPattern(children);
        }
        if (json.isJsonArray()) {
            var children = new ArrayList<NbtPattern>();
            json.getAsJsonArray().forEach(e -> children.add(fromJson(e)));
            return new NbtListPattern(children);
        }
        if (json.isJsonPrimitive()) return NbtLeafParser.parseLeaf(json.getAsString());
        throw new JsonParseException("Null is not a recipe NBT value");
    }

    private static JsonElement toJson(NbtPattern pattern) {
        if (pattern instanceof NbtCompoundPattern compound) {
            JsonObject json = new JsonObject();
            compound.keys().forEach((key, value) -> json.add(key, toJson(value)));
            return json;
        }
        if (pattern instanceof NbtListPattern list) {
            JsonArray json = new JsonArray();
            list.entries().forEach(value -> json.add(toJson(value)));
            return json;
        }
        if (pattern instanceof NbtNumberPattern number) return new JsonPrimitive(number.toString());
        if (pattern instanceof NbtPatternString string) return new JsonPrimitive(string.source());
        throw new JsonParseException("Unsupported recipe NBT pattern: " + pattern.getClass().getName());
    }

    private static Tag materialize(NbtPattern pattern) {
        if (pattern instanceof NbtCompoundPattern compound) {
            CompoundTag tag = new CompoundTag();
            compound.keys().forEach((key, value) -> tag.put(key, materialize(value)));
            return tag;
        }
        if (pattern instanceof NbtListPattern list) {
            ListTag tag = new ListTag();
            for (NbtPattern entry : list.entries()) {
                if (!tag.add(materialize(entry))) throw new JsonParseException("NBT list entries must have the same type");
            }
            return tag;
        }
        if (pattern instanceof NbtNumberPattern number) {
            try {
                // Upstream writes the numeric threshold, without its matching operator.
                return TagParser.parseTag("{value:" + NbtComparisonMode.stripMode(number.toString()) + "}").get("value");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
                throw new JsonParseException("Invalid numeric recipe NBT", invalid);
            }
        }
        if (pattern instanceof NbtPatternString string) return StringTag.valueOf(string.source());
        throw new JsonParseException("Unsupported recipe NBT pattern");
    }
}
