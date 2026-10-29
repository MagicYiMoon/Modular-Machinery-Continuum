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
 * original named above. Modifications made 2026-08 .. 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.machine.factory.FactoryControllerFamily;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


































 
public class TileFactoryController extends TileController {

    /**
     * The runs this controller owns -- upstream's {@code recipeThreadList} (U47). Named core runs are kept in
     * the same serialized list but are identified by their non-empty name and refreshed from
     * {@code DynamicMachine.getCoreThreadPresets()} on each factory tick. Their count is independent of the
     * dynamic {@code maxThreads} ceiling, matching upstream's separate {@code coreRecipeThreads} map.
     */
    private final List<FactoryRecipeThread> recipeThreads = new ArrayList<>();

    /** Existing work-only search/cleanup clock; independent of the source-compatible lifetime getter. */
    private long factoryWorkTicks;

    







 
    private int maxThreads;
    /** Source runtime delta to the dynamic pool; core presets remain independent. */
    private int extraThreadCount;

    /**
     * The machine's parallelism ceiling, mirrored once per tick by {@link FactoryTicker} -- upstream's
     * {@code getMaxParallelism()} on the controller, which its factory reads to compute the budget left for
     * another run (U400-U418). See {@link #maxThreads} for why it is mirrored rather than looked up.
     */
    private int machineParallelism = 1;
    /** Source-compatible factory search backoff; transient because it describes the current server tick. */
    private int recipeSearchRetryCounter;
    private long nextRecipeSearchTick;
    private boolean searchRecipeImmediately = true;
    private long recipeDefinitionRevision = -1;

    public TileFactoryController(BlockPos pos, BlockState state) {
        super(hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities.FACTORY_CONTROLLER, pos, state);
    }

    // ------------------------------------------------------------------
    // The contract with FactoryTicker
    // ------------------------------------------------------------------

    /** Advances the existing work-only scheduling clock, not the script's controller lifetime. */
    public void countFactoryTick() {
        this.factoryWorkTicks++;
    }

    public int currentRecipeSearchDelay() {
        int coreCount = 0;
        for (FactoryRecipeThread thread : recipeThreads) if (thread.isPresetThread()) coreCount++;
        if (coreCount == 0) return Math.min(20 + recipeSearchRetryCounter * 5, 100);
        return Math.min(20 + recipeSearchRetryCounter * Math.max(10 / coreCount, 1), 150);
    }

    public boolean isRecipeSearchDue() {
        return searchRecipeImmediately || factoryWorkTicks >= nextRecipeSearchTick;
    }

    public void recordRecipeSearchResult(boolean started) {
        searchRecipeImmediately = false;
        if (started) {
            recipeSearchRetryCounter = 0;
            nextRecipeSearchTick = factoryWorkTicks + 1;
        } else {
            recipeSearchRetryCounter = Math.min(recipeSearchRetryCounter + 1, 130);
            nextRecipeSearchTick = factoryWorkTicks + currentRecipeSearchDelay();
        }
    }

    /** Used by an event or a completion to wake the next search without waiting for backoff. */
    public void requestImmediateRecipeSearch() {
        searchRecipeImmediately = true;
    }

    /**
     * Clears a stale retry schedule after a lifecycle transition.
     *
     * <p>The source resets its recipe-search retry counter when a recipe starts and when the formed machine
     * is reset. This port also keeps a relative next-search tick, so both pieces must be cleared together when
     * the controller is formed/unformed or its machine binding changes.</p>
     */
    public void resetRecipeSearchState() {
        recipeSearchRetryCounter = 0;
        nextRecipeSearchTick = factoryWorkTicks;
        searchRecipeImmediately = true;
    }

    public int getRecipeSearchRetryCounter() { return recipeSearchRetryCounter; }

    /** Reloads can add a runnable recipe even when the machine's core-thread configuration is unchanged. */
    public void refreshRecipeSearchRevision(long revision) {
        if (recipeDefinitionRevision != revision) {
            recipeDefinitionRevision = revision;
            resetRecipeSearchState();
        }
    }

    /**
     * Takes this tick's copy of the machine's limits.
     *
     * @param maxThreads         definition's base dynamic capacity, before the runtime extra-thread delta
     * @param machineParallelism the machine's parallelism ceiling (upstream {@code getMaxParallelism()})
     */
    public void setFactoryCeiling(int maxThreads, int machineParallelism) {
        int threads = Math.max(0, maxThreads), parallel = Math.max(1, machineParallelism);
        if (this.maxThreads != threads || this.machineParallelism != parallel) {
            this.maxThreads = threads;
            this.machineParallelism = parallel;
            markForUpdate();
        }
    }

