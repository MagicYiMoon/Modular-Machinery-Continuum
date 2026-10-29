/* SPDX-License-Identifier: GPL-3.0-only
 * Derived from MMCE ControllerModelAnimationEvent; adapted to GeckoLib 4, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.geo;

import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import org.openzen.zencode.java.ZenCodeType;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

/** Per-predicate animation queue and live GeckoLib context, never a mutable model definition. */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@ZenCodeType.Name("mods.modularmachinery.ControllerModelAnimationEvent")
public final class ControllerModelAnimationEvent extends MachineEvent {
    private final MachineGeoModel model;
    private final AnimationState<TileController> state;
    private final java.util.List<MachineGeoModel.Animation> animations = new java.util.ArrayList<>();
    private int playState;

    /** Retained for Java callers declaring a queue without a render context. */
    public ControllerModelAnimationEvent(MachineGeoModel model) { this(null, model, null); }

    public ControllerModelAnimationEvent(TileController controller, MachineGeoModel model,
                                         AnimationState<TileController> state) {
        super(controller);
        this.model = model;
        this.state = state;
        if (model != null) animations.addAll(model.getAnimations());
    }

    @ZenCodeType.Method
    public void addAnimation(String name) { addAnimation(name, false); }

    @ZenCodeType.Method
    public void addAnimation(String name, boolean loop) {
        if (name != null && !name.isBlank()) animations.add(new MachineGeoModel.Animation(name, loop));
    }

    @ZenCodeType.Method
    public void setAnimation(String name) { setAnimation(name, false); }

    @ZenCodeType.Method
    public void setAnimation(String name, boolean loop) {
        animations.clear();
        addAnimation(name, loop);
    }

    public java.util.List<MachineGeoModel.Animation> getAnimations() {
        return java.util.Collections.unmodifiableList(animations);
    }

    public RawAnimation buildAnimation() {
        RawAnimation raw = RawAnimation.begin();
        for (var animation : animations) {
            if (animation.loop()) raw.thenLoop(animation.name());
            // GL4 thenPlay uses DEFAULT, which can inherit loop=true from the resource file.
            else raw.then(animation.name(), software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE);
        }
        return raw;
    }

    @ZenCodeType.Getter("currentModelName")
    public String getCurrentModelName() { return model == null ? "" : model.getMachineName(); }

    @ZenCodeType.Getter("currentAnimationName")
    @ZenCodeType.Nullable
    public String getCurrentAnimationName() {
        var animation = state == null ? null : state.getController().getCurrentAnimation();
        return animation == null ? null : animation.animation().name();
    }

    @ZenCodeType.Getter("transitionLengthTicks")
    public double getTransitionLengthTicks() {
        return state != null && state.getController() instanceof MachineAnimationController controller
                ? controller.getTransitionLengthTicks() : 0;
    }

    @ZenCodeType.Setter("transitionLengthTicks")
    public void setTransitionLengthTicks(double ticks) {
        if (!Double.isFinite(ticks) || ticks < 0) throw new IllegalArgumentException("transitionLengthTicks must be finite and nonnegative");
        if (state != null && state.getController() instanceof MachineAnimationController controller)
            controller.setTransitionLengthTicks(ticks);
    }

    @ZenCodeType.Getter("playState")
    public int getPlayState() { return playState; }

    @ZenCodeType.Setter("playState")
    public void setPlayState(int playState) { this.playState = playState; }

    @ZenCodeType.Getter("animationSpeed")
    public double getAnimationSpeed() { return state == null ? 1 : state.getController().getAnimationSpeed(); }

    @ZenCodeType.Getter("animationTick")
    public double getAnimationTick() { return state == null ? 0 : state.getAnimationTick(); }

    /** Keep source script values: running=0, transitioning=1, stopped=2 (GL4 paused also maps to 2). */
    @ZenCodeType.Getter("animationState")
    public int getAnimationState() {
        if (state == null) return 2;
        return switch (state.getController().getAnimationState()) {
            case RUNNING -> 0;
            case TRANSITIONING -> 1;
            case PAUSED, STOPPED -> 2;
        };
    }

    @ZenCodeType.Getter("limbSwing")
    public float getLimbSwing() { return state == null ? 0 : state.getLimbSwing(); }

    @ZenCodeType.Getter("limbSwingAmount")
    public float getLimbSwingAmount() { return state == null ? 0 : state.getLimbSwingAmount(); }

    @ZenCodeType.Getter("partialTick")
    public float getPartialTick() { return state == null ? 0 : state.getPartialTick(); }

    @ZenCodeType.Getter("moving")
    public boolean isMoving() { return state != null && state.isMoving(); }

    /** The source event visits components/upgrades before the animation-specific script handlers. */
    @Override
    public void postCrTEvent() {
        GeoMachineModelRegistry.dispatchAnimation(controller == null ? null : controller.getMachineName(), this);
    }
}
