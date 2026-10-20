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
import hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Upgrade bus block — {@code modularmachinery:blockupgradebus}.
 *
 * <p>Shares its {@code type=} ladder with the parallel controller (both are
 * {@link UpgradeBusData}), which is why one enum serves two blocks — the same relationship
 * upstream expressed with two parallel metadata tables.
 */
public class BlockUpgradeBus extends BlockVariantComponent<UpgradeBusData> {

    /** Read by {@code TileUpgradeBus} to size the upgrade inventory; see {@link BlockVariantComponent}. */
    public static final EnumProperty<UpgradeBusData> TYPE = UpgradeBusData.createProperty();

    public BlockUpgradeBus(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, UpgradeBusData.NORMAL));
    }

    @Override
    protected EnumProperty<UpgradeBusData> variantProperty() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Upstream {@code BlockUpgradeBus.addInformation} at its L50-L54 emits exactly this line, with exactly
     * this key and {@code data.getMaxUpgradeSlot()} as its argument. The argument now comes from
     * {@link UpgradeBusData#getTipValue()}, i.e. from the tier itself.</p>
     */
    @Override
    protected String tipKey() {
        return "block.modularmachinery.blockupgradebus.tip";
    }

    @Override
    protected BlockEntity createBlockEntity(UpgradeBusData variant, BlockPos pos, BlockState state) {
        return new TileUpgradeBus(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TileUpgradeBus bus) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuUpgradeBus
                                            .forBus(windowId, inventory, bus),
                            Component.translatable("gui.modularmachinery.upgrade_bus")),
                    // The payload is the MENU's own writer, so the client's half and the check that decodes it
                    // read one definition rather than two (defect B; see MenuUpgradeBus#writeOpenData).
                    buffer -> hellfirepvp.modularmachinery.common.menu.MenuUpgradeBus
                            .writeOpenData(buffer, bus));
        }
        return InteractionResult.SUCCESS;
    }
}