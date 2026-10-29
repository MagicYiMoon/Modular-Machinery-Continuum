/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
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




























 
public class BlockMEFluidInputBus extends BlockMEFluidBus {

    public BlockMEFluidInputBus(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity createTile(BlockPos pos, BlockState state) {
        return MEBusTiles.createFluid(true, pos, state);
    }

    




















 
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            return InteractionResult.PASS;
        }
        if (MEBusTiles.handleItemBusSettingsTransfer(level, pos, player, hand,
                state.getBlock().getDescriptionId())) {
            return InteractionResult.SUCCESS;
        }
        MEBusTiles.openFluidInputBusMenu(level, pos, player);
        return InteractionResult.SUCCESS;
    }
}
