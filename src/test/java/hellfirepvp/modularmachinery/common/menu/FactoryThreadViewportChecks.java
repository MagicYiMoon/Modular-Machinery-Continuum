/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import java.util.HashSet;
import java.util.Set;

/** Exercise production input handling without opening a game window. */
public final class FactoryThreadViewportChecks {
    public static void main(String[] args) {
        for (int count : new int[]{0, 1, 6, 7, 23, 101}) {
            var view = new FactoryThreadViewport();
            view.updateCount(count);
            Set<Integer> seen = new HashSet<>();
            for (int scroll = 0; scroll <= count; scroll++) {
                for (int row = 0; row < view.visibleCount(); row++) {
                    int index = view.indexAt(10, 9 + 33 * row);
                    require(index >= 0 && index < count, "visible row selected an absent thread");
                    require(index == view.index(row), "tooltip and rendered row disagree");
                    require(view.indexAt(10, 40 + 33 * row) == -1, "row gap selected a thread");
                    seen.add(index);
                }
                view.scroll(10, 9, -1);
            }
            require(seen.size() == count, "scrolling did not expose every thread: " + count);
            require(view.indexAt(94, 9) == -1 && view.indexAt(7, 9) == -1
                    && view.indexAt(10, 206) == -1, "off-list coordinates selected a thread");
            int bottom = view.offset();
            require(!view.scroll(120, 9, 1) && view.offset() == bottom, "wheel outside list moved it");
            require(view.scroll(95, 9, -1) == (count > 6) && view.offset() == bottom,
                    "bottom wheel did not clamp/consume correctly");
            for (int i = 0; i <= count; i++) view.scroll(10, 9, 1);
            require(view.offset() == 0, "reverse wheel did not reach first thread");
            if (count > 6) {
                require(!view.click(95, 9, 1), "right-click started dragging");
                require(view.click(95, 9, 0), "scrollbar did not capture click");
                require(view.drag(1000, 0) && view.offset() == count - 6, "drag missed final six threads");
                require(view.indexAt(10, 174) == count - 1, "last thread cannot be hovered");
                require(view.drag(-100, 0) && view.offset() == 0, "drag above track did not clamp");
                require(view.release(0) && !view.drag(1000, 0), "released scrollbar still dragged");
                require(view.click(95, 204, 0) && view.offset() == count - 6,
                        "track click did not reach end");
                view.updateCount(3);
                require(view.offset() == 0 && !view.drag(1000, 0), "shrinking list retained offset/capture");
            }
        }
        var large = new FactoryThreadViewport();
        large.updateCount(Integer.MAX_VALUE);
        require(large.click(95, 204, 0) && large.index(5) == Integer.MAX_VALUE - 1,
                "large list overflowed at final row");
        require(large.thumbY() + large.thumbHeight() == 205, "thumb left track bounds");
        System.out.println("[MMC][factory-viewport] PASS: all rows reachable, wheel bounds, hit targets, track clicks, dragging, shrinking, integer limits");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
