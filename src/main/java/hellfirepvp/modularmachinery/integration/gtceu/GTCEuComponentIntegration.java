/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import hellfirepvp.modularmachinery.common.event.RegisterMachineComponentProxiesEvent;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxy;
import hellfirepvp.modularmachinery.common.machine.component.ProxiedMachineComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** GTCEu Modern 7.5: normal buses, fluid hatches and EU energy hatches, using recipe-side IO. */
public final class GTCEuComponentIntegration implements MachineComponentProxy {
    public static final String KEY = "modularmachinery:gtceu_normal_parts";
    // Weak values too: each view retains its tank, which retains the GT machine/world.
    // A live returned handler retains the view, so repeated queries preserve identity during reservations.
    private final Map<NotifiableFluidTank, WeakReference<IFluidHandler>> fluidViews = new WeakHashMap<>();
    private final Map<BlockEntity, WeakReference<GTCEuEnergyView>> energyViews = new WeakHashMap<>();

    private GTCEuComponentIntegration() { }
    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterMachineComponentProxiesEvent event) ->
                event.getRegistry().register(KEY, new GTCEuComponentIntegration()));
    }

    private static @Nullable MetaMachine machine(BlockEntity be) {
        return be instanceof IMachineBlockEntity holder ? holder.getMetaMachine() : null;
    }

    @Override public boolean isSupported(BlockEntity be) {
        var machine = machine(be);
        // Special subclasses (ME, infinite/creative, steam, reservoir...) have distinct contracts.
        // Only the concrete normal classes audited for this adapter are accepted.
        return machine != null && (machine.getClass() == ItemBusPartMachine.class
                || machine.getClass() == FluidHatchPartMachine.class
                || machine.getClass() == EnergyHatchPartMachine.class);
    }

    @Override public @Nullable ProxiedMachineComponent proxyComponent(BlockEntity be) {
        var machine = machine(be);
        if (machine != null && machine.getClass() == ItemBusPartMachine.class) {
            var inventory = ((ItemBusPartMachine) machine).getInventory();
            var direction = direction(inventory.handlerIO);
            // GT's own recipe handler uses storage directly; it preserves filters/change listeners.
            // The separate circuit inventory is never exposed as consumable items.
            return direction == null ? null : ProxiedMachineComponent.of(direction, inventory.storage, null, null);
        }
        if (machine != null && machine.getClass() == FluidHatchPartMachine.class) {
            var tank = ((FluidHatchPartMachine) machine).tank;
            var direction = direction(tank.handlerIO);
            return direction == null ? null : ProxiedMachineComponent.of(direction, null, fluidView(tank), null);
        }
        if (machine != null && machine.getClass() == EnergyHatchPartMachine.class) {
            var hatch = (EnergyHatchPartMachine) machine;
            var direction = direction(hatch.energyContainer.getHandlerIO());
            return direction == null ? null : ProxiedMachineComponent.of(direction, null, null, energyView(be, hatch));
        }
        return null;
    }

    private static @Nullable IOType direction(IO io) {
        return io == IO.IN ? IOType.INPUT : io == IO.OUT ? IOType.OUTPUT : null;
    }

    private synchronized IFluidHandler fluidView(NotifiableFluidTank tank) {
        var reference = fluidViews.get(tank);
        var view = reference == null ? null : reference.get();
        if (view == null) {
            view = new InternalFluidView(tank);
            fluidViews.put(tank, new WeakReference<>(view));
        }
        return view;
    }

    private synchronized GTCEuEnergyView energyView(BlockEntity owner, EnergyHatchPartMachine hatch) {
        var reference = energyViews.get(owner);
        var view = reference == null ? null : reference.get();
        if (view == null) {
            view = new GTCEuEnergyView(owner, hatch);
            energyViews.put(owner, new WeakReference<>(view));
        }
        return view;
    }

    private record InternalFluidView(NotifiableFluidTank tank) implements IFluidHandler {
        @Override public int getTanks() { return tank.getTanks(); }
        @Override public FluidStack getFluidInTank(int index) { return tank.getFluidInTank(index); }
        @Override public int getTankCapacity(int index) { return tank.getTankCapacity(index); }
        @Override public boolean isFluidValid(int index, FluidStack fluid) { return tank.isFluidValid(index, fluid); }
        @Override public int fill(FluidStack fluid, FluidAction action) { return tank.fillInternal(fluid, action); }
        @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return tank.drainInternal(fluid, action); }
        @Override public FluidStack drain(int amount, FluidAction action) {
            return amount <= 0 ? FluidStack.EMPTY : tank.drainInternal(amount, action);
        }
    }
}
