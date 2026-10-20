/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
 */
package hellfirepvp.modularmachinery.client.preview;

import net.minecraft.world.item.ItemStack;
import java.util.function.BiConsumer;

/** Optional client bridge: no JEI types are linked when JEI is absent. */
public final class BlueprintIngredientLookup {
    private static BiConsumer<ItemStack, Integer> lookup;
    private BlueprintIngredientLookup() { }
    public static void install(BiConsumer<ItemStack, Integer> handler) { lookup = handler; }
    public static boolean available() { return lookup != null; }
    public static boolean open(ItemStack stack, int button) {
        if (lookup == null || stack.isEmpty() || (button != 0 && button != 1)) return false;
        lookup.accept(stack.copyWithCount(1), button);
        return true;
    }
}
