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
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.base;

import hellfirepvp.modularmachinery.common.machine.IOType;

/**
 * Implemented by every block entity that is a functional part of a formed multiblock.
 *
 * <p>Upstream's {@code MachineComponentTile} let the machine controller locate and address its
 * component block entities. It is kept as a distinct interface — rather than being folded into
 * {@link TileEntityBase} — because casing and other decorative components are block entities that
 * deliberately are <b>not</b> machine components, and the structure matcher branches on exactly this
 * distinction.
 *
 * <h2>Why {@link #getIOType()} lives here</h2>
 *
 * <p>A requirement must know whether a component is an <b>input</b> or an <b>output</b>: drawing from an
 * output bus would void the product the machine just made. The first implementation read this
 * reflectively, because the concept was already present on two separate base classes
 * ({@code TileItemBus}, {@code TileFluidTank}).
 *
 * <p>That was a genuine bug, not merely inelegant: the reflective call targeted a <b>public</b> method
 * on subclasses of a <b>package-private</b> class, so {@code Method#invoke} threw
 * {@code IllegalAccessException}, which the code caught and interpreted as "this component has no
 * direction". Every component was therefore treated as directionless, {@code ComponentKind.INPUT}
 * matched nothing, and an energy requirement could never find its hatch — presenting as a machine that
 * forms, consumes its inputs, and then <b>stalls forever at 0 progress</b>.
 *
 * <p>Declaring the accessor on the interface removes both the reflection and the failure mode: it is
 * compiler-enforced, so a component cannot silently forget to expose its direction.
 */
public interface MachineComponentTile {

    /**
     * Binds this component to the machine controller that consumed it during structure assembly.
     *
     * @param controllerPos the controller's position, or {@code null} to unbind
     */
    void setControllerPos(net.minecraft.core.BlockPos controllerPos);

    /**
     * @return the controller position this component is bound to, or {@code null} if unbound
     */
    net.minecraft.core.BlockPos getControllerPos();

    /** @return true while this component is bound to a formed machine */
    default boolean isBound() {
        return getControllerPos() != null;
    }

    /**
     * @return this component's direction, or {@code null} for a component that has none (a controller
     *         part or a passive component), which is treated as acceptable for either use
     */
    default IOType getIOType() {
        return null;
    }
}
