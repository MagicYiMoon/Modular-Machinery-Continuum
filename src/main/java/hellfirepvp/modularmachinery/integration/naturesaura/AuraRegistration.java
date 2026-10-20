/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.naturesaura;

import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;

/** Loaded only when Nature's Aura is installed. */
public final class AuraRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmagic");
    public static final RegistryObject<BlockEntityType<TileAuraProvider>> INPUT = TYPES.register("blockauraproviderinput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileAuraProvider(AuraRegistration.INPUT.get(), pos, state, true), RegistryModularMagicBlocks.blockAuraProviderInput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileAuraProvider>> OUTPUT = TYPES.register("blockauraprovideroutput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileAuraProvider(AuraRegistration.OUTPUT.get(), pos, state, false), RegistryModularMagicBlocks.blockAuraProviderOutput.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); }
    public static BlockEntity create(BlockPos pos, BlockState state, boolean input) { return (input ? INPUT.get() : OUTPUT.get()).create(pos, state); }
    public static void inspect(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (level.getBlockEntity(pos) instanceof TileAuraProvider tile)
            player.displayClientMessage(Component.translatable("message.modularmachinery.aura.stored", tile.auraType().getName().toString(), tile.amount()), true);
    }
}
