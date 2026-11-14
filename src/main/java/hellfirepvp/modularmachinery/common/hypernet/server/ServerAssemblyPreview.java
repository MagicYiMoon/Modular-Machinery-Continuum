/* SPDX-License-Identifier: GPL-3.0-only
 * Read-only assembly diagnostics and simulated outputs adapt NECO ModularServer,
 * ModuleCalculable and ServerInfoColumn, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet.server;

import net.minecraft.world.item.ItemStack;
import java.math.BigInteger;
import java.util.*;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerAssemblyLayout.*;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerModuleDefinition.*;

/** Assembly preview only: no network work, FE payment, item changes, or started-state claims. */
public final class ServerAssemblyPreview {
    public enum Fault { CATALOG_INVALID, ASSEMBLY_INVALID, INVALID_MODULE, MISSING_CPU, MISSING_RAM,
        MISSING_PSU, MISSING_CAPACITOR, HARDWARE_BANDWIDTH, DEPENDENCIES, OUTPUT_OVERFLOW }
    public record Snapshot(Map<Kind, Integer> counts, long bandwidthProvided, long bandwidthUsed,
            double bandwidthEfficiency, BigInteger energyProvision, BigInteger energyCapacity,
            BigInteger energyConsumption, Set<Fault> faults, List<Address> invalidModules,
            List<ServerAssemblyInventory.Missing> dependencies, Map<Calculation, Double> expected,
            Map<Calculation, Double> averageEfficiency) {
        public Snapshot {
            counts = Map.copyOf(counts); faults = Set.copyOf(faults); invalidModules = List.copyOf(invalidModules);
            dependencies = List.copyOf(dependencies); expected = Map.copyOf(expected); averageEfficiency = Map.copyOf(averageEfficiency);
        }
        public int installed(Kind kind) { return counts.getOrDefault(kind, 0); }
        public int totalInstalled() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
        public boolean assemblyReady() { return faults.isEmpty(); }
    }
    public static Snapshot inspect(ItemStack server, ServerModuleRegistry.Catalog catalog) {
        if (!catalog.valid()) return unavailable(Fault.CATALOG_INVALID);
        final ServerAssemblyInventory assembly;
        try { assembly = catalog.open(server); }
        catch (IllegalArgumentException corrupt) { return unavailable(Fault.ASSEMBLY_INVALID); }
        var counts = new EnumMap<Kind, Integer>(Kind.class);
        var faults = EnumSet.noneOf(Fault.class); var invalid = new ArrayList<Address>();
        for (var definition : slots()) if (assembly.occupied(definition.address()) && !assembly.installed(definition.address()))
            invalid.add(definition.address());
        if (!invalid.isEmpty()) faults.add(Fault.INVALID_MODULE);
        var missing = assembly.missingDependencies(); if (!missing.isEmpty()) faults.add(Fault.DEPENDENCIES);
        long bandwidthProvided = 0, bandwidthUsed = 0;
        var energyProvision = BigInteger.ZERO; var energyCapacity = BigInteger.ZERO; var energyConsumption = BigInteger.ZERO;
        var calculators = new ArrayList<ServerModuleDefinition>();
        for (var address : assembly.activeModules()) {
            var definition = Objects.requireNonNull(catalog.find(assembly.get(address)));
            counts.merge(definition.type().kind(), 1, Integer::sum);
            bandwidthProvided += definition.bandwidthProvision(); bandwidthUsed += definition.bandwidthConsumption();
            energyProvision = energyProvision.add(BigInteger.valueOf(definition.energyProvision()));
            energyCapacity = energyCapacity.add(BigInteger.valueOf(definition.energyCapacity()));
            energyConsumption = energyConsumption.add(BigInteger.valueOf(definition.energyConsumption()));
            if (definition.type().calculable()) calculators.add(definition);
        }
        if (counts.getOrDefault(Kind.CPU, 0) == 0) faults.add(Fault.MISSING_CPU);
        if (counts.getOrDefault(Kind.RAM, 0) == 0) faults.add(Fault.MISSING_RAM);
        if (counts.getOrDefault(Kind.PSU, 0) == 0) faults.add(Fault.MISSING_PSU);
        if (counts.getOrDefault(Kind.CAPACITOR, 0) == 0) faults.add(Fault.MISSING_CAPACITOR);
        if (bandwidthProvided * 1.5 < bandwidthUsed) faults.add(Fault.HARDWARE_BANDWIDTH);
        // Match the source float ratio and its 50% floor; zero supply always yields zero.
        double bandwidthEfficiency = bandwidthProvided <= 0 ? 0
                : Math.max(Math.min((float) bandwidthProvided / bandwidthUsed, 1.0F), 0.5F);
        var expected = new EnumMap<Calculation, Double>(Calculation.class);
        var average = new EnumMap<Calculation, Double>(Calculation.class);
        for (var type : Calculation.values()) {
            double total = 0, efficiencyTotal = 0;
            for (var calculator : calculators) {
                double efficiency = calculator.type().efficiency(type);
                total += calculator.generation() * efficiency * bandwidthEfficiency;
                efficiencyTotal += efficiency;
            }
            if (!Double.isFinite(total)) faults.add(Fault.OUTPUT_OVERFLOW);
            else expected.put(type, total);
            average.put(type, calculators.isEmpty() ? 0 : efficiencyTotal / calculators.size());
        }
        // Source ServerInfoColumn returns after errors and does not advertise expected output.
        if (!faults.isEmpty()) expected.clear();
        return new Snapshot(counts, bandwidthProvided, bandwidthUsed, bandwidthEfficiency, energyProvision,
                energyCapacity, energyConsumption, faults, invalid, missing, expected, average);
    }
    private static Snapshot unavailable(Fault fault) {
        return new Snapshot(Map.of(), 0, 0, 0, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                Set.of(fault), List.of(), List.of(), Map.of(), Map.of());
    }
    private ServerAssemblyPreview() { }
}
