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
     * Pushes once per server tick with one shared budget for all faces. Uses Flux Networks' native
     * capability when installed and enabled, otherwise Forge Energy. Each offer uses only one path,
     * and the buffer is debited only by the amount the neighbour actually accepted.
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
            int accepted = -1;
            if (hellfirepvp.modularmachinery.common.data.Config.enableFluxNetworksIntegration()
                    && net.minecraftforge.fml.ModList.get().isLoaded("fluxnetworks")) {
                accepted = hellfirepvp.modularmachinery.integration.fluxnetworks.FluxNetworksIntegration
                        .tryReceive(neighbour, face.getOpposite(), offered);
            }
            if (accepted < 0) accepted = neighbour.getCapability(
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
