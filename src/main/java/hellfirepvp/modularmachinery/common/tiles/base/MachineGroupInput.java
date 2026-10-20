/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/package hellfirepvp.modularmachinery.common.tiles.base;

/**
 * A component that can be told to feed a <i>group</i> of requirement slots from one inventory.
 *
 * <p>Upstream's API, read from the jar: five methods, no state. {@link TileMachineComponent} supplies the
 * storage and the NBT.
 *
 * <p><b>This is deliberately not dead code, and the distinction matters.</b> §154 found that upstream's four
 * base-layer classes have no implementors in this port, which is why porting the layer on its own would have
 * added classes nothing references. This interface is implemented by {@link TileMachineComponent} — the base
 * every hatch and bus in the port already extends — so it is exercised by every component the self-test forms.
 */
public interface MachineGroupInput {

    /** @param groupId the group this component belongs to */
    void setGroupId(int groupId);

    /**
     * @return whether this component is <b>capable</b> of group input at all.
     *
     * <p>Separate from {@link #isGroupInput()}: a component can be able to act as a group input without having
     * been configured as one. Upstream keeps both, and merging them would leave the GUI unable to tell
     * "not configured" from "cannot be configured".
     */
    boolean canGroupInput();

    /** @return whether this component is currently configured as a group input */
    boolean isGroupInput();

    /** @param groupInput whether to act as a group input */
    void setGroupInput(boolean groupInput);

    /**
     * @return the group this component belongs to while it is CONFIGURED as a group input, or -1 when it is not
     *
     * <p>Upstream's rule: {@code TileColorableMachineComponent.getGroupId} gates on {@code isGroupInput()} and
     * returns -1 otherwise. <b>0 is a group number, not "unset"</b> — the packet's {@code clampGroupId} holds a
     * REQUESTED id at {@code >= 0} because a negative cannot be typed into the GUI, which is a different question
     * from what this getter reports for a component that is not participating in a group at all.
     */
    int getGroupId();
}