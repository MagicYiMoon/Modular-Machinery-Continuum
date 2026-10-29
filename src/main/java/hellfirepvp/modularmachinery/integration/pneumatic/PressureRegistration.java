/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;

/** Loaded only with PneumaticCraft present. */
public final class PressureRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmachinery");
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "modularmachinery");
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<MenuPressureHatch>> MENU = MENUS.register("pressure_hatch", () ->
            net.minecraftforge.common.extensions.IForgeMenuType.create((id, inventory, data) -> new MenuPressureHatch(id, inventory, data.readBlockPos())));
    public static final RegistryObject<BlockEntityType<TilePressureHatch>> INPUT = TYPES.register("pressureinput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TilePressureHatch(PressureRegistration.INPUT.get(), pos, state, true), RegistryBlocks.pressureInput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TilePressureHatch>> OUTPUT = TYPES.register("pressureoutput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TilePressureHatch(PressureRegistration.OUTPUT.get(), pos, state, false), RegistryBlocks.pressureOutput.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        TYPES.register(bus); MENUS.register(bus);
        bus.addListener(PressureRegistration::setup);
    }
    private static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var registry = me.desht.pneumaticcraft.api.PneumaticRegistry.getInstance().getUpgradeRegistry();
            var builder = new me.desht.pneumaticcraft.api.upgrade.IUpgradeRegistry.Builder()
                    .with(registry.getUpgradeById(new net.minecraft.resources.ResourceLocation("pneumaticcraft", "volume")), 64)
                    .with(registry.getUpgradeById(new net.minecraft.resources.ResourceLocation("pneumaticcraft", "security")), 1);
            registry.addApplicableUpgrades(INPUT.get(), builder); registry.addApplicableUpgrades(OUTPUT.get(), builder);
        });
    }
    public static void open(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server && level.getBlockEntity(pos) instanceof TilePressureHatch tile)
            net.minecraftforge.network.NetworkHooks.openScreen(server, new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, ignored) -> MenuPressureHatch.forTile(id, inventory, tile), tile.getBlockState().getBlock().getName()), pos);
    }
    public static void dropUpgrades(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePressureHatch tile)
            for (int i = 0; i < tile.upgrades().getSlots(); i++) {
                var stack = tile.upgrades().getStackInSlot(i);
                tile.upgrades().setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
    }
    public static BlockEntity create(BlockPos pos, BlockState state, boolean input) { return (input ? INPUT : OUTPUT).get().create(pos, state); }
}
