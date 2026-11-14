/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.Config;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import java.util.*;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Deterministic conversion/serialization boundaries; these fluids are test configuration, not defaults. */
public final class EFabricatorCoolingChecks {
    private EFabricatorCoolingChecks() { }
    public static void run() {
        // Rapid rule mutations must not race Forge's autosave/file watcher on Windows.
        try {
            var field = net.minecraftforge.common.ForgeConfigSpec.class.getDeclaredField("childConfig"); field.setAccessible(true);
            var original = (com.electronwill.nightconfig.core.CommentedConfig)field.get(Config.SPEC);
            var isolated = com.electronwill.nightconfig.core.CommentedConfig.inMemory(); Config.SPEC.correct(isolated); Config.SPEC.setConfig(isolated);
            try { checkConversions(); } finally { Config.SPEC.setConfig(original); }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void checkConversions() {
        var previous = List.copyOf(Config.efCoolants());
        try {
            Config.efCoolantsSetting().set(List.of("minecraft:water|2|minecraft:lava|640|190"));
            var input = new FluidTank(1000); input.fill(new FluidStack(Fluids.WATER, 10), EXECUTE);
            var output = new FluidTank(1000); var ledger = new EFabricatorCooling();
            ledger.refill(List.of(input), List.of(output), () -> {});
            require(input.getFluidAmount() == 8 && output.getFluidAmount() == 640 && ledger.units() == 190, "ratio/output-limited conversion");
            var saved = ledger.save(); ledger.refill(List.of(input), List.of(output), () -> {});
            require(ledger.save().equals(saved) && input.getFluidAmount() == 8, "short output space consumed coolant");
            require(EFabricatorCooling.load(saved).save().equals(saved), "cooling NBT roundtrip");
            var missing = new FluidTank(1000); missing.fill(new FluidStack(Fluids.LAVA, 1), EXECUTE);
            var empty = new EFabricatorCooling(); empty.refill(List.of(missing), List.of(output), () -> {});
            require(empty.isEmpty() && missing.getFluidAmount() == 1, "unregistered coolant consumed");
            var wrong = new FluidTank(1000); wrong.fill(new FluidStack(Fluids.WATER, 1), EXECUTE);
            empty.refill(List.of(input), List.of(wrong), () -> {});
            require(empty.isEmpty() && input.getFluidAmount() == 8, "wrong output fluid consumed coolant");
            empty.refill(List.of(input), List.of(), () -> {});
            require(empty.isEmpty() && input.getFluidAmount() == 8, "missing output hatch consumed coolant");

            var limited = new FluidTank(1000) {
                boolean restricted = true;
                @Override public int fill(FluidStack stack, FluidAction action) {
                    return super.fill(new FluidStack(stack, action.execute() && restricted ? Math.min(32, stack.getAmount()) : stack.getAmount()), action);
                }
            };
            var two = new FluidTank(10); two.fill(new FluidStack(Fluids.WATER, 2), EXECUTE);
            var deferred = new EFabricatorCooling(); deferred.refill(List.of(two), List.of(limited), () -> {});
            require(two.isEmpty() && limited.getFluidAmount() == 32 && deferred.units() == 0
                    && deferred.save().getCompound("Pending").getInt("Heat") == 608, "partial fill lost heat or credited early");
            var deferredTag = deferred.save(); deferred = EFabricatorCooling.load(deferredTag); limited.restricted = false;
            Config.efCoolantsSetting().set(List.of());
            deferred.refill(List.of(), List.of(limited), () -> {});
            require(deferred.units() == 190 && limited.getFluidAmount() == 640 && !deferred.save().contains("Pending"), "reload lost original pending conversion");
            deferred.refill(List.of(), List.of(limited), () -> {});
            require(deferred.units() == 190 && limited.getFluidAmount() == 640, "pending conversion credited twice");
            var absent = deferredTag.copy(); absent.getCompound("Pending").getCompound("Output").putString("FluidName", "missing_mod:fluid");
            try { EFabricatorCooling.load(absent); throw new AssertionError("missing output dependency accepted"); } catch (IllegalArgumentException expected) { }

            Config.efCoolantsSetting().set(List.of("minecraft:water|2|minecraft:lava|640|190"));
            var shortDrain = new FluidTank(10) {
                @Override public FluidStack drain(FluidStack stack, FluidAction action) {
                    return super.drain(new FluidStack(stack, action.execute() ? Math.min(3, stack.getAmount()) : stack.getAmount()), action);
                }
            };
            shortDrain.fill(new FluidStack(Fluids.WATER, 4), EXECUTE); var heat = new FluidTank(1280);
            var fractional = new EFabricatorCooling(); fractional.refill(List.of(shortDrain), List.of(heat), () -> {});
            require(fractional.units() == 190 && heat.getFluidAmount() == 640 && shortDrain.getFluidAmount() == 1
                    && fractional.save().getCompound("Pending").getInt("Held") == 1, "short actual drain erased unconverted cold fluid");
            fractional = EFabricatorCooling.load(fractional.save());
            Config.efCoolantsSetting().set(List.of("minecraft:water|1|-|0|999"));
            fractional.refill(List.of(shortDrain), List.of(heat), () -> {});
            require(fractional.units() == 380 && heat.getFluidAmount() == 1280 && shortDrain.isEmpty(), "partial cold input reinterpreted after reload");

            Config.efCoolantsSetting().set(List.of("missing:fluid|1|-|0|240"));
            require(EFabricatorCoolants.find(Fluids.WATER) == null, "config removal left stale coolant");
            Config.efCoolantsSetting().set(List.of("water|1|-|0|240"));
            var cold = new FluidTank(1000); cold.fill(new FluidStack(Fluids.WATER, 500), EXECUTE);
            var cap = new EFabricatorCooling(); cap.refill(List.of(cold), List.of(), () -> {});
            require(cap.units() == 100080 && cold.getFluidAmount() == 83, "source ceil/cache or no-output conversion");
            cap.refill(List.of(cold), List.of(), () -> {});
            require(cap.units() == 100080 && cold.getFluidAmount() == 83, "full cache kept consuming");
            cap.consume(5); require(cap.units() == 100075, "per-craft coolant charge");
            require(EFabricatorCooling.load(cap.save()).units() == 100075, "non-multiple cache not saved");
            require(EFabricatorSpecs.coolingAcceleration(56, 56, 19, 100000).equals(new EFabricatorSpecs.Acceleration(18, 40))
                    && EFabricatorSpecs.coolingAcceleration(56, 56, 19, 3).equals(new EFabricatorSpecs.Acceleration(1, 2))
                    && EFabricatorSpecs.coolingAcceleration(0, 1, 19, 100).ticks() == 0
                    && EFabricatorSpecs.coolingAcceleration(56, 0, 19, 100).ticks() == 0
                    && EFabricatorSpecs.coolingAcceleration(56, 56, 1, 100).ticks() == 0
                    && EFabricatorSpecs.coolingAcceleration(Integer.MAX_VALUE, 1, 19, 100000).ticks() == 0, "source acceleration/limits");
            Modularmachinery.LOG.info("[MMC][ef-cooling-codec] PASS: exact ratios, output capacity/type/missing hatch, no-output ceil cache, actual short drain/fill retained across NBT/config changes, missing fluids refused, no duplicate credit and source acceleration limits; explicit test-only fluid rules");
        } finally { Config.efCoolantsSetting().set(previous); }
    }
    private static void require(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
}
