/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.geo;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A script supplied GeckoLib model definition for a Modular Machinery controller.
 *
 * <p>The definition deliberately contains only resource identifiers and animation names.  This
 * class is loaded on both sides, so it must not refer to GeckoLib client renderer classes.  The
 * client renderer resolves the identifiers when it actually renders a controller.</p>
 */
public final class MachineGeoModel {
    private final String machineName;
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;
    private final List<Animation> animations = new ArrayList<>();

    public MachineGeoModel(String machineName, ResourceLocation model, ResourceLocation texture,
                           ResourceLocation animation) {
        this.machineName = machineName;
        this.model = model;
        this.texture = texture;
        this.animation = animation;
    }

    public String getMachineName() { return machineName; }
    public ResourceLocation getModel() { return model; }
    public ResourceLocation getTexture() { return texture; }
    public ResourceLocation getAnimation() { return animation; }

    /** Adds an animation to the controller's trigger list in declaration order. */
    public void addAnimation(String name, boolean loop) {
        if (name == null || name.isBlank()) return;
        animations.removeIf(a -> a.name().equals(name));
        animations.add(new Animation(name, loop));
    }

    public List<Animation> getAnimations() {
        return Collections.unmodifiableList(animations);
    }

    public void clearAnimations() { animations.clear(); }

    /** The first declared animation is the default presentation for a formed machine. */
    public Animation defaultAnimation() {
        return animations.isEmpty() ? null : animations.get(0);
    }

    public record Animation(String name, boolean loop) { }
}
