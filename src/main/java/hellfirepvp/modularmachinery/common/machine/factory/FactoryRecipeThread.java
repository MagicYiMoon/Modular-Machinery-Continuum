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
package hellfirepvp.modularmachinery.common.machine.factory;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.List;

/**
 * ONE recipe run inside a factory controller.
 *
 * <p>Upstream calls this {@code FactoryRecipeThread} and says in its own javadoc what it is:
 * <i>"Part of the TileFactoryController that stores data for separate recipe runs. Not a thread in the
 * true sense of the word."</i> (upstream U33-U38). The name is kept so the two read alike; THE TYPE NAME IS
 * THE ONE THING ABOUT IT THAT IS NOT A THREAD, and that is upstream's wording rather than this port's.</p>
 *
 * <h2>RULE A0 -- what upstream's thread owns and what this one owns instead</h2>
 *
 * <pre>
 * upstream FactoryRecipeThread extends RecipeThread (upstream RecipeThread, 301 lines)
 *   ctrl, permanentModifiers, semiPermanentModifiers, activeRecipe, status (CraftingStatus),
 *   waitForFinish, searchTask, context (RecipeCraftingContext), recipeSet (TreeSet)
 * this port: the craft's own state, and NOTHING that only a thread pool needs
 *   recipeName, ticks, duration, parallelism, maxParallelism, idleTime, blockedReason
 * </pre>
 *
 * <p>WHY EACH OMISSION, because a dropped field is a dropped behaviour unless the reason is written down:</p>
 *
 * <ul>
 *   <li><b>No {@code RecipeCraftingContext} field.</b> The port builds its context per evaluation from the
 *       recipe and the machine's live component positions ({@code MachineTicker.satisfies} does exactly
 *       that for the normal controller), so a held context would be a second copy of state that is already
 *       derived -- and the port's context is a value, not a handle (PLAN 228/229, RecipeCraftingContext).</li>
 *   <li><b>No {@code searchTask}.</b> Upstream's per-thread search is asynchronous
 *       ({@code FactoryRecipeSearchTask} hand its result back through a {@code ForkJoinTask}). This port
 *       searches inline on the server thread, which is the whole point of B1's decision, so there is no
 *       task handle to hold.</li>
 *   <li><b>No {@code CraftingStatus}.</b> That class does not exist in the port at all (PLAN 521, proven
 *       absent by concept search). Its reader here is {@code blockedReason}: the same string without the
 *       four-state wrapper, and it is what the failure event carries and the GUI would print.</li>
 *   <li>Per-thread permanent/semi-permanent modifiers and core recipe sets are retained below. Recipe sets
 *       store names rather than old loader objects so a definition reload resolves the current recipe.</li>
 * </ul>
 */
public class FactoryRecipeThread {

    /**
     * How long an IDLE thread is kept before it is dropped (upstream U42, verbatim).
     *
     * <p>Load-bearing rather than cosmetic: upstream removes idle threads every 20 ticks
     * (TileFactoryController U514-U519), and without the timeout a factory that once ran twenty different
     * recipes would hold twenty thread objects for the rest of the world's life.</p>
     */
    public static final int IDLE_TIME_OUT = 200;

    /** The controller this run belongs to. Never null for a live thread; upstream allowed null for a preset. */
    private final TileFactoryController factory;

    /** Empty for a dynamic thread; non-empty for a source-style fixed core thread. */
    private String threadName = "";
    private final Set<String> recipeSet = new LinkedHashSet<>();

    // --- the craft's own state, in the same field set the port's TileController carries for one craft ---

    private String recipeName;
    private int ticks;
    private int duration;
    private int parallelism = 1;
    private int maxParallelism = 1;

    /**
     * Ticks this thread has been idle, counted by the ticker (upstream U45, U127, U205).
     *
     * <p>Package-visible upstream ({@code public int idleTime}); it is a counter with one writer, so it is
     * private here with the two accessors the ticker and the cleanup need.</p>
     */
    private int idleTime;

