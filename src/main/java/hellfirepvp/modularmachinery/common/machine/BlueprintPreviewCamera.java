/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's preview camera controls.
 */
package hellfirepvp.modularmachinery.common.machine;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Orthographic preview camera. Its displaced center lives in scene coordinates, like the source camera. */
public final class BlueprintPreviewCamera {
    private final float sceneSpan;
    private float yaw = 135, pitch = 25, zoom = 1;
    private double offsetX, offsetY, offsetZ;

    public BlueprintPreviewCamera() { this(85); }
    /** Keeps each viewport's existing framing while sharing the camera's world-space behavior. */
    public BlueprintPreviewCamera(float sceneSpan) {
        if (!Float.isFinite(sceneSpan) || sceneSpan <= 0) throw new IllegalArgumentException("Invalid preview scene span");
        this.sceneSpan = sceneSpan;
    }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float zoom() { return zoom; }
    public Quaternionf rotation() {
        return new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
    }
    public float scale(BlueprintSceneDefinition.Bounds bounds) {
        double x = (double) bounds.maxX() - bounds.minX() + 1;
        double y = (double) bounds.maxY() - bounds.minY() + 1;
        double z = (double) bounds.maxZ() - bounds.minZ() + 1;
        return (float) (sceneSpan / Math.max(1, Math.sqrt(x * x + y * y + z * z))) * zoom;
    }
    public Vector3f center(BlueprintSceneDefinition.Bounds bounds) {
        return new Vector3f((float) (((double) bounds.minX() + bounds.maxX() + 1) / 2 + offsetX),
                (float) (((double) bounds.minY() + bounds.maxY() + 1) / 2 + offsetY),
                (float) (((double) bounds.minZ() + bounds.maxZ() + 1) / 2 + offsetZ));
    }
    public void rotate(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        yaw = (float) (((yaw + dx) % 360 + 360) % 360);
        pitch = (float) Math.max(-89.9, Math.min(89.9, pitch + dy));
    }
    public void zoomBy(double delta) {
        if (Double.isFinite(delta)) zoom = (float) Math.max(.3, Math.min(3, zoom + delta * .1));
    }
    public void pan(double dx, double dy, BlueprintSceneDefinition.Bounds bounds) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        float scale = scale(bounds);
        // Inverse of the renderer's scale/rotation: the structure follows the pointer in either camera orientation.
        Vector3f shift = new Vector3f((float) (-dx / scale), (float) (dy / scale), 0).rotate(rotation().conjugate());
        if (!shift.isFinite()) return;
        offsetX += shift.x;
        offsetY += shift.y;
        offsetZ += shift.z;
    }
    /** Source resetCenter preserves rotation and zoom. */
    public void resetCenter() { offsetX = offsetY = offsetZ = 0; }
}
