/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EF pattern search and actions. */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.*;

public final class MenuEFabricatorPatternSearch extends AbstractContainerMenu {
    public static final int WIDTH = 243, HEIGHT = 212;
    private final BlockPos pos;
    private final UUID session;
    private final TileEFabricatorController owner;
    private final Player viewer;
    private Query query = Query.empty();
    private Snapshot snapshot = Snapshot.empty();
    private long nextUpdate, token, lastQueryTick = Long.MIN_VALUE;
    private final Map<TileEFabricatorPatternBus, Cached> cache = new IdentityHashMap<>();
    private List<Target> shown = List.of();
    private record Target(TileEFabricatorPatternBus bus, int slot, ItemStack stack, Description description) { }
    private record Cached(long revision, long definitions, Object recipes, List<Target> entries) { }
    public MenuEFabricatorPatternSearch(int id, Inventory inv, BlockPos pos, UUID session) { this(id, inv, pos, session, null); }
    private MenuEFabricatorPatternSearch(int id, Inventory inv, BlockPos pos, UUID session, TileEFabricatorController owner) {
        super(RegistryMenus.EFABRICATOR_PATTERN_SEARCH.get(), id); this.pos = pos.immutable(); this.session = session; this.owner = owner; viewer = inv.player;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, 9 + row * 9 + col, 18 + col * 18, 130 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 18 + col * 18, 188));
    }
    public static MenuEFabricatorPatternSearch forController(int id, Inventory inv, TileEFabricatorController owner, UUID session) { return new MenuEFabricatorPatternSearch(id, inv, owner.getBlockPos(), session, owner); }
    public UUID session() { return session; }
    public Snapshot snapshot() { return snapshot; }
    public void accept(UUID session, Snapshot data) { if (owner == null && this.session.equals(session)) snapshot = data; }
    private boolean valid(Player player, UUID session) { return owner != null && player == viewer && player.containerMenu == this && this.session.equals(session) && stillValid(player); }
    private List<TileEFabricatorPatternBus> buses() {
        if (!owner.isAssembled() || !owner.isStructureChunkLoaded()) return List.of();
        return owner.parts().stream().filter(p -> p instanceof TileEFabricatorPatternBus && p.owner() == owner && p.isBoundPart())
                .map(p -> (TileEFabricatorPatternBus)p).sorted(Comparator.comparing(TileEFabricatorPatternBus::getBlockPos,
                        Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX))).toList();
    }
    private void refresh() {
        var buses = buses(); cache.keySet().retainAll(buses); var matches = new ArrayList<Target>(); boolean readable = true;
        long definitions = MachineReloadListener.getServerDefinitionRevision(); var recipes = owner.getLevel().getRecipeManager();
        for (var bus : buses) {
            if (!bus.readable()) { readable = false; cache.remove(bus); continue; }
            var entry = cache.get(bus);
            if (entry == null || entry.revision != bus.patternRevision() || entry.definitions != definitions || entry.recipes != recipes) {
                var values = new ArrayList<Target>();
                for (int i = 0; i < EFabricatorSpecs.PATTERN_SLOTS; i++) {
                    var stack = bus.patternAt(i); if (stack.isEmpty()) continue;
                    var description = net.minecraftforge.fml.ModList.get().isLoaded("ae2")
                            ? hellfirepvp.modularmachinery.integration.ae2.efabricator.EFabricatorPatterns.describe(stack, owner.getLevel()) : Description.invalid();
                    values.add(new Target(bus, i, stack, description));
                }
                entry = new Cached(bus.patternRevision(), definitions, recipes, List.copyOf(values)); cache.put(bus, entry);
            }
            for (var target : entry.entries) if (query.matches(target.description)) matches.add(target);
        }
        int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE), page = Math.min(query.page(), pages - 1);
        var next = List.copyOf(matches.subList(page * PAGE_SIZE, Math.min(matches.size(), (page + 1) * PAGE_SIZE)));
        if (token == 0 || !shown.equals(next) || snapshot.page() != page) token++;
        shown = next;
        snapshot = new Snapshot(token, page, pages, matches.size(), buses.size(), owner.isAssembled(), readable,
                shown.stream().map(t -> new Entry(t.bus.getBlockPos(), t.slot, t.description)).toList());
    }
    @Override public void broadcastChanges() {
        if (owner != null && viewer.containerMenu == this && stillValid(viewer) && owner.getLevel().getGameTime() >= nextUpdate) {
            nextUpdate = owner.getLevel().getGameTime() + 20; refresh();
            if (viewer instanceof ServerPlayer player) ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EFabricatorPatternPackets.Update(containerId, session, snapshot));
        }
        super.broadcastChanges();
    }
    public boolean query(Player player, UUID session, Query query) {
        if (!valid(player, session)) return false;
        this.query = query; nextUpdate = 0;
        long tick = player.level().getGameTime(); if (tick == lastQueryTick) return true; lastQueryTick = tick;
        broadcastChanges(); return true;
    }
    /** Pattern identities stay server-side; clients can only select a row in the current bounded page. */
    public boolean action(Player player, UUID session, int action, long expected, int row) {
        if (!valid(player, session)) return false;
        if (action == 2) { if (player instanceof ServerPlayer server) EFabricatorMenus.controller(server, owner, false); return true; }
        if (player.isSpectator()) return false;
        var buses = buses();
        if (action == 1) {
            var carried = getCarried(); if (carried.isEmpty()) return false;
            for (var bus : buses) {
                var inventory = bus.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null); if (inventory == null) continue;
                for (int i = 0; i < inventory.getSlots(); i++) if (inventory.insertItem(i, carried.copyWithCount(1), false).isEmpty()) {
                    carried.shrink(1); setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried); nextUpdate = 0; broadcastChanges(); return true;
                }
            }
            return false;
        }
        if (action != 0 || expected != snapshot.token() || row < 0 || row >= shown.size() || !getCarried().isEmpty()) return false;
        var target = shown.get(row);
        if (!buses.contains(target.bus) || !target.bus.readable() || !ItemStack.matches(target.bus.patternAt(target.slot), target.stack)) { nextUpdate = 0; broadcastChanges(); return false; }
        var inventory = target.bus.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (inventory == null) return false;
        var removed = inventory.extractItem(target.slot, 1, false); if (removed.isEmpty()) return false;
        setCarried(removed); nextUpdate = 0; broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof TileEFabricatorController current
                && !current.isRemoved() && (owner == null || owner == current) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public void clicked(int index, int button, ClickType type, Player player) { if (stillValid(player)) super.clicked(index, button, type, player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY; var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return copy;
    }
}
