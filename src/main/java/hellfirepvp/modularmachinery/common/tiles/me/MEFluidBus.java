/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import hellfirepvp.modularmachinery.common.tiles.base.MEMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Base for the Applied Energistics 2 fluid buses: nine tanks the machine can use, wired to the ME network.
 *
 * <h2>Upstream's structure, followed exactly (rule A0)</h2>
 *
 * <p>Upstream has {@code MEItemBus} (119 lines) and {@code MEFluidBus} (175 lines) as SIBLINGS under {@code MEMachineComponent}, each
 * carrying its OWN copy of the slot bookkeeping -- {@code changedSlots}, {@code lastFullCheckTick}, {@code inTick} and the 100-tick
 * re-check. **There is no common base class.**</p>
 *
 * <p><b>An earlier version of this port introduced an {@code AbstractMEBus} to hold that bookkeeping once, and this class has been
 * changed BACK to upstream's shape.** **The reason is rule A0: no evidence was ever produced that upstream's duplication cannot work
 * here, and the argument for extracting it was compactness -- which is a preference.** **§426.3 also recorded that the extraction never
 * finished: {@code MEItemBus} kept its own copy AND the new base held a second one, so the deviation cost duplication without removing
 * any.**</p>
 *
 * <h2>The one factual difference from the item bus, from §418.1's read of the five rules</h2>
 *
 * <table>
 *   <caption>Where the target amount comes from</caption>
 *   <tr><td>item bus</td><td>the COUNT written in {@code configInventory}</td></tr>
 *   <tr><td>fluid bus</td><td>the TANK's CAPACITY</td></tr>
 * </table>
 *
 * <p>Everything else maps one for one, including rule 2's "the old contents must fit into the network first" precondition, which
 * §399.3 identified as the step that silently destroys contents when skipped.</p>
 *
 * <h2>Nine tanks of its own, and THIS deviation IS evidence-backed</h2>
 *
 * <p>Upstream holds an {@code AEFluidInventoryUpgradeable}. **`javap` against AE2 15.4.11 shows that class is MISSING (§415.3), so
 * following upstream is impossible here** -- which is the condition rule A0 requires before deviating. The closest shipped class,
 * the one that DID survive, is Forge's {@link FluidTank}, and nine of them at 8000 mB reproduce upstream's constants.</p>
 *
 * <p><b>The port's own {@code TileFluidTank} was considered and rejected on evidence, not preference (§422.1):** it extends
 * {@code TileMachineComponent} rather than {@code MEMachineComponent}, AND it holds ONE private tank where this class needs nine.</p>
 *
 * <h2>And the storage API is the SAME one the item bus uses</h2>
 *
 * <p>AE2 15.x unified its storage channels -- {@code IFluidStorageChannel} is gone because there is one {@code MEStorage} keyed on
 * {@code AEKey}. **So this class reuses §398's exact calls**, passing {@code AEFluidKey.of(fluidStack)} where the item bus passes
 * {@code AEItemKey.of(stack)}.</p>
 */
