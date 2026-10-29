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
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.MachineTickEvent;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.FactoryRecipeFailureEvent;
import hellfirepvp.modularmachinery.common.event.recipe.FactoryRecipeFinishEvent;
import hellfirepvp.modularmachinery.common.event.recipe.FactoryRecipeStartEvent;
import hellfirepvp.modularmachinery.common.event.recipe.FactoryRecipeTickEvent;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;






























































 
public final class FactoryTicker {

    private FactoryTicker() {
    }

    /** Diagnostic gate, matching MachineTicker's. */
    private static final boolean TRACE = Boolean.getBoolean("modularmachinery.selfTest");

    private static void trace(String message, Object... args) {
        if (TRACE) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.info("[MMCE][factory] " + message, args);
        }
    }

    /**
     * Advances a factory controller one tick.
     *
     * <p>The guards, and their upstream counterparts, in order: client side (no authoritative state),
     * unformed (drop the runs -- see below), redstone (upstream returns before doing anything at
     * TileFactoryController L76-U79, a PAUSE rather than a teardown), no machine bound (nothing to run),
     * component re-bind after a world load, the dynamic-pattern cadence check.</p>
     *
     * @param level      the server level -- never call this off the server thread
     * @param pos        the controller position
     * @param controller the factory controller block entity
     */
    public static void tick(Level level, BlockPos pos, TileFactoryController controller) {
        if (level.isClientSide()) return;
        long started = System.nanoTime();
        try {
            controller.flushPendingComponentUnbinds();
            tickController(level, pos, controller);
            controller.countControllerTick();
        }
        finally { controller.recordPerformanceNanos(System.nanoTime() - started); }
    }

    private static void tickController(Level level, BlockPos pos, TileFactoryController controller) {
        if (level.isClientSide()) {
            return;
        }
        if (!controller.isFormed()) {
            // THE RUNS ARE DROPPED, NOT PAUSED, and the difference is the player's items: a factory that came
            // apart has already consumed every run's inputs, and a run that can never emit would occupy its
            // thread forever. This is MachineTicker's unformed branch, applied per thread.
            if (controller.hasActiveRecipe()) {
                trace("not formed -- abandoning {} run(s) at {}", controller.getRunningThreadCount(), pos);
                controller.abandonAllRecipes();
                controller.markForUpdate();
            }
            MachineTicker.tryAutomaticFormation(level, pos, controller);
            return;
        }
        // PAUSE, NOT ABANDON: returning here leaves every run and its progress untouched, and the factory
        // resumes when the signal drops (upstream U76-U79 returns the same way).
        if (controller.isRedstoneStopped()) {
            return;
        }

        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(controller.getMachineName());
        if (machine == null) {
            return;
        }
        controller.refreshRecipeSearchRevision(MachineReloadListener.getServerDefinitionRevision());

         
        if (controller.consumeRebindRequest()) {
            java.util.List<BlockPos> rebound = hellfirepvp.modularmachinery.common.machine.MachineFormation
                    .rebind((net.minecraft.server.level.ServerLevel) level, pos,
                            MachineTicker.facingOf(controller), machine, loader);
            controller.setBoundPositions(rebound);
            trace("re-bound {} component(s) after a world load", rebound.size());
        }

        Direction facing = MachineTicker.facingOf(controller);
        // THE STRUCTURE RE-CHECK, ON THE SAME CADENCE AND THROUGH THE SAME CALL AS THE NORMAL CONTROLLER
         
        // the SHARED base TileMultiblockMachineController (U533-U559), and both TileMachineController.U116-U118
        // and TileFactoryController.U126-U128 call it from their own doAsyncStep. So a factory whose casing is
        // broken has to stop exactly like a single-craft machine, and leaving this out would have been a silent
        // half-fix -- the defect the user reported still present on every factory controller.
        //
        // MachineTicker.structureStillHolds is shared rather than copied for the rule-B reason: one structure
        // walk, one cadence, one unform event.
        if (controller.isStructureCheckRequested() || MachineTicker.structureCheckDue(level)) {
            if (!MachineTicker.structureStillHolds(level, pos, facing, machine, loader, controller)) {
                return;
            }
        }
        if (!machine.getDynamicPatterns().isEmpty() && level.getGameTime() % 20 == 0) {
            if (!MachineTicker.dynamicPatternsStillHold(machine, level, pos, facing, loader, controller)) {
                return;
            }
        }

         
        // busses must see them, and the factory is the second reader of one rule rather than a second rule.
        MachineContext context = new MachineContext(
                (net.minecraft.server.level.ServerLevel) level, pos, facing, machine, loader.getVariables(),
                controller.getFormedPattern(machine));
        if (!controller.getBoundPositions().isEmpty()) context.overrideComponentPositions(controller.getBoundPositions());

        List<MachineRecipe> candidates = MachineReloadListener.getRecipeLoader()
                .getByMachine().get(machine.getRegistryName());
        if (candidates == null) {
            // NOT an early return, unlike the normal controller: a factory with a run in flight still has to
            // tick it, and MachineTicker.findByName returning null is what abandons a run whose recipe a
            // /reload removed (MachineTicker.advance's first branch).
            candidates = List.of();
        }

        // FAMILY BOOKKEEPING, written on the tick rather than only on formation: a controller that formed and
        // then had its blueprint swapped is registered under the name it now carries.
        controller.syncFamily();

        // THE MACHINE TICK BRACKET, at upstream's factory position (TileFactoryController U133/U156:
        // onMachineTick(START) ... work ... onMachineTick(END)) and around the WORK rather than around the
         
        new MachineTickEvent(controller, Phase.START).postEvent();
        try {
            controller.countFactoryTick();
            // THE CEILING AND THE PARALLELISM BUDGET ARE MIRRORED ONCE PER TICK (upstream reads
            // foundMachine.getMaxThreads() and getMaxParallelism() directly; this port's tile holds no
             
            controller.setFactoryCeiling(machine.getMaxThreads(), MachineTicker.machineParallelism(machine, context));
            controller.syncCoreThreadPresets(machine.getCoreThreadPresets(), machine.getCoreThreadPermanentModifiers(),
                    machine.getCoreThreadSemiPermanentModifiers());
            controller.cleanIdleThreads();

            // ONE NEW RUN PER TICK, which is the same rate upstream's single in-flight search achieves
            // (TileFactoryController L322-U359 starts one craft per search result, and a result arrives at
            // most once per tick). TWO CRAFTS THEREFORE START OVER TWO TICKS AND THEN RUN TOGETHER, which is
            // the whole observable meaning of factory mode.
            if (controller.hasIdleThread() && controller.isRecipeSearchDue()) {
                long searchStarted = System.nanoTime();
                int activeBeforeSearch = controller.getActiveThreadCount();
                if (candidates.isEmpty()) {
                    controller.recordRecipeSearchResult(false);
                } else {
                    startOneRecipe(controller, machine, context, candidates);
                    controller.recordRecipeSearchResult(controller.getActiveThreadCount() > activeBeforeSearch);
                }
                controller.recordRecipeSearchNanos(System.nanoTime() - searchStarted);
            }

             
            //
            // UPSTREAM'S CADENCE, named exactly: doSyncStep calls markNoUpdateSync() on EVERY tick in which the
            // factory has any thread at all (TileFactoryController U145-U148), and that call is what makes its
            // GUI's per-thread progress advance. This port called markForUpdate() only on the four TRANSITIONS
            // below (start, finish, blocked-reason change), so a screen reading the tile's runs would have shown
            // every progress bar frozen at the value it held when its run started -- and progress is precisely
            // what the factory screen is for.
            //
            // ONE DATA PACKET PER TICK PER CONTROLLER THAT HAS THREADS, and none at all when it has none
            // (upstream's own condition), with no block update in it -- which is upstream's own split between
            // its two sync calls (TileEntitySynchronized U102 at L102-U105 and markNoUpdateSync at U117-U123).
            if (controller.getThreadCount() > 0) {
                controller.markNoUpdateSync();
            }

            for (FactoryRecipeThread thread : new ArrayList<>(controller.getFactoryRecipeThreadList())) {
                tickThread(thread, controller, machine, context, candidates);
            }
        } finally {
            new MachineTickEvent(controller, Phase.END).postEvent();
        }
    }

    // ------------------------------------------------------------------
    // Starting one run
    // ------------------------------------------------------------------

    /**
     * Tries to start ONE new run on an idle thread, in the order upstream's search does.
     *
     * <p>The order is upstream's ({@code FactoryRecipeSearchTask.computeTask} U50-U76 with
     * {@code canCheck} U89-U99, then {@code TileFactoryController.offerRecipe} U432-U453): walk the machine's
     * recipes; skip the ones that cannot run; ask whether this one is satisfied NOW, including whether it
     * could finish; decide its parallelism; then take the inputs and hand it to a thread.</p>
     */
    private static void startOneRecipe(TileFactoryController controller, DynamicMachine machine,
                                       MachineContext context, List<MachineRecipe> candidates) {
        if (!controller.hasIdleThread()) return;
        int available = controller.getAvailableParallelism();
        if (available <= 0) {
            return;
        }
        // WHAT THE FACTORY IS ALREADY RUNNING, counted per recipe -- upstream's `runningRecipes`
        // (FactoryRecipeSearchTask U16, built at U29-U33 from factory.getActiveRecipeList(), which is every
        // thread with an active recipe: TileFactoryController U684-U699, CORE THREADS FIRST and both lists
        // unfiltered by whether the run is crafting). A run that is HELD -- starved, or complete with no room
        // for its output -- therefore still occupies its recipe's slot, which is upstream's behaviour and not
        // an accident of the port's.
        //
        // IT IS TAKEN ONCE PER SEARCH, which is upstream's placement (the task's constructor) and also the
        // port's: startOneRecipe starts at most one run, so nothing it starts can be missing from this count.
        java.util.Map<String, Integer> runningRecipes = runningRecipeCounts(controller);
        for (MachineRecipe recipe : candidates) {
            if (!canCheck(recipe, runningRecipes, controller)) {
                continue;
            }
            if (!MachineTicker.isRunnable(recipe)) {
                continue;
            }
            // Resolve against the chosen thread's modifiers before checking or paying. A prospective new
            // dynamic thread has no modifiers yet; allocate it only after the checks succeed, preserving
            // the idle factory's no-update behavior when every candidate lacks materials.
            FactoryRecipeThread thread = controller.findIdleThreadFor(recipe);
            RecipeCraftingContext run = MachineTicker.resolvedRun(machine, recipe, context,
                    thread == null ? List.of() : thread.activeModifiers());
            if (!MachineTicker.satisfies(recipe, run, context, true)) {
                continue;
            }
            if (thread == null) thread = controller.claimThreadFor(recipe);
            if (thread == null) {
                trace("no idle thread at the ceiling of {}", controller.getMaxThreads());
                return;
            }
            // The context is still pinned to the group selected by the successful check while inputs are drawn.
            // FactoryRecipeThread.beginRecipe clears transient state, so persist the selected group immediately
            // after beginRecipe below rather than before it.
            context.selectIsolationGroup(run.getSelectedIsolationGroupKey());
            // THE EFFECTIVE PARALLELISM COMES FROM THE FACTORY'S REMAINING BUDGET, NOT FROM THE MACHINE'S
            // WHOLE ONE (upstream passes getAvailableParallelism() into the search task, L465-U470), and it is
            // still min-reduced against the requirements, so a second run cannot be granted parallelism the
            // first one has already spent.
             
            // reduced against its scaled amount and paid for at the scaled amount, so what the check demanded
            // is what the run costs -- and `effectiveParallelism` writes the granted value into THE LIST THE
            // WORK PATH READS, which is the only reason the fluid half of the emission knows about it.
            // Delayed inputs are paid at their trigger tick, exactly as on an ordinary controller.
            List<Requirement> immediate = run.getRequirements().stream().filter(r -> r.getTriggerTime() <= 0).toList();
            int parallelism = MachineTicker.effectiveParallelism(recipe, immediate, context, available);
            if (parallelism <= 0) {
                continue;
            }
             
            // a craft whose inputs cannot be taken must leave the machine exactly as it found it.
            if (!MachineTicker.consumeInputs(immediate, context, parallelism)) {
                trace("recipe '{}' passed its checks but its inputs could not be taken", recipe.registryName());
                continue;
            }
            thread.setRecipe(recipe);
             
            // gap): upstream scales the recipe's tick time with the duration modifiers for a factory run exactly
            // as it does for a single one (the run holds its own ActiveMachineRecipe), and MachineTicker
            // .effectiveRecipeDuration is where that arithmetic and the ONE active-modifier scan now live.
            thread.beginRecipe(recipe.registryName(), run.effectiveDuration(), parallelism);
            thread.setActiveCatalystRequirements(run.activeCatalystIndices());
            thread.setIsolationGroupKey(run.getSelectedIsolationGroupKey());
            thread.observeRuntimeRecipeContext(run);
            recipe.commands().runStartCommands(controller);

            // RECIPE START, at upstream's point (TileFactoryController.onThreadRecipeStart U275 fires it
            // BEFORE activeRecipe.start): the handler observes a run that has a recipe and no progress yet.
            // The port takes the inputs one line above rather than inside activeRecipe.start, which is the
            // port's own consumption model (MachineTicker.tryStart consumes before beginRecipe for the same
             
            // difference is stated rather than implied.
            new FactoryRecipeStartEvent(thread, controller, run).postEvent();
            controller.markForUpdate();
            trace("started '{}' on a factory thread: duration={}, parallelism={}, {} of {} run(s)",
                    recipe.registryName(), thread.getRecipeDuration(), parallelism,
                    controller.getRunningThreadCount(), controller.getThreadCount());
            return;
        }
    }

    // ------------------------------------------------------------------
    // The recipe filter: upstream's canCheck, and where its input comes from
    // ------------------------------------------------------------------

    /**
     * How many runs of each recipe this factory already has in flight, keyed by the recipe's own name.
     *
     * <p>Upstream builds the same map from {@code factory.getActiveRecipeList()}
     * ({@code FactoryRecipeSearchTask} U29-U33), and that list is every thread holding an active recipe
     * (upstream {@code TileFactoryController} U684-U699) -- so this counts EXACTLY what upstream counts,
     * including runs that are currently held rather than crafting. The port's per-thread equivalent of that
     * list is a walk over the controller's threads asking {@code hasActiveRecipe()}.</p>
     */
    private static java.util.Map<String, Integer> runningRecipeCounts(TileFactoryController controller) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (FactoryRecipeThread thread : controller.getFactoryRecipeThreadList()) {
            if (!thread.hasActiveRecipe()) {
                continue;
            }
            counts.merge(thread.getRecipeName(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Upstream's {@code FactoryRecipeSearchTask.canCheck} (L89-U99), rule for rule, over this port's
     * accounting.
     *
     * <pre>
     * upstream                                                          this port
     * String required = recipe.getThreadName();                          recipe.matchesThreadName(...)
     * if (!required.isEmpty() &amp;&amp; (thread == null                   controller.hasIdleThreadFor(recipe)
     *     || !thread.getThreadName().equals(required))) return false;    checks the eligible named/dynamic pool
     * int maxThreads = recipe.getMaxThreads();                           recipe.allowsAnotherCopy(running)
     * return maxThreads == -1 || runningRecipes.getInt(name) &lt; maxThreads;
     * </pre>
     *
     * <p>Upstream searches per thread; this port searches on the controller's server tick and checks the
     * compatible named pool first.
     * A name-pinned recipe cannot spill into an unnamed dynamic thread, while an unpinned recipe remains
     * eligible for the dynamic pool.</p>
     *
     * @param recipe          the candidate
     * @param runningRecipes  the snapshot {@link #runningRecipeCounts} took for this search
     * @return whether the search may consider this recipe at all
     */
    private static boolean canCheck(MachineRecipe recipe, java.util.Map<String, Integer> runningRecipes,
                                    TileFactoryController controller) {
        if (!controller.hasIdleThreadFor(recipe)) {
            trace("recipe '{}' has no compatible idle factory thread", recipe.registryName());
            return false;
        }
        int running = runningRecipes.getOrDefault(recipe.registryName(), 0);
        // Each recipe identity owns at most one thread. That thread may still process a parallel batch.
        if (running > 0) {
            trace("recipe '{}' already owns a factory thread -- skipped", recipe.registryName());
            return false;
        }
        if (!recipe.allowsAnotherCopy(running)) {
            trace("recipe '{}' already has {} of {} allowed run(s) in flight -- skipped",
                    recipe.registryName(), running, recipe.maxThreads());
            return false;
        }
        return true;
    }

    /**
     * The name of every factory thread this port creates -- upstream's own value for a simple thread
     * ({@code FactoryRecipeThread} U51, the {@code ""} third argument), not a placeholder of the port's.
     */
    private static final String FACTORY_THREAD_NAME = "";

    // ------------------------------------------------------------------
    // Advancing one run
    // ------------------------------------------------------------------

    /**
     * Advances ONE run by one tick -- upstream {@code doThreadRecipeTick} (TileFactoryController
     * U202-U257), mapped onto this port's requirement machinery.
     */
    private static void tickThread(FactoryRecipeThread thread, TileFactoryController controller,
                                   DynamicMachine machine, MachineContext context,
                                   List<MachineRecipe> candidates) {
        if (!thread.hasActiveRecipe()) {
            thread.countIdleTick();
            return;
        }

        MachineRecipe recipe = MachineTicker.findByName(candidates, thread.getRecipeName());
        if (recipe == null) {
            // The recipe vanished (a /reload changed the definitions). Its inputs are already gone, so the run
            // is dropped -- the same call MachineTicker.advance makes for the same reason.
            trace("run '{}' not found in the machine's candidates -- dropping it", thread.getRecipeName());
            thread.abandonRecipe();
            controller.markForUpdate();
            return;
        }
        thread.setRecipe(recipe);

         
        // per run per tick through the ONE producer (`MachineTicker.resolvedRun`), and every requirement read
        // below comes from it rather than from `recipe.requirements()` -- the same list the start check of this
        // run asked its questions of, so a modifier cannot change what the run DEMANDS without changing what it
        // MAKES. Rebuild on each evaluation because modifier conditions can change mid-run. Retaining the
        // result for script queries does not make it a cache for the following tick.
        context.selectIsolationGroup(thread.getIsolationGroupKey());
        RecipeCraftingContext run = MachineTicker.resolvedRun(machine, recipe, context, thread.activeModifiers(),
                        thread.getActiveCatalystRequirements())
                .setSelectedIsolationGroupKey(thread.getIsolationGroupKey());
        thread.observeRuntimeRecipeContext(run);
        List<Requirement> requirements = run.getRequirements();

        // THE PER-RUN TICK BRACKET (upstream U227-U252: the PreTickEvent, the work, the PostTickEvent).
        FactoryRecipeTickEvent start = new FactoryRecipeTickEvent(thread, controller, Phase.START, run);
        start.postEvent();
        try {
            if (!thread.hasActiveRecipe()) return;
            RecipeCraftingContext updated = thread.getRuntimeRecipeContext();
            if (updated != null) { run = updated; requirements = run.getRequirements(); }
            if (start.isFailure()) {
                // Upstream U229-U231: the event decided, so the caller does no work this tick. The event's own
                // postEvent already applied what it promised (a rewind, or a teardown).
                if (thread.hasActiveRecipe()) {
                    notWorking(thread, controller, recipe, run, context, start.getFailureReason());
                }
                return;
            }

            int currentTick = thread.getRecipeTicks();
            String tickFailure = MachineTicker.processTriggeredInputs(requirements, run, context, thread.getParallelism(), currentTick,
                    thread::hasTriggeredRequirement, thread::markTriggeredRequirement);
            List<Requirement> activeRequirements = MachineTicker.activeRequirementsAtTick(run, requirements, currentTick);
            if (tickFailure == null) tickFailure = MachineTicker.processPerTick(recipe, activeRequirements, context, thread.getParallelism(),
                    thread.getRecipeTicks() >= thread.getRecipeDuration());
            if (tickFailure != null) {
                // A SHORTFALL STALLS THE RUN, exactly as it does for a single-craft controller, and the reason
                 
                // MachineTicker.advance's failure branch. DESCRIBED OVER THE RESOLVED LIST: a diagnostic that
                // reported the definition's amounts while the run demanded the scaled ones would be a third
                // description of the same craft. With no modifier active the two lists are THE SAME OBJECT
                // (`RecipeCraftingContext.apply` returns its argument), so this changes nothing there.
                notWorking(thread, controller, recipe, run, context, tickFailure);
                return;
            }
            setBlockedReason(thread, controller, null);

            recipe.commands().runTickCommands(controller, thread.getRecipeTicks());

            thread.advanceRecipe();
            trace("'{}' {}/{} on a factory thread", recipe.registryName(),
                    thread.getRecipeTicks(), thread.getRecipeDuration());

            if (thread.getRecipeTicks() >= thread.getRecipeDuration()) {
                // THE FLUIDS' OWN FIELD IS PUT IN STEP WITH THE MULTIPLIER THE ITEM PATH USES, for the reason
                // MachineTicker.setParallelism documents and at the same point the single craft does it
                // (MachineTicker.advance, immediately before this gate): `RequirementFluid.produce` reads ITS
                // OWN parallelism field rather than the argument, and the list resolved above is FRESH this
                // tick -- so without this line a run granted N would reserve `amount * N` room here and place
                // `amount`. The machine path and the factory path call the SAME method for this.
                // Completion mirrors upstream finishCrafting: delayed trigger metadata controls start-time IO,
                // but every output requirement is still considered by the final placement pass.
                MachineTicker.setParallelism(requirements, thread.getParallelism());
                 
                // per run. Upstream reaches the same conclusion by a different route (RecipeThread.onFinished
                // U73-U78 checks canFinishCrafting and holds the craft at 100% until there is room); the port
                // re-checks every tick rather than every tenth, which is what MachineTicker.advance does and is
                // therefore the one convention in force here rather than a second one.
                for (Requirement requirement : requirements) {
                    if (requirement.getIOType() != IOType.OUTPUT) {
                        continue;
                    }
                     
                    // parallelism (thread.getParallelism(), handed to emitOutputs two lines below), so the room
                    // it must find is `amount * that`, not `amount`. A run held here is re-asked next tick, which
                    // is the port's single-craft convention this branch deliberately copies.
                    //
                     
                    // exactly the same multi-output exposure: the loop below used to ask each output
                    // separately against the same free space, so a nine-output recipe could be admitted and lose
                    // its tail. MACHINE AND FACTORY MUST NOT DISAGREE ABOUT THIS -- the two call the SAME
                    // helper, with the SAME multiplier they hand to emitOutputs, rather than each keeping its
                    // own copy of the rule.
                    if (!requirement.canEmit(context, thread.getParallelism())) {
                        trace("run '{}' at {} is complete but its output has no room; holding",
                                recipe.registryName(), controller.getBlockPos());
                         
                        // A held run is NOT working -- FactoryRecipeThread.isWorking's own javadoc says so
                        // ("a recipe that is held ... is NOT working") -- but nothing wrote the reason, so
                        // blockedReason stayed null, the run kept counting as RUNNING in
                        // getRunningThreadCount, and the screen drew "working" over a run that could not
                        // advance. THE REASON IS THE SAME KEY THE SINGLE-CRAFT PATH USES, from the same
                        // derivation (MachineTicker.outputSpaceKey reads the requirement's own typeId and
                        // direction, and both language files carry the family), so a factory and a machine
                        // cannot end up describing the same refusal with two different keys (rule B).
                        setBlockedReason(thread, controller, MachineTicker.outputSpaceKey(requirement));
                        return;
                    }
                }
                // THE ALLOCATION IS ASKED ONCE FOR THE WHOLE RUN, after the per-requirement guard above has
                // answered for a SINGLE output and named the reason. The guard is kept rather than replaced
                // because it is the cheaper question and it is what the port has always asked here; the
                // allocation is the one that can see the inputs are richer than the outputs.
                    String allocationFailure = MachineTicker.outputAllocationFailure(context, requirements,
                        thread.getParallelism());
                if (allocationFailure != null) {
                    trace("run '{}' at {} is complete but its outputs have no room together; holding",
                            recipe.registryName(), controller.getBlockPos());
                     
                    setBlockedReason(thread, controller, allocationFailure);
                    return;
                }
                // THE EMISSION TAKES THAT SAME LIST, which is the point of threading it: `emitOutputs` asks and
                // places what `satisfies` asked, so a factory run with an active modifier makes the doubled
                 
                // and it is the line the audit's "the check demands two and one is placed" symptom ended on).
                MachineTicker.emitOutputs(recipe, run, context, thread.getParallelism());
                recipe.commands().runFinishCommands(controller);
                // FINISHED, at upstream's point (TileFactoryController.onThreadRecipeFinished U304, called
                // from RecipeThread.onFinished after context.finishCrafting()): after the outputs are placed,
                // while the thread still carries the completed recipe. This ordering is observable through
                // FactoryRecipeFinishEvent#getContext and lets a handler inspect the run that just finished.
                new FactoryRecipeFinishEvent(thread, controller, run).postEvent();
                // The source clears the active recipe only after the finish event. Keep that lifetime here so
                // event handlers see a non-null recipe/context, while the thread is idle for the next search.
                thread.finishRecipe();
                controller.markForUpdate();
            }
        } finally {
             
            // factory SKIPS the end event on its own early-return paths (U242-U249 isNotWorking returns before
            // U252) -- the port does not, because the port's established rule for a bracketed tick is that the
            // bracket always closes, and a run that starved is exactly the case a handler needs to observe.
            new FactoryRecipeTickEvent(thread, controller, Phase.END, run).postEvent();
        }
    }

    /**
     * Reports a run that is not crafting, at upstream's point and with upstream's payload
     * ({@code TileFactoryController.onThreadRecipeFailure} U286-U299, called from {@code isNotWorking} U259-U269).
     *
     * <p>THE CONTRACT IS THE RETURN FLAG: a failure that says "destroy" tears the run down, and one that does
     * not leaves it in place to retry -- which is why the default comes from the RECIPE
     * ({@code doesCancelRecipeOnPerTickFailure()}, upstream U295, the port's
     * {@code MachineRecipe.cancelOnPerTickFailure()}) rather than from the event's constructor.</p>
     *
     * @param run the run's own resolved requirements, already built by the caller for this tick: the fallback
     *            description below walks THAT list, so a run held by a scaled requirement is described by the
     *            scaled amounts. With no modifier active it IS the definition's list, object for object.
     */
    private static void notWorking(FactoryRecipeThread thread, TileFactoryController controller,
                                   MachineRecipe recipe, RecipeCraftingContext run, MachineContext context,
                                   String reason) {
        String cause = reason == null || reason.isEmpty()
                ? run.describeBlockers(context)
                : reason;
        // The formed machine owns the progress failure policy in upstream. Apply it before the
        // failure event, while the run is still active and its position/progress are observable.
        switch (context.getMachine().getFailureAction()) {
            case RESET -> thread.resetRecipeProgress();
            case DECREASE -> thread.rewindRecipeTick();
            case STILL -> { }
        }
        setBlockedReason(thread, controller, cause);
        FactoryRecipeFailureEvent event = new FactoryRecipeFailureEvent(
                thread, controller, cause, recipe.cancelOnPerTickFailure(), run);
        event.postEvent();
        if (event.isDestructRecipe()) {
            // Upstream U266 tears the run down in place; the port's equivalent is the same operation its own
            // failure paths use.
            thread.abandonRecipe();
            controller.markForUpdate();
        }
    }

    /**
     * Records a run's blocked reason, syncing only when it CHANGES.
     *
     * <p>Upstream syncs on a status transition rather than every tick
     * ({@code markNoUpdateSync()} inside the {@code isNotWorking} branch, U243-U248), and this port's single-craft
     * path syncs on transitions and completion for the same reason (TileController.advanceRecipe). Syncing a
     * blocked flag that is already set would be one packet per run per tick.</p>
     */
    private static void setBlockedReason(FactoryRecipeThread thread, TileFactoryController controller,
                                        String reason) {
        String before = thread.getBlockedReason();
        boolean changed = before == null ? reason != null : !before.equals(reason);
        thread.setBlockedReason(reason);
        if (changed) {
            controller.markForUpdate();
        }
    }
}
