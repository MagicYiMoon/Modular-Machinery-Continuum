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
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.machine.RecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Base of the factory recipe events -- ONE RUN, rather than the controller as a whole.
 *
 * <p>Upstream: {@code FactoryRecipeEvent extends RecipeEvent} and adds the thread
 * (upstream FactoryRecipeEvent, 23 lines, L11-U22), whose constructor passes
 * {@code recipeThread.getContext()} up to the recipe event.</p>
 *
 * <h2>What the payload is, and what it cannot be</h2>
 *
 * <p>The thread handle IS the payload difference from the four plain recipe events: a handler on a factory can
 * ask WHICH run fired, what it is running, how far it is, and with what parallelism -- none of which is
 * meaningful for a controller that has at most one run. It is available on all four subclasses through
 * {@link #getFactoryRecipeThread()}.</p>
 *
 * <p>The ticker supplies the exact context evaluated for this run, including its active modifiers and
 * catalysts. The compatibility constructor uses the last valid evaluation when available, otherwise a plain
 * recipe context. Scripts receive a registered read-only RecipeThread view; Java integrations retain access
 * to the actual FactoryRecipeThread through getFactoryRecipeThread().</p>
 *
 * <p><b>NO DIRECT FIRE SITE, AND THAT IS UPSTREAM'S SHAPE TOO:</b> upstream never posts a
 * {@code FactoryRecipeEvent} (a grep of the whole upstream tree finds construction only through the four
 * subclasses), and neither does this port. It exists so the four subclasses share one payload, and it is
 * instantiated on every one of their fires through {@code super(...)} -- which is what the self-test asserts
 * rather than assuming.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.FactoryRecipeEvent")
public class FactoryRecipeEvent extends RecipeEvent {

    protected final FactoryRecipeThread recipeThread;

    public FactoryRecipeEvent(FactoryRecipeThread recipeThread, TileController controller) {
        this(recipeThread, controller, recipeThread == null ? null
                : recipeThread.getRuntimeRecipeContext() != null ? recipeThread.getRuntimeRecipeContext()
                : recipeThread.getRecipe() == null ? null : new RecipeCraftingContext(recipeThread.getRecipe()));
    }

    public FactoryRecipeEvent(FactoryRecipeThread recipeThread, TileController controller, RecipeCraftingContext context) {
        super(controller, context, recipeThread == null ? null
                : new hellfirepvp.modularmachinery.common.machine.RecipeThread(controller, recipeThread));
        this.recipeThread = recipeThread;
    }

    /** @return the run this event is about; never {@code null} for the four events that are fired */
    public FactoryRecipeThread getFactoryRecipeThread() {
        return recipeThread;
    }

    /** Scripts receive a registered query type; Java integration callers retain the actual run getter. */
    @org.openzen.zencode.java.ZenCodeType.Getter("factoryRecipeThread")
    @org.openzen.zencode.java.ZenCodeType.Nullable
    public RecipeThread getFactoryRecipeThreadView() {
        return getRecipeThread();
    }
}
