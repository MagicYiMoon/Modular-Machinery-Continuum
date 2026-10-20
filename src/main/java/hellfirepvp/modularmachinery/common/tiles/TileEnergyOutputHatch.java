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

import hellfirepvp.modularmachinery.common.block.BlockEnergyOutputHatch;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Energy output hatch — gives Forge Energy to neighbours, never accepts it.
 */
public class TileEnergyOutputHatch extends TileEnergyHatch {

    public TileEnergyOutputHatch(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.ENERGY_OUTPUT_HATCH, pos, state, IOType.OUTPUT,
                state.getValue(BlockEnergyOutputHatch.SIZE).getMaxEnergy(),
                state.getValue(BlockEnergyOutputHatch.SIZE).getMaxTransfer());
    }

    /**
     * Gives Forge Energy to every neighbour that will take it (PLAN section 383).
     *
     * <h2>What was missing, and why this is a reconstruction rather than a port</h2>
     *
     * <p>Upstream's {@code TileEnergyOutputHatch} is 288 lines; this class was 37 and constructor-only, so the hatch held
     * energy, exposed it to the machine, and pushed it NOWHERE. PLAN section 365.2 called it "a behaviour to reconstruct
     * rather than a method to copy", and this method is the part of those 288 lines that has a meaning on 1.20.1.
     *
     * <p><b>Most of upstream's length is other mods' transports</b> -- Draconic Evolution, GregTech and FluxNetworks each
     * get a branch in its per-face loop, and IndustrialCraft 2 gets an on-load callback. None of those mods exists on
     * 1.20.1 (PLAN section 359.5 lists IC2 as topping out at 1.19.2), so the branch that matters here is upstream's
     * "FE / RF Transfer" one, and Forge Energy is the whole of it.
     *
     * <h2>The three rules taken from upstream, each of which a guess would get wrong</h2>
     *
     * <ol>
     *   <li><b>It is NOT throttled.</b> Upstream pushes every tick, capped by {@code size.transferLimit}; the item buses
     *       use a random 5..60 tick delay (section 366.2) and this does not. Sampling the energy buffer on the same cadence
     *       as the item buses would make a power hatch deliver a fraction of its rated throughput.</li>
     *   <li><b>The capability is queried with {@code face.getOpposite()}</b> -- ask the neighbour for the face that points
     *       BACK at us. Passing {@code face} asks for its far side and finds nothing on most blocks.</li>
     *   <li><b>The loop breaks once the budget is spent.</b> Upstream's {@code if (maxCanExtract &lt;= 0) break;}, and
     *       without it every later face is still asked -- which is harmless for FE but would matter for the mod-specific
     *       transports upstream has, and is the structure a reader should not have to re-derive.</li>
     * </ol>
     *
     * <h2>And why the transfer limit needs no field here</h2>
     *
     * <p>The constructor passes {@code getMaxTransfer()} into the base, which bakes it into the {@code EnergyStorage} --
     * so {@link #extractEnergy}{@code (amount, true)} already returns a value clamped to it. That is what upstream's
     * {@code Math.min(this.size.transferLimit, this.energy.get())} computes by hand, and taking it from the storage keeps
     * the limit in ONE place instead of two that can disagree.
     *
     * @param level the level, always server-side when this is reached
     * @param pos this hatch's position
     */
    public void step(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        // Simulated: how much this hatch COULD give away right now, already clamped to its transfer limit. Nothing has
        // moved yet -- a neighbour that accepts none of it must not leave the hatch drained.
        //
        // Taken through getEnergyStorage() rather than consumeInternal(): the latter is the owner-facing draw path and
        // it carries a trace log for the first few draws, which a path that runs EVERY TICK would turn into spam. This
        // routes through the storage's own overridden extractEnergy, so setChanged() still fires.
        int budget = getEnergyStorage().extractEnergy(Integer.MAX_VALUE, true);
        if (budget <= 0) {
            return;
        }

        boolean moved = false;
        for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
            if (budget <= 0) {
                break;
            }
            net.minecraft.world.level.block.entity.BlockEntity neighbour =
                    level.getBlockEntity(pos.relative(face));
            if (neighbour == null) {
                continue;
            }
            int offered = budget;
            var accepted = neighbour.getCapability(
                            net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,
                            face.getOpposite())
                    .map(handler -> handler.receiveEnergy(offered, false))
                    .orElse(0);
            if (accepted <= 0) {
                continue;
            }
            // Extract for real, and trust the storage's own clamping rather than a second copy of the limit.
            int taken = getEnergyStorage().extractEnergy(accepted, false);
            budget -= taken;
            if (taken > 0) {
                moved = true;
            }
        }

        if (moved) {
            // Upstream's markNoUpdateSync(): clients show the buffer, so a push that changed it must be synced. Guarded
            // by `moved` rather than called unconditionally, because a hatch surrounded by full pipes would otherwise
            // mark itself dirty every tick forever. The storage's own extractEnergy already calls setChanged() when it
            // moves anything; this is the belt-and-braces call for the case where the capability path is not the one
            // that moved it.
            setChanged();
        }
    }
}
