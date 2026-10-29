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
import net.minecraftforge.eventbus.api.Event;

















 
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.MachineEvent")
public class MachineEvent extends Event {

    protected final TileController controller;

    public MachineEvent(TileController controller) {
        this.controller = controller;
    }

    public TileController getController() {
        return controller;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("controller")
    public hellfirepvp.modularmachinery.common.helper.IMachineController getIMachineController() {
        return controller;
    }

    /**
     * Dispatches this event through the machine, in upstream order.
     *
     * <p>Wrapped the way upstream wraps it: a component or handler that throws must not take the server tick
     * down with it, and upstream chose to log and continue rather than propagate.</p>
     */
    public void postEvent() {
        try {
            postEventToComponents();
            // Upstream's UpgradeMachineEventHandler runs after component callbacks even when a component has
            // already canceled the event. Keep that ordering while resolving live bus upgrades on this tick thread.
            hellfirepvp.modularmachinery.common.upgrade.ScriptUpgradeEventRegistry.dispatch(this);
            if (!isCanceled()) {
                postCrTEvent();
            }
        } catch (Exception e) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                    "Caught an exception when posting a machine event", e);
        }
    }

    /**
     * Path 1: every component tile of this machine that implements the contract.
     *
     * <p>THE WALK IS THIS PORT OWN AND IS REUSED, NOT MIRRORED (rule B): upstream iterates a map of live objects
     * (491.4), while this port holds boundPositions and resolves them. The predicate is the same one
     * isStructureChunkLoaded uses, so an unloaded component is skipped rather than resolved.</p>
     */
    public void postEventToComponents() {
        if (controller == null) {
            return;
        }
        net.minecraft.world.level.Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        for (net.minecraft.core.BlockPos componentPos : controller.getBoundPositions()) {
            if (!level.isLoaded(componentPos)) {
                continue;
            }
            if (level.getBlockEntity(componentPos) instanceof MachineComponentTileNotifiable notifiable) {
                notifiable.onMachineEvent(this);
                if (isCanceled()) {
                    break;
                }
            }
        }
    }

    /**
     * Dispatches registered script callbacks without loading the optional CraftTweaker API.
     * Specialized client events can override this step while preserving component/upgrade ordering.
     */
    public void postCrTEvent() {
        hellfirepvp.modularmachinery.common.machine.ScriptMachineEventRegistry.dispatch(this);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}


