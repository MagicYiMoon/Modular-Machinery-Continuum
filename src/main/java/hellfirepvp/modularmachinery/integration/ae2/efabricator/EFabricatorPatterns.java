/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import appeng.api.stacks.*;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.menu.AutoCraftingMenu;
import hellfirepvp.modularmachinery.common.efabricator.EFabricatorQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** AE2 15's native crafting-grid and remainder semantics; never mutate a rejected input holder. */
public final class EFabricatorPatterns {
    private EFabricatorPatterns() { }
    public static boolean encoded(net.minecraft.world.item.ItemStack stack) {
        return appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(stack);
    }
    public static hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Description describe(ItemStack stack, net.minecraft.world.level.Level world) {
        var invalid = hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Description.invalid();
        try {
            var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, world, false);
            if (details == null) return invalid;
            var inputs = new ArrayList<hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Ingredient>();
            var outputs = new ArrayList<hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Ingredient>();
            for (var input : details.getInputs()) for (var possible : input.getPossibleInputs()) addIngredient(inputs, possible.what());
            for (var output : details.getOutputs()) addIngredient(outputs, output.what());
            ItemStack icon = ItemStack.EMPTY;
            if (details.getPrimaryOutput().what() instanceof AEItemKey item) icon = new ItemStack(item.getItem());
            return new hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Description(icon, inputs, outputs, details instanceof AECraftingPattern);
        } catch (RuntimeException ignored) { return invalid; }
    }
    private static void addIngredient(List<hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Ingredient> values, AEKey key) {
        var id = key instanceof AEItemKey item ? net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem())
                : key instanceof AEFluidKey fluid ? net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid.getFluid()) : null;
        if (id != null && values.size() < 16 && values.stream().noneMatch(v -> v.id().equals(id)))
            values.add(new hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.Ingredient(id, key.getDisplayName()));
    }
    static Prepared prepare(AECraftingPattern pattern, KeyCounter[] input, ServerLevel world) {
        if (input == null || input.length != pattern.getInputs().length || input.length == 0) return null;
        var originals = new KeyCounter[input.length]; var working = new KeyCounter[input.length];
        var seen = Collections.newSetFromMap(new IdentityHashMap<KeyCounter, Boolean>());
        for (int i = 0; i < input.length; i++) {
            if (input[i] == null || !seen.add(input[i])) return null;
            var rule = pattern.getInputs()[i]; long units = 0;
            for (var entry : input[i]) {
                long amount = entry.getLongValue();
                if (amount < 0) return null;
                if (amount == 0) continue;
                if (!rule.isValid(entry.getKey(), world)) return null;
                long perSlot = 0;
                if (entry.getKey() instanceof AEItemKey) perSlot = 1;
                else if (entry.getKey() instanceof AEFluidKey) for (var candidate : rule.getPossibleInputs()) {
                    if (candidate.what().equals(entry.getKey())) { perSlot = candidate.amount(); break; }
                }
                if (perSlot <= 0 || amount % perSlot != 0) return null;
                units = Math.addExact(units, amount / perSlot);
            }
            if (units != rule.getMultiplier()) return null;
            originals[i] = new KeyCounter(); originals[i].addAll(input[i]);
            working[i] = new KeyCounter(); working[i].addAll(input[i]);
        }
        var grid = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        pattern.fillCraftingGrid(working, (slot, stack) -> grid.setItem(slot, stack.copy()));
        for (var rest : working) { rest.removeZeros(); if (!rest.isEmpty()) return null; }
        var output = pattern.assemble(grid, world).copy();
        if (output.isEmpty()) return null;
        var expected = pattern.getOutputs();
        if (expected.length != 1 || !Objects.equals(expected[0].what(), AEItemKey.of(output)) || expected[0].amount() != output.getCount()) return null;
        // Native AE fluid substitution removes the empty bucket only when no bucket was consumed.
        var remaining = pattern.getRemainingItems(grid);
        if (remaining.size() != 9) return null;
        return new Prepared(new EFabricatorQueue.Work(1, output, remaining), originals);
    }
    record Prepared(EFabricatorQueue.Work work, KeyCounter[] originals) {
        boolean unchanged(KeyCounter[] current) {
            if (current == null || current.length != originals.length) return false;
            for (int i = 0; i < current.length; i++) {
                if (current[i] == null || !current[i].keySet().equals(originals[i].keySet())) return false;
                for (var entry : originals[i]) if (current[i].get(entry.getKey()) != entry.getLongValue()) return false;
            }
            return true;
        }
        void consume(KeyCounter[] current) { for (var counter : current) counter.clear(); }
    }
}
