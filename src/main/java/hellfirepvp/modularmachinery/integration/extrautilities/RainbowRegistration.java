/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.extrautilities;

import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.*;

public final class RainbowRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmagic");
    public static final RegistryObject<BlockEntityType<TileRainbowProvider>> PROVIDER = TYPES.register("blockrainbowprovider", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileRainbowProvider(RainbowRegistration.PROVIDER.get(), pos, state), RegistryModularMagicBlocks.blockRainbowProvider.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); }
}
