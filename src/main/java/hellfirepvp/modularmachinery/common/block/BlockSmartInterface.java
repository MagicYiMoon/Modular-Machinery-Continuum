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
import hellfirepvp.modularmachinery.common.block.prop.SmartInterfaceType;
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Smart interface block — {@code modularmachinery:blocksmartinterface}.
 *
 * <p>Carries a {@link SmartInterfaceType} ({@code type=number} / {@code type=string}), matching the
 * shipped blockstate JSON. Upstream encoded the same distinction in block metadata.
 */
public class BlockSmartInterface extends BlockVariantComponent<SmartInterfaceType> {

    /** Read by {@code TileSmartInterface}; see {@link BlockVariantComponent}. */
    public static final EnumProperty<SmartInterfaceType> TYPE = SmartInterfaceType.createProperty();

    public BlockSmartInterface(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, SmartInterfaceType.NUMBER));
    }

    @Override
    protected EnumProperty<SmartInterfaceType> variantProperty() {
        return TYPE;
    }

    @Override
    protected BlockEntity createBlockEntity(SmartInterfaceType variant, BlockPos pos, BlockState state) {
        return new TileSmartInterface(pos, state);
    }

    /**
     * Hands the server the interface's own tick, which is the prune.
     *
     * <p><b>Upstream needed no wiring at all</b> — its tile implemented {@code ITickable}, so
     * {@code doRestrictedTick} (U43-U72) ran for every interface, every tick, with an early return on the
     * client and a one-second cadence after that. On 1.20.1 ticking is opted into per block entity type, so
     * without this override the prune would be dead code that looks wired: {@code TileSmartInterface#tick}
     * would exist and never be called, and a datum whose controller was broken would sit on the interface for
     * ever. This is the same pattern {@code BlockFactoryController#getTicker} uses, and the client side returns
     * null because a client's copy of the data is only ever written by the sync packet.</p>
     */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TileSmartInterface iface) {
                iface.tick();
            }
        };
    }

    /**
     * Opens the smart interface screen.
     *
     * <p>The block was registered and placed fine but did nothing on right-click, which reads as a broken
     * block rather than a missing screen.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.TileSmartInterface iface) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuSmartInterface
                                            .forInterface(windowId, inventory, iface),
                            Component.translatable("gui.modularmachinery.smart_interface")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}