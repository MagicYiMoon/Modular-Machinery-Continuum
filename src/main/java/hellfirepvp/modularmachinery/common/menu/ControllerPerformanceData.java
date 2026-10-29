/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Per-open-controller timings. Each long uses two logical ints, then IntContainerData packs shorts. */
public final class ControllerPerformanceData {
    public static final int SIZE = 6;
    private ControllerPerformanceData() { }

    public static ContainerData empty() { return new SimpleContainerData(SIZE); }

    public static ContainerData forController(TileController controller) {
        return new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> (int) controller.getAveragePerformanceMicros();
                    case 1 -> (int) (controller.getAveragePerformanceMicros() >>> 32);
                    case 2 -> (int) controller.getAverageRecipeSearchMicros();
                    case 3 -> (int) (controller.getAverageRecipeSearchMicros() >>> 32);
                    case 4 -> (int) controller.getPerformanceSamples();
                    case 5 -> controller.getRecipeSearchSamples();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return SIZE; }
        };
    }

    public static long tickMicros(ContainerData data) { return join(data.get(0), data.get(1)); }
    public static long searchMicros(ContainerData data) { return join(data.get(2), data.get(3)); }
    private static long join(int low, int high) { return (low & 0xFFFFFFFFL) | ((long) high << 32); }
    public static String tickText(ContainerData data) {
        return data.get(4) == 0 ? "—" : Long.toString(tickMicros(data));
    }
    public static String searchText(ContainerData data) {
        return data.get(5) == 0 ? "—" : BigDecimal.valueOf(searchMicros(data), 3)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
