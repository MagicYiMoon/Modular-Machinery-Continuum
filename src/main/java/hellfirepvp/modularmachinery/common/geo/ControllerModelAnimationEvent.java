/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.geo;

/** Script event used to declare animations available to a controller model. */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.ControllerModelAnimationEvent")
public final class ControllerModelAnimationEvent {
    private final MachineGeoModel model;
    /** Per-predicate working list; callbacks must not mutate the registry's model definition. */
    private final java.util.List<MachineGeoModel.Animation> animations = new java.util.ArrayList<>();
    private int playState;

    public ControllerModelAnimationEvent(MachineGeoModel model) {
        this.model = model;
        if (model != null) this.animations.addAll(model.getAnimations());
    }

    @org.openzen.zencode.java.ZenCodeType.Method
    public void addAnimation(String name) {
        addAnimation(name, false);
    }

    @org.openzen.zencode.java.ZenCodeType.Method
    public void addAnimation(String name, boolean loop) {
        if (name == null || name.isBlank()) return;
        animations.removeIf(animation -> animation.name().equals(name));
        animations.add(new MachineGeoModel.Animation(name, loop));
    }

    public java.util.List<MachineGeoModel.Animation> getAnimations() {
        return java.util.Collections.unmodifiableList(animations);
    }
    @org.openzen.zencode.java.ZenCodeType.Getter("playState")
    public int getPlayState() { return playState; }

    @org.openzen.zencode.java.ZenCodeType.Setter("playState")
    public void setPlayState(int playState) { this.playState = playState; }

    @org.openzen.zencode.java.ZenCodeType.Method
    public void setAnimation(String name) {
        setAnimation(name, false);
    }

    @org.openzen.zencode.java.ZenCodeType.Method
    public void setAnimation(String name, boolean loop) {
        animations.clear();
        addAnimation(name, loop);
    }
}
