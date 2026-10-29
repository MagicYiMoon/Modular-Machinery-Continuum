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















































 
public abstract class BlockMEMachineComponent extends BlockMachineComponent {

    protected BlockMEMachineComponent(Properties properties) {
        super(properties);
    }

    /**
     * Builds this block's grid-connected tile.
     *
     * <p><b>Called only when AE2 is present</b> — see {@link #newBlockEntity}. **And the implementation must reach the tile through
     * {@code MEBusTiles} rather than naming it inline**, for the class-load reason of §413: the JVM's verifier resolves the types a
     * method body names when the class is LINKED, so an inline reference is needed at load time however lazy the call site looks.</p>
     */
    @Nullable
    protected abstract BlockEntity createTile(BlockPos pos, BlockState state);

    /**
     * The single guarded door into the ME tile family.
     *
     * <p><b>FINAL on purpose:</b> this is the one place the AE2 presence check may live, and a subclass override is the way it would
     * otherwise be forgotten.</p>
     */
    @Nullable
    @Override
    public final BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            // The block places, has no block entity, and a structure JSON naming it still resolves -- the same state the six
            // remaining ME placeholders are permanently in.
            return null;
        }
        return createTile(pos, state);
    }
}
