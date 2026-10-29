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
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;





























 
public final class MEBusTiles {

    private MEBusTiles() {
    }

    

    /**
     * Builds the tile for one of the ME item bus blocks.
     *
     * <p><b>THE SLOT COUNT IS NOT PASSED HERE ANY MORE, AND THAT IS A FIX RATHER THAN A TIDY-UP.</b> This class used to
     * hold one {@code SLOTS = 27} for both blocks and hand it to both constructors, while upstream's input bus is
     * 16 buffer + 16 configuration slots and its output bus is 36. The number now lives in the class it describes
     * ({@code MEItemInputBus#STORAGE_SLOTS}/{@code CONFIG_SLOTS}, {@code MEItemOutputBus#SLOTS}) and there is no
     * argument left for a caller to get wrong -- which is what the audit of the two directions found: one shared
     * constant, wrong for both.</p>
     *
     * @param direction which bus, from the block's own field
     * @param pos the position the block entity is being created at
     * @param state the block's state
     * @return the tile, or {@code null} if the guarded register produced no type for it
     */
    @Nullable
    public static BlockEntity create(boolean input, BlockPos pos, BlockState state) {
        // Reached only when AE2 is loaded -- see the class javadoc. Returning null rather than throwing if the register is
        // somehow absent, so a misordered load degrades to "the block places and does nothing" rather than a crash.
        var type = input
                ? hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_ITEM_INPUT_BUS
                : hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_ITEM_OUTPUT_BUS;
        if (!type.isPresent()) {
            return null;
        }
        return input
                ? new MEItemInputBus(type, pos, state)
                : new MEItemOutputBus(type, pos, state);
    }

    

