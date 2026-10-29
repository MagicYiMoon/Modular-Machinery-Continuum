/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import java.util.*;
import java.util.function.Consumer;

/** Server script declarations replayed onto fresh JSON definitions, independent of reload-listener ordering. */
public final class ScriptMachineModifierRegistry {
    private static final Map<String, List<Consumer<DynamicMachine>>> CHANGES = new LinkedHashMap<>();
    private ScriptMachineModifierRegistry() { }

    public static synchronized void clear() { CHANGES.clear(); }

    public static synchronized void applyAndRegister(String name, Consumer<DynamicMachine> change) {
        var machine = MachineReloadListener.serverDefinitions().machines().findByName(name);
        if (machine == null) throw new IllegalArgumentException("unknown Modular Machinery machine: " + name);
        change.accept(machine);
        CHANGES.computeIfAbsent(name, ignored -> new ArrayList<>()).add(change);
    }

    public static synchronized void reapply(MachineLoader loader) {
        CHANGES.forEach((name, changes) -> {
            var machine = loader.findByName(name);
            // A deleted machine must not remain in the registry just because its script still exists.
            if (machine != null) changes.forEach(change -> change.accept(machine));
        });
    }
}
