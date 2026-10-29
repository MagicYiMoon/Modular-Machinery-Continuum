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
import hellfirepvp.modularmachinery.common.util.SmartInterfaceData;
import net.minecraft.core.BlockPos;

/**
 * Notification when a provider publishes a datum or its UI accepts a player edit.
 * Java callers retain the port's raw Data. Scripts see the source SmartInterfaceData contract as a
 * guarded view of that same storage, so newData.value edits affect recipe readers and save/sync.
 * A value setter does not recursively emit this event. While this event is being dispatched, its new
 * datum may be edited even before the formation walk finishes recording the controller's components.
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.SmartInterfaceUpdateEvent")
public class SmartInterfaceUpdateEvent extends MachineEvent {

    private final BlockPos interfacePos;
    private final TileSmartInterface.Data newData;
    private boolean publishing;

    public SmartInterfaceUpdateEvent(TileController controller, BlockPos interfacePos,
                                     TileSmartInterface.Data newData) {
        super(controller);
        this.interfacePos = interfacePos.immutable();
        this.newData = newData;
    }

    /** @return the position of the smart interface that published this data (upstream U20's first argument) */
    @org.openzen.zencode.java.ZenCodeType.Getter("interfacePos")
    public BlockPos getInterfacePos() {
        return interfacePos;
    }

    /** @return the datum that was just published */
    public TileSmartInterface.Data getNewData() {
        return newData;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("newData")
    @org.openzen.zencode.java.ZenCodeType.Nullable
    public SmartInterfaceData getScriptNewData() {
        if (controller == null || newData == null) return null;
        TileSmartInterface owner = null;
        var level = controller.getLevel();
        if (level != null && (!(level instanceof net.minecraft.server.level.ServerLevel server) || server.getServer().isSameThread())
                && level.hasChunkAt(interfacePos) && level.getBlockEntity(interfacePos) instanceof TileSmartInterface iface) owner = iface;
        return hellfirepvp.modularmachinery.common.util.SmartInterfaceData.publication(controller, owner, newData, () -> publishing);
    }

    @Override public void postEvent() {
        boolean previous = publishing;
        publishing = true;
        try { super.postEvent(); }
        finally { publishing = previous; }
    }

    @Override public void postCrTEvent() {
        String machine = newData == null ? null : newData.getMachineName();
        if (machine == null) super.postCrTEvent();
        else hellfirepvp.modularmachinery.common.machine.ScriptMachineEventRegistry.dispatchToMachine(machine, this);
    }
}


