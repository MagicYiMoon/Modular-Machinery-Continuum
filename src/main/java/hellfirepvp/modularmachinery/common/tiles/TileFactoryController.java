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

/**
 * Factory controller block entity: MANY recipe runs on ONE structure.
 *
 * <p>This was a stub until factory mode landed (PLAN 662). The block, the menu and the screen already existed,
 * so what was missing was the thing the class is named after: a controller that holds more than one run. A
 * normal {@link TileController} carries ONE craft's state in its own fields; this one carries a LIST of
 * {@link FactoryRecipeThread}s and is driven by {@link FactoryTicker} instead of {@link MachineTicker}.</p>
 *
 * <h2>What the single-craft fields inherited from TileController now mean</h2>
 *
 * <p>THEY ARE NOT USED, AND THE READERS ARE OVERRIDDEN RATHER THAN LEFT TO LIE. A factory controller never
 * calls {@code beginRecipe}; the inherited {@code recipeName}/{@code recipeTicks}/{@code recipeDuration}
 * therefore stay null/0, and a GUI reading them would show a factory that is always idle however much work it
 * is doing. So {@code hasActiveRecipe}, {@code getRecipeName}, {@code getRecipeTicks},
 * {@code getRecipeDuration}, {@code getParallelism} and {@code getMaxParallelism} report the FIRST RUNNING RUN
 * -- which is upstream's own answer, since its {@code getActiveRecipe()} returns
 * {@code getActiveRecipeList()[0]} and that list is built in thread order (upstream TileFactoryController
 * U675-U699). The MVC data slots the machine screen reads
 * ({@code MenuMachineController.liveData}) therefore show a factory's first run without the menu knowing
 * anything changed.</p>
 *
 * <p>A SECOND ENGINE CANNOT PICK THIS TILE UP BY ACCIDENT: {@code MachineTicker.tick} refuses a
 * {@code TileFactoryController} outright, because a tile that reports its state from its threads would make the
 * single-craft engine find "idle", take a craft and consume its inputs on EVERY tick without ever appearing to
 * run one. The block hands out this class's own ticker (see {@code BlockFactoryController.getTicker}).</p>
 *
 * <h2>Family bookkeeping</h2>
 *
 * <p>{@link FactoryControllerFamily} holds the live controllers per machine definition -- the port's answer to
 * upstream's {@code BlockFactoryController.FACTORY_CONTROLLERS}, for the reason recorded on that class (a
 * block per machine cannot be registered on 1.20.1, because the registries are frozen long before this port
 * reads its machine definitions). Registration follows this tile's own lifecycle: on formation, on a machine
 * binding change, and on every factory tick; removal on {@code setRemoved} and on unforming.</p>
 */
public class TileFactoryController extends TileController {

    /**
     * The runs this controller owns -- upstream's {@code recipeThreadList} (U47). Named core runs are kept in
     * the same serialized list but are identified by their non-empty name and refreshed from
     * {@code DynamicMachine.getCoreThreadPresets()} on each factory tick. Their count is independent of the
     * dynamic {@code maxThreads} ceiling, matching upstream's separate {@code coreRecipeThreads} map.
     */
    private final List<FactoryRecipeThread> recipeThreads = new ArrayList<>();

    /**
     * Ticks this controller has been alive, counted by the ticker.
     *
     * <p>Upstream keeps {@code ticksExisted} on the tile and gates two behaviours on it: the core-thread refresh
     * every 20 ticks (U484-U486) and the idle-thread cleanup (U515-U517). The port's single-craft ticker uses
     * {@code level.getGameTime()} for its one cadence check instead -- but that is NOT usable for the factory,
     * because a self-test drives ticks without the world's clock moving, and a cadence nothing can reach is a
     * branch nothing can verify (PLAN 338 recorded exactly that trap). A counter on the tile is driven by the
     * caller of the tick and therefore works in the harness.</p>
     */
    private long ticksExisted;

    /**
     * The machine definition's factory thread ceiling, mirrored once per tick by {@link FactoryTicker}.
     *
     * <p>Upstream reads {@code foundMachine.getMaxThreads()} on demand (U455-U457) because its tile holds the
     * machine object. This port's tile deliberately does not hold the definition -- the definition is resolved
     * by the ticker from the reload listener, and a tile that reached for it would be PLAN 599.2's dependency
     * direction reversed. Zero means "no tick has happened yet", which is honest: the ceiling belongs to the
     * definition, and until one is bound there is nothing to run.</p>
     */
    private int maxThreads;

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

    /** Counts this tick. See {@link #ticksExisted} for what reads it. */
    public void countFactoryTick() {
        this.ticksExisted++;
    }

    public int currentRecipeSearchDelay() {
        int coreCount = 0;
        for (FactoryRecipeThread thread : recipeThreads) if (thread.isPresetThread()) coreCount++;
        if (coreCount == 0) return Math.min(20 + recipeSearchRetryCounter * 5, 100);
        return Math.min(20 + recipeSearchRetryCounter * Math.max(10 / coreCount, 1), 150);
    }

