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
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.common.geo.MachineGeoModel;
import hellfirepvp.modularmachinery.common.geo.ControllerModelAnimationEvent;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The machine controller block entity.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream extended {@code TileEntityBase} (itself a {@code TileEntity})
 * and persisted through {@code readFromNBT}/{@code writeToNBT}. The 1.20.1 hooks are
 * {@code load(CompoundTag)} and {@code saveAdditional(CompoundTag)} — and note that
 * {@code saveAdditional} <b>returns void and mutates the tag it is given</b>, rather than returning a
 * new tag as {@code writeToNBT} did.
 *
 * <p>This class currently stores only the machine's assembly state. Recipe execution, the structure
 * matcher and the async tick model land in later units; the containers and GUI are separate work.
 */
public class TileController extends TileEntityBase implements GeoBlockEntity {
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    /** Last model/resource revision observed by this client-side animation instance. */
    private long clientAnimationRevision = Long.MIN_VALUE;

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    /**
     * GeckoLib's controller is deliberately data-driven.  A script can declare any animation name
     * in the model's animation file; the first declared animation is selected while the machine is
     * formed.  A working machine gets the same animation for now, which keeps old scripts such as
     * the Dream Energy Core example functional without requiring special resource names.
     */
    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar registrar) {
        // The source controller is constructed with transitionLengthTicks = 0.  Keeping the
        // zero-length transition matters for model predicates that deliberately switch to STOP
        // when a machine unforms: a five-tick blend makes the old animated pose linger after the
        // structure has already fallen apart.
        registrar.add(new AnimationController<>(this, "mmce_machine", 0, state -> {
            // A resource reload replaces GeckoLib's baked maps but does not recreate loaded block
            // entities.  Reset the controller once when MMCE's revision changes so an old queued
            // animation cannot retain references to the previous geometry/animation definition.
            if (getLevel() != null && getLevel().isClientSide) {
                long currentRevision = GeoMachineModelRegistry.revision();
                if (clientAnimationRevision != currentRevision) {
                    clientAnimationRevision = currentRevision;
                    state.getController().forceAnimationReset();
                }
            }
            MachineGeoModel model = GeoMachineModelRegistry.getForController(this);
            if (model == null || !isFormed()) return PlayState.STOP;
            ControllerModelAnimationEvent event = new ControllerModelAnimationEvent(model);
            GeoMachineModelRegistry.dispatchAnimation(getMachineName(), event);
            MachineGeoModel.Animation animation = event.getAnimations().isEmpty()
                    ? model.defaultAnimation() : event.getAnimations().get(0);
            if (event.getPlayState() != 0) return PlayState.STOP;
            if (animation == null || animation.name().isBlank()) return PlayState.STOP;
            RawAnimation raw = animation.loop()
                    ? RawAnimation.begin().thenLoop(animation.name())
                    : RawAnimation.begin().thenPlay(animation.name());
            return state.setAndContinue(raw);
        }));
    }

    @Override
    public double getTick(Object object) {
        return getLevel() == null ? 0 : getLevel().getGameTime();
    }
    /**
     * The blueprint slot's backing inventory: exactly one stack, accepted only from ItemBlueprint.
     *
     * <p>The restriction lives on the handler's isItemValid rather than in a bespoke Slot subclass.
     * Upstream used an inner ContainerController$SlotBlueprint for this; the effect is the same, and it has
     * to hold in BOTH places a stack can arrive -- a player click and a hopper -- which is why it belongs on
     * the inventory rather than on the slot widget. Slot#mayPlace alone would leave automation open.
     */
    private final net.minecraftforge.items.ItemStackHandler blueprintInventory =
            new net.minecraftforge.items.ItemStackHandler(1) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                    // THE BLUEPRINT IN THE SLOT NAMES THE MACHINE (PLAN section 264).
                    //
                    // `setMachineName` had exactly two callers: the /modularmachinery bind command and the
                    // self-test. The slot accepted blueprints and nothing ever read one, so the ONLY way to
                    // bind a controller was to type a command -- which is not the flow the mod describes.
                    //
                    // A blueprint carries one machine key; dropping it in is how a controller learns which
                    // machine it is. This is that link, and it was the last missing piece of the binding
                    // path: the slot, the item, the preview screen and the formation code all existed, and
                    // nothing joined them.
                    if (!loadingBlueprint) syncMachineFromBlueprint();
                }

                @Override
                public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                    if (!(stack.getItem() instanceof hellfirepvp.modularmachinery.common.item.ItemBlueprint)) return false;
                    String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
                    var associated = hellfirepvp.modularmachinery.common.item.ItemBlueprint.getAssociatedMachine(stack);
                    return fixed == null || associated != null && fixed.equals(associated.getRegistryName());
                }
            };

    /** @return the single-slot blueprint inventory, for the controller menu and automation */
    public net.minecraftforge.items.IItemHandler getBlueprintInventory() {
        return blueprintInventory;
    }

    /** True while the controller believes its multiblock is assembled and running. */

    /**
     * Set when a saved controller comes back FORMED, so the first tick can re-bind its components.
     *
     * <p>THE FLAG IS PERSISTED AND THE BINDING IS NOT (PLAN 627): load reads "formed" back as true while
     * boundPositions comes back empty, and only a player action used to fix that. It is a ONE-SHOT request
     * rather than a condition, so a machine that legitimately binds nothing -- every part a casing -- is not
     * re-walked every tick.</p>
     */
    private boolean pendingRebind = false;
    private boolean loadingBlueprint;
    private boolean formed = false;
    private long nextAutomaticStructureCheck = Long.MIN_VALUE;
    private int structureSearchRetries;

    /** First tick, then upstream's unformed retry delay: 30 + retries * 5, capped at 100. */
    boolean automaticStructureCheckDue(long tick) {
        if (tick < nextAutomaticStructureCheck) return false;
        nextAutomaticStructureCheck = tick + Math.min(30 + structureSearchRetries * 5, 100);
        structureSearchRetries = Math.min(structureSearchRetries + 1, 14);
        return true;
    }

    /** Identifier of the machine definition currently bound to this controller, or {@code null}. */
    private String machineName = null;

    /** Player that placed this controller; used by the optional controller security system. */
    private UUID owner;

    /** Controller-scoped data exposed to integrations and persisted with the block entity. */
    private CompoundTag customData = new CompoundTag();
    /** Permanent controller modifiers, keyed like upstream's customModifier map. */
    private final Map<String, RecipeModifier> customModifiers = new LinkedHashMap<>();

    public CompoundTag getCustomDataTag() {
        return customData;
    }

    public void setCustomDataTag(CompoundTag data) {
        this.customData = data == null ? new CompoundTag() : data.copy();
        markForUpdate();
    }

    public Map<String, RecipeModifier> getCustomModifiers() {
        return java.util.Collections.unmodifiableMap(customModifiers);
    }

    public boolean hasModifier(String key) {
        return key != null && customModifiers.containsKey(key);
    }

    public void addPermanentModifier(String key, RecipeModifier modifier) {
        if (key == null || key.isEmpty() || modifier == null) return;
        customModifiers.put(key, modifier);
        markForUpdate();
    }

    public void removePermanentModifier(String key) {
        if (key != null && customModifiers.remove(key) != null) markForUpdate();
    }

    public void clearCustomRuntimeState() {
        customData = new CompoundTag();
        customModifiers.clear();
        markForUpdate();
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        if (!java.util.Objects.equals(this.owner, owner)) {
            this.owner = owner;
            markForUpdate();
        }
    }

    // --- Active recipe state (R4: written only on the server thread) ---

    /** Name of the recipe currently running, or {@code null} when idle. */
    private String recipeName;
    private final java.util.Set<Integer> activeCatalystRequirements = new java.util.LinkedHashSet<>();
    /** Ticks elapsed on the current recipe. */
    private int recipeTicks;
    // PARALLELISM OF THE RUNNING CRAFT (PLAN 592-601, upstream ActiveMachineRecipe U43/U58-U69/U156-U157).
    // IT IS PERSISTED BECAUSE IT IS PART OF THE RECIPE STATE, NOT OF THE MACHINE CONFIGURATION: a craft that is
    // half done when the world saves must remember how parallel it was, or it would resume producing a different
    // amount than it started with. UPSTREAM STORES BOTH THE VALUE AND ITS CEILING, and this port does too, because
    // the ceiling is what a reloaded craft is allowed to be reduced to.
    private int parallelism = 1;
    private int maxParallelism = 1;
    /** Duration of the current recipe in ticks. */
    private int recipeDuration;
    /** Indices of delayed requirements already consumed during this recipe run. */
    private final java.util.Set<Integer> triggeredRequirements = new java.util.HashSet<>();
    /** Source-style combination-component group used by the active recipe, or null for normal machines. */
    private String isolationGroupKey;

    // Source TOP exposes rolling CPU/search figures. These counters are server-side diagnostics and are
    // intentionally transient: a world reload starts a fresh measurement window instead of persisting timings.
    private long performanceNanos;
    private long performanceSamples;
    private long recipeSearchNanos;
    private long recipeSearchSamples;

    public void recordPerformanceNanos(long nanos) {
        performanceNanos += Math.max(0L, nanos);
        performanceSamples++;
    }

    public void recordRecipeSearchNanos(long nanos) {
        recipeSearchNanos += Math.max(0L, nanos);
        recipeSearchSamples++;
    }

    /** Rolling-window average in microseconds, capped to avoid overflow after a very long server uptime. */
    public long getAveragePerformanceMicros() {
        return performanceSamples == 0 ? 0 : Math.min(Long.MAX_VALUE / 1_000L,
                performanceNanos / performanceSamples / 1_000L);
    }

    public long getAverageRecipeSearchMicros() {
        return recipeSearchSamples == 0 ? 0 : Math.min(Long.MAX_VALUE / 1_000L,
                recipeSearchNanos / recipeSearchSamples / 1_000L);
    }

    public long getPerformanceSamples() { return performanceSamples; }

    public String getIsolationGroupKey() {
        return isolationGroupKey;
    }

    public void setIsolationGroupKey(String key) {
        if (java.util.Objects.equals(isolationGroupKey, key)) return;
        isolationGroupKey = key;
        markForUpdate();
    }

    public boolean hasTriggeredRequirement(int index) {
        return triggeredRequirements.contains(index);
    }

    public void markTriggeredRequirement(int index) {
        triggeredRequirements.add(index);
    }

    public void clearTriggeredRequirements() {
        triggeredRequirements.clear();
    }

    /**
     * How many components the structure matcher bound to this controller at the last formation.
     *
     * <p>Recorded because it is the difference between "the machine is assembled" and "the machine is
     * assembled <i>and</i> its busses and hatches are actually wired up". A structure can match while
     * binding nothing — for instance when every declared position holds a decorative casing — and such a
     * machine forms, looks correct, and can never run a recipe. The GUI surfaces the number so that case
     * is visible to a player instead of being a silent dead end.
     *
     * <p>Transient: it is re-derived on every formation, so persisting it would only create a way for the
     * saved value to disagree with the world.
     */
    private int boundComponents = 0;

    /**
     * The positions of the components bound at the last formation. Empty when the machine is unformed.
     *
     * <p>Retained so the chunk-unloaded state can cover the whole structure rather than only this controller's
     * own chunk, which is what upstream means (§136.3). `MachineFormation.bindComponents` already computed
     * this list for its own unbinding pass, so keeping it costs no extra walking.
     *
     * <p>Transient, like `boundComponents`: re-derived on every formation, so persisting it would only create
     * a way for the saved value to disagree with the world.
     */
    private java.util.List<BlockPos> boundPositions = java.util.List.of();
    /** World positions covered by the formed pattern, synchronized for the client model hider. */
    private java.util.List<BlockPos> hiddenPositions = java.util.List.of();

    /** @param positions the components bound at the last formation; see {@link #boundPositions} */
    public void setBoundPositions(java.util.List<BlockPos> positions) {
        this.boundPositions = java.util.List.copyOf(positions);
    }

    /**
     * @return how many bound component POSITIONS the controller retained at the last formation.
     *
     * <p>Exists so a check can prove the list is populated rather than empty (§139.2). Without it,
     * {@link #isStructureChunkLoaded} iterates nothing and returns {@code true} — and a check asserting
     * "chunk-loaded agrees with the level" cannot tell that apart from a structure whose every block is
     * genuinely loaded. **A loop over an empty collection is not a check.**
     *
     * <p>Callers should compare this against the count the formation itself reported, which is an
     * independent number.
     */
    /**
     * The component positions bound by the last formation, READ-ONLY.
     *
     * <p>Added for the machine event dispatch (PLAN sections 491-493, path 1): upstream iterates a map of
     * live component objects, and this port holds this list instead. THE LIST IS NOT DUPLICATED -- the
     * dispatch reuses the very list isStructureChunkLoaded walks, which is why the accessor lives here
     * rather than a second index being built.</p>
     */
    public java.util.List<BlockPos> getBoundPositions() {
        return boundPositions;
    }

    public java.util.List<BlockPos> getHiddenPositions() { return hiddenPositions; }

    public void setHiddenPositions(java.util.List<BlockPos> positions) {
        this.hiddenPositions = positions == null ? java.util.List.of() : java.util.List.copyOf(positions);
    }

    /**
     * The pattern this machine was FORMED with — the definition's own parts with its dynamic sections merged in
     * (PLAN §750), or {@code null} while it is unformed.
     *
     * <p><b>IT IS UPSTREAM'S {@code foundPattern}, AND THE SAME OBJECT IS THE POINT.</b> Upstream keeps the
     * matched array on the tile ({@code TileMultiblockMachineController} U140) precisely so that after formation
     * the structure is <i>the one that actually matched</i> — the wall PLUS the variable-length line — and every
     * later reader (the re-check at U689, the component binding at U781, the casing walk at U490) asks THAT array
     * rather than the definition. This port stored the bound <i>positions</i> instead, which is enough for the
     * chunk-unloaded question but not for "which cells are components", so the engine kept asking the
     * definition's static half and the shipped {@code assembly_line} had no reachable input or output bus.</p>
     *
     * <p>Transient and re-derived on every formation, like {@link #boundPositions}: it is a fact about the world,
     * not about the machine, and a saved copy could disagree with the blocks after a reload.</p>
     */
    private hellfirepvp.modularmachinery.common.machine.MachinePattern formedPattern;

    /** @param pattern the pattern the formation walk bound components from; see {@link #formedPattern} */
    public void setFormedPattern(hellfirepvp.modularmachinery.common.machine.MachinePattern pattern) {
        this.formedPattern = pattern;
    }

    /**
     * @return the formed pattern, falling back to the definition's own when nothing has been recorded.
     *
     * <p>The fallback matters for the one-shot re-bind after a world load, which runs before any formation in
     * that session: asking for the formed pattern there must not answer {@code null} and take every component
     * lookup down with it. The definition's pattern is the right answer for the 4 of 5 shipped machines that
     * declare no dynamic sections at all.</p>
     */
    public hellfirepvp.modularmachinery.common.machine.MachinePattern getFormedPattern(
            hellfirepvp.modularmachinery.common.machine.DynamicMachine fallback) {
        return formedPattern != null ? formedPattern : fallback.getPattern();
    }

    public int getBoundPositionCount() {
        return boundPositions.size();
    }

    /** @param count the number of components bound by the last formation */
    public void setBoundComponentCount(int count) {
        this.boundComponents = count;
    }

    /** @return the number of components bound at the last formation */
    public int getBoundComponentCount() {
        return boundComponents;
    }

    public boolean hasActiveRecipe() {        return recipeName != null;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public java.util.Set<Integer> getActiveCatalystRequirements() {
        return java.util.Set.copyOf(activeCatalystRequirements);
    }

    public void setActiveCatalystRequirements(java.util.Set<Integer> values) {
        activeCatalystRequirements.clear();
        if (values != null) activeCatalystRequirements.addAll(values);
    }

    public int getRecipeTicks() {
        return recipeTicks;
    }

    public int getRecipeDuration() {
        return recipeDuration;
    }

    /**
     * @return how many times this craft's inputs and outputs are multiplied, as decided when it started
     *
     * <p>IT IS PART OF THE RECIPE STATE RATHER THAN A QUERY AGAINST THE MACHINE (upstream ActiveMachineRecipe U43):
     * a craft in progress keeps the value it began with, so removing a parallel controller mid-craft does not
     * silently change what the craft is producing. THE CEILING IS STORED ALONGSIDE IT because that is what a
     * reloaded or restarted craft is allowed to be reduced to.</p>
     */
    public int getParallelism() {
        return parallelism;
    }

    /** @return the ceiling this craft started under, used when a restart has to reduce its parallelism */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    // ------------------------------------------------------------------
    // The parallelism READOUT (upstream GuiMachineController U145-U154 / GuiFactoryController U277-U311)
    // ------------------------------------------------------------------

    /**
     * The two lines upstream's controller GUI prints under the progress line, as <b>finished components</b>.
     *
     * <pre>
     * upstream GuiMachineController U145-U154        this port
     *   int parallelism    = getParallelism()          getParallelism()
     *   int maxParallelism = getMaxParallelism()       getMaxParallelism()
     *   if (parallelism &gt; 1) {                        the SAME guard, so the pair is readout rather
     *     I18n.format("gui.controller.parallelism", …)   than a constant 1/1 on every idle machine
     *     I18n.format("gui.controller.max_parallelism",…)
     *   }
     * </pre>
     *
     * <p><b>RULE A0 -- THE SOURCE OF THE TWO NUMBERS IS NOT UPSTREAM'S.</b></p>
     * <ol>
     *   <li><b>upstream file:line.</b> {@code GuiMachineController} U145-U146 reads
     *       {@code activeRecipe.getParallelism()} / {@code getMaxParallelism()} off the
     *       {@code ActiveMachineRecipe}, and {@code GuiFactoryController} U277-U311 reads the factory's
     *       {@code thread.getParallelism()} off the run it is describing. Both are the value of the craft
     *       the line describes, and both are the ceiling it started under.</li>
     *   <li><b>evidence of the difference.</b> On 1.12.2 the client's {@code GuiContainer} was CONSTRUCTED
     *       WITH the tile ({@code GuiMachineController} U42-U44) and could therefore hold the tile's
     *       {@code ActiveMachineRecipe}; the port's screens are constructed with a MENU, and the menu's six
     *       int slots are defined by {@code MenuMachineController} (PROGRESS, DURATION, FORMED, COMPONENTS,
     *       REDSTONE, CHUNK) with no parallelism among them. The port's screens nonetheless have the
     *       reading -- {@code ScreenFactoryController} reads its machine name and its run list off the
     *       client-side tile for exactly this reason (PLAN 664), and the sentence is in that class's own
     *       table: the name "is not an int and the menu's data slots are int-only".</li>
     *   <li><b>what this does instead.</b> Reads the two values off the tile the screen already resolves --
     *       on a client that is the copy the update tag produced, which is why {@code saveAdditional} writes
     *       both keys (see the field javadoc above). {@link TileFactoryController} overrides both getters to
     *       report its FIRST RUNNING RUN, so the factory screen's numbers are the run's, i.e. upstream's
     *       source, reached through the tile. NO MENU SLOT WAS ADDED: widening {@code DATA_SIZE} would change
     *       what every screen that reuses the menu's layout reads, and a check on a dedicated server cannot
     *       watch a screen draw either slot.</li>
     * </ol>
     *
     * <p><b>WHY THE LINES ARE BUILT HERE RATHER THAN IN THE TWO SCREENS.</b> A screen class cannot be
     * loaded on a dedicated server at all, so an assertion about what a line SAYS could not be made against
     * one -- the suite reads client classes as bytes and can only ask which fields a body reads. Building
     * the components on the TILE puts the key, the argument and the guard in a class the suite can call,
     * and the screens are left with a loop. The port's own precedent is the factory menu's pinned
     * arithmetic, stated there for this same reason: a value a check must see has to live where the check
     * can reach it.</p>
     *
     * <p>The keys are the ones the lang files carry ({@code gui.controller.parallelism} /
     * {@code gui.controller.max_parallelism}); the arguments are the two numbers, NOT literals -- a {@code
     * Component} argument that happened to equal the value would still be the wrong argument, and the suite
     * reads the argument back out of the component it gets here.</p>
     *
     * @return the two components in upstream's order, or an EMPTY list when this craft is not parallel
     */
    public java.util.List<net.minecraft.network.chat.Component> parallelismReadoutLines() {
        if (getParallelism() <= 1) {
            return java.util.List.of();
        }
        return java.util.List.of(
                net.minecraft.network.chat.Component.translatable(PARALLELISM_KEY, getParallelism()),
                net.minecraft.network.chat.Component.translatable(MAX_PARALLELISM_KEY, getMaxParallelism()));
    }

    /** Upstream {@code GuiMachineController} U148, verbatim. Public so the screen and the suite share ONE name. */
    public static final String PARALLELISM_KEY = "gui.controller.parallelism";
    /** Upstream {@code GuiMachineController} U151, verbatim. */
    public static final String MAX_PARALLELISM_KEY = "gui.controller.max_parallelism";

    /**
     * How far apart the readout's lines are drawn, and how far the block is offset from the line above it --
     * ONE number for both screens, reusing the line height this port's status text already uses. Upstream
     * advances 10 pixels inside a 0.72 pose (i.e. 7.2 panel pixels); the port's screens draw unscaled 1.20.1
     * text, whose own comfortable line height is this value -- {@code ScreenMachineController} already lays its
     * status, progress and components lines out on it before this readout was added.
     */
    public static final int READOUT_LINE_STRIDE = 18;

    /** Starts a recipe. Inputs must already have been consumed by the caller. */
    public void beginRecipe(String recipeName, int duration, int parallelism) {
        this.recipeName = recipeName;
        this.recipeDuration = Math.max(1, duration);
        this.recipeTicks = 0;
        this.parallelism = Math.max(1, parallelism);
        this.maxParallelism = Math.max(1, parallelism);
        this.triggeredRequirements.clear();
        this.isolationGroupKey = null;
        // A NEW CRAFT HAS NO HOLD REASON (PLAN 683). Without this a machine that was held by a full output and
        // then started a different craft would keep displaying the previous craft's reason.
        setStatusMessage(null);
        markForUpdate();
    }

    /**
     * Advances the progress counter by one tick, <b>stopping at the duration</b>.
     *
     * <p><b>THE CLAMP IS UPSTREAM'S, AND WITHOUT IT THE COUNTER RAN AWAY (PLAN 683, DEFECT B).</b> Upstream
     * {@code ActiveMachineRecipe.tick} returns <i>before</i> its own {@code this.tick++} once the craft is
     * complete (U82-U85), where complete means {@code tick >= totalTick} (U117-U118) — so upstream's counter is
     * clamped at {@code totalTick} by construction and a player can never read a progress above 100%.</p>
     *
     * <p>THE PORT INCREMENTED UNCONDITIONALLY, and the "output has no room" hold below leaves the increment in
     * place while returning early — so a machine whose output bus was full counted up forever while the GUI
     * read {@code 2478 / 60 t}. That is not cosmetic: progress is the only number a player has for telling
     * "working" apart from "stuck", and it said "working" for 40 minutes.</p>
     */
    public void advanceRecipe() {
        if (this.recipeTicks < this.recipeDuration) {
            this.recipeTicks++;
        }
        // Syncing every tick would flood the client with packets for no visual benefit; progress is
        // synced on the transitions and on completion instead.
    }
        // THE COUNTER, READ AND WRITTEN (PLAN 542, D7b). The READ was here all along; this is the WRITE.
        //
        // Upstream RecipeTickEvent.preventProgressing rewrites the recipe own tick counter: if it is above zero it
        // subtracts one, so the craft does not advance on that tick. THE PORT ADVANCES UNCONDITIONALLY ONE LINE
        // ABOVE, so subtracting one here has EXACTLY the same net effect -- one tick of no progress -- and the
        // floor-at-zero guard is upstream own, carried over rather than invented.
        //
        // WHY THIS IS NOT A SETTER: nothing in this port needs to place the counter at an arbitrary value, and a
        // setter would let a caller leave it above the duration or below zero. A one-step rewind is the whole
        // contract upstream gives its handlers.
        public void rewindRecipeTick() {
            if (this.recipeTicks > 0) {
                this.recipeTicks--;
            }
        }


    /** Completes the current recipe and returns to idle. */
    public void finishRecipe() {
        this.recipeName = null;
        this.recipeTicks = 0;
        this.recipeDuration = 0;
        this.triggeredRequirements.clear();
        this.activeCatalystRequirements.clear();
        this.isolationGroupKey = null;
        setStatusMessage(null);
        markForUpdate();
    }

    /**
     * Why the machine is being held, as an <b>unlocalized translation key</b>, or {@code null} when it is not.
     *
     * <p><b>ADDED FOR PLAN 683 (DEFECT B): A BLOCKED CRAFT HAD NO REASON ANYWHERE A PLAYER COULD SEE.</b> The
     * hold itself is correct and upstream — {@code RecipeThread.onFinished} asks {@code canFinishCrafting()}
     * and, on failure, sets {@code waitForFinish} and stores the failure message as the thread's status
     * (upstream RecipeThread U73-U78) — but upstream's {@code CraftingStatus} carries that message to the GUI
     * as an <b>unloc</b> key, and this port has no {@code CraftingStatus}.</p>
     *
     * <p><b>WHY A KEY AND NOT A SENTENCE.</b> Upstream's field is literally called {@code unlocMessage} and the
     * client localizes it, so a Chinese client on an English server still reads Chinese. A server-side
     * {@code getString()} would fix the text to the server's locale, which is the same defect the port's other
     * status lines avoid by choosing their key client-side (ScreenMachineController). The port ships both
     * language files.</p>
     *
     * <p><b>IT RIDES THE EXISTING SYNC RATHER THAN A NEW PACKET.</b> {@code saveAdditional} is what
     * {@code getUpdateTag} returns, so writing it there puts it on the block entity's own update packet, which
     * is how {@code formed} and the recipe state already reach the client — and
     * {@code ScreenFactoryController}/{@code ScreenSmartInterface} already read extra state off the
     * client-side block entity. {@link #markForUpdate()} is called only on a CHANGE, so a machine holding for
     * a thousand ticks sends one packet, not a thousand.</p>
     */
    private String statusMessage = null;

    /** @param key the unlocalized reason the machine is holding, or {@code null} to clear it */
    public void setStatusMessage(String key) {
        if (java.util.Objects.equals(this.statusMessage, key)) {
            return;     // unchanged: no packet, no chunk-dirty flag
        }
        this.statusMessage = key;
        markForUpdate();
    }

    /** @return the unlocalized reason the machine is holding, or {@code null}; see {@link #statusMessage} */
    public String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Whether the craft this controller carries is <b>advancing</b> rather than held — upstream's
     * {@code getControllerStatus().isCrafting()}.
     *
     * <p><b>WHO ASKS (PLAN 711).</b> Upstream's TheOneProbe provider paints its progress bar from exactly this
     * question: {@code MMInfoProvider} U246 puts the machine controller's status through {@code isCrafting()}
     * and takes the three FAILURE colours at U250-U252 when it answers false. The port's own probe
     * ({@code MachineProbeProvider}) needs the same question answered once, at the tile, rather than rebuilt at
     * the HUD — so this method is the tile's answer and the probe is its reader.</p>
     *
     * <p><b>THE PREDICATE, AND BOTH HOLDS THAT FEED IT.</b> {@code statusMessage} is this port's field for
     * upstream's {@code CraftingStatus.unlocMessage}, and {@code MachineTicker} writes it at the output-room
     * refusal (its U677) <b>and at the energy shortfall (PLAN 712, its stall branch)</b>. A machine holding
     * because its output has no room therefore answers <b>false</b> here and takes upstream's red, and so does
     * a machine stalled with no power — which is upstream's own behaviour on both paths: the output hold is
     * {@code RecipeThread.onFinished} U73-U78, and the energy hold is {@code ActiveMachineRecipe.tick}
     * U88-U98 returning {@code CraftingStatus.failure("craftcheck.failure.energy.input")} without reaching its
     * own {@code this.tick++} U101, so {@code CraftingStatus.isCrafting()} U53-U55 answers false while the
     * craft is kept. PLAN 711 recorded the energy half as an open gap; PLAN 712 closed it and this line is
     * corrected with it, because a method javadoc that still describes its own predicate as wrong is the same
     * defect as a wrong predicate. An empty {@code statusMessage} now means "this controller is holding nothing
     * it can name" on every path this engine has.</p>
     */
    public boolean isCrafting() {
        return hasActiveRecipe() && statusMessage == null;
    }

    /**
     * Drops the current recipe without emitting.
     *
     * <p>Used when the machine comes apart mid-craft or its definition disappears on reload. The inputs
     * are already consumed, so this loses them — the alternative is a phantom recipe that can never
     * complete, which is worse because it occupies the machine forever.
     */
    public void abandonRecipe() {
        if (recipeName != null) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.info(
                    "[MMCE] controller at {} abandoned recipe '{}' at {}/{} ticks",
                    worldPosition.toShortString(), recipeName, recipeTicks, recipeDuration);
        }
        finishRecipe();
    }

    /**
     * @param type the block entity type; passed as a supplier so subclasses (e.g. the factory
     *             controller) can bind their own registry entry without a static-initialisation cycle
     */
    protected TileController(java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<?>> type,
                             BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.machineName = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(state.getBlock());
    }

    public TileController(BlockPos pos, BlockState state) {
        this(hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities.CONTROLLER, pos, state);
    }

    /**
     * Takes the re-bind request if one is pending, and clears it.
     *
     * <p>CONSUMED ONCE RATHER THAN TESTED, so this cannot become a per-tick structure walk. The caller is
     * MachineTicker, which has the facing the walk needs; TileController does not carry it.</p>
     */
    public boolean consumeRebindRequest() {
        boolean requested = this.pendingRebind;
        this.pendingRebind = false;
        return requested;
    }

    public boolean isFormed() {
        return formed;
    }

    /**
     * True when a redstone signal is arriving at this controller, so the machine is held stopped.
     *
     * <p>Added for the controller's status model (§135): upstream's display set has SIX states and this port
     * derived only four of them, because nothing tracked the other two. `gui.controller.status.redstone_stopped`
     * — "Machine stopped by incoming redstone signal" — has been sitting in both language files, unwired,
     * since the asset conversion (§107.1).
     *
     * <p>A stopped machine reported as {@code idle} is the failure this prevents: "not running" and "cannot
     * run until you cut the signal" are the same pixels and different problems.
     *
     * <p>Read straight from the world rather than tracked in a field. A cached flag would need invalidating on
     * every neighbour change, and the one place that consumes it — a status line — is already reading the
     * controller every frame.
     */
    public boolean isRedstoneStopped() {
        net.minecraft.world.level.Level level = getLevel();
        return level != null && level.hasNeighborSignal(getBlockPos());
    }

    /**
     * True when this controller's own chunk is loaded.
     *
     * <p><b>Weaker than upstream's state, and deliberately so.</b> Upstream reports `chunk_unloaded` when
     * <i>a block in the multiblock structure</i> unloads — which needs the structure's component positions, and
     * the port RETAINS them (corrected per PLAN 501.6 -- see setBoundPositions, added later) ({@code boundComponents} is only a count, §91's note).
     * This checks the controller's own chunk, which catches the case where the status line is being read about
     * a block entity whose chunk has just gone away.
     *
     * <p>Reported as a weaker check rather than silently presented as the same thing: a status line that names
     * the wrong reason is worse than one that is coarser (§109).
     */
    public boolean isStructureChunkLoaded() {
        net.minecraft.world.level.Level level = getLevel();
        if (level == null || !level.isLoaded(getBlockPos())) {
            return false;
        }
        // Every bound component, which is what upstream's `chunk_unloaded` means: a block in the multiblock
        // structure going away. The list is what formation bound, so it matches the structure actually
        // standing rather than the definition's declared extent.
        for (BlockPos componentPos : boundPositions) {
            if (!level.isLoaded(componentPos)) {
                return false;
            }
        }
        return true;
    }

    public void setFormed(boolean formed) {
        if (this.formed != formed) {
            nextAutomaticStructureCheck = Long.MIN_VALUE;
            structureSearchRetries = 0;
        }
        // THE FIRE SITES LIVE HERE RATHER THAN AT THE CALLERS, AND THE REASON IS STRUCTURAL (PLAN 498):
        //
        //   upstream fires MachineStructureFormedEvent at TileMultiblockMachineController U609 and
        //   MachineStructureUpdateEvent at U610, right after it -- both inside the CONTROLLER OWN TICK.
        //   THIS PORT DOES NOT FORM IN THE TICK LOOP. Formation is driven by explicit actions, and
        //   setFormed has FIVE callers: CommandModularMachinery U249, ControllerInteraction U148,
        //   ItemConstructTool U119, and MachineSelfTest twice.
        //
        //   SO COPYING UPSTREAM POSITION WOULD MAKE FOUR OF THOSE FIVE FORM SILENTLY, with nothing fired.
        //   Putting the dispatch inside setFormed keeps upstream ORDER AND SEMANTICS and reaches every caller.
        if (this.formed != formed) {
            this.formed = formed;
        if (formed && formedPattern != null) {
            java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
            net.minecraft.core.Direction facing = getBlockState().hasProperty(
                    hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                    ? getBlockState().getValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                    : net.minecraft.core.Direction.NORTH;
            for (var part : formedPattern.getParts()) {
                positions.add(hellfirepvp.modularmachinery.common.machine.StructureMatcher.toWorldOffset(
                        worldPosition, facing, part.x(), part.y(), part.z()));
            }
            setHiddenPositions(positions);
        } else if (!formed) {
            setHiddenPositions(java.util.List.of());
        }
        if (formed) {
            // Upstream U609 then U610: formed FIRST, then update, and the order is upstream own (497.1).
            new hellfirepvp.modularmachinery.common.event.machine.MachineStructureFormedEvent(this)
                    .postEvent();
            new hellfirepvp.modularmachinery.common.event.machine.MachineStructureUpdateEvent(this)
                    .postEvent();
        } else {
            // Upstream U557: the structure changed. Same event, the unformed direction.
            new hellfirepvp.modularmachinery.common.event.machine.MachineStructureUpdateEvent(this)
                    .postEvent();
        }
            syncFormedState();
            markForUpdate();
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this));
        }
    }

    /** Upstream failure-action RESET: keep the active recipe and restart its progress at tick zero. */
    public void resetRecipeProgress() {
        this.recipeTicks = 0;
        markForUpdate();
    }

    public String getMachineName() {
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        return fixed == null ? machineName : fixed;
    }

    /**
     * Resolves the current upgrade set for upgrade-scoped event callbacks.
     *
     * <p>This intentionally mirrors {@code MachineContext.installedUpgrades()} without constructing a synthetic
     * server context. Events such as structure formation and machine ticks have a controller but no recipe context;
     * the controller's bound component positions are the authoritative set of upgrade buses for those events.</p>
     */
    public java.util.List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade>
    getInstalledUpgradesForEvents() {
        java.util.LinkedHashMap<hellfirepvp.modularmachinery.common.upgrade.UpgradeType,
                hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade> merged = new java.util.LinkedHashMap<>();
        if (level == null) return java.util.List.of();
        for (BlockPos pos : boundPositions) {
            if (level.getBlockEntity(pos) instanceof TileUpgradeBus bus) {
                for (var upgrade : bus.getUpgradesFor(machineName)) {
                    // Work on a detached copy: bus-owned upgrade instances are refreshed from inventory and must
                    // not have their stack count increased every time an event is posted.
                    var detached = upgrade.copy();
                    var previous = merged.putIfAbsent(detached.getType(), detached);
                    if (previous != null) {
                        int room = previous.getType().getMaxStackSize() - previous.getStackSize();
                        if (room > 0) previous.incrementStackSize(Math.min(room, detached.getStackSize()));
                    }
                }
            }
        }
        return java.util.List.copyOf(merged.values());
    }

    /** Detached blueprint controllers use the client model snapshot without attaching to a real world. */
    public boolean isBlueprintPreview() {
        return false;
    }

    public void setMachineName(String machineName) {
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        this.machineName = fixed == null ? machineName : fixed;
        markForUpdate();
    }

    /** Blueprint changes request a fresh search; optional blueprints do not unset a formed machine. */
    public void syncMachineFromBlueprint() {
        var blueprint = hellfirepvp.modularmachinery.common.item.ItemBlueprint
                .getAssociatedMachine(blueprintInventory.getStackInSlot(0));
        var found = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                .findByName(machineName);
        // An optional blueprint may be removed without changing the recognised machine. A required
        // blueprint must remain present, as in source checkStructure().
        if (formed && found != null && (!found.isRequiresBlueprint() || found == blueprint)) {
            markForUpdate();
            return;
        }
        if (formed) {
            setFormed(false);
            if (this instanceof TileFactoryController factory) factory.abandonAllRecipes();
            else abandonRecipe();
        }
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        this.machineName = fixed != null ? fixed : blueprint == null ? null : blueprint.getRegistryName();
        nextAutomaticStructureCheck = Long.MIN_VALUE;
        structureSearchRetries = 0;
        markForUpdate();
    }

    /**
     * Mirrors {@link #formed} onto the blockstate so the shipped
     * {@code facing=…,formed=…} model variants select the correct texture.
     */
    private void syncFormedState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(hellfirepvp.modularmachinery.common.block.BlockController.FORMED)
                && state.getValue(hellfirepvp.modularmachinery.common.block.BlockController.FORMED) != formed) {
            level.setBlock(worldPosition,
                    state.setValue(hellfirepvp.modularmachinery.common.block.BlockController.FORMED, formed),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("blueprint")) {
            loadingBlueprint = true;
            try { blueprintInventory.deserializeNBT(tag.getCompound("blueprint")); }
            finally { loadingBlueprint = false; }
        }
        this.formed = tag.getBoolean("formed");
        // A WORLD RELOAD IS NOT A FORMATION: the flag comes back and the binding does not (PLAN 627).
        this.pendingRebind = this.formed;
        this.hiddenPositions = readHiddenPositions(tag);
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        this.machineName = fixed != null ? fixed : tag.contains("machineName") ? tag.getString("machineName") : null;
        if (tag.hasUUID("owner")) {
            this.owner = tag.getUUID("owner");
        } else {
            this.owner = null;
        }
        this.recipeName = tag.contains("recipeName") ? tag.getString("recipeName") : null;
        this.activeCatalystRequirements.clear();
        if (tag.contains("activeCatalysts", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            for (int index : tag.getIntArray("activeCatalysts")) this.activeCatalystRequirements.add(index);
        }
        this.recipeTicks = tag.getInt("recipeTicks");
        this.recipeDuration = tag.getInt("recipeDuration");
        this.isolationGroupKey = tag.contains("isolationGroupKey") ? tag.getString("isolationGroupKey") : null;
        this.parallelism = tag.contains("parallelism") ? Math.max(1, tag.getInt("parallelism")) : 1;
        this.maxParallelism = tag.contains("maxParallelism") ? Math.max(1, tag.getInt("maxParallelism")) : this.parallelism;
        this.triggeredRequirements.clear();
        if (tag.contains("triggeredRequirements", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            for (int index : tag.getIntArray("triggeredRequirements")) this.triggeredRequirements.add(index);
        }
        // ASSIGNED UNCONDITIONALLY RATHER THAN ONLY WHEN PRESENT, and the difference is the CLIENT: this same
        // method is what onDataPacket runs, so an absent key has to CLEAR the message. Reading it only when
        // present would leave a stale "output full" line on a machine that has since started producing.
        this.statusMessage = tag.contains("statusMessage") ? tag.getString("statusMessage") : null;
        this.customData = tag.contains("customData") ? tag.getCompound("customData") : new CompoundTag();
        this.customModifiers.clear();
        if (tag.contains("customModifier", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag list = tag.getList("customModifier", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                RecipeModifier modifier = RecipeModifier.deserialize(entry.getCompound("modifier"));
                if (modifier != null && entry.contains("key")) {
                    this.customModifiers.put(entry.getString("key"), modifier);
                }
            }
        }
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("blueprint", blueprintInventory.serializeNBT());
        tag.putBoolean("formed", formed);
        if (!hiddenPositions.isEmpty()) {
            long[] positions = new long[hiddenPositions.size()];
            for (int i = 0; i < positions.length; i++) positions[i] = hiddenPositions.get(i).asLong();
            tag.putLongArray("hiddenPositions", positions);
        }
        if (machineName != null) {
            tag.putString("machineName", machineName);
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        // Progress is persisted so a recipe survives a chunk unload / server restart rather than
        // silently restarting and consuming a second set of inputs.
        if (recipeName != null) {
            tag.putString("recipeName", recipeName);
            tag.putInt("recipeTicks", recipeTicks);
            tag.putInt("recipeDuration", recipeDuration);
        tag.putInt("parallelism", parallelism);
        tag.putInt("maxParallelism", maxParallelism);
            if (isolationGroupKey != null) tag.putString("isolationGroupKey", isolationGroupKey);
            if (!triggeredRequirements.isEmpty()) {
                tag.putIntArray("triggeredRequirements", triggeredRequirements.stream().mapToInt(Integer::intValue).toArray());
            }
            if (!activeCatalystRequirements.isEmpty()) {
                tag.putIntArray("activeCatalysts", activeCatalystRequirements.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        // THE HOLD REASON RIDES THE SAME TWO HOOKS AS EVERYTHING ELSE: getUpdateTag delegates to
        // saveAdditional, so this one line is the whole client sync (see the field's javadoc). Written only
        // when set, so the tag carries nothing on the ordinary path.
        if (statusMessage != null) {
            tag.putString("statusMessage", statusMessage);
        }
        if (!customData.isEmpty()) {
            tag.put("customData", customData.copy());
        }
        if (!customModifiers.isEmpty()) {
            ListTag list = new ListTag();
            customModifiers.forEach((key, modifier) -> {
                if (key != null && modifier != null) {
                    CompoundTag entry = new CompoundTag();
                    entry.putString("key", key);
                    entry.put("modifier", modifier.serialize());
                    list.add(entry);
                }
            });
            tag.put("customModifier", list);
        }
    }

    private static java.util.List<BlockPos> readHiddenPositions(CompoundTag tag) {
        if (!tag.contains("hiddenPositions", net.minecraft.nbt.Tag.TAG_LONG_ARRAY)) return java.util.List.of();
        long[] positions = tag.getLongArray("hiddenPositions");
        java.util.ArrayList<BlockPos> result = new java.util.ArrayList<>(positions.length);
        for (long value : positions) result.add(BlockPos.of(value));
        return java.util.List.copyOf(result);
    }
}
