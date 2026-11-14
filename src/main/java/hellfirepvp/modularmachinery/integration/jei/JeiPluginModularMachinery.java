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

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.screen.ScreenMEItemInputBus;
import hellfirepvp.modularmachinery.client.screen.ScreenBlueprint;
import hellfirepvp.modularmachinery.client.screen.ScreenMachineEditor;
import hellfirepvp.modularmachinery.client.preview.BlueprintIngredientLookup;
import hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration: makes the mod's JSON machine recipes browsable in JEI, and keeps them equal to
 * what the engine is running after a {@code /reload}.
 *
 * <h2>Soft dependency, and how that is enforced</h2>
 *
 * <p>JEI is a {@code compileOnly} dependency. This class is loaded <b>only by JEI itself</b> — through
 * the {@link JeiPlugin} annotation, which JEI scans for — so with JEI absent the class is never touched
 * and the mod loads normally. That is why no {@code DeferredRegister}, no event subscriber and no code
 * reachable from {@code @Mod} may reference anything in this package: a single such reference would turn
 * a soft dependency into a hard crash on startup for every player without JEI.
 *
 * <p>This is also why the classes live in their own {@code integration.jei} package rather than beside
 * the recipe code. The package boundary is the enforcement: a reference from {@code common.*} to
 * {@code integration.jei.*} is visible in review, whereas a reference buried among unrelated imports in
 * an existing class is not.
 *
 * <h2>Where the recipes come from</h2>
 *
 * <p>Straight from {@code MachineReloadListener} — <b>the same registry the machines themselves run
 * from</b>. JEI must never re-parse the JSON: a second parse would be a second source of truth, and a
 * recipe shown in JEI that the machine would refuse to run is worse than no JEI entry at all. Reading
 * the same objects makes the two impossible to disagree. {@link #collectRecipes()} read a second time is
 * the whole of the reload below.
 *
 * <h2>The reload path, and why it hides rather than only adds</h2>
 *
 * <p><b>RULE A0 — how upstream did the same job, and the three places this deviates.</b> Upstream's
 * {@code ModIntegrationJEI.reloadRecipeWrappers()} (U105-U121) is driven by
 * {@code ModIntegrationCrafttweaker.onScriptsReloaded} (U127) in the script-reload POST phase. Its entries
 * were <b>mutable wrapper objects held in a map</b> ({@code MACHINE_RECIPE_WRAPPERS}), so the reload could
 * hand each wrapper the new recipe ({@code wrapper.reloadWrapper(recipe)}, and
 * {@code CategoryDynamicRecipe.reloadCategory} U? re-measured the layout the same way), and what JEI was
 * already showing changed <b>in place</b> — upstream needed no removal, because nothing was ever replaced.
 * The deviations:
 *
 * <ol>
 *   <li><b>Replace rather than mutate.</b> A ported recipe is an immutable {@link MachineRecipe} record, so
 *       a reload produces new objects and JEI holds the old ones. The entry has to be replaced, and the old
 *       one has to stop being shown.</li>
 *   <li><b>One category, not one per machine.</b> Upstream rebuilt a category per machine ({@code reloadCategory}
 *       above); this port has a single merged category (PLAN 306's reasoning) whose layout is computed per
 *       entry in {@code setRecipe}/ {@code draw}, so there is no cached layout to re-measure — the new
 *       objects are laid out by the same code the old ones used.</li>
 *   <li><b>Hidden, not removed.</b> See the paragraph after this list: JEI 15.62 cannot drop an entry.</li>
 * </ol>
 *
 * <p>"Stop being shown" is the hard half, and the API decided its shape. {@code IRecipeManager.hideRecipes}
 * in JEI 15.62 does <b>not</b> remove a recipe from JEI's index — read from the bytecode, it is
 * {@code RecipeTypeData.getHiddenRecipes().addAll(recipes)} (and {@code unhideRecipes} the matching
 * {@code removeAll}), with {@code isRecipeVisible} consulting that same set. So nothing can be taken back
 * out of JEI; what can be done is to make an old entry <b>invisible</b>, which is what a player sees.
 * {@link #refreshRecipes()} therefore hides the set last handed to JEI and then adds the set the engine is
 * running now. {@code MachineRecipe} being a record is what makes that exact: {@code hideRecipes} resolves
 * its argument through {@code Set.contains} against records whose equality is <b>structural</b>, so an entry
 * whose contents changed is matched and hidden even though the new object is a different instance, while the
 * live set added in the same pass is not in the hidden set and stays visible.
 */
@JeiPlugin
public class JeiPluginModularMachinery implements IModPlugin {

    /** The plugin's own id. Distinct from the mod id by JEI's convention. */
    public static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(Modularmachinery.MODID, "jei_plugin");

    /**
     * The recipe type for machine recipes.
     *
     * <p>One type covers every machine rather than one per machine: a pack can define arbitrarily many
     * machines from JSON, and generating a JEI category per machine would mean an unbounded number of
     * categories. The machine a recipe belongs to is shown inside the entry and used as its catalyst,
     * which is how a player narrows down to one machine anyway.
     */
    public static final RecipeType<MachineRecipe> MACHINE_RECIPE =
            RecipeType.create(Modularmachinery.MODID, "machine_recipe", MachineRecipe.class);

    /** The source mod's separate JEI structure-preview category. */
    public static final RecipeType<StructurePreviewEntry> STRUCTURE_PREVIEW = StructurePreviewCategory.TYPE;

    /**
     * JEI's recipe index, kept from {@link #onRuntimeAvailable} — the only moment JEI hands it over.
     *
     * <p>Null means "there is no JEI index in this process to repair": JEI is not installed, or has not
     * finished starting. A reload arriving in that state is dropped rather than queued, which is correct for
     * the only case that reaches it — at server start {@code load()} runs before JEI's runtime exists, and
     * JEI then reads the same registry itself through {@code registerRecipes}.
     */
    private static IRecipeManager recipeManager;
    private static IJeiRuntime runtime;
    static IJeiRuntime currentRuntime() { return runtime; }

    /**
     * The last set handed to JEI, so that a <b>removed</b> recipe can be found again.
     *
     * <p>A recipe a reload deleted has no counterpart in the new set, so nothing in the new set can name it
     * and the only handle JEI accepts is the entry it already holds. This list is that handle. It is never
     * used to hide anything that is still live: entry equality is structural, and the hide and the add run
     * as one sequence in {@link #refreshRecipes()}.
     */
    private static List<MachineRecipe> lastAdded = List.of();
    private static List<StructurePreviewEntry> lastPreviews = List.of();

    /** Bumped once per observed reload; see {@link #pendingGeneration}. */
    private static long lastRequestedGeneration;

    /**
     * The generation of the request the client thread has yet to run, or has already run.
     *
     * <p>JEI's index may only be touched from the client thread: {@code IRecipeManager.addRecipes} and
     * {@code hideRecipes} both call {@code ErrorUtil.assertMainThread()} (read from the bytecode of
     * {@code mezz.jei.library.recipes.RecipeManager}). An integrated server reloads on <b>its own</b>
     * thread, so the refresh cannot run where the reload is observed; it is queued onto the client thread,
     * and this counter says which request is current, so a queued request that a later reload has already
     * superseded is dropped instead of run out of order.
     */
    private static long pendingGeneration;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new StructurePreviewCategory(guiHelper));
        registration.addRecipeCategories(new MachineRecipeCategory(guiHelper));
    }

    /**
     * JEI's start-up read. Adds the entries, and records them in {@link #lastAdded} so that the first reload
     * can hide them.
     *
     * <p>Not a second registration path: the set it adds comes from {@link #collectRecipes()} and goes in
     * through {@link #addToJei}, which is the same pair {@link #refreshRecipes()} uses. The only thing that
     * differs after start-up is that something is already there to hide.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        lastPreviews.forEach(StructurePreviewEntry::close);
        lastPreviews = collectPreviews();
        registration.addRecipes(STRUCTURE_PREVIEW, lastPreviews);
        lastAdded = addToJei(registration, collectRecipes());
    }

    /** Restores the source GUI's JEI drag-and-drop configuration for the sixteen item-input cells. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(ScreenBlueprint.class, new BlueprintScreenHandler());
        registration.addGhostIngredientHandler(ScreenMEItemInputBus.class,
                new IGhostIngredientHandler<ScreenMEItemInputBus>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(ScreenMEItemInputBus screen,
                                                               ITypedIngredient<I> ingredient,
                                                               boolean doStart) {
                        ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                        if (stack.isEmpty()) {
                            return List.of();
                        }
                        List<Target<I>> targets = new ArrayList<>();
                        for (var slot : screen.getMenu().slots) {
                            if (!(slot instanceof MenuMEItemInputBus.ConfigSlot config)) {
                                continue;
                            }
                            int x = screen.getGuiLeft() + slot.x;
                            int y = screen.getGuiTop() + slot.y;
                            targets.add(new Target<I>() {
                                @Override
                                public Rect2i getArea() {
                                    return new Rect2i(x, y, 16, 16);
                                }

                                @Override
                                public void accept(I ignored) {
                                    ModularmachineryNetwork.PktMEItemInputBusGhost.send(
                                            screen.getMenu().getBusPos(), config.getGridIndex(), stack);
                                }
                            });
                        }
                        return targets;
                    }

                    @Override
                    public void onComplete() {
                    }
                });
        registration.addGhostIngredientHandler(ScreenMachineEditor.class,
                new IGhostIngredientHandler<ScreenMachineEditor>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(ScreenMachineEditor screen,
                                                               ITypedIngredient<I> ingredient,
                                                               boolean doStart) {
                        ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                        if (stack.isEmpty()) return List.of();
                        List<Target<I>> targets = new ArrayList<>();
                        for (int i = 0; i < screen.requirementCount(); i++) {
                            int index = i;
                            targets.add(new Target<I>() {
                                @Override public Rect2i getArea() {
                                    return new Rect2i(screen.requirementTargetX(),
                                            screen.requirementTargetY(index), 48, 20);
                                }
                                @Override public void accept(I ignored) {
                                    screen.ghostItem(index, stack);
                                }
                            });
                        }
                        return targets;
                    }
                    @Override public void onComplete() { }
                });
    }

    /**
     * JEI's runtime hand-over: the one place a reload can work, because adding and hiding are runtime
     * operations on the client.
     *
     * <p>Signature read with {@code javap mezz.jei.api.IModPlugin} from jei-1.20.1-forge-15.62.0.216.jar
     * ({@code public default void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime)}), not recalled.
     * {@code JeiStarter.start} calls it after {@code registerRecipes} and after the {@code JeiRuntime} is
     * completed, and the same method gets the runtime JEI stores in {@code Internal} — so the manager taken
     * here is the live one.
     *
     * <p>The entries added by registerRecipes belong to this runtime and must be retained until
     * refreshRecipes hides them. Resetting this list here would lose the first revision after a reload.
     * onRuntimeUnavailable releases references to the previous runtime.
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        BlueprintIngredientLookup.install((stack, button) -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            var parent = client.screen;
            // Queue until the mouse event finishes, so JEI retains this blueprint as its parent screen.
            client.tell(() -> {
                if (runtime != jeiRuntime || client.screen != parent || !(parent instanceof ScreenBlueprint)) return;
                var factory = jeiRuntime.getJeiHelpers().getFocusFactory();
                var role = button == 0 ? mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT
                        : mezz.jei.api.recipe.RecipeIngredientRole.INPUT;
                var focus = factory.createFocus(role, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack);
                if (button == 0) jeiRuntime.getRecipesGui().show(focus);
                else jeiRuntime.getRecipesGui().show(List.of(focus, factory.createFocus(
                        mezz.jei.api.recipe.RecipeIngredientRole.CATALYST, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack)));
            });
        });
        recipeManager = jeiRuntime.getRecipeManager();
        refreshRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        BlueprintIngredientLookup.install(null);
        runtime = null;
        recipeManager = null;
        lastAdded = List.of();
        lastPreviews.forEach(StructurePreviewEntry::close);
        lastPreviews = List.of();
        pendingGeneration = ++lastRequestedGeneration;
    }

    /**
     * Re-shows exactly what the engine is running now.
     *
     * <p>Reached from both ends of the reload path — {@link #requestReload()} when the reload is observed,
     * and {@link #onRuntimeAvailable} when JEI hands over a new index — so there is one description of what
     * JEI should display and no second registration to keep in step with it.
     */
    private static List<StructurePreviewEntry> collectPreviews() {
        return MachineReloadListener.getLoader().all().stream()
                .filter(machine -> machine.getPattern() != null)
                .map(StructurePreviewEntry::new).toList();
    }

    private static void refreshRecipes() {
        IRecipeManager manager = recipeManager;
        if (manager == null) {
            return;   // no JEI index in this process; see recipeManager.
        }
        manager.hideRecipes(STRUCTURE_PREVIEW, lastPreviews);
        // JEI retains hidden recipe objects in its index; release their private worlds explicitly.
        lastPreviews.forEach(StructurePreviewEntry::close);
        lastPreviews = collectPreviews();
        manager.addRecipes(STRUCTURE_PREVIEW, lastPreviews);
        List<MachineRecipe> current = collectRecipes();
        // THE RELOAD'S EXTRA WORK, in one call: the previous revision stops being displayable. JEI keeps
        // both sets and filters by the hidden one, so a replacement is "hide the old key, add the new
        // object" and a deletion is "hide the old key, add nothing".
        manager.hideRecipes(MACHINE_RECIPE, List.copyOf(lastAdded));
        lastAdded = addToJei(new ManagerRegistration(manager), current);
        // Runtime creation can revisit the same objects registered during JEI startup.
        manager.unhideRecipes(MACHINE_RECIPE, lastAdded);
        Modularmachinery.LOG.info("[MMCE] JEI: re-registered {} machine recipe(s) after a reload",
                current.size());
    }

    /**
     * Runs {@link #refreshRecipes()} at the point where a reload is observed.
     *
     * <p>Called after a server snapshot is installed, behind the optional-mod and client-side guards. The
     * work is handed to the client thread when the calling thread is not it, because an integrated server
     * performs a reload on the server thread and JEI asserts its index is only touched from the main thread;
     * running it directly is reserved for the case where this <b>is</b> the client thread, which is the only
     * one where that assertion cannot fail.
     */
    public static void requestReload() {
        if (recipeManager == null) {
            return;   // JEI has no runtime in this process; there is no index to repair.
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.isSameThread()) {
            refreshRecipes();
            return;
        }
        final long generation = ++lastRequestedGeneration;
        pendingGeneration = generation;
        client.execute(() -> {
            if (pendingGeneration != generation) {
                return;   // a newer reload has been requested; this request is already out of date.
            }
            refreshRecipes();
        });
    }

    /**
     * TELLS JEI THAT A TIER LIVES IN THE STACK'S NBT (PLAN section 277).
     *
     * <p>JEI catalogs ITEMS, and every tier of a bus is the SAME item distinguished only by a
     * {@code BlockStateTag}. Without this, JEI showed one entry per item, named with the base key --
     * reported as "jei显示的还是物品输出总线，应该和拿手上一样显示完整名称". The hand shows the tier because
     * {@code ItemBlockMachineComponent.getName} reads the tag; JEI never saw the tag at all.
     *
     * <p><b>{@code useNbtForSubtypes}, not a custom interpreter.</b> The first attempt implemented
     * {@code ISubtypeInterpreter} as a lambda and did not compile: that interface has TWO abstract methods, so
     * it is not a functional interface. Reading the API settled it -- JEI already offers the case this mod
     * needs, "these stacks differ by their NBT", as one call per item. Writing an interpreter would have been
     * work done to reach something the API hands over.
     *
     * <p>The API was read from the JEI jar by {@code javap} rather than recalled, per section 186, after the
     * compiler rejected the guess.
     */
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        registration.useNbtForSubtypes(
                hellfirepvp.modularmachinery.common.registry.RegistryItems.BLUEPRINT.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.itemInputBus.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.itemOutputBus.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.fluidInputHatch.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.fluidOutputHatch.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.energyInputHatch.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.energyOutputHatch.get(),
                hellfirepvp.modularmachinery.common.registry.RegistryItems.blockCasing.get());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The controller is the catalyst: it is the block a player right-clicks to form and run a
        // machine, so it is what they have in mind when they look a recipe up. Using a casing or a
        // bus here would attach the recipe to a block that cannot run it.
        registration.addRecipeCatalyst(RegistryBlocks.blockController.get(), MACHINE_RECIPE);
        registration.addRecipeCatalyst(RegistryBlocks.blockController.get(), STRUCTURE_PREVIEW);
        registration.addRecipeCatalyst(RegistryBlocks.blockFactoryController.get(), STRUCTURE_PREVIEW);
    }

    /**
     * Gathers every loaded machine recipe — the CURRENT revision, read fresh on each call.
     *
     * <p>Skips <b>unexpanded adapter recipes</b>, matching what the engine does: an adapter recipe is a
     * template naming a vanilla recipe type, and until it is expanded against the vanilla
     * {@code RecipeManager} it has no requirements and no duration. Listing one in JEI would show a
     * recipe with no inputs that the machine will not actually run.
     *
     * <p>Package-private rather than private so {@link MachineRecipeCategory} can size itself from the same
     * list JEI is given (PLAN section 306). A second traversal of the loader could disagree with this one,
     * which is the reason the plugin already reads the machines from a single source — and the same reason
     * the reload path calls this method instead of carrying its own copy of the traversal.
     *
     * <p>The reload path depends on it re-reading: {@code RecipeLoader.load} clears its map and rebuilds
     * every {@code MachineRecipe} from the files, so the objects the engine runs afterwards are new ones,
     * and a list captured before the reload would be a list of the previous revision.
     *
     * @return the live set, in the loader's own order, so a rebuild cannot reorder the category
     */
    static List<MachineRecipe> collectRecipes() {
        List<MachineRecipe> collected = new ArrayList<>();
        var byMachine = MachineReloadListener.getRecipeLoader().getByMachine();
        for (var entry : byMachine.entrySet()) {
            for (MachineRecipe recipe : entry.getValue()) {
                if (recipe.isAdapter() || !recipe.loadJEI()) {
                    continue;
                }
                collected.add(recipe);
            }
        }
        return collected;
    }

    /**
     * THE ONE PLACE RECIPES ENTER JEI.
     *
     * <p>Both callers — {@code registerRecipes} at JEI's start-up and {@link #refreshRecipes()} on a reload
     * — arrive here, so the reload cannot grow a second registration path that drifts from the first. The
     * parameter is {@link IRecipeRegistration} because that is the type JEI's start-up call provides; the
     * reload reaches the same method through {@link ManagerRegistration}, a two-line delegate.
     *
     * @return the set JEI now holds, to be kept as {@link #lastAdded} for the next reload
     */
    private static List<MachineRecipe> addToJei(IRecipeRegistration registration, List<MachineRecipe> recipes) {
        if (!recipes.isEmpty()) {
            registration.addRecipes(MACHINE_RECIPE, recipes);
        }
        return List.copyOf(recipes);
    }

    /**
     * The reload's view of {@link IRecipeRegistration}, wrapping the runtime {@link IRecipeManager}.
     *
     * <p>There is <b>one</b> description of how a recipe enters JEI ({@link #addToJei}) and it takes the
     * start-up type, because that is the type JEI calls the plugin with. This makes the reload arrive at the
     * same method with the same argument, rather than duplicating the call against a second API surface.
     *
     * <p>Delegating is exact rather than approximate: read from the bytecode, JEI's own
     * {@code mezz.jei.library.load.registration.RecipeRegistration.addRecipes} is
     * {@code checkNotNull(type); checkNotNull(recipes); recipeManager.addRecipes(type, recipes)} and
     * {@code mezz.jei.library.recipes.RecipeManager.addRecipes} is {@code checkNotNull(type);
     * checkNotNull(recipes); validateRecipes(type, recipes); assertMainThread();
     * internal.addRecipes(type, recipes)} — so this call performs the same {@code validateRecipes} and the
     * same main-thread assertion as the start-up one.
     *
     * <p>The methods the reload never calls throw rather than returning something invented: a registration
     * view that quietly answers {@code getJeiHelpers()} with null would be a plausible-looking object that
     * fails somewhere else.
     */
    private record ManagerRegistration(IRecipeManager manager) implements IRecipeRegistration {

        @Override
        public <T> void addRecipes(RecipeType<T> recipeType, List<T> recipes) {
            manager.addRecipes(recipeType, recipes);
        }

        @Override
        public mezz.jei.api.helpers.IJeiHelpers getJeiHelpers() {
            throw new UnsupportedOperationException("the reload path only adds recipes");
        }

        @Override
        public mezz.jei.api.runtime.IIngredientManager getIngredientManager() {
            throw new UnsupportedOperationException("the reload path only adds recipes");
        }

        @Override
        public mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory getVanillaRecipeFactory() {
            throw new UnsupportedOperationException("the reload path only adds recipes");
        }

        @Override
        public <T> void addIngredientInfo(T ingredient, mezz.jei.api.ingredients.IIngredientType<T> type,
                                          net.minecraft.network.chat.Component... description) {
            throw new UnsupportedOperationException("the reload path only adds recipes");
        }

        @Override
        public <T> void addIngredientInfo(List<T> ingredients, mezz.jei.api.ingredients.IIngredientType<T> type,
                                          net.minecraft.network.chat.Component... description) {
            throw new UnsupportedOperationException("the reload path only adds recipes");
        }
    }
}
