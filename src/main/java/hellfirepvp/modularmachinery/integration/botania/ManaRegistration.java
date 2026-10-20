/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.botania;

import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;

/** Only loaded with Botania present. */
public final class ManaRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmagic");
    public static final RegistryObject<BlockEntityType<TileManaProvider>> INPUT = TYPES.register("blockmanaproviderinput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileManaProvider(ManaRegistration.INPUT.get(), pos, state), RegistryModularMagicBlocks.blockManaProviderInput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileManaProvider.Output>> OUTPUT = TYPES.register("blockmanaprovideroutput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileManaProvider.Output(ManaRegistration.OUTPUT.get(), pos, state), RegistryModularMagicBlocks.blockManaProviderOutput.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); }
    public static BlockEntity create(BlockPos pos, BlockState state, boolean input) {
        return input ? INPUT.get().create(pos, state) : OUTPUT.get().create(pos, state);
    }
    public static void describe(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (level.getBlockEntity(pos) instanceof TileManaProvider tile)
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.modularmachinery.mana.stored", tile.getCurrentMana(), tile.capacity()), true);
    }
}
