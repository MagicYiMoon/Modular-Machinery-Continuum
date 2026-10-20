/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;

/** Loaded only with Blood Magic 3 present. */
public final class BloodMagicRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmagic");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "modularmachinery");
    public static final RegistryObject<BlockEntityType<TileLifeEssenceProvider>> LIFE_INPUT = TYPES.register("blocklifeessenceproviderinput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileLifeEssenceProvider(BloodMagicRegistration.LIFE_INPUT.get(), pos, state, true), RegistryModularMagicBlocks.blockLifeEssenceProviderInput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileLifeEssenceProvider>> LIFE_OUTPUT = TYPES.register("blocklifeessenceprovideroutput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileLifeEssenceProvider(BloodMagicRegistration.LIFE_OUTPUT.get(), pos, state, false), RegistryModularMagicBlocks.blockLifeEssenceProviderOutput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileWillProvider>> WILL_INPUT = TYPES.register("blockwillproviderinput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileWillProvider(BloodMagicRegistration.WILL_INPUT.get(), pos, state, true), RegistryModularMagicBlocks.blockWillProviderInput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileWillProvider>> WILL_OUTPUT = TYPES.register("blockwillprovideroutput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileWillProvider(BloodMagicRegistration.WILL_OUTPUT.get(), pos, state, false), RegistryModularMagicBlocks.blockWillProviderOutput.get()).build(null));
    public static final RegistryObject<MenuType<MenuLifeEssence>> MENU = MENUS.register("life_essence", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
            (id, inventory, data) -> new MenuLifeEssence(id, inventory, data.readBlockPos())));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); MENUS.register(bus); }
    public static BlockEntity create(BlockPos pos, BlockState state, boolean life, boolean input) {
        return life ? (input ? LIFE_INPUT.get() : LIFE_OUTPUT.get()).create(pos, state)
                : (input ? WILL_INPUT.get() : WILL_OUTPUT.get()).create(pos, state);
    }
    public static void open(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server)) return;
        if (level.getBlockEntity(pos) instanceof TileLifeEssenceProvider tile) {
            net.minecraftforge.network.NetworkHooks.openScreen(server, new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, ignored) -> MenuLifeEssence.forTile(id, inventory, tile), tile.getBlockState().getBlock().getName()), pos);
        } else if (level.getBlockEntity(pos) instanceof TileWillProvider tile) {
            for (var type : EnumDemonWillType.values()) player.sendSystemMessage(Component.translatable("message.modularmachinery.will.stored",
                    Component.translatable("will.modularmachinery." + (type == EnumDemonWillType.DEFAULT ? "raw" : type.toString())), tile.amount(type)));
        }
    }
}
