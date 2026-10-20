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

/**
 * Draws Forge Energy from the machine's energy hatches while the recipe runs.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream implemented its own long-typed {@code IEnergyHandler} and moved
 * energy in {@code long}; 1.20.1 Forge Energy is {@code int}-based
 * ({@link net.minecraftforge.energy.IEnergyStorage}), so the per-tick figure is an {@code int}. See
 * {@link hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData#getMaxEnergy()} for the same
 * constraint applied to hatch capacity.
 *
 * <p>Energy is drawn <b>per tick for the whole recipe duration</b>, not once at the start. That is why
 * a recipe can stall halfway when its hatches run dry, and why the machine must be able to resume
 * rather than lose progress — a design point the tick model in a later unit has to respect.
 */
public final class RequirementEnergy extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement.Parallelizable {

    private final IOType ioType;
    private final int energyPerTick;
    // PARALLELISM (PLAN 591-609). The pair is upstream ComponentRequirement own, and the exemption resets the value
    // to one because an exempt requirement must not carry a multiplier it refuses to apply.
    private int parallelism = 1;
    private boolean parallelizeUnaffected = false;

    public RequirementEnergy(IOType ioType, int energyPerTick) {
        this.ioType = ioType;
        this.energyPerTick = energyPerTick;
    }

    @Override
    public IOType getIOType() {
        return ioType;
    }

    /** @return FE drawn (input) or produced (output) per tick */
    public int getEnergyPerTick() {
        return energyPerTick;
    }

    /** @return total FE for a recipe of the given duration */
    public long getTotalEnergy(int recipeTime) {
        return (long) energyPerTick * recipeTime;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>THE SHAPE IS UPSTREAM RequirementEnergy U119-U130, WHICH IS THE SAME SHAPE RequirementItem U273-U284
     * USES -- read both and they are line for line the same:</b></p>
     *
     * <pre>
     * if (ignoreOutputCheck and this is an OUTPUT) return maxParallelism;    NO LIMIT AT ALL
     * if (parallelizeUnaffected) { probe one copy; return maxParallelism or 0; }
     * return simulate(maxParallelism);                                       HOW MANY FIT
     * </pre>
     *
     * <p><b>AND THE EXEMPT BRANCH IS THE COUNTER-INTUITIVE ONE:</b> an exempt requirement that can do ONE copy
     * returns {@code maxParallelism}, NOT one. IT DOES NOT CONSTRAIN THE CRAFT, BECAUSE IT IS NOT ITSELF
     * MULTIPLIED -- only a requirement that cannot do even one blocks it, by returning zero. A port that returned 1
     * there would silently cap every machine that has such a requirement.</p>
     *
     * <p>THE PROBE IS canStart, WHICH IS THE PORT OWN SINGLE-COPY TEST, and the count is derived from the same two
     * primitives canStart uses -- totalStored and totalCapacity -- rather than from a second set of energy APIs.</p>
     */
    @Override
    public int getMaxParallelism(MachineContext context, int maxParallelism,
                                 java.util.List<Requirement> siblings) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return maxParallelism;
        if (parallelizeUnaffected) {
            return canStart(context) ? maxParallelism : 0;
        }
        if (energyPerTick <= 0) {
            // A zero-cost requirement cannot constrain anything, and dividing by it would not be a number.
            return maxParallelism;
        }
        long stored = totalStored(context, ioType);
        long available = ioType == IOType.OUTPUT
                ? totalCapacity(context, IOType.OUTPUT) - stored
                : stored;
        if (available <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.min(maxParallelism, available / energyPerTick));
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
    /**
     * {@inheritDoc}
     *
     * <p>For an <b>input</b> requirement, satisfied when the machine's energy hatches collectively hold
     * at least one tick's worth — the recipe draws {@link #getEnergyPerTick()} each tick and will stall
     * rather than fail when the buffer runs dry, so the start check only needs the first tick covered.
     *
     * <p>For an <b>output</b> requirement the question is whether the stored energy has somewhere to go,
     * which for a machine that pushes into an external network is the same "is there room" test.
     */
    @Override
    public boolean canStart(MachineContext context) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        long stored = totalStored(context, ioType);
        // An output hatch must also be able to accept the surplus, not merely hold it.
        if (ioType == IOType.OUTPUT) {
            long capacity = totalCapacity(context, IOType.OUTPUT);
            return capacity - stored >= energyPerTick;
        }
        return stored >= energyPerTick;
    }

    /** Sums stored FE across the machine's energy hatches of the given direction. */
    private long totalStored(MachineContext context, IOType direction) {
        long total = 0;
        for (var storage : context.forSelector(getSelectorTag()).capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
                direction == IOType.INPUT
                        ? MachineContext.ComponentKind.INPUT
                        : MachineContext.ComponentKind.OUTPUT)) {
            total += storage.getEnergyStored();
        }
        return total;
    }

    /** Sums capacity across the machine's energy hatches of the given direction. */
    private long totalCapacity(MachineContext context, IOType direction) {
        long total = 0;
        for (var storage : context.forSelector(getSelectorTag()).capabilities(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
                direction == IOType.INPUT
                        ? MachineContext.ComponentKind.INPUT
                        : MachineContext.ComponentKind.OUTPUT)) {
            total += storage.getMaxEnergyStored();
        }
        return total;
    }

    @Override
    public String describe() {
        return energyPerTick + " FE/tick (" + ioType + ")";
    }

    @Override
    public String typeId() {
        return "modularmachinery:energy";
    }

    @Override
    public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        if (io != null && io != ioType) {
            return this;
        }
        // Per-tick energy. Scaling this scales the whole recipe's draw, since getTotalEnergy multiplies by
        // the duration -- which is what upstream means by a modifier on energy.
        int scaled = Math.max(0, Math.round(Requirement.apply(energyPerTick, value, operation)));
        return copySelectorTo(new RequirementEnergy(ioType, scaled));
    }
}
