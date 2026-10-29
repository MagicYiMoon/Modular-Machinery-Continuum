/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.block.prop.EmberHatchSize;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public final class BlockEmberHatch extends BlockVariantComponent<EmberHatchSize> {
    public static final EnumProperty<EmberHatchSize> SIZE = EnumProperty.create("size", EmberHatchSize.class);
    public BlockEmberHatch(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(SIZE, EmberHatchSize.TINY));
    }
    private static boolean available() { return net.minecraftforge.fml.ModList.get().isLoaded("embers"); }
    @Override protected EnumProperty<EmberHatchSize> variantProperty() { return SIZE; }
    @Override protected BlockEntity createBlockEntity(EmberHatchSize size, BlockPos pos, BlockState state) {
        return available() ? hellfirepvp.modularmachinery.integration.embers.EmberRegistration.create(pos, state) : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && available()) hellfirepvp.modularmachinery.integration.embers.EmberRegistration.restore(level, pos, stack);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (available()) hellfirepvp.modularmachinery.integration.embers.EmberRegistration.readout(level, pos, player);
            else player.displayClientMessage(Component.translatable("message.modularmachinery.ember.requires_mod"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        var state = stack.getTagElement("BlockStateTag");
        EmberHatchSize size = state == null ? EmberHatchSize.TINY : SIZE.getValue(state.getString("size")).orElse(EmberHatchSize.TINY);
        double amount = stack.getTag() == null ? 0 : stack.getTag().getDouble("storedEmber");
        amount = Double.isFinite(amount) ? Math.max(0, Math.min(size.capacity(), amount)) : 0;
        lines.add(Component.translatable("message.modularmachinery.ember.stored", String.format(java.util.Locale.ROOT, "%.2f", amount), size.capacity()));
        lines.add(Component.translatable(size.capacity() >= 3200 ? "tooltip.modularmachinery.ember.volatile" : "tooltip.modularmachinery.ember.normal"));
        lines.add(Component.translatable("tooltip.modularmachinery.ember"));
    }
}
