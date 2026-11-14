/* SPDX-License-Identifier: GPL-3.0-only; actual AE patterns for opt-in regression/client fixtures. */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import java.util.*;

public final class EFabricatorMenuPatterns {
    private EFabricatorMenuPatterns() { }
    public static List<ItemStack> create(ServerLevel world) {
        var recipe = (CraftingRecipe)world.getRecipeManager().byKey(new ResourceLocation("minecraft:oak_planks")).orElseThrow();
        var in = new ItemStack[9]; Arrays.fill(in, ItemStack.EMPTY); in[0] = new ItemStack(Items.OAK_LOG);
        var crafting = PatternDetailsHelper.encodeCraftingPattern(recipe, in, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var processing = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)});
        return List.of(crafting, processing);
    }
}
