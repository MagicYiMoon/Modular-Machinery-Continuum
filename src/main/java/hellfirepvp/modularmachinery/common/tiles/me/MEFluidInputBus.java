/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * {@code modularmachinery:blockmefluidinputbus} -- keeps each of its nine tanks filled to CAPACITY with a configured fluid.
 *
 * <h2>The five rules, read from upstream at §418.1 (`MEFluidInputBus` U85-139)</h2>
 *
 * <ol>
 *   <li><b>config empty, tank non-empty</b> -- push the tank's contents INTO the network. It gives back what it was not asked to hold.</li>
 *   <li><b>config set, tank does not match</b> -- and only if the tank is empty OR its contents fit ENTIRELY into the network,
 *       extract the configured fluid and fill the tank.</li>
 *   <li><b>config matches, tank at capacity</b> -- nothing.</li>
 *   <li><b>tank below capacity</b> -- top up from the network.</li>
 *   <li><b>tank above the configured amount</b> -- push the excess back.</li>
 * </ol>
 *
 * <h2>And the ONE factual difference from the item bus, which is where the target comes from</h2>
 *
 * <p><b>For items the target is the COUNT written in the config; for fluids it is the TANK'S CAPACITY.</b> **Upstream reads
 * {@code int capacity = tanks.getCapacity()} once per pass and compares against that.** **So a fluid input bus is not "keep N
 * buckets", it is "keep this tank full".**</p>
 *
 * <p>Rule 2's precondition is the same step §399.3 identified as destroying contents when skipped: **the old contents must fit into
 * the network BEFORE the tank is overwritten.**</p>
 *
 * <h2>What the config is here, and where its producer now lives</h2>
 *
 * <p>Upstream holds a {@code config} object with {@code getFluidInSlot(slot)}, set through its own GUI
 * ({@code ContainerMEFluidInputBus} + {@code GuiMEFluidInputBus}, upstream U21-101 and U21-78). **This class holds a per-slot
 * {@link FluidStack} array with the same read path.**</p>
 *
 * <p><b>§649(b) RECORDED THAT {@link #setConfigured} HAD ZERO CALL SITES, so the whole configuration dimension was saved, loaded
 * and consulted while nothing could ever put a value in it.</b> The producer is now the port's own screen: the nine config slots
 * of {@code MenuMEFluidInputBus} click through to {@link PktMEFluidInputBusConfig}, and {@link #setConfiguredFluid} is the one
 * place a value is normalised before it is stored. See that packet for the authorization rules.</p>
 */
public class MEFluidInputBus extends MEFluidBus implements SettingsTransfer {

    @Override
    public hellfirepvp.modularmachinery.common.machine.IOType getIOType() {
        return hellfirepvp.modularmachinery.common.machine.IOType.INPUT;
    }

    /** What each tank is asked to hold. Indexed the same as {@link #tanks}. */
    private final FluidStack[] config;

    private static final String CONFIG_TAG = "config";

    public MEFluidInputBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.config = new FluidStack[TANK_SLOT_AMOUNT];
        for (int i = 0; i < config.length; i++) {
            config[i] = FluidStack.EMPTY;
        }
    }

    /** Required by {@code MEMachineComponent}, which declares it abstract (the same error §400 hit for the item bus). */
    @Override
    public net.minecraft.world.item.ItemStack getVisualItemStack() {
        return new net.minecraft.world.item.ItemStack(getBlockState().getBlock());
    }

    /** @return the fluid this slot is asked to hold; EMPTY means "hold nothing, give back whatever is there" */
    public FluidStack getConfigured(int slot) {
        return slot >= 0 && slot < config.length ? config[slot] : FluidStack.EMPTY;
    }

    /** Sets a slot's configured fluid, and marks the slot dirty so the next pass acts on it. */
    public void setConfigured(int slot, FluidStack fluid) {
        if (slot < 0 || slot >= config.length) {
            return;
        }
        config[slot] = fluid == null ? FluidStack.EMPTY : fluid.copy();
        synchronized (this) {
            changedSlots[slot] = true;
        }
        setChanged();
    }

    /**
     * The one place a configuration written from OUTSIDE this class is normalised -- the GUI's own entry point, and the
     * method {@code PktMEFluidInputBusConfig} calls once it has authorised the write.
     *
     * <h2>Why the amount is the TANK's capacity rather than whatever the client sent</h2>
     *
     * <p>{@code doBusWork} never compares the configured amount against the tank: rule 3 fills to {@code capacity} and rule 4 tops
     * up to it, so the configured stack's role is to name the FLUID and then bound the extraction in rule 4 (which reads
     * {@code Math.min(wanted, configured.getAmount() > 0 ? configured.getAmount() : wanted)}). <b>A configured amount of 1 mB
     * would therefore cap every top-up at one millibucket</b> -- a config set from a bucket, a tank or a mod's own container
     * would silently throttle the bus by whatever amount that container happened to carry.</p>
     *
     * <p>Upstream does not have this problem because nothing ever reads the configured amount: its transport compares only the
     * fluid ({@code cfgStack.equals(invStack)}) against {@code tanks.getCapacity()}. Normalising here reproduces that observable
     * behaviour with a stack that still carries a truthful amount, and it also means the GUI shows what the bus will actually
     * keep rather than a number that has no effect.</p>
     *
     * @param slot  the tank slot to configure
     * @param fluid the fluid to keep there, or EMPTY to clear the slot (which makes rule 1 give the contents back)
     */
    public void setConfiguredFluid(int slot, FluidStack fluid) {
        FluidTank tank = tankAt(slot);
        if (tank == null) {
            return;
        }
        if (fluid == null || fluid.isEmpty()) {
            setConfigured(slot, FluidStack.EMPTY);
            return;
        }
        FluidStack normalised = fluid.copy();
        normalised.setAmount(Math.max(1, tank.getCapacity()));
        setConfigured(slot, normalised);
    }

    /** @return the capacity every tank is configured against, for the GUI's own normalisation and the self-test's assertion. */
    public int configuredAmountFor(int slot) {
        FluidTank tank = tankAt(slot);
        return tank == null ? 0 : Math.max(1, tank.getCapacity());
    }

    /** Snapshot of the fluid currently held by one of the nine tanks for the client menu. */
    public FluidStack getFluidInSlot(int slot) {
        FluidTank tank = tankAt(slot);
        return tank == null ? FluidStack.EMPTY : tank.getFluid().copy();
    }

    @Override
    public CompoundTag downloadSettings() {
        CompoundTag tag = new CompoundTag();
        CompoundTag configTag = new CompoundTag();
        for (int i = 0; i < config.length; i++) {
            if (!config[i].isEmpty()) configTag.put("slot" + i, config[i].writeToNBT(new CompoundTag()));
        }
        tag.put(CONFIG_TAG, configTag);
        return tag;
    }

    @Override
    public void uploadSettings(CompoundTag settings) {
        if (settings == null || !settings.contains(CONFIG_TAG)) return;
        CompoundTag configTag = settings.getCompound(CONFIG_TAG);
        for (int i = 0; i < config.length; i++) {
            String key = "slot" + i;
            FluidStack loaded = configTag.contains(key) ? FluidStack.loadFluidStackFromNBT(configTag.getCompound(key))
                    : FluidStack.EMPTY;
            setConfiguredFluid(i, loaded);
        }
    }

    @Override
    protected void doBusWork(int[] slots) {
        MEStorage network = getNetworkStorage();
        if (network == null) {
            for (int slot : slots) {
                markSlotFailed(slot);
            }
            return;
        }
        var source = getActionSource();

        for (int slot : slots) {
            FluidTank tank = tankAt(slot);
            if (tank == null) {
                continue;
            }
            FluidStack configured = getConfigured(slot);
            FluidStack held = tank.getFluid();
            int capacity = tank.getCapacity();

            // RULE 1: nothing configured, something held -- give it back.
            if (configured.isEmpty()) {
                if (held.isEmpty()) {
                    markSlotClean(slot);
                    continue;
                }
                FluidStack leftover = insertToNetwork(network, source, held);
                if (leftover.getAmount() < held.getAmount()) {
                    tank.drain(held.getAmount() - leftover.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                }
                if (leftover.isEmpty()) {
                    clearSlotFailure(slot);
                    markSlotClean(slot);
                } else {
                    markSlotFailed(slot);
                }
                continue;
            }

            // RULE 2: the tank does not hold what is configured.
            // AEFluidStack.equals in the source compares fluid identity/NBT, without the amount.
            if (!configured.isFluidEqual(held)) {
                if (!held.isEmpty()) {
                    FluidStack leftover = insertToNetwork(network, source, held);
                    if (!leftover.isEmpty()) {
                        // THE PRECONDITION: the old contents must go FIRST. Overwriting would destroy them -- §399.3.
                        tank.drain(held.getAmount() - leftover.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                        markSlotFailed(slot);
                        continue;
                    }
                    tank.drain(held.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                }
                fillTankFromNetwork(network, source, configured, tank, capacity, slot);
                continue;
            }

            // RULE 3.
            if (held.getAmount() == capacity) {
                markSlotClean(slot);
                continue;
            }

            // Removing capacity cards leaves existing fluid intact. Return only the excess, as upstream does.
            if (held.getAmount() > capacity) {
                FluidStack excess = held.copy();
                excess.setAmount(held.getAmount() - capacity);
                FluidStack leftover = insertToNetwork(network, source, excess);
                int returned = excess.getAmount() - leftover.getAmount();
                if (returned > 0) tank.drain(returned, IFluidHandler.FluidAction.EXECUTE);
                if (leftover.isEmpty()) {
                    clearSlotFailure(slot);
                    markSlotClean(slot);
                } else {
                    markSlotFailed(slot);
                }
                continue;
            }

            // RULE 4: top up to CAPACITY -- the fluid counterpart of the item bus's configured count.
            int wanted = capacity - held.getAmount();
            long moved = network.extract(AEFluidKey.of(configured), wanted, Actionable.MODULATE, source);
            if (moved > 0) {
                FluidStack received = configured.copy();
                received.setAmount((int) moved);
                tank.fill(received, IFluidHandler.FluidAction.EXECUTE);
                clearSlotFailure(slot);
                markSlotClean(slot);
            } else {
                markSlotFailed(slot);
            }
        }
    }

    private void fillTankFromNetwork(MEStorage network, appeng.api.networking.security.IActionSource source,
                                     FluidStack configured, FluidTank tank, int capacity, int slot) {
        long moved = network.extract(AEFluidKey.of(configured), capacity, Actionable.MODULATE, source);
        if (moved > 0) {
            FluidStack received = configured.copy();
            received.setAmount((int) moved);
            tank.fill(received, IFluidHandler.FluidAction.EXECUTE);
            clearSlotFailure(slot);
            markSlotClean(slot);
        } else {
            markSlotFailed(slot);
        }
    }

    /** @return what the network did NOT take; EMPTY when everything went */
    private FluidStack insertToNetwork(MEStorage network, appeng.api.networking.security.IActionSource source, FluidStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        long accepted = network.insert(AEFluidKey.of(stack), stack.getAmount(), Actionable.MODULATE, source);
        if (accepted <= 0) {
            return stack;
        }
        int remaining = (int) (stack.getAmount() - accepted);
        if (remaining <= 0) return FluidStack.EMPTY;
        FluidStack leftover = stack.copy();
        leftover.setAmount(remaining);
        return leftover;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag configTag = new CompoundTag();
        for (int i = 0; i < config.length; i++) {
            if (!config[i].isEmpty()) {
                // Written as NBT rather than a list index so a reordered or resized config cannot silently shift entries.
                configTag.put("slot" + i, config[i].writeToNBT(new CompoundTag()));
            }
        }
        tag.put(CONFIG_TAG, configTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(CONFIG_TAG)) {
            CompoundTag configTag = tag.getCompound(CONFIG_TAG);
            for (int i = 0; i < config.length; i++) {
                String key = "slot" + i;
                config[i] = configTag.contains(key)
                        ? FluidStack.loadFluidStackFromNBT(configTag.getCompound(key))
                        : FluidStack.EMPTY;
            }
        }
    }
}
