/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.astral;

import hellfirepvp.modularmachinery.common.registry.RegistryModularMagicBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;

public final class AstralRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmagic");
    public static final RegistryObject<BlockEntityType<TileStarlightProvider.Input>> INPUT = TYPES.register("blockstarlightproviderinput", () -> BlockEntityType.Builder.of(
            (p, s) -> new TileStarlightProvider.Input(AstralRegistration.INPUT.get(), p, s), RegistryModularMagicBlocks.blockStarlightProviderInput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileStarlightProvider.Output>> OUTPUT = TYPES.register("blockstarlightprovideroutput", () -> BlockEntityType.Builder.of(
            (p, s) -> new TileStarlightProvider.Output(AstralRegistration.OUTPUT.get(), p, s), RegistryModularMagicBlocks.blockStarlightProviderOutput.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileConstellationProvider>> CONSTELLATION = TYPES.register("blockconstellationprovider", () -> BlockEntityType.Builder.of(
            (p, s) -> new TileConstellationProvider(AstralRegistration.CONSTELLATION.get(), p, s), RegistryModularMagicBlocks.blockConstellationProvider.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); }
    public static boolean isLinkingTool(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof hellfirepvp.astralsorcery.common.auxiliary.link.IItemLinkingTool;
    }
    public static void unlink(Level level, BlockPos pos, Player player) {
        hellfirepvp.astralsorcery.common.auxiliary.link.LinkHandler.clearSession(player.getUUID());
        if (level.getBlockEntity(pos) instanceof TileStarlightProvider tile) {
            for (BlockPos at : tile.getLinkedPositions()) tile.tryUnlink(player, at);
            player.displayClientMessage(Component.translatable("message.modularmachinery.starlight.unlinked"), true);
        }
    }
    public static BlockEntity create(BlockPos pos, BlockState state, int kind) { return (kind == 0 ? INPUT.get() : kind == 1 ? OUTPUT.get() : CONSTELLATION.get()).create(pos, state); }
    public static void inspect(Level level, BlockPos pos, Player player) {
        var tile = level.getBlockEntity(pos);
        if (tile instanceof TileStarlightProvider.Input input) player.displayClientMessage(Component.translatable("message.modularmachinery.starlight.input", input.stored()), true);
        else if (tile instanceof TileStarlightProvider.Output output) {
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) output.cycleConstellation();
            player.displayClientMessage(Component.translatable("message.modularmachinery.starlight.output", output.getStarlightProduction(), String.valueOf(output.getAttunedConstellationKey())), true);
        } else if (tile instanceof TileConstellationProvider provider) player.displayClientMessage(Component.translatable("message.modularmachinery.constellation.active", provider.activeNames()), true);
    }
}
