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

/**
 * The JEI category that draws one machine recipe.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream registered a {@code ModPlugin} subclass that produced JEI
 * {@code IRecipeWrapper}s. In JEI 15.x the wrapper is gone: recipes are wrapped in a
 * {@link RecipeType} and the category lays them out itself through an {@link IRecipeLayoutBuilder}. The
 * mod's own {@link MachineRecipe} is used directly as the recipe object, which is what keeps this
 * category honest — it reads the same requirement objects the machine executes.
 *
 * <h2>Layout</h2>
 *
 * <p>Items first (inputs left, outputs right), then fluids, then a text line for energy and duration.
 * Requirement order within a recipe is whatever the JSON declared, so an item output may precede an item
 * input; the layout sorts by role rather than trusting declaration order, otherwise a pack that wrote its
 * outputs first would render a mirrored recipe.
 *
 * <p>Nothing is drawn for a requirement that resolves to no ingredient. A requirement whose id failed to
 * resolve is reported by the loader, and showing an empty slot in JEI would suggest the recipe needs
 * nothing there.
 */
public class MachineRecipeCategory implements IRecipeCategory<MachineRecipe> {

    private static final int WIDTH = 160;

    private static final int SLOT = 18;
    private static final int INPUT_X = 4;
    /**
     * The y of the first slot ROW (PLAN section 300).
     *
     * <p>It is 16 rather than 4 so that the machine name has a row of its own at the top: at 4 the name and the
     * first slot occupied the same pixels, which the client reported as "重叠了".
     */
    private static final int FIRST_ROW = 16;
    private static final int OUTPUT_X = WIDTH - 4 - SLOT;

    /**
     * How many slot ROWS one column may hold before the next column starts (PLAN section 307).
     *
     * <p>The tallest shipped recipe has NINE outputs. As one column that needs 178 pixels of slot, and the panel
     * it produced was 208 tall -- at GUI scale 4 that is 832 screen pixels and does not fit a 1080p window. The
     * client saw exactly that: the name at the top, the rate lines pushed off the bottom.
     *
     * <p>Six is the largest budget that keeps the whole panel on screen at scale 4, and nine slots fold into it
     * as 6 + 3.
     */
    private static final int MAX_ROWS = 6;

    /**
     * One name row, MAX_ROWS of slots, and the text lines below them.
     *
     * <p><b>THE BUDGET GREW BY TWO LINES IN PLAN 663, AND THAT IS §307's LESSON APPLIED RATHER THAN
     * REDISCOVERED.</b> §307 is the round the client reported "the name at the top, the rate lines pushed off
     * the bottom", because the panel was sized for the slots and the text was drawn past its edge — a panel
     * that clips its own text is indistinguishable from a panel that failed. Three lines (energy in, energy
     * out, duration) already fit in 30; a smart interface requirement adds up to TWO more (min and max), so
     * the reserve is 50. A recipe with neither pays four empty lines of panel, which is the cheap direction to
     * be wrong in.</p>
     */
    // The existing reserve fits the two compact Blood Magic rows; full details are hover text.
    private static final int HEIGHT = FIRST_ROW + MAX_ROWS * SLOT + 140;

    private final IDrawable icon;
    /** The panel height, from the constants above: it must fit the folded layout, not the data. */
    private final int height = HEIGHT;

