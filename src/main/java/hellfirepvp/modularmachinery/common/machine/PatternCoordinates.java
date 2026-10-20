/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared Cartesian expansion for static parts, dynamic templates and modifier positions. */
public final class PatternCoordinates {
    /** Bound expansion before allocating positions, including remotely supplied definitions. */
    public static final int MAX_POSITIONS = 65_536;

    private PatternCoordinates() {}

    public static List<BlockPos> expand(JsonObject part) {
        List<Integer> xs = axis(part, "x"), ys = axis(part, "y"), zs = axis(part, "z");
        long count = (long) xs.size() * ys.size() * zs.size();
        if (count > MAX_POSITIONS)
            throw new JsonParseException("Coordinate expansion exceeds " + MAX_POSITIONS + " positions");
        List<BlockPos> positions = new ArrayList<>((int) count);
        for (int x : xs) for (int y : ys) for (int z : zs) positions.add(new BlockPos(x, y, z));
        return positions;
    }

    private static List<Integer> axis(JsonObject part, String key) {
        if (!part.has(key)) return List.of(0);
        JsonElement value = part.get(key);
        if (!value.isJsonArray()) return List.of(integer(value, key));
        if (value.getAsJsonArray().isEmpty())
            throw new JsonParseException("Coordinate '" + key + "' must not be an empty array");
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (JsonElement element : value.getAsJsonArray()) {
            values.add(integer(element, key));
            if (values.size() > MAX_POSITIONS)
                throw new JsonParseException("Coordinate '" + key + "' exceeds " + MAX_POSITIONS + " values");
        }
        return new ArrayList<>(values);
    }

    /** Offsets describe one translation and therefore require scalar coordinates. */
    public static int scalar(JsonObject object, String key) {
        return object.has(key) ? integer(object.get(key), key) : 0;
    }

    private static int integer(JsonElement value, String key) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try { return value.getAsBigDecimal().intValueExact(); }
            catch (ArithmeticException | NumberFormatException invalid) {
                throw new JsonParseException("Coordinate '" + key + "' must be a 32-bit integer", invalid);
            }
        }
        throw new JsonParseException("Coordinate '" + key + "' must be an integer or a nonempty integer array");
    }
}
