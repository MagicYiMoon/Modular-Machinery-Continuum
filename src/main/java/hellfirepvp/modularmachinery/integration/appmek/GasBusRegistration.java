/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.*;
import net.minecraftforge.common.extensions.IForgeMenuType;

/** Loaded only with AE2, Mekanism and Applied Mekanistics present. */
public final class GasBusRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Modularmachinery.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Modularmachinery.MODID);
    public static final RegistryObject<BlockEntityType<MEGasBus>> INPUT = TYPES.register("blockmegasinputbus", () -> BlockEntityType.Builder.of(
            (pos, state) -> new MEGasBus(GasBusRegistration.INPUT, pos, state, true), RegistryMEBlocks.all().get("blockmegasinputbus").get()).build(null));
    public static final RegistryObject<BlockEntityType<MEGasBus>> OUTPUT = TYPES.register("blockmegasoutputbus", () -> BlockEntityType.Builder.of(
            (pos, state) -> new MEGasBus(GasBusRegistration.OUTPUT, pos, state, false), RegistryMEBlocks.all().get("blockmegasoutputbus").get()).build(null));
    public static final RegistryObject<MenuType<MenuMEGasBus>> MENU = MENUS.register("me_gas_bus", () -> IForgeMenuType.create(
            (id, inventory, data) -> new MenuMEGasBus(id, inventory, data.readBlockPos(), data.readBoolean())));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); MENUS.register(bus); }
    public static BlockEntity create(BlockPos pos, BlockState state, boolean input) {
        return input ? INPUT.get().create(pos, state) : OUTPUT.get().create(pos, state);
    }
    public static void open(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                            net.minecraft.world.InteractionHand hand) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server) || !(level.getBlockEntity(pos) instanceof MEGasBus bus)) return;
        var stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof appeng.api.implementations.items.IMemoryCard card) {
            String name = bus.getBlockState().getBlock().getDescriptionId();
            if (player.isShiftKeyDown()) card.setMemoryCardContents(stack, name, bus.downloadSettings());
            else if (name.equals(card.getSettingsName(stack))) {
                bus.uploadSettings(card.getData(stack)); card.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.SETTINGS_LOADED);
            } else card.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.INVALID_MACHINE);
            return;
        }
        net.minecraftforge.network.NetworkHooks.openScreen(server, new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, ignored) -> MenuMEGasBus.forBus(id, inventory, bus), bus.getBlockState().getBlock().getName()),
                data -> { data.writeBlockPos(pos); data.writeBoolean(bus.isInput()); });
    }
}
