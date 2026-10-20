/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE BlockArray descriptive materials.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.BiFunction;

/** A sampled scene counts one candidate per position, including preview-only upgrade positions. */
public final class BlueprintMaterials {
    private BlueprintMaterials() { }

    public static List<ItemStack> summarize(Map<BlockPos, BlockState> states,
                                            BiFunction<BlockPos, BlockState, ItemStack> resolver) {
        List<ItemStack> result = new ArrayList<>();
        states.forEach((pos, state) -> {
            ItemStack stack = resolver.apply(pos, state);
            if (stack.isEmpty()) return;
            ItemStack existing = result.stream().filter(s -> ItemStack.isSameItemSameTags(s, stack))
                    .findFirst().orElse(null);
            if (existing != null) existing.grow(1);
            else result.add(stack.copyWithCount(1));
        });
        // Stable ties preserve the pattern's order. Display counts may exceed the item's stack limit.
        result.sort(Comparator.<ItemStack, Boolean>comparing(BlueprintMaterials::isController).reversed()
                .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()));
        return List.copyOf(result);
    }

    private static boolean isController(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem item
                && item.getBlock() instanceof hellfirepvp.modularmachinery.common.block.BlockController;
    }
}
