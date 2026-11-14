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

/**
 * modularmachinery:blockmepatternprovider -- the block form of TileMEPatternProvider.
 *
 * EXPERIMENT STATE (PLAN section 452). createTile currently names NO TYPE AT ALL. Section 451 established by
 * experiment that the trigger is this CLASS and not the registration position; this file isolates whether the
 * trigger is the BODY of createTile, which normally reaches the tile through MEBusTiles.
 *
 * If registering THIS passes standalone, the body is the trigger. If it fails, the body is exonerated and the
 * trigger is in the class hierarchy this class shares with the four working ME buses.
 *
 * The AE2 guard is inherited from BlockMEMachineComponent, which makes newBlockEntity final (section 429).
 */
public class BlockMEPatternProvider extends BlockMEMachineComponent {

    public BlockMEPatternProvider(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    protected BlockEntity createTile(BlockPos pos, BlockState state) {
        // CHAIN A. createPatternProvider returns BlockEntity, which is what keeps this body from resolving
        // TileMEPatternProvider and through it AE2 (PLAN section 452).
        return MEBusTiles.createPatternProvider(
                hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_PATTERN_PROVIDER, pos, state);
    }

    /**
     * Opens AE2 pattern-editing screen on right-click (PLAN section 459).
     *
     * THIS BODY NAMES ONLY MEBusTiles. AE2 is reached inside that class, behind a signature of vanilla types, for the
     * class-linking reason section 452 established: a method body is verified when its CLASS is linked, so naming an
     * AE2-adjacent type here would break the standalone configuration even though the line only runs on a click.
     *
     * Upstream opened TWO GUIs (section 458): AE2 renamer at U68, its own screen at U76. THIS IS THE FIRST OF THOSE.
     * The provider own screen is still unported.
     */
    @Override
    public net.minecraft.world.InteractionResult use(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        // THE GUARD THAT WAS MISSING, and a crash report found it (PLAN section 483):
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
