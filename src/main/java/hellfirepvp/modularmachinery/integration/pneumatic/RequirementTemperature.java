/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Non-consuming native heat condition, checked before starting and before each processing tick. */
public final class RequirementTemperature extends ConditionRequirement implements TickBatchRequirement {
    private final int min, max;
    public RequirementTemperature(int min, int max) {
        if (min < 0 || max < min) throw new IllegalArgumentException("temperature requires 0 <= min <= max in Kelvin");
        this.min = min; this.max = max;
    }
    public int minimum() { return min; }
    public int maximum() { return max; }
    @Override public String typeId() { return "modularmachinery:temperature"; }
    public Component tooltip() { return Component.translatable("jei.modularmachinery.temperature", min, max); }
    @Override public String describe() { return tooltip().getString(); }
    @Override public boolean canStart(MachineContext ctx) { return describeFailure(ctx) == null; }
    @Override public String describeFailure(MachineContext ctx) {
        var level = ctx.getLevel();
        if (level == null || !level.getServer().isSameThread()) return "craftcheck.failure.temperature.unavailable";
        for (var pos : ctx.forSelector(getSelectorTag()).getComponentPositions()) {
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof TilePressureHatch tile) || !tile.live()) continue;
            double temperature = tile.heat().getTemperature();
            if (Double.isFinite(temperature) && (int) temperature >= min && (int) temperature <= max) return null;
        }
        return "craftcheck.failure.temperature.range";
    }
    @Override public TickPlan planTick(MachineContext ctx, List<Requirement> siblings, int copies) {
        String reason = null;
        for (var requirement : siblings) if (requirement instanceof RequirementTemperature temperature) {
            reason = temperature.describeFailure(ctx); if (reason != null) break;
        }
        final String failure = reason;
        return new TickPlan() {
            public boolean valid() { return failure == null; }
            public boolean execute() { return valid(); }
            public String failure() { return failure; }
        };
    }
    @Override public void writeJson(JsonObject json) { json.addProperty("min", min); json.addProperty("max", max); }
    public static final class Type implements RequirementType {
        @Override public String requiresModid() { return "pneumaticcraft"; }
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            if (io != IOType.INPUT) throw new JsonParseException("temperature is an input condition");
            try {
                if (!json.has("min") && !json.has("max")) throw new IllegalArgumentException("temperature requires min or max");
                int min = number(json, "min", 0), max = number(json, "max", Integer.MAX_VALUE);
                return new RequirementTemperature(min, max);
            } catch (IllegalArgumentException | ArithmeticException ex) { throw new JsonParseException("temperature requires integer Kelvin bounds: 0 <= min <= max", ex); }
        }
        private static int number(JsonObject json, String key, int fallback) {
            if (!json.has(key)) return fallback;
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key);
            return json.get(key).getAsBigDecimal().intValueExact();
        }
    }
}
