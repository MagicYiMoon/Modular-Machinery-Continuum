/* SPDX-License-Identifier: GPL-3.0-only
 * Module parameters and calculate-type efficiencies adapt NECO Module*Base and
 * ModuleCPU/GPU/QuantumCalculateCard/SoulCalculateCard, KasumiNova and contributors,
 * revision b37fbb53. JSON authoring and validation are MMC adaptations.
 */
package hellfirepvp.modularmachinery.common.hypernet.server;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.machine.MachineDisplayInfo;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerAssemblyLayout.Kind;

/** Immutable server-authored attributes. Physical item NBT never supplies capabilities. */
public record ServerModuleDefinition(ResourceLocation id, ResourceLocation item, Type type,
        double generation, double energyRatio, int bandwidth, long energyProvision,
        long energyCapacity, long energyConsumption, MachineDisplayInfo display) {
    public enum Calculation { INTRICATE, LOGIC, NEURON, QBIT }
    public enum Type {
        CPU(Kind.CPU, 1, 1, .025, .001),
        GPU(Kind.CALCULATE_CARD, .2, 5, .1, .005),
        SOUL_CARD(Kind.CALCULATE_CARD, .01, .005, 1, .0001),
        QUANTUM_CARD(Kind.CALCULATE_CARD, .5, .25, .3, 1),
        RAM(Kind.RAM), CPU_EXTENSION(Kind.CPU_EXTENSION),
        CALCULATE_EXTENSION(Kind.CALCULATE_EXTENSION), EXTENSION_EXTENSION(Kind.EXTENSION_EXTENSION),
        CPU_HEATSINK(Kind.CPU_HEATSINK), RAM_HEATSINK(Kind.RAM_HEATSINK),
        CALCULATE_HEATSINK(Kind.CALCULATE_HEATSINK), EXTENSION_HEATSINK(Kind.EXTENSION_HEATSINK),
        PSU(Kind.PSU), CAPACITOR(Kind.CAPACITOR);
        private final Kind kind;
        private final List<Double> efficiencies;
        Type(Kind kind, double... efficiencies) { this.kind = kind; this.efficiencies = Arrays.stream(efficiencies).boxed().toList(); }
        public Kind kind() { return kind; }
        public boolean calculable() { return !efficiencies.isEmpty(); }
        public double efficiency(Calculation type) { return calculable() ? efficiencies.get(type.ordinal()) : 0; }
        public boolean hasBandwidth() {
            return calculable() || this == RAM || this == CPU_EXTENSION || this == CALCULATE_EXTENSION || this == EXTENSION_EXTENSION;
        }
    }
    public ServerModuleDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(item); Objects.requireNonNull(type); Objects.requireNonNull(display);
        if (id.toString().length() > 256 || item.toString().length() > 256 || item.toString().equals("minecraft:air")
                || !Double.isFinite(generation) || generation < 0 || !Double.isFinite(energyRatio) || energyRatio < 0
                || bandwidth < 0 || energyProvision < 0 || energyCapacity < 0 || energyConsumption < 0)
            throw new IllegalArgumentException("Invalid server module parameters");
        if (type.calculable()) {
            if (generation <= 0) throw new IllegalArgumentException("Calculate generation must be positive");
            for (var calculation : Calculation.values()) if (!Double.isFinite(generation * type.efficiency(calculation)))
                throw new IllegalArgumentException("Calculate generation overflows");
        } else if (generation != 0 || energyRatio != 0) throw new IllegalArgumentException("Module cannot calculate");
        if ((!type.hasBandwidth() && bandwidth != 0) || (type != Type.PSU && energyProvision != 0)
                || (type != Type.CAPACITOR && (energyCapacity != 0 || energyConsumption != 0)))
            throw new IllegalArgumentException("Parameters do not apply to this module type");
    }
    /** NECO ModuleCPUExt.getHardwareBandwidth returns zero, despite retaining its configured field. */
    public int bandwidthConsumption() { return type == Type.RAM || type == Type.CPU_EXTENSION ? 0 : bandwidth; }
    public int bandwidthProvision() { return type == Type.RAM ? bandwidth : 0; }
    public static ServerModuleDefinition parse(JsonObject json) {
        var type = Type.valueOf(text(json, "type").toUpperCase(Locale.ROOT));
        var allowed = new HashSet<>(Set.of("id", "item", "type", "display-info"));
        if (type.calculable()) allowed.addAll(Set.of("generation", "energy-ratio"));
        if (type.hasBandwidth()) allowed.add("bandwidth");
        if (type == Type.PSU) allowed.add("energy-provision");
        if (type == Type.CAPACITOR) allowed.addAll(Set.of("energy-capacity", "energy-consumption"));
        for (var key : json.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unexpected server module field for " + type + ": " + key);
        try {
            return new ServerModuleDefinition(resource(text(json, "id")), resource(text(json, "item")), type,
                    type.calculable() ? number(json, "generation").getAsDouble() : 0,
                    type.calculable() ? number(json, "energy-ratio").getAsDouble() : 0,
                    type.hasBandwidth() ? number(json, "bandwidth").getAsBigDecimal().intValueExact() : 0,
                    type == Type.PSU ? number(json, "energy-provision").getAsBigDecimal().longValueExact() : 0,
                    type == Type.CAPACITOR ? number(json, "energy-capacity").getAsBigDecimal().longValueExact() : 0,
                    type == Type.CAPACITOR ? number(json, "energy-consumption").getAsBigDecimal().longValueExact() : 0,
                    json.has("display-info") ? MachineDisplayInfo.parse(json.get("display-info")) : MachineDisplayInfo.EMPTY);
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Bandwidth and energy limits must be exact integers in range", e); }
    }
    private static ResourceLocation resource(String value) {
        if (value.length() > 256 || !value.contains(":")) throw new IllegalArgumentException("Expected namespaced resource ID");
        return new ResourceLocation(value);
    }
    private static String text(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Server module " + key + " must be text");
        return json.get(key).getAsString();
    }
    private static JsonPrimitive number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Server module " + key + " must be a number");
        return json.getAsJsonPrimitive(key);
    }
}
