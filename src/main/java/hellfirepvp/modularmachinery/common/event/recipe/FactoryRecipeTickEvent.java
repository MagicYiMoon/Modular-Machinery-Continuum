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
package hellfirepvp.modularmachinery.common.event.recipe;

import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Fired around each tick of ONE factory run (upstream FactoryRecipeTickEvent, 81 lines).
 *
 * <p>FIRE SITES, and there are two per tick per running run: {@code FactoryTicker.tickThread} fires
 * {@code Phase.START} before the run's work and {@code Phase.END} after it. Upstream's are the same two
 * (TileFactoryController U227 and U252), with one difference this port states: upstream returns BEFORE the end
 * event on its not-working paths (U242-U249), while this port always closes the bracket, because the port's
 * established rule for a bracketed tick is that a handler which prepared at START gets its END (PLAN 561/565,
 * and MachineTicker's {@code finally}).</p>
 *
 * <h2>THE OVERRIDE IS THE BEHAVIOUR, which is why the flags are not merely stored</h2>
 *
 * <p>Upstream's {@code postEvent} (U26-U49) reads its own flags after the dispatch and ACTS:
 * {@code preventProgressing} rewinds the run's tick counter so the craft does not advance, and a failure with
 * {@code destructRecipe} tears the run down. A port that kept the flags and dropped the override would produce
 * an event whose handlers set a flag and nothing happens -- the section 420 pattern. Both effects are here:</p>
 *
 * <pre>
 * upstream                                            this port
 * activeRecipe.getTick()/setTick(getTick() - 1)         FactoryRecipeThread.rewindRecipeTick()
 *   guarded by "if getTick() > 0"                        the same floor, in the same method
 * recipeThread.setActiveRecipe(null).setContext(null)
 *   .setStatus(failure(reason))
 *   .getSemiPermanentModifiers().clear()                FactoryRecipeThread.abandonRecipe()
 * </pre>
 *
 * <p>The last line is the port's own teardown operation, used by every other failure path in this port; the
 * three things upstream clears beyond the active recipe do not exist here (see FactoryRecipeThread).</p>
 *
 * <p>The rewind is a ONE-STEP REWIND rather than a setter, and its net effect is one tick of no progress
 * because the ticker advances unconditionally one line later -- the argument PLAN 542 recorded for the
 * single-craft event, applied unchanged to a run.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.FactoryRecipeTickEvent")
public class FactoryRecipeTickEvent extends FactoryRecipeEvent {

    public final Phase phase;
    private boolean preventProgressing = false;
    private boolean isFailure = false;
    private boolean destructRecipe = false;
    private String failureReason = null;

    public FactoryRecipeTickEvent(FactoryRecipeThread recipeThread, TileController controller, Phase phase) {
        super(recipeThread, controller);
        this.phase = phase;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("phase")
    public Phase getRecipePhase() { return phase; }

    @Override
    public void postEvent() {
        super.postEvent();

        if (preventProgressing) {
            // Upstream U30-U38, guard included: the run does not advance on this tick.
            recipeThread.rewindRecipeTick();
            return;
        }

        if (isFailure && destructRecipe) {
            // Upstream U40-U46: the run is torn down in place. This port's equivalent operation.
            recipeThread.abandonRecipe();
        }
    }

    /** Fails this run, optionally destroying it. Sets the cancel flag as well, matching upstream U51-U57. */
    @org.openzen.zencode.java.ZenCodeType.Method
    public void setFailed(boolean destructRecipe, String reason) {
        this.isFailure = true;
        this.destructRecipe = destructRecipe;
        this.failureReason = reason;
        setCanceled(true);
    }

    /** Holds this run back one tick. Sets the cancel flag as well, matching upstream U59-U64. */
    @org.openzen.zencode.java.ZenCodeType.Method
    public void preventProgressing(String reason) {
        this.preventProgressing = true;
        this.failureReason = reason;
        setCanceled(true);
    }

    /** @return true when a handler refused this tick's work */
    @org.openzen.zencode.java.ZenCodeType.Getter("failure")
    public boolean isFailure() {
        return isFailure;
    }

    /** @return true when a handler asked for this run to be destroyed */
    @org.openzen.zencode.java.ZenCodeType.Getter("destructRecipe")
    public boolean isDestructRecipe() {
        return destructRecipe;
    }

    /** @return true when a handler held this run back */
    @org.openzen.zencode.java.ZenCodeType.Getter("preventProgressing")
    public boolean isPreventProgressing() {
        return preventProgressing;
    }

    /** @return the handler's reason, or {@code null} when it gave none */
    public String getFailureReason() {
        return failureReason;
    }
}
