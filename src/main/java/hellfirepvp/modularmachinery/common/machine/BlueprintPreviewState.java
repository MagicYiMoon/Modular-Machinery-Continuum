/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's shared structure preview controls.
 */
package hellfirepvp.modularmachinery.common.machine;

/** Client-independent preview choices. Rebuilding the geometry preserves the frozen candidate and formed mode. */
public final class BlueprintPreviewState {
    /** A world projection keeps this candidate tick even after its source panel resumes or changes size. */
    public record ProjectionSnapshot(int dynamicSize, long sampleTick) { }

    private final DynamicMachine machine;
    private final MachineVariables variables;
    private final BlockStateResolver resolver;
    private BlueprintSceneDefinition definition;
    private int dynamicSize, layer;
    private boolean formed, cycling = true, clockInitialized;
    private long cycleTick;

    public BlueprintPreviewState(DynamicMachine machine, MachineVariables variables, BlockStateResolver resolver) {
        this.machine = machine;
        this.variables = variables;
        this.resolver = resolver;
        dynamicSize = minimumSize(machine);
        rebuildDefinition();
        layer = definition.bounds().minY();
    }

    public BlueprintSceneDefinition definition() { return definition; }
    public int dynamicSize() { return dynamicSize; }
    public int layer() { return layer; }
    public boolean formed() { return formed; }
    public boolean cycling() { return cycling; }
    public long cycleTick() { return cycleTick; }
    public void toggleFormed() { formed = !formed; }
    public void setLayer(int value) { layer = definition.bounds().clampLayer(value); }

    public void updateClock(long worldTick) {
        if (cycling || !clockInitialized) cycleTick = worldTick;
        clockInitialized = true;
    }

    public void toggleCycle(long worldTick) {
        // Freeze the last displayed candidate, including when clicking near a cycle boundary.
        if (!clockInitialized) updateClock(worldTick);
        cycling = !cycling;
        if (cycling) updateClock(worldTick);
    }

    /** Capture the last displayed phase, rather than advancing at a click on a cycle boundary. */
    public ProjectionSnapshot projectionSnapshot(long worldTick) {
        if (!clockInitialized) updateClock(worldTick);
        return new ProjectionSnapshot(dynamicSize, cycleTick);
    }

    public boolean changeSize(int delta) {
        int next = (int) Math.max(minimumSize(machine), Math.min((long) maximumSize(machine), (long) dynamicSize + delta));
        if (next == dynamicSize) return false;
        dynamicSize = next;
        rebuildDefinition();
        return true;
    }

    private void rebuildDefinition() {
        definition = new BlueprintSceneDefinition(machine, buildPattern(machine, dynamicSize), variables, resolver);
        setLayer(layer);
    }

    public static int minimumSize(DynamicMachine machine) {
        return machine == null ? 0 : machine.getDynamicPatterns().values().stream().mapToInt(DynamicPattern::getMinSize).max().orElse(0);
    }

    public static int maximumSize(DynamicMachine machine) {
        return machine == null ? 0 : machine.getDynamicPatterns().values().stream().mapToInt(DynamicPattern::getMaxSize).max().orElse(0);
    }

    /** Source panel uses one requested size, clamped separately to each segment's bounds. */
    public static MachinePattern buildPattern(DynamicMachine source, int requestedSize) {
        if (source == null || source.getPattern() == null) return null;
        if (source.getDynamicPatterns().isEmpty()) return source.getPattern();
        MachinePattern expanded = new MachinePattern();
        source.getPattern().getParts().forEach(expanded::addPart);
        int size = Math.max(minimumSize(source), Math.min(maximumSize(source), requestedSize));
        for (DynamicPattern dynamic : source.getDynamicPatterns().values()) {
            dynamic.mergeSectionsInto(expanded, Math.min(Math.max(dynamic.getMinSize(), size), dynamic.getMaxSize()));
        }
        return expanded;
    }
}
