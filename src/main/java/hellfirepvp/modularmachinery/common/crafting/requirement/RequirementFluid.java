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
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Requires a fluid in the machine's fluid hatches, or produces one into them.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream used {@code FluidStack} and the 1.12.2 {@code IFluidHandler};
 * 1.20.1 keeps the class names but changes the API surface — {@code IFluidHandler} gained explicit
 * {@code IFluidHandler.FluidAction} instead of a bare {@code boolean simulate}, and fluid amounts are
 * still millibuckets so no scaling is needed. Fluid <b>blocks</b> (the {@code water} entry inside a
 * structure pattern) are a scaffold concern handled by the structure matcher, not by this class: this is
 * about fluid held in a hatch's tank.
 *
 * <h2>Why this existed as a gap</h2>
 *
 * <p>The fluid hatch blocks, block entities and tanks were all registered and working from the start,
 * but no requirement type ever read them — so a pack whose machine needed water would have been
 * unsatisfiable with no error anywhere. Only the item and energy types were implemented. That is a
 * silent hole rather than a visible failure, which is why it is worth closing even though none of the
 * shipped default recipes happen to use fluids.
 *
 * <p>No default recipe exercises this, so it is verified by {@code MachineSelfTest} constructing a
 * requirement directly rather than by a shipped recipe.
 */
public final class RequirementFluid extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement.Parallelizable {

    private hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt nbt =
            hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.EMPTY;