    /**
     * Drops threads that have been idle too long, on upstream's cadence.
     *
     * <p>Upstream: {@code cleanIdleTimeoutThread} (U514-U519), gated on {@code ticksExisted % 20} inside the
     * method itself, which is why the gate is here rather than at the caller -- the caller runs every tick.</p>
     */
    public void cleanIdleThreads() {
        if (factoryWorkTicks % 20 != 0) {
            return;
        }
        recipeThreads.removeIf(thread -> !thread.isPresetThread()
                && thread.isIdle() && thread.getIdleTime() >= FactoryRecipeThread.IDLE_TIME_OUT);
    }

    /**
     * Whether another run could be started here (upstream {@code hasIdleThread}, U521-U533).
     *
     * <p>Two ways to be true, and both are upstream's: the list is below the ceiling, or a thread in it has no
     * recipe (which is the case that keeps a factory running while a thread it started earlier idles).</p>
     */
    public boolean hasIdleThread() {
        for (FactoryRecipeThread thread : recipeThreads) {
            if (thread.isPresetThread() && thread.isIdle()) return true;
        }
        return hasIdleDynamicThread();
    }

    /** Upstream limits the dynamic pool independently of its always-present core threads. */
    private boolean hasIdleDynamicThread() {
        int count = 0, active = 0;
        for (FactoryRecipeThread thread : recipeThreads) {
            if (thread.isPresetThread()) continue;
            count++;
            if (thread.hasActiveRecipe()) active++;
        }
        int ceiling = getMaxThreads();
        return active < ceiling && (count < ceiling || active < count);
    }

    /**
     * Hands out an idle thread, creating one when the ceiling allows.
     *
     * @return a thread to run on, or {@code null} at the ceiling -- which is why the caller asks BEFORE taking
     *         anything, so a factory that cannot run a craft leaves the machine exactly as it found it
     */
    public FactoryRecipeThread claimThread() {
        if (!hasIdleDynamicThread()) return null;
        for (FactoryRecipeThread thread : recipeThreads) {
            if (!thread.isPresetThread() && thread.isIdle()) {
                return thread;
            }
        }
        FactoryRecipeThread thread = new FactoryRecipeThread(this);
        recipeThreads.add(thread);
        return thread;
    }

    /** Ensures source-style named core threads exist after a machine definition reload. */
    public void syncCoreThreadPresets(Map<String, List<String>> presets) {
        syncCoreThreadPresets(presets, Map.of(), Map.of());
    }

    public void syncCoreThreadPresets(Map<String, List<String>> presets,
                                      Map<String, Map<String, hellfirepvp.modularmachinery.common.modifier.RecipeModifier>> permanent,
                                      Map<String, Map<String, hellfirepvp.modularmachinery.common.modifier.RecipeModifier>> semiPermanent) {
        if (presets == null) return;
        // Like upstream updateCoreThread, removal also removes a running core job.
        boolean changed = recipeThreads.removeIf(thread -> thread.isPresetThread() && !presets.containsKey(thread.getThreadName()));
        for (Map.Entry<String, List<String>> entry : presets.entrySet()) {
            FactoryRecipeThread found = null;
            for (FactoryRecipeThread thread : recipeThreads) {
                if (entry.getKey().equals(thread.getThreadName())) {
                    found = thread;
                    break;
                }
            }
            if (found != null) {
                if (!found.getRecipeSet().equals(new java.util.LinkedHashSet<>(entry.getValue()))) changed = true;
                // Refresh the search set even while running; this must not reset paid progress.
                found.configurePreset(entry.getKey(), entry.getValue(), permanent.get(entry.getKey()), semiPermanent.get(entry.getKey()));
                continue;
            }
            FactoryRecipeThread created = new FactoryRecipeThread(this);
            created.configurePreset(entry.getKey(), entry.getValue(), permanent.get(entry.getKey()), semiPermanent.get(entry.getKey()));
            recipeThreads.add(created);
            changed = true;
        }
        // The periodic ticker sync is skipped when the last thread disappears.
        if (changed) markForUpdate();
        if (changed) requestImmediateRecipeSearch();
    }

