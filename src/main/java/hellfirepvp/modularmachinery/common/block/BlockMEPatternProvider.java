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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;












 
public class BlockMEPatternProvider extends BlockMEMachineComponent {

    public BlockMEPatternProvider(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    protected BlockEntity createTile(BlockPos pos, BlockState state) {
        // CHAIN A. createPatternProvider returns BlockEntity, which is what keeps this body from resolving
         
        return MEBusTiles.createPatternProvider(
                hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_PATTERN_PROVIDER, pos, state);
    }

    








 
    @Override
    public net.minecraft.world.InteractionResult use(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
         
        //
        //   NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost
        //       at BlockMEPatternProvider.use(BlockMEPatternProvider.java:67)
        //
        // THE BLOCK IS REGISTERED UNCONDITIONALLY (all 38 are, in both configurations), so a player CAN
        // right-click it on a client WITHOUT AE2 -- and this body names MEBusTiles, whose own method bodies
        // name the AE2-dependent tiles. BlockMEMachineComponent guards newBlockEntity, but use() is a
        // SECOND unconditional entry point and it was not guarded.
        //
        // So the check goes HERE, before the indirection is touched: with AE2 absent the block is inert,
        // which is the same degradation newBlockEntity already produces (no block entity, block still places).
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (MEBusTiles.handlePatternProviderSettings(level, pos, player, hand,
                state.getBlock().getDescriptionId())) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (MEBusTiles.openPatternProviderMenu(level, pos, player)) {
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
}
