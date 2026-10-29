/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds assembly instructions to the configured tool, including the vanilla stick by default. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoAssemblyTooltipEvents {
    private AutoAssemblyTooltipEvents() { }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty() || !event.getItemStack().is(Config.autoAssemblyItem())) return;
        event.getToolTip().add(Component.translatable("tooltip.modularmachinery.auto_assembly.use")
                .withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable("tooltip.modularmachinery.auto_assembly.materials")
                .withStyle(ChatFormatting.GRAY));
    }
}
