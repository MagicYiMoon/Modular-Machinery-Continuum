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

/**
 * Base for the Applied Energistics 2 FLUID buses (PLAN section 427).
 *
 * <h2>Upstream's structure, restored under rule A0</h2>
 *
 * <p>An earlier version of this port merged the fluid buses into {@code BlockMEItemBus} behind a {@code Kind} flag, justified by rule
 * B's "reuse". **Rule A0 was added to the goal afterwards and it subordinates B: upstream has THREE classes here, and no evidence was
 * produced that they cannot work on 1.20.1.** **This class restores that shape.**</p>
 *
 * <p>Upstream's files and sizes, read at §425.1:</p>
 *
 * <pre>
 *   BlockMEFluidBus (33)        abstract, extends BlockMEMachineComponent, carries breakBlock
 *   BlockMEFluidInputBus (59)   onBlockActivated (U28) + createTileEntity (U48) + addInformation (U54)
 *   BlockMEFluidOutputBus (35)
 * </pre>
 *
 * <h2>What this class is, and what is deliberately absent</h2>
 *
 * <p><b>Upstream's {@code breakBlock} -- the body of this class's shared behaviour -- is NOT ported here, because §364 already covers
 * it globally.</b> {@code BlockMachineComponent.playerDestroy} drains the block's {@code ITEM_HANDLER} through the CAPABILITY, one
 * override for all 125 blocks. **That is not a deviation from upstream's BEHAVIOUR; it is the port's single implementation of the same
 * behaviour, and it cannot miss a slot because it walks the handler rather than naming slots.**</p>
 *
 * <p><b>Upstream's {@code BlockMEMachineComponent} layer is now present (PLAN section 429).</b> §427.3 recorded its absence as an open
 * rule-A0 question -- a deviation with no evidence behind it. **Reading upstream's 77 lines settled it: most of that class is 1.12.2
 * plumbing whose 1.20.1 form is {@code BlockBehaviour.Properties} (already supplied by the caller), and its ONE piece of live content is
 * the AE2 presence guard.** **That guard was duplicated between this class and {@code BlockMEItemBus}, and it is the most load-bearing
 * line in the whole ME block family.** The layer now exists and holds it, finally.</p>
 */
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
