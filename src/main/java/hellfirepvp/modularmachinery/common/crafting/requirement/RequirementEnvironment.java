/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.TileEnvironmentDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import java.util.*;

/** Non-consuming detector-local conditions, independently implemented for the modern recipe lifecycle. */
public final class RequirementEnvironment extends ConditionRequirement implements TickBatchRequirement {
    public static final String TYPE_ID = "modularmachinery:environment";
    private final String condition;
    private final int min, max;
    private final List<String> values;
    private final String weather;
    private RequirementEnvironment(String condition, int min, int max, List<String> values, String weather) {
        this.condition = condition; this.min = min; this.max = max;
        this.values = List.copyOf(values); this.weather = weather;
    }
    @Override public String typeId() { return TYPE_ID; }
    public String condition() { return condition; }
    public Component tooltip() {
        return switch (condition) {
            case "altitude", "time" -> Component.translatable("jei.modularmachinery.environment." + condition, min, max);
            case "weather" -> Component.translatable("jei.modularmachinery.environment.weather",
                    Component.translatable("environment.modularmachinery.weather." + weather));
            default -> Component.translatable("jei.modularmachinery.environment." + condition, String.join(", ", values));
        };
    }
    @Override public String describe() { return tooltip().getString(); }
    @Override public boolean canStart(MachineContext context) { return describeFailure(context) == null; }
    @Override public String describeFailure(MachineContext context) {
        var level = context.getLevel();
        if (!level.getServer().isSameThread()) return "craftcheck.failure.environment.unavailable";
        boolean found = false;
        for (var pos : context.forSelector(getSelectorTag()).getComponentPositions()) {
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof TileEnvironmentDetector tile) || tile.isRemoved()) continue;
            found = true;
            if (matches(level, pos)) return null;
        }
        return found ? "craftcheck.failure.environment." + condition : "component.missing.modularmachinery.environment";
    }
    private boolean matches(ServerLevel level, BlockPos pos) {
        return switch (condition) {
            case "altitude" -> pos.getY() >= min && pos.getY() <= max;
            case "time" -> inTimeRange(level.getDayTime(), min, max);
            case "dimensions" -> values.contains(level.dimension().location().toString());
            case "biomes" -> values.stream().anyMatch(value -> value.startsWith("#")
                    ? level.getBiome(pos).is(TagKey.create(Registries.BIOME, new ResourceLocation(value.substring(1))))
                    : level.getBiome(pos).is(new ResourceLocation(value)));
            case "moonphase" -> !level.isDay() && values.contains(Integer.toString(level.getMoonPhase()));
            case "weather" -> switch (weather) {
                case "sunny" -> !level.getLevelData().isRaining() && !level.getLevelData().isThundering();
                case "raining" -> level.getLevelData().isRaining();
                case "thundering" -> level.getLevelData().isThundering();
                // Snow is observed above the solid sensor; it need not place a snow layer inside the sensor.
                case "snowing" -> (level.getLevelData().isRaining() || level.getLevelData().isThundering()) && level.canSeeSky(pos.above())
                        && level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                pos.getX(), pos.getZ()) <= pos.getY() + 1
                        && level.getBiome(pos.above()).value().getPrecipitationAt(pos.above()) == Biome.Precipitation.SNOW;
                default -> false;
            };
            default -> false;
        };
    }
    public static boolean inTimeRange(long time, int min, int max) {
        long day = Math.floorMod(time, 24000L);
        return min <= max ? day >= min && day <= max : day >= min || day <= max;
    }
    @Override public TickPlan planTick(MachineContext context, List<Requirement> siblings, int copies) {
        String failure = null;
        for (var requirement : siblings) {
            if (requirement instanceof RequirementEnvironment environment) {
                failure = environment.describeFailure(context);
                if (failure != null) break;
            }
        }
        final String result = failure;
        // No world mutation between the engine's simulation and commit; no resource to debit.
        return new TickPlan() {
            public boolean valid() { return result == null; }
            public boolean execute() { return valid(); }
            public String failure() { return result; }
        };
    }
    @Override public void writeJson(JsonObject json) {
        json.addProperty("condition", condition);
        switch (condition) {
            case "altitude", "time" -> { json.addProperty("min", min); json.addProperty("max", max); }
            case "weather" -> json.addProperty("weather", weather);
            default -> {
                var array = new JsonArray();
                for (String value : values) {
                    if (condition.equals("moonphase")) array.add(Integer.parseInt(value)); else array.add(value);
                }
                json.add("values", array);
            }
        }
    }
    public static final class Type implements RequirementType {
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            if (io != IOType.INPUT) throw new JsonParseException("environment supports input conditions only");
            String condition = string(json, "condition");
            int min = 0, max = 0;
            var values = new ArrayList<String>();
            String weather = "";
            switch (condition) {
                case "altitude", "time" -> {
                    min = integer(json.get("min"), "min"); max = integer(json.get("max"), "max");
                    if (condition.equals("altitude") && min > max) throw new JsonParseException("altitude min must not exceed max");
                    if (condition.equals("time") && (min < 0 || min > 23999 || max < 0 || max > 23999))
                        throw new JsonParseException("time bounds must be 0..23999; min > max crosses midnight");
                }
                case "dimensions", "biomes", "moonphase" -> {
                    if (!json.has("values") || !json.get("values").isJsonArray()
                            || json.getAsJsonArray("values").size() == 0 || json.getAsJsonArray("values").size() > 256)
                        throw new JsonParseException("environment values must be a non-empty array of at most 256 entries");
                    for (var entry : json.getAsJsonArray("values")) {
                        if (condition.equals("moonphase")) {
                            int phase = integer(entry, "moon phase");
                            if (phase < 0 || phase > 7) throw new JsonParseException("moon phase must be 0..7");
                            values.add(Integer.toString(phase));
                        } else {
                            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
                                throw new JsonParseException("dimension/biome values must be registry ID strings");
                            String value = entry.getAsString();
                            boolean tag = condition.equals("biomes") && value.startsWith("#");
                            String id = tag ? value.substring(1) : value;
                            if (!id.contains(":") || ResourceLocation.tryParse(id) == null)
                                throw new JsonParseException("Expected namespaced registry ID (or biome #tag): " + value);
                            values.add(value);
                        }
                    }
                }
                case "weather" -> {
                    weather = string(json, "weather");
                    if (!Set.of("sunny", "raining", "snowing", "thundering").contains(weather))
                        throw new JsonParseException("Unknown environment weather: " + weather);
                }
                default -> throw new JsonParseException("Unknown environment condition: " + condition);
            }
            return new RequirementEnvironment(condition, min, max, values, weather);
        }
        private static String string(JsonObject json, String key) {
            var value = json.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new JsonParseException("environment requires string '" + key + "'");
            return value.getAsString();
        }
        private static int integer(JsonElement value, String key) {
            try {
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
                return value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new JsonParseException("environment '" + key + "' must be a 32-bit integer");
            }
        }
    }
}
