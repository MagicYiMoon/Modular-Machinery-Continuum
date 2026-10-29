/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

/** Shared bounded controller status area, independent of the fixed footer and inventory. */
public class ControllerStatusViewport {
    public static final double SCALE = 0.72D;
    public static final int Y = 8, WIDTH = 135, HEIGHT = 82, SCROLL_WIDTH = 4;
    public static final int FOOTER_Y = 92, FOOTER_STRIDE = 8, FOOTER_ROWS = 3;
    public static final int LOGICAL_HEIGHT = (int) (HEIGHT / SCALE);
    public static final int TEXT_WIDTH = (int) ((WIDTH - 2) / SCALE);
    private int contentHeight, offset;
    private boolean dragging;
    private double grabY;
    private final int x, lineStride;
    public ControllerStatusViewport(int x, int lineStride) {
        if (lineStride <= 0) throw new IllegalArgumentException("Line stride must be positive");
        this.x = x;
        this.lineStride = lineStride;
    }
    public int x() { return x; }
    public int scrollX() { return x + WIDTH + 1; }
    public void updateHeight(int height) {
        contentHeight = Math.max(0, height);
        offset = Math.min(offset, maxOffset());
        if (maxOffset() == 0) dragging = false;
    }
    public int offset() { return offset; }
    public int maxOffset() { return Math.max(0, contentHeight - LOGICAL_HEIGHT); }
    public int thumbHeight() { return Math.max(10, HEIGHT * LOGICAL_HEIGHT / Math.max(contentHeight, LOGICAL_HEIGHT)); }
    public int thumbY() { return Y + (maxOffset() == 0 ? 0 : (int) ((long) (HEIGHT - thumbHeight()) * offset / maxOffset())); }
    private boolean overTrack(double x, double y) {
        return x >= scrollX() && x < scrollX() + SCROLL_WIDTH && y >= Y && y < Y + HEIGHT;
    }
    public boolean scroll(double x, double y, double delta) {
        if (delta == 0 || maxOffset() == 0 || (!overTrack(x, y)
                && !(x >= this.x && x < this.x + WIDTH && y >= Y && y < Y + HEIGHT))) return false;
        offset = Math.max(0, Math.min(maxOffset(), offset + (delta > 0 ? -lineStride : lineStride)));
        return true;
    }
    public boolean click(double x, double y, int button) {
        if (button != 0 || maxOffset() == 0 || !overTrack(x, y)) return false;
        dragging = true;
        grabY = y >= thumbY() && y < thumbY() + thumbHeight() ? y - thumbY() : thumbHeight() / 2D;
        move(y); return true;
    }
    public boolean drag(double y, int button) {
        if (button != 0 || !dragging) return false;
        move(y); return true;
    }
    public boolean release(int button) {
        if (button != 0 || !dragging) return false;
        dragging = false; return true;
    }
    private void move(double y) {
        offset = (int) Math.max(0, Math.min(maxOffset(), Math.round(
                (y - grabY - Y) / Math.max(1, HEIGHT - thumbHeight()) * maxOffset())));
    }
}
