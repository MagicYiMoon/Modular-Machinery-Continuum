/* SPDX-License-Identifier: GPL-3.0-only
 * Card gestures adapted from NECO HyperNetEventHandler/HyperNetConnectCardInfo, b37fbb53.
 * New dimension/machine/owner validation and loaded-only lookup.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Pack-selected card items and the built-in default share the same authenticated server interaction. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class HyperNetCardInteractions {
    private static final String TAG = "mmcHyperNetCard";
    private HyperNetCardInteractions() { }
    private static String itemId(ItemStack stack) { return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())); }
    private static boolean configured(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(hellfirepvp.modularmachinery.common.registry.RegistryItems.HYPERNET_CONNECT_CARD.get())) return true;
        String id = itemId(stack);
        return MachineReloadListener.getLoader().getMachines().values().stream().anyMatch(machine -> {
            var definition = machine.getHyperNetDefinition();
            return definition != null && definition.connectionCard().equals(id);
        });
    }
    private static CompoundTag binding(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG, Tag.TAG_COMPOUND) ? stack.getTag().getCompound(TAG) : null;
    }
    private static boolean valid(CompoundTag tag, ItemStack stack) {
        return tag != null && tag.contains("version", Tag.TAG_INT) && tag.getInt("version") == 1
                && tag.contains("pos", Tag.TAG_LONG) && tag.hasUUID("networkOwner")
                && tag.contains("dimension", Tag.TAG_STRING) && ResourceLocation.tryParse(tag.getString("dimension")) != null
                && tag.contains("machine", Tag.TAG_STRING) && !tag.getString("machine").isBlank()
                && tag.getString("machine").length() <= 256 && tag.getString("item").equals(itemId(stack));
    }
    /** Binding syntax only, as in the source UI; connectivity still requires server validation. */
    public static boolean isBoundCard(ItemStack stack){return !stack.isEmpty()&&valid(binding(stack),stack);}
    public static boolean isCard(ItemStack stack){return !stack.isEmpty()&&(configured(stack)||valid(binding(stack),stack));}
    /** A physical terminal's retained card is read on the server, never from a client-authored task packet. */
    static boolean connectFromCard(TileHyperNetTerminal terminal,ItemStack stack){
        if(!(terminal.getLevel() instanceof ServerLevel world))return false;
        var tag=binding(stack);if(!valid(tag,stack)||!tag.getString("dimension").equals(world.dimension().location().toString()))return false;
        var target=HyperNetMonitor.loaded(world,BlockPos.of(tag.getLong("pos")));if(target==null)return false;
        var definition=HyperNetMonitor.definition(target);var data=target.getHyperNetData();
        if(definition==null||definition.getHyperNetDefinition()==null||definition.getHyperNetDefinition().role()!=HyperNetDefinition.Role.CENTER
                ||!definition.getRegistryName().equals(tag.getString("machine"))||!definition.getHyperNetDefinition().connectionCard().equals(itemId(stack))
                ||!data.hasUUID("networkOwner")||!data.getUUID("networkOwner").equals(tag.getUUID("networkOwner")))return false;
        var node=terminal.getHyperNetNode();return node!=null&&node.connect(target.getBlockPos(),tag.getUUID("networkOwner"))==HyperNetNode.ConnectResult.SUCCESS;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isSpectator()) return;
        var stack = event.getItemStack(); var tag = binding(stack);
        if (!configured(stack) && tag == null) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof TileController controller)) return;
        if(controller instanceof TileHyperNetTerminal)return; // This terminal keeps the card in its own GUI slot.
        var machine = MachineReloadListener.getLoader().findByName(controller.getMachineName());
        var definition = machine == null ? null : machine.getHyperNetDefinition();
        if (definition == null) return;
        if (definition.role() == HyperNetDefinition.Role.CENTER && !definition.connectionCard().equals(itemId(stack))) return;
        event.setCanceled(true);
        event.setCancellationResult(event.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        var node = controller.getHyperNetNode();
        HyperNetNode.ConnectResult result;
        if (definition.role() == HyperNetDefinition.Role.CENTER) {
            result = node.claimCenter(event.getEntity().getUUID());
            if (result == HyperNetNode.ConnectResult.SUCCESS) {
                var card = new CompoundTag(); card.putInt("version", 1);
                card.putString("dimension", world.dimension().location().toString());
                card.putLong("pos", event.getPos().asLong()); card.putString("machine", machine.getRegistryName());
                card.putUUID("networkOwner", controller.getHyperNetData().getUUID("networkOwner"));
                card.putString("item", itemId(stack)); stack.getOrCreateTag().put(TAG, card);
                event.getEntity().getInventory().setChanged(); event.getEntity().containerMenu.broadcastChanges();
                event.getEntity().displayClientMessage(Component.translatable("message.modularmachinery.hypernet.card_written",
                        event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()), false);
                return;
            }
        } else if (!valid(tag, stack)) result = HyperNetNode.ConnectResult.INVALID_BINDING;
        else if (!tag.getString("dimension").equals(world.dimension().location().toString())) result = HyperNetNode.ConnectResult.WRONG_DIMENSION;
        else {
            var pos = BlockPos.of(tag.getLong("pos"));
            var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            var entity = chunk == null || !world.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(pos)) ? null
                    : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
            if (!(entity instanceof TileController target)) result = HyperNetNode.ConnectResult.UNKNOWN_CENTER;
            else {
                var targetMachine = MachineReloadListener.serverDefinitions().machines().findByName(target.getMachineName());
                var targetDefinition = targetMachine == null ? null : targetMachine.getHyperNetDefinition();
                var data = target.getHyperNetData();
                if (targetDefinition == null || targetDefinition.role() != HyperNetDefinition.Role.CENTER
                        || !targetMachine.getRegistryName().equals(tag.getString("machine"))
                        || !targetDefinition.connectionCard().equals(itemId(stack))) result = HyperNetNode.ConnectResult.UNKNOWN_CENTER;
                else if (!data.hasUUID("networkOwner") || !data.getUUID("networkOwner").equals(tag.getUUID("networkOwner")))
                    result = HyperNetNode.ConnectResult.NO_PERMISSION;
                else result = node.connect(pos, event.getEntity().getUUID());
            }
        }
        event.getEntity().displayClientMessage(Component.translatable("message.modularmachinery.hypernet."
                + result.name().toLowerCase(java.util.Locale.ROOT)), false);
    }
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack(); var tag = binding(stack);
        if (!configured(stack) && tag == null) return;
        event.getToolTip().add(Component.translatable("tooltip.modularmachinery.hypernet.card").withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable("tooltip.modularmachinery.hypernet.reusable").withStyle(ChatFormatting.GREEN));
        event.getToolTip().add(Component.translatable("tooltip.modularmachinery.hypernet.online_hint").withStyle(ChatFormatting.GRAY));
        if (valid(tag, stack)) {
            var pos = BlockPos.of(tag.getLong("pos"));
            event.getToolTip().add(Component.translatable("tooltip.modularmachinery.hypernet.binding", tag.getString("dimension"),
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY));
        } else event.getToolTip().add(Component.translatable("message.modularmachinery.hypernet.invalid_binding").withStyle(ChatFormatting.GRAY));
    }
}
