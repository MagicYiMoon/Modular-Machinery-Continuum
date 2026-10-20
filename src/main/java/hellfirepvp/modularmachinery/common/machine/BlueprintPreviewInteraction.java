/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's preview selection and upgrade overlays.
 */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Preview interaction state, with no client or JEI dependencies. */
public final class BlueprintPreviewInteraction {
    private BlockPos selected;
    private final Set<Integer> enabledUpgrades = new LinkedHashSet<>();
    private boolean pressed;
    private int pressedButton = -1;
    private double pressX, pressY, maxDistance;

    public BlockPos selected() { return selected; }
    public boolean upgradeEnabled(int index) { return enabledUpgrades.contains(index); }

    public void select(BlockPos pos, BlueprintSceneDefinition definition) {
        selected = pos != null && definition.cells().containsKey(pos) ? pos.immutable() : null;
    }

    public void retain(BlueprintSceneDefinition definition) {
        if (selected != null && !definition.cells().containsKey(selected)) selected = null;
        enabledUpgrades.removeIf(index -> index < 0 || index >= definition.upgrades().size());
    }

    public void toggleUpgrade(int index, BlueprintSceneDefinition definition) {
        if (index >= 0 && index < definition.upgrades().size() && !enabledUpgrades.remove(index)) enabledUpgrades.add(index);
    }

    public Set<BlockPos> highlightedPositions(BlueprintSceneDefinition definition, Collection<BlockPos> visible) {
        Set<BlockPos> highlighted = definition.highlightedPositions(enabledUpgrades);
        highlighted.retainAll(visible);
        return Set.copyOf(highlighted);
    }

    public void press(double x, double y) {
        press(x, y, 0);
    }

    public void press(double x, double y, int button) {
        if (pressed || button < 0 || button > 1) return;
        pressed = true;
        pressedButton = button;
        pressX = x;
        pressY = y;
        maxDistance = 0;
    }

    public boolean pressed() { return pressed; }
    public int pressedButton() { return pressed ? pressedButton : -1; }

    /** Losing the viewport or the physical mouse press must never produce a later click on re-entry. */
    public void validateGesture(boolean insideViewport, boolean buttonDown) {
        if (pressed && (!insideViewport || !buttonDown)) cancel();
    }

    public void drag(double x, double y) {
        if (pressed) maxDistance = Math.max(maxDistance, Math.hypot(x - pressX, y - pressY));
    }

    public boolean release(double x, double y) {
        return release(x, y, 0);
    }

    public boolean release(double x, double y, int button) {
        if (!pressed || button != pressedButton) return false;
        drag(x, y);
        pressed = false;
        return maxDistance <= 5;
    }

    public void cancel() { pressed = false; }
}