public abstract class MEFluidBus extends MEMachineComponent implements IGridTickable, IUpgradeableObject,
        hellfirepvp.modularmachinery.common.tiles.base.BreakInventoryProvider {

    /** Upstream's constant: nine tanks. */
    public static final int TANK_SLOT_AMOUNT = 9;

    /** Upstream's constant, in millibuckets. */
    public static final int TANK_DEFAULT_CAPACITY = 8000;

    /** How often every slot is re-examined regardless of dirtiness. Upstream's constant, copied from its MEItemBus. */
    protected static final long FULL_CHECK_INTERVAL = 100L;

    /** How many ticks a slot waits after a failed transfer before it is offered again. */
    protected static final int FAILURE_BACKOFF_TICKS = 20;

    /** One tank per slot, all the same capacity. Upstream's {@code AEFluidInventoryUpgradeable} became an array (§415.3). */
    protected final FluidTank[] tanks;

    /** Five AE2 upgrade slots, matching the upstream MEFluidBus StackUpgradeInventory. */
    protected final IUpgradeInventory upgrades;
    /** Machine-facing view of the nine tanks, matching the upstream FLUID_HANDLER capability. */
    private final net.minecraftforge.fluids.capability.IFluidHandler fluidHandler = new net.minecraftforge.fluids.capability.IFluidHandler() {
        @Override public int getTanks() { return tanks.length; }
        @Override public FluidStack getFluidInTank(int tank) { return fluidAt(tank).copy(); }
        @Override public int getTankCapacity(int tank) { FluidTank t = tankAt(tank); return t == null ? 0 : t.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tankAt(tank) != null; }
        @Override public int fill(FluidStack stack, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction action) {
            if (stack == null || stack.isEmpty()) return 0;
            int remaining = stack.getAmount();
            for (FluidTank tank : tanks) {
                if (remaining <= 0) break;
                FluidStack request = stack.copy();
                request.setAmount(remaining);
                int moved = tank.fill(request, action);
                remaining -= moved;
            }
            return stack.getAmount() - remaining;
        }
        @Override public FluidStack drain(FluidStack stack, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction action) {
            if (stack == null || stack.isEmpty()) return FluidStack.EMPTY;
            int remaining = stack.getAmount();
            FluidStack result = FluidStack.EMPTY;
            for (FluidTank tank : tanks) {
                if (remaining <= 0) break;
                FluidStack request = stack.copy();
                request.setAmount(remaining);
                FluidStack moved = tank.drain(request, action);
                if (!moved.isEmpty()) {
                    if (result.isEmpty()) result = moved.copy();
                    else if (result.isFluidEqual(moved)) result.grow(moved.getAmount());
                    else break;
                    remaining -= moved.getAmount();
                }
            }
            return result;
        }
        @Override public FluidStack drain(int amount, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction action) {
            if (amount <= 0) return FluidStack.EMPTY;
            int remaining = amount;
            FluidStack result = FluidStack.EMPTY;
            for (FluidTank tank : tanks) {
                if (remaining <= 0) break;
                // The untyped Forge drain operation must choose one fluid identity for the
                // whole request.  Draining first and comparing afterwards loses a different
                // fluid on EXECUTE, because FluidTank has already mutated itself by then.
                FluidStack request = result.isEmpty() ? tank.getFluid() : result;
                if (request.isEmpty()) continue;
                request = request.copy();
                request.setAmount(remaining);
                FluidStack moved = tank.drain(request, action);
                if (!moved.isEmpty()) {
                    if (result.isEmpty()) result = moved.copy();
                    else result.grow(moved.getAmount());
                    remaining -= moved.getAmount();
                }
            }
            return result;
        }
    };

    protected final boolean[] changedSlots;
    protected final int[] failureCounter;
    private long lastFullCheckTick = 0L;
    protected boolean inTick = false;

    protected MEFluidBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        this(type, pos, state, TANK_SLOT_AMOUNT, TANK_DEFAULT_CAPACITY);
    }

    protected MEFluidBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state,
                         int tankCount, int capacity) {
        super(type, pos, state);
        this.tanks = new FluidTank[tankCount];
        this.changedSlots = new boolean[tankCount];
        this.failureCounter = new int[tankCount];
        for (int i = 0; i < tankCount; i++) {
            this.tanks[i] = createTank(i, capacity);
        }
        // AE2 15.x exposes the upgrade inventory through this public factory. The machine item is the
        // block item represented by this tile, so the associations registered by MEUpgradeIntegration
        // apply to both fluid input and output buses exactly as they did in 1.12.2.
        this.upgrades = UpgradeInventories.forMachine(getVisualItemStack().getItem(), 5, this::onUpgradesChanged);
        updateTankCapacity();
        // Node configuration, so it is safe before create() runs in onLoad -- and it is what makes the network call
        // tickingRequest at all.
        getMainNode().addService(IGridTickable.class, this);
    }

    private FluidTank createTank(int slot, int capacity) {
        return new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
                synchronized (MEFluidBus.this) {
                    // A change made BY a transfer pass is not an external change, and re-arming the slot the pass is working on
                    // would make every pass re-offer it forever. Same rule as the item bus, same reason.
                    if (!inTick) {
                        changedSlots[slot] = true;
                    }
                }
                setChanged();
            }
        };
    }

    private void onUpgradesChanged() {
        updateTankCapacity();
        setChanged();
        synchronized (this) {
            for (int i = 0; i < changedSlots.length; i++) {
                changedSlots[i] = true;
            }
        }
    }

    private void updateTankCapacity() {
        int installed = upgrades == null ? 0 : upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
        int exponent = Math.min(installed * 2, 30);
        long capacity = (long) TANK_DEFAULT_CAPACITY * (1L << exponent);
        int target = (int) Math.min(Integer.MAX_VALUE, capacity);
        for (FluidTank tank : tanks) {
            tank.setCapacity(target);
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    /** Number of installed copies of an AE2 upgrade card, for menus and compatibility code. */
    public int getInstalledUpgrades(net.minecraft.world.level.ItemLike item) {
        return upgrades.getInstalledUpgrades(item);
    }

    @Override
    public net.minecraftforge.items.IItemHandler getBreakInventory() {
        return upgrades.toItemHandler();
    }

    /**
     * Exposes only the fluid capability to machine recipes.  The AE2 upgrade inventory
     * is available through {@link #getBreakInventory()} for block drops, but is kept out
     * of the recipe-facing item capability so capacity cards cannot be consumed as inputs.
     */
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> capability,
            @Nullable net.minecraft.core.Direction side) {
        if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> fluidHandler).cast();
        }
        return super.getCapability(capability, side);
    }

    /** @return the tank in a slot, or null when the slot is out of range */
    @Nullable
    protected FluidTank tankAt(int slot) {
        return slot >= 0 && slot < tanks.length ? tanks[slot] : null;
    }

    /** @return the fluid in a slot, or EMPTY when the slot is out of range or the tank is empty */
    protected FluidStack fluidAt(int slot) {
        FluidTank tank = tankAt(slot);
        return tank == null ? FluidStack.EMPTY : tank.getFluid();
    }

    /** @return whether any tank holds anything */
    public boolean hasFluid() {
        for (FluidTank tank : tanks) {
            if (!tank.getFluid().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The slots worth offering on this pass: every slot once per {@link #FULL_CHECK_INTERVAL} ticks, otherwise the dirty ones
     * whose failure backoff has elapsed.
     */
    protected synchronized int[] getNeedUpdateSlots() {
        long now = level == null ? 0L : level.getGameTime();

        if (lastFullCheckTick + FULL_CHECK_INTERVAL < now) {
            lastFullCheckTick = now;
            int[] all = new int[tanks.length];
            for (int i = 0; i < all.length; i++) {
                all[i] = i;
            }
            // Ageing the failure counters happens here, once per full pass: a counter is consulted only when its slot is
            // offered, so decrementing more often would just cost time.
            for (int i = 0; i < failureCounter.length; i++) {
                if (failureCounter[i] > 0) {
                    failureCounter[i]--;
                }
            }
            return all;
        }

        java.util.List<Integer> needed = new java.util.ArrayList<>(changedSlots.length);
        for (int i = 0; i < changedSlots.length; i++) {
            if (changedSlots[i] && failureCounter[i] <= 0) {
                needed.add(i);
            }
        }
        int[] result = new int[needed.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = needed.get(i);
        }
        return result;
    }

    /** Marks a slot's transfer as failed, so it is retried after the backoff instead of being forgotten. */
    protected synchronized void markSlotFailed(int slot) {
        if (slot >= 0 && slot < failureCounter.length) {
            failureCounter[slot] = FAILURE_BACKOFF_TICKS;
            // Left dirty ON PURPOSE: the slot still has something to move, it just needs to wait.
            changedSlots[slot] = true;
        }
    }

    /** Marks a slot's contents as settled, so it is not offered again until something changes it. */
    protected synchronized void markSlotClean(int slot) {
        if (slot >= 0 && slot < changedSlots.length) {
            changedSlots[slot] = false;
        }
    }

    /** Clears a slot's failure backoff after a SUCCESS, so an immediate retry is possible if it fails again. */
    protected synchronized void clearSlotFailure(int slot) {
        if (slot >= 0 && slot < failureCounter.length) {
            failureCounter[slot] = 0;
        }
    }

    /** @return whether any slot is marked dirty */
    public boolean hasChangedSlots() {
        for (boolean changed : changedSlots) {
            if (changed) {
                return true;
            }
        }
        return false;
    }

    /** @return the network's storage, or null when this bus is not connected or the grid is not active */
    @Nullable
    protected MEStorage getNetworkStorage() {
        if (!getMainNode().isActive()) {
            return null;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        IStorageService storage = grid.getService(IStorageService.class);
        return storage == null ? null : storage.getInventory();
    }

    /** @return this bus as an action source, for AE2's bookkeeping about who moved what */
    protected IActionSource getActionSource() {
        return IActionSource.ofMachine(this);
    }

    private final TickingRequest tickingRequest = new TickingRequest(5, 120, false, true);

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // Never sleeping: the tanks' contents are the machine's business, not the grid's. Alertable, so a subclass that marks a
        // slot dirty can wake the node instead of waiting out the interval.
        return tickingRequest;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        int[] slots = getNeedUpdateSlots();
        if (slots.length == 0) {
            // IDLE rather than SLEEP: a sleeping device is not woken by an external change, and this bus must be.
            return TickRateModulation.IDLE;
        }
        inTick = true;
        try {
            doBusWork(slots);
        } finally {
            inTick = false;
        }
        return hasChangedSlots() ? TickRateModulation.FASTER : TickRateModulation.SAME;
    }

    /** Moves fluid between this bus's tanks and the grid, for the given slots. */
    protected abstract void doBusWork(int[] slots);

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        upgrades.writeToNBT(tag, "upgrades");
        net.minecraft.nbt.ListTag tanksTag = new net.minecraft.nbt.ListTag();
        for (FluidTank tank : tanks) {
            net.minecraft.nbt.CompoundTag tankTag = new net.minecraft.nbt.CompoundTag();
            tank.writeToNBT(tankTag);
            tanksTag.add(tankTag);
        }
        tag.put("tanks", tanksTag);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        upgrades.readFromNBT(tag, "upgrades");
        updateTankCapacity();
        if (tag.contains("tanks", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag tanksTag = tag.getList("tanks", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < tanks.length && i < tanksTag.size(); i++) {
                tanks[i].readFromNBT(tanksTag.getCompound(i));
            }
        }
    }
}
