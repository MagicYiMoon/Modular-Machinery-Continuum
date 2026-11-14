/* SPDX-License-Identifier: GPL-3.0-only
 * Assembly layout and dependencies adapted from NECO AssemblySlotManager and
 * AssemblyInv*Const, KasumiNova and contributors, revision b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet.server;

import java.util.*;

/** Fixed physical slot identities, independent of GUI order or page visibility. */
public final class ServerAssemblyLayout {
    public enum Bank {
        CPU("cpuInv", 41), CALCULATE_CARD("calInv", 20), EXTENSION("extInv", 36), POWER("powerInv", 8);
        private final String nbtKey;
        private final int size;
        Bank(String nbtKey, int size) { this.nbtKey = nbtKey; this.size = size; }
        public String nbtKey() { return nbtKey; }
        public int size() { return size; }
    }
    public enum Kind {
        CPU, RAM, CPU_EXTENSION, CPU_HEATSINK, RAM_HEATSINK,
        CALCULATE_CARD, CALCULATE_EXTENSION, CALCULATE_HEATSINK,
        EXTENSION_CARD, EXTENSION_EXTENSION, EXTENSION_HEATSINK, PSU, CAPACITOR
    }
    public record Address(Bank bank, int index) {
        public Address {
            Objects.requireNonNull(bank);
            if (index < 0 || index >= bank.size()) throw new IllegalArgumentException("Invalid assembly slot");
        }
    }
    public record Slot(Address address, Kind kind, List<Address> dependencies, List<Address> cooling) {
        public Slot {
            Objects.requireNonNull(address); Objects.requireNonNull(kind);
            dependencies = List.copyOf(dependencies); cooling = List.copyOf(cooling);
        }
    }
    private static final Map<Address, Slot> SLOTS;
    private static final Map<Address, List<Address>> DEPENDENTS;
    static {
        var slots = new LinkedHashMap<Address, Slot>();
        add(slots, Bank.CPU, 20, Kind.CPU_EXTENSION, List.of(), List.of());
        for (int i = 0; i < 4; i++) {
            var dependency = i < 2 ? List.<Address>of() : List.of(new Address(Bank.CPU, 20));
            add(slots, Bank.CPU, i, Kind.CPU, dependency, List.of(new Address(Bank.CPU, 21 + i)));
            add(slots, Bank.CPU, 21 + i, Kind.CPU_HEATSINK, dependency, List.of());
        }
        for (int i = 0; i < 16; i++) {
            var dependency = i < 8 ? List.<Address>of() : List.of(new Address(Bank.CPU, 20));
            add(slots, Bank.CPU, 4 + i, Kind.RAM, dependency, List.of(new Address(Bank.CPU, 25 + i)));
            add(slots, Bank.CPU, 25 + i, Kind.RAM_HEATSINK, dependency, List.of());
        }
        addLines(slots, Bank.CALCULATE_CARD, 2, 8, 12,
                Kind.CALCULATE_CARD, Kind.CALCULATE_EXTENSION, Kind.CALCULATE_HEATSINK);
        addLines(slots, Bank.EXTENSION, 4, 16, 20,
                Kind.EXTENSION_CARD, Kind.EXTENSION_EXTENSION, Kind.EXTENSION_HEATSINK);
        for (int i = 0; i < 4; i++) {
            add(slots, Bank.POWER, i, Kind.PSU, List.of(), List.of());
            add(slots, Bank.POWER, 4 + i, Kind.CAPACITOR, List.of(), List.of());
        }
        var ordered = new LinkedHashMap<Address, Slot>();
        var dependents = new HashMap<Address, List<Address>>();
        for (var bank : Bank.values()) for (int i = 0; i < bank.size(); i++) {
            var address = new Address(bank, i);
            var slot = Objects.requireNonNull(slots.get(address), "Unmapped assembly slot");
            ordered.put(address, slot);
            for (var dependency : slot.dependencies())
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(address);
        }
        dependents.replaceAll((address, list) -> List.copyOf(list));
        SLOTS = Collections.unmodifiableMap(ordered);
        DEPENDENTS = Map.copyOf(dependents);
    }
    private static void addLines(Map<Address, Slot> slots, Bank bank, int width, int extStart, int coolingStart,
                                 Kind module, Kind extension, Kind heatsink) {
        for (int line = 0; line < 4; line++) {
            var dependency = List.of(new Address(bank, extStart + line));
            add(slots, bank, extStart + line, extension, List.of(), List.of());
            for (int i = 0; i < width; i++) {
                int index = line * width + i;
                add(slots, bank, index, module, dependency, List.of(new Address(bank, coolingStart + index)));
                add(slots, bank, coolingStart + index, heatsink, dependency, List.of());
            }
        }
    }
    private static void add(Map<Address, Slot> slots, Bank bank, int index, Kind kind,
                            List<Address> dependencies, List<Address> cooling) {
        var address = new Address(bank, index);
        if (slots.put(address, new Slot(address, kind, dependencies, cooling)) != null)
            throw new IllegalStateException("Duplicate assembly slot");
    }
    public static Slot slot(Address address) { return Objects.requireNonNull(SLOTS.get(address)); }
    public static Collection<Slot> slots() { return SLOTS.values(); }
    public static List<Address> dependents(Address address) { return DEPENDENTS.getOrDefault(address, List.of()); }
    private ServerAssemblyLayout() { }
}