    /**
     * Builds the tile for one of the ME fluid bus blocks.
     *
     * <p>Separate from {@link #create} rather than folded into it with a flag, because the two families pass DIFFERENT
     * constructors: the item bus takes a slot count, the fluid bus takes nothing (nine tanks of 8000 mB are its own constants).
     * A single method with a boolean would have to guess which arguments apply.</p>
     */
    @Nullable
    public static BlockEntity createFluid(boolean input, BlockPos pos, BlockState state) {
        var type = input
                ? hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_FLUID_INPUT_BUS
                : hellfirepvp.modularmachinery.common.registry.RegistryMEBlockEntities.ME_FLUID_OUTPUT_BUS;
        if (!type.isPresent()) {
            return null;
        }
        return input
                ? new MEFluidInputBus(type, pos, state)
                : new MEFluidOutputBus(type, pos, state);
    }

    






















 
    public static boolean openItemInputBusMenu(net.minecraft.world.level.Level level, BlockPos pos,
                                               net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof MEItemInputBus bus)) {
            return false;
        }
        // NetworkHooks.openScreen with an explicit buffer writer, the shape every other block in this port uses for a
        // position-carrying menu -- the same call openPatternProviderMenu below makes, and BlockBus.use before it.
        // ServerPlayer.openMenu(MenuProvider, BlockPos) does NOT exist on 1.20.1, which a compile caught once already.
        net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inventory, ignored) ->
                                hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus
                                        .forBus(windowId, inventory, bus),
                        // The mod's own key, already shipped in both language files and already the title this tile's
                        // screen has carried since the asset was converted.
                        net.minecraft.network.chat.Component.translatable("gui.meiteminputbus.title")),
                buffer -> buffer.writeBlockPos(pos));
        return true;
    }

    


























































 
    public static boolean handleItemBusSettingsTransfer(net.minecraft.world.level.Level level, BlockPos pos,
                                                       net.minecraft.world.entity.player.Player player,
                                                       net.minecraft.world.InteractionHand hand,
                                                       String settingsName) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        // The provider first: a click with a card on something that has no settings is not this method's business.
        if (!(level.getBlockEntity(pos) instanceof SettingsTransfer settingsProvider)) {
            return false;
        }
        net.minecraft.world.item.ItemStack held = serverPlayer.getItemInHand(hand);
        if (!(held.getItem() instanceof appeng.api.implementations.items.IMemoryCard memoryCard)) {
            return false;
        }

        if (serverPlayer.isShiftKeyDown()) {
            net.minecraft.nbt.CompoundTag tag = settingsProvider.downloadSettings();
            if (tag != null) {
                memoryCard.setMemoryCardContents(held, settingsName, tag);
                return true;
            }
            return false;
        }

        String savedName = memoryCard.getSettingsName(held);
        net.minecraft.nbt.CompoundTag tag = memoryCard.getData(held);
        if (settingsName.equals(savedName)) {
            // An empty tag rather than a null one: the port's upload implementations tolerate a tag that lacks
            // their key (see SettingsTransfer#uploadSettings), and AE2's own getData is free to return null for a
            // blank card -- which upstream would have passed straight on.
            settingsProvider.uploadSettings(tag == null ? new net.minecraft.nbt.CompoundTag() : tag);
            memoryCard.notifyUser(serverPlayer, appeng.api.implementations.items.MemoryCardMessages.SETTINGS_LOADED);
            return true;
        }
        // A card from a DIFFERENT component: refuse it by name and let the caller's own behaviour continue.
        // INVALID_MACHINE is AE2's own message for exactly this case, which is why it is upstream's choice here.
        memoryCard.notifyUser(serverPlayer, appeng.api.implementations.items.MemoryCardMessages.INVALID_MACHINE);
        return false;
    }

    /**
     * Opens the ME item OUTPUT bus's menu — <b>the direction that could not be opened at all</b> (AUDIT_COMPONENTS
     * row 11), built as the mirror of {@link #openItemInputBusMenu}.
     *
     * <p><b>Upstream has a second block method for this:</b> {@code BlockMEItemOutputBus.onBlockActivated} (U27-U47)
     * opens {@code GuiType.ME_ITEM_OUTPUT_BUS}, and the port's {@code BlockMEItemBus} carries a {@code Direction} flag
     * instead of two block classes, so the same open step is chosen by the flag rather than by the class. The port's
     * {@code use} previously opened a menu for the INPUT direction only, and its javadoc said so in as many words ("an
     * output bus still cannot be opened") — that sentence is what this method retires.</p>
     *
     * <p>The firewalls are the three this class already documents: the signature names no AE2 type and returns a
     * primitive (a body's names are resolved when its class links, and the return type of an invoked method when the
     * CALLER is verified), the AE2-typed cast lives HERE rather than in the block, and the guard that keeps this class
     * from being loaded without AE2 stays in the caller.</p>
     *
     * @return true when the menu was opened; false on the client, for a non-server player, or when the position holds
     *         no item output bus
     */
    public static boolean openItemOutputBusMenu(net.minecraft.world.level.Level level, BlockPos pos,
                                               net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof MEItemOutputBus bus)) {
            return false;
        }
        // NetworkHooks.openScreen with an explicit buffer writer, the shape every other block in this port uses for a
        // position-carrying menu -- the same call openItemInputBusMenu above makes. ServerPlayer.openMenu(MenuProvider,
        // BlockPos) does NOT exist on 1.20.1, which a compile caught once already.
        net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inventory, ignored) ->
                                hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus
                                        .forBus(windowId, inventory, bus),
                        // The key upstream's own screen draws at (8, 8) (GuiMEItemOutputBus U56), already shipped in
                        // both language files -- and an orphan key until this screen existed.
                        net.minecraft.network.chat.Component.translatable("gui.meitemoutputbus.title")),
                buffer -> buffer.writeBlockPos(pos));
        return true;
    }

    






















 
    public static boolean openFluidInputBusMenu(net.minecraft.world.level.Level level, BlockPos pos,
                                                net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof MEFluidInputBus bus)) {
            return false;
        }
        // The same call shape as openItemInputBusMenu above, and the same as the one the block used to make itself:
        // NetworkHooks.openScreen with an explicit buffer writer, because ServerPlayer.openMenu(MenuProvider, BlockPos)
        // does NOT exist on 1.20.1.
        net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inventory, ignored) ->
                                hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus
                                        .forBus(windowId, inventory, bus),
                        net.minecraft.network.chat.Component.translatable("gui.mefluidinputbus.title")),
                buffer -> buffer.writeBlockPos(pos));
        return true;
    }

    /** Opens the source mod's read-only ME fluid output bus screen. */
    public static boolean openFluidOutputBusMenu(net.minecraft.world.level.Level level, BlockPos pos,
                                                 net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof MEFluidOutputBus bus)) {
            return false;
        }
        net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inventory, ignored) ->
                                hellfirepvp.modularmachinery.common.menu.MenuMEFluidOutputBus
                                        .forBus(windowId, inventory, bus),
                        net.minecraft.network.chat.Component.translatable("gui.mefluidoutputbus.title")),
                buffer -> buffer.writeBlockPos(pos));
        return true;
    }

    








 
    @Nullable
    public static BlockEntity createPatternProvider(
            net.minecraftforge.registries.RegistryObject<
                    net.minecraft.world.level.block.entity.BlockEntityType<TileMEPatternProvider>> type,
            BlockPos pos, BlockState state) {
        if (!type.isPresent()) {
            return null;
        }
        return new TileMEPatternProvider(type, pos, state);
    }

    /** Builds the source mod's coordinate-only pattern mirror. */
    @Nullable
    public static BlockEntity createPatternMirror(
            net.minecraftforge.registries.RegistryObject<
                    net.minecraft.world.level.block.entity.BlockEntityType<TileMEPatternMirrorImage>> type,
            BlockPos pos, BlockState state) {
        if (!type.isPresent()) return null;
        return new TileMEPatternMirrorImage(type, pos, state);
    }

    /**
     * Applies an AE2 memory card binding to a mirror.  The card format is the
     * source format: the provider block's description id identifies the card and
     * {@code Pos} stores the target position.
     */
    public static boolean handlePatternMirrorSettings(net.minecraft.world.level.Level level, BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player,
                                                      net.minecraft.world.InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.isShiftKeyDown()) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof TileMEPatternMirrorImage mirror)) return false;
        net.minecraft.world.item.ItemStack held = serverPlayer.getItemInHand(hand);
        if (!(held.getItem() instanceof appeng.api.implementations.items.IMemoryCard memoryCard)) return false;
        String settingsName = "block.modularmachinery.blockmepatternprovider";
        String savedName = memoryCard.getSettingsName(held);
        if (!settingsName.equals(savedName)
                && !"tile.modularmachinery.blockmepatternprovider".equals(savedName)) {
            memoryCard.notifyUser(serverPlayer, appeng.api.implementations.items.MemoryCardMessages.INVALID_MACHINE);
            return true;
        }
        net.minecraft.nbt.CompoundTag data = memoryCard.getData(held);
        if (data == null || !data.contains("Pos")) return false;
        mirror.setProviderPos(BlockPos.of(data.getLong("Pos")));
        serverPlayer.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.blockmepatternprovider.load"), false);
        return true;
    }

    /** Saves a pattern provider coordinate in the source-compatible AE2 memory-card format. */
    public static boolean handlePatternProviderSettings(net.minecraft.world.level.Level level, BlockPos pos,
                                                        net.minecraft.world.entity.player.Player player,
                                                        net.minecraft.world.InteractionHand hand,
                                                        String settingsName) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || hand != net.minecraft.world.InteractionHand.MAIN_HAND
                || !serverPlayer.isShiftKeyDown()) return false;
        net.minecraft.world.item.ItemStack held = serverPlayer.getItemInHand(hand);
        if (!(held.getItem() instanceof appeng.api.implementations.items.IMemoryCard memoryCard)) return false;
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.putLong("Pos", pos.asLong());
        memoryCard.setMemoryCardContents(held, settingsName, data);
        serverPlayer.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.blockmepatternprovider.save"), false);
        return true;
    }

    







 
    public static boolean openPatternProviderMenu(net.minecraft.world.level.Level level, BlockPos pos,
                                                  net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof TileMEPatternProvider provider)) {
            return false;
        }
        // openMenu is a DEFAULT method on PatternProviderLogicHost, which the tile implements, and forBlockEntity is the
        // public factory -- BlockEntityLocator itself is package-private in AE2 and cannot be constructed from here.
         
        // at U76, depending on how the block was clicked. The port equivalent is the same split -- a
        // modifier-click reaches AE2 own pattern editor (pure AE2 functionality, nothing to reimplement)
        // and a plain click reaches the port own screen.
        if (serverPlayer.isShiftKeyDown()) {
            // U68 equivalent. openMenu is a DEFAULT method on PatternProviderLogicHost -- BlockEntityLocator
            // is package-private in AE2, so MenuLocators.forBlockEntity is the only way to obtain one.
            provider.openMenu(serverPlayer, appeng.menu.locator.MenuLocators.forBlockEntity(provider));
        } else {
            // U76 equivalent: the port own screen. NetworkHooks.openScreen with an explicit buffer writer,
            // which is the shape every other block in this port uses for a position-carrying menu -- see
            // BlockBus.use. ServerPlayer.openMenu(MenuProvider, BlockPos) does NOT exist on 1.20.1, which a
            // compile caught rather than a runtime surprise.
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider(
                            (windowId, inventory, p) -> new hellfirepvp.modularmachinery.common.menu.MenuMEPatternProvider(
                                    windowId, inventory, pos),
                            net.minecraft.network.chat.Component.translatable("gui.modularmachinery.me_pattern_provider")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return true;
    }
}
