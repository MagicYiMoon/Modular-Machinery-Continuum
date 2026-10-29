/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.helper.IDynamicPatternInfo;
import net.minecraft.core.Direction;

/** Values are copied so retained script observations cannot change on the next formation or reload. */
public record DynamicPatternInfo(String name, Direction facing, int size, int minSize, int maxSize)
        implements IDynamicPatternInfo {
    @Override public String getPatternName() { return name; }
    // Source Status.getFacing() returns EnumFacing.name(), despite its lowercase Javadoc.
    @Override public String getFacing() { return facing.name(); }
    @Override public int getSize() { return size; }
    @Override public int getMinSize() { return minSize; }
    @Override public int getMaxSize() { return maxSize; }

    public static DynamicPatternInfo measured(DynamicPattern pattern, Direction controllerFacing, int size) {
        // Source currently measures the same layout for every face, selecting an enum-map entry on equal size.
        // Keep deterministic enum order for that tie. Vertical faces are not rotated.
        Direction match = pattern.getFaces().stream().map(face -> rotateFace(face, controllerFacing))
                .min(java.util.Comparator.comparingInt(Direction::get3DDataValue)).orElse(controllerFacing);
        return new DynamicPatternInfo(pattern.getName(), match, size, pattern.getMinSize(), pattern.getMaxSize());
    }

    private static Direction rotateFace(Direction face, Direction controllerFacing) {
        if (face.getAxis().isVertical()) return face;
        Direction cursor = controllerFacing;
        while (cursor != Direction.NORTH && cursor.getAxis().isHorizontal()) {
            cursor = cursor.getCounterClockWise();
            face = face.getCounterClockWise();
        }
        return face;
    }
}