    /**
     * Why this thread is not crafting, or {@code null} while it is.
     *
     * <p>This is the port's substitute for upstream's per-thread {@code CraftingStatus}: the same string the
     * failure event carries ({@code thread.getStatus().getUnlocMessage()}, upstream U294), kept because the
     * tick also needs it for the "held at 100%" and "starved" cases.</p>
     */
    private String blockedReason;
    /** Indices of delayed requirements already consumed during this thread's recipe run. */
    private final java.util.Set<Integer> triggeredRequirements = new java.util.HashSet<>();
    /** Source-style combination-component group selected by this run, or null for normal machines. */
    private String isolationGroupKey;
    private final java.util.Set<Integer> activeCatalystRequirements = new java.util.LinkedHashSet<>();

    /** Upstream RecipeThread modifier scopes, retained per factory run. */
    private final Map<String, RecipeModifier> permanentModifiers = new LinkedHashMap<>();
    private final Map<String, RecipeModifier> semiPermanentModifiers = new LinkedHashMap<>();

    /**
     * The loader's recipe object this run resolved to, or {@code null} while idle.
     *
     * <p>NOT A SECOND COPY OF STATE: it is the object the ticker already resolved out of the machine's
     * candidate list, held so the events can hand a handler the run's recipe without every fire site
     * re-resolving it. Upstream reaches the same result differently -- its thread owns an
     * {@code ActiveMachineRecipe} and its events read {@code getContext().getActiveRecipe().getRecipe()}
     * (upstream RecipeEvent L24) -- and this port's context is derived from the recipe instead (PLAN 500.3).</p>
     *
     * <p>NOT PERSISTED: the recipe is re-resolved by NAME from the loader on the first tick after a load, so a
     * stored handle would only create a way for the saved object to disagree with the reloaded definitions --
     * the reason MachineTicker abandons a run whose recipe name is gone.</p>
     */
    private MachineRecipe recipe;

    public FactoryRecipeThread(TileFactoryController factory) {
        this.factory = factory;
    }

    /** Configures the source mod's named core-thread preset. */
    public void configurePreset(String threadName, Collection<String> recipes) {
        configurePreset(threadName, recipes, Map.of(), Map.of());
    }

    public void configurePreset(String threadName, Collection<String> recipes,
                                Map<String, RecipeModifier> permanent, Map<String, RecipeModifier> semiPermanent) {
        this.threadName = threadName == null ? "" : threadName;
        this.recipeSet.clear();
        if (recipes != null) {
            for (String recipe : recipes) {
                if (recipe != null && !recipe.isBlank()) this.recipeSet.add(recipe);
            }
        }
        permanentModifiers.clear();
        semiPermanentModifiers.clear();
        if (permanent != null) permanent.forEach(this::addPermanentModifier);
        if (semiPermanent != null) semiPermanent.forEach(this::addSemiPermanentModifier);
    }

    public String getThreadName() {
        return threadName;
    }

    public Set<String> getRecipeSet() {
        return java.util.Collections.unmodifiableSet(recipeSet);
    }

    public boolean isPresetThread() {
        return !threadName.isEmpty();
    }

    /** Whether this thread may accept a candidate recipe under source thread-name/set rules. */
    public boolean acceptsRecipe(MachineRecipe candidate) {
        if (candidate == null || !candidate.matchesThreadName(threadName)) return false;
        return recipeSet.isEmpty() || recipeSet.contains(candidate.registryName());
    }

    /** @return the run's recipe, or {@code null} while it is idle */
    public MachineRecipe getRecipe() {
        return recipe;
    }

    /** @param recipe the loader's recipe object; cleared by {@link #finishRecipe()} and {@link #abandonRecipe()} */
    public void setRecipe(MachineRecipe recipe) {
        if (hasActiveRecipe() && recipe != null && !recipeName.equals(recipe.registryName()))
            throw new IllegalStateException("An active factory thread cannot bind a different recipe");
        this.recipe = recipe;
    }

    /** @return the controller this run belongs to */
    public TileFactoryController getFactory() {
        return factory;
    }
    // ------------------------------------------------------------------
    // Craft state -- the same operations the port's TileController exposes for ONE craft
    // ------------------------------------------------------------------

