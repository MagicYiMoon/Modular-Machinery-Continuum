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
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;






























 
public abstract class BlockMEFluidBus extends BlockMEMachineComponent {

    protected BlockMEFluidBus(Properties properties) {
        super(properties);
    }

    /**
     * Creates the grid-connected fluid tile.
     *
     * <p><b>The AE2 guard is NOT here -- it lives in {@link BlockMEMachineComponent}, where it is final.</b> Before §429, this class and
     * {@code BlockMEItemBus} each carried their own copy of that check, and §§404 and §411 each cost a round because it was wrong once.</p>
     *
     * <p>An implementation must reach the tile through {@code MEBusTiles} rather than naming it inline; §413 has why the verifier makes
     * an inline reference a load-time dependency.</p>
     */
    @Nullable
    @Override
    protected abstract BlockEntity createTile(BlockPos pos, BlockState state);
}
