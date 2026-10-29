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















 
public interface MachineComponentTileNotifiable {

    /**
     * Called for each component tile of the machine the event belongs to. Cancel semantics belong to the
     * CALLER, matching upstream: it stops the walk as soon as the event is cancelled.
     *
     * @param event the event being dispatched through this machine
     */
    void onMachineEvent(MachineEvent event);
}

