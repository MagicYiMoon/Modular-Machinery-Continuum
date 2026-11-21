/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.assembly;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.machine.MachineStructureChanges;
import hellfirepvp.modularmachinery.common.machine.StructureMatcher;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Server-driven dismantling. Actual destruction goes through the player's Forge break path. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class MachineDisassembly {
    private static final int MAX_CELLS = 1024;
    private record Cell(BlockState state, BlockEntity blockEntity, List<BlockState> acceptedStates) {}
    private record Capture(ServerLevel level, BlockPos pos, List<ItemEntity> drops) {}
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();

    private MachineDisassembly() {}

    public static void run(ServerPlayer player, TileController controller) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = controller.getBlockPos();
        if (CAPTURE.get() != null || player.isSpectator() || controller.getLevel() != level
                || !player.getMainHandItem().is(RegistryItems.DISASSEMBLY_TOOL.get())
                || !level.mayInteract(player, origin)) return;
        if (controller.getOwner() != null && !controller.getOwner().equals(player.getUUID())
                && !player.hasPermissions(2)) {
            message(player, "owner");
            return;
        }
        if (!controller.isFormed() || controller.getRecordedFormedPattern() == null) {
            message(player, "unformed");
            return;
        }
        if (controller.getRecipeName() != null || controller.getActiveRecipeList().length > 0
                || MachineAssemblyManager.contains(level, origin)) {
            message(player, "busy");
            return;
        }
        var loader = MachineReloadListener.serverDefinitions().machines();
        var machine = loader.findByName(controller.getMachineName());
        if (machine == null || !controller.getBlockState().hasProperty(BlockController.FACING)) {
            message(player, "unformed");
            return;
        }
        var parts = controller.getRecordedFormedPattern().getParts();
        if (parts.size() > MAX_CELLS) {
            message(player, "large", MAX_CELLS);
            return;
        }

        var facing = controller.getBlockState().getValue(BlockController.FACING);
        var cells = new LinkedHashMap<BlockPos, Cell>();
        for (var part : parts) {
            BlockPos pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
            if (origin.equals(pos)) continue;
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) {
                message(player, "unloaded", pos.toShortString());
                return;
            }
            var state = level.getBlockState(pos);
            var accepted = StructureMatcher.acceptedStates(part, loader.getVariables(), loader.getResolver(),
                    machine.getModifierReplacements());
            if (!StructureMatcher.matchesAny(state, accepted, facing)
                    || !StructureMatcher.matchesPartNbt(level, pos, part)) {
                message(player, "changed", pos.toShortString());
                return;
            }
            if (!state.isAir()) cells.put(pos, new Cell(state, level.getBlockEntity(pos), accepted));
        }
        if (cells.isEmpty()) {
            message(player, "nothing");
            return;
        }

        List<BlockPos> order = new ArrayList<>(cells.keySet());
        order.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()); // Supports stay until upper cells are removed.
        int removed = 0;
        try (AssemblyNetwork network = AssemblyNetwork.open(player)) {
            var inventory = new PlayerMainInvWrapper(player.getInventory());
            for (BlockPos pos : order) {
                Cell cell = cells.get(pos);
                if (player.level() != level || !player.getMainHandItem().is(RegistryItems.DISASSEMBLY_TOOL.get())) break;
                if (!level.hasChunkAt(pos)) continue;
                BlockState current = level.getBlockState(pos);
                // Removing a neighbour changes fence/bar connections. Accept that if the same block
                // still satisfies the pattern, but leave replacement blocks or block entities alone.
                if (current.getBlock() != cell.state().getBlock()
                        || current != cell.state() && !StructureMatcher.matchesAny(current, cell.acceptedStates(), facing)
                        || current.getBlock() instanceof LiquidBlock || current.getDestroySpeed(level, pos) < 0
                        || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || MachineStructureChanges.watchedByOtherController(level, pos, controller)) continue;
                var tile = level.getBlockEntity(pos);
                if (tile != cell.blockEntity() || tile instanceof TileController || tile instanceof MachineComponentTile component
                        && component.getControllerPos() != null && !origin.equals(component.getControllerPos())) continue;
                if (!player.isCreative() && !current.canHarvestBlock(level, pos, player)) continue;

                var drops = new ArrayList<ItemEntity>();
                CAPTURE.set(new Capture(level, pos, drops));
                boolean changed = false;
                try {
                    changed = player.gameMode.destroyBlock(pos) && level.getBlockState(pos).getBlock() != current.getBlock();
                    if (changed) removed++;
                } finally {
                    CAPTURE.remove();
                    for (ItemEntity drop : drops) {
                        if (changed) {
                            ItemStack left = network == null ? drop.getItem() : network.insert(drop.getItem());
                            if (!left.isEmpty()) left = ItemHandlerHelper.insertItemStacked(inventory, left, false);
                            if (left.isEmpty()) continue;
                            drop.setItem(left);
                        }
                        level.addFreshEntity(drop);
                    }
                }
            }
        }
        message(player, "result", removed, cells.size() - removed);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureDrop(EntityJoinLevelEvent event) {
        Capture capture = CAPTURE.get();
        if (capture == null || event.getLevel() != capture.level || event.loadedFromDisk()
                || !(event.getEntity() instanceof ItemEntity item) || item.getItem().isEmpty()) return;
        BlockPos pos = capture.pos;
        // Match the block's own drop volume, not pre-existing entities or drops from adjacent cells.
        if (item.getX() < pos.getX() || item.getX() >= pos.getX() + 1
                || item.getY() < pos.getY() || item.getY() >= pos.getY() + 1
                || item.getZ() < pos.getZ() || item.getZ() >= pos.getZ() + 1) return;
        capture.drops.add(item);
        event.setCanceled(true);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.modularmachinery.disassembler." + key, args), false);
    }
}