    public boolean isRecipeSearchDue() {
        return searchRecipeImmediately || ticksExisted >= nextRecipeSearchTick;
    }

    public void recordRecipeSearchResult(boolean started) {
        searchRecipeImmediately = false;
        if (started) {
            recipeSearchRetryCounter = 0;
            nextRecipeSearchTick = ticksExisted + 1;
        } else {
            recipeSearchRetryCounter = Math.min(recipeSearchRetryCounter + 1, 130);
            nextRecipeSearchTick = ticksExisted + currentRecipeSearchDelay();
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
        nextRecipeSearchTick = ticksExisted;
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
     * @param maxThreads         how many runs may exist at once (upstream {@code getMaxThreads()}, U455-U457)
     * @param machineParallelism the machine's parallelism ceiling (upstream {@code getMaxParallelism()})
     */
    public void setFactoryCeiling(int maxThreads, int machineParallelism) {
        this.maxThreads = Math.max(0, maxThreads);
        this.machineParallelism = Math.max(1, machineParallelism);
    }

    /**
     * Drops threads that have been idle too long, on upstream's cadence.
     *
     * <p>Upstream: {@code cleanIdleTimeoutThread} (U514-U519), gated on {@code ticksExisted % 20} inside the
     * method itself, which is why the gate is here rather than at the caller -- the caller runs every tick.</p>
     */
    public void cleanIdleThreads() {
        if (ticksExisted % 20 != 0) {
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
        return active < maxThreads && (count < maxThreads || active < count);
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
        for (FactoryRecipeThread thread : getFactoryRecipeThreadList()) {
            if (!thread.isPresetThread() && !hasIdleDynamicThread()) continue;
            if (thread.isIdle() && thread.acceptsRecipe(recipe)) return thread;
        }
        // A recipe pinned to a core thread must never spill into an unnamed dynamic thread.
        if (!recipe.matchesThreadName("")) return null;
        return claimThread();
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

    /** @return how many runs exist, running or not (upstream's {@code getFactoryRecipeThreadList().size()}) */
    public int getThreadCount() {
        return recipeThreads.size();
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

    /** @return the mirrored ceiling; see {@link #maxThreads} */
    public int getMaxThreads() {
        return maxThreads;
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

    /**
     * Whether the run this tile REPORTS is advancing — upstream's per-thread {@code thread.getStatus()
     * .isCrafting()}, which its TheOneProbe provider asks at {@code MMInfoProvider} U174 to choose between the
     * running colours and the three FAILURE colours (U171-U181), and which its factory GUI asks at
     * {@code GuiFactoryController} U126.
     *
     * <p><b>IT IS OVERRIDDEN FOR THE SAME REASON {@code getRecipeTicks} IS (PLAN 711).</b> The inherited
     * single-craft fields stay unused here, so the inherited {@code TileController.isCrafting()} would answer
     * from {@code statusMessage} — a field this tile never writes, because a factory run's reason lives on its
     * thread — and would therefore report "crafting" for a run the engine has already HELD. That is the exact
     * defect the HUD is being fixed for, one layer down.</p>
     *
     * <p><b>NO NEW SIGNAL.</b> The question is put to the same run whose progress this tile reports
     * ({@link #firstActiveThread}, the walk upstream gets from {@code getActiveRecipeList()[0]}) through the
     * same predicate the engine's reason feeds ({@link FactoryRecipeThread#isWorking()}, i.e.
     * {@code hasActiveRecipe() && blockedReason == null}, written by {@code FactoryTicker} at U479, U493 and
     * U526). The reason, the writer and the predicate are all pre-existing; this method only names the run.</p>
     */
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
            FactoryControllerFamily.unregister(this);
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
        // A LOADED CONTROLLER IS NOT TICKED YET, so the mirrored ceiling stays at zero until the ticker hands
        // one over -- and a run restored from NBT therefore cannot be mistaken for one that may start on the
        // strength of a ceiling nothing has read.
        this.maxThreads = 0;
        this.recipeSearchRetryCounter = 0;
        this.nextRecipeSearchTick = 0;
        this.searchRecipeImmediately = true;
        this.recipeDefinitionRevision = -1;
    }

    /**
     * Writes the runs out (upstream {@code writeCustomNBT}, U638-U660).
     *
     * <p>Upstream also writes its {@code status} and {@code totalParallelism}; this port's status is the runs
     * themselves (there is no CraftingStatus to write -- PLAN 521) and the parallelism ceiling is the machine
     * definition's, which is read back from the definition rather than persisted (persisting it would let a
     * saved ceiling outlive the machine file that declared it).</p>
     */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!recipeThreads.isEmpty()) {
            ListTag list = new ListTag();
            for (FactoryRecipeThread thread : recipeThreads) {
                list.add(thread.serialize());
            }
            tag.put("threadList", list);
        }
    }
}