    public MachineRecipeCategory(IGuiHelper guiHelper) {
        // The controller is the icon, matching the catalyst: recognising the category by the block that
        // runs it is how a player finds it in the recipe list.
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(RegistryBlocks.blockController.get()));

    }
    @Override
    public RecipeType<MachineRecipe> getRecipeType() {
        return JeiPluginModularMachinery.MACHINE_RECIPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.modularmachinery.machine_recipe");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return height;
    }

    /**
     * Draws what has no SLOT: the energy rates and the recipe time (PLAN section 298).
     *
     * <p>The client found a completely empty page in this category. It is not a layout defect and not a
     * data defect: {@code power_transformer_energy_transform} declares two ENERGY requirements and no
     * items at all, because a transformer converts FE and has nothing to put in a slot. A category that
     * can only show items therefore renders that recipe as a blank panel -- which reads as a bug, and
     * correctly so, because a page with no content is indistinguishable from a page that failed.
     *
     * <p><b>Text rather than an energy ingredient.</b> JEI has no built-in FE ingredient, and inventing one
     * would need a whole {@code IIngredientType} plus a renderer plus a helper, for a number that fits on
     * one line. Every energy recipe in this mod has at most one input rate and one output rate, so a line
     * each is the whole of the information.
     */
    @Override
    public void draw(MachineRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     net.minecraft.client.gui.GuiGraphics gfx, double mouseX, double mouseY) {
        int inRate = 0;
        int outRate = 0;
        for (var requirement : recipe.requirements()) {
            if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy energy) {
                if (energy.getIOType() == hellfirepvp.modularmachinery.common.machine.IOType.INPUT) {
                    inRate += energy.getEnergyPerTick();
                } else {
                    outRate += energy.getEnergyPerTick();
                }
            }
        }
        // THE MACHINE FIRST (PLAN section 299).
        //
        // The client pointed out that the page said what the recipe CONSUMES and not what RUNS it, and with
        // nine pages drawn from three machines -- transformer 1, alloy_furnace 3, iron_centrifuge 6 -- there
        // was no way to tell which controller a page belonged to. In a mod whose whole point is assembling a
        // structure to run a recipe, the machine is the first thing a reader needs.
        //
        // Resolved through the same loader the machines themselves run from, so a name that the port would
        // refuse to bind is a name JEI would also fail to show. Falls back to the raw registryname rather
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // than showing nothing: an unresolved name is still more useful than a blank line.
        hellfirepvp.modularmachinery.common.machine.DynamicMachine owning =
                hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                        .findByName(recipe.machine());
        // CENTRED ON ITS OWN ROW (PLAN section 300).
        //
        // It was left-aligned at INPUT_X, 4 -- the same pixels as the first input slot, which the client
        // reported as "重叠了". Centring it is not only cosmetic: a machine name is a label for the WHOLE
        // page, not a column heading for the inputs below it, and a label that reads as a heading is a
        // label in the wrong place.
        // TRANSLATABLE, WITH THE DATAPACK NAME AS THE FALLBACK (PLAN section 301).
        //
        // Upstream has no translation layer for machine names: the localizedname field in the JSON IS the
        // display string, and the jar zh_CN.lang carries no keys for them. So the port adds one -- a lang
        // key per machine, resolved through Component so a resource pack can override it.
        //
        // The datapack name stays as the FALLBACK rather than being replaced: a machine added by a player
        // has no lang key, and showing its own name beats showing a raw key.
        String machineKey = "machine." + hellfirepvp.modularmachinery.Modularmachinery.MODID + "." + recipe.machine();
        String machineLabel = net.minecraft.network.chat.Component.translatable(machineKey).getString();
        if (machineLabel.equals(machineKey) && owning != null) {
            machineLabel = owning.getLocalizedName();
        }
        // 0x404040 with NO shadow: the recipe area is light grey, and black-with-shadow read as a smudge
        // around the glyphs at GUI scale 4. This is the value JEI uses for its own text.
        // NO SHADOW, and that is the whole point: drawCenteredString(Font,String,int,int,int) ALWAYS draws a
        // shadow, and the shadow is what the client described as "糊" -- a dark offset copy behind every glyph at
        // GUI scale 4. Centring is therefore computed here and drawString is called with dropShadow=false.
        int labelWidth = mc.font.width(machineLabel);
        gfx.drawString(mc.font, machineLabel, (WIDTH - labelWidth) / 2, 5, 0x404040, false);
        // BELOW THIS RECIPE'S SLOTS, not at the panel bottom (PLAN section 307).
        //
        // The panel is sized for the WORST recipe in the set, because getHeight() takes no recipe. Drawing
        // the rates at height - 30 therefore pushed them off the bottom for every SMALL recipe -- which is
        // what the client saw when a transformer page (no slots at all) lost its three rate lines.
        //
        // Counting the rows here keeps the text attached to the slots it describes.
        int usedRows = Math.min(MAX_ROWS, Math.max(1, countColumn(recipe)));
        int y = FIRST_ROW + usedRows * SLOT + 2;
        if (!recipe.displayName().isBlank()) {
            String label = recipe.displayName();
            int availableWidth = WIDTH - INPUT_X * 2;
            if (mc.font.width(label) > availableWidth)
                label = mc.font.plainSubstrByWidth(label, availableWidth - mc.font.width("...")) + "...";
            gfx.drawString(mc.font, label, INPUT_X, y, 0x404040, false);
            y += 10;
        }
        // Only drawn when there IS energy: a page that says "0 FE/t" would be noise on every item recipe.
        if (inRate > 0) {
            gfx.drawString(mc.font, "In: " + inRate + " FE/t", INPUT_X, y, 0x404040, false);
            y += 10;
        }
        if (outRate > 0) {
            gfx.drawString(mc.font, "Out: " + outRate + " FE/t", INPUT_X, y, 0x404040, false);
            y += 10;
        }
        for (Component line : TooltipMana.lines(recipe)) {
            gfx.drawString(mc.font, line, INPUT_X, y, 0x404040, false); y += 10;
        }
        var bloodLines = new ArrayList<>(TooltipBloodMagic.lines(recipe));
        bloodLines.addAll(TooltipAura.lines(recipe));
        bloodLines.addAll(TooltipAstralRainbow.lines(recipe));
        for (int i = 0; i < Math.min(2, bloodLines.size()); i++) {
            Component line = i == 1 && bloodLines.size() > 2
                    ? Component.translatable("jei.modularmachinery.magic.more", bloodLines.size() - 1) : bloodLines.get(i);
            gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line.getString(), WIDTH - INPUT_X * 2), INPUT_X, y, 0x404040, false); y += 10;
        }
        if (recipe.recipeTime() > 0) {
            gfx.drawString(mc.font, "Time: " + recipe.recipeTime() + " t", INPUT_X, y, 0x808080, false);
            y += 10;
        }
        // THE SMART INTERFACE LINES (PLAN 663, B4).
        //
        // An interface requirement has NO SLOT — upstream's own provideJEIComponent() returns null — so this
        // text is the whole of its JEI presence, which is why TooltipInterfaceNumberInput exists and why it is
        // called here rather than from a slot's rich-tooltip callback (a callback attached to a slot that does
        // not exist never runs).
        for (String line : TooltipInterfaceNumberInput.lines(recipe)) {
            gfx.drawString(mc.font, line, INPUT_X, y, 0x404040, false);
            y += 10;
        }
        // Source RecipePrimer.addRecipeTooltip() appends recipe-level lines after the requirement details.
        // They are ordinary translation keys when a language entry exists, and literal text otherwise.
        // Keep them in the same draw pass so the JEI page and the machine use the exact same recipe object.
        var tooltipLines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line : recipe.formattedTooltips()) {
            var wrapped = mc.font.split(hellfirepvp.modularmachinery.client.RecipeTooltipText.resolve(line), WIDTH - INPUT_X * 2);
            if (wrapped.isEmpty()) tooltipLines.add(net.minecraft.util.FormattedCharSequence.EMPTY); else tooltipLines.addAll(wrapped);
        }
        int remainingRows = Math.max(0, (height - y) / 10);
        boolean clipped = tooltipLines.size() > remainingRows;
        int drawn = Math.min(tooltipLines.size(), Math.max(0, remainingRows - (clipped ? 1 : 0)));
        for (int i = 0; i < drawn; i++) {
            gfx.drawString(mc.font, tooltipLines.get(i), INPUT_X, y, 0x404040, false); y += 10;
        }
        if (clipped && remainingRows > 0) {
            gfx.drawString(mc.font, Component.translatable("jei.modularmachinery.more_recipe_tooltips"), INPUT_X, y, 0x808080, false);
        }
    }

    /**
     * The taller of this recipe's two slot columns, capped at {@link #MAX_ROWS}.
     *
     * <p>Deliberately the same predicate setRecipe uses -- resolvable items and fluids, split by direction --
     * so the text and the slots cannot disagree about how many rows there are.
     */
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

        // Items on the first row, fluids on the second, so the two kinds never share a slot position and
        // a player can tell at a glance which is which.
        for (int i = 0; i < itemInputs.size(); i++) {
            RequirementItem item = itemInputs.get(i);
builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + (i / MAX_ROWS) * SLOT,
                        FIRST_ROW + (i % MAX_ROWS) * SLOT)
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
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + (index / MAX_ROWS) * SLOT,
                            FIRST_ROW + (index % MAX_ROWS) * SLOT)
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
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + (index / MAX_ROWS) * SLOT,
                            FIRST_ROW + (index % MAX_ROWS) * SLOT)
                    .addItemStacks(choices)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("jei.modularmachinery.fuel_time", fuel.getTime()));
                        if (fuel.getSelectorTag() != null) tooltip.add(Component.translatable("jei.modularmachinery.selector", fuel.getSelectorTag()));
                        tooltip.add(Component.translatable("jei.modularmachinery.input_chance", Math.round(fuel.getChance() * 100)));
                    });
        }
        for (int i = 0; i < itemOutputs.size(); i++) {
            RequirementItem item = itemOutputs.get(i);
builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X - (i / MAX_ROWS) * SLOT,
                        FIRST_ROW + (i % MAX_ROWS) * SLOT)
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
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X - (index / MAX_ROWS) * SLOT,
                            FIRST_ROW + (index % MAX_ROWS) * SLOT)
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
builder.addSlot(RecipeIngredientRole.INPUT,
                        INPUT_X + ((i + itemInputs.size() + arrays.size() + fuels.size()) / MAX_ROWS) * SLOT,
                        FIRST_ROW + ((i + itemInputs.size() + arrays.size() + fuels.size()) % MAX_ROWS) * SLOT)
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
builder.addSlot(RecipeIngredientRole.OUTPUT,
                    OUTPUT_X - ((i + itemOutputs.size() + arrayOutputs.size()) / MAX_ROWS) * SLOT,
                        FIRST_ROW + ((i + itemOutputs.size() + arrayOutputs.size()) % MAX_ROWS) * SLOT)
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
                    itemOutputs.size() + arrayOutputs.size() + fluidOutputs.size(), INPUT_X, OUTPUT_X, FIRST_ROW, SLOT, MAX_ROWS);
        }
    }

    @Override
    public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, MachineRecipe recipe,
                           mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, double mouseX, double mouseY) {
        int y = FIRST_ROW + Math.min(MAX_ROWS, Math.max(1, countColumn(recipe))) * SLOT + 2;
        if (!recipe.displayName().isBlank() && mouseX >= INPUT_X && mouseX < WIDTH - INPUT_X
                && mouseY >= y && mouseY < y + 10) {
            tooltip.add(Component.literal(recipe.displayName()));
            tooltip.add(Component.literal("ID: " + recipe.registryName()));
            return;
        }
        if (mouseX >= INPUT_X && mouseX < WIDTH - INPUT_X && mouseY >= y && mouseY < height) {
            TooltipBloodMagic.lines(recipe).forEach(tooltip::add);
            TooltipAura.lines(recipe).forEach(tooltip::add);
            TooltipAstralRainbow.lines(recipe).forEach(tooltip::add);
            recipe.formattedTooltips().forEach(line -> tooltip.add(hellfirepvp.modularmachinery.client.RecipeTooltipText.resolve(line)));
        }
    }
}
