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
 * When a paired event fires: once before the work and once after (upstream Phase, 6 lines).
 *
 * <p>The two values come from the call sites, not from the enum body: an event is constructed with Phase.START,
 * the work runs, and the matching event is constructed with Phase.END (TileMachineController U174 and U199 for the
 * recipe tick, TileMultiblockMachineController U1022 and U1039 for the recipe check).</p>
 *
 * <p><b>PUBLIC HERE, PACKAGE-PRIVATE UPSTREAM, AND THE REASON IS PACKAGING RATHER THAN DESIGN.</b> Upstream declares
 * this enum with no modifier and uses it from the same package. THIS PORT PUTS THE EVENTS IN common.event.machine AND
 * THE CONTROLLER IN common.tiles, SO THE SAME DECLARATION WOULD BE UNREACHABLE FROM THE CLASS THAT CONSTRUCTS A
 * MachineTickEvent. Widening it is the minimum change that keeps the call sites the same shape as upstream.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.Phase")
public enum Phase {
    START,
    END
}

