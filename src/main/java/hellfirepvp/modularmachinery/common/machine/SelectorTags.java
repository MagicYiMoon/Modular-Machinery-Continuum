/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public final class SelectorTags {
    private SelectorTags() {}

    public static String read(JsonObject json) {
        if (!json.has("selector-tag")) return null;
        var value = json.get("selector-tag");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new JsonParseException("'selector-tag' must be a string");
        String tag = value.getAsString();
        return tag.isEmpty() ? null : tag;
    }
}
