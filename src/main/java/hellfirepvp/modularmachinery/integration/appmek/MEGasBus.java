/* SPDX-License-Identifier: GPL-3.0-only
 * Port of MMCE's MEGasBus/InputBus/OutputBus using AE2 15 and Applied Mekanistics keys.
 */
package hellfirepvp.modularmachinery.integration.appmek;

import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.*;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.*;
import appeng.core.definitions.AEItems;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import hellfirepvp.modularmachinery.common.tiles.me.SettingsTransfer;
import hellfirepvp.modularmachinery.integration.mekanism.MekanismGasBridge;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.gas.*;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import java.util.*;
import java.util.function.Supplier;

/** Nine independent buffers. Network returns never delete gas which the network cannot accept. */
public class MEGasBus extends MEMachineComponent implements IGridTickable, IUpgradeableObject, GasTankProvider,
        BreakInventoryProvider, SettingsTransfer {
    public static final int SLOTS = 9, DEFAULT_CAPACITY = 8000;
    private final boolean input;
    private final Cell[] cells = new Cell[SLOTS];
    private final GasStack[] configured = new GasStack[SLOTS];
    private final IUpgradeInventory upgrades;
    private int capacity = DEFAULT_CAPACITY;
    private final IGasHandler handler;
    private LazyOptional<IGasHandler> gasCapability;

    public MEGasBus(Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state, boolean input) {
        super(type, pos, state); this.input = input;
        for (int slot = 0; slot < SLOTS; slot++) { cells[slot] = new Cell(); configured[slot] = GasStack.EMPTY; }
        upgrades = UpgradeInventories.forMachine(getBlockState().getBlock().asItem(), 5, () -> { updateCapacity(); setChanged(); });
        handler = new IGasHandler() {
            public int getTanks() { return SLOTS; }
            public GasStack getChemicalInTank(int tank) { return valid(tank) ? cells[tank].stored.copy() : GasStack.EMPTY; }
            public void setChemicalInTank(int tank, GasStack gas) { if (valid(tank)) cells[tank].setGasTag(gas.write(new CompoundTag())); }
            public long getTankCapacity(int tank) { return valid(tank) ? capacity : 0; }
            public boolean isValid(int tank, GasStack stack) { return valid(tank) && MekanismGasBridge.handler(cells[tank]).isValid(0, stack); }
            public GasStack insertChemical(int tank, GasStack stack, Action action) {
                return valid(tank) ? MekanismGasBridge.handler(cells[tank]).insertChemical(0, stack, action) : stack.copy();
            }
            public GasStack extractChemical(int tank, long amount, Action action) {
                return valid(tank) ? MekanismGasBridge.handler(cells[tank]).extractChemical(0, amount, action) : GasStack.EMPTY;
            }
        };
        gasCapability = LazyOptional.of(() -> handler);
        getMainNode().addService(IGridTickable.class, this);
    }
    public boolean isInput() { return input; }
    @Override public IOType getIOType() { return input ? IOType.INPUT : IOType.OUTPUT; }
    @Override public ItemStack getVisualItemStack() { return new ItemStack(getBlockState().getBlock()); }
    @Override public List<? extends GasTankAccess> gasTanks() { return List.of(cells); }
    @Override public IUpgradeInventory getUpgrades() { return upgrades; }
    @Override public IItemHandler getBreakInventory() { return upgrades.toItemHandler(); }
    public int capacity() { return capacity; }
    public GasStack stored(int tank) { return valid(tank) ? cells[tank].stored.copy() : GasStack.EMPTY; }
    public GasStack configured(int tank) { return valid(tank) ? configured[tank].copy() : GasStack.EMPTY; }
    public void configure(int tank, GasStack gas) {
        if (!input || !valid(tank)) return;
        configured[tank] = gas == null || gas.isEmpty() ? GasStack.EMPTY : new GasStack(gas, 1);
        setChanged();
    }
    private static boolean valid(int tank) { return tank >= 0 && tank < SLOTS; }
    private void updateCapacity() {
        int cards = upgrades == null ? 0 : upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
        capacity = (int) Math.min(Integer.MAX_VALUE, (long) DEFAULT_CAPACITY << (Math.min(cards, 5) * 2));
        // Reducing capacity keeps paid contents; the next network pass returns the excess.
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == Capabilities.GAS_HANDLER ? gasCapability.cast() : super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); gasCapability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); gasCapability = LazyOptional.of(() -> handler); }
    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(5, 100, false, true); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return transfer() ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }
    protected MEStorage networkStorage() {
        if (!getMainNode().isActive()) return null;
        IGrid grid = getMainNode().getGrid();
        return grid == null ? null : grid.getService(IStorageService.class).getInventory();
    }
    /** Also used by the regression suite, with a real AE2 storage cell behind this API. */
    public boolean transfer() {
        MEStorage network = networkStorage();
        if (network == null) return false;
        boolean moved = false;
        var source = IActionSource.ofMachine(this);
        for (int slot = 0; slot < SLOTS; slot++) {
            GasStack held = cells[slot].stored;
            GasStack wanted = configured[slot];
            long target = input && !wanted.isEmpty() ? capacity : 0;
            if (!held.isEmpty() && (!input || wanted.isEmpty() || !held.isTypeEqual(wanted) || held.getAmount() > target)) {
                long giveBack = input && !wanted.isEmpty() && held.isTypeEqual(wanted) ? held.getAmount() - target : held.getAmount();
                long returned = bounded(network.insert(MekanismKey.of(held), giveBack, Actionable.MODULATE, source), giveBack);
                if (returned > 0) { cells[slot].set(new GasStack(held, held.getAmount() - returned)); moved = true; }
                held = cells[slot].stored;
                // A different gas must leave completely before a new gas can enter this cell.
                if (!held.isEmpty() && (!held.isTypeEqual(wanted) || !input || wanted.isEmpty())) continue;
            }
            if (input && !wanted.isEmpty() && (held.isEmpty() || held.isTypeEqual(wanted))) {
                long room = Math.max(0, target - held.getAmount());
                if (room > 0) {
                    long taken = bounded(network.extract(MekanismKey.of(wanted), room, Actionable.MODULATE, source), room);
                    if (taken > 0) { cells[slot].set(new GasStack(wanted, held.getAmount() + taken)); moved = true; }
                }
            }
        }
        return moved;
    }
    private static long bounded(long moved, long requested) { return Math.min(requested, Math.max(0, moved)); }
    @Override public CompoundTag downloadSettings() {
        var tag = new CompoundTag(); var list = new ListTag();
        for (GasStack gas : configured) list.add(gas.write(new CompoundTag()));
        tag.put("gasConfig", list); return tag;
    }
    @Override public void uploadSettings(CompoundTag tag) {
        if (!input || tag == null || !tag.contains("gasConfig", Tag.TAG_LIST)) return;
        var list = tag.getList("gasConfig", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < SLOTS; slot++) configure(slot, slot < list.size() ? GasStack.readFromNBT(list.getCompound(slot)) : GasStack.EMPTY);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); upgrades.writeToNBT(tag, "upgrades"); tag.merge(downloadSettings());
        var list = new ListTag(); for (Cell cell : cells) list.add(cell.getGasTag()); tag.put("gasTanks", list);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); upgrades.readFromNBT(tag, "upgrades"); updateCapacity(); uploadSettings(tag);
        var list = tag.getList("gasTanks", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < SLOTS; slot++) cells[slot].stored = slot < list.size() ? GasStack.readFromNBT(list.getCompound(slot)) : GasStack.EMPTY;
    }
    private final class Cell implements GasTankAccess {
        private GasStack stored = GasStack.EMPTY;
        private void set(GasStack stack) { stored = stack.isEmpty() ? GasStack.EMPTY : stack.copy(); setChanged(); }
        public boolean hasGas() { return !stored.isEmpty(); }
        public long getGasAmount() { return stored.getAmount(); }
        public long getGasCapacity() { return capacity; }
        public CompoundTag getGasTag() { return stored.write(new CompoundTag()); }
        public void setGasTag(CompoundTag tag) { set(GasStack.readFromNBT(tag)); }
        public void clearGas() { set(GasStack.EMPTY); }
        public boolean canAcceptGas() { return input; }
        public boolean canExtractGas() { return !input; }
    }
}
