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
 * Fired when ONE factory run starts (upstream FactoryRecipeStartEvent, 14 lines).
 *
 * <p>FIRE SITE, and it is the only one: {@code FactoryTicker.startOneRecipe}, in the middle of starting a run
 * -- after the thread has a recipe and before any tick of it has run. Upstream's is
 * {@code TileFactoryController.onThreadRecipeStart} (U274-U284), reached from {@code offerRecipe} and from a
 * thread's own restart, which is the same position in the same flow. Upstream fires it BEFORE
 * {@code activeRecipe.start(thread.getContext())} (U275-U277); this port consumes the inputs as its own step
 * before {@code beginRecipe} rather than inside the start, so the handler here observes a run whose inputs are
 * already gone -- the port's own consumption model (PLAN 513/497) and the difference is recorded rather than
 * implied.</p>
 *
 * <p>NO FIELDS AND NO FLAGS, exactly as upstream has none: it is a notification, and a handler that wants to
 * change the run does it through the thread it is handed.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.FactoryRecipeStartEvent")
public class FactoryRecipeStartEvent extends FactoryRecipeEvent {

    public FactoryRecipeStartEvent(FactoryRecipeThread recipeThread, TileController controller) {
        super(recipeThread, controller);
    }
}
