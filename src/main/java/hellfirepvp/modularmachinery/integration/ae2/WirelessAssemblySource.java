/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.assembly.AssemblyNetwork;
import hellfirepvp.modularmachinery.common.assembly.MachineAssembly;
import hellfirepvp.modularmachinery.common.data.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Server-thread-only assembly access through an actual terminal host. Range and power semantics
 * belong to AE2 (and compatible terminal subclasses); no direct, unlimited grid extraction is used.
 * Crafting orders are standalone player orders: output goes to ME storage, never a transient job
 * buffer. Canceling assembly cancels calculations, but accepted AE orders can finish normally.
 */
public final class WirelessAssemblySource implements AssemblyNetwork {
    private final ServerPlayer player;
    private final int slot;
    private ItemStack terminalStack;
    private final WirelessTerminalItem terminal;
    private WirelessTerminalMenuHost host;
    private final IGrid grid;
    private final IActionSource source;
    private final Map<AEKey, Order> orders = new LinkedHashMap<>();
    private boolean closed;
    private long nextPoll;

    private static final class Order {
        long amount;
        Future<ICraftingPlan> calculation;
        ICraftingPlan plan;
        boolean submitted;
        boolean failed;
    }

    private record Choice(AEKey key, long amount) { }

    private WirelessAssemblySource(ServerPlayer player, int slot, ItemStack stack,
                                   WirelessTerminalItem terminal, WirelessTerminalMenuHost host, IGrid grid) {
        this.player = player;
        this.slot = slot;
        this.terminalStack = stack;
        this.terminal = terminal;
        this.host = host;
        this.grid = grid;
        this.source = IActionSource.ofPlayer(player, () -> this.host.getActionableNode());
    }

