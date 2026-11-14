/* SPDX-License-Identifier: GPL-3.0-only
 * NECO ECalculatorData/ContainerECalculatorController, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.ecalculator.*;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static hellfirepvp.modularmachinery.common.ecalculator.ECalculatorMonitor.*;

/** No inventory slots: clicking a task can never transfer or create its illustrated output. */
public final class MenuECalculatorController extends AbstractContainerMenu {
    public static final int WIDTH = 255, HEIGHT = 221;
    private final BlockPos pos;
    private final UUID session;
    private final TileECalculatorController owner;
    private final Player viewer;
    private Snapshot snapshot = Snapshot.empty();
    private ECalculatorStatistics.Page statistics = ECalculatorStatistics.Page.UNKNOWN;
    private int corePage, taskPage;
    private long nextUpdate;
    public MenuECalculatorController(int id, Inventory inventory, BlockPos pos, UUID session) {
        this(id, inventory, pos, session, null);
    }
    private MenuECalculatorController(int id, Inventory inventory, BlockPos pos, UUID session, TileECalculatorController owner) {
        super(RegistryMenus.ECALCULATOR_CONTROLLER.get(), id);
        this.pos = pos.immutable(); this.session = session; this.owner = owner; viewer = inventory.player;
    }
    public static MenuECalculatorController forController(int id, Inventory inventory, TileECalculatorController owner, UUID session) {
        return new MenuECalculatorController(id, inventory, owner.getBlockPos(), session, owner);
    }
    public UUID session() { return session; }
    public Snapshot snapshot() { return snapshot; }
    public ECalculatorStatistics.Page statistics() { return statistics; }
    public void accept(UUID session, Snapshot data) {
        if (owner == null && this.session.equals(session)) { snapshot = data; statistics = ECalculatorStatistics.Page.UNKNOWN; }
    }
    public void acceptStatistics(UUID session, ECalculatorStatistics.Page data) {
        if (owner == null && this.session.equals(session) && snapshot.taskPage() == data.taskPage()
                && snapshot.tasks().stream().map(Task::id).toList().equals(data.tasks().stream().map(ECalculatorStatistics.Task::id).toList())) statistics = data;
    }
    private void refresh() {
        if (owner == null) return;
        boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2"), online = false, incomplete = false;
        var cores = new ArrayList<Core>(); var tasks = new ArrayList<Task>();
        var measurements = new HashMap<UUID, ECalculatorStatistics.Measurement>();
        long capacity = 0, used = 0, hyperMemory = 0, normal = 0, hyper = 0, maxNormal = 0, maxHyper = 0, parallel = 0;
        // Read only the already-bound physical parts. No AE public roster filtering and no block/neighbor scans.
        for (var part : owner.parts()) {
            if (part.isRemoved() || part.owner() != owner) continue;
            var spec = part.specification();
            if (part instanceof TileECalculatorMEChannel channel) online |= channel.isNetworkActive();
            if (part instanceof TileECalculatorDrive drive && drive.isTierSupported()
                    && drive.memorySnapshot().getItem() instanceof ItemECalculatorMemory cell)
                capacity = add(capacity, cell.specification().bytes());
            parallel = add(parallel, spec.parallelism());
            if (spec.normalSlots() + spec.hyperSlots() == 0) continue;
            var access = part.cpuAccess();
            boolean readable = access != null && access.monitorReadable(); incomplete |= !readable;
            var owned = access == null ? List.<Task>of() : access.monitorTasks();
            if (access != null) measurements.putAll(access.monitorStatistics());
            int n = 0, h = 0;
            for (var task : owned) {
                if (task.hyper()) { h++; hyperMemory = add(hyperMemory, task.bytes()); } else n++;
                used = add(used, task.bytes()); tasks.add(task);
            }
            normal += n; hyper += h; maxNormal += spec.normalSlots(); maxHyper += spec.hyperSlots();
            cores.add(new Core(part.getBlockPos().immutable(), spec.tier(), n, h, spec.normalSlots(), spec.hyperSlots(),
                    readable));
        }
        cores.sort(Comparator.comparing(Core::pos, Comparator.<BlockPos>comparingInt(p -> p.getY())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)));
        // Stable order within a menu; a changing memory size must not move a cancel button to another task.
        tasks.sort(Comparator.comparing(Task::id));
        var segments = tasks.stream().map(Task::bytes).sorted(Comparator.reverseOrder()).limit(MAX_SEGMENTS).toList();
        int corePages = Math.max(1, (cores.size() + CORE_PAGE_SIZE - 1) / CORE_PAGE_SIZE);
        int taskPages = Math.max(1, (tasks.size() + TASK_PAGE_SIZE - 1) / TASK_PAGE_SIZE);
        corePage = Math.max(0, Math.min(corePage, corePages - 1)); taskPage = Math.max(0, Math.min(taskPage, taskPages - 1));
        snapshot = new Snapshot((ae ? 1 : 0) | (owner.isAssembled() ? 2 : 0) | (owner.isOperational() ? 4 : 0) | (online ? 8 : 0) | (incomplete ? 16 : 0),
                owner.tier(), capacity, used, hyperMemory, parallel, normal, hyper, maxNormal, maxHyper,
                corePage, corePages, taskPage, taskPages, tasks.size(), segments,
                cores.subList(corePage * CORE_PAGE_SIZE, Math.min(cores.size(), (corePage + 1) * CORE_PAGE_SIZE)),
                tasks.subList(taskPage * TASK_PAGE_SIZE, Math.min(tasks.size(), (taskPage + 1) * TASK_PAGE_SIZE)));
        long nanos = 0; double operations = 0; boolean measured = ae && owner.isAssembled() && !incomplete;
        for (var task : tasks) {
            var sample = measurements.getOrDefault(task.id(), ECalculatorStatistics.Measurement.UNKNOWN);
            measured &= sample.ready(); nanos = add(nanos, sample.nanosPerTick()); operations += sample.operationsPerTick();
        }
        statistics = new ECalculatorStatistics.Page(taskPage, measured, nanos, operations, snapshot.tasks().stream()
                .map(t -> new ECalculatorStatistics.Task(t.id(), measurements.getOrDefault(t.id(), ECalculatorStatistics.Measurement.UNKNOWN))).toList());
    }
    @Override public void broadcastChanges() {
        if (owner != null && owner.getLevel().getGameTime() >= nextUpdate && viewer.containerMenu == this && stillValid(viewer)) {
            nextUpdate = owner.getLevel().getGameTime() + 20; refresh();
            if (viewer instanceof ServerPlayer player) {
                ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ECalculatorMonitorPackets.Update(containerId, session, snapshot));
                ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ECalculatorMonitorPackets.Statistics(containerId, session, statistics));
            }
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (owner == null || player != viewer || player.containerMenu != this || !stillValid(player) || id < 0 || id > 3) return false;
        if (id < 2) corePage += id == 0 ? -1 : 1; else taskPage += id == 2 ? -1 : 1;
        nextUpdate = 0; broadcastChanges(); return true;
    }
    public boolean cancel(Player player, UUID session, UUID task) {
        if (owner == null || player != viewer || player.containerMenu != this || !this.session.equals(session) || !stillValid(player)
                || snapshot.tasks().stream().noneMatch(t -> t.id().equals(task) && t.running())) return false;
        for (var part : owner.parts()) {
            if (part.isRemoved() || part.owner() != owner) continue;
            var access = part.cpuAccess();
            if (access != null && access.cancelTask(task)) { nextUpdate = 0; broadcastChanges(); return true; }
        }
        nextUpdate = 0; broadcastChanges(); return false;
    }
    @Override public boolean stillValid(Player player) {
        return player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof TileECalculatorController controller
                && (owner == null || owner == controller) && !controller.isRemoved()
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
