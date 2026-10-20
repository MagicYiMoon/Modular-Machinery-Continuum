/* SPDX-License-Identifier: GPL-3.0-only */
/* Modular Machinery CE — Forge 1.20.1 port. */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Optional ownership protection copied from MMCE's security handler.
 * The owner is persisted on the controller and copied to its dropped item by the loot table.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ControllerSecurityEvents {
    private ControllerSecurityEvents() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!Config.enableSecuritySystem()
                || !(event.getState().getBlock() instanceof BlockController)
                || event.getPlayer().isCreative()) {
            return;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof TileController controller) {
            UUID owner = controller.getOwner();
            if (owner != null && !owner.equals(event.getPlayer().getGameProfile().getId())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!Config.enableSecuritySystem() || !(event.getEntity() instanceof Player player)
                || player.isCreative() || !(event.getPlacedBlock().getBlock() instanceof BlockController)) {
            return;
        }
        ItemStack stack = findControllerStack(player);
        if (!stack.isEmpty() && stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag.hasUUID("owner") && !tag.getUUID("owner").equals(player.getGameProfile().getId())) {
                event.setCanceled(true);
            }
        }
    }

    private static ItemStack findControllerStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof net.minecraft.world.item.BlockItem
                && ((net.minecraft.world.item.BlockItem) main.getItem()).getBlock() instanceof BlockController) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof net.minecraft.world.item.BlockItem
                && ((net.minecraft.world.item.BlockItem) off.getItem()).getBlock() instanceof BlockController) {
            return off;
        }
        return ItemStack.EMPTY;
    }
}