    public boolean hasActiveRecipe() {
        return recipeName != null;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public int getRecipeTicks() {
        return ticks;
    }

    public int getRecipeDuration() {
        return duration;
    }

    /** @return how many times this run's inputs and outputs are multiplied (upstream ActiveMachineRecipe U43) */
    public int getParallelism() {
        return parallelism;
    }

    /** @return the ceiling this run started under, used when a restart has to reduce its parallelism */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    public String getIsolationGroupKey() {
        return isolationGroupKey;
    }

    public void setIsolationGroupKey(String key) {
        this.isolationGroupKey = key;
    }

    public java.util.Set<Integer> getActiveCatalystRequirements() {
        return java.util.Set.copyOf(activeCatalystRequirements);
    }

    public void setActiveCatalystRequirements(java.util.Set<Integer> values) {
        activeCatalystRequirements.clear();
        if (values != null) activeCatalystRequirements.addAll(values);
    }

    /** Starts one recipe identity with a batch multiplier. Inputs must already have been consumed. */
    public void beginRecipe(String recipeName, int duration, int parallelism) {
        if (hasActiveRecipe()) throw new IllegalStateException("A factory thread already has an active recipe");
        this.recipeName = recipeName;
        this.duration = Math.max(1, duration);
        this.ticks = 0;
        this.parallelism = Math.max(1, parallelism);
        this.maxParallelism = Math.max(1, parallelism);
        this.triggeredRequirements.clear();
        this.activeCatalystRequirements.clear();
        this.isolationGroupKey = null;
        this.blockedReason = null;
        this.idleTime = 0;
    }

    /**
     * Advances this run's progress counter by one tick, <b>stopping at its own duration</b>.
     *
     * <p><b>THE CLAMP IS TILE CONTROLLER'S, AND WITHOUT IT A HELD FACTORY RUN COUNTED UP FOREVER (PLAN 708,
     * DEFECT B).</b> It is the same guard, on the same kind of field, for the same reason, and the two must
     * not disagree — a factory run and a single craft are the same craft with a different owner
     * ({@code TileController.advanceRecipe}, PLAN 683; upstream {@code ActiveMachineRecipe.tick} U82-U85
     * returns <i>before</i> its own {@code this.tick++} once {@code isCompleted()} — {@code tick >= totalTick},
     * U117-U118 — so upstream's counter is clamped by construction on both paths).</p>
     *
     * <p><b>WHY THE FACTORY NEEDED IT EVEN THOUGH IT INHERITS NOTHING FROM {@code TileController}:</b> a factory
     * run keeps its own {@code ticks}/{@code duration} on this class, and {@code FactoryTicker.tickThread}
     * increments it <i>before</i> it asks whether the outputs have room, then RETURNS on the hold — so the
     * increment survived the refusal and the next tick incremented again. The client saw the two numbers come
     * from different sources: the TOP overlay and the GUI's ticks line read this raw counter
     * ({@code 797 / 60}, {@code 300 / 60 t}), while the row's percentage and the bar are derived from
     * {@code min(1, ticks/duration)} and so read 100% on every one of those ticks. Clamping at the writer makes
     * all four agree at {@code duration/duration}.</p>
     */
    public void advanceRecipe() {
        if (this.ticks < this.duration) {
            this.ticks++;
        }
    }

    /**
     * Rewinds this run's tick counter by one, floored at zero.
     *
     * <p>THE ONE-STEP REWIND IS THE WHOLE CONTRACT a {@code FactoryRecipeTickEvent} handler gets from
     * {@code preventProgressing} (upstream U30-U38 subtracts one when the counter is above zero), and the
     * floor is upstream's own. Carried over rather than reinvented, exactly as
     * {@code TileController.rewindRecipeTick} was for the single-craft path at PLAN 542.</p>
     */
    public void rewindRecipeTick() {
        if (this.ticks > 0) {
            this.ticks--;
        }
    }

    /** Upstream failure-action RESET for this run. */
    public void resetRecipeProgress() {
        this.ticks = 0;
    }

    /** Completes the run and returns the thread to idle, ready for the next recipe. */
    public void finishRecipe() {
        boolean releasedRecipe = hasActiveRecipe();
        this.recipeName = null;
        this.recipe = null;
        this.ticks = 0;
        this.duration = 0;
        this.parallelism = 1;
        this.maxParallelism = 1;
        this.blockedReason = null;
        this.triggeredRequirements.clear();
        this.activeCatalystRequirements.clear();
        this.isolationGroupKey = null;
        // Upstream clears semi-permanent modifiers after a completed craft; permanent ones survive.
        this.semiPermanentModifiers.clear();
        // Includes normal completion, reload removal and destruction by tick/failure event handlers.
        if (releasedRecipe && factory != null) factory.requestImmediateRecipeSearch();
    }

    /** Drops the run without emitting. The inputs are already gone; see TileController#abandonRecipe. */
    public void abandonRecipe() {
        finishRecipe();
    }

    /**
     * @return whether this thread is running a craft that is advancing
     *
     * <p>Upstream asks the same question of its status ({@code status.getStatus() == CRAFTING}, U218-U220).
     * A recipe that is held -- starved of energy, or complete with no room for its output -- is NOT working,
     * which is what makes the controller's own {@code isWorking} the OR of its threads rather than a count.</p>
     */
    public boolean isWorking() {
        return hasActiveRecipe() && blockedReason == null;
    }

    /** @return whether this thread is free to be given a recipe (upstream U225-U228) */
    public boolean isIdle() {
        return !hasActiveRecipe();
    }

    public int getIdleTime() {
        return idleTime;
    }

    /** Called by the ticker for every tick this thread spends without a recipe (upstream U205). */
    public void countIdleTick() {
        this.idleTime++;
    }

    /** @return why this thread is not crafting, or {@code null} while it is */
    public String getBlockedReason() {
        return blockedReason;
    }

    /** @param reason the port's substitute for upstream's per-thread status message */
    public void setBlockedReason(String reason) {
        this.blockedReason = reason;
    }

    public void clearBlockedReason() {
        this.blockedReason = null;
    }

    public Map<String, RecipeModifier> getPermanentModifiers() {
        return java.util.Collections.unmodifiableMap(permanentModifiers);
    }

    public Map<String, RecipeModifier> getSemiPermanentModifiers() {
        return java.util.Collections.unmodifiableMap(semiPermanentModifiers);
    }

    public void addPermanentModifier(String key, RecipeModifier modifier) {
        if (key != null && !key.isEmpty() && modifier != null) permanentModifiers.put(key, modifier);
    }

    public void removePermanentModifier(String key) {
        if (key != null) permanentModifiers.remove(key);
    }

    public void addSemiPermanentModifier(String key, RecipeModifier modifier) {
        if (key != null && !key.isEmpty() && modifier != null) semiPermanentModifiers.put(key, modifier);
    }

    public void removeSemiPermanentModifier(String key) {
        if (key != null) semiPermanentModifiers.remove(key);
    }

    public void clearSemiPermanentModifiers() {
        semiPermanentModifiers.clear();
    }

    public boolean hasTriggeredRequirement(int index) {
        return triggeredRequirements.contains(index);
    }

    public void markTriggeredRequirement(int index) {
        triggeredRequirements.add(index);
    }

    /** All per-thread modifiers in deterministic insertion order. */
    public List<RecipeModifier> activeModifiers() {
        List<RecipeModifier> modifiers = new java.util.ArrayList<>(semiPermanentModifiers.values());
        modifiers.addAll(permanentModifiers.values());
        return modifiers;
    }

    // ------------------------------------------------------------------
    // Persistence -- upstream serialize/deserialize, minus what this port does not hold
    // ------------------------------------------------------------------

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        if (!threadName.isEmpty()) tag.putString("threadName", threadName);
        if (!recipeSet.isEmpty()) {
            net.minecraft.nbt.ListTag recipes = new net.minecraft.nbt.ListTag();
            for (String name : recipeSet) recipes.add(net.minecraft.nbt.StringTag.valueOf(name));
            tag.put("recipeSet", recipes);
        }
        if (recipeName != null) {
            tag.putString("recipeName", recipeName);
            tag.putInt("ticks", ticks);
            tag.putInt("duration", duration);
            tag.putInt("parallelism", parallelism);
            tag.putInt("maxParallelism", maxParallelism);
            if (isolationGroupKey != null) tag.putString("isolationGroupKey", isolationGroupKey);
        }
        tag.putInt("idleTime", idleTime);
        if (blockedReason != null) {
            // PERSISTED BECAUSE UPSTREAM PERSISTS ITS STATUS (upstream U176): a reloaded factory otherwise
            // shows a starved thread as a running one until the next tick recomputes the reason.
            tag.putString("blockedReason", blockedReason);
        }
        if (!triggeredRequirements.isEmpty()) {
            tag.putIntArray("triggeredRequirements", triggeredRequirements.stream().mapToInt(Integer::intValue).toArray());
        }
        if (!activeCatalystRequirements.isEmpty()) tag.putIntArray("activeCatalysts",
                activeCatalystRequirements.stream().mapToInt(Integer::intValue).toArray());
        writeModifiers(tag, "permanentModifiers", permanentModifiers);
        writeModifiers(tag, "semiPermanentModifiers", semiPermanentModifiers);
        return tag;
    }