    /** Finds an idle thread that can run this recipe, creating a dynamic thread when allowed. */
    public FactoryRecipeThread claimThreadFor(hellfirepvp.modularmachinery.common.crafting.MachineRecipe recipe) {
        if (recipe == null) return null;
        FactoryRecipeThread existing = findIdleThreadFor(recipe);
        if (existing != null) return existing;
        // A recipe pinned to a core thread must never spill into an unnamed dynamic thread.
        if (!recipe.matchesThreadName("")) return null;
        return claimThread();
    }

    /** Select without allocating or marking an idle factory dirty when no candidate can be paid for. */
    FactoryRecipeThread findIdleThreadFor(hellfirepvp.modularmachinery.common.crafting.MachineRecipe recipe) {
        if (recipe == null) return null;
        for (FactoryRecipeThread thread : getFactoryRecipeThreadList()) {
            if (!thread.isPresetThread() && !hasIdleDynamicThread()) continue;
            if (thread.isIdle() && thread.acceptsRecipe(recipe)) return thread;
        }
        return null;
    }

    /** @return whether this recipe has an idle compatible thread or room for a dynamic one */
    public boolean hasIdleThreadFor(hellfirepvp.modularmachinery.common.crafting.MachineRecipe recipe) {
        if (recipe == null) return false;
        for (FactoryRecipeThread thread : recipeThreads) {
            if (!thread.isPresetThread() && !hasIdleDynamicThread()) continue;
            if (thread.isIdle() && thread.acceptsRecipe(recipe)) return true;
        }
        return recipe.matchesThreadName("") && hasIdleDynamicThread();
    }

    /**
     * The run list, for the GUI and the family.
     *
     * <p>Upstream's {@code getFactoryRecipeThreadList()} (U388-U390) is read by its own factory GUI
     * ({@code GuiFactoryController.drawRecipeQueue} iterates it) and by the scrollbar arithmetic. THE LIVE LIST
     * IS NOT HANDED OUT: it is mutated by the ticker while a screen iterates it, and a copy costs one
     * allocation per frame on a screen a player has open, which is the cheaper side of that trade.</p>
     */
    public List<FactoryRecipeThread> getFactoryRecipeThreadList() {
        List<FactoryRecipeThread> ordered = new ArrayList<>(recipeThreads.size());
        for (FactoryRecipeThread thread : recipeThreads) if (thread.isPresetThread()) ordered.add(thread);
        for (FactoryRecipeThread thread : recipeThreads) if (!thread.isPresetThread()) ordered.add(thread);
        return List.copyOf(ordered);
    }

    @Override
    public hellfirepvp.modularmachinery.common.machine.RecipeThread[] getRecipeThreadList() {
        return getFactoryRecipeThreadList().stream()
                .map(thread -> new hellfirepvp.modularmachinery.common.machine.RecipeThread(this, thread))
                .toArray(hellfirepvp.modularmachinery.common.machine.RecipeThread[]::new);
    }

    @Override public void addModifier(String key, hellfirepvp.modularmachinery.common.modifier.RecipeModifier modifier) {
        if (!canChangeRuntimeModifiers()) return;
        for (FactoryRecipeThread thread : getFactoryRecipeThreadList()) thread.addSemiPermanentModifier(key, modifier);
    }
    @Override public void removeModifier(String key) {
        if (!canChangeRuntimeModifiers()) return;
        for (FactoryRecipeThread thread : getFactoryRecipeThreadList()) thread.removeSemiPermanentModifier(key);
    }
    @Override protected void refreshRuntimeModifierContexts() {
        super.refreshRuntimeModifierContexts();
        for (FactoryRecipeThread thread : getFactoryRecipeThreadList()) {
            var run = thread.getRuntimeRecipeContext();
            if (run != null) thread.observeRuntimeRecipeContext(MachineTicker.refreshRuntimeModifiers(this, thread, run));
        }
        requestImmediateRecipeSearch();
    }

    /** @return how many runs exist, running or not (upstream's {@code getFactoryRecipeThreadList().size()}) */
    public int getThreadCount() {
        return recipeThreads.size();
    }

    /** Core presets have their own slots and do not count against the dynamic-thread ceiling. */
    public int getDynamicThreadCount() {
        int count = 0;
        for (FactoryRecipeThread thread : recipeThreads) if (!thread.isPresetThread()) count++;
        return count;
    }

