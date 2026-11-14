/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO MachineCoolants/EFabricatorController. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.nbt.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Private host ledger. Consumed coolant earns units only after all resulting hot fluid is accepted. */
public final class EFabricatorCooling {
    private int cache;
    private Reservation pending;
    private static final class Reservation {
        FluidStack input, output;
        int held, heat, unitsPending, units;
    }
    public int units() { return cache; }
    public void consume(int amount) {
        if (amount < 0 || amount > cache) throw new IllegalArgumentException("Invalid cooling consumption");
        cache -= amount;
    }
    public void refill(List<IFluidHandler> inputs, List<IFluidHandler> outputs, Runnable changed) {
        flush(outputs, changed);
        if (cache >= EFabricatorSpecs.COOLANT_CACHE || pending != null && pending.heat > 0) return;
        if (pending != null) {
            // Keep the terms of a partially collected conversion across config reloads.
            for (var input : inputs) {
                collect(input, pending.input, pending.input.getAmount() - pending.held, changed);
                if (pending == null || pending.heat > 0) break;
            }
            flush(outputs, changed);
            if (pending != null || cache >= EFabricatorSpecs.COOLANT_CACHE) return;
        }
        for (var input : inputs) for (int slot = 0; slot < input.getTanks(); slot++) {
            var contents = input.getFluidInTank(slot).copy();
            if (contents.isEmpty()) continue;
            var rule = EFabricatorCoolants.find(contents.getFluid());
            if (rule == null) continue;
            int batches = (EFabricatorSpecs.COOLANT_CACHE - cache + rule.units() - 1) / rule.units();
            batches = Math.min(batches, Integer.MAX_VALUE / rule.inputAmount());
            if (rule.output() != null) {
                batches = Math.min(batches, Integer.MAX_VALUE / rule.outputAmount());
                // Plan all hot output first, including ratios such as 1 mB -> 320 mB.
                long room = 0;
                for (var output : outputs) if (output != input)
                    room += Math.max(0, output.fill(new FluidStack(rule.output(), Integer.MAX_VALUE), SIMULATE));
                batches = (int)Math.min(batches, room / rule.outputAmount());
            }
            if (batches <= 0) continue;
            var request = new FluidStack(contents, batches * rule.inputAmount());
            var simulated = input.drain(request, SIMULATE);
            if (simulated.isEmpty() || !simulated.isFluidEqual(request)) continue;
            batches = Math.min(batches, simulated.getAmount() / rule.inputAmount());
            if (batches <= 0) continue;
            pending = new Reservation(); pending.input = new FluidStack(contents, rule.inputAmount());
            pending.output = rule.output() == null ? FluidStack.EMPTY : new FluidStack(rule.output(), rule.outputAmount());
            pending.units = rule.units();
            collect(input, pending.input, batches * rule.inputAmount(), changed);
            flush(outputs, changed);
            if (pending != null || cache >= EFabricatorSpecs.COOLANT_CACHE) return;
        }
    }
    private void collect(IFluidHandler source, FluidStack type, int amount, Runnable changed) {
        var drained = source.drain(new FluidStack(type, amount), EXECUTE);
        if (drained.isEmpty()) { if (pending.held == 0) pending = null; return; }
        if (!drained.isFluidEqual(type) || drained.getAmount() > amount)
            throw new IllegalStateException("Fluid handler violated its simulated drain contract");
        pending.held += drained.getAmount();
        int batches = pending.held / pending.input.getAmount();
        pending.held %= pending.input.getAmount();
        pending.unitsPending = batches * pending.units;
        pending.heat = batches * pending.output.getAmount();
        if (pending.heat == 0) {
            cache += pending.unitsPending; pending.unitsPending = 0;
            if (pending.held == 0) pending = null;
        }
        changed.run();
    }
    private void flush(List<IFluidHandler> outputs, Runnable changed) {
        if (pending == null || pending.heat == 0) return;
        for (var output : outputs) {
            int offered = Math.min(pending.heat, Math.max(0, output.fill(new FluidStack(pending.output, pending.heat), SIMULATE)));
            if (offered <= 0) continue;
            int accepted = output.fill(new FluidStack(pending.output, offered), EXECUTE);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Fluid handler violated its fill contract");
            pending.heat -= accepted;
            if (accepted > 0) changed.run();
            if (pending.heat == 0) {
                cache += pending.unitsPending; pending.unitsPending = 0;
                if (pending.held == 0) pending = null;
                changed.run(); return;
            }
        }
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putInt("Cache", cache);
        if (pending != null) {
            var p = new CompoundTag(); p.put("Input", pending.input.writeToNBT(new CompoundTag()));
            if (!pending.output.isEmpty()) p.put("Output", pending.output.writeToNBT(new CompoundTag())); p.putInt("Units", pending.units);
            p.putInt("Held", pending.held); p.putInt("Heat", pending.heat); p.putInt("Credit", pending.unitsPending); tag.put("Pending", p);
        }
        return tag;
    }
    public boolean isEmpty() { return cache == 0 && pending == null; }
    public static EFabricatorCooling load(CompoundTag tag) {
        var result = new EFabricatorCooling();
        if (tag.isEmpty()) return result;
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Cache", Tag.TAG_INT)
                || tag.getInt("Cache") < 0 || tag.getInt("Cache") >= 2 * EFabricatorSpecs.COOLANT_CACHE)
            throw new IllegalArgumentException("Invalid EF cooling cache");
        result.cache = tag.getInt("Cache");
        if (tag.contains("Pending")) {
            if (!tag.contains("Pending", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid EF cooling reservation type");
            var p = tag.getCompound("Pending"); var r = new Reservation();
            r.input = FluidStack.loadFluidStackFromNBT(p.getCompound("Input"));
            r.output = FluidStack.loadFluidStackFromNBT(p.getCompound("Output"));
            r.units = p.getInt("Units"); r.held = p.getInt("Held"); r.heat = p.getInt("Heat"); r.unitsPending = p.getInt("Credit");
            if (!p.contains("Input", Tag.TAG_COMPOUND) || p.contains("Output") && (!p.contains("Output", Tag.TAG_COMPOUND) || r.output.isEmpty())
                    || !p.contains("Units", Tag.TAG_INT) || !p.contains("Held", Tag.TAG_INT)
                    || !p.contains("Heat", Tag.TAG_INT) || !p.contains("Credit", Tag.TAG_INT)
                    || r.input.isEmpty() || r.units <= 0 || r.units > EFabricatorSpecs.COOLANT_CACHE
                    || r.held < 0 || r.held >= r.input.getAmount() || r.heat < 0 || r.unitsPending < 0
                    || r.unitsPending >= 2 * EFabricatorSpecs.COOLANT_CACHE || (r.heat == 0) != (r.unitsPending == 0)
                    || r.heat > 0 && r.output.isEmpty() || r.heat == 0 && r.held == 0
                    || result.cache + r.unitsPending >= 2 * EFabricatorSpecs.COOLANT_CACHE)
                throw new IllegalArgumentException("Invalid EF cooling reservation");
            result.pending = r;
        }
        return result;
    }
}
