/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

/** Editor canvas coordinates stay stable; only its presentation shrinks to fit the window. */
record EditorViewport(int width, int height, float scale, float x, float y) {
    static EditorViewport fit(int screenWidth, int screenHeight) {
        float scale = Math.min(1F, Math.min(Math.max(1, screenWidth - 4) / 440F,
                Math.max(1, screenHeight - 4) / 260F));
        int width = Math.max(440, (int) Math.floor(screenWidth / scale));
        int height = Math.max(260, (int) Math.floor(screenHeight / scale));
        return new EditorViewport(width, height, scale,
                (screenWidth - width * scale) / 2F, (screenHeight - height * scale) / 2F);
    }
    double logicalX(double screenX) { return (screenX - x) / scale; }
    double logicalY(double screenY) { return (screenY - y) / scale; }
    double screenX(double logicalX) { return x + logicalX * scale; }
    double screenY(double logicalY) { return y + logicalY * scale; }
}
