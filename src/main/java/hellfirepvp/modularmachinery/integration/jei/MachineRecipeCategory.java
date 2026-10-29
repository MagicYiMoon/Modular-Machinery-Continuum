/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFuel;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Source-style horizontal inputs, process arrow and outputs, with bounded overflow for pack recipes. */
public class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {
    private static final int WIDTH = 180, MAX_HEIGHT = 134;
    private static final int INPUT_X = RecipeRequirementWidget.LEFT, FIRST_ROW = RecipeRequirementWidget.TOP;
    private static final int SLOT = RecipeRequirementWidget.SLOT;
    private final IDrawable icon, arrow;
    private final int height;

    public MachineRecipeCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(RegistryBlocks.blockController.get()));
        arrow = helper.getRecipeArrow();
        // JEI category dimensions are fixed at registration. Size from loaded recipes like upstream,
        // retaining room for three ingredient rows after a later /reload; excess details remain hoverable.
        var font = net.minecraft.client.Minecraft.getInstance().font;
        height = Math.min(MAX_HEIGHT, Math.max(94, JeiPluginModularMachinery.collectRecipes().stream()
                .mapToInt(recipe -> detailsY(recipe) + 10 * details(recipe).stream()
                        .mapToInt(line -> font.split(line, WIDTH - 8).size()).sum() + 2)
                .max().orElse(94)));
    }
    @Override public RecipeType<MachineRecipe> getRecipeType() { return JeiPluginModularMachinery.MACHINE_RECIPE; }
    @Override public Component getTitle() { return Component.translatable("jei.modularmachinery.machine_recipe"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return height; }

    private static int detailsY(MachineRecipe recipe) {
        return FIRST_ROW + RecipeRequirementWidget.visibleRows(countColumn(recipe)) * SLOT + 4;
    }
    private static List<Component> details(MachineRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        if (!recipe.displayName().isBlank()) lines.add(Component.literal(recipe.displayName()));
        if (recipe.recipeTime() > 0) lines.add(Component.literal("Time: " + recipe.recipeTime() + " t"));
        long input = 0, output = 0;
        for (var requirement : recipe.requirements()) {
            if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy energy) {
                if (energy.getIOType() == IOType.INPUT) input += energy.getEnergyPerTick();
                else output += energy.getEnergyPerTick();
            }
        }
        if (input > 0) lines.add(Component.literal("In: " + input + " FE/t"));
        if (output > 0) lines.add(Component.literal("Out: " + output + " FE/t"));
        for (var requirement : recipe.requirements())
            if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnvironment environment)
                lines.add(environment.tooltip());
        lines.addAll(TooltipMana.lines(recipe));
        if (net.minecraftforge.fml.ModList.get().isLoaded("embers"))
            for (var requirement : recipe.requirements())
                if (requirement instanceof hellfirepvp.modularmachinery.integration.embers.RequirementEmber ember)
                    lines.add(ember.tooltip());
        if (net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft"))
            for (var requirement : recipe.requirements())
                if (requirement instanceof hellfirepvp.modularmachinery.integration.pneumatic.RequirementCompressedAir air)
                    lines.add(air.tooltip());
        lines.addAll(TooltipBloodMagic.lines(recipe));
        if (net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft"))
            for (var requirement : recipe.requirements())
                if (requirement instanceof hellfirepvp.modularmachinery.integration.pneumatic.RequirementTemperature temperature)
                    lines.add(temperature.tooltip());
        lines.addAll(TooltipAura.lines(recipe));
        lines.addAll(TooltipAstralRainbow.lines(recipe));
        TooltipInterfaceNumberInput.lines(recipe).forEach(line -> lines.add(Component.literal(line)));
        recipe.formattedTooltips().forEach(line -> lines.add(hellfirepvp.modularmachinery.client.RecipeTooltipText.resolve(line)));
        return lines;
    }
    @Override public void draw(MachineRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                               net.minecraft.client.gui.GuiGraphics g, double mouseX, double mouseY) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        var machine = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader().findByName(recipe.machine());
        Component name = Component.translatableWithFallback("machine.modularmachinery." + recipe.machine(),
                machine == null ? recipe.machine() : machine.getLocalizedName());
        String label = font.plainSubstrByWidth(name.getString(), WIDTH - 8);
        g.drawString(font, label, (WIDTH - font.width(label)) / 2, 4, 0x404040, false);
        arrow.draw(g, (WIDTH - arrow.getWidth()) / 2, FIRST_ROW + (detailsY(recipe) - FIRST_ROW - arrow.getHeight()) / 2);
        int y = detailsY(recipe), available = (height - y) / 10;
        var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        details(recipe).forEach(line -> lines.addAll(font.split(line, WIDTH - 8)));
        boolean overflow = lines.size() > available;
        int shown = Math.min(lines.size(), Math.max(0, available - (overflow ? 1 : 0)));
        for (int i = 0; i < shown; i++, y += 10) g.drawString(font, lines.get(i), INPUT_X, y, 0x404040, false);
        if (overflow && available > 0) g.drawString(font,
                Component.translatable("jei.modularmachinery.more_recipe_tooltips"), INPUT_X, y, 0x808080, false);
    }
    private static mezz.jei.api.gui.builder.IRecipeSlotBuilder addSlot(IRecipeLayoutBuilder builder,
                                                                       RecipeIngredientRole role, int index) {
        int x = role == RecipeIngredientRole.INPUT ? RecipeRequirementWidget.LEFT : RecipeRequirementWidget.RIGHT;
        return builder.addSlot(role, x + index % RecipeRequirementWidget.COLUMNS * SLOT,
                FIRST_ROW + index / RecipeRequirementWidget.COLUMNS % RecipeRequirementWidget.ROWS * SLOT)
                .setStandardSlotBackground();
    }
    @Override public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder extras,
                                             MachineRecipe recipe, IFocusGroup focuses) {
        var slots = extras.getRecipeSlots().getSlots();
        var widget = new RecipeRequirementWidget(slots);
        extras.addSlottedWidget(widget, slots);
        extras.addGuiEventListener(widget);
    }
    @Override public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, MachineRecipe recipe,
                                      mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, double x, double y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= height) return;
        if (y < FIRST_ROW) {
            var machine = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader().findByName(recipe.machine());
            if (machine != null) tooltip.add(Component.translatableWithFallback(machine.getTranslationKey(), machine.getLocalizedName()));
        } else if (y >= detailsY(recipe)) {
            details(recipe).forEach(tooltip::add);
            tooltip.add(Component.literal("ID: " + recipe.registryName()));
        }
    }

    private static int countColumn(MachineRecipe recipe) {
        int inputs = 0;
        int outputs = 0;
        for (var original : recipe.requirements()) {
            var requirement = original instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick rate
                    ? rate.fluid() : original;
            if (requirement instanceof RequirementIngredientArray array) {
                if (array.getIOType() == IOType.INPUT) inputs++; else outputs++;
            } else if (requirement instanceof RequirementFuel) {
                inputs++;
            } else if (requirement instanceof RequirementItem item) {
                if (item.asStack().isEmpty()) {
                    continue;
                }
                if (item.getIOType() == IOType.INPUT) {
                    inputs++;
                } else {
                    outputs++;
                }
            } else if (requirement instanceof RequirementFluid fluid) {
                if (!fluid.isResolvable()) {
                    continue;
                }
                if (fluid.getIOType() == IOType.INPUT) {
                    inputs++;
                } else {
                    outputs++;
                }
            }
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            inputs += hellfirepvp.modularmachinery.integration.mekanism.GasJeiSupport.count(recipe, IOType.INPUT);
            outputs += hellfirepvp.modularmachinery.integration.mekanism.GasJeiSupport.count(recipe, IOType.OUTPUT);
        }
        return Math.max(inputs, outputs);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        var controllers = builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST);
        // Per-recipe catalysts keep a dedicated controller's JEI focus on its own machine.
        for (boolean factory : new boolean[] {false, true}) {
            var stack = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.stack(recipe.machine(), factory);
            if (!stack.isEmpty()) controllers.addItemStack(stack);
        }
        List<RequirementFuel> fuels = new ArrayList<>();
        List<RequirementIngredientArray> arrays = new ArrayList<>();
        List<RequirementIngredientArray> arrayOutputs = new ArrayList<>();
        List<RequirementItem> itemInputs = new ArrayList<>();
        List<RequirementItem> itemOutputs = new ArrayList<>();
        List<RequirementFluid> fluidInputs = new ArrayList<>();
        List<RequirementFluid> fluidOutputs = new ArrayList<>();

        for (var original : recipe.requirements()) {
            var requirement = original instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick rate
                    ? rate.fluid() : original;
            if (requirement instanceof RequirementFuel fuel) {
                fuels.add(fuel);
            } else if (requirement instanceof RequirementIngredientArray array) {
                (array.getIOType() == IOType.INPUT ? arrays : arrayOutputs).add(array);
            } else if (requirement instanceof RequirementItem item) {
                // An unresolvable requirement is skipped rather than shown as an empty slot: the loader
                // already reported it, and an empty slot would imply the recipe needs nothing there.
                if (item.asStack().isEmpty()) {
                    continue;
                }
                (item.getIOType() == IOType.INPUT ? itemInputs : itemOutputs).add(item);
            } else if (requirement instanceof RequirementFluid fluid) {
                if (!fluid.isResolvable()) {
                    continue;
                }
                (fluid.getIOType() == IOType.INPUT ? fluidInputs : fluidOutputs).add(fluid);
            }
        }

        // Group items, alternatives, fuel, fluids and gases in horizontal input/output grids.
        for (int i = 0; i < itemInputs.size(); i++) {
            RequirementItem item = itemInputs.get(i);
            addSlot(builder, RecipeIngredientRole.INPUT, i)
                    .addItemStack(item.asDisplayStack())
                    // The declared amount is surfaced as a tooltip because a slot shows a single stack
                    // icon: "32 coal" and "1 coal" would otherwise look identical.
                    .addRichTooltipCallback((view, tooltip) ->
                            {
                                tooltip.add(Component.literal("x" + item.getAmount()));
                        if (item.getSelectorTag() != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", item.getSelectorTag()));
                                if (item.getConsumeDurability() > 0) tooltip.add(Component.translatable(
                                        "jei.modularmachinery.durability_use", item.getConsumeDurability(), item.getAmount()));
                            });
        }
        for (int i = 0; i < arrays.size(); i++) {
            var array = arrays.get(i);
            int index = itemInputs.size() + i;
            List<ItemStack> alternatives = new ArrayList<>();
            for (var item : array.getIngredients()) {
                if (item.getTag() != null) {
                    var tags = net.minecraftforge.registries.ForgeRegistries.ITEMS.tags();
                    if (tags != null) tags.getTag(item.getTag()).forEach(candidate -> {
                        var stack = new ItemStack(candidate, item.getAmount());
                        stack.setTag(item.getNbt().display());
                        alternatives.add(stack);
                    });
                } else if (!item.asDisplayStack().isEmpty()) alternatives.add(item.asDisplayStack());
            }
            addSlot(builder, RecipeIngredientRole.INPUT, index)
                    .addItemStacks(alternatives)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable(array instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst
                                ? "jei.modularmachinery.catalyst" : "jei.modularmachinery.ingredient_array"));
                        if (array.getSelectorTag() != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", array.getSelectorTag()));
                        tooltip.add(Component.translatable("jei.modularmachinery.input_chance", Math.round(array.getChance() * 100)));
                        if (array instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst catalyst) {
                            if (!catalyst.getTooltips().isEmpty())
                                tooltip.add(Component.translatable("tooltip.machinery.catalyst.effect"));
                            catalyst.getTooltips().forEach(tip -> tooltip.add(Component.literal(tip)));
                        }
                    });
        }
        for (int i = 0; i < fuels.size(); i++) {
            var fuel = fuels.get(i);
            int index = itemInputs.size() + arrays.size() + i;
            List<ItemStack> choices = new ArrayList<>();
            for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                var stack = new ItemStack(item);
                stack.setTag(fuel.getNbt().display());
                int burn = RequirementFuel.burnTime(stack);
                if (burn <= 0) continue;
                stack.setCount((int) Math.max(1, ((long) fuel.getTime() + burn - 1) / burn));
                choices.add(stack);
            }
            addSlot(builder, RecipeIngredientRole.INPUT, index)
                    .addItemStacks(choices)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("jei.modularmachinery.fuel_time", fuel.getTime()));
                        if (fuel.getSelectorTag() != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", fuel.getSelectorTag()));
                        tooltip.add(Component.translatable("jei.modularmachinery.input_chance", Math.round(fuel.getChance() * 100)));
                    });
        }
        for (int i = 0; i < itemOutputs.size(); i++) {
            RequirementItem item = itemOutputs.get(i);
            addSlot(builder, RecipeIngredientRole.OUTPUT, i)
                    .addItemStack(item.asDisplayStack())
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.literal("x" + item.getAmount()));
                        if (item.getSelectorTag() != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", item.getSelectorTag()));
                        // A probabilistic output must say so, or JEI would promise something the machine
                        // only sometimes delivers.
                        if (!item.isGuaranteed()) {
                            tooltip.add(Component.literal(
                                    Math.round(item.getChance() * 100) + "% chance"));
                        }
                    });
        }
        for (int i = 0; i < arrayOutputs.size(); i++) {
            var array = arrayOutputs.get(i);
            int index = itemOutputs.size() + i;
            List<ItemStack> choices = new ArrayList<>();
            for (var candidate : array.getCandidates()) {
                var item = candidate.outputItem();
                ItemStack stack = item.asDisplayStack();
                if (!stack.isEmpty()) { stack.setCount(Math.max(1, candidate.maxAmount())); choices.add(stack); }
            }
            addSlot(builder, RecipeIngredientRole.OUTPUT, index)
                    .addItemStacks(choices)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("tooltip.machinery.ingredient_array_output"));
                        tooltip.add(Component.literal(Math.round(array.getChance() * 100) + "% chance; one candidate per batch"));
                        for (var candidate : array.getCandidates())
                            tooltip.add(Component.literal(candidate.item().getRawId() + " x" + candidate.minAmount() + ".." + candidate.maxAmount()
                                    + " (weight " + candidate.weight() + ")"));
                    });
        }
        for (int i = 0; i < fluidInputs.size(); i++) {
            RequirementFluid fluid = fluidInputs.get(i);
            addSlot(builder, RecipeIngredientRole.INPUT, i + itemInputs.size() + arrays.size() + fuels.size())
                    .setFluidRenderer(Math.max(1, fluid.getAmount()), false, 16, 16)
                    .addFluidStack(fluid.asDisplayStack().getFluid(), fluid.getAmount(), fluid.asDisplayStack().getTag())
                    .addRichTooltipCallback((view, tooltip) -> {
                        String selector = recipe.requirements().stream()
                                .filter(r -> r == fluid || r instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick rate && rate.fluid() == fluid)
                                .map(hellfirepvp.modularmachinery.common.crafting.Requirement::getSelectorTag)
                                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
                        if (selector != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", selector));
                        if (recipe.requirements().stream().anyMatch(r -> r instanceof
                                hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick rate
                                && rate.fluid() == fluid)) {
                            tooltip.add(Component.translatable("jei.modularmachinery.fluid_per_tick", fluid.getAmount()));
                        }
                    });
        }
        for (int i = 0; i < fluidOutputs.size(); i++) {
            RequirementFluid fluid = fluidOutputs.get(i);
            addSlot(builder, RecipeIngredientRole.OUTPUT, i + itemOutputs.size() + arrayOutputs.size())
                    .setFluidRenderer(Math.max(1, fluid.getAmount()), false, 16, 16)
                    .addFluidStack(fluid.asDisplayStack().getFluid(), fluid.getAmount(), fluid.asDisplayStack().getTag())
                    .addRichTooltipCallback((view, tooltip) -> {
                        String selector = recipe.requirements().stream()
                                .filter(r -> r == fluid || r instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick rate && rate.fluid() == fluid)
                                .map(hellfirepvp.modularmachinery.common.crafting.Requirement::getSelectorTag)
                                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
                        if (selector != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", selector));
                        if (recipe.requirements().stream().anyMatch(r -> r instanceof
                                hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick rate
                                && rate.fluid() == fluid)) {
                            tooltip.add(Component.translatable("jei.modularmachinery.fluid_per_tick", fluid.getAmount()));
                        }
                    });
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            hellfirepvp.modularmachinery.integration.mekanism.GasJeiSupport.addSlots(builder, recipe,
                    itemInputs.size() + arrays.size() + fuels.size() + fluidInputs.size(),
                    itemOutputs.size() + arrayOutputs.size() + fluidOutputs.size(),
                    index -> addSlot(builder, RecipeIngredientRole.INPUT, index),
                    index -> addSlot(builder, RecipeIngredientRole.OUTPUT, index));
        }
    }

}
