/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

/** Ordinary controller placement; leaves the blueprint well and inventory untouched. */
public final class MachineStatusViewport extends ControllerStatusViewport {
    public static final int X = 8, SCROLL_X = X + WIDTH + 1;
    public MachineStatusViewport() { super(X, 10); }
}