    public java.util.List<net.minecraft.network.chat.Component> factoryParallelismReadoutLines() {
        int parallel = getFactoryParallelism();
        if (parallel <= 1 || getFactoryMaxParallelism() <= 1) return java.util.List.of();
        return java.util.List.of(
                net.minecraft.network.chat.Component.translatable(PARALLELISM_KEY, parallel),
                net.minecraft.network.chat.Component.translatable(MAX_PARALLELISM_KEY, getFactoryMaxParallelism()));
    }

    /** @return how many runs are advancing, for the family's "threads running" figure */
    public int getRunningThreadCount() {
        int running = 0;
        for (FactoryRecipeThread thread : recipeThreads) {
            if (thread.isWorking()) {
                running++;
            }
        }
        return running;
    }

    /** Source isWorking checks every core and dynamic thread, not only the first occupied run. */
    @Override
    public boolean isWorking() {
        return isFormed() && !isRedstoneStopped() && getRunningThreadCount() > 0;
    }

    /** @return the base dynamic capacity plus the runtime delta, clamped to a nonnegative int */
    public int getMaxThreads() {
        // Keep the signed source delta, but do not let addition wrap the scheduling capacity.
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) maxThreads + extraThreadCount));
    }

    @Override public int getExtraThreadCount() { return extraThreadCount; }

    @Override public void setExtraThreadCount(int value) {
        if (!(getIWorld() instanceof net.minecraft.server.level.ServerLevel) || extraThreadCount == value) return;
        extraThreadCount = value;
        requestImmediateRecipeSearch();
        markForUpdate();
    }

    /** Controller-wide batch budget; getMaxParallelism() describes only the first occupied thread. */
    public int getFactoryMaxParallelism() {
        return machineParallelism;
    }

    /** Batch budget used by all occupied threads, including stalled ones. */
    public int getFactoryParallelism() {
        long used = 1;
        for (FactoryRecipeThread thread : recipeThreads) {
            if (thread.hasActiveRecipe()) used += Math.max(0, thread.getParallelism() - 1);
        }
        return (int) Math.min(Integer.MAX_VALUE, used);
    }

    /** Counts occupied jobs, including paused jobs and jobs waiting for output space. */
    public int getActiveThreadCount() {
        int active = 0;
        for (FactoryRecipeThread thread : recipeThreads) if (thread.hasActiveRecipe()) active++;
        return active;
    }

    /**
     * How much of the machine's parallelism is still unspent (upstream {@code getAvailableParallelism},
     * U400-U418).
     *
     * <p>Subtracting {@code parallelism - 1} per running run is upstream's arithmetic and it is not a
     * simplification: the first copy of a craft is what the machine's own ceiling already accounted for, so a
     * run at parallelism 8 has consumed 7 of the budget. The floor of 1 is upstream's too, and it is what keeps
     * the LAST run startable when the budget is exactly spent.</p>
     */
    public int getAvailableParallelism() {
        if (!hasIdleThread()) return 0;
        int available = machineParallelism;
        for (FactoryRecipeThread thread : recipeThreads) {
            if (!thread.hasActiveRecipe()) {
                continue;
            }
            available -= (thread.getParallelism() - 1);
        }
        return Math.max(1, available);
    }

    /** Drops every run. The inputs are already consumed; see TileController#abandonRecipe. */
    public void abandonAllRecipes() {
        for (FactoryRecipeThread thread : recipeThreads) {
            thread.abandonRecipe();
        }
    }

    // ------------------------------------------------------------------
    // What a GUI, a HUD or a data slot reads -- upstream's activeRecipe == activeRecipes[0]
    // ------------------------------------------------------------------

    /** @return the first run in thread order that has a recipe, or {@code null} (upstream U675-U699) */
    private FactoryRecipeThread firstActiveThread() {
        for (FactoryRecipeThread thread : recipeThreads) {
            if (thread.hasActiveRecipe()) {
                return thread;
            }
        }
        return null;
    }

    @Override
    public boolean hasActiveRecipe() {
        return firstActiveThread() != null;
    }

    @Override
    public String getRecipeName() {
        FactoryRecipeThread thread = firstActiveThread();
        return thread == null ? null : thread.getRecipeName();
    }

    @Override
    public int getRecipeTicks() {
        FactoryRecipeThread thread = firstActiveThread();
        return thread == null ? 0 : thread.getRecipeTicks();
    }

    @Override
    public int getRecipeDuration() {
        FactoryRecipeThread thread = firstActiveThread();
        return thread == null ? 0 : thread.getRecipeDuration();
    }

    @Override
    public int getParallelism() {
        FactoryRecipeThread thread = firstActiveThread();
        return thread == null ? 1 : thread.getParallelism();
    }

    @Override
    public int getMaxParallelism() {
        FactoryRecipeThread thread = firstActiveThread();
        return thread == null ? 1 : thread.getMaxParallelism();
    }

    
















 
    @Override
    public boolean isCrafting() {
        FactoryRecipeThread thread = firstActiveThread();
        return thread != null && thread.isWorking();
    }

    // ------------------------------------------------------------------
    // Family bookkeeping
    // ------------------------------------------------------------------

    /**
     * Puts this controller in its machine's family, if it is formed and bound.
     *
     * <p>CLIENT COPIES ARE EXCLUDED, and not for tidiness: a client block entity is a different object in a
     * different level, and registering it would make the server's family counts depend on which chunks a
     * player had loaded.</p>
     */
    public void syncFamily() {
        if (level == null || level.isClientSide() || !isFormed()) {
            return;
        }
        FactoryControllerFamily.register(this);
    }

    @Override
    public void setFormed(boolean formed) {
        boolean changed = isFormed() != formed;
        if (!formed) {
            if (extraThreadCount != 0) {
                extraThreadCount = 0; // Independent of the custom-data clearing option, as in resetMachine(true).
                markForUpdate();
            }
            FactoryControllerFamily.unregister(this);
            for (FactoryRecipeThread thread : recipeThreads) thread.observeRuntimeRecipeContext(null);
        }
        super.setFormed(formed);
        if (changed) resetRecipeSearchState();
        if (formed) {
            syncFamily();
        }
    }

    @Override
    public void setMachineName(String machineName) {
        boolean changed = !java.util.Objects.equals(getMachineName(), machineName);
        // BEFORE the binding changes: the family is keyed by the name the controller is leaving.
        FactoryControllerFamily.unregister(this);
        super.setMachineName(machineName);
        if (changed) resetRecipeSearchState();
    }

    @Override
    public void syncMachineFromBlueprint() {
        String previous = getMachineName();
        super.syncMachineFromBlueprint();
        if (!java.util.Objects.equals(previous, getMachineName())) resetRecipeSearchState();
    }

    @Override
    public void setRemoved() {
        FactoryControllerFamily.unregister(this);
        super.setRemoved();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Reads the runs back (upstream {@code readCustomNBT}, U578-U621, which reads a {@code threadList} of
     * serialized threads).
     *
     * <p>The list is CLEARED FIRST, which upstream does as well (U593-U594): a controller that is loaded twice
     * -- a world save followed by a chunk reload -- would otherwise concatenate both sets of runs and report
     * twice the work.</p>
     */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        recipeThreads.clear();
        if (tag.contains("threadList", Tag.TAG_LIST)) {
            ListTag list = tag.getList("threadList", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FactoryRecipeThread thread = FactoryRecipeThread.deserialize(list.getCompound(i), this);
                if (thread != null) {
                    recipeThreads.add(thread);
                }
            }
        }
        // The client displays this snapshot; the server refreshes it from the definition before scheduling.
        this.maxThreads = Math.max(0, tag.getInt("factoryMaxThreads"));
        this.extraThreadCount = tag.getInt("extraThreadCount");
        this.machineParallelism = Math.max(1, tag.getInt("factoryMaxParallelism"));
        this.recipeSearchRetryCounter = 0;
        this.nextRecipeSearchTick = 0;
        this.searchRecipeImmediately = true;
        this.recipeDefinitionRevision = -1;
    }

    /**
     * Writes the runs out (upstream {@code writeCustomNBT}, U638-U660).
     *
     * <p>The limits accompany the existing client update tag. On the server they are refreshed from the
     * live machine definition before scheduling, so saved limits cannot override an edited definition.</p>
     */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("factoryMaxThreads", maxThreads);
        tag.putInt("extraThreadCount", extraThreadCount);
        tag.putInt("factoryMaxParallelism", machineParallelism);
        if (!recipeThreads.isEmpty()) {
            ListTag list = new ListTag();
            for (FactoryRecipeThread thread : recipeThreads) {
                list.add(thread.serialize());
            }
            tag.put("threadList", list);
        }
    }
}
