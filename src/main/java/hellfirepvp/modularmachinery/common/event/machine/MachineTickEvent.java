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
 * Fired every machine tick, carrying which phase of the tick it is (upstream 17 lines).
 *
 * <p>Upstream fires it at TileMultiblockMachineController U1055, inside the controller tick loop. THE PHASE FIELD IS
 * PUBLIC AND FINAL, exactly as upstream declares it (U11), because handlers read it directly rather than through a
 * getter -- so adding a getter here would be a change to the contract rather than a translation of it.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.MachineTickEvent")
public class MachineTickEvent extends MachineEvent {

    public final Phase phase;

    public MachineTickEvent(TileController controller, Phase phase) {
        super(controller);
        this.phase = phase;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("phase")
    public Phase getMachinePhase() { return phase; }
}


