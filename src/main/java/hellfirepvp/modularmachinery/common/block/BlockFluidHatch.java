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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import hellfirepvp.modularmachinery.common.block.prop.FluidHatchSize;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Base for the fluid input/output hatches.
 *
 * <p>Variant space is {@link FluidHatchSize} ({@code size=tiny} … {@code size=vacuum}), matching the
 * shipped {@code blockfluidinputhatch.json} / {@code blockfluidoutputhatch.json}.
 *
 * <p>{@link #SIZE} is {@code static final} because vanilla calls {@code createBlockStateDefinition}
 * from inside the {@code Block} constructor — see {@link BlockVariantComponent}.
 */
public abstract class BlockFluidHatch extends BlockVariantComponent<FluidHatchSize> {

    public static final EnumProperty<FluidHatchSize> SIZE = FluidHatchSize.createProperty();

    protected BlockFluidHatch(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, FluidHatchSize.NORMAL));
    }

    @Override
    protected EnumProperty<FluidHatchSize> variantProperty() {
        return SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Upstream {@code BlockFluidHatch.addInformation} (L58-L64) changes its own WORDING at the bottom
     * of the ladder</b>, and that is the one place in the whole tooltip feature where the tier picks the key
     * rather than only the number: the top tier reports a capacity
     * ({@code tooltip.fluidhatch.tank.info}, "Can hold %s mb") while the bottom tier reports that it holds
     * nothing at all ({@code tooltip.fluidhatch.empty}, "Empty", no placeholder). Both keys already ship.</p>
     *
     * <p>This is a deviation from the shape of every other family, and it is upstream's shape rather than
     * one invented here. Upstream's other two lines on this block — {@code tooltip.fluidhatch.tank.mek},
     * which it guarded with a live Mekanism presence check — are NOT emitted: a presence check on the
     * tooltip path is a separate question, and the localisation of that guard is recorded as open rather
     * than guessed.</p>
     */
    @Nullable
    @Override
    public net.minecraft.network.chat.Component tipLine(BlockState state, String tierName) {
        java.util.Optional<FluidHatchSize> resolved = SIZE.getValue(tierName);
        if (resolved.isEmpty() || !resolved.get().equals(state.getValue(SIZE))) {
            return null;
        }
        FluidHatchSize tier = resolved.get();
        return net.minecraft.network.chat.Component.translatable(
                tier == FluidHatchSize.TINY ? "tooltip.fluidhatch.empty" : "tooltip.fluidhatch.tank.info",
                tier.getTipValue());
    }

    @Nullable
    @Override
    protected abstract BlockEntity createBlockEntity(FluidHatchSize variant, BlockPos pos, BlockState state);

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
         
        //
        // The client reported that a bucket could not be used on the fluid input bus: this method went
        // straight to openScreen, so a bucket in hand did nothing at all.
        //
        // FluidUtil.interactWithFluidHandler does the whole job -- it finds the IFluidHandler capability at
        // the position, works out whether the held stack should FILL the tank or be FILLED by it, and swaps
        // the container in the player's hand. It returns false when the held item is not a fluid container,
        // which is exactly the case that should fall through to the GUI -- so an empty hand still opens it.
        //
        // The signature is taken from the Forge 1.20.1 sources, not from memory:
        //   interactWithFluidHandler(Player, InteractionHand, Level, BlockPos, @Nullable Direction) -> boolean
        //
         
        //
        // The drain gate on the tank refuses external drain on an INPUT hatch so a neighbouring machine cannot
        // steal what the owner is collecting. That is a rule about automation, and the client found its edge:
        // an empty bucket could not take the fluid back out. A player standing in front of the hatch is not a
        // neighbouring machine, so the call runs inside the tank's player-access window.
        if (level.getBlockEntity(pos)
                instanceof hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank tank
                && tank.withPlayerAccess(() -> net.minecraftforge.fluids.FluidUtil
                        .interactWithFluidHandler(player, hand, level, pos, hit.getDirection()))) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Shared parent, as with BlockEnergyHatch and BlockBus.
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank hatch) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuFluidHatch
                                            .forHatch(windowId, inventory, hatch),
                            Component.translatable("gui.modularmachinery.fluid_hatch")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
