/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.assembly.MachineAssembly;
import hellfirepvp.modularmachinery.common.assembly.MachineAssemblyManager;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The source MMCE stick-right-click survival assembler, adapted to the 1.20.1 event API. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoAssemblyEvents {
    private static final Map<UUID, Long> REQUEST_COOLDOWNS = new HashMap<>();

    private AutoAssemblyEvents() { }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isShiftKeyDown()) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof BlockController)
                || !(level.getBlockEntity(pos) instanceof TileController controller)) return;

        ItemStack held = event.getItemStack();
        boolean blueprint = held.getItem() instanceof ItemBlueprint;
        boolean assembler = held.is(Config.autoAssemblyItem());
        if (!blueprint && !assembler) return;

        // Do not let BlockController open its GUI for the same click.  The server performs all world changes;
        // cancelling on the client also prevents a predicted menu from flashing open before the queue starts.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (blueprint) {
            if (!(level instanceof ServerLevel) || !(event.getEntity() instanceof ServerPlayer player)) return;
            if (controller.getBlueprintInventory().getStackInSlot(0).isEmpty()) {
                ItemStack copy = held.copyWithCount(1);
                controller.getBlueprintInventory().insertItem(0, copy, false);
                if (!player.isCreative()) held.shrink(1);
            }
            return;
        }

        // The source client sends the dynamic size captured by the blueprint projection.  Handling the
        // request on the server keeps the world mutation authoritative and avoids running the server's
        // RightClickBlock handler once and then running the packet a second time in an integrated client.
        if (level.isClientSide) {
            int size = 0;
            if (hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper.getRenderPos() != null
                    && pos.equals(hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper.getRenderPos())) {
                size = hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper.getDynamicPatternSize();
            }
            ModularmachineryNetwork.PktAutoAssemblyRequest.send(pos, size);
            return;
        }

        // The client packet above is the single authoritative trigger for assembler use.  The server-side
        // RightClickBlock event is still cancelled so the controller GUI cannot open, but it does not start a
        // second job before the packet arrives.
        if (!(level instanceof ServerLevel) || !(event.getEntity() instanceof ServerPlayer)) return;
    }

    /** Server-side entry point shared by the right-click fallback and the projection packet. */
    public static void processAssemblyRequest(ServerLevel server, ServerPlayer player, BlockPos pos,
                                              int dynamicPatternSize) {
        if (player.isShiftKeyDown() || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D) > 64.0D) return;
        if (!(server.getBlockState(pos).getBlock() instanceof BlockController)
                || !(server.getBlockEntity(pos) instanceof TileController controller)) return;
        if (!player.getMainHandItem().is(Config.autoAssemblyItem())) return;

        // MMCE's AssemblyEventHandler uses a one-second access token.  Keep the same server-side
        // guard so a double click or a duplicated client packet cannot restart a completed job.
        long now = server.getGameTime();
        Long previous = REQUEST_COOLDOWNS.get(player.getGameProfile().getId());
        if (previous != null && now - previous < 20L) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.assembly.tip.too_quickly"), false);
            return;
        }
        REQUEST_COOLDOWNS.put(player.getGameProfile().getId(), now);

        if (MachineAssemblyManager.contains(server, pos)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.assembly.tip.already_assembly"), false);
            return;
        }

        ItemStack blueprintStack = controller.getBlueprintInventory().getStackInSlot(0);
        DynamicMachine machine = ItemBlueprint.getAssociatedMachine(blueprintStack);
        // Match AssemblyEventHandler#processAutoAssembly: a present blueprint is authoritative.  If it
        // names a machine that was removed or renamed, the request must fail instead of silently using a
        // stale machineName left on the controller.  With no blueprint, the controller's bound machine is
        // the only fallback (the source's dedicated controller block supplies the same parent machine).
        if (blueprintStack.isEmpty()) {
            machine = MachineReloadListener.getLoader().findByName(controller.getMachineName());
        }
        if (machine == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.assembly.tip.no_machine"), false);
            return;
        }

        Direction facing = server.getBlockState(pos).hasProperty(BlockController.FACING)
                ? server.getBlockState(pos).getValue(BlockController.FACING) : Direction.NORTH;
        MachineAssembly assembly = MachineAssembly.start(server, pos, player, machine, facing,
                MachineReloadListener.getLoader(), dynamicPatternSize, player.isCreative());
        if (assembly != null) {
            if (player.isCreative()) {
                assembly.assembleCreative();
            } else {
                MachineAssemblyManager.add(server, pos, assembly);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) return;
        MachineAssemblyManager.tick(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MachineAssemblyManager.removePlayer(player);
            REQUEST_COOLDOWNS.remove(player.getGameProfile().getId());
        }
    }
}
