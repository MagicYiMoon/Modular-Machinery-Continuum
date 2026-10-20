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
 * Fired when ONE factory run completes (upstream FactoryRecipeFinishEvent, 14 lines).
 *
 * <p>FIRE SITE, and it is the only one: {@code FactoryTicker.tickThread}, AFTER the outputs have been placed
 * and before the thread is cleared to idle. Upstream's is {@code TileFactoryController.onThreadRecipeFinished}
 * (U304-U311), reached from {@code RecipeThread.onFinished} U82 -- also after {@code context.finishCrafting()},
 * so the handler sees a run whose products are already in the machine while the completed recipe/context is
 * still available. The port mirrors that ordering; the thread is cleared immediately after dispatch.</p>
 *
 * <p>ONE NOTIFICATION PER RUN, NOT PER RECIPE NAME: a factory running the same recipe on three threads fires it
 * three times, which is exactly what a handler counting completed crafts needs.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.FactoryRecipeFinishEvent")
public class FactoryRecipeFinishEvent extends FactoryRecipeEvent {

    public FactoryRecipeFinishEvent(FactoryRecipeThread recipeThread, TileController controller) {
        super(recipeThread, controller);
    }
}
