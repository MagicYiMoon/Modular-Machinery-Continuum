/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import hellfirepvp.modularmachinery.common.util.nbt.NbtMatchingHelper;
import hellfirepvp.modularmachinery.common.util.nbt.NbtPattern;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.nbt.CompoundTag;

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
            if (!part.get("nbt").isJsonObject())
                throw new JsonParseException("The ComponentType 'nbt' expects a JSON object");
            matchingSource = part.getAsJsonObject("nbt").toString();
            JsonObject wrapper = new JsonObject();
            wrapper.add("nbt", part.get("nbt").deepCopy());
            matching = RecipeNbt.read(wrapper).pattern();
        }
        if (part.has("preview-nbt")) {
            if (!part.get("preview-nbt").isJsonObject())
                throw new JsonParseException("The ComponentType 'preview-nbt' expects a JSON object");
            previewSource = part.getAsJsonObject("preview-nbt").toString();
            JsonObject wrapper = new JsonObject();
            wrapper.add("nbt", part.get("preview-nbt").deepCopy());
            preview = RecipeNbt.read(wrapper).output();
        }
        return new StructureNbt(matchingSource, previewSource, matching, preview);
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
