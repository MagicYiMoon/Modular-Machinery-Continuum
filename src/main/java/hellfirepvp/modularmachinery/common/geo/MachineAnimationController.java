/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE controller animation predicate adapted for GeckoLib 4, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.geo;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/** Full sequences with stable playback between frames and invalidation at model/resource boundaries. */
public final class MachineAnimationController extends AnimationController<TileController> {
    private long observedRevision = Long.MIN_VALUE;
    private MachineGeoModel observedModel;
    private boolean suspended = true;

    public MachineAnimationController(TileController tile) {
        super(tile, "mmce_machine", 0,
                state -> ((MachineAnimationController) state.getController()).evaluate(state));
    }

    public double getTransitionLengthTicks() { return transitionLength; }

    /** GL4's public setter is integer-only; the protected field preserves the source double API. */
    public void setTransitionLengthTicks(double ticks) {
        if (!Double.isFinite(ticks) || ticks < 0) throw new IllegalArgumentException("transitionLengthTicks must be finite and nonnegative");
        transitionLength = ticks;
    }

    private PlayState evaluate(AnimationState<TileController> state) {
        MachineGeoModel model = animatable.isFormed()
                ? GeoMachineModelRegistry.selectedForController(animatable) : null;
        long revision = GeoMachineModelRegistry.revision();
        if (model != observedModel || revision != observedRevision) {
            observedModel = model;
            observedRevision = revision;
            resetPlayback();
        }
        if (model == null) return suspend();
        if (suspended) resetPlayback();

        var event = new ControllerModelAnimationEvent(animatable, model, state);
        event.postEvent();
        if (event.getPlayState() != 0 || event.getAnimations().isEmpty()) return suspend();

        suspended = false;
        // RawAnimation equality avoids restarting a one-shot introduction on every render frame.
        return state.setAndContinue(event.buildAnimation());
    }

    private PlayState suspend() {
        if (!suspended) resetPlayback();
        suspended = true;
        return PlayState.STOP;
    }

    private void resetPlayback() {
        stop();
        currentAnimation = null;
        currentRawAnimation = null;
        animationQueue.clear();
        boneAnimationQueues.clear();
        boneSnapshots.clear();
        shouldResetTick = true;
        justStartedTransition = false;
        forceAnimationReset();
    }
}
