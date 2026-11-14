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
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Fired when a recipe starts (upstream RecipeStartEvent, 15 lines).
 *
 * <p>UPSTREAM TAKES A RecipeThread AND PASSES ONLY ITS CONTEXT ON: <code>super(controller, thread,
 * thread.getContext())</code> at U12. THE THREAD IS A CONVEYOR, NOT A PAYLOAD. So the port signature takes the
 * context directly, which is a mechanical substitution rather than a design choice -- and the proof is upstream own
 * ResultChanceCreateEvent, which takes a RecipeCraftingContext outright because no thread is involved there
 * (PLAN 500.3).</p>
 *
 * <p>The class adds nothing beyond the constructor, and that is the correct port of a class that adds nothing beyond
 * the constructor.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.RecipeStartEvent")
public class RecipeStartEvent extends RecipeEvent {

    public RecipeStartEvent(TileController controller, RecipeCraftingContext context) {
        super(controller, context);
    }
}
