/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.embers;

import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.*;

/** Link only when the external mod exists. */
public final class EmberRegistration {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "modularmachinery");
    public static final RegistryObject<BlockEntityType<TileEmberHatch>> INPUT = TYPES.register("emberinput", () -> BlockEntityType.Builder.of(
            (pos, state) -> new TileEmberHatch(EmberRegistration.INPUT.get(), pos, state), RegistryBlocks.emberInput.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) { TYPES.register(bus); }
    public static BlockEntity create(BlockPos pos, BlockState state) { return INPUT.get().create(pos, state); }
    public static void restore(Level level, BlockPos pos, ItemStack stack) {
        if (stack.hasTag() && level.getBlockEntity(pos) instanceof TileEmberHatch tile) tile.restoreAmount(stack.getTag().getDouble("storedEmber"));
    }
    public static void readout(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof TileEmberHatch tile)
            player.displayClientMessage(Component.translatable("message.modularmachinery.ember.stored",
                    String.format(java.util.Locale.ROOT, "%.2f", tile.amount()), tile.capacity()), true);
    }
}
