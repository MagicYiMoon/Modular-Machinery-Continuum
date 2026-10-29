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















 
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.FactoryRecipeStartEvent")
public class FactoryRecipeStartEvent extends FactoryRecipeEvent {

    public FactoryRecipeStartEvent(FactoryRecipeThread recipeThread, TileController controller) {
        super(recipeThread, controller);
    }

    public FactoryRecipeStartEvent(FactoryRecipeThread recipeThread, TileController controller,
                                  hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext context) {
        super(recipeThread, controller, context);
    }
}
