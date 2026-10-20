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
 * Fired when a multiblock finishes forming (upstream 13 lines).
 *
 * <p>Upstream fires it at TileMultiblockMachineController U609, guarded by nothing: it is the moment the structure
 * became valid. NOTHING IS ADDED HERE -- the class has no state beyond the controller, which MachineEvent carries.
 * A subclass that adds nothing is the correct port of a subclass that adds nothing.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.MachineStructureFormedEvent")
public class MachineStructureFormedEvent extends MachineEvent {

    public MachineStructureFormedEvent(TileController controller) {
        super(controller);
    }
}


