/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

/** Fits JEI's recipe area at vanilla's smallest GUI size, including its headers and border. */
final class StructurePreviewLayout {
    static final int WIDTH = 180, HEIGHT = 133, BUTTON_HEIGHT = 14;
    static final int SCENE_TOP = 23, SCENE_BOTTOM = 81;
    static final int ACTIONS_Y = 83, CONTROLS_Y = 99, SUMMARY_Y = 115;
    static final int SUMMARY_ROWS = 1, ALTERNATIVE_ROWS = 2;
    static final int PRIMARY_Y = 25, ALTERNATIVE_Y = 44;
    static final int UPGRADE_Y = 24, UPGRADE_ROWS = 3;
    static final float CAMERA_SPAN = SCENE_BOTTOM - SCENE_TOP - 10;

    private StructurePreviewLayout() { }

    static boolean inScene(double x, double y) {
        return x >= 2 && x < 178 && y >= SCENE_TOP && y < SCENE_BOTTOM;
    }
}
