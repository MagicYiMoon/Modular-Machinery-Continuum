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

import hellfirepvp.modularmachinery.common.block.BlockFluidOutputHatch;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fluid output hatch — externally drainable, never externally fillable.
 *
 * <p>Capacity comes from {@link BlockFluidOutputHatch#SIZE}; see {@code TileFluidInputHatch} for why
 * the variant is resolved against a specific property rather than by name.
 */
public class TileFluidOutputHatch extends TileFluidTank {

    public TileFluidOutputHatch(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.FLUID_OUTPUT_HATCH, pos, state, IOType.OUTPUT,
                state.getValue(BlockFluidOutputHatch.SIZE).getCapacity());
    }
}