    public static AssemblyNetwork open(ServerPlayer player) {
        // Inventory slots include the hotbar, main inventory and offhand. No dependency on a
        // particular wireless crafting terminal addon or on Curios is necessary for this path.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof WirelessTerminalItem terminal)) continue;
            IGrid grid = terminal.getLinkedGrid(stack, player.level(), null);
            if (grid == null || !terminal.hasPower(player, 1, stack)) continue;
            if (!(terminal.getMenuHost(player, slot, stack, null) instanceof WirelessTerminalMenuHost host)) continue;
            var node = host.getActionableNode();
            if (node != null && node.isActive() && node.getGrid() == grid) {
                var result = new WirelessAssemblySource(player, slot, stack, terminal, host, grid);
                result.restoreFluidRefunds();
                return result;
            }
        }
        return null;
    }

    private boolean connected() {
        if (closed || !Config.autoAssemblyWireless() || player.isRemoved()) return false;
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || current.getItem() != terminal
                || !terminal.hasPower(player, 1, current)
                || terminal.getLinkedGrid(current, player.level(), null) != grid) return false;
        // Moving an item through a menu may copy its ItemStack. Rebind the host when a compatible
        // terminal returns to this slot, while keeping every pending order on its original grid.
        if (current != terminalStack) {
            if (!(terminal.getMenuHost(player, slot, current, null) instanceof WirelessTerminalMenuHost replacement)) return false;
            terminalStack = current;
            host = replacement;
        }
        var node = host.getActionableNode(); // Rechecks wireless range, dimension and access point activity.
        return node != null && node.isActive() && node.getGrid() == grid;
    }

    private List<Choice> choices(ItemStack item, FluidStack fluid) {
        List<Choice> result = new ArrayList<>();
        if (!item.isEmpty()) {
            AEItemKey exact = AEItemKey.of(item);
            result.add(new Choice(exact, item.getCount()));
            // Flattened MMC tiers accept an untagged base-tier item, but never another tier.
            if (net.minecraft.world.level.block.Block.byItem(item.getItem()) instanceof
                    hellfirepvp.modularmachinery.common.block.BlockMachineComponent) {
                for (var entry : grid.getStorageService().getCachedInventory().findFuzzy(exact, FuzzyMode.IGNORE_ALL)) {
                    if (entry.getKey() instanceof AEItemKey key && !key.equals(exact)
                            && MachineAssembly.matchesMaterial(key.getReadOnlyStack(), item))
                        result.add(new Choice(key, item.getCount()));
                }
            }
        }
        // A bucket fallback is required so a protection-event rollback can never lose fluid even
        // if the original network disconnects or refuses reinsertion in the meantime.
        if (fluid != null && !FluidUtil.getFilledBucket(fluid).isEmpty())
            result.add(new Choice(AEFluidKey.of(fluid), fluid.getAmount()));
        return result;
    }

    @Override
    public Budget snapshot() {
        if (!connected()) return (item, fluid) -> false;
        Map<AEKey, Long> remaining = new LinkedHashMap<>();
        return (item, fluid) -> {
            for (Choice choice : choices(item, fluid)) {
                long available = remaining.computeIfAbsent(choice.key(), key ->
                        grid.getStorageService().getInventory().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source));
                // Reserve materials, not battery charge. A low battery must never be mistaken for
                // missing stock and cause duplicate crafting of items that are already in storage.
                if (available >= choice.amount()) {
                    remaining.put(choice.key(), available - choice.amount());
                    return true;
                }
            }
            return false;
        };
    }

    @Override
    public boolean canExtract(ItemStack item, FluidStack fluid) {
        if (!connected()) return false;
        for (Choice choice : choices(item, fluid)) {
            if (StorageHelper.poweredExtraction(host, grid.getStorageService().getInventory(), choice.key(),
                    choice.amount(), source, Actionable.SIMULATE) >= choice.amount()) return true;
        }
        return false;
    }

    @Override
    public Payment extract(ItemStack item, FluidStack fluid) {
        if (!connected()) return null;
        for (Choice choice : choices(item, fluid)) {
            var storage = grid.getStorageService().getInventory();
            if (StorageHelper.poweredExtraction(host, storage, choice.key(), choice.amount(), source,
                    Actionable.SIMULATE) < choice.amount()) continue;
            long taken = StorageHelper.poweredExtraction(host, storage, choice.key(), choice.amount(), source);
            player.getInventory().setChanged();
            if (taken == choice.amount()) {
                return new Payment(choice.key() instanceof AEFluidKey, () -> refund(choice.key(), taken));
            }
            if (taken > 0) refund(choice.key(), taken);
        }
        return null;
    }

    private void refund(AEKey key, long amount) {
        long inserted = grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, source);
        long remainder = amount - inserted;
        if (remainder <= 0) return;
        if (key instanceof AEFluidKey fluid && remainder != 1000) {
            // A storage addon may return less than its simulation promised. A partial bucket cannot
            // be represented by a vanilla bucket item; persist the exact amount instead of rounding
            // it up or discarding it if that storage also refuses the rollback.
            var persistent = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            var refunds = persistent.getList("MMCAssemblyFluidRefunds", net.minecraft.nbt.Tag.TAG_COMPOUND);
            refunds.add(fluid.toStack((int) remainder).writeToNBT(new net.minecraft.nbt.CompoundTag()));
            persistent.put("MMCAssemblyFluidRefunds", refunds);
            player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persistent);
            player.displayClientMessage(Component.translatable("message.assembly.ae.refund_saved"), false);
            return;
        }
        ItemStack returned = key instanceof AEItemKey item ? item.toStack((int) remainder)
                : FluidUtil.getFilledBucket(((AEFluidKey) key).toStack((int) remainder));
        if (!returned.isEmpty() && !player.getInventory().add(returned)) player.drop(returned, false);
    }

    private void restoreFluidRefunds() {
        var persistent = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        var refunds = persistent.getList("MMCAssemblyFluidRefunds", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = refunds.size() - 1; i >= 0; i--) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(refunds.getCompound(i));
            if (fluid.isEmpty()) { refunds.remove(i); continue; }
            long inserted = grid.getStorageService().getInventory().insert(AEFluidKey.of(fluid), fluid.getAmount(),
                    Actionable.MODULATE, source);
            if (inserted >= fluid.getAmount()) refunds.remove(i);
            else if (inserted > 0) {
                fluid.shrink((int) inserted);
                refunds.set(i, fluid.writeToNBT(new net.minecraft.nbt.CompoundTag()));
            }
        }
        if (persistent.contains("MMCAssemblyFluidRefunds")) {
            if (refunds.isEmpty()) persistent.remove("MMCAssemblyFluidRefunds");
            else persistent.put("MMCAssemblyFluidRefunds", refunds);
            player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persistent);
        }
    }

    @Override
    public boolean request(ItemStack item, FluidStack fluid) {
        if (!Config.autoAssemblyAutoCraft() || !connected()) return false;
        var service = grid.getCraftingService();
        Choice selected = null;
        for (Choice choice : choices(item, fluid)) {
            if (service.isCraftable(choice.key())) { selected = choice; break; }
        }
        if (selected == null && !item.isEmpty()
                && net.minecraft.world.level.block.Block.byItem(item.getItem()) instanceof
                    hellfirepvp.modularmachinery.common.block.BlockMachineComponent) {
            for (AEKey key : service.getCraftables(AEItemKey.filter())) {
                if (key instanceof AEItemKey candidate && MachineAssembly.matchesMaterial(candidate.getReadOnlyStack(), item)) {
                    selected = new Choice(key, item.getCount());
                    break;
                }
            }
        }
        if (selected == null) return false;
        Order order = orders.computeIfAbsent(selected.key(), ignored -> new Order());
        order.amount = Math.addExact(order.amount, selected.amount());
        return true;
    }

    @Override
    public void tick() {
        long now = player.serverLevel().getGameTime();
        if (now < nextPoll || closed) return;
        nextPoll = now + 20;
        if (!connected()) return;
        restoreFluidRefunds();
        // At most one calculation/submission at a time; do not create a worker job per block.
        for (var entry : orders.entrySet()) {
            Order order = entry.getValue();
            if (order.submitted || order.failed) continue;
            try {
                if (order.calculation == null) {
                    if (!Config.autoAssemblyAutoCraft()) { order.failed = true; continue; }
                    ICraftingSimulationRequester requester = () -> source;
                    order.calculation = grid.getCraftingService().beginCraftingCalculation(player.level(), requester,
                            entry.getKey(), order.amount, CalculationStrategy.REPORT_MISSING_ITEMS);
                    return;
                }
                // Never block the server thread on a crafting calculation.
                if (!order.calculation.isDone()) return;
                if (order.plan == null) order.plan = order.calculation.get();
                if (order.plan.simulation()) {
                    fail(entry.getKey(), order, "ingredients");
                    continue;
                }
                var result = grid.getCraftingService().submitJob(order.plan, null, null, false, source);
                if (result.successful()) {
                    order.submitted = true;
                    player.displayClientMessage(Component.translatable("message.assembly.ae.requested",
                            order.amount, entry.getKey().getDisplayName()), false);
                } else {
                    // A busy/undersized CPU can become available while another material finishes.
                    nextPoll = now + 100;
                }
                return;
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                Modularmachinery.LOG.warn("[MMC] Assembly crafting calculation failed for {}", entry.getKey(), ex);
                fail(entry.getKey(), order, "calculation");
            }
        }
    }

    private void fail(AEKey key, Order order, String reason) {
        order.failed = true;
        player.displayClientMessage(Component.translatable("message.assembly.ae.failed", key.getDisplayName(),
                Component.translatable("message.assembly.ae.reason." + reason)), false);
    }

    @Override
    public boolean available() { return connected(); }

    @Override
    public boolean waiting() {
        return !closed && orders.values().stream().anyMatch(order -> !order.failed);
    }

    @Override
    public void close() {
        closed = true;
        for (Order order : orders.values()) {
            if (order.calculation != null && !order.calculation.isDone()) order.calculation.cancel(true);
        }
        orders.clear();
    }
}
