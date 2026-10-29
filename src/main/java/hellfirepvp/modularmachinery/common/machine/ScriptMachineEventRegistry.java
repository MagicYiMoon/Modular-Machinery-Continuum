/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Server-side CraftTweaker callbacks for the source MMEvents machine hooks. */
public final class ScriptMachineEventRegistry {
    private static final Map<String, Map<Class<?>, List<Consumer<Object>>>> HANDLERS = new LinkedHashMap<>();
    private ScriptMachineEventRegistry() { }
    public static synchronized void clear() { HANDLERS.clear(); }
    @SuppressWarnings("unchecked")
    public static synchronized <T extends MachineEvent> void register(String machineName, Class<T> eventType,
                                                                        Consumer<T> callback) {
        if (machineName == null || machineName.isBlank() || eventType == null || callback == null) return;
        HANDLERS.computeIfAbsent(machineName, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(eventType, ignored -> new ArrayList<>()).add((Consumer<Object>) (Consumer<?>) callback);
    }
    /** Dispatches the exact source event class, preserving registration order. */
    public static void dispatch(MachineEvent event) {
        if (event == null || event.getController() == null) return;
        dispatchToMachine(event.getController().getMachineName(), event);
    }
    /** Publication can precede writing the newly recognized machine identity onto the controller. */
    public static void dispatchToMachine(String machineName, MachineEvent event) {
        if (event == null || event.getController() == null) return;
        if (machineName == null || machineName.isBlank()) return;
        List<Consumer<Object>> callbacks;
        synchronized (ScriptMachineEventRegistry.class) {
            Map<Class<?>, List<Consumer<Object>>> byType = HANDLERS.get(machineName);
            callbacks = byType == null ? List.of() : List.copyOf(byType.getOrDefault(event.getClass(), List.of()));
        }
        for (Consumer<Object> callback : callbacks) {
            try { callback.accept(event); }
            catch (Throwable failure) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                        "[MMCE] CraftTweaker machine event handler failed for {}", machineName, failure);
            }
            if (event.isCanceled()) break;
        }
    }
}
