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
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import net.minecraft.core.BlockPos;

/**
 * Fired when a smart interface publishes a value to a machine (upstream 33 lines).
 *
 * <h2>RULE A0 — THE PAYLOAD IS THIS PORT'S DATA TYPE, AND THE CRAFTTWEAKER FIELD TYPE IS GONE</h2>
 *
 * <p>Two deviations, both decided here rather than inherited, both with the upstream line that forces
 * them:</p>
 *
 * <p><b>1. THE FIELD WAS {@code SmartInterfaceData} AND IS NOW {@link TileSmartInterface.Data}.</b> The user
 * chose branch (a) of the three-branch fork recorded at PLAN 551.4 — "adapt the event to the port model" —
 * and the consequence is recorded where the choice was made: the port's {@code Data} is the single
 * representation of a smart interface value (its key is the declared type's name and its value is the
 * number or the string), and the upstream-shaped {@code common.util.SmartInterfaceData} class is
 * <b>deleted</b> rather than kept beside it. Two shapes for one concept is the defect this port keeps
 * meeting: one of them ends up with no reader. Upstream's own two reader call sites
 * ({@code PktSmartInterfaceUpdate} U48-U53 and {@code GuiContainerSmartInterface} U58-U95) both go through
 * {@code provider.getMachineData(...)}, and so does this port's requirement — so the shape a reader
 * actually receives is what is kept.</p>
 *
 * <p><b>2. THE CRAFTTWEAKER POSITION IS A VANILLA {@link BlockPos}.</b> Kept from the earlier round, and it
 * is the same deviation PLAN 547.3 recorded: upstream stores
 * {@code crafttweaker.api.world.IBlockPos} in the event itself, converting at construction
 * (upstream U15/U20), and a core class must not name a soft dependency (488.4). Every field of information
 * upstream carried is still here, in the port's own type.</p>
 *
 * <h2>The producer, stated because this project has been bitten by events without one</h2>
 *
 * <p>This event has TWO real fire sites, and both are production paths:</p>
 *
 * <ol>
 *   <li>{@code TileSmartInterface.SmartInterfaceProvider.addMachineData} — reached from
 *       {@code MachineFormation}'s binding walk at every structure formation, i.e. the same place upstream
 *       fires it (upstream U149, itself reached from {@code TileMultiblockMachineController U922/U927}).</li>
 *   <li>The smart interface update packet's server handler — a player setting a value from the screen
 *       (upstream {@code PktSmartInterfaceUpdate} U54).</li>
 * </ol>
 *
 * <p><b>AND IT HAS NO READER IN THIS PORT, WHICH IS STATED RATHER THAN PAPERED OVER.</b> Upstream's only
 * consumers are CraftTweaker script handlers — {@code MachineBuilder.addSmartInterfaceUpdateHandler}
 * (U415-U416), {@code MMEvents.onSmartInterfaceUpdate} (U155-U159) and the two upgrade builders
 * (U284/U321). Path 3 of the dispatch ({@code MachineEvent.postCrTEvent}) is deliberately empty here
 * (PLAN 493), and path 1 reaches components implementing
 * {@link MachineComponentTileNotifiable} — of which this port has none (upstream's only implementer is
 * {@code MEPatternProvider}, U88). So the event is a faithfully-shaped, really-fired notification with no
 * in-port subscriber, exactly as upstream would be with no scripts installed, and PLAN 663 records it in
 * those words.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.SmartInterfaceUpdateEvent")
public class SmartInterfaceUpdateEvent extends MachineEvent {

    private final BlockPos interfacePos;
    private final TileSmartInterface.Data newData;

    public SmartInterfaceUpdateEvent(TileController controller, BlockPos interfacePos,
                                     TileSmartInterface.Data newData) {
        super(controller);
        this.interfacePos = interfacePos;
        this.newData = newData;
    }

    /** @return the position of the smart interface that published this data (upstream U20's first argument) */
    @org.openzen.zencode.java.ZenCodeType.Getter("interfacePos")
    public BlockPos getInterfacePos() {
        return interfacePos;
    }

    /** @return the datum that was just published */
    @org.openzen.zencode.java.ZenCodeType.Getter("newData")
    public TileSmartInterface.Data getNewData() {
        return newData;
    }
}


