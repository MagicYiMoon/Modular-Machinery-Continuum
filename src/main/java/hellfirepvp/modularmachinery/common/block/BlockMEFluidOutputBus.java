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

import hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * {@code modularmachinery:blockmefluidoutputbus} -- the block form of {@code MEFluidOutputBus}.
 *
 * <p>The mirror of {@link BlockMEFluidInputBus}, and upstream's file is 35 lines against the input's 59 -- **which is the same
 * asymmetry §401.4 found on the item side and for the same reason: an INPUT bus maintains a configured state and needs the plumbing
 * that implies, while an OUTPUT bus only empties itself.**</p>
 *
 * <p>The tile is created through {@link MEBusTiles}, never inline: that indirection is the class-load firewall of §413.</p>
 */
public class BlockMEFluidOutputBus extends BlockMEFluidBus {

    public BlockMEFluidOutputBus(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity createTile(BlockPos pos, BlockState state) {
        return MEBusTiles.createFluid(false, pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            return InteractionResult.PASS;
        }
        MEBusTiles.openFluidOutputBusMenu(level, pos, player);
        return InteractionResult.SUCCESS;
    }
}
