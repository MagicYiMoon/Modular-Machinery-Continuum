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

import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Base of the recipe events (upstream RecipeEvent, 63 lines).
 *
 * RULE A0 -- THE TYPE SET IS REDUCED, AND THE REASON IS STRUCTURAL RATHER THAN CONVENIENT (PLAN 500.3):
 *
 *   upstream   RecipeEvent(TileMultiblockMachineController, RecipeThread, RecipeCraftingContext)     U22
 *              with activeRecipe derived from context.getActiveRecipe()                               U24
 *   this port  NO ActiveMachineRecipe, NO RecipeThread, and RecipeCraftingContext HAS NO getActiveRecipe
 *
 * WHY: UPSTREAM RUNS A RECIPE ON A RecipeThread, WHICH IS WHAT THAT TYPE EXISTS FOR. THIS PORT RUNS RECIPES
 * SYNCHRONOUSLY IN THE TICK -- MachineTicker.advance is called straight from tick with NO THREAD ANYWHERE in the
 * recipe path -- SO A THREAD HANDLE WOULD NAME SOMETHING THAT DOES NOT HAPPEN.
 *
 * AND WHAT IS KEPT IS THE OBSERVABLE CONTRACT: which event fires when, the cancel semantics, and the caller-owned
 * handlers. What handlers LOSE is the ability to inspect a thread, BECAUSE THERE IS NO THREAD TO INSPECT. That is
 * recorded here rather than left for a handler author to discover.
 *
 * <p>getContext().getRecipe() REPLACES upstream getActiveRecipe().getRecipe(): the port context carries the recipe
 * directly (RecipeCraftingContext R111), so the wrapper upstream needs has no purpose here.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.RecipeEvent")
public class RecipeEvent extends MachineEvent {

    private final RecipeCraftingContext context;

    public RecipeEvent(TileController controller, RecipeCraftingContext context) {
        super(controller);
        this.context = context;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("context")
    public RecipeCraftingContext getContext() {
        return context;
    }

    /**
     * Recipe callbacks are kept in the optional CraftTweaker integration registry rather than in the core
     * recipe object. This keeps JSON/client snapshots serializable while still giving script handlers the same
     * exact-class dispatch and cancellation short-circuit as upstream RecipeEvent.postCrTEvent().
     */
    @Override
    public void postEvent() {
        super.postEvent();
        if (!isCanceled()) {
            hellfirepvp.modularmachinery.common.crafting.ScriptRecipeRegistry.dispatch(this);
        }
    }
}
