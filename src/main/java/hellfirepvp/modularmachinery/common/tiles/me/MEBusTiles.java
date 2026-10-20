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

/**
 * The only place that names an ME tile in a method signature, so that nothing which must load without AE2 ever names one.
 *
 * <h2>Why this class exists, stated as the failure it prevents</h2>
 *
 * <p>PLAN section 411 recorded the standalone run's rejection of a real ME block:</p>
 *
 * <pre>
 *   NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost
 *   Caused by: Registry Object not present: modularmachinery:blockmeiteminputbus
 * </pre>
 *
 * <p>and §410.4 had asserted the belief that made it a surprise: <i>"a method body is resolved lazily, so a class referencing AE2
 * is safe to load at registration time."</i></p>
 *
 * <p><b>That belief is wrong, and the reason is more fundamental than "the body runs later".</b> A class file's method bodies
 * name the types they use, and the JVM's <b>verifier</b> resolves those types when the class is LINKED -- not when the method
 * runs. Checking that the operand stack types are assignable requires loading them. <b>So a type appearing anywhere in a method
 * body is a type the class needs at load time</b>, and a lazy reference is not the same thing as a lazy LOAD.</p>
 *
 * <h2>The firewall, and why it is a separate class rather than a guard</h2>
 *
 * <p>{@code BlockMEItemBus} now calls {@link #create} and names only this class. <b>This class is the only one whose methods name
 * {@code MEItemInputBus}/{@code MEItemOutputBus}, and nothing loads it until {@link #create} is actually invoked</b> -- which the
 * caller does behind a {@code ModList} check, so it is never invoked without AE2.</p>
 *
 * <p>A guard INSIDE the same class could not have worked: the guard would be in a method of a class whose own method bodies
 * already named AE2 types, so the verification that fails would have happened before the guard ran.</p>
 */
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

    /**
     * Opens the ME item input bus's configuration menu — the firewalls for the right-click path (PLAN section 686).
     *
     * <p><b>The signature names no AE2 type and returns a primitive, and both parts are load-bearing.</b> The block's
     * {@code use} calls this, and a method body is VERIFIED when its class is LINKED — so a body naming
     * {@code MenuMEItemInputBus} in the BLOCK would drag the menu in, and the menu names {@code MEItemInputBus}, whose
     * hierarchy reaches AE2. §459 established the same rule for the pattern provider's open step and §452 for a return
     * type; this is that rule applied to the third block that opens a menu.</p>
     *
     * <p><b>The FLUID bus's open step is built here too now ({@link #openFluidInputBusMenu}, PLAN section 690), and
     * that closes an asymmetry §686 recorded rather than a preference:</b> {@code BlockMEFluidInputBus} used to name
     * {@code MEFluidInputBus} and its menu in its OWN {@code use}, and it was the weaker of the two forms — "naming the
     * menu here is fine" is the assumption §404 was written about, while "this class is only reached behind a guard" is
     * a claim that can be checked. Both blocks now guard their own {@code use} and reach the AE2-side tiles through this
     * class, so the family has ONE convention instead of two. <b>And the guard has to be in the BLOCK, not here:</b>
     * loading this class is itself the failure, so no check inside it can run (§413).</p>
     *
     * <p>The position travels in the open packet; the grid's length does not, because
     * {@code MenuMEItemInputBus.CONFIG_SLOTS} is a constant the client also reads (unlike the ordinary item bus, whose
     * tier varies per block).</p>
     *
     * @return true when the menu was opened; false on the client, for a non-server player, or when the position holds
     *         no item input bus
     */
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

    /**
     * THE MEMORY CARD STEP: sneaking saves a component's settings to the card, a plain click loads them back
     * (audit row 43).
     *
     * <h2>Upstream's body, and where it lived</h2>
     *
     * <pre>
     *   U K/block/appeng/BlockMEMachineComponent.java:57-76
     *       if (player.isSneaking()) {
     *           NBTTagCompound tag = settingsProvider.downloadSettings();
     *           if (tag != null) { memoryCard.setMemoryCardContents(heldItem, getTranslationKey(), tag); return true; }
     *       } else {
     *           String savedName = memoryCard.getSettingsName(heldItem);
     *           NBTTagCompound tag = memoryCard.getData(heldItem);
     *           if (getTranslationKey().equals(savedName)) {
     *               settingsProvider.uploadSettings(tag);
     *               memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
     *               return true;
     *           } else {
     *               memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
     *           }
     *       }
     *       return false;
     * </pre>
     *
     * <p><b>Deviation (rule A0) — upstream's method was on the BLOCK base class, and this one is here.</b>
     * Upstream's {@code BlockMEMachineComponent} is the parent of the AE2 blocks and its method signature names
     * {@code appeng.api.implementations.items.IMemoryCard}. <b>On 1.20.1 a signature is a LOAD-TIME dependency</b>:
     * the port's {@code BlockMEMachineComponent} is loaded unconditionally, because {@code BlockMEPlaceholder}
     * extends it and all eight ME blocks are registered whether or not AE2 is installed. Putting the method there
     * would make the standalone server fail to link that class — the exact
     * {@code NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost} of PLAN §404/§411/§413, reached
     * through a fourth door. So the AE2-naming method lives in THIS class, the one the port already designates as
     * "the only place that names an ME-side type", and its caller guards on AE2's presence before touching it.</p>
     *
     * <p><b>Upstream's card is AE2's own item, not a MMCE item</b> — that is what the audit row's "memory card
     * (SettingsTransfer)" resolves to, and it is why this method names no item class of this mod. The five
     * {@code IMemoryCard} methods it calls were read out of AE2 15.4.11 with {@code javap} rather than remembered:
     * {@code setMemoryCardContents(ItemStack, String, CompoundTag)}, {@code getSettingsName(ItemStack)},
     * {@code getData(ItemStack)}, {@code getColorCode(ItemStack)}, {@code notifyUser(Player, MemoryCardMessages)}
     * — the same five upstream's body uses, unchanged in 15.x.</p>
     *
     * <p><b>The identity check is a NAME, and it is the whole safety property of the load direction:</b> the card
     * stores the component's translation key beside the tag, so a card saved from a fluid bus is refused by an item
     * bus ({@code INVALID_MACHINE}) instead of having its foreign payload written into this one. The caller passes
     * that name in, because the name is the BLOCK's ({@code getTranslationKey()} upstream) and this class is not a
     * block.</p>
     *
     * <p><b>Upstream's notification set is kept exactly, including its silence:</b> the save direction notifies
     * nobody (only {@code setMemoryCardContents} runs) while the load direction reports
     * {@code SETTINGS_LOADED} or {@code INVALID_MACHINE}. Adding a "saved" message would have been a nicety
     * upstream does not have, and the check asserts the pair that does exist.</p>
     *
     * @param level        the level the click happened in
     * @param pos          the clicked position
     * @param player       the clicking player, whose held stack is read from {@code hand}
     * @param hand         the hand the card is in — the caller's own hand, so an off-hand press works
     * @param settingsName the name this component's settings are stored under; the caller's block identity
     * @return true when the click was consumed by the card, so the GUI must NOT open
     */
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

    /**
     * Opens the ME fluid input bus's configuration menu -- the same firewall as {@link #openItemInputBusMenu}, for the
     * other bus in this family that opens one (PLAN section 690).
     *
     * <p><b>Why this method exists rather than the block building its own menu, which is what it did before:</b>
     * {@code BlockMEFluidInputBus.use} named {@code MEFluidInputBus} INLINE, in an {@code instanceof}. That is a type
     * named in the method body of an unconditionally-registered block, and a body's names are resolved when the class
     * LINKS (section 413) -- the latent form of the {@code NoClassDefFoundError} that the item bus's click raised.</p>
     *
     * <p><b>It did NOT throw in this harness, and the reason is worth keeping rather than discarding:</b>
     * {@code instanceof} answers false for a NULL operand without resolving the class, and without AE2 there is never a
     * block entity at that position ({@code BlockMEMachineComponent.newBlockEntity} returns null). So that crash was
     * one non-null operand away — a world saved WITH AE2 and reopened without it is the obvious way to reach one — and
     * it is a latent load-time dependency rather than a live defect. The self-test asserts the SHAPE over the whole
     * block family for exactly that reason: the shape is the defect, whether or not today's operands happen to be
     * null. This block's own body now names nothing but this class and vanilla types.</p>
     *
     * <p>The signature names no AE2 type and returns a primitive, for the reason {@link #openItemInputBusMenu} records:
     * the block's {@code use} calls this, and the return type of an invoked method is resolved when the CALLER is
     * verified.</p>
     *
     * @return true when the menu was opened; false on the client, for a non-server player, or when the position holds
     *         no fluid input bus
     */
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

    /**
     * Builds the tile for the AE2 pattern provider block -- CHAIN A (PLAN section 452).
     *
     * THE RETURN TYPE IS BlockEntity, AND THAT IS LOAD-BEARING. An earlier version returned TileMEPatternProvider,
     * and it crashed the standalone configuration at REGISTRY time with a NoClassDefFoundError for
     * IInWorldGridNodeHost. The reason is the descriptor: create and createFluid both return BlockEntity, while that
     * version returned TileMEPatternProvider, whose hierarchy names AE2 interfaces. When a caller method body is
     * VERIFIED, the JVM resolves the return types of the methods it invokes -- so a concrete return type here reaches
     * through the firewall where an erased BlockEntity does not.
     */
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

    /**
     * Opens AE2 pattern-editing menu for a pattern provider -- THE FIREWALL FOR THE RIGHT-CLICK PATH (PLAN section 459).
     *
     * THE SIGNATURE NAMES NO AE2 TYPE, AND THAT IS THE WHOLE POINT. The block use(...) calls this, and a method body is
     * VERIFIED when its class is LINKED -- so a body naming MenuLocators or TileMEPatternProvider would drag AE2 in through
     * the caller, exactly the way section 452 return type did. Level / BlockPos / Player are all vanilla.
     *
     * @return true when the menu was opened; false on the client, for a non-server player, or for a foreign block entity
     */
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
        // THE TWO-GUI BRANCH (PLAN section 458): upstream opens the AE2 RENAMER at U68 and its OWN screen
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
