/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's world-scene selection.
 */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic GUI picking against the same outline shapes and matrix used for rendering. No client types. */
public final class BlueprintRayPicker {
    private BlueprintRayPicker() { }

    /** JEI draws in screen coordinates but delivers mouse events relative to its recipe panel. */
    public static Matrix4f relativeTransform(Matrix4f sceneToGui, Matrix4f panelToGui) {
        return new Matrix4f(panelToGui).invert().mul(sceneToGui);
    }

    public static BlockPos pick(Matrix4f sceneToGui, double mouseX, double mouseY,
                                BlockGetter scene, Iterable<BlockPos> visiblePositions) {
        Matrix4f inverse = new Matrix4f(sceneToGui).invert();
        // In Minecraft's GUI projection positive GUI Z is nearer the camera.
        Vector3f a = inverse.transformPosition(new Vector3f((float) mouseX, (float) mouseY, 1000));
        Vector3f b = inverse.transformPosition(new Vector3f((float) mouseX, (float) mouseY, -1000));
        Vec3 start = new Vec3(a.x, a.y, a.z), end = new Vec3(b.x, b.y, b.z);
        BlockPos nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (BlockPos pos : visiblePositions) {
            var state = scene.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getShape(scene, pos, CollisionContext.empty());
            if (shape.isEmpty() && !state.getFluidState().isEmpty()) shape = Shapes.block();
            BlockHitResult hit = shape.clip(start, end, pos);
            if (hit != null && hit.getLocation().distanceToSqr(start) < distance) {
                distance = hit.getLocation().distanceToSqr(start);
                nearest = pos.immutable();
            }
        }
        return nearest;
    }
}
