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

import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Fired when the structure is re-checked (upstream 13 lines).
 *
 * <p>UPSTREAM FIRES IT IN TWO PLACES, AND THE SECOND IS WORTH NOTING: TileMultiblockMachineController U557 on an
 * update, and U610 -- IMMEDIATELY AFTER MachineStructureFormedEvent at U609. So a formation posts BOTH, in that
 * order, and a handler that only listens for one of them sees different behaviour. That ordering is upstream own and
 * is carried over wherever the port fires these.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.MachineStructureUpdateEvent")
public class MachineStructureUpdateEvent extends MachineEvent {

    public MachineStructureUpdateEvent(TileController controller) {
        super(controller);
    }
}


