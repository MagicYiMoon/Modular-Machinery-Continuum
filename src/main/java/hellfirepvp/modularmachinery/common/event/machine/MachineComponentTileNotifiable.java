/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.event.machine;

/**
 * A component block entity that wants to observe machine events (PLAN sections 491-493, path 1).
 *
 * RULE A0 -- upstream has this as {@code MachineComponentTileNotifiable} and MachineEvent calls it at U55-U56:
 *
 *     if (tileEntity instanceof final MachineComponentTileNotifiable componentTile) {
 *         componentTile.onMachineEvent(this);
 *         if (isCanceled()) break;
 *     }
 *
 * SO THE CONTRACT IS ONE METHOD AND THE NAME IS KEPT. What is NOT kept is the traversal (491.4): upstream
 * iterates controller.getFoundComponents(), a map of LIVE component objects, and this port holds boundPositions,
 * a list of positions, so that a chunk unload cannot leave a live reference behind. THE CONTRACT IS THE SAME;
 * ONLY THE WALK THAT REACHES IT DIFFERS.
 */
public interface MachineComponentTileNotifiable {

    /**
     * Called for each component tile of the machine the event belongs to. Cancel semantics belong to the
     * CALLER, matching upstream: it stops the walk as soon as the event is cancelled.
     *
     * @param event the event being dispatched through this machine
     */
    void onMachineEvent(MachineEvent event);
}

