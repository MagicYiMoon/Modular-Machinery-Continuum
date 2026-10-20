/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The source mod's coordinate-bound mirror of an ME pattern provider. */
public class BlockMEPatternMirrorImage extends BlockMEMachineComponent {

    public BlockMEPatternMirrorImage(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    protected BlockEntity createTile(BlockPos pos, BlockState state) {
        return MEBusTiles.createPatternMirror(
                hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_PATTERN_MIRROR,
                pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (MEBusTiles.handlePatternMirrorSettings(level, pos, player, hand)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternMirrorImage mirror) {
            BlockPos bound = mirror.getProviderPos();
            if (bound == null) {
                player.displayClientMessage(Component.translatable("message.blockmepatternprovider.tip0"), false);
            } else {
                player.displayClientMessage(Component.translatable("message.blockmepatternprovider.tip1",
                        bound.getX(), bound.getY(), bound.getZ()), false);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.mepatternmirrorimage"));
    }

    /** The capability is a view of a remote provider; never spill that provider when the mirror is broken. */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        // The normal machine-component implementation drops every ITEM_HANDLER slot.
        // For a mirror those slots belong to the bound provider at another position.
    }
}
