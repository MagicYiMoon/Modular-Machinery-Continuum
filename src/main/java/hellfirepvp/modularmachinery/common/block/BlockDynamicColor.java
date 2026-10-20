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
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A block that participates in machine colour tinting.
 *
 * <p>1.12.2 expressed this with {@code Block#getColorMultiplier(IBlockState, IBlockAccess, BlockPos, int)},
 * which no longer exists. On 1.20.1 tints come from a {@code BlockColor} registered per block against
 * {@code RegisterColorHandlersEvent.Block}, so a dynamic-colour block only has to declare a tint
 * index and hand back a colour for it.
 *
 * <p>Upstream had two parallel interfaces, {@code BlockDynamicColor} and {@code ItemDynamicColor};
 * on 1.20.1 the item side is an {@code ItemColor} registration. This interface covers the block side
 * and is deliberately trivial so that every machine component can implement it for free.
 */
public interface BlockDynamicColor {

    /**
     * @param tintIndex the tint index being resolved, as passed by the block colour handler
     * @return an ARGB colour, or {@code -1} to leave the texture untinted
     */
    int getColorMultiplier(int tintIndex);
}
