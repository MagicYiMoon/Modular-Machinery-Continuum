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

import hellfirepvp.modularmachinery.common.block.BlockParallelController;
import hellfirepvp.modularmachinery.common.block.prop.ParallelControllerData;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Parallel controller — raises how many recipe executions a machine may run at once.
 *
 * <p>Upstream passed the parallelism value into the constructor
 * ({@code TileParallelController(int)}), having read it from block metadata. On 1.20.1 the tier comes
 * from the {@code type=} blockstate property, so the value is derived from the state and
 * {@link #getParallelism()} reports it. The live parallelism counter is persisted because it is
 * machine state, not derived state.
 */
public class TileParallelController extends TileMachineComponent {

    /**
     * The tier whose config entry decides this controller's ceiling.
     *
     * <p><b>THE CONFIGURED CEILING IS READ FROM THE TIER RATHER THAN CACHED HERE, AND THAT IS THE FIX.</b> This
     * class used to hold {@code private final int maxParallelism}, assigned from the tier once at construction, so
     * the number was frozen at PLACEMENT: a server owner could edit every key and an existing controller would keep
     * reporting the old figure until it was broken and replaced. The tier is a pure function of the blockstate, so
     * keeping it is cheaper than keeping the number and it cannot drift from the other readers of the same value
     * ({@code MenuParallelController}'s data slot, {@code BlockParallelController}'s tooltip via
     * {@code TierTipValue.getTipValue()}, and the clamp in {@link #setParallelism}).
     */
    private final ParallelControllerData tier;

    private int parallelism;

    public TileParallelController(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.PARALLEL_CONTROLLER, pos, state);
        this.tier = state.getValue(BlockParallelController.TYPE);
        this.parallelism = tier.getMaxParallelism();
    }

    /** @return the maximum parallel executions this controller permits, as configured for its tier */
    public int getMaxParallelism() {
        return tier.getMaxParallelism();
    }

    /** @return the currently available parallelism */
    public int getParallelism() {
        return Math.min(Math.max(1, parallelism), Math.max(1, getMaxParallelism()));
    }

    public void setParallelism(int parallelism) {
         
        // carry the rule because the packet is the authority for a client request and this is the
        // authority for everything else.
        int clamped = Math.min(Math.max(parallelism, 1), Math.max(1, getMaxParallelism()));
        if (this.parallelism != clamped) {
            this.parallelism = clamped;
            markForUpdate();
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.parallelism = tag.contains("parallelism") ? tag.getInt("parallelism") : getMaxParallelism();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("parallelism", parallelism);
    }
}