    private static void writeModifiers(CompoundTag tag, String key, Map<String, RecipeModifier> modifiers) {
        if (modifiers.isEmpty()) return;
        ListTag list = new ListTag();
        modifiers.forEach((name, modifier) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", name);
            entry.put("modifier", modifier.serialize());
            list.add(entry);
        });
        tag.put(key, list);
    }

    /**
     * @param tag     a tag written by {@link #serialize()}
     * @param factory the controller the thread belongs to
     * @return the thread, or {@code null} when the tag describes no run at all
     *
     * <p>Upstream's deserialize returns null when the tag carries no {@code status} key (U74-U76). The
     * equivalent guard here is the recipe name: a thread tag with neither a recipe nor a reason is a thread
     * that has done nothing, and re-creating it would grow the list on every load.</p>
     */
    public static FactoryRecipeThread deserialize(CompoundTag tag, TileFactoryController factory) {
        if (tag == null) {
            return null;
        }
        boolean hasRecipe = tag.contains("recipeName");
        boolean hasReason = tag.contains("blockedReason");
        boolean hasPreset = tag.contains("threadName") || tag.contains("recipeSet", net.minecraft.nbt.Tag.TAG_LIST);
        if (!hasRecipe && !hasReason && !hasPreset && tag.getInt("idleTime") == 0) {
            return null;
        }
        FactoryRecipeThread thread = new FactoryRecipeThread(factory);
        if (tag.contains("threadName")) thread.threadName = tag.getString("threadName");
        if (tag.contains("recipeSet", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag recipes = tag.getList("recipeSet", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < recipes.size(); i++) {
                String name = recipes.getString(i);
                if (!name.isBlank()) thread.recipeSet.add(name);
            }
        }
        thread.idleTime = tag.getInt("idleTime");
        if (hasReason) {
            thread.blockedReason = tag.getString("blockedReason");
        }
        if (tag.contains("triggeredRequirements", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            for (int index : tag.getIntArray("triggeredRequirements")) thread.triggeredRequirements.add(index);
        }
        if (tag.contains("activeCatalysts", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            for (int index : tag.getIntArray("activeCatalysts")) thread.activeCatalystRequirements.add(index);
        }
        if (hasRecipe) {
            thread.recipeName = tag.getString("recipeName");
            thread.ticks = tag.getInt("ticks");
            thread.duration = tag.getInt("duration");
            thread.parallelism = Math.max(1, tag.getInt("parallelism"));
            thread.maxParallelism = Math.max(1, tag.getInt("maxParallelism"));
            thread.isolationGroupKey = tag.contains("isolationGroupKey") ? tag.getString("isolationGroupKey") : null;
        }
        readModifiers(tag, "permanentModifiers", thread.permanentModifiers);
        readModifiers(tag, "semiPermanentModifiers", thread.semiPermanentModifiers);
        return thread;
    }

    private static void readModifiers(CompoundTag tag, String key, Map<String, RecipeModifier> target) {
        if (!tag.contains(key, net.minecraft.nbt.Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            RecipeModifier modifier = RecipeModifier.deserialize(entry.getCompound("modifier"));
            if (modifier != null && entry.contains("key")) target.put(entry.getString("key"), modifier);
        }
    }
}
