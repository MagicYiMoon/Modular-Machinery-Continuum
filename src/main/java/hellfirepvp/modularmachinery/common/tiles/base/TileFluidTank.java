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
package hellfirepvp.modularmachinery.common.tiles.base;

import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base for a fluid hatch (input or output).
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream had its own {@code HybridTank} wrapping an
 * {@code IFluidTank}, and exported it through the fluid capability by returning the handler directly.
 * On 1.20.1 the tank is a {@link FluidTank} (Forge's ready-made {@link IFluidHandler}) exposed via a
 * {@link LazyOptional}, for the same invalidation reasons as {@link TileItemBus}.
 *
 * <p>Directional behaviour is expressed through {@link #getIOType()} rather than by returning a
 * different handler per side. On 1.20.1 the side-aware contract is the handler's own
 * {@code canFill}/{@code canDrain} predicates, so a single tank instance can answer per side without
 * allocating a wrapper per face — which is what the 1.12.2 code did and what leaked handlers.
 */
public abstract class TileFluidTank extends TileMachineComponent implements GasTankAccess {

    private final IOType ioType;
    private final FluidTank tank;
    /** Raw Mekanism GasStack NBT. Kept even when Mekanism is not installed so removing the optional mod
     * does not silently destroy a machine's contents. */
    private CompoundTag gasTag = new CompoundTag();

    protected TileFluidTank(java.util.function.Supplier<? extends BlockEntityType<?>> type,
                            BlockPos pos, BlockState state, IOType ioType, int capacity) {
        super(type, pos, state);
        this.ioType = ioType;
        this.tank = createTank(capacity);
    }

    private FluidTank createTank(int capacity) {
        return new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }

            @Override
            public boolean isFluidValid(FluidStack stack) {
                // AN OUTPUT HATCH ACCEPTS NO EXTERNAL FILL; THE MACHINE PUSHES INTO IT DIRECTLY (PLAN §714).
                //
                // THIS PREDICATE IS THE GATE THAT ACTUALLY REFUSED, and finding the second one took a run:
                // Forge's FluidTank.fill refuses at its own first line -- `if (resource.isEmpty() ||
                // !isFluidValid(resource)) return 0;`, FluidTank L116-L121 -- so the fill override below can
                // allow a call and THIS still refuses it from inside super.fill. Both therefore read the SAME
                // owner-side window, `accessingInternally`, which produceInternal opens. That flag was written by
                // produceInternal and read by drain and by NOTHING ELSE, so every write into an OUTPUT hatch
                // returned 0 from one gate or the other and the whole fluid-output path was dead: the pre-start
                // room question answered 0, getMaxParallelism answered 0, and a craft could never start.
                // Invisible for as long as it was, because no check in this suite had ever driven a fluid OUTPUT
                // through the tick (PLAN 699.7 / 704.9:1, closed by the fluid-output-allocation check).
                //
                // THE EXTERNAL CONTRACT IS UNCHANGED: a neighbour, a pipe and a player's bucket get `false` here
                // and 0 from fill, exactly as the output hatch's own javadoc promises ("externally drainable,
                // never externally fillable"). Upstream is looser -- FluidHatchSize.buildTank(tile, true, true)
                // leaves BOTH hatches fillable by anyone, for both directions -- so keeping the port's stricter
                // contract is a pre-existing decision and is not widened here.
                //
                // This is the FIFTH instance of the error this file's javadoc already names four times
                // (F105-F113): "a rule that depends on WHO IS CALLING cannot live on a resource every caller
                // shares" -- here on the fill side, in the two methods whose whole job is to express it.
                return !hasGas() && (ioType == IOType.INPUT || accessingInternally) && super.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                // THE SECOND GATE ON THE SAME DOOR, and it is not redundant: this one also drives the
                // does-not-fit / wrong-fluid refusals below super.fill's first line. See isFluidValid above.
                if (hasGas() || (ioType != IOType.INPUT && !accessingInternally)) {
                    return 0;
                }
                return super.fill(resource, action);
            }

            @Override
            public void setFluid(FluidStack stack) {
                if (stack != null && !stack.isEmpty() && TileFluidTank.this.hasGas()) {
                    TileFluidTank.this.clearGas();
                }
                super.setFluid(stack == null ? FluidStack.EMPTY : stack);
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                // An input hatch refuses EXTERNAL drain so a neighbouring machine cannot steal the fluid
                // its owner is collecting. The owning machine reaches the tank through the internal
                // channel instead -- see consumeInternal.
                if (ioType != IOType.OUTPUT && !accessingInternally && !playerAccessing) {
                    return FluidStack.EMPTY;
                }
                return super.drain(resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                if (ioType != IOType.OUTPUT && !accessingInternally && !playerAccessing) {
                    return FluidStack.EMPTY;
                }
                return super.drain(maxDrain, action);
            }
        };
    }

    /**
     * Set while the owning machine is drawing from or writing to this tank directly.
     *
     * <p><b>Why this exists.</b> The direction gates above are correct for the capability they expose,
     * but they applied to <b>every</b> caller — so the machine that owned the tank could not consume its
     * own input fluid any more than a neighbour could. A water requirement was therefore unsatisfiable
     * no matter how full the hatch was, with nothing reporting why. This is the <b>fourth</b> instance of
     * the same structural error in this port (energy hatches, reflective {@code getIOType}, item bus
     * validation, and now fluid tanks), and the rule is now explicit:
     *
     * <blockquote>a rule that depends on <i>who is calling</i> cannot live on a resource every caller
     * shares.</blockquote>
     *
     * <p>A plain field rather than anything cleverer because all machine work happens on the server
     * thread; there is no concurrent caller to race with.
     */
    private boolean accessingInternally = false;

    /**
     * Open while a PLAYER is acting on this tank with a container in hand.
     *
     * <p><b>Why the drain gate needs two flags and not one.</b> The gate above refuses external drain on an
     * input hatch so that a neighbouring machine cannot steal the fluid the owner is collecting. That
     * reasoning is about AUTOMATION, and it does not extend to the player standing in front of it: the client
     * reported that an empty bucket could not take fluid back out, which is a thing a tank block should allow
     * and which the hatch's own GUI (a level, a fluid name, a capacity) implies.
     *
     * <p>So a player action opens this window for the duration of one call, and the gate treats it like the
     * machine's own internal access. Automation still cannot drain an input hatch.
     */
    private boolean playerAccessing = false;

    /**
     * Runs {@code action} with PLAYER access open, restoring the previous state afterwards.
     *
     * <p>Uses the same try/finally shape as {@link #consumeInternal} so an exception cannot leave the window
     * open and quietly turn an input hatch into an output one.
     */
    public <T> T withPlayerAccess(java.util.function.Supplier<T> action) {
        boolean previous = playerAccessing;
        playerAccessing = true;
        try {
            return action.get();
        } finally {
            playerAccessing = previous;
        }
    }

    /**
     * Draws fluid for the machine's own recipe consumption, bypassing the external direction gate.
     *
     * @return the fluid actually drawn, which may be less than requested
     */
    public FluidStack consumeInternal(FluidStack request, IFluidHandler.FluidAction action) {
        accessingInternally = true;
        try {
            return tank.drain(request, action);
        } finally {
            accessingInternally = false;
        }
    }

    /**
     * Writes produced fluid into this tank, bypassing the external direction gate.
     *
     * @return the amount accepted, in millibuckets
     */
    public int produceInternal(FluidStack stack, IFluidHandler.FluidAction action) {
        if (hasGas()) return 0;
        accessingInternally = true;
        try {
            return tank.fill(stack, action);
        } finally {
            accessingInternally = false;
        }
    }

    public IOType getIOType() {
        return ioType;
    }

    public FluidTank getTank() {
        return tank;
    }

    @Override public boolean hasGas() {
        if (gasTag.isEmpty()) return false;
        if (ModList.get().isLoaded("mekanism")) {
            return hellfirepvp.modularmachinery.integration.mekanism.MekanismGasBridge.has(this);
        }
        return gasTag.getLong("amount") > 0;
    }

    @Override public long getGasAmount() {
        if (!hasGas()) return 0;
        if (ModList.get().isLoaded("mekanism")) return hellfirepvp.modularmachinery.integration.mekanism.MekanismGasBridge.amount(this);
        return gasTag.getLong("amount");
    }

    @Override public long getGasCapacity() { return tank.getCapacity(); }

    @Override public CompoundTag getGasTag() { return gasTag.copy(); }

    @Override public void setGasTag(CompoundTag tag) {
        CompoundTag next = tag == null ? new CompoundTag() : tag.copy();
        gasTag = next;
        if (!next.isEmpty()) tank.setFluid(FluidStack.EMPTY);
        markForUpdate();
    }

    @Override public void clearGas() {
        if (!gasTag.isEmpty()) {
            gasTag = new CompoundTag();
            markForUpdate();
        }
    }

    @Override public boolean canAcceptGas() { return ioType == IOType.INPUT && !hasFluid(); }
    @Override public boolean canExtractGas() { return ioType == IOType.OUTPUT && hasGas(); }

    private boolean hasFluid() { return !tank.isEmpty(); }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return LazyOptional.of(() -> tank).cast();
        }
        if (ModList.get().isLoaded("mekanism")
                && cap == hellfirepvp.modularmachinery.integration.mekanism.MekanismGasBridge.capability()) {
            return LazyOptional.of(() -> hellfirepvp.modularmachinery.integration.mekanism.MekanismGasBridge.handler(this)).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("tank")) {
            tank.readFromNBT(tag.getCompound("tank"));
        }
        gasTag = tag.contains("gas") ? tag.getCompound("gas").copy() : new CompoundTag();
        if (hasGas()) tank.setFluid(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("tank", tank.writeToNBT(new CompoundTag()));
        if (!gasTag.isEmpty()) tag.put("gas", gasTag.copy());
    }
}
