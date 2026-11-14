/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ECalculatorEventHandler. */
package hellfirepvp.modularmachinery.common.ecalculator;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.world.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class ECalculatorInteraction {
    private ECalculatorInteraction() { }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) return;
        var world = event.getLevel(); var state = world.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof BlockECalculatorPart block) || block.specification().kind() != ECalculatorSpecs.Kind.DRIVE
                || !event.getItemStack().isEmpty() && !(event.getItemStack().getItem() instanceof ItemECalculatorMemory)) return;
        var result = world.isClientSide ? InteractionResult.SUCCESS : block.use(state, world, event.getPos(), event.getEntity(), event.getHand(), event.getHitVec());
        if (result.consumesAction()) { event.setCanceled(true); event.setCancellationResult(result); }
    }
}
