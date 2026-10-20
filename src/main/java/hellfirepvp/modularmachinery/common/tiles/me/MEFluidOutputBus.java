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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * {@code modularmachinery:blockmefluidoutputbus} -- hands the machine's fluid products to the ME network.
 *
 * <h2>Upstream's loop, read at §422.2 (`MEFluidOutputBus` U51-85)</h2>
 *
 * <pre>
 * for (final int slot : needUpdateSlots) {
 *     changedSlots[slot] = false;
 *     IAEFluidStack fluid = tanks.getFluidInSlot(slot);
 *     if (fluid == null) continue;                       // <- null check BEFORE any extraction
 *     IAEFluidStack left = Platform.poweredInsert(inv, fluid.copy(), source);
 *     if (left != null) { if (fluid.getStackSize() != left.getStackSize()) successAtLeastOnce = true; }
 *     else successAtLeastOnce = true;
 * }
 * </pre>
 *
 * <p><b>The null check's POSITION is the one thing this class must not move.</b> The item-side equivalent
 * ({@code MEItemOutputBus}, §401.2) extracts first and then builds the AE key, and upstream's {@code continue} on a null key
 * <b>destroys the stack</b> because it has already left the inventory. **Checking before touching the tank leaves no such window, and
 * §422.2 recorded that this is a specific ordering mistake rather than a property of the base class.**</p>
 */
public class MEFluidOutputBus extends MEFluidBus {

    @Override
    public hellfirepvp.modularmachinery.common.machine.IOType getIOType() {
        return hellfirepvp.modularmachinery.common.machine.IOType.OUTPUT;
    }

    public MEFluidOutputBus(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Required by {@code MEMachineComponent}, which declares it abstract (the same error §400 hit for the item bus). */
    @Override
    public net.minecraft.world.item.ItemStack getVisualItemStack() {
        return new net.minecraft.world.item.ItemStack(getBlockState().getBlock());
    }

    /** Snapshot used by the read-only output-bus menu. */
    public FluidStack getFluidInSlot(int slot) {
        FluidTank tank = tankAt(slot);
        return tank == null ? FluidStack.EMPTY : tank.getFluid().copy();
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
            FluidStack held = tank.getFluid();
            // THE NULL CHECK, BEFORE ANYTHING IS TAKEN OUT. See the class javadoc.
            if (held.isEmpty()) {
                markSlotClean(slot);
                continue;
            }

            long accepted = network.insert(AEFluidKey.of(held), held.getAmount(), Actionable.MODULATE, source);
            if (accepted <= 0) {
                // Nothing moved: an ascending no-progress counter, capped -- upstream's shape, and the cap is what makes the
                // SAME/FASTER decision meaningful rather than a busy loop.
                markSlotFailed(slot);
                continue;
            }

            int remaining = (int) (held.getAmount() - accepted);
            // FluidTank.drain takes an int, and `accepted` is a long because MEStorage works in longs.
            tank.drain((int) accepted, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            clearSlotFailure(slot);
            if (remaining <= 0) {
                markSlotClean(slot);
            } else {
                // Partial: something moved, so it is not a failure -- but the tank still holds fluid and must be retried.
                markSlotFailed(slot);
            }
        }
    }
}
