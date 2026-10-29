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

import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Fired when ONE factory run is not crafting (upstream FactoryRecipeFailureEvent, 36 lines).
 *
 * <p>FIRE SITE, and it is the only one: {@code FactoryTicker.notWorking}, reached from two places -- a run
 * whose START tick event refused the tick, and a run whose energy draw failed this tick. Upstream's is
 * {@code TileFactoryController.onThreadRecipeFailure} (U286-U299), called from {@code isNotWorking} (U259-U269),
 * i.e. whenever the thread's status is not CRAFTING. The port's two callers ARE its two "not crafting" cases,
 * which is the mapping rather than an approximation: the port's tick either took its energy and advanced, or it
 * did not craft.</p>
 *
 * <h2>destructRecipe is DATA, not behaviour -- which is why this class is simple and the tick event is not</h2>
 *
 * <p>The flag is READ BY THE CALLER to decide whether to tear the run down (upstream U298 returns
 * {@code event.isDestructRecipe()}, and U263-U268 acts on it). There is no override here whose effect the flag
 * depends on, so a port can carry it without the scaffolding-without-behaviour problem the tick event has.
 * {@code setDestructRecipe} IS WRITABLE ON PURPOSE (upstream U32-U35): a handler may decide that a failure is
 * severe enough to destroy the in-flight run, or that it is not.</p>
 *
 * <p>THE DEFAULT COMES FROM THE RECIPE, not from this class: upstream constructs it with
 * {@code recipe.doesCancelRecipeOnPerTickFailure()} (upstream U293-U295), which this port carries as
 * {@code MachineRecipe.cancelOnPerTickFailure()} -- false unless a recipe declares
 * {@code cancelIfPerTickFails}, so a starved run is HELD rather than destroyed unless the author said otherwise.
 * That default is what makes "the machine keeps its items when the power dips" true.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.FactoryRecipeFailureEvent")
public class FactoryRecipeFailureEvent extends FactoryRecipeEvent {

    private final String cause;
    private boolean destructRecipe;

    public FactoryRecipeFailureEvent(FactoryRecipeThread recipeThread, TileController controller,
                                     String cause, boolean destructRecipe) {
        super(recipeThread, controller);
        this.cause = cause;
        this.destructRecipe = destructRecipe;
    }

    public FactoryRecipeFailureEvent(FactoryRecipeThread recipeThread, TileController controller,
                                     String cause, boolean destructRecipe,
                                     hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext context) {
        super(recipeThread, controller, context);
        this.cause = cause;
        this.destructRecipe = destructRecipe;
    }

    /** @return why the run is not crafting; upstream passes the thread's status message, this port a reason string */
    @org.openzen.zencode.java.ZenCodeType.Getter("cause")
    public String getCause() {
        return cause;
    }

    /** @return whether the caller should tear this run down; a handler may change it before the caller reads it */
    @org.openzen.zencode.java.ZenCodeType.Getter("destructRecipe")
    public boolean isDestructRecipe() {
        return destructRecipe;
    }

    @org.openzen.zencode.java.ZenCodeType.Method
    public void setDestructRecipe(boolean destructRecipe) {
        this.destructRecipe = destructRecipe;
    }
}
