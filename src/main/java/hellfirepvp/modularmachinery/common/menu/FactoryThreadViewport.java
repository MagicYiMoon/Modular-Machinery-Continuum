/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

/** Source factory scrollbar geometry, shared by rendering, hit tests and headless input checks. */
public final class FactoryThreadViewport {
    public static final int SCROLL_X = 94, SCROLL_Y = 8, SCROLL_WIDTH = 6, SCROLL_HEIGHT = 197;
    private int count, offset;
    private boolean dragging;
    private double grabY;

    public void updateCount(int count) {
        this.count = Math.max(0, count);
        offset = Math.min(offset, maxOffset());
        if (maxOffset() == 0) dragging = false;
    }
    public int offset() { return offset; }
    public int maxOffset() { return Math.max(0, count - MenuFactoryController.MAX_PAGE_ELEMENTS); }
    public int visibleCount() { return Math.min(count, MenuFactoryController.MAX_PAGE_ELEMENTS); }
    public int index(int row) { return row >= 0 && row < visibleCount() ? offset + row : -1; }
    public int thumbHeight() {
        return Math.max(12, SCROLL_HEIGHT * MenuFactoryController.MAX_PAGE_ELEMENTS / Math.max(count, 6));
    }
    public int thumbY() {
        return SCROLL_Y + (maxOffset() == 0 ? 0 : (int) ((long) (SCROLL_HEIGHT - thumbHeight()) * offset / maxOffset()));
    }
    public int indexAt(double x, double y) {
        if (x < MenuFactoryController.RECIPE_QUEUE_X
                || x >= MenuFactoryController.RECIPE_QUEUE_X + MenuFactoryController.RECIPE_ELEMENT_WIDTH
                || y < MenuFactoryController.RECIPE_QUEUE_Y) return -1;
        int relativeY = (int) Math.floor(y - MenuFactoryController.RECIPE_QUEUE_Y);
        if (relativeY % MenuFactoryController.RECIPE_ROW_PITCH >= MenuFactoryController.RECIPE_ELEMENT_HEIGHT) return -1;
        return index(relativeY / MenuFactoryController.RECIPE_ROW_PITCH);
    }
    private boolean overTrack(double x, double y) {
        return x >= SCROLL_X && x < SCROLL_X + SCROLL_WIDTH && y >= SCROLL_Y && y < SCROLL_Y + SCROLL_HEIGHT;
    }
    public boolean scroll(double x, double y, double delta) {
        if (delta == 0 || maxOffset() == 0 || (indexAt(x, y) < 0 && !overTrack(x, y))) return false;
        offset = Math.max(0, Math.min(maxOffset(), offset + (delta > 0 ? -1 : 1)));
        return true;
    }
    public boolean click(double x, double y, int button) {
        if (button != 0 || maxOffset() == 0 || !overTrack(x, y)) return false;
        dragging = true;
        grabY = y >= thumbY() && y < thumbY() + thumbHeight() ? y - thumbY() : thumbHeight() / 2.0;
        moveThumb(y);
        return true;
    }
    public boolean drag(double y, int button) {
        if (button != 0 || !dragging) return false;
        moveThumb(y);
        return true;
    }
    public boolean release(int button) {
        if (button != 0 || !dragging) return false;
        dragging = false;
        return true;
    }
    private void moveThumb(double y) {
        double fraction = (y - grabY - SCROLL_Y) / Math.max(1, SCROLL_HEIGHT - thumbHeight());
        offset = (int) Math.max(0, Math.min(maxOffset(), Math.round(fraction * maxOffset())));
    }
}
