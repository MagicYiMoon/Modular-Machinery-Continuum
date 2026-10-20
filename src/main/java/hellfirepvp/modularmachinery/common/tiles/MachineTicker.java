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

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.machine.MachineFormation;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachinePattern;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.machine.StructureMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Advances one machine by one tick: selects a recipe, gates on completion viability, consumes, and
 * emits.
 *
 * <h2>R4: why this is strictly single-threaded</h2>
 *
 * <p>The port's top risk register flags concurrent machine ticking. 1.12.2 ran machine work on a
 * worker pool, which was possible because its world access sat behind loose abstractions. On 1.20.1
 * that is not survivable: {@code Level#getChunk}/{@code getBlockState} are not thread-safe, and
 * touching a {@code Level} from a worker thread while the server tick runs will corrupt state or
 * deadlock. Vanilla's own chunk and entity machinery assumes a single writer.
 *
 * <p>This ticker therefore touches the level, block entities and capabilities <b>only</b> on the
 * server thread, and does no off-thread work at all. Where later optimisation is wanted, the shape is
 * fixed in advance: <b>collect</b> the needed state into an immutable snapshot on the server thread,
 * <b>compute</b> off-thread against that snapshot only, then <b>apply</b> on the server thread. What
 * must never happen is passing a {@code Level} or a {@code BlockEntity} to a worker —the API is
 * designed so that this cannot be expressed.
 *
 * <h2>Consumption semantics: why inputs go at the start and energy does not</h2>
 *
 * <p>The two requirement directions are deliberately handled differently, and the reason is that they
 * fail differently:
 *
 * <ul>
 *   <li><b>Inputs are consumed once, up front.</b> Drawing items gradually would mean a recipe
 *       interrupted at 90% has already destroyed most of the stack while producing nothing —the
 *       player's loss with none of the benefit. Taking everything after {@code canFinish} has
 *       confirmed the recipe is completable keeps the failure mode "nothing happens" rather than
 *       "items vanished".</li>
 *   <li><b>Energy is drawn per tick.</b> That is the mod's defining behaviour —a machine starves
 *       visibly when its power runs out, and resumes when it returns. Stored energy is the progress
 *       meter, so the recipe is <b>not</b> reset on starvation; it simply stops advancing.</li>
 * </ul>
 *
 * <p>Outputs are emitted at completion. An output requirement's {@code canFinish} was checked up front,
 * so an emit failure at this point means something changed the machine's contents mid-craft —the tick
 * loop re-checks and stalls rather than dropping items on the floor.
 */
public final class MachineTicker {

    private MachineTicker() {
    }

    /**
     * Advances the controller one tick.
     *
     * @param level      the server level —never call this off the server thread
     * @param pos        the controller position
     * @param controller the controller block entity, whose recipe state is read and updated
     */
    public static void tick(Level level, BlockPos pos, TileController controller) {
        // Client-side controllers hold no authoritative state; rendering reads the synced tag.
        if (level.isClientSide()) {
            return;
        }
        // A FACTORY CONTROLLER IS NOT DRIVEN BY THIS ENGINE, and the guard is here rather than only in the block
        // that hands out tickers (PLAN 662): if that wiring were ever lost, a factory tile would be ticked as a
        // single-craft controller -- and because a factory tile reports its state from its THREADS, every tick
        // would find "no active recipe", start a craft, consume its inputs into the inherited single-craft
        // fields, and do it again. A machine that eats a bus per tick and never crafts is worse than a machine
        // that stops, so the wrong engine refuses the tile instead of half-driving it.
        if (controller instanceof TileFactoryController) {
            return;
        }
        if (!controller.isFormed()) {
            if (controller.hasActiveRecipe()) {
                // The machine came apart mid-craft. The inputs were already consumed, so the honest
                // outcome is to drop the progress rather than keep a phantom recipe alive that could
                // never emit its output.
                trace("not formed —abandoning active recipe");
                controller.abandonRecipe();
            }
            tryAutomaticFormation(level, pos, controller);
            return;
        }

        // REDSTONE STOPS A MACHINE (PLAN section 361).
        //
        // Upstream's TileMachineController#doControllerTick returns early on a strong signal. The port HAD
        // isRedstoneStopped() -- TileController:231 -- and it was read only by MenuMachineController's data slot
        // and the self-test, so the control existed, was DISPLAYED to the player, and did nothing. Section 354.4
        // is where the survey measured that.
        //
        // PAUSE, NOT ABANDON, and the difference is the player's items: returning here leaves the active recipe
        // and its progress untouched, and the machine resumes when the signal drops. Abandoning -- which is what
        // the branch above does for an unformed machine -- would destroy the inputs already consumed, turning a
        // redstone pulse into item loss.
        if (controller.isRedstoneStopped()) {
            return;
        }

        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(controller.getMachineName());
        if (machine == null) {
            return;
        }

        // COMPONENT RE-BIND AFTER A WORLD LOAD (PLAN 627). The controller own flag is persisted and its
        // BOUND POSITIONS ARE NOT, so a reloaded machine reported itself formed with zero components and
        // could not run until the player shift-clicked it. UPSTREAM HAS NO SUCH HOLE: doStructureCheck
        // (TileMultiblockMachineController U533-U559) calls updateComponents() at U556 on every structure
        // check, so its components come back by themselves.
        //
        // IT IS A ONE-SHOT REQUEST A TICK CONSUMES, not a condition tested every tick: a machine whose
        // parts are all casings legitimately binds nothing, and re-walking it forever would be a silent
        // per-tick cost on every formed machine in the world.
        if (controller.consumeRebindRequest()) {
            java.util.List<net.minecraft.core.BlockPos> rebound =
                    hellfirepvp.modularmachinery.common.machine.MachineFormation.rebind(
                            (net.minecraft.server.level.ServerLevel) level, controller.getBlockPos(),
                            facingOf(controller), machine, loader);
            controller.setBoundPositions(rebound);
            trace("re-bound {} component(s) after a world load", rebound.size());
        }

        Direction facing = controller.getBlockState().hasProperty(
                hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                ? controller.getBlockState().getValue(
                        hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                : Direction.NORTH;

        // THE DYNAMIC PATTERNS ARE RE-CHECKED PERIODICALLY (PLAN section 338).
        //
        // Upstream re-asks in the controller's own update (`T690`'s `!matchesDynamicPattern(foundMachine)`), so a
        // machine whose line is broken AFTER formation stops working. The port had only the formation gate
        // (section 335), which refuses a wrong line at build time but leaves an already-formed machine running
        // when a section is later removed.
        //
        // NOT EVERY TICK. The walk rebuilds the pattern and compares every part against the world, and doing that
        // twenty times a second for every running machine would be paid by machines that never change. One check
        // per second is enough for a player to see the machine stop, at a twentieth of the cost.
        //
        // A broken line calls setFormed(false); the branch at the top of this method then abandons any active
        // recipe on the NEXT tick, which is the same path a player breaking a casing has always taken.
        // THE WHOLE STRUCTURE IS RE-CHECKED ON UPSTREAM'S OWN CADENCE (PLAN 683, DEFECT A), BEFORE the narrower
        // dynamic-pattern check, because a full re-check SUBSUMES it: MachineFormation.tryForm re-asks every
        // dynamic pattern itself. The 20-tick check stays because it is finer-grained than the 50-tick one, and
        // removing it to avoid an overlap would WEAKEN a shipped behaviour -- upstream runs both inside one
        // check, so having both is closer to upstream, not further from it.
        if (structureCheckDue(level)) {
            if (!structureStillHolds(level, pos, facing, machine, loader, controller)) {
                return;
            }
        }
        if (!machine.getDynamicPatterns().isEmpty() && level.getGameTime() % 20 == 0) {
            if (!dynamicPatternsStillHold(machine, level, pos, facing, loader, controller)) {
                return;
            }
        }

        // THE CONTEXT RESOLVES COMPONENTS OUT OF THE PATTERN THE MACHINE WAS FORMED WITH (PLAN §750). The
        // definition's own pattern is only the static half: for a machine with dynamic sections the busses live in
        // the sections, and asking the definition here is the half of the defect the binding half was hiding --
        // the machine would form, bind its busses, and then have the recipe layer unable to see any of them.
        MachineContext context = new MachineContext(
                (net.minecraft.server.level.ServerLevel) level, pos, facing, machine, loader.getVariables(),
                controller.getFormedPattern(machine));

        RecipeLoader recipeLoader = MachineReloadListener.getRecipeLoader();
        List<MachineRecipe> candidates = recipeLoader.getByMachine().get(machine.getRegistryName());
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        // THE MACHINE TICK BRACKET (PLAN sections 498.2, 403).
        //
        // Upstream brackets ONE TICK OF WORK with an event pair -- TileMachineController.doSyncStep
        // calls onMachineTick(Phase.START) at U123, does the work, and calls onMachineTick(Phase.END) at
        // U136. TileFactoryController does the same at U133 and U156. THE PAIR IS THE CONTRACT: a handler
        // can prepare or cancel at START and finish at END, and firing once per tick would be a different
        // contract.
        //
        // SO THE BRACKET GOES AROUND THE WORK AND NOT AROUND THE WHOLE METHOD: the guards above are
        // exits where NO WORK HAPPENS, and bracketing those would report work that did not occur. It is
        // also placed AFTER the candidate lookup, matching upstream -- doSyncStep runs after the structure
        // and recipe checks, not before them.
        //
        // STRAIGHT-LINE, NO try/finally, AND THAT IS COPIED RATHER THAN CHOSEN: upstream U123-U136 is
        // straight-line too, so a throwing handler is not caught here upstream either. Adding a finally
        // would give this port a behaviour upstream does not have.
        new hellfirepvp.modularmachinery.common.event.machine.MachineTickEvent(controller,
                hellfirepvp.modularmachinery.common.event.machine.Phase.START).postEvent();

        // THE TICK'S ONE RESOLVED REQUIREMENT LIST (PLAN 723.3). Built here, once, and handed to BOTH paths:
        // the start check consumes it, and the work path -- the finish gate, the emission, the input draw --
        // reads the SAME list instead of `recipe.requirements()`. That is what stops a modifier from changing
        // what the machine is ASKED for without changing what it MAKES, which is the defect 723.3 measured.
        MachineRecipe running = activeRecipe(controller, candidates);
        // A running recipe must keep the group chosen when it started.  An idle controller may retain a
        // persisted key from an older run, but that key must never constrain the next recipe search.
        context.selectIsolationGroup(controller.hasActiveRecipe() ? controller.getIsolationGroupKey() : null);
        RecipeCraftingContext resolvedRun = resolvedRun(machine, running, context);
        if (resolvedRun != null) {
            resolvedRun.setSelectedIsolationGroupKey(controller.getIsolationGroupKey());
        }

        long performanceStarted = System.nanoTime();
        long searchStarted = System.nanoTime();
        if (controller.hasActiveRecipe()) {
            advance(controller, context, candidates, resolvedRun);
        } else {
            tryStart(controller, context, candidates, resolvedRun);
        }
        controller.recordPerformanceNanos(System.nanoTime() - performanceStarted);
        controller.recordRecipeSearchNanos(System.nanoTime() - searchStarted);

        new hellfirepvp.modularmachinery.common.event.machine.MachineTickEvent(controller,
                hellfirepvp.modularmachinery.common.event.machine.Phase.END).postEvent();
    }

    /** Shared source-style blueprint-first discovery, also used by factory controllers. */
    static void tryAutomaticFormation(Level level, BlockPos pos, TileController controller) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !controller.automaticStructureCheckDue(level.getGameTime())) return;
        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine blueprint = hellfirepvp.modularmachinery.common.item.ItemBlueprint
                .getAssociatedMachine(controller.getBlueprintInventory().getStackInSlot(0));
        if (blueprint != null && formCandidate(server, pos, controller, blueprint, loader)) return;
        for (DynamicMachine candidate : loader.all()) {
            if (candidate == blueprint || candidate.isRequiresBlueprint()) continue;
            if (formCandidate(server, pos, controller, candidate, loader)) return;
        }
    }

    private static boolean formCandidate(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                         TileController controller, DynamicMachine machine, MachineLoader loader) {
        var attempt = MachineFormation.tryForm(level, pos, facingOf(controller), machine, loader);
        if (!attempt.formed()) return false;
        controller.setMachineName(machine.getRegistryName());
        controller.setBoundComponentCount(attempt.boundCount());
        controller.setFormed(true);
        return true;
    }

    /** Diagnostic gate, so the verbose per-tick trace only appears when a self-test asked for it. */
    private static final boolean TRACE = Boolean.getBoolean("modularmachinery.selfTest");

    /** One-shot latch so the energy-lookup shape is reported once per JVM, not once per tick. */
    private static boolean energyReported = false;

    /**
     * Recipes whose energy refusal has already been reported. Per recipe rather than a single static flag, so
     * a second machine failing is still visible (§120.2).
     */
    private static final java.util.Set<String> energyRefusalsReported = new java.util.HashSet<>();

    /** One-shot latch for the instanceof probe. */
    private static boolean hatchProbeLogged = false;

    private static void trace(String message, Object... args) {
        if (TRACE) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.info("[MMCE][tick] " + message, args);
        }
    }

    // ------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------

    private static void tryStart(TileController controller, MachineContext context,
                                 List<MachineRecipe> candidates, RecipeCraftingContext resolvedRun) {
        for (MachineRecipe recipe : candidates) {
            if (!isRunnable(recipe)) {
                trace("skipping '{}': {}", recipe.registryName(), notRunnableReason(recipe));
                continue;
            }

            // canStart asks "may I begin", canFinish asks "can this run to completion". Both are
            // required before anything is taken: skipping canFinish is precisely the bug that would
            // destroy a player's items on an output-full machine.
            // THE MULTIPLIERS ARE RESET TO ONE BEFORE THE START CHECK (PLAN 683.2 fact 8, closed). The
            // requirement objects in `recipe.requirements()` are the DEFINITION's, shared by every machine
            // running it, and `effectiveParallelism` writes the granted value into them. A leftover from
            // another craft would make the room question below read `amount * <someone else's parallelism>`,
            // and the value is re-decided five lines further down anyway -- so resetting here cannot lose a
            // decision, it can only stop one leaking in. THE RESET IS A NO-OP ON AN EXEMPT REQUIREMENT
            // (`setParallelizeUnaffected(true)` already forces one and `setParallelism` refuses when exempt),
            // so there is no second rule to keep in step.
            //
            // AND IT RESETS THE DEFINITION'S OWN LIST, which is the list the reduction below still walks --
            // see `requirements` below for why both views carry the granted value.
            RecipeCraftingContext run = resolvedRun != null && resolvedRun.getRecipe() == recipe
                    ? resolvedRun
                    // THE START PATH'S OWN RESOLUTION, through the ONE rule (RecipeCraftingContext.withModifiers)
                    // and with the SAME active-modifier list the tick-level resolution used. A candidate that is
                    // not the running recipe has no tick-level list, and resolving it any other way would be the
                    // second convention this fix exists to remove.
                    : RecipeCraftingContext.withModifiers(recipe, activeModifiers(recipeMachine(controller, context),
                            context), context);
            for (Requirement requirement : recipe.requirements()) {
                if (requirement instanceof Requirement.Parallelizable parallelizable) {
                    parallelizable.setParallelism(1);
                }
            }
            if (!satisfies(recipe, run, context, true)) {
                trace("recipe '{}' not satisfied", recipe.registryName());
                continue;
            }
            context.selectIsolationGroup(run.getSelectedIsolationGroupKey());
            List<Requirement> requirements = run.getRequirements();

            // THE EFFECTIVE PARALLELISM IS DECIDED **BEFORE ANYTHING IS TAKEN** (PLAN 597.3, 601, 497).
            //
            // THIS ORDER IS LOAD-BEARING AND IT WAS WRONG ONE ROUND EARLIER: the computation and its zero check
            // used to sit AFTER consumeInputs, so a craft whose requirements could not supply even one copy would
            // have ALREADY HAD ITS INPUTS REMOVED and then been skipped -- destroying the player's items on a
            // machine that simply could not run. THAT IS THE EXACT FAILURE 346 WARNED ABOUT, and it was reachable
            // by a machine with a parallel controller asking for more copies than its input bus holds.
            //
            // MOVING IT HERE FIXES BOTH PROBLEMS AT ONCE: nothing is consumed before the craft is known to be
            // runnable, AND the value is available to the consumption path itself, which needs it to take N copies.
            List<Requirement> immediate = requirements.stream()
                    .filter(r -> r.getTriggerTime() <= 0).toList();
            int craftParallelism = effectiveParallelism(recipe, immediate, context,
                    machineParallelism(recipeMachine(controller, context), context));
            if (craftParallelism <= 0) {
                trace("skipping '{}': its requirements cannot supply a single parallel copy", recipe.registryName());
                continue;
            }
            // Consume inputs only after the completion check has passed. THE RESOLVED LIST IS WHAT IS
            // CONSUMED, so the machine takes what the check demanded rather than the definition's own amount.
            if (!consumeInputs(immediate, context, craftParallelism)) {
                trace("recipe '{}' passed checks but inputs could not be taken", recipe.registryName());
                continue;
            }

            // RECIPE START EVENT (PLAN 513). Upstream fires it FIRST in onStart (TileMachineController
            // U266), BEFORE activeRecipe.start(...), so a handler observes a recipe that has not started.
            // The port equivalent of that method is this line: beginRecipe is where a recipe begins.
            //
            // THE CONTEXT IS BUILT FROM THE RECIPE, WHICH IS WHAT UPSTREAM DOES TOO: it passes
            // recipeThread.getContext(), a RecipeCraftingContext built from the recipe, and the thread
            // is only a conveyor for it (513.1).
            new hellfirepvp.modularmachinery.common.event.recipe.RecipeStartEvent(controller,
                    new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe))
                    .postEvent();
            // THE DURATION IS THE MODIFIED ONE (PLAN section 353.3's duration gap, closed here). Upstream writes
            // the recipe's own tickTime through the duration modifiers before the craft starts (MachineRecipe:250
            // and ActiveMachineRecipe:80/148 both call it `getRecipeTotalTickTime` after that), so a pack's
            // `"target": "modularmachinery:duration"` modifier is what decides how long this run takes. Until now
            // the port passed the definition's raw recipeTime through and such a modifier scaled nothing.
            controller.beginRecipe(recipe.registryName(),
                    effectiveRecipeDuration(recipeMachine(controller, context), recipe, context,
                            java.util.List.of(), run.activeCatalystIndices()),
                    craftParallelism);
            controller.setActiveCatalystRequirements(run.activeCatalystIndices());
            controller.setIsolationGroupKey(run.getSelectedIsolationGroupKey());
            recipe.commands().runStartCommands(controller);
            trace("started '{}' duration={}", recipe.registryName(), controller.getRecipeDuration());
            return;
        }
    }

    /** Resolves the machine bound to a controller, by the name the controller carries. */
    private static DynamicMachine recipeMachine(TileController controller, MachineContext context) {
        return MachineReloadListener.getLoader().findByName(controller.getMachineName());
    }

    /**
     * <b>THE ACTIVE MODIFIERS FOR ONE EVALUATION, AND THE ONLY LIST THAT DECIDES WHETHER A MODIFIER APPLIES.</b>
     *
     * <p>A {@link hellfirepvp.modularmachinery.common.modifier.RecipeModifier} must not be applied without
     * knowing whether it is ACTIVE: applying every declared modifier would grant the vent bonus whether or not
     * the vent exists, which is worse than a documented bonus that silently fails (PLAN 234-236). The condition
     * lives in {@link hellfirepvp.modularmachinery.common.modifier.ModifierReplacement#matches}, whose offset is
     * rotated by {@code StructureMatcher.toWorldOffset} -- the SAME helper the pattern machinery uses, so a
     * condition cannot disagree with the structure about where "one block above the controller" is.</p>
     *
     * <p><b>RULE B: IT IS ONE METHOD BECAUSE THERE MUST BE ONE ANSWER.</b> Both paths that need the list -- the
     * start check and the work path -- call this, and both hand the result to
     * {@code RecipeCraftingContext.withModifiers}, which is the one place requirements are scaled. A second
     * scan written at a call site could see a different world (a vent placed or broken between the two reads in
     * one tick) and the check and the emission would then disagree about the same craft.</p>
     */
    private static List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> activeModifiers(
            DynamicMachine machine, MachineContext context) {
        List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> active = new ArrayList<>();
        if (machine == null) {
            return active;
        }
        for (hellfirepvp.modularmachinery.common.modifier.ModifierReplacement replacement
                : machine.getModifierReplacements()) {
            if (replacement.matches(context.getLevel(), context.getControllerPos(), context.getFacing())) {
                active.addAll(replacement.getModifiers());
            }
        }
        // Scripted multi-block replacements are optional conditions, just like the upstream
        // MultiBlockModifierReplacement.  They never participate in formation; they only add
        // their rules when every cell is present with the controller's current rotation.
        hellfirepvp.modularmachinery.common.machine.BlockStateResolver resolver =
                hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader().getResolver();
        for (hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement replacement
                : machine.getMultiBlockModifierReplacements()) {
            if (replacement.matches(context.getLevel(), context.getControllerPos(), context.getFacing(), resolver)) {
                active.addAll(replacement.getModifiers());
            }
        }
        // Upstream's TileMultiblockMachineController.createContext appends controller-scoped
        // customModifiers after structure modifiers. Resolve the same state from the world tile so
        // scripted/runtime modifiers affect both normal and factory evaluations.
        if (context.getLevel() != null) {
            net.minecraft.world.level.block.entity.BlockEntity entity =
                    context.getLevel().getBlockEntity(context.getControllerPos());
            if (entity instanceof TileController controller) {
                active.addAll(controller.getCustomModifiers().values());
            }
        }
        // THE UPGRADES ARE THE SECOND PRODUCER, AND THEY JOIN THE **SAME LIST** RATHER THAN A PARALLEL ONE (audit
        // row 37). Adding them here is the whole wiring: this method's own javadoc establishes that the list it
        // returns is the only thing that decides whether a modifier applies, and its callers hand the result to
        // `RecipeCraftingContext.withModifiers`. An upgrade's effect is therefore scaled by the SAME code as a
        // machine's declared modifier and inherits plan-723.3's invariant -- the start check and the write end read
        // one resolved list, so an upgrade cannot make the machine ask for room for two and place one.
        //
        // WHY THIS IS THE RIGHT PLACE AND NOT A NEW EVENT BUS: upstream reaches its upgrades through
        // `UpgradeMachineEventHandler.onMachineEvent`, which dispatches CraftTweaker closures against machine
        // events. There is no script engine here (see `MachineUpgrade`'s A0 note), so an upgrade cannot act by
        // being CALLED -- it acts by DECLARING modifiers, and this is the one method that turns a declaration into
        // effect. Rebuilding the list per tick is not a cost to be optimised away: it is what makes an upgrade
        // installed or removed mid-craft take effect on the very next tick, which is what upstream's per-tick
        // MachineTickEvent gave its upgrades.
        java.util.Set<String> upgradeModifierKeys = new java.util.HashSet<>();
        // Controller-scoped modifiers occupy the same key space as an upstream upgrade callback.
        if (context.getLevel() != null) {
            net.minecraft.world.level.block.entity.BlockEntity entity =
                    context.getLevel().getBlockEntity(context.getControllerPos());
            if (entity instanceof TileController controller) {
                upgradeModifierKeys.addAll(controller.getCustomModifiers().keySet());
            }
        }
        for (hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade upgrade : upgradeSnapshotOf(context)) {
            if (upgrade instanceof hellfirepvp.modularmachinery.common.upgrade.SimpleMachineUpgrade simple) {
                active.addAll(simple.unkeyedRequirementsModifiers());
                simple.keyedRequirementsModifiers().forEach((key, modifier) -> {
                    if (upgradeModifierKeys.add(key)) active.add(modifier);
                });
            } else {
                active.addAll(upgrade.requirementsModifiers());
            }
        }
        return active;
    }

    /**
     * <b>THE INSTALLED UPGRADES FOR ONE EVALUATION, CACHED FOR THE LIFETIME OF THAT EVALUATION.</b>
     *
     * <p>Why a cache at all: {@code activeModifiers} is called from the start path, the tick path and the factory's
     * per-run resolution, and each call would otherwise walk the machine's components and re-read every bus's
     * inventory. Why the cache is safe: a {@link MachineContext} is built per evaluation and never outlives it — see
     * its class javadoc, "nothing caches across ticks" — so the identity map below cannot hand a later tick a stale
     * set. The key is the CONTEXT, not the level or a static field, because a dedicated server holds several levels
     * and several machines at once and a "last result" would leak between them.
     *
     * <p><b>The merge across buses is upstream's own</b> ({@code checkAndAddUpgradeBus}, U855-U869): one entry per
     * TYPE with the stack sizes summed, because an upgrade's identity is its type and two speed items in two buses
     * are one speed upgrade of size two. Upstream also refuses to grow an entry past
     * {@code type.getMaxStackSize()} (U861-U863) and so does this — without the cap, nine items spread across three
     * buses would reach a factor no single bus allows.
     */
    public static List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade> upgradeSnapshotOf(
            MachineContext context) {
        if (context == null) {
            return List.of();
        }
        return UPGRADE_SNAPSHOTS.computeIfAbsent(context, MachineTicker::collectUpgrades);
    }

    /**
     * The per-context cache described above.
     *
     * <p>{@code WeakHashMap} so a context that the caller has dropped does not keep a bus's block entity alive; the
     * values hold {@code MachineUpgrade} objects and NOT the tiles, so the map is safe to hold weakly.
     */
    private static final Map<MachineContext, List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade>>
            UPGRADE_SNAPSHOTS = new java.util.WeakHashMap<>();

    /** Walks the machine's buses and merges their installed upgrades by type. See {@link #upgradeSnapshotOf}. */
    private static List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade> collectUpgrades(
            MachineContext context) {
        List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade> merged = new ArrayList<>();
        for (hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade upgrade : context.installedUpgrades()) {
            hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade existing = null;
            for (hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade candidate : merged) {
                if (candidate.equals(upgrade)) {
                    existing = candidate;
                    break;
                }
            }
            int cap = upgrade.getType().getMaxStackSize();
            if (existing == null) {
                if (upgrade.getStackSize() > cap) {
                    // Trimmed rather than dropped: the item IS installed and must do something, and the cap is the
                    // declared ceiling on how much. Upstream's own loop skips an upgrade already at its cap rather
                    // than discarding what a bus holds (U861-U863).
                    upgrade.decrementStackSize(upgrade.getStackSize() - cap);
                }
                merged.add(upgrade);
                continue;
            }
            int room = cap - existing.getStackSize();
            if (room > 0) {
                existing.incrementStackSize(Math.min(room, upgrade.getStackSize()));
            }
        }
        return List.copyOf(merged);
    }

    /**
     * The ACTIVE recipe's requirements with the machine's active modifiers applied.
     *
     * <p><b>THIS IS THE TICK'S ONE RESOLVED LIST</b> (PLAN 723.3's fix), and {@code advance} is its consumer:
     * the finish gate, the emission and the fluid field all read it instead of re-deriving anything. It is built
     * once per tick, from the recipe the controller names, through the same two calls the start path uses
     * ({@link #activeModifiers} and {@code RecipeCraftingContext.withModifiers}) -- so the two paths cannot
     * resolve differently, and there is no second list to keep in step.</p>
     *
     * <p>WHY THE LIST MUST BE BUILT PER TICK RATHER THAN STORED WITH THE CRAFT: a modifier's condition is a
     * STRUCTURE FACT. A player who breaks the vent mid-craft changes what the machine is, and the very next tick
     * must ask again -- which is what this call is, and why {@code resolve} is not cached anywhere.</p>
     *
     * <p><b>THE FACTORY CALLS THIS PER RUN, AND THAT IS WHY IT IS PUBLIC (PLAN 723.3, the factory half).</b> A
     * factory runs N recipes at once, so the resolution is PER RUN rather than per machine: each
     * {@code FactoryRecipeThread} resolves its own recipe through this one method, keeping the factory on the
     * ONE producer instead of re-deriving a modifier scan of its own. The machine context is already the
     * factory's own, so a run and a single craft resolve the same way about the same structure.</p>
     *
     * @return the running recipe's resolved requirements, or {@code null} when no recipe is running
     */
    public static RecipeCraftingContext resolvedRun(DynamicMachine machine, MachineRecipe activeRecipe,
                                                    MachineContext context) {
        if (activeRecipe == null) return null;
        java.util.Set<Integer> forced = java.util.Set.of();
        if (context.getLevel() != null && context.getLevel().getBlockEntity(context.getControllerPos())
                instanceof TileController controller && controller.hasActiveRecipe()) {
            forced = controller.getActiveCatalystRequirements();
        }
        return RecipeCraftingContext.withModifiers(activeRecipe, activeModifiers(machine, context), context, forced);
    }

    /** Factory variant: append that run's semi/permanent modifiers to the controller-wide set. */
    public static RecipeCraftingContext resolvedRun(DynamicMachine machine, MachineRecipe activeRecipe,
                                                    MachineContext context,
                                                    Iterable<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> extraModifiers) {
        return resolvedRun(machine, activeRecipe, context, extraModifiers, java.util.Set.of());
    }

    public static RecipeCraftingContext resolvedRun(DynamicMachine machine, MachineRecipe activeRecipe,
                                                    MachineContext context,
                                                    Iterable<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> extraModifiers,
                                                    java.util.Set<Integer> forcedCatalysts) {
        if (activeRecipe == null) return null;
        List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers = activeModifiers(machine, context);
        if (extraModifiers != null) {
            for (hellfirepvp.modularmachinery.common.modifier.RecipeModifier modifier : extraModifiers) {
                if (modifier != null) modifiers.add(modifier);
            }
        }
        return RecipeCraftingContext.withModifiers(activeRecipe, modifiers, context, forcedCatalysts);
    }

    /**
     * The duration a recipe runs for on THIS machine, its active duration modifiers included.
     *
     * <p><b>THE SECOND HALF OF THE ONE MODIFIER LIST (PLAN section 353.3's duration gap).</b>
     * {@code "target": "modularmachinery:duration"} names a type that cannot be instantiated into a requirement
     * (upstream {@code RequirementDuration:27} throws), so it can never be applied by
     * {@code RecipeCraftingContext.withModifiers}, which walks requirements. Upstream applies it to the RECIPE'S
     * LENGTH instead — {@code MachineRecipe:250} (the per-machine recipe copy) and {@code ActiveMachineRecipe:80}
     * / {@code :133} / {@code :148} (the running craft's total) — always against the SAME modifier collection the
     * requirements were scaled with, which upstream assembles from the controller's found (structure-conditional)
     * modifiers plus its custom ones (TileMultiblockMachineController:603-604).</p>
     *
     * <p><b>SO THIS USES {@link #activeModifiers} AND NOT A SECOND SCAN</b> (rule B): the duration a craft is
     * granted and the amounts its requirements were scaled to come from ONE evaluation of the structure, which is
     * the same argument {@link #resolvedRun} makes for the requirements. Writing a second scan here could see a
     * different world — a vent broken between two reads in one tick — and the run would then be, say, a
     * double-length craft at single amounts.</p>
     *
     * <p>Both starting paths call it: the single craft in {@code tryStart} and each factory run in
     * {@code FactoryTicker.startOneRecipe}, so a factory run and a single craft of the same recipe on the same
     * structure cannot disagree about how long it takes.</p>
     *
     * @param machine the machine bound to the controller, or {@code null} when it cannot be resolved — an unknown
     *                machine has no modifiers and the recipe's own duration is the answer
     * @param recipe  the recipe about to start
     * @param context the machine as it stands right now
     * @return the duration in ticks, never below one
     */
    public static int effectiveRecipeDuration(DynamicMachine machine, MachineRecipe recipe,
                                              MachineContext context) {
        if (recipe == null) {
            return 0;
        }
        int base = RecipeCraftingContext.withModifiers(recipe, activeModifiers(machine, context), context)
                .effectiveDuration();
        // AND THE UPGRADES ARE THE SECOND SOURCE OF DURATION (audit row 37). They are multiplied onto the result
        // rather than folded into the modifier list above for the reason `MachineUpgrade#durationMultiplier`
        // records: a duration modifier is a `RecipeModifier`, which this port matches against a REQUIREMENT's type
        // id, and a recipe's length is not a requirement -- so the two are genuinely different channels and both
        // have to reach this number. Multiplication is order-free, so the two cannot disagree about the outcome.
        //
        // Reading the upgrades through `upgradeSnapshotOf(context)` rather than `activeModifiers` keeps the cost
        // identical to the modifier scan's (one cached pass per tick) and keeps `activeModifiers`'s contract
        // intact: it returns REQUIREMENT modifiers, which is what its two other callers hand to
        // `RecipeCraftingContext.withModifiers`.
        float factor = upgradesDurationFactor(context);
        return factor == 1.0F ? base : Math.max(1, Math.round(base * factor));
    }

    /** Factory counterpart including modifiers owned by the selected thread. */
    public static int effectiveRecipeDuration(DynamicMachine machine, MachineRecipe recipe,
                                              MachineContext context,
                                              Iterable<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> extraModifiers) {
        return effectiveRecipeDuration(machine, recipe, context, extraModifiers, java.util.Set.of());
    }

    public static int effectiveRecipeDuration(DynamicMachine machine, MachineRecipe recipe,
                                              MachineContext context,
                                              Iterable<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> extraModifiers,
                                              java.util.Set<Integer> forcedCatalysts) {
        if (recipe == null) return 0;
        List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers = activeModifiers(machine, context);
        if (extraModifiers != null) for (var modifier : extraModifiers) if (modifier != null) modifiers.add(modifier);
        int base = RecipeCraftingContext.withModifiers(recipe, modifiers, context, forcedCatalysts).effectiveDuration();
        return Math.max(1, Math.round(base * upgradesDurationFactor(context)));
    }

    /**
     * @return the product of every installed upgrade's duration factor on this machine, {@code 1.0} for none
     *
     * <p>Public for {@code FactoryTicker}, which starts its runs through {@code effectiveRecipeDuration} — the same
     * call the single craft uses — and for a check that wants to read the factor without starting a craft.
     */
    public static float upgradesDurationFactor(MachineContext context) {
        float factor = 1.0F;
        for (hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade upgrade : upgradeSnapshotOf(context)) {
            factor *= upgrade.durationMultiplier();
        }
        return factor;
    }

    /** @return the running recipe from this machine's candidates, or null when it is not running */
    private static MachineRecipe activeRecipe(TileController controller, List<MachineRecipe> candidates) {
        return controller.hasActiveRecipe() ? findByName(candidates, controller.getRecipeName()) : null;
    }

    /**
     * Whether a recipe is even a candidate for execution, independent of the machine's contents.
     *
     * <p>This exists so the engine and its diagnostics cannot disagree. {@code runnableRecipes} is used
     * by {@code /modularmachinery check} and by the self-test to answer "what could this machine run",
     * and it originally used only the requirement checks —so it reported an <b>unexpanded adapter</b>
     * recipe as runnable (it has no requirements, so those checks pass trivially) while {@code tryStart}
     * skipped it. A diagnostic that disagrees with the engine is worse than no diagnostic: it sends you
     * looking for the fault in the wrong place.
     *
     * <p>Two conditions, both about the definition rather than the contents:
     * <ul>
     *   <li><b>adapter recipes are templates</b>, not runnable recipes. They name a vanilla recipe type
     *       plus modifiers and only become concrete once expanded against the vanilla
     *       {@code RecipeManager}. Unexpanded they have no requirements and a zero duration, so treating
     *       them as runnable made them complete in one tick and restart forever, starving the machine's
     *       real recipes of every tick.</li>
     *   <li><b>a zero duration cannot run</b> —it would hot-loop for the same reason.</li>
     * </ul>
     */
    /**
     * Re-walks a formed machine's dynamic patterns and unforms it when one no longer holds (PLAN section 339).
     *
     * <p>Upstream does this in the controller's own update -- {@code T690}'s
     * {@code !matchesDynamicPattern(foundMachine)} -- so a machine whose line is broken AFTER formation stops
     * working. The port had only the formation gate (section 335), which refuses a wrong line at build time but
     * leaves an already-formed machine running when a section is later removed.
     *
     * <p><b>Public so the self-test can call it directly.</b> The tick calls it once a second, and a check that
     * depended on {@code level.getGameTime() % 20} landing at the right moment could not be written at all --
     * the world's clock does not move inside a self-test. Extracting the decision from its cadence is what makes
     * it observable, and the cadence is the caller's business rather than this method's.
     *
     * @return true when every declared pattern still holds, false when the machine has been unformed
     */
    public static boolean dynamicPatternsStillHold(DynamicMachine machine, Level level, BlockPos pos,
                                                   Direction facing, MachineLoader loader,
                                                   TileController controller) {
        for (hellfirepvp.modularmachinery.common.machine.DynamicPattern pattern
                : machine.getDynamicPatterns().values()) {
            int size = pattern.matchSize(level, pos, facing, loader.getVariables(), loader.getResolver());
            if (!pattern.accepts(size)) {
                trace("dynamic pattern '{}' now finds {} section(s), needs {}..{} -- unforming",
                        pattern.getName(), size, pattern.getMinSize(), pattern.getMaxSize());
                if (Config.cleanCustomDataOnStructureCheckFailed()) controller.clearCustomRuntimeState();
                controller.setFormed(false);
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The periodic structure re-check (PLAN 683, DEFECT A)
    // ------------------------------------------------------------------
    //
    // UPSTREAM'S MECHANISM, read in full before anything was written:
    //
    //   TileMultiblockMachineController U533-U559  doStructureCheck()  -- canCheckStructure gate, the chunk
    //             guard (U542-U548), then `if (!isStructureFormed()) { status = MISSING_STRUCTURE }` and
    //             `updateComponents()` (U556) + MachineStructureUpdateEvent (U557).
    //   TileMultiblockMachineController U673-U735  checkStructure()    -- sets lastStructureCheckTick (U678),
    //             and for a FORMED machine: the area-loaded guard (U683-U685), then
    //             `!foundPattern.matches(world, ctrlPos, true, foundReplacements) || !matchesDynamicPattern()`
    //             -> `resetMachine(true)` (U689-U691), which sets MISSING_STRUCTURE and clears foundMachine.
    //   TileMultiblockMachineController U351-U370  canCheckStructure() -- THE CADENCE, see below.
    //   TileMachineController        U116-U118     doAsyncStep() = `!doStructureCheck() || !isStructureFormed()`
    //             -- and doControllerTick RETURNS when it is true, so doSyncStep never runs and the craft does
    //             not advance. That is how "breaking a block stops the machine" is implemented upstream: not by
    //             tearing the recipe down, but by never ticking it.
    //   TileFactoryController        U126-U128     the SAME doAsyncStep, so FACTORIES ARE COVERED TOO.
    //
    // THE CADENCE, from U351-U370 with the shipped config defaults (U171-U189: structureCheckDelay=30,
    // delayedStructureCheck=true, maxStructureCheckDelay=100):
    //
    //   lastStructureCheckTick == -1                  -> check (first ever)
    //   formed && foundComponents.isEmpty()           -> check EVERY TICK
    //   !delayedStructureCheck                        -> check every structureCheckDelay (30)
    //   formed                                        -> check every
    //       min(structureCheckDelay + currentRecipeSearchDelay(), maxStructureCheckDelay)
    //       = min(30 + 20, 100) = 50 ticks, OR the moment a block inside the pattern's area changed
    //   not formed                                    -> check every min(30 + retries*5, 100)
    //
    // SO A FORMED, RUNNING MACHINE IS RE-CHECKED EVERY 50 TICKS (2.5 s), which is the number this port uses.
    // NOT EVERY TICK: that would be twenty full pattern walks a second per machine, on a code path a machine
    // that never changes anything also pays for.

    /** upstream TileMultiblockMachineController U122 -- {@code public static int structureCheckDelay = 30}. */
    public static final int STRUCTURE_CHECK_DELAY = 30;

    /**
     * upstream TileMultiblockMachineController U180-U182 -- the <b>config</b> default for
     * {@code max-structure-check-delay} is 100, and {@code loadFromConfig} overwrites the field with it at
     * startup. The 200 on the field declaration (U122) is therefore never the effective value on a stock
     * install, which is why 100 is the number carried here.
     */
    public static final int MAX_STRUCTURE_CHECK_DELAY = 100;

    /** upstream U372-U374 -- {@code min(20 + recipeResearchRetryCounter * 5, 100)}, at rest (0 retries) = 20. */
    public static final int RECIPE_SEARCH_DELAY_AT_REST = 20;

    /**
     * Upstream's own expression for the formed-machine interval (U359), so a change to either constant moves the
     * cadence here the same way it moves upstream's.
     */
    public static int structureCheckInterval() {
        return Math.min(STRUCTURE_CHECK_DELAY + RECIPE_SEARCH_DELAY_AT_REST, MAX_STRUCTURE_CHECK_DELAY);
    }

    /** @return true when upstream's formed-machine check is due this tick; see the cadence table above */
    public static boolean structureCheckDue(Level level) {
        return level.getGameTime() % structureCheckInterval() == 0;
    }

    /**
     * Re-runs the structure check for an already formed machine, and <b>unforms it when a part is gone</b>.
     *
     * <p><b>THIS IS THE MISSING PRODUCER OF {@code TileController.setFormed(false)}.</b> The port shipped that
     * event, its listeners, the blockstate mirror and the GUI's {@code missing_structure} line, and the only
     * things that ever fired it were the dynamic-pattern walk (once a second, and only for machines that declare
     * a dynamic pattern) and a player action. So breaking a casing or a hatch left a running machine running
     * forever, which is what the user reported.</p>
     *
     * <p><b>RULE B: IT CALLS THE EXISTING WALK RATHER THAN A SECOND ONE.</b> {@code MachineFormation.tryForm} is
     * the port's one structure walk -- the matcher plus the dynamic patterns plus the component binding -- and it
     * is the same call a shift-click makes. A re-check that matched the pattern by hand would be a second
     * convention for one rule and the two would drift, which is exactly how the port's rotation bug survived
     * four callers.</p>
     *
     * <p><b>AND THE RE-BIND COMES FOR FREE, WHICH IS UPSTREAM'S BEHAVIOUR.</b> Upstream calls
     * {@code updateComponents()} on EVERY check (U556), so adding a hatch to an already formed machine wires it
     * up without re-forming; {@code tryForm} does the same, including the unbind pass for components that left
     * the pattern.</p>
     *
     * <p><b>RULE A0 -- ONE UPSTREAM PIECE IS NOT PORTED, AND IT IS THE FAST PATH.</b></p>
     * <ol>
     *   <li><b>upstream file:line</b> -- {@code TileMultiblockMachineController U359-U366} ORs the cadence with
     *       {@code MMWorldEventListener.INSTANCE.isAreaChanged(world, pos+min, pos+max)}, and that listener
     *       (github/kasuminova/mmce/common/world/MMWorldEventListener L28-L130) is registered on the world with
     *       {@code world.addEventListener(this)} (L43) and records every {@code notifyBlockUpdate} whose flags
     *       include bit 1; the map is cleared at the start of every server tick (L59-L65). That is what makes
     *       upstream's reaction to a broken block IMMEDIATE rather than up to 50 ticks late.</li>
     *   <li><b>why it cannot work on 1.20.1/Forge 47.3.0</b> -- the interface it implements is gone.
     *       {@code net.minecraft.world.IWorldEventListener} and {@code World#addEventListener} were removed in
     *       1.13; on 1.20.1 there is no per-level block-update listener to register. Forge's nearest
     *       equivalents are per-event ({@code BlockEvent.BreakEvent}, {@code BlockEvent.EntityPlaceEvent}) and
     *       miss explosions, pistons and {@code /setblock}, i.e. exactly the cases upstream's listener does
     *       catch -- so building on them would be a partial fast path claiming to be upstream's.</li>
     *   <li><b>what this port does instead</b> -- the PERIODIC check only, at upstream's own formed-machine
     *       interval of 50 ticks (2.5 s), which is the cadence upstream itself uses when nothing in the area
     *       changed. The cost is that a broken block is noticed up to 50 ticks late rather than on the next
     *       tick. WHAT THE CHECK COSTS: one full pattern walk per formed machine per 50 ticks -- sparse, over the
     *       pattern's DECLARED parts (a 3x5x3 shell is 32 comparisons, not 45), so it scales with the shell the
     *       pack authored rather than with the volume it encloses. Plus the component re-bind, which is the same
     *       walk upstream does on every check, and the unbind sweep.</li>
     * </ol>
     *
     * @return true when the structure still holds (or the check was skipped), false when the machine has just
     *         been unformed
     */
    public static boolean structureStillHolds(Level level, BlockPos pos, Direction facing,
                                             DynamicMachine machine, MachineLoader loader,
                                             TileController controller) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return true;    // a client level is never authoritative for machine state
        }
        // THE CHUNK GUARD COMES FIRST, AND IT IS UPSTREAM'S (U683-U685 and U542-U548).
        //
        // WITHOUT IT THIS CHECK WOULD INTRODUCE A DEFECT. StructureMatcher deliberately reports an unloaded
        // part as a MISMATCH (L139-L148: "a chunk that is not loaded must count as a mismatch rather than being
        // read as air"), so a machine spanning a chunk border would be unformed -- and its craft abandoned --
        // every time a neighbouring chunk unloaded. Upstream answers that case with CraftingStatus.CHUNK_UNLOADED
        // and does NOT unform; this port has no such status, so the check is SKIPPED for that tick, which is
        // upstream's decision (the structure is not declared broken) with the one part this port cannot express
        // left out.
        if (!chunksAroundPatternLoaded(level, pos, facing, machine)) {
            trace("structure check skipped at {}: not every chunk the pattern covers is loaded", pos);
            return true;
        }

        MachineFormation.Attempt attempt = MachineFormation.tryForm(server, pos, facing, machine, loader);
        if (!attempt.formed()) {
            trace("structure at {} no longer matches '{}': {}", pos, machine.getRegistryName(),
                    String.join("; ", attempt.report()));
            // THE EXISTING UNFORMED EVENT, unchanged. setFormed(false) fires MachineStructureUpdateEvent,
            // mirrors the blockstate and syncs, and the GUI's missing_structure line reads isFormed().
            if (Config.cleanCustomDataOnStructureCheckFailed()) controller.clearCustomRuntimeState();
            controller.setFormed(false);
            return false;
        }
        return true;
    }

    /**
     * Whether every chunk the machine's pattern covers is loaded.
     *
     * <p>Upstream's guard is {@code getWorld().isAreaLoaded(foundPattern.getPatternBoundingBox(ctrlPos))}
     * (U683). On 1.20.1 the equivalent is {@code LevelReader#hasChunksAt(BlockPos, BlockPos)} (verified against
     * the mapped jar: {@code public default boolean hasChunksAt(BlockPos, BlockPos)}), and the box is derived
     * through {@code StructureMatcher.toWorldOffset} so this cannot disagree with the matcher about where the
     * pattern lands under a rotated controller. A 90-degree rotation maps the box onto itself, so transforming
     * the two opposite corners and taking their min/max covers the whole rotated box.</p>
     */
    private static boolean chunksAroundPatternLoaded(Level level, BlockPos pos, Direction facing,
                                                    DynamicMachine machine) {
        MachinePattern pattern = machine.getPattern();
        if (pattern.isEmpty()) {
            return true;
        }
        BlockPos a = StructureMatcher.toWorldOffset(pos, facing, pattern.getMinX(), pattern.getMinY(), pattern.getMinZ());
        BlockPos b = StructureMatcher.toWorldOffset(pos, facing, pattern.getMaxX(), pattern.getMaxY(), pattern.getMaxZ());
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        // hasChunksAt iterates x1..x2 and z1..z2, so the ordering of the two corners is part of its contract.
        return level.hasChunksAt(min, max);
    }

    public static boolean isRunnable(MachineRecipe recipe) {
        return !recipe.isAdapter() && recipe.recipeTime() > 0;
    }

    private static String notRunnableReason(MachineRecipe recipe) {
        if (recipe.isAdapter()) {
            return "unexpanded adapter recipe";
        }
        return "duration " + recipe.recipeTime();
    }

    // ------------------------------------------------------------------
    // Advancing
    // ------------------------------------------------------------------

    /**
     * Advances the running craft one tick: pays for it, gates its completion, and emits.
     *
     * <p><b>EVERY REQUIREMENT IT READS IS THE TICK'S RESOLVED LIST, NOT {@code recipe.requirements()}.</b> The
     * list arrives as a parameter rather than being built here, so the start check and this method cannot end up
     * with two lists that disagree -- the defect PLAN 723.3 measured, where a machine's modifier doubled what
     * {@code canStart} demanded and the emission still placed the definition's own single item.</p>
     *
     * @param requirements the tick's resolved requirements for this recipe, from
     *                     {@link #resolvedRun(hellfirepvp.modularmachinery.common.machine.DynamicMachine,
     *                     MachineRecipe, MachineContext)} -- never null while a recipe is active
     */
    private static void advance(TileController controller, MachineContext context,
                                List<MachineRecipe> candidates, RecipeCraftingContext run) {
        context.selectIsolationGroup(controller.getIsolationGroupKey());
        if (run != null) run.setSelectedIsolationGroupKey(controller.getIsolationGroupKey());
        MachineRecipe recipe = findByName(candidates, controller.getRecipeName());
        if (recipe == null) {
            // The recipe vanished (a /reload changed the definitions). The inputs are already gone;
            // dropping progress is the only defensible outcome.
            trace("active recipe '{}' not found in candidates —abandoning", controller.getRecipeName());
            controller.abandonRecipe();
            return;
        }
        // The tick resolved the list from the controller's own recipe name; a mismatch here means the candidate
        // list changed underneath the craft, and the resolved list is then the honest thing to read anyway --
        // it is built from the SAME recipe object the loop above just resolved by name, so this is a guard
        // against a null rather than a second rule.
        List<Requirement> requirements = run == null ? recipe.requirements() : run.getRequirements();

        // Draw this tick's energy. A shortfall stalls the recipe instead of failing it, and the machine
        // resumes when power returns.
        //
        // The previous comment here read "the stored energy acts as the progress meter". That is FALSE —
        // TileController.recipeTicks is an independent counter, incremented by advanceRecipe() and persisted
        // in NBT. The claim sent a diagnosis after an arithmetic ceiling that does not exist (§118.2), so it
        // is corrected rather than left standing.
        int currentTick = controller.getRecipeTicks();
        String tickFailure = processTriggeredInputs(requirements, run, context, controller.getParallelism(), currentTick,
                controller::hasTriggeredRequirement, controller::markTriggeredRequirement);
        List<Requirement> activeRequirements = activeRequirementsAtTick(run, requirements, currentTick);
        if (tickFailure == null) tickFailure = processPerTick(recipe, activeRequirements, context, controller.getParallelism(),
                controller.getRecipeTicks() >= controller.getRecipeDuration());
        if (tickFailure != null) {
            // Upstream ActiveMachineRecipe applies the formed machine's failure-action before publishing
            // the failure status. Preserve that progress policy independently from the recipe event's
            // destruct flag: STILL holds, DECREASE rewinds one tick, RESET restarts the run.
            DynamicMachine failureMachine = context.getMachine();
            if (failureMachine != null) {
                switch (failureMachine.getFailureAction()) {
                    case RESET -> controller.resetRecipeProgress();
                    case DECREASE -> controller.rewindRecipeTick();
                    case STILL -> { }
                }
            }
            // THE CONTROLLER'S POSITION IS IN EVERY LINE —and that is the fix for seven wasted rounds.
            //
            // A recipe name identifies a DEFINITION, not an instance. The self-test writes into the server's
            // default world and never cleans up, so machines from earlier runs keep ticking; a leftover
            // running the same recipe printed `'iron_centrifuge_centrifuge_grass' stalled at 80/120` forever
            // while the machine under test ran a 5-tick recipe and passed. Those lines were indistinguishable
            // from the test's own, and six hypotheses were built on them (§122.3).
            //
            // With the position, a reader can tell one machine from another —or see at a glance that the
            // line is coming from somewhere the test never placed anything.
            trace("'{}' at {} stalled: {} at {}/{}", recipe.registryName(),
                    controller.getBlockPos(), tickFailure, controller.getRecipeTicks(), controller.getRecipeDuration());
            // NAME THE MACHINE AND PRINT THE NUMBERS *AT THE STALL* —not once per JVM (§120.2).
            if (energyFailureKey(recipe).equals(tickFailure)) traceEnergyRefusal(recipe, context, controller);
            // RECIPE FAILURE EVENT (PLAN 529, 531). Upstream fires it from isNotWorking, which runs when
            // the machine is NOT crafting, and IT ACTS ON THE RETURN VALUE: true tears the active recipe down
            // (TileMachineController U211-U214). THE PORT EQUIVALENT OF NOT CRAFTING IS THIS BRANCH --
            // the energy shortfall -- and the port own comment above says exactly what upstream false means:
            // "A shortfall STALLS the recipe instead of failing it, and the machine resumes when power
            // returns." So the default is false, the stall is kept, and ONLY A HANDLER ASKING FOR
            // DESTRUCTION changes that.
            //
            // THE REASON IS WRITTEN (PLAN 712, closing the gap PLAN 711.9 recorded). Upstream returns
            // CraftingStatus.failure("craftcheck.failure.energy.input") from ActiveMachineRecipe.tick U88-U98
            // WITHOUT reaching its own this.tick++, so upstream's status.isCrafting() is false on exactly this
            // hold and MMInfoProvider U246-U252 paints the machine red with that message. The port's
            // statusMessage is upstream's unlocMessage and TileController.isCrafting() is
            // hasActiveRecipe() && statusMessage == null, so writing the key here IS that restoration -- the
            // derived key is energyFailureKey's, whose own record carries the lines. NOTHING ABOUT THE HOLD
            // CHANGES: the write is display only, the craft keeps its progress, and it resumes when the power
            // does (PLAN 534.3).
            controller.setStatusMessage(tickFailure);
            //
            // THE CAUSE COMES FROM THE PORT OWN DIAGNOSTICS: upstream passes status.getUnlocMessage(),
            // and describeBlockers is the port answer to the same question (PLAN 521.2). Nothing here needs
            // the CraftingStatus class.
            hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext failureContext =
                    new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe);
            hellfirepvp.modularmachinery.common.event.recipe.RecipeFailureEvent failureEvent =
                    new hellfirepvp.modularmachinery.common.event.recipe.RecipeFailureEvent(controller,
                            failureContext,
                            // describeBlockers TAKES A MachineContext -- exactly what advance was given,
                            // so no extra plumbing is needed (PLAN 521.2).
                            tickFailure,
                            recipe.cancelOnPerTickFailure());
            failureEvent.postEvent();
            if (failureEvent.isDestructRecipe()) {
                // Upstream destruction, U214: drop the active recipe so it has to restart.
                controller.abandonRecipe();
            }
            return;
        }

        recipe.commands().runTickCommands(controller, controller.getRecipeTicks());

        // THE REASON IS CLEARED ON THE TICK THE ENERGY ARRIVES (PLAN 712). Upstream does not clear separately
        // -- ActiveMachineRecipe.tick OVERWRITES its status every tick, returning CraftingStatus.working()
        // (type CRAFTING, U84/U102) on the success path and failure(...) on the stall -- so a machine that was
        // stalled and now is not answers isCrafting() == true again. This is that overwrite on this port's
        // channel, and it is SCOPED TO THIS METHOD'S OWN KEY: clearing unconditionally would also erase a
        // reason some other hold wrote (the output-room hold below runs later in the same tick and re-writes
        // its own, but a future writer earlier in the tick would be silently eaten).
        if (energyFailureKey(recipe).equals(controller.getStatusMessage())
                || "craftcheck.failure.fluid.input".equals(controller.getStatusMessage())
                || "craftcheck.failure.fluid.output.space".equals(controller.getStatusMessage())) {
            controller.setStatusMessage(null);
        }
        // THE RECIPE TICK BRACKET (PLAN 561, 565). Upstream brackets the work with an event pair inside doSyncStep:
        // onMachineTick(Phase.START) at U123, the work, onMachineTick(Phase.END) at U136. THE PAIR IS THE CONTRACT:
        // a handler may hold the craft back at START and observe at END.
        //
        // try/finally RATHER THAN STRAIGHT-LINE, AND THE DIFFERENCE FROM THE MACHINE TICK BRACKET IS ARGUED, NOT
        // STYLISTIC: THIS PORT HAS AN EARLY return INSIDE THIS REGION (the output-room hold below), WHICH UPSTREAM
        // DOES NOT -- upstream puts that case in a different method entirely (isNotWorking). WITHOUT finally THAT
        // RETURN WOULD SKIP THE END EVENT ENTIRELY, on a path that fires every tick a machine is holding.
        //
        // AND 565 SETTLED THE ONLY OBJECTION: I had claimed finally would also survive a THROW, giving different
        // behaviour from upstream. IT CANNOT -- MachineEvent.postEvent CATCHES AND LOGS ALREADY, so nothing throws
        // out of it to be caught here. The cost I stated does not exist.
        hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext tickContext =
                new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe);
        new hellfirepvp.modularmachinery.common.event.recipe.RecipeTickEvent(controller, tickContext,
                hellfirepvp.modularmachinery.common.event.machine.Phase.START).postEvent();
        try {
        controller.advanceRecipe();
        trace("'{}' {}/{}", recipe.registryName(),
                controller.getRecipeTicks(), controller.getRecipeDuration());

        if (controller.getRecipeTicks() >= controller.getRecipeDuration()) {
            // ASK THE OUTPUTS ONLY, BEFORE PLACING ANYTHING (PLAN section 346).
            //
            // This is upstream's intent -- `RecipeThread.onFinished` (73-78) checks `canFinishCrafting()` and, on
            // failure, holds the craft at 100% until there is room -- implemented for a port whose tick order is
            // NOT upstream's.
            //
            // The difference, and section 345 is the record of getting it wrong twice: this port consumes its
            // inputs UP FRONT (its own header says so), and `Requirement`'s javadoc defines canFinish as a
            // PRE-START question -- "could this recipe run to completion given current contents? ... before
            // stealing any input". After `consumeInputs` has run, an INPUT requirement's canFinish is false
            // forever, and asking it at completion held every machine at 100% producing nothing.
            //
            // Only the OUTPUTS are asked here, and the output question is the one that can still change after
            // consumption: is there room for what the machine is about to make.
            // THE FLUIDS' OWN FIELD IS PUT IN STEP WITH THE PARAMETER THE ITEM PATH USES, for the reason
            // `effectiveParallelism` documents: a fluid requirement's writer (`RequirementFluid.produce`) reads
            // ITS OWN parallelism field rather than the argument, and a RESOLVED (modifier-scaled) copy is a
            // fresh object whose field is still one. Without this line the gate below would reserve
            // `amount * granted` room while the writer placed `amount` -- a second way for the check and the
            // emission to disagree in the very round that removes the first.
            // Completion mirrors upstream finishCrafting: delayed trigger metadata controls start-time IO,
            // but every output requirement is still considered by the final placement pass.
            setParallelism(requirements, controller.getParallelism());
            // RULE A0 -- THE DEVIATION THIS GATE CARRIES (PLAN 704): upstream asks each output requirement
            // the room question ON ITS OWN and then writes them one after another (RequirementItem.doItemIO
            // U286-U292 / RequirementFluid.doFluidIO U166-U177, then RecipeCraftingContext.finishCrafting
            // U386-U421), so the first output eats the room the others were each promised and the tail is
            // destroyed. Asked the WHOLE output set here instead: see outputAllocationFailure, which is the
            // multi-output half of PLAN 683.2 fact 8 (the multiplier half) and reads the SAME granted
            // multiplier emitOutputs reads.
            String allocationFailure = outputAllocationFailure(context, requirements,
                    controller.getParallelism());
            if (allocationFailure != null) {
                trace("'{}' at {} is complete but its outputs have no room together; holding",
                        recipe.registryName(), controller.getBlockPos());
                // THE REASON REACHES THE PLAYER (PLAN 683, DEFECT B, key derivation in outputSpaceKey). The
                // hold was already correct and already upstream; what was missing was any channel from here
                // to a screen, so a player watched a counter climb past its own duration with nothing saying
                // why. The controller now carries the unlocalized key and the screen renders it -- see
                // TileController#statusMessage and ScreenMachineController.
                //
                // SET ON EVERY HOLDING TICK RATHER THAN ONCE: setStatusMessage is a no-op when the value is
                // unchanged, so this costs a comparison and sends nothing, and it cannot be left stale by a
                // second blocking requirement taking over.
                //
                // AND THE KEY NAMES THE REQUIREMENT THAT RAN OUT, derived from its own typeId, so a player
                // reading "item output space" also knows it is an OUTPUT and not an input.
                controller.setStatusMessage(allocationFailure);
                return;     // hold the WHOLE craft: nothing is placed, so the retry places it once
            }
            emitOutputs(recipe, requirements, context, controller.getParallelism());
            recipe.commands().runFinishCommands(controller);
            controller.finishRecipe();
            // RECIPE FINISH EVENT (PLAN 513). Upstream fires it FIRST in onFinished (U300), and this port
            // fires it AFTER finishRecipe because that call IS the finish: upstream onFinished body is
            // the event plus a sync, while here the work -- emitOutputs then finishRecipe -- happens
            // immediately before. Firing before finishRecipe would report a recipe as finished while
            // the controller still considered it running.
            new hellfirepvp.modularmachinery.common.event.recipe.RecipeFinishEvent(controller,
                    new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe))
                    .postEvent();
        }
        } finally {
            // Fires on every exit, including the output-room hold above -- which is the whole reason this is a
            // finally rather than a second call site.
            new hellfirepvp.modularmachinery.common.event.recipe.RecipeTickEvent(controller, tickContext,
                    hellfirepvp.modularmachinery.common.event.machine.Phase.END).postEvent();
        }
    }

    /**
     * Applies the source trigger rule to a resolved requirement list.
     *
     * <p>Source {@code RecipeCraftingContext.ioTick} branches to the {@code PerTick} lifecycle before it
     * checks {@code triggerTime}; continuous requirements therefore run on every recipe tick. Trigger times
     * gate ordinary one-shot requirements (most visibly delayed item inputs), not continuous rates.</p>
     */
    public static List<Requirement> activeRequirementsAtTick(RecipeCraftingContext run,
                                                               List<Requirement> requirements,
                                                               int currentTick) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        return requirements.stream()
                .filter(r -> r instanceof Requirement.PerTick
                        || run == null || run.triggerTime(r) <= 0 || run.triggerTime(r) <= currentTick)
                .toList();
    }

    /** Shared by ordinary and factory controllers; a missing delayed payment must hold progress. */
    public static String processTriggeredInputs(List<Requirement> requirements, RecipeCraftingContext run,
                                                MachineContext context, int parallelism, int tick,
                                                java.util.function.IntPredicate hasTriggered,
                                                java.util.function.IntConsumer markTriggered) {
        setParallelism(requirements, parallelism);
        for (int index = 0; index < requirements.size(); index++) {
            Requirement requirement = requirements.get(index);
            int triggerTime = run == null ? requirement.getTriggerTime() : run.triggerTime(requirement);
            if (triggerTime <= 0 || triggerTime != tick || requirement instanceof Requirement.PerTick
                    || requirement.getIOType() != IOType.INPUT
                    || (!requirement.isTriggerRepeatable() && hasTriggered.test(index))) continue;
            // Check the whole granted batch before any direct extraction can partially consume it.
            if (!requirement.canStart(context)
                    || requirement instanceof Requirement.Parallelizable batch
                    && batch.getMaxParallelism(context, Math.max(1, parallelism), List.of(requirement)) < Math.max(1, parallelism))
                return failureKey(requirement, null);
            if (!consumeInputs(List.of(requirement), context, parallelism)) return failureKey(requirement, null);
            markTriggered.accept(index); // Failed payments are never remembered as successful.
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Requirement evaluation
    // ------------------------------------------------------------------

    /**
     * The <b>existing shipped</b> message key for "this requirement has nowhere to put its output".
     *
     * <p><b>RULE B: THE KEY IS DERIVED FROM THE REQUIREMENT'S OWN NAMES, NOT WRITTEN OUT HERE.</b> Both halves
     * are already on {@link Requirement}: {@link Requirement#typeId()} is the registry name upstream derives
     * keys from —{@code RequirementItem.getMissingComponentErrorMessage} (upstream U238-U242) builds
     * {@code String.format("component.missing.%s.%s.%s", namespace, path, ioType)} —and
     * {@link Requirement#getIOType()} is the direction. Applying upstream's own derivation to the failure
     * family gives {@code craftcheck.failure.<path>.<io>.space}, which is exactly the file's existing naming:</p>
     *
     * <pre>
     * upstream RequirementItem  U291  CraftCheck.failure("craftcheck.failure.item.output.space")
     * upstream RequirementFluid U175  CraftCheck.failure("craftcheck.failure.fluid.output.space")
     *   and both language files already carry all three of
     *     craftcheck.failure.item.output.space / fluid.output.space / energy.output.space
     * </pre>
     *
     * <p>A literal pair of strings here would cover the two cases a player complains about and silently produce
     * a raw key for the third (an energy output hatch has a room check too, and the port ships
     * {@code craftcheck.failure.energy.output.space}). The design is what makes all three work, and
     * {@code MachineSelfTest.verifyTranslationKeys} asserts the derived keys resolve —without that guard a
     * derivation is a way to ship raw key text into a GUI with nothing reporting it.</p>
     *
     * @param requirement the OUTPUT requirement that refused; its {@code canFinish} is already known false
     * @return the unlocalized key, e.g. {@code craftcheck.failure.item.output.space}
     */
    public static String outputSpaceKey(Requirement requirement) {
        return failureKey(requirement, requirement.getIOType() == null ? "output" : null);
    }

    /**
     * <b>RULE B -- THE ONE DERIVATION, WITH {@code .space} AS THE PART THAT DEPENDS ON THE DIRECTION AND NOT
     * ON THE KEY.</b> Upstream's failure keys are {@code craftcheck.failure.<path>.<io>} for a SHORTFALL and
     * {@code craftcheck.failure.<path>.<io>.space} for a FULL-FOR-OUTPUT, and the two shipped examples read
     * exactly that way:
     *
     * <pre>
     * upstream RequirementEnergy U140  CraftCheck.failure("craftcheck.failure.energy.input")
     * upstream RequirementEnergy U145  CraftCheck.failure("craftcheck.failure.energy.output.space")
     * upstream RequirementItem   U291  CraftCheck.failure("craftcheck.failure.item.output.space")
     * </pre>
     *
     * <p>So the suffix is <b>part of the OUTPUT-FULL key and not of the key family</b>: an input shortfall has
     * no room to run out of, which is why upstream writes {@code .energy.input} with no {@code .space}. Both
     * files already ship {@code craftcheck.failure.energy.input} ({@code "Not enough energy!"} /
     * {@code "缺少能量输入！"}), so the energy stall's reason is an EXISTING key reached through the existing
     * derivation -- not a second convention and not a new string.</p>
     *
     * <p>{@code ioOverride} exists because the output-room hold must keep answering {@code output} for a
     * requirement whose own direction is unset, which is the behaviour {@link #outputSpaceKey} documented and
     * this round did not change. Passing {@code null} asks the requirement.</p>
     */
    private static String failureKey(Requirement requirement, String ioOverride) {
        String typeId = requirement.typeId();               // "modularmachinery:item" / ":fluid" / ":energy"
        int colon = typeId.indexOf(':');
        String path = colon < 0 ? typeId : typeId.substring(colon + 1);
        if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray) path = "item";
        String io = ioOverride != null
                ? ioOverride
                : (requirement.getIOType() == null
                        ? "output"
                        : requirement.getIOType().name().toLowerCase(java.util.Locale.ROOT));
        String key = "craftcheck.failure." + path + "." + io;
        return "output".equals(io) ? key + ".space" : key;
    }

    /**
     * <b>RULE A0 -- UPSTREAM WRITES A FAILURE ON THE ENERGY-SHORTFALL PATH, SO THIS IS A RESTORATION AND NOT A
     * NEW SIGNAL.</b> The chain was read end to end before this method was added:
     *
     * <ol>
     *   <li><b>upstream {@code ActiveMachineRecipe.tick} U88-U98</b> -- {@code if ((check =
     *       context.ioTick(tick)).isFailure())} fires the machine's failure action and then
     *       {@code return CraftingStatus.failure(check.getFirstErrorMessage(""));} <b>WITHOUT reaching its own
     *       {@code this.tick++} (U101)</b>. The craft is therefore HELD, not destroyed: the counter does not
     *       move and the active recipe survives for the next tick.</li>
     *   <li><b>upstream {@code CraftingStatus.failure} U31-U33</b> -- returns a status of type
     *       {@code NO_RECIPE}, and {@code isCrafting()} (U53-U55) is true ONLY for type {@code CRAFTING}. So
     *       on that path upstream answers <b>false</b> -- which is exactly what {@code MMInfoProvider}
     *       U246-U252 turns into {@code TextFormatting.RED} and the three FAILURE bar colours.</li>
     *   <li><b>the message upstream carries</b> -- {@code ioTick} returns the first failing requirement's own
     *       unlocalized key ({@code RecipeCraftingContext} U253-U256 / U286-U291), and for a per-tick energy
     *       input that is {@code craftcheck.failure.energy.input}
     *       ({@code RequirementEnergy.doEnergyIO} U138-U140).</li>
     *   <li><b>the port's equivalent</b> -- {@code TileController#statusMessage} is the port's field for
     *       upstream's {@code unlocMessage}, and {@code TileController.isCrafting()} is
     *       {@code hasActiveRecipe() && statusMessage == null}. WRITING THIS KEY ON THE STALL IS THEREFORE
     *       WHAT MAKES THE RED APPEAR: nothing else has to change, and the earlier reading of this branch as
     *       "a signal the engine does not make" was wrong.</li>
     * </ol>
     *
     * <p><b>DISPLAY ONLY -- the UPS SEMANTICS ARE UNTOUCHED.</b> This writes a string and returns; it does not
     * call {@code abandonRecipe}, does not touch {@code recipeTicks}, and does not change the return value
     * that keeps the craft alive. A starved machine still holds its progress and still resumes when power
     * returns (PLAN 534.3), which the suite's own
     * {@code "shortfall stalls without destroying the recipe"} check asserts on the same tick this method is
     * called from.</p>
     *
     * @param recipe the recipe whose tick could not be paid for
     * @return the shipped unlocalized key for an input shortfall, e.g. {@code craftcheck.failure.energy.input}
     */
    public static String energyFailureKey(MachineRecipe recipe) {
        for (Requirement requirement : recipe.requirements()) {
            if (requirement instanceof RequirementEnergy energy && energy.getIOType() == IOType.INPUT) {
                return failureKey(requirement, null);
            }
        }
        // No declared input energy is the one case where the derivation has no requirement to read its type
        // from, so the type name comes from the requirement class that was being looked for rather than from a
        // literal "energy" that would silently disagree if that class were renamed.
        return failureKey(new RequirementEnergy(IOType.INPUT, 0), null);
    }

    /**
     * Simulates output placement in writer order, reserving individual slots and tanks.
     * Reservations include NBT identity, so distinct products cannot claim the same empty space.
     * Guaranteed outputs that do not fit keep the craft waiting; probabilistic outputs retain
     * the existing best-effort policy. This does not rearrange existing inventory contents.
     *
     * @return the refusing output's translation key, or null if the batch can proceed
     */
    public static String outputAllocationFailure(MachineContext context, List<Requirement> outputs,
                                                 int parallelism) {
        int copies = Math.max(1, parallelism);
        var checkedBatchTypes = new java.util.HashSet<String>();
        for (Requirement r : outputs) {
            if (!(r instanceof hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick)
                    && r instanceof hellfirepvp.modularmachinery.common.crafting.BatchIORequirement batch
                    && checkedBatchTypes.add(r.typeId())) {
                String failure = batch.outputFailure(context, outputs, copies);
                if (failure != null) return failure;
            }
        }

        // PASS 1 -- FLUIDS, the order emitOutputs places them in. THE ROOM IS THE TANKS' SUM, WALKED THE WAY THE
        // WRITER WALKS THEM (PLAN §714): RequirementFluid.outputRoomFor asks each output tank for the REMAINDER
        // of this batch in turn -- the shape upstream's HybridFluidUtils.doSimulateDrainOrFill (U31-U47) and
        // RequirementFluid.produce both use -- and charges what earlier fluid outputs of this same emission
        // already reserved against it. Before §714 this pass asked whether ONE tank would accept the whole batch
        // plus the reservation, which REFUSED a batch the writer places across two tanks: the fluid-output check
        // measured exactly that (160 mB held for 80 ticks over two 100 mB tanks, nothing placed and nothing lost).
        java.util.IdentityHashMap<net.minecraftforge.fluids.capability.IFluidHandler, java.util.Map<Integer, net.minecraftforge.fluids.FluidStack>> fluidReserved =
                new java.util.IdentityHashMap<>();
        for (Requirement requirement : outputs) {
            if (requirement.getIOType() == IOType.OUTPUT && requirement.isIgnoreOutputCheck()) continue;
            if (!(requirement instanceof RequirementFluid fluid) || fluid.getIOType() != IOType.OUTPUT
                    || !fluid.isResolvable()) {
                continue;
            }
            int batch = fluid.getAmount() * (fluid.isParallelizeUnaffected() ? 1 : copies);
            if (batch <= 0) {
                continue;
            }
            // The stack comes from the requirement's own asStack, so the fluid asked about here is the fluid it
            // would place -- not a second resolution of the same id.
            net.minecraftforge.fluids.FluidStack probe = fluid.asStack();
            int fits = RequirementFluid.outputRoomFor(context.forSelector(fluid.getSelectorTag()),
                    new net.minecraftforge.fluids.FluidStack(probe, batch), batch, fluidReserved);
            if (fits < batch) {
                // THE SAME "CHARGED BUT CANNOT BLOCK" RULE AS THE ITEMS, one paragraph down: a probabilistic
                // fluid output is reserved room when there is room and simply not placed when there is not,
                // because whether it exists is a roll that has not happened yet. A guaranteed one holds.
                if (fluid.isGuaranteed()) {
                    return outputSpaceKey(requirement);
                }
                continue;
            }
        }

        // Reserve per slot, including NBT identity, in the same order as the output writer.
        return hellfirepvp.modularmachinery.common.crafting.helper.ItemOutputPlan.failure(context, outputs, copies);
    }

    /**
     * Whether every requirement is satisfied, RESOLVING the machine's active modifiers first.
     *
     * <p><b>THE STATELESS ENTRY POINT: it resolves by itself</b>, which is what a caller with a context but no
     * running craft wants -- {@code runnableRecipes} and the self-test are those callers. The tick path does NOT
     * come through here: see the four-argument overload below for why, and note that both reach the same
     * evaluation through the same two calls ({@link #activeModifiers} and
     * {@code RecipeCraftingContext.withModifiers}).</p>
     *
     * @param checkFinish when true, also require every requirement's {@link Requirement#canFinish}
     */
    public static boolean satisfies(MachineRecipe recipe, DynamicMachine machine, MachineContext context,
                                    boolean checkFinish) {
        // STAGE 2 of the crafting rework (PLAN section 228-229): the loop moved to RecipeCraftingContext.
        //
        // The semantics are IDENTICAL -- canStart always, canFinish when asked, nothing taken until both
        // agree -- and that is the point: this is the same evaluation, given an object that a thread can own.
        // A thread-per-recipe factory needs each run to have its own requirement list; a static helper taking
        // a recipe cannot express that, and a context that does nothing but wrap the same loop would be the
        // "helper with no callers" this port has already shipped twice (section 199, section 224).
        //
        // So the context is exercised on every evaluation the ticker performs, rather than existing beside it.
        // STAGE 4b/ii (PLAN section 235): only the modifiers whose STRUCTURE CONDITION HOLDS are applied --
        // through activeModifiers, so the condition is tested in ONE place for every caller.
        return satisfies(recipe, RecipeCraftingContext.withModifiers(recipe,
                activeModifiers(machine, context), context), context, checkFinish);
    }

    /**
     * Whether every requirement is satisfied, over an <b>ALREADY RESOLVED</b> requirement list.
     *
     * <p><b>RULE B -- ONE EVALUATION, AND THE CALLER SUPPLIES THE LIST SO THERE IS ONLY ONE OF IT.</b> The list
     * is produced by {@code RecipeCraftingContext.withModifiers} from the machine's active modifiers, and this
     * overload does not invent a second way to decide what a requirement is: it asks the very objects the
     * emission will write (PLAN 723.3). A modifier that doubles an output therefore doubles BOTH the room the
     * check demands and the amount the emitter places, which is the whole point.</p>
     *
     * <p><b>AND THE EVENT CARRIES THAT SAME CONTEXT</b>, which is not convenience: upstream handlers can act on
     * it ({@code setParallelism} writes to it), so a second context built at the call site would look identical
     * and be a DIFFERENT object, silently breaking the linkage (PLAN 570).</p>
     *
     * @param run         this tick's resolved requirements for {@code recipe}
     * @param checkFinish when true, also require every requirement's {@link Requirement#canFinish}
     */
    public static boolean satisfies(MachineRecipe recipe, RecipeCraftingContext run, MachineContext context,
                                    boolean checkFinish) {
        // RECIPE CHECK EVENT (PLAN 568, 570). Upstream brackets checkStartResult with it (U1022 and U1039) and READS
        // THE RESULT BACK (U1042-U1046): a handler can REFUSE a craft, which is what setFailed is for.
        //
        // THE CONTROLLER IS RESOLVED BY POSITION (570): MachineContext carries level and controllerPos and NOT the
        // controller itself (it exposes only getControllerPos, T87), so no signature has to change to reach it. A
        // MACHINE THAT IS NO LONGER THERE SIMPLY GETS NO EVENT -- the same choice the component dispatch made at 494.
        hellfirepvp.modularmachinery.common.tiles.TileController checkController =
                context.getLevel().getBlockEntity(context.getControllerPos())
                        instanceof hellfirepvp.modularmachinery.common.tiles.TileController c ? c : null;
        if (checkController == null) {
            return checkFinish ? run.canFinish(context) : run.canStart(context);
        }

        new hellfirepvp.modularmachinery.common.event.recipe.RecipeCheckEvent(checkController, run,
                hellfirepvp.modularmachinery.common.event.machine.Phase.START).postEvent();

        boolean satisfied = checkFinish ? run.canFinish(context) : run.canStart(context);

        hellfirepvp.modularmachinery.common.event.recipe.RecipeCheckEvent endCheck =
                new hellfirepvp.modularmachinery.common.event.recipe.RecipeCheckEvent(checkController, run,
                        hellfirepvp.modularmachinery.common.event.machine.Phase.END);
        endCheck.postEvent();

        // A HANDLER OVERRIDES THE EVALUATION, exactly as upstream U1042 does: a refusal wins over a satisfied check.
        return endCheck.isFailure() ? false : satisfied;
    }

    /**
     * Pushes a granted multiplier into a requirement list, through the requirement's own setter.
     *
     * <p><b>ONE METHOD FOR BOTH VIEWS OF A RUN.</b> A recipe has its DEFINITION list
     * ({@code recipe.requirements()}) and -- once a modifier applies -- the RESOLVED copies
     * {@code RecipeCraftingContext.withModifiers} built for it. Both are walked by something: the reduction
     * (which computes the grant) walks the definitions, while the emission's FLUID half reads its requirement's
     * own field ({@code RequirementFluid.produce} multiplies by {@code parallelism}). A resolved copy is a fresh
     * object whose field is still one, so without this call a modifier plus a parallel craft would reserve
     * {@code amount * granted} room and place {@code amount} -- two lists disagreeing, which is the defect this
     * round exists to remove. THE VALUE IS WRITTEN BY ONE METHOD so the two views cannot be set by two rules
     * (PLAN 629).</p>
     *
     * <p><b>AND IT IS PUBLIC BECAUSE THE FACTORY NEEDS THE SAME WRITE (PLAN 723.3).</b> {@code FactoryTicker}
     * resolves a FRESH list per run per tick, and a fresh list's fluid requirements carry the field at one, so
     * the factory has exactly the exposure this call closes on the machine path -- a run granted N would
     * reserve {@code amount * N} room and place {@code amount}. Same value, same rule, one method.</p>
     */
    public static void setParallelism(List<Requirement> requirements, int parallelism) {
        for (Requirement requirement : requirements) {
            if (requirement instanceof Requirement.Parallelizable parallelizable) {
                parallelizable.setParallelism(parallelism);
            }
        }
    }

    /**
     * The parallelism this machine can actually run at (upstream TileMultiblockMachineController U293-U304).
     *
     * <pre>
     * upstream  int parallelism = foundMachine.getInternalParallelism();
     *           int maxParallelism = foundMachine.getMaxParallelism();
     *           for (provider : foundParallelControllers) {
     *               parallelism += provider.getParallelism();
     *               if (parallelism >= maxParallelism) { return maxParallelism; }
     *           }
     *           return Math.max(1, parallelism);
     * </pre>
     *
     * <p><b>IT COMPUTES RATHER THAN STORES, WHICH IS WHAT THE BODY SHOWS AND WHAT 587 GOT WRONG FIRST:</b> the
     * accumulator is a local, so nothing is cached at formation time and a controller added or removed later is
     * picked up on the next call. THE CEILING CLAMP RETURNS EARLY, so a machine already at its ceiling stops
     * summing -- irrelevant to the result and copied anyway rather than tidied.</p>
     *
     * <p><b>AND IT LIVES IN THE TICKER RATHER THAN ON TileController, for a dependency reason recorded at 599.2:</b>
     * this class already resolves the definition at K123 and already holds the MachineContext that carries the
     * component positions, so the sum costs no new dependency anywhere. A getter on the controller would have had
     * to reach MachineReloadListener from a tile.</p>
     *
     * <p>THE FLOOR OF 1 IS UPSTREAM OWN and it is load-bearing: a non-parallelizable machine and a definition-less
     * machine both come out at 1, which is what every caller downstream treats as "just one craft".</p>
     */
    /**
     * The controller own facing, read the way the port already reads it elsewhere (ControllerInteraction U102-U103
     * and CommandModularMachinery U315-U316 use exactly these two lines): from the block state, with NORTH as the
     * fallback for a state that somehow lacks the property.
     *
     * <p>IT IS DERIVED HERE RATHER THAN TAKEN FROM MachineContext BECAUSE THE RE-BIND RUNS BEFORE THE CONTEXT
     * EXISTS IN tick -- the context is built at K171 and the re-bind has to happen earlier, since nothing may
     * consult a machine before its components are known. The alternative was moving the re-bind after the context
     * and leaving a window where the machine is formed with no components, which is the very state 627 is about.</p>
     */
    public static net.minecraft.core.Direction facingOf(TileController controller) {
        net.minecraft.world.level.block.state.BlockState state = controller.getBlockState();
        return state.hasProperty(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                ? state.getValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                : net.minecraft.core.Direction.NORTH;
    }

    /**
     * The machine's parallelism ceiling, BEFORE any requirement has reduced it (upstream
     * {@code TileMultiblockMachineController.getMaxParallelism()}, whose body is K601-K611 below / PLAN 599).
     *
     * <p>Split out of {@code effectiveParallelism} for the FACTORY (PLAN 662, rule B): upstream's factory asks
     * this same question to compute how much of the machine's parallelism is still unspent
     * ({@code TileFactoryController.getAvailableParallelism}, U400-U418), so the factory must be able to reach
     * the number the single-craft path starts from rather than recompute it and risk disagreeing. Behaviour is
     * unchanged: the 3-argument {@code effectiveParallelism} below now starts from exactly this value.</p>
     */
    public static int machineParallelism(DynamicMachine machine, MachineContext context) {
        if (machine == null || !machine.isParallelizable()) {
            return 1;
        }
        long parallelism = Math.max(0, machine.getInternalParallelism());
        int ceiling = Math.max(1, machine.getMaxParallelism());
        if (parallelism >= ceiling) return ceiling;
        for (net.minecraft.core.BlockPos componentPos : context.getComponentPositions()) {
            if (context.getLevel().getBlockEntity(componentPos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.TileParallelController pc) {
                parallelism += pc.getParallelism();
                if (parallelism >= ceiling) return ceiling;
            }
        }
        return (int) Math.max(1L, parallelism);
    }

    /**
     * The parallelism this craft may ACTUALLY run at, given a starting point.
     *
     * @param requirements        the list the reduction WALKS and the list the granted value is written into:
     *                            the tick's RESOLVED requirements (PLAN 723.3). It is the same list the work path
     *                            consumes and emits, so the value that governs the work is decided over the
     *                            requirements the work will actually use -- a scaled input is reduced against its
     *                            scaled amount, not against the definition's.
     * @param startingParallelism the ceiling to reduce: the machine's own for a single craft, or the factory's
     *                            remaining budget for a factory run (upstream's
     *                            {@code FactoryRecipeSearchTask} is constructed with
     *                            {@code factory.getAvailableParallelism()}, L465-U470)
     */
    public static int effectiveParallelism(MachineRecipe recipe, List<Requirement> requirements,
                                           MachineContext context, int startingParallelism) {
        // Upstream RecipePrimer.setParallelized(false) disables the recipe-level batch expansion while
        // leaving the machine's parallel controller installed for other recipes.  The requirements still
        // go through the normal reduction path, but their ceiling starts at one so no input/output is
        // multiplied for this recipe.
        int parallelism = recipe.parallelized() ? startingParallelism : 1;
        // THE MIN REDUCTION AND THE DISTRIBUTION (PLAN 597.2, upstream M434-M447 and U457-U462), which is what turns
        // the value above INTO the effective parallelism rather than merely its starting point.
        //
        // UPSTREAM: getMaxParallelism(parallelizable) starts from the recipe ceiling and calls Math.min against each
        // parallelizable requirement own capacity (M447); then setParallelism PUSHES the result into every such
        // requirement (U457-U462), which is why there is no parallelism field on the context at all -- the value
        // LIVES IN THE REQUIREMENTS (606).
        //
        // A REQUIREMENT THAT CANNOT SUPPLY EVEN ONE COPY RETURNS ZERO, AND ZERO HERE MEANS THE CRAFT DOES NOT START.
        // That is upstream own behaviour (M485-M492 reduces and retries) and it is the reason this is a reduction
        // rather than a multiplication: a machine asked for eight copies with supplies for three runs THREE.
        int resolved = parallelism;
        // WHY THE FIRST VERSION OF THIS DIAGNOSTIC COULD NOT ANSWER THE QUESTION IT WAS ADDED FOR (PLAN 651).
        //
        // IT LOGGED ONLY WHEN `resolved < before && before > 1` -- SO IT STAYED SILENT EXACTLY WHEN `before` WAS
        // ALREADY 1, WHICH IS THE CASE A PLAYER REPORTS: "parallelism is only 1". That is the same shape as 471
        // (the self-test forcing roll to 0 and making the probabilistic path unreachable) FOR THE FIFTH TIME: AN
        // INSTRUMENT THAT DOES NOT FIRE IN THE CASE THAT MATTERS IS NOT AN INSTRUMENT.
        //
        // SO THE SOURCE IS REPORTED NOW, NOT ONLY THE REDUCTION. It costs one line per craft start, and a craft
        // start is rare -- WHEREAS NOT KNOWING WHY THE ANSWER IS 1 COSTS A ROUND TRIP THROUGH THE USER.
        if (parallelism <= 1) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.info(
                    "[MMCE] parallelism {} for recipe {} (no parallel controller, or the machine declares none)",
                    parallelism, recipe.registryName());
        }
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof Requirement.Parallelizable parallelizable)) {
                continue;
            }
            int before = resolved;
            resolved = parallelizable.getMaxParallelism(context, resolved, requirements);
            // A DIAGNOSTIC THAT NAMES THE REDUCING REQUIREMENT (PLAN 631). "Parallelism did not happen" has at
            // least two causes -- the ceiling arrived as one, or a requirement reduced it -- and they need
            // different fixes. THIS LINE MAKES THE SECOND ONE VISIBLE BY NAME AND BY NUMBER, which is the same
            // shape as the energy shortfall check at 540: report the value that decided, not that a decision was
            // made.
            // LOGGED WHEN IT MATTERS RATHER THAN ONLY UNDER TRACE, because the interesting case is a
            // CLIENT one: "I set parallelism 2 and got 1" cannot be diagnosed with a flag that only the
            // self-test sets. THIS FIRES AT MOST ONCE PER CRAFT START and only when the ceiling was above
            // one and a requirement reduced it -- the exact situation a player would report.
            if (resolved <= 0 || (resolved < before && before > 1)) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.info(
                        "[MMCE] parallelism {} reduced to {} by requirement {}",
                        before, resolved, requirement.typeId());
            }
            if (resolved <= 0) {
                break;
            }
        }
        if (resolved <= 0) {
            return 0;
        }
        // THE GRANTED VALUE IS WRITTEN INTO THE LIST THE WORK PATH READS (see setParallelism's javadoc).
        setParallelism(requirements, resolved);
        return Math.max(1, resolved);
    }

    /**
     * Removes the INPUT requirements' items and fluids from the machine's input components.
     *
     * @param requirements the tick's RESOLVED requirements (PLAN 723.3): the machine takes what the check
     *                     demanded, so a modifier that scales an input is paid for at the scaled amount rather
     *                     than at the definition's
     * @return false when the inputs could not all be taken, in which case the caller must not start —
     *         a partially consumed recipe is worse than one that never began
     */
    public static boolean consumeInputs(List<Requirement> requirements, MachineContext context, int parallelism) {
        var batchPlans = new java.util.LinkedHashMap<String, hellfirepvp.modularmachinery.common.crafting.BatchIORequirement.Plan>();
        for (Requirement r : requirements) {
            if (!(r instanceof hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick)
                    && r instanceof hellfirepvp.modularmachinery.common.crafting.BatchIORequirement batch
                    && !batchPlans.containsKey(r.typeId())) {
                var plan = batch.planInputs(context, requirements, parallelism);
                if (!plan.valid()) return false;
                batchPlans.put(r.typeId(), plan);
            }
        }
        var fluidPlan = hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan.required(requirements)
                ? hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan.plan(context, requirements, parallelism) : null;
        if (fluidPlan != null && !fluidPlan.valid()) return false;
        boolean hasTools = hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.requiresPlan(requirements);
        if (hasTools) {
            var planned = hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.plan(
                    context, requirements, parallelism, true);
            if (!planned.valid() || !planned.execute()) return false;
        }
        // THE MULTIPLIER IS PASSED DOWN RATHER THAN LOOKED UP (PLAN 597.2, 498). The value was decided once,

        // before anything was taken, and a second lookup here could disagree with it -- which is the whole reason

        // upstream pushes it into the requirements instead of reading it back.
        for (Requirement requirement : requirements) {
            if (requirement instanceof RequirementItem item && item.getIOType() == IOType.INPUT) {
                if (!hasTools && !extract(item, context, parallelism)) {
                    return false;
                }
            } else if (requirement instanceof RequirementFluid fluid
                    && fluid.getIOType() == IOType.INPUT) {
                if (fluidPlan != null) continue;
                // Fluids are taken in one go, like items: only a per-tick resource (energy) is drawn
                // incrementally. See RequirementFluid#consume.
                if (!fluid.consume(context)) {
                    return false;
                }
            }
            // One-shot extension channels commit their grouped reservations after the built-in inputs.
        }
        if (fluidPlan != null && !fluidPlan.execute()) return false;
        for (var plan : batchPlans.values()) if (!plan.execute()) return false;
        return true;
    }

    private static boolean extract(RequirementItem requirement, MachineContext context, int parallelism) {
        context = context.forSelector(requirement.getSelectorTag());
        int remaining = requirement.getAmount() * (requirement.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
        // N COPIES ARE TAKEN, which is what makes the parallelism visible on the input side (598). A craft

        // granted N by the min reduction must consume N times its declared amount or it would produce N for the

        // price of one -- and the reduction is what guarantees the bus can supply them.
        var handlers = context.capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                MachineContext.ComponentKind.INPUT);

        for (var handler : handlers) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!requirement.matches(context, stack)) {
                    continue;
                }
                // extractItem respects the handler's own rules, so the machine cannot take more than a
                // slot legitimately allows.
                ItemStack taken = handler.extractItem(slot, remaining, false);
                remaining -= taken.getCount();
            }
        }
        return remaining <= 0;
    }

    /** @return true when this tick's energy was available and has been drawn */
    /**
     * Reports an energy refusal with the machine named and the numbers it compared.
     *
     * <p><b>Why this exists.</b> The previous diagnostic fired once per JVM from a static flag and printed no
     * machine name, so it reported the first energy requirement the server saw rather than the one that
     * failed (§120.2). It was read as authoritative for three rounds and pointed at the wrong recipe.
     *
     * <p>Two deliberate properties, both learned from that:
     *
     * <ul>
     *   <li><b>It names the recipe.</b> A report without its subject is read as being about whatever the
     *       reader is investigating —the same defect as §95's unqualified texture names and §117's
     *       unqualified "energy shortfall".</li>
     *   <li><b>It is rate-limited per recipe, not silenced globally.</b> The first refusal for each recipe is
     *       reported, so a second machine failing later is still visible, while the 947-repeat flood this
     *       port produced (§117) does not recur.</li>
     * </ul>
     */
    private static void traceEnergyRefusal(MachineRecipe recipe, MachineContext context,
                                           TileController controller) {
        if (!TRACE || !energyRefusalsReported.add(recipe.registryName())) {
            return;
        }
        try {
            var storages = context.capabilities(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
                    MachineContext.ComponentKind.INPUT);
            StringBuilder sb = new StringBuilder();
            sb.append("energy refusal: recipe='").append(recipe.registryName())
                    .append("' at ").append(controller.getBlockPos())
                    .append(" progress ").append(controller.getRecipeTicks())
                    .append('/').append(controller.getRecipeDuration())
                    .append(" inputStorages=").append(storages.size());
            for (var storage : storages) {
                sb.append(" [stored=").append(storage.getEnergyStored())
                        .append(" max=").append(storage.getMaxEnergyStored())
                        .append(" canExtract=").append(storage.canExtract()).append(']');
            }
            for (Requirement r : recipe.requirements()) {
                if (r instanceof RequirementEnergy e && e.getIOType() == IOType.INPUT) {
                    sb.append(" needed/tick=").append(e.getEnergyPerTick())
                            .append(" shortfallTotal=").append(e.getEnergyPerTick());
                }
            }
            trace(sb.toString());
        } catch (RuntimeException ex) {
            // A diagnostic that can throw would hide the information it exists to provide.
            trace("energy refusal for '{}': diagnostic failed: {}", recipe.registryName(), ex.toString());
        }
    }

    /** Pays one advancing tick; a completed recipe waiting for outputs must not pay its rate again. */
    public static String processPerTick(MachineRecipe recipe, List<Requirement> requirements,
                                       MachineContext context, int parallelism, boolean complete) {
        if (complete) return null;
        boolean hasFluidRate = requirements.stream().anyMatch(r -> r instanceof
                hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick);
        boolean hasSelector = requirements.stream().anyMatch(r -> r.getSelectorTag() != null);
        boolean hasEnergyOutput = requirements.stream().anyMatch(r -> r instanceof RequirementEnergy && r.getIOType() == IOType.OUTPUT);
        List<hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick> perTick = requirements.stream()
                .filter(hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick.class::isInstance)
                .map(hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick.class::cast).toList();
        if (!hasFluidRate && !hasSelector && !hasEnergyOutput && perTick.isEmpty()) {
            return consumeEnergyTick(recipe, context, parallelism) ? null : energyFailureKey(recipe);
        }
        // Mirror ComponentRequirement.PerTick's lifecycle around the one-tick transaction.  Fluid rates
        // remain grouped below, while extension requirements can perform their own context-based IO here.
        for (var requirement : perTick) {
            requirement.resetIOTick(context);
            requirement.startIOTick(context, 1F);
        }
        var fluids = hellfirepvp.modularmachinery.common.crafting.helper.FluidTickIO.plan(context, requirements, parallelism);
        if (fluids.failure() != null) {
            resetPerTick(perTick, context);
            return fluids.failure();
        }
        Object gases = null;
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
            try {
                Class<?> io = Class.forName("hellfirepvp.modularmachinery.integration.mekanism.GasTickIO");
                gases = io.getMethod("plan", MachineContext.class, List.class, int.class)
                        .invoke(null, context, requirements, parallelism);
                if (!(Boolean) gases.getClass().getMethod("valid").invoke(gases)) {
                    resetPerTick(perTick, context);
                    return (String) gases.getClass().getMethod("failure").invoke(gases);
                }
            } catch (ReflectiveOperationException ex) {
                resetPerTick(perTick, context);
                return "craftcheck.failure.gas.input";
            }
        }
        var extensionTicks = new java.util.LinkedHashMap<String, hellfirepvp.modularmachinery.common.crafting.TickBatchRequirement.TickPlan>();
        for (var requirement : perTick) {
            if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.TickBatchRequirement batch
                    && !extensionTicks.containsKey(requirement.typeId())) {
                var plan = batch.planTick(context, requirements, parallelism);
                if (!plan.valid()) { resetPerTick(perTick, context); return plan.failure(); }
                extensionTicks.put(requirement.typeId(), plan);
            }
        }
        var energyDraws = new java.util.LinkedHashMap<
                hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch, Integer>();
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementEnergy energy) || energy.getIOType() != IOType.INPUT) continue;
            long remaining = (long) Math.max(0, energy.getEnergyPerTick())
                    * (energy.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            for (var hatch : context.forSelector(energy.getSelectorTag()).internalEnergyHatches()) {
                if (hatch.getIOType() != IOType.INPUT && hatch.getIOType() != null) continue;
                int reserved = energyDraws.getOrDefault(hatch, 0);
                int room = hatch.consumeInternal(Integer.MAX_VALUE, true) - reserved;
                int take = (int) Math.min(remaining, Math.max(0, room));
                if (take > 0) energyDraws.put(hatch, reserved + take);
                remaining -= take;
                if (remaining == 0) break;
            }
            if (remaining > 0) {
                resetPerTick(perTick, context);
                return energyFailureKey(recipe);
            }
        }
        var energyFills = new java.util.LinkedHashMap<hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch, Integer>();
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementEnergy energy) || energy.getIOType() != IOType.OUTPUT) continue;
            long remaining = (long) Math.max(0, energy.getEnergyPerTick())
                    * (energy.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            for (var hatch : context.forSelector(energy.getSelectorTag()).internalEnergyHatches()) {
                if (hatch.getIOType() != IOType.OUTPUT && hatch.getIOType() != null) continue;
                int prior = energyFills.getOrDefault(hatch, 0);
                int room = Math.max(0, hatch.produceInternal(Integer.MAX_VALUE, true) - prior);
                int take = (int) Math.min(remaining, room);
                if (take > 0) { energyFills.put(hatch, prior + take); remaining -= take; }
                if (remaining == 0) break;
            }
            if (remaining > 0 && !energy.isIgnoreOutputCheck()) {
                resetPerTick(perTick, context);
                return "craftcheck.failure.energy.output.space";
            }
        }
        // Simulation and commit run together on the server thread, with no event callback in between.
        energyDraws.forEach((hatch, amount) -> hatch.consumeInternal(amount, false));
        if (!fluids.execute()) {
            resetPerTick(perTick, context);
            return "craftcheck.failure.fluid.input";
        }
        if (gases != null) {
            try {
                if (!(Boolean) gases.getClass().getMethod("execute").invoke(gases)) {
                    resetPerTick(perTick, context);
                    return "craftcheck.failure.gas.input";
                }
            } catch (ReflectiveOperationException ex) {
                resetPerTick(perTick, context);
                return "craftcheck.failure.gas.input";
            }
        }
        for (var plan : extensionTicks.values()) {
            if (!plan.execute()) { resetPerTick(perTick, context); return plan.failure() == null ? "craftcheck.failure.component" : plan.failure(); }
        }
        energyFills.forEach((hatch, amount) -> hatch.produceInternal(amount, false));
        for (var requirement : perTick) {
            // The built-in fluid-rate requirement was already committed by the shared planner. Other
            // batch requirements own their complete operation and must receive doIOTick themselves.
            if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick
                    || requirement instanceof hellfirepvp.modularmachinery.common.crafting.TickBatchRequirement
                    || "modularmachinery:gas_pertick".equals(requirement.typeId())) {
                continue;
            }
            String failure = requirement.doIOTick(context, parallelism);
            if (failure != null) {
                resetPerTick(perTick, context);
                return failure;
            }
        }
        for (var requirement : perTick) {
            String failure = requirement.resetIOTick(context);
            if (failure != null) return failure;
        }
        return null;
    }

    private static void resetPerTick(
            List<hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick> requirements,
            MachineContext context) {
        for (var requirement : requirements) requirement.resetIOTick(context);
    }

    public static boolean consumeEnergyTick(MachineRecipe recipe, MachineContext context,
                                                int parallelism) {
        if (recipe.requirements().stream().anyMatch(r -> r.getSelectorTag() != null
                || r instanceof RequirementEnergy && r.getIOType() == IOType.OUTPUT))
            return processPerTick(recipe, recipe.requirements(), context, parallelism, false) == null;
        boolean sawEnergyRequirement = false;
        for (Requirement requirement : recipe.requirements()) {
            if (!(requirement instanceof RequirementEnergy energy)
                    || energy.getIOType() != IOType.INPUT) {
                continue;
            }
            sawEnergyRequirement = true;
            var selected = context.forSelector(energy.getSelectorTag());
            var storages = selected.capabilities(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
                    MachineContext.ComponentKind.INPUT);

            // Unconditional first-tick report: the exact shape of this lookup is what a stall depends
            // on. Wrapped in a try/catch because a diagnostic that can itself throw would hide the very
            // information it exists to provide.
            if (TRACE && !energyReported) {
                energyReported = true;
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("energy lookup: ").append(storages.size()).append(" INPUT storage(s) of ")
                            .append(context.getComponentPositions().size()).append(" component(s)");
                    for (var storage : storages) {
                        sb.append(" [stored=").append(storage.getEnergyStored())
                                .append(" max=").append(storage.getMaxEnergyStored())
                                .append(" canExtract=").append(storage.canExtract())
                                .append(" canReceive=").append(storage.canReceive()).append(']');
                    }
                    int anyCount = context.capabilities(
                            net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
                            MachineContext.ComponentKind.ANY).size();
                    sb.append("; ANY-direction=").append(anyCount)
                            .append("; needed/tick=").append(energy.getEnergyPerTick());
                    trace(sb.toString());
                } catch (Throwable t) {
                    trace("energy diagnostic threw: {}", t.toString());
                }
            }

            int remaining = energy.getEnergyPerTick() * (energy.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            // N CRAFTS OF ENERGY PER TICK, not one (PLAN 597.2, 611.3). WITH THIS MULTIPLIER MISSING, A MACHINE

            // GRANTED A PARALLELISM OF 64 PAID ONE CRAFT ENERGY FOR SIXTY-FOUR CRAFTS WORK -- a balance hole rather

            // than a cosmetic gap, and the last place where the granted value did not reach the work it governs.

            //

            // THE VALUE IS READ FROM THE CONTROLLER because it is the persisted per-craft value (605) and not the

            // machine current maximum: if a parallel controller is removed mid-craft, the craft keeps paying for what

            // it was granted rather than silently becoming cheaper.
            // Draw through the hatches THEMSELVES, not through their capability.
            //
            // The capability list hands back the anonymous EnergyStorage subclass
            // (runtime class TileEnergyHatch$1), which is not an instance of TileEnergyHatch —so an
            // earlier `instanceof TileEnergyHatch` guard here matched nothing and silently drew nothing,
            // stalling every recipe at 0 progress against a completely full 160,000 FE buffer. The
            // capability is the EXTERNAL contract (an input hatch refuses extraction on purpose, so a
            // neighbouring machine cannot drain it); the owner needs the INTERNAL channel.
            int drawn = 0;
            for (var hatch : selected.internalEnergyHatches()) {
                if (hatch.getIOType() != IOType.INPUT && hatch.getIOType() != null) {
                    continue;
                }
                drawn += hatch.consumeInternal(remaining - drawn, false);
                if (drawn >= remaining) {
                    break;
                }
            }
            remaining -= drawn;

            if (remaining > 0) {
                return false;
            }
        }
        // Reconstructed from build/recover/ (PLAN section 344): the bytecode's tail is
        //   iload_2 / ifne -> trace("recipe '{}' has no INPUT energy requirement") -> return true
        // so the flag records whether ANY input energy requirement was seen, and the diagnostic fires when
        // none was -- which is the case where a recipe silently costs no power.
        if (!sawEnergyRequirement && TRACE) {
            trace("recipe '{}' has no INPUT energy requirement", recipe.registryName());
        }
        return true;
    }

    /**
     * Places a completed recipe's outputs (PLAN section 344).
     *
     * <p><b>RECONSTRUCTED from the compiled class in {@code build/recover/}</b>, whose bytecode was the only
     * surviving copy after section 342's `-replace` deleted this method. Every structure here is derived from
     * that dump and cross-checked against two verbatim reads taken before the loss; the fluid branch, the
     * chance roll, the handler lookup and the slot loop all match statement for statement.
     *
     * <p><b>AND IT PLACES THE TICK'S RESOLVED REQUIREMENTS</b> (PLAN 723.3), which is the whole point of the
     * {@code requirements} parameter: upstream hands the write path the SAME context its checks used
     * ({@code RecipeCraftingContext.finishCrafting(components, context, chance)}, U386-U411, whose requirements
     * recompute their modifiers at write time), and this port carries that value in the resolved copies instead.
     * Emitting {@code recipe.requirements()} here is what made a machine modifier change what the machine was
     * ASKED for and not what it MADE: the check demanded room for two items and one was placed. NOT ONE BYTE of
     * the write below changed -- only the list it walks.
     *
     * <p>Fluids go first, so a recipe producing both into one hatch has a chance to fit the fluid rather than
     * being squeezed out by items. Items follow, each with its own chance roll and its own sweep over the
     * OUTPUT item handlers.
     *
     * @param recipe       the definition the run belongs to; read for its name in the trace lines and for the
     *                     result-chance event below, NOT for its requirements
     * @param requirements the run's resolved requirements, from {@code RecipeCraftingContext.getRequirements()}
     */
    public static void emitOutputs(MachineRecipe recipe, List<Requirement> requirements,
                                   MachineContext context, int parallelism) {

        // RESULT CHANCE EVENT (PLAN 588, 590.2, 595.2). Upstream fires it ONCE at the top of finishCrafting (U390-U394)
        // and READS THE RESULT BACK, then hands that roller to every requirement. THIS PORT HAS NO ROLLER -- its odds
        // live per requirement as floats -- SO THE EVENT CARRIES A FLAG INSTEAD, which is the same capability without
        // a value that would have no upstream counterpart to mean: "make every probabilistic output of this craft
        // succeed", which is what upstream own ResultChance.GUARANTEED does.
        //
        // AND THE DEFAULT CHANGES NOTHING. A handler that does not call forceGuaranteed() leaves the craft behaving
        // exactly as its JSON declares -- which is the property an event must have when it was not asked for.
        //
        // THE CONTROLLER IS RESOLVED BY POSITION, as at 571.1: MachineContext exposes getControllerPos and not the
        // controller, so no signature changes to reach it. A machine that is no longer there gets no event, which is
        // the same choice the component dispatch made at 494.
        boolean guaranteedOutput = false;
        hellfirepvp.modularmachinery.common.tiles.TileController chanceController =
                context.getLevel().getBlockEntity(context.getControllerPos())
                        instanceof hellfirepvp.modularmachinery.common.tiles.TileController c ? c : null;
        if (chanceController != null) {
            hellfirepvp.modularmachinery.common.event.recipe.ResultChanceCreateEvent chanceEvent =
                    new hellfirepvp.modularmachinery.common.event.recipe.ResultChanceCreateEvent(
                            chanceController,
                            new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe),
                            1.0F,
                            hellfirepvp.modularmachinery.common.event.machine.Phase.END);
            chanceEvent.postEvent();
            guaranteedOutput = chanceEvent.isGuaranteed();
        }
        // THE TWO LOOPS BELOW WALK THE RUN'S RESOLVED LIST, NOT THE DEFINITION'S -- AND THAT WAS THE HALF OF
        // PLAN 723.3's FIX THAT WAS STILL MISSING (both loops read `recipe.requirements()` while the signature
        // above promises the resolved list and outputAllocationFailure already asked ITS room question of it).
        // SO AN ACTIVE MODIFIER DOUBLED WHAT `satisfies` DEMANDED AND THE EMISSION STILL PLACED THE
        // DEFINITION'S OWN AMOUNT: the two lists disagreed about one craft, which is the drift this parameter
        // exists to remove (#629's lesson) and the reason it is a parameter rather than a local.
        for (Requirement requirement : requirements) {
            // Fluids first, so a recipe producing both into the same hatch space has a chance to fit
            // the fluid rather than being squeezed out by items.
            if (requirement instanceof RequirementFluid fluid
                    && fluid.getIOType() == IOType.OUTPUT && fluid.isResolvable()) {
                int placed = fluid.produce(context);
                if (placed < fluid.getAmount()) {
                    hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                            "[MMCE] machine has room for only {}/{}mB of {} —the remainder is lost; "
                                    + "the machine's contents changed mid-craft",
                            placed, fluid.getAmount(), fluid.getRawId());
                } else if (TRACE) {
                    trace("emit {}mB of {}", placed, fluid.getRawId());
                }
            }
        }
        // Chemical integrations share ordinary hybrid hatches with fluids. Keep this pass after the
        // complete fluid pass so the allocation gate and writer reserve cells in the same order.
        // Required extension outputs go first so discardable outputs cannot consume their reservation.
        var committedOutputGroups = new java.util.HashSet<String>();
        for (Requirement requirement : requirements.stream()
                .sorted(java.util.Comparator.comparing(Requirement::isIgnoreOutputCheck)).toList()) {
            if (!(requirement instanceof hellfirepvp.modularmachinery.common.crafting.Requirement.PerTick)
                    && requirement instanceof hellfirepvp.modularmachinery.common.crafting.BatchIORequirement batch
                    && requirement.getIOType() == IOType.OUTPUT) {
                if (committedOutputGroups.contains(requirement.typeId())) continue;
                var grouped = batch.planOutputs(context, requirements, parallelism);
                if (grouped != null) {
                    committedOutputGroups.add(requirement.typeId());
                    if (!grouped.execute()) hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[MMCE] Could not commit grouped output for {}", requirement.typeId());
                    continue;
                }
                int produced = batch.produce(context, parallelism);
                if (TRACE) trace("emit extension batch {} units for {}", produced, requirement.typeId());
            }
        }
        // The ITEM half of the same list (see the comment on the fluid loop above).
        for (Requirement requirement : requirements) {
            if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray array
                    && array.getIOType() == IOType.OUTPUT) {
                emitRandomItems(array, context, parallelism, guaranteedOutput);
                continue;
            }
            if (!(requirement instanceof RequirementItem item) || item.getIOType() != IOType.OUTPUT) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = item.outputStack(context);
            if (stack.isEmpty()) {
                continue;
            }
            // A probabilistic output is rolled HERE rather than in a requirement check: rolling earlier would
            // make the machine cycle forever on a recipe whose 40%-chance output never came up, and the inputs
            // are already consumed by then anyway.
            if (!guaranteedOutput && !item.isGuaranteed()) {
                // ThreadLocalRandom rather than a level-owned RandomSource: emitOutputs runs on the
                // server thread and has no Level in scope. Each emission draws independently, so two
                // machines finishing on the same tick do not share an outcome.
                float roll = TRACE
                        ? 0.0F   // self-test runs must be deterministic; the roll is reported instead
                        : java.util.concurrent.ThreadLocalRandom.current().nextFloat();
                if (TRACE) {
                    trace("emit '{}' chance={} roll={} (forced to 0 under selfTest for determinism)",
                            item.getRawId(), item.getChance(), roll);
                }
                if (roll > item.getChance()) {
                    trace("emit '{}' skipped: roll {} > chance {}", item.getRawId(), roll, item.getChance());
                    continue;
                }
            }

            // Distinguishes "the requirement produced no stack" from "the stack went nowhere",
            // which have entirely different causes: the former is an unresolved id or a tag-based
            // requirement, the latter is a component/handler selection problem.
            if (TRACE) {
                trace("emit '{}': amount={} stack={} chance={}", item.getRawId(), item.getAmount(),
                        stack.getCount() + "x " + item.getRawId(), item.getChance());
            }

            java.util.List<net.minecraftforge.items.IItemHandler> handlers = context.forSelector(item.getSelectorTag()).capabilities(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                    MachineContext.ComponentKind.OUTPUT);
            if (TRACE) {
                trace("emit '{}': {} OUTPUT item handler(s)", item.getRawId(), handlers.size());
            }

            long remainingLong = item.outputAmount(context) * (item.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            int remaining = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remainingLong));
            if (remaining <= 0) continue;
            // N COPIES ARE PRODUCED, not one (PLAN 597.2, 598): the craft was granted a parallelism by the min

            // reduction, it CONSUMED N times its inputs for it, and it must therefore emit N times its outputs or

            // the machine would quietly eat the difference. THE MULTIPLIER IS THE ONE THE CRAFT STARTED WITH, read

            // back from the controller rather than recomputed -- recomputing here could disagree with what was

            // consumed, which is why upstream keeps it on the recipe state instead (605, 606).
            for (net.minecraftforge.items.IItemHandler handler : handlers) {
                net.minecraft.world.item.ItemStack rest =
                        stack.copyWithCount(remaining);
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (rest.isEmpty()) {
                        break;
                    }
                    net.minecraft.world.item.ItemStack wouldAccept = handler.insertItem(slot, rest, false);
                    if (TRACE) {
                        trace("  slot {}: offered {} accepted {}", slot, rest.getCount(),
                                rest.getCount() - wouldAccept.getCount());
                    }
                    rest = wouldAccept;
                }
                remaining = rest.getCount();
                if (remaining <= 0) {
                    break;
                }
            }
            // Anything still remaining means the output space changed between the canFinish check and
            // completion. Dropping the surplus would be a silent dupe/loss, so it is reported and
            // discarded deliberately rather than scattered on the floor.
            //
            // A PROBABILISTIC OUTPUT IS NOT PART OF THE ALLOCATION GATE (see outputAllocationFailure), so its
            // message says so and points at the room instead of at "the machine's contents changed mid-craft":
            // that sentence was the port's hypothesis for a cause this path does not have.
            if (remaining > 0) {
                if (item.isGuaranteed()) {
                    hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                            "[MMCE] machine at output has no room for {} x {}; {} item(s) lost —"
                                    + "this means the machine's contents changed mid-craft",
                            item.getAmount(), item.getRawId(), remaining);
                } else {
                    hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                            "[MMCE] machine has no room for a rolled '{}' (chance={}); {} of {} item(s) not"
                                    + " placed — a probabilistic output is not reserved room in advance, so"
                                    + " freeing a slot lets the next craft have it",
                            item.getRawId(), item.getChance(), remaining, remaining);
                }
            } else if (TRACE) {
                trace("emit '{}' complete", item.getRawId());
            }
        }
    }

    private static void emitRandomItems(hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray array,
                                       MachineContext context, int parallelism, boolean guaranteedOutput) {
        if (!guaranteedOutput && (array.getChance() <= 0 || (!TRACE
                && java.util.concurrent.ThreadLocalRandom.current().nextFloat() >= array.getChance()))) return;
        var candidate = array.chooseCandidate(context, parallelism);
        if (candidate == null) return;
        int candidateIndex = array.getCandidates().indexOf(candidate);
        var item = candidate.outputItem();
        var stack = array.modifiedCandidateOutputStack(context, candidateIndex, item.asStack());
        if (stack.isEmpty()) return;
        long remaining = array.outputAmount(context, candidateIndex, candidate.rollAmount(TRACE))
                * (array.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
        if (remaining <= 0) return;
        var selected = context.forSelector(array.getSelectorTag());
        for (var handler : selected.capabilities(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                MachineContext.ComponentKind.OUTPUT)) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                int offer = (int)Math.min(Integer.MAX_VALUE, remaining);
                remaining -= (long)offer - handler.insertItem(slot, stack.copyWithCount(offer), false).getCount();
            }
        }
        if (remaining > 0) hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                "[MMCE] random output inventory changed during insertion: {} x {} unplaced", remaining, item.getRawId());
    }

    public static MachineRecipe findByName(List<MachineRecipe> candidates, String name) {
        if (name == null) {
            return null;
        }
        for (MachineRecipe recipe : candidates) {
            if (name.equals(recipe.registryName())) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * @return the recipes this machine could currently run to completion, by the same rules the engine
     *         uses —see {@link #isRunnable} for why sharing that predicate matters
     */
    public static List<String> runnableRecipes(MachineContext context, List<MachineRecipe> candidates) {
        List<String> runnable = new ArrayList<>();
        for (MachineRecipe recipe : candidates) {
            // No controller is in scope here, so there is no formed structure to test a modifier condition
            // against. Passing null means no modifiers apply -- which makes this a diagnostic that reports the
            // UNMODIFIED requirements. That is a real divergence from the engine path when a machine has an
            // active modifier, and it is stated rather than hidden.
            if (isRunnable(recipe) && satisfies(recipe, (DynamicMachine) null, context, true)) {
                runnable.add(recipe.registryName());
            }
        }
        return runnable;
    }
}
