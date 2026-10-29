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
import hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe;
import hellfirepvp.modularmachinery.common.machine.RecipeThread;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Base recipe event: the evaluated context, an immutable progress observation and a live logical-thread query.
 * Ordinary controllers have one implicit thread; factory events explicitly identify their own run.
 * A closing END event retains the completed craft's observation even after the ticker returns its thread to idle.
 * Search/check events can precede assignment of a craft; their zero-progress observation describes the candidate.
 * The logical thread is independent of Java worker threads; execution remains on the server tick thread.
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.RecipeEvent")
public class RecipeEvent extends MachineEvent {

    private final RecipeCraftingContext context;
    private final hellfirepvp.modularmachinery.common.machine.RecipeThread recipeThread;
    private final hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe activeRecipe;

    public RecipeEvent(TileController controller, RecipeCraftingContext context) {
        this(controller, context, context != null && context.runtimeThread() != null ? context.runtimeThread()
                : controller == null || controller instanceof hellfirepvp.modularmachinery.common.tiles.TileFactoryController
                ? null : new hellfirepvp.modularmachinery.common.machine.RecipeThread(controller, null));
    }

    protected RecipeEvent(TileController controller, RecipeCraftingContext context,
                          hellfirepvp.modularmachinery.common.machine.RecipeThread recipeThread) {
        super(controller);
        this.context = context;
        this.recipeThread = recipeThread;
        var running = recipeThread == null ? null : recipeThread.getActiveRecipe();
        if (context == null) activeRecipe = running;
        else if (running != null && running.getRegistryName().equals(context.getRecipeName())) activeRecipe = running;
        else if (context.observedRecipe() != null) activeRecipe = context.observedRecipe();
        else activeRecipe = new hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe(
                    context.getRecipeName(), 0, context.effectiveDuration(), 1, 1);
        if (context != null) context.observeRecipe(activeRecipe);
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("activeRecipe")
    @org.openzen.zencode.java.ZenCodeType.Nullable
    public ActiveMachineRecipe getActiveRecipe() { return activeRecipe; }

    @org.openzen.zencode.java.ZenCodeType.Getter("recipeThread")
    @org.openzen.zencode.java.ZenCodeType.Nullable
    public RecipeThread getRecipeThread() { return recipeThread; }

    @org.openzen.zencode.java.ZenCodeType.Getter("context")
    @org.openzen.zencode.java.ZenCodeType.Nullable
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
