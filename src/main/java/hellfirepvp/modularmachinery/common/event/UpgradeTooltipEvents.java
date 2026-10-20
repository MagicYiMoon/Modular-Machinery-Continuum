/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade;
import hellfirepvp.modularmachinery.common.upgrade.MachineUpgradeItem;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Restores the source capability path's tooltip behaviour for upgrades attached to arbitrary items.
 *
 * <p>The source event handler ran for every {@code ItemStack} carrying an upgrade capability, not only for a
 * dedicated upgrade item.  The port's four built-in {@link MachineUpgradeItem}s already render their own lines,
 * so they are excluded here to avoid duplicate descriptions.  Script-supported items (diamonds, machine parts,
 * and so on) use this Forge event path and therefore expose the same dynamic description callbacks.</p>
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpgradeTooltipEvents {
    private UpgradeTooltipEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() instanceof MachineUpgradeItem) return;
        for (MachineUpgrade upgrade : RegistryMachineUpgrade.upgradesFor(stack)) {
            for (String line : upgrade.getDescriptions()) {
                event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
