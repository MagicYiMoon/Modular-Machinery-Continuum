/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

/** Factory-specific placement; scrolling behavior is shared with the ordinary controller. */
public final class FactoryStatusViewport extends ControllerStatusViewport {
    public static final int X = 112, SCROLL_X = X + WIDTH + 1;
    public FactoryStatusViewport() { super(X, 12); }
}
