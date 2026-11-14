/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ContainerEFabricatorController. */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static hellfirepvp.modularmachinery.common.efabricator.EFabricatorMonitor.*;

public final class MenuEFabricatorController extends AbstractContainerMenu {
    public static final int WIDTH = 243, HEIGHT = 202;
    private final BlockPos pos;
    private final UUID session;
    private final TileEFabricatorController owner;
    private final Player viewer;
    private Snapshot snapshot = Snapshot.empty();
    private final EFabricatorThroughput throughput = new EFabricatorThroughput();
    private int page;
    private long nextUpdate;
    public MenuEFabricatorController(int id, Inventory inventory, BlockPos pos, UUID session) { this(id, inventory, pos, session, null); }
    private MenuEFabricatorController(int id, Inventory inventory, BlockPos pos, UUID session, TileEFabricatorController owner) {
        super(RegistryMenus.EFABRICATOR_CONTROLLER.get(), id);
        this.pos = pos.immutable(); this.session = session; this.owner = owner; viewer = inventory.player;
        // Only the player's real inventory has slots. The core/product panel is display-only.
        for (int y = 0; y < 3; y++) for (int x = 0; x < 9; x++) addSlot(new Slot(inventory, x + y * 9 + 9, 18 + x * 18, 119 + y * 18));
        for (int x = 0; x < 9; x++) addSlot(new Slot(inventory, x, 18 + x * 18, 177));
    }
    public static MenuEFabricatorController forController(int id, Inventory inv, TileEFabricatorController owner, UUID session) {
        return new MenuEFabricatorController(id, inv, owner.getBlockPos(), session, owner);
    }
    public UUID session() { return session; }
    public Snapshot snapshot() { return snapshot; }
    public double craftsPerTick() { return throughput.perTick(); }
    public void accept(UUID id, Snapshot data) {
        if (owner == null && session.equals(id)) {
            snapshot = data;
            throughput.sample(viewer.level().getGameTime(), data.crafted(), data.readable());
        }
    }
    private long[] fluidLevels(List<IFluidHandler> handlers) {
        long amount = 0, capacity = 0;
        var visited = Collections.newSetFromMap(new IdentityHashMap<IFluidHandler, Boolean>());
        for (var h : handlers) if (visited.add(h)) for (int i = 0; i < h.getTanks(); i++) {
            amount = add(amount, Math.max(0, h.getFluidInTank(i).getAmount())); capacity = add(capacity, Math.max(0, h.getTankCapacity(i)));
        }
        return new long[]{amount, Math.max(amount, capacity)};
    }
    private void refresh() {
        var parts = new HashMap<BlockPos, TileEFabricatorPart>(); boolean online = false, readable = owner.productionReadable();
        int buses = 0, processors = 0;
        for (var part : owner.parts()) if (!part.isRemoved() && part.owner() == owner && part.isBoundPart()) {
            parts.put(part.getBlockPos(), part);
            if (part instanceof TileEFabricatorMEChannel c) online |= c.isNetworkActive();
            if (part.specification().kind() == EFabricatorSpecs.Kind.PATTERN_BUS) buses++;
            if (part.specification().kind() == EFabricatorSpecs.Kind.PARALLEL) processors++;
        }
        var workers = new ArrayList<Worker>(); long queued = 0, capacity = 0; double energy = 0;
        for (var part : parts.values()) if (part instanceof TileEFabricatorWorker w) {
            readable &= w.readable(); queued = add(queued, Math.max(0, w.queuedCrafts())); capacity = add(capacity, w.queueDepth()); energy += Math.max(0, w.energyStored());
            workers.add(new Worker(w.getBlockPos(), w.readable(), Math.max(0, w.queuedCrafts()), w.queueDepth(), Math.max(0, w.energyStored()),
                    w.currentProductIcon()));
        }
        workers.sort(Comparator.comparing(Worker::pos, Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)));
        int pages = Math.max(1, (workers.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
        long[] input = {0, 0}, output = {0, 0};
        var loader = MachineReloadListener.getLoader(); var machine = loader.findByName(EFabricatorRegistration.machineName(owner.tier()));
        if (owner.isAssembled() && machine != null && owner.isStructureChunkLoaded()) {
            var context = new MachineContext((ServerLevel)owner.getLevel(), pos, owner.getBlockState().getValue(BlockEFabricatorController.FACING), machine, loader.getVariables(), owner.getFormedPattern(machine));
            context.overrideComponentPositions(owner.getBoundPositions());
            input = fluidLevels(context.internalFluidInputs()); output = fluidLevels(context.internalFluidOutputs());
        }
        long pending = 0; for (var entry : owner.pendingOutputs()) pending = add(pending, entry.amount());
        snapshot = new Snapshot((net.minecraftforge.fml.ModList.get().isLoaded("ae2") ? 1 : 0) | (owner.isAssembled() ? 2 : 0)
                | (owner.isOperational() ? 4 : 0) | (online ? 8 : 0) | (readable ? 0 : 16), owner.tier().level,
                owner.isOverclocked(), owner.isActiveCooling(), owner.parallelism(), owner.workDelay(), Math.max(0, owner.ticksUntilWork()),
                Math.max(0, owner.coolingUnits()), Math.max(0, owner.totalCrafted()), queued, capacity, energy, (long)workers.size() * EFabricatorSpecs.ENERGY_CACHE,
                pending, input[0], input[1], output[0], output[1], workers.size(), buses, processors, page, pages,
                workers.subList(page * PAGE_SIZE, Math.min(workers.size(), (page + 1) * PAGE_SIZE)));
    }
    @Override public void broadcastChanges() {
        if (owner != null && viewer.containerMenu == this && stillValid(viewer) && owner.getLevel().getGameTime() >= nextUpdate) {
            nextUpdate = owner.getLevel().getGameTime() + 10; refresh();
            if (viewer instanceof ServerPlayer p) ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EFabricatorMonitorPackets.Update(containerId, session, snapshot));
        }
        super.broadcastChanges();
    }
    /** Explicit desired states, not toggles: a repeated packet cannot flip an already-applied mode. */
    public boolean action(Player player, UUID session, int action, boolean enabled) {
        if (owner == null || player != viewer || player.containerMenu != this || !this.session.equals(session) || !stillValid(player) || action < 0 || action > 4) return false;
        if (action == 4) { if (player instanceof ServerPlayer server) EFabricatorMenus.controller(server, owner, true); return true; }
        if (action == 0) owner.setModes(enabled, owner.isActiveCooling());
        else if (action == 1) owner.setModes(owner.isOverclocked(), enabled);
        else page += action == 2 ? -1 : 1;
        nextUpdate = 0; broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof TileEFabricatorController c
                && (owner == null || c == owner) && !c.isRemoved() && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
