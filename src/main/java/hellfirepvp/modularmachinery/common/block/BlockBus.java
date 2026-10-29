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
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import hellfirepvp.modularmachinery.common.block.prop.ItemBusSize;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Base for the item input/output buses.
 *
 * <p>The whole variant space is {@link ItemBusSize} ({@code size=tiny} … {@code size=ludicrous}), which
 * the shipped {@code blockinputbus.json} / {@code blockoutputbus.json} already key on. What this class
 * owns is the mapping from variant to slot count, which upstream derived from block metadata.
 *
 * <p>{@link #SIZE} is {@code static final} on purpose — see
 * {@link BlockVariantComponent} for why an instance field here crashes block construction.
 */
public abstract class BlockBus extends BlockVariantComponent<ItemBusSize> {

    private static final EnumProperty<ItemBusSize> SIZE = ItemBusSize.createProperty();

    protected BlockBus(Properties properties) {
        super(properties);
        // NOTE: createBlockStateDefinition has already run by now, during Block.<init>, using the
        // static SIZE. The default state must be registered here, after super().
        registerDefaultState(stateDefinition.any().setValue(SIZE, ItemBusSize.NORMAL));
    }

    @Override
    protected EnumProperty<ItemBusSize> variantProperty() {
        return SIZE;
    }

    /** @return the number of slots a bus of this variant exposes to the machine */
    protected int slotsFor(ItemBusSize size) {
        return size.getSlots();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Upstream {@code BlockBus.addInformation} (L73-L77) emits exactly this line with
     * {@code size.getSlotCount()} as its argument, and both keys already ship in this port's language
     * files. <b>One key for every tier, including the one-slot tier</b>: upstream's {@code tooltip.itembus
     * .slot} is "1 Slot" with no placeholder, and {@code String.format} leaves a placeholder-free template
     * alone — so the same call renders both, and giving TINY its own sentence would be a second rule for no
     * observable difference.</p>
     */
    @Override
    protected String tipKey() {
        return "tooltip.itembus.slots";
    }

    @Nullable
    @Override
    protected abstract BlockEntity createBlockEntity(ItemBusSize variant, BlockPos pos, BlockState state);

    












 
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type == hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities.ITEM_INPUT_BUS.get()) {
            return (tickLevel, pos, tickState, blockEntity) -> {
                if (blockEntity instanceof hellfirepvp.modularmachinery.common.tiles.TileItemInputBus bus
                        && tickLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    bus.step(serverLevel, pos);
                }
            };
        }
        if (type == hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities.ITEM_OUTPUT_BUS.get()) {
            return (tickLevel, pos, tickState, blockEntity) -> {
                if (blockEntity instanceof hellfirepvp.modularmachinery.common.tiles.TileItemOutputBus bus
                        && tickLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    bus.step(serverLevel, pos);
                }
            };
        }
        // Both item directions are now live. The energy and fluid hatches are separate work: the energy output hatch
        // pushes FE, and upstream's version is 288 lines against the port's 37, so it is a behaviour to reconstruct
         
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Sits on the shared abstract bus rather than on BlockInputBus/BlockOutputBus, so both directions
        // and every size tier get the GUI from one place. Duplicating it per subclass is how an input bus
        // ends up with a working screen and the output bus silently without one.
if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.base.TileItemBus bus) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuItemBus
                                            .forBus(windowId, inventory, bus),
                            Component.translatable("gui.modularmachinery.item_bus")),
                    // Position AND slot count. The count cannot be derived on the client (the bus size is
                    // a blockstate property and the chunk may be unloaded), and it must match on both
                    // sides or every slot index shifts during synchronisation.
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        // The TIER, not the slot count: a tier carries the count AND the grid geometry, so the
                        // client cannot build the right number of slots in the wrong places.
                         
                        //
                        // This read the BLOCKSTATE while the menu read the TIER FROM THE HANDLER, so the two
                        // could disagree -- and when they did, the client built a different number of bus slots
                        // than the server, every PLAYER-INVENTORY slot index shifted, and the client reported
                        // the player's items "suddenly moving" when a big or larger bus was opened. That is the
                        // same disagreement section 274 crashed on, reached through the other door.
                        buffer.writeVarInt(hellfirepvp.modularmachinery.common.block.prop.ItemBusSize
                                .forSlotCount(bus.getInventory().getSlots()).ordinal());
                    });
        }
        return InteractionResult.SUCCESS;
    }
}
