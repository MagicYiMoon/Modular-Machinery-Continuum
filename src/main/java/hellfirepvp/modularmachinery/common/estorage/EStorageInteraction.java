/* SPDX-License-Identifier: GPL-3.0-only
 * NECO EStorageEventHandler's main-hand sneak gesture, before vanilla sneak-use bypass.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class EStorageInteraction {
    private EStorageInteraction() { }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) return;
        var world = event.getLevel();
        var state = world.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof BlockEStorageDrive drive)
                || !event.getItemStack().isEmpty() && !(event.getItemStack().getItem() instanceof ItemEStorageCell)) return;
        InteractionResult result = world.isClientSide ? InteractionResult.SUCCESS : drive.use(state, world,
                event.getPos(), event.getEntity(), event.getHand(), event.getHitVec());
        if (result.consumesAction()) { event.setCanceled(true); event.setCancellationResult(result); }
    }
}