    public RequirementFluid withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt nbt) {
        this.nbt = nbt;
        return this;
    }

    public hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt getNbt() { return nbt; }

    public boolean matches(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid() == resolved && nbt.matches(stack.getTag());
    }

    public FluidStack asStack(int count) {
        if (resolved == null) return FluidStack.EMPTY;
        return new FluidStack(resolved, count, nbt.output());
    }

    public FluidStack asDisplayStack() {
        FluidStack stack = asStack();
        if (!stack.isEmpty()) stack.setTag(nbt.display());
        return stack;
    }

    private FluidStack drainMatching(hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank tank,
                                     int count, IFluidHandler.FluidAction action) {
        FluidStack stored = tank.getTank().getFluid();
        if (!matches(stored)) return FluidStack.EMPTY;
        return tank.consumeInternal(new FluidStack(stored, count), action);
    }

    private final IOType ioType;
    private final String rawId;

    /**
     * Probability this requirement is satisfied, in [0,1]. 1 means always (PLAN section 315).
     *
     * <p>Upstream has this field (RequirementFluid:42, public float chance = 1F), parses it from the JSON
     * (RequirementTypeFluid:52-59), exposes setChance (:106) and CONSUMES it in startCrafting (:128 via
     * ResultChance.canWork). The port had none of the four, so a fluid output declaring a probability was
     * produced every time.
     *
     * <p>This class carried a comment saying a fluid requirement has no probability. That was the port
     * speaking about itself, not about the mod.
     */
    private final float chance;

    /** Same switch MachineTicker.TRACE reads, re-read here to keep the dependency pointing one way. */
    private static final boolean TRACE_SELFTEST = Boolean.getBoolean("modularmachinery.selfTest");
    private final int amount;
    // PARALLELISM (PLAN 591-609), the same pair RequirementEnergy carries for the same upstream reasons.
    private int parallelism = 1;
    private boolean parallelizeUnaffected = false;

    private final Fluid resolved;
    private final String resolutionNote;

    /**
     * A requirement that is always satisfied, which is what every requirement without a declared chance is.
     */
    public RequirementFluid(IOType ioType, String rawId, int amount) {
        this(ioType, rawId, amount, 1.0F);
    }

    /**
     * A requirement satisfied with the given probability.
     *
     * <p>The value is clamped HERE, as a last line of defence for a direct caller. The JSON path does NOT
     * rely on this: the loader applies upstream rule that an out-of-range chance is IGNORED and the
     * requirement stays at its default, which is not the same as clamping it to 0 or 1 (section 314).
     */
    public RequirementFluid(IOType ioType, String rawId, int amount, float chance) {

        this.ioType = ioType;
        this.rawId = rawId == null ? "" : rawId;
        this.amount = amount;
        this.chance = Math.max(0.0F, Math.min(1.0F, chance));

        // Resolve once, at load, so a bad id is reported when the pack loads rather than every tick.
        String bare = this.rawId;
        // Legacy fluid ids were sometimes written with a `fluid:` prefix that 1.20.1 does not use.
        if (bare.startsWith("fluid:")) {
            bare = bare.substring("fluid:".length());
        }
        Fluid fluid = null;
        String note = null;
        ResourceLocation key = ResourceLocation.tryParse(bare);
        if (key != null) {
            fluid = ForgeRegistries.FLUIDS.getValue(key);
            // Forge's registry lookup returns the registry's DEFAULT VALUE for an unregistered name
            // rather than null, so a typo such as 'modularmachinery:not_a_fluid' came back as
            // Fluids.EMPTY and looked perfectly resolvable. Treating the default as "not found" is what
            // makes the resolution check mean anything.
            if (fluid == null || fluid == Fluids.EMPTY) {
                fluid = null;
                note = "no fluid registered as '" + bare + "'";
            }
        } else {
            note = "'" + bare + "' is not a valid resource location";
        }
        this.resolved = fluid;
        this.resolutionNote = note;
    }

    /**
     * Probability this requirement is satisfied, in [0,1].
     *
     * <p>Upstream exposes the same value on RequirementFluid and READS it in startCrafting. The port exposes
     * it here so the crafting path can be given the roll in the same pass as the item one (section 315); a
     * getter with no caller yet is deliberate and is the reason this is recorded as half a unit.
     */
    public float getChance() {
        return chance;
    }

    /** @return whether this requirement is always satisfied, i.e. its chance is 1 */
    public boolean isGuaranteed() {
        return chance >= 1.0F;
    }

    @Override
    public IOType getIOType() {
        return ioType;
    }

    public String getRawId() {
        return rawId;
    }

    /** @return required or produced amount, in millibuckets */
    public int getAmount() {
        return amount;
    }

    public boolean isResolvable() {
        return resolved != null;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    /** @return the resolved fluid as a stack, or empty when the id did not resolve */
    public FluidStack asStack() {
        return asStack(amount);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Input: the machine's fluid hatches must collectively hold at least {@link #getAmount()}, and of
     * a fluid <b>matching</b> the one asked for. Output: there must be somewhere to put it.
     *
     * <p>An unresolvable id can never be satisfied. Returning false is deliberate rather than throwing:
     * a pack with a typo should see its machine refuse to start and the loader log the reason, not a
     * crash in the tick loop.
     */
    /**
     * {@inheritDoc}
     *
     * <p><b>THE SHAPE IS UPSTREAM RequirementFluid U153 AND IT IS THE SAME SHAPE THE OTHER TWO USE:</b> probe one
     * copy when exempt, otherwise simulate with the ceiling and count what fits. ASKING FOR {@code amount *
     * maxParallelism} IN ONE SIMULATED CALL IS WHAT UPSTREAM DOES -- {@code doItemIOInternal(components, context,
     * maxParallelism, ...)} at U283 -- RATHER THAN LOOPING ONE COPY AT A TIME, WHICH WOULD BE N MULTIPLIED CALLS
     * INTO THE SAME TANKS.</p>
     *
     * <p><b>AND IT READS THE TANKS THROUGH internalFluidTanks FOR THE REASON canStart OWN COMMENT GIVES (F184-F188):
     * an input hatch capability refuses drain on purpose, so asking it would make a water requirement unsatisfiable
     * no matter how full the tank was. THE OWNER USES THE INTERNAL CHANNEL; THE CAPABILITY IS THE EXTERNAL
     * CONTRACT. Reusing that decision rather than rediscovering it is the point of 470 and 473.</b></p>
     *
     * <p>AND THE EXEMPT BRANCH RETURNS maxParallelism WHEN ONE COPY FITS -- see RequirementEnergy own javadoc for
     * why returning 1 there would silently cap every machine carrying such a requirement.</p>
     */
    @Override
    public int getMaxParallelism(MachineContext context, int maxParallelism,
                                 java.util.List<Requirement> siblings) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return maxParallelism;
        if (ioType == IOType.INPUT) {
            var all = new java.util.ArrayList<Requirement>(siblings == null ? java.util.List.of() : siblings);
            if (!all.contains(this)) all.add(this);
            int low = 0, high = Math.max(0, maxParallelism);
            while (low < high) {
                int copies = low + (int) (((long) high - low + 1) / 2);
                if (hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan.plan(context, all, copies).valid()) low = copies;
                else high = copies - 1;
            }
            return low;
        }
        if (resolved == null) {
            return 0;
        }
        if (parallelizeUnaffected) {
            return canStart(context) ? maxParallelism : 0;
        }
        if (amount <= 0) {
            return maxParallelism;
        }
        if (ioType == IOType.INPUT) {
            var inputs = new java.util.ArrayList<Requirement>(siblings == null ? java.util.List.of() : siblings);
            if (!inputs.contains(this)) inputs.add(this);
            int low = 0, high = Math.max(0, maxParallelism);
            while (low < high) {
                int copies = low + (int) (((long) high - low + 1) / 2);
                if (hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan.plan(context, inputs, copies).valid()) low = copies;
                else high = copies - 1;
            }
            return low;
        }
        // Test complete batches: reserving a too-large first fluid can occupy every empty tank
        // and incorrectly report zero even though a smaller batch of all products fits.
        var outputs = new java.util.ArrayList<RequirementFluid>();
        for (Requirement sibling : siblings == null ? java.util.List.<Requirement>of() : siblings) {
            if (sibling instanceof RequirementFluid other && other.ioType == IOType.OUTPUT
                    && other.isResolvable() && other.amount > 0) outputs.add(other);
        }
        if (!outputs.contains(this)) outputs.add(this);
        int low = 0, high = Math.max(0, maxParallelism);
        while (low < high) {
            int copies = low + (int) (((long) high - low + 1) / 2);
            var reserved = new java.util.IdentityHashMap<IFluidHandler, java.util.Map<Integer, FluidStack>>();
            boolean fits = true;
            for (RequirementFluid output : outputs) {
                long demand = (long) output.amount * (output.parallelizeUnaffected ? 1 : copies);
                if (demand > Integer.MAX_VALUE || outputRoomFor(context.forSelector(output.getSelectorTag()), output.asStack((int) demand),
                        (int) demand, reserved) < demand) {
                    fits = false;
                    break;
                }
            }
            if (fits) low = copies;
            else high = copies - 1;
        }
        return low;
    }

    @Override
    public int getParallelism() {
        return parallelism;
    }

    @Override
    public void setParallelism(int parallelism) {
        if (!parallelizeUnaffected) {
            this.parallelism = parallelism;
        }
    }

    @Override
    public boolean isParallelizeUnaffected() {
        return parallelizeUnaffected;
    }

    @Override
    public void setParallelizeUnaffected(boolean unaffected) {
        this.parallelizeUnaffected = unaffected;
        if (parallelizeUnaffected) {
            this.parallelism = 1;
        }
    }
    @Override
    public boolean canStart(MachineContext context) {
        if (resolved == null) {
            return false;
        }
        // Read the tanks themselves, not their capabilities. An input hatch's IFluidHandler refuses
        // drain on purpose (so a neighbouring machine cannot steal the fluid its owner is collecting),
        // which also refused the owning machine -- making a water requirement unsatisfiable no matter
        // how full the hatch was. The capability is the EXTERNAL contract; the owner uses the internal
        // channel. This is the same split the energy hatch needed; see TileEnergyHatch#consumeInternal.
        if (ioType == IOType.INPUT) {
            return hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan
                    .plan(context, java.util.List.of(this), 1).valid();
        }
        // AN OUTPUT IS ASKED WHAT THE TANKS WOULD TAKE TOGETHER, through the one arithmetic the writer and the
        // emission gate also use (PLAN §714): this loop used to require ONE tank to accept the whole amount, so a
        // product the hatch space can hold across two tanks read as "nowhere to put it".
        return (ioType == IOType.OUTPUT && isIgnoreOutputCheck())
                || outputRoomFor(context.forSelector(getSelectorTag()), asStack(amount), amount, null) >= amount;
    }

    /** @return true when this tank is an input tank, i.e. something a recipe may consume from */
    private static boolean isInputTank(
            hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank tank) {
        return tank.getIOType() == IOType.INPUT;
    }

    /**
     * How much of a demand the machine's OUTPUT tanks would take <b>together</b>, walking them in the order the
     * writer walks them -- and, when {@code reserved} is given, counting what it found as reserved.
     *
     * <p><b>THIS IS UPSTREAM'S ARITHMETIC AND IT IS ALSO THE WRITER'S, WHICH IS WHY IT IS ONE METHOD HERE.</b>
     * Upstream asks every handler for the REMAINDER of the demand and sums what they accept:
     * {@code HybridFluidUtils.doSimulateDrainOrFill} (U31-U47) sets {@code stack.amount = maxDrainOrFill -
     * totalIO} before each {@code handler.fill(stack, false)} and sums, and {@code doDrainOrFill} (U56-U74) writes
     * with the same walking remainder. {@link #produce} already fills that way -- {@code wanted - placed} into each
     * tank in turn, break when the batch is in. So a room question asked per tank ("can ONE of you take the whole
     * batch") refuses batches the writer places: 160 mB over two 100 mB tanks is placeable and no single tank can
     * hold it. THAT WAS THE DEFECT THIS METHOD EXISTS TO REMOVE, and it only became reachable once an output hatch
     * could be written to at all (PLAN §714, the two fill gates on TileFluidTank).</p>
     *
     * <p><b>RULE A0 -- A RESTORATION, NOT A DEVIATION.</b> Upstream's file:line is quoted above. The port's
     * per-tank shape was the deviation and it is removed rather than documented. The one addition is
     * {@code reserved}, which §704 introduced for MULTI-OUTPUT allocation: several fluid outputs, each asked
     * against a state nothing has been written to yet, would each be told "yes" and the tail would be destroyed by
     * the sequential writer. The reservation is therefore kept per TANK ({@link java.util.IdentityHashMap},
     * because two tank tiles are never equal) and charged against the room this walk finds -- §704's "every prefix
     * of the emission must fit" rule, expressed in the writer's own walking order.</p>
     *
     * <p><b>AND IT ASKS THE TANKS, NOT A NUMBER.</b> The primitive is {@code produceInternal} SIMULATE -- the same
     * one {@link #produce} writes through -- so this cannot disagree with the writer about which tanks are
     * eligible, what a tank's capacity is, or which fluid it already holds. Input tanks are skipped for the same
     * reason {@code produce} skips them, and the request is unbounded so the answer IS the tank's room rather than
     * its room capped by the batch.</p>
     *
     * @param context  the machine as it stands right now
     * @param fluid    the fluid the demand is made of; only its identity is read
     * @param demand   the batch, in millibuckets
     * @param reserved when non-null, what earlier outputs of the SAME emission already claimed per tank: this walk
     *                 reads it, and adds what it counted to it only when the whole demand fits (a partial fit is
     *                 never charged, which is §704's rule for a probabilistic output)
     * @return the amount of {@code demand} the tanks would accept, never more than {@code demand}
     */
    public static int outputRoomFor(MachineContext context, FluidStack fluid, int demand,
                                    java.util.IdentityHashMap<IFluidHandler, java.util.Map<Integer, FluidStack>> reserved) {
        int remaining = Math.max(0, demand);
        if (remaining == 0 || context == null || fluid == null || fluid.isEmpty()) {
            return 0;
        }
        var staged = new java.util.IdentityHashMap<IFluidHandler, java.util.Map<Integer, FluidStack>>();
        for (var handler : context.internalFluidOutputs()) {
            var existing = reserved == null ? java.util.Map.<Integer, FluidStack>of()
                    : reserved.getOrDefault(handler, java.util.Map.of());
            long handlerRoom = handler.fill(new FluidStack(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            for (int slot = 0; slot < handler.getTanks() && remaining > 0 && handlerRoom > 0; slot++) {
                FluidStack stored = handler.getFluidInTank(slot);
                FluidStack prior = existing.getOrDefault(slot, FluidStack.EMPTY);
                if ((!stored.isEmpty() && !stored.isFluidEqual(fluid))
                        || (!prior.isEmpty() && !prior.isFluidEqual(fluid))
                        || !handler.isFluidValid(slot, fluid)) continue;
                long room = Math.max(0L, (long) handler.getTankCapacity(slot) - stored.getAmount() - prior.getAmount());
                int take = (int) Math.min(remaining, Math.min(room, Math.max(0L, handlerRoom - prior.getAmount())));
                handlerRoom = Math.max(0L, handlerRoom - prior.getAmount());
                if (take <= 0) continue;
                staged.computeIfAbsent(handler, ignored -> new java.util.HashMap<>())
                        .put(slot, new FluidStack(fluid, take + prior.getAmount()));
                remaining -= take;
                handlerRoom -= take;
            }
            if (remaining <= 0) break;
        }
        int placed = demand - remaining;
        if (reserved != null && placed >= demand) {
            staged.forEach((handler, slots) -> reserved.computeIfAbsent(handler, ignored -> new java.util.HashMap<>()).putAll(slots));
        }
        return placed;
    }

    @Override
    public boolean canFinish(MachineContext context) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        if (resolved == null) {
            return false;
        }
        if (ioType == IOType.INPUT) {
            // An input only has to be present to be consumed; the machine takes it at the start, so
            // start and finish agree.
            return canStart(context);
        }
        // An output must have room for the whole amount, otherwise the craft would complete with
        // nowhere to put its product after the inputs were already spent. ASKED OF THE TANKS TOGETHER, through
        // the one arithmetic the writer and the emission gate also use (PLAN §714): this used to require ONE
        // tank to accept the whole amount, so a machine whose hatch would take the product across two tanks
        // refused a craft the writer places.
        return outputRoomFor(context.forSelector(getSelectorTag()), asStack(amount), amount, null) >= amount;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>THE FLUID HALF OF THE SAME GAP (PLAN 683.2 fact 8 / 683.9:4).</b> The finish gate called
     * {@link #canFinish}, which asks for room for ONE craft, while {@link #produce} writes
     * {@code amount * parallelism} (F385) -- so a parallel craft with room for one copy completed, the
     * surplus was refused by the tank and {@code emitOutputs} reported "the remainder is lost". Upstream
     * covers fluids with the same arithmetic as items: {@code RequirementFluid.doFluidIO} (U166-U167)
     * simulates {@code parallelism} copies and returns {@code craftcheck.failure.fluid.output.space} when
     * fewer fit (U167-U177).</p>
     *
     * <p><b>AND THE ROOM IS ASKED OF THE TANKS TOGETHER, NOT OF ONE OF THEM (PLAN §714).</b> Upstream simulates
     * {@code parallelism} copies with {@code HybridFluidUtils.doSimulateDrainOrFill}, which walks the handlers
     * asking each for the remainder (U31-U47) -- the same walk {@link #produce} writes with -- so a batch spread
     * over two hatches is placeable. The sentence that stood here claimed the per-tank question was "how both
     * {@link #canStart} and {@link #produce} ask it"; reading {@code produce}'s body disproves it (it fills
     * {@code wanted - placed} into each tank in turn), and that is why every output-room site in this class now
     * calls {@link #outputRoomFor} instead of running its own loop.</p>
     */
    @Override
    public boolean canEmit(MachineContext context, int parallelism) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        if (ioType != IOType.OUTPUT || resolved == null) {
            return canFinish(context);
        }
        int batch = amount * Math.max(1, parallelism);
        return outputRoomFor(context.forSelector(getSelectorTag()), asStack(batch), batch, null) >= batch;
    }

    /**
     * @return the amount of the required fluid that could be drained, simulated only.
     *
     * <p>Matches on fluid identity rather than draining any fluid: a water requirement must not be
     * satisfied by a tank of lava. {@code FluidStack#isFluidEqual} compares the fluid and its NBT, which
     * is what "the same fluid" means for a requirement.
     */
    private int drainable(IFluidHandler handler) {
        FluidStack want = asStack(amount);
        FluidStack drained = handler.drain(want, IFluidHandler.FluidAction.SIMULATE);
        return drained.getAmount();
    }

    /** @return how much of the required fluid this handler could accept, simulated only */
    private int fillable(IFluidHandler handler) {
        return handler.fill(asStack(amount), IFluidHandler.FluidAction.SIMULATE);
    }

    /**
     * Actually removes the fluid from the machine's hatches.
     *
     * <p>Separate from {@link #canStart} for the same reason items are: nothing may be taken until
     * {@link #canFinish} has confirmed the recipe can complete.
     *
     * @return true when the full amount was taken
     */
    public boolean consume(MachineContext context) {
        return resolved != null && hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan
                .plan(context, java.util.List.of(this), parallelism).execute();
    }

    /**
     * Writes the produced fluid into the machine's output tanks.
     *
     * @return the amount actually placed, so a shortfall can be reported rather than silently lost
     */
    public int produce(MachineContext context) {
        if (resolved == null) {
            return 0;
        }
        int placed = 0;
        // THE MULTIPLIER (PLAN 597.2/609, upstream RequirementFluid own doFluidIO path): this craft

        // produces its declared amount multiplied by how many parallels it was granted. THE VALUE COMES FROM THE

        // FIELD THE MIN REDUCTION PUSHED IN (497), not from a second lookup, which is why the requirements carry it.

        int wanted = amount * parallelism;
        // THE PROBABILITY ROLL, WHICH THIS METHOD DID NOT DO (PLAN 578). Upstream consumes a fluid requirement own
        // chance in startCrafting via ResultChance.canWork, and THIS PORT OWN JAVADOC AT F59-F68 CLAIMED THAT WAS
        // FIXED -- IT WAS NOT: the chance field existed, was parsed, was clamped and had isGuaranteed(), and NOTHING
        // EVER CONSULTED IT. A fluid output declaring "chance": 0.4 was produced every single time.
        //
        // THE SHAPE IS COPIED FROM THE ITEM PATH (MachineTicker K728-K743) RATHER THAN INVENTED, because the two
        // must agree: isGuaranteed as the guard, ThreadLocalRandom for the draw, and the same self-test override.
        //
        // AND THE TRACE FLAG IS RE-READ HERE RATHER THAN EXPOSED FROM MachineTicker: MachineTicker.TRACE is private
        // and a requirement depending on the ticker would be the wrong direction. The property is the shared truth.
        if (!isGuaranteed()) {
            float roll = TRACE_SELFTEST ? 0.0F : java.util.concurrent.ThreadLocalRandom.current().nextFloat();
            if (roll > chance) {
                return 0;
            }
        }
        for (var handler : context.forSelector(getSelectorTag()).internalFluidOutputs()) {
            placed += handler.fill(asStack(wanted - placed), IFluidHandler.FluidAction.EXECUTE);
            if (placed >= wanted) break;
        }
        return placed;
    }

    @Override
    public String describe() {
        return (ioType == IOType.INPUT ? "input " : "output ") + amount + "mB "
                + (resolved == null ? rawId + " (UNRESOLVED)" : rawId);
    }

    @Override
    public String typeId() {
        return "modularmachinery:fluid";
    }

    /**
     * Applies a machine modifier. <b>THE {@code chance} FLAG SELECTS WHICH NUMBER IS SCALED, AND THAT IS THE
     * WHOLE CONTRACT:</b>
     *
     * <ul>
     *   <li>{@code chance == true} → the PROBABILITY ({@link #getChance()}) is scaled; the AMOUNT is carried
     *       over unchanged.</li>
     *   <li>{@code chance == false} → the AMOUNT is scaled; the PROBABILITY is carried over unchanged.</li>
     * </ul>
     *
     * <p>The producer of that flag is the modifier's {@code affectChance} key, read at
     * {@link hellfirepvp.modularmachinery.common.modifier.RecipeModifier#deserialize} (F149-F156) and passed
     * through {@code RecipeModifier.apply}. Upstream compares exactly this pair at
     * {@code RecipeModifier.applyModifiers} U150 ({@code mod.affectsChance() != isChance} skips the modifier).
     *
     * <p><b>RULE B -- THE SPLIT IS MIRRORED FROM {@link RequirementItem#scaled} (F485-F494), NOT INVENTED, AND
     * UPSTREAM'S FLUID HALF IS THE SAME SPLIT:</b> {@code RequirementFluid.deepCopyModified} (upstream U66-U74)
     * scales {@code copied.amount} through {@code applyModifiers(..., false)} at U68, scales {@code this.chance}
     * through {@code applyModifiers(..., true)} at U70, and carries both into the copy. The two-call form and this
     * either/or form agree because this port applies a modifier LIST ONE MODIFIER AT A TIME
     * ({@code RecipeCraftingContext.apply}, F90-F104): a chance modifier and an amount modifier are two entries
     * and therefore two passes, which is upstream's two calls.
     *
     * <p><b>WHAT THIS METHOD WAS, AND WHY IT WAS TWO DEFECTS RATHER THAN ONE (PLAN section 318 fixed only the
     * PARSING half):</b> it scaled the amount UNCONDITIONALLY — so {@code "affectChance": true} changed the amount
     * instead of the probability — and it returned through the THREE-ARG constructor (F85-F87), which delegates to
     * {@code this(..., 1.0F)} and therefore <b>reset the requirement's declared probability to 1.0</b>. That second
     * half applied to EVERY modifier on this target, including one that never mentions chance at all, so a pack's
     * declared probability was silently deleted by any amount scaling.
     */
    @Override
    public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        if (io != null && io != ioType) {
            return this;
        }
        if (chance) {
            // The probability is the scaled number here; `amount` is carried, not recomputed.
            return copySelectorTo(new RequirementFluid(ioType, rawId, amount, Requirement.apply(this.chance, value, operation)).withNbt(nbt));
        }
        int scaled = Math.max(0, Math.round(Requirement.apply(amount, value, operation)));
        // this.chance, NOT the three-arg constructor: the probability is not this modifier's business.
        return copySelectorTo(new RequirementFluid(ioType, rawId, scaled, this.chance).withNbt(nbt));
    }
}
