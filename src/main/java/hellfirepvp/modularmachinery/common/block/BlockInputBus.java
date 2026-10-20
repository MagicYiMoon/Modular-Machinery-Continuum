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

import hellfirepvp.modularmachinery.common.block.prop.ItemBusSize;
import hellfirepvp.modularmachinery.common.tiles.TileItemInputBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Item input bus block.
 *
 * <p>Registered as {@code modularmachinery:blockinputbus}; its blockstate JSON already keys the seven
 * tiers on {@code size=}, so the only job here is to hand the position and state to the block entity,
 * which resolves the tier itself.
 */
public class BlockInputBus extends BlockBus {

    public BlockInputBus(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntity createBlockEntity(ItemBusSize variant, BlockPos pos, BlockState state) {
        return new TileItemInputBus(pos, state);
    }
}
