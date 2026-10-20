/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import wayoftime.bloodmagic.common.item.BloodMagicItems;
import wayoftime.bloodmagic.demonaura.WorldDemonWillHandler;
import java.nio.file.*;
import java.util.*;

/** Native chunk will, shared reservations, mixed payment and real controller lifecycles. */
public final class WillRegressionChecks {
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(),
                RegistryModularMagicBlocks.blockLifeEssenceProviderInput.get(), RegistryModularMagicBlocks.blockLifeEssenceProviderOutput.get(),
                RegistryModularMagicBlocks.blockWillProviderInput.get(), RegistryModularMagicBlocks.blockWillProviderOutput.get(),
                RegistryModularMagicBlocks.blockWillProviderInput.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var oldWill = new EnumMap<EnumDemonWillType, Double>(EnumDemonWillType.class);
        String id = "will_regression_" + UUID.randomUUID().toString().replace("-", "");
        Path machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        Path recipeFile = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        for (var type : EnumDemonWillType.values()) oldWill.put(type, WorldDemonWillHandler.getCurrentWill(level, origin, type));
        try {
            for (int i = 0; i < blocks.length; i++) { before[i] = level.getBlockState(origin.offset(i, 0, 0)); level.setBlock(origin.offset(i, 0, 0), blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            var input = (TileWillProvider) level.getBlockEntity(origin.offset(4, 0, 0));
            var output = (TileWillProvider) level.getBlockEntity(origin.offset(5, 0, 0));
            var lp = (TileLifeEssenceProvider) level.getBlockEntity(origin.offset(2, 0, 0));
            var lpOut = (TileLifeEssenceProvider) level.getBlockEntity(origin.offset(3, 0, 0));
            lp.orbInventory().setStackInSlot(0, new net.minecraft.world.item.ItemStack(BloodMagicItems.MASTER_BLOOD_ORB.get()));
            lpOut.orbInventory().setStackInSlot(0, new net.minecraft.world.item.ItemStack(BloodMagicItems.MASTER_BLOOD_ORB.get()));
            var machine = new DynamicMachine(id, "Will", new MachinePattern());
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of(lp.getBlockPos(), lpOut.getBlockPos(), input.getBlockPos(), output.getBlockPos(), origin.offset(6, 0, 0), input.getBlockPos()));
            var parser = new WillRequirementType();
            require(parser.createRequirement(IOType.INPUT, JsonParser.parseString("{\"amount\":1,\"will-type\":\"raw\",\"min\":200}").getAsJsonObject()) instanceof RequirementWill,
                    "source input min is independent of the unused default output max");
            for (var type : EnumDemonWillType.values()) {
                input.take(type, Double.MAX_VALUE, false); output.add(type, 10, false);
                String name = type == EnumDemonWillType.DEFAULT ? "raw" : type.toString();
                var r = (RequirementWill) parser.createRequirement(IOType.INPUT, JsonParser.parseString("{\"amount\":1.25,\"will-type\":\"" + name + "\",\"min\":2}").getAsJsonObject());
                require(r.consume(context, 2000) && close(input.amount(type), 8.75), "all five native will types and source non-batched cost");
                var a = new RequirementWill(IOType.INPUT, 5, type, 0, 100);
                var b = new RequirementWill(IOType.INPUT, 5, type, 0, 100);
                require(!a.planInputs(context, List.of(a, b), 1).valid() && close(input.amount(type), 8.75), "same-chunk providers and duplicate positions cannot double-count will");
                var tooMuch = new RequirementWill(IOType.OUTPUT, 2, type, 0, 10);
                require(tooMuch.outputFailure(context, List.of(tooMuch), 1) != null && tooMuch.produce(context, 1) == 0
                        && close(input.amount(type), 8.75), "output limit applies to current plus production");
                var floor = new RequirementWill(IOType.INPUT, 7, type, 2, 100);
                require(!floor.consume(context, 1) && close(input.amount(type), 8.75), "input leaves its configured minimum");
                var selector = new RequirementWill(IOType.INPUT, 1, type, 0, 100); selector.setSelectorTag("not_present");
                require(!selector.canStart(context), "selector cannot consume a different structure part");
            }
            for (String invalid : List.of("{\"amount\":-1,\"will-type\":\"raw\"}", "{\"amount\":1e999,\"will-type\":\"raw\"}",
                    "{\"amount\":1,\"will-type\":\"wrong\"}", "{\"amount\":1,\"will-type\":\"raw\",\"max\":-1}",
                    "{\"amount\":1,\"will-type\":\"raw\",\"perTick\":true}")) {
                try { parser.createRequirement(IOType.INPUT, JsonParser.parseString(invalid).getAsJsonObject()); throw new AssertionError("invalid will JSON accepted"); }
                catch (JsonParseException expected) {}
            }
            var shortWill = new RequirementWill(IOType.INPUT, 100, EnumDemonWillType.DEFAULT, 0, 100);
            var costLP = new RequirementLifeEssence(IOType.INPUT, 20); lp.add(100, false);
            for (var demands : List.of(List.<Requirement>of(costLP, shortWill), List.<Requirement>of(shortWill, costLP)))
                require(!MachineTicker.consumeInputs(demands, context, 1) && lp.amount() == 100, "mixed LP/will shortfall preflights both orders");
            Files.writeString(machineFile, "{\"parallelizable\":true,\"parallelism\":2,\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\"}]}");
            var json = JsonParser.parseString("""
                    {"recipeTime":2,"parallelized":true,"requirements":[
                      {"type":"modularmachinery:lifeessence","io-type":"input","amount":10},
                      {"type":"modularmachinery:lifeessence","io-type":"output","amount":4},
                      {"type":"modularmachinery:will","io-type":"input","amount":1.25,"will-type":"raw","min":2},
                      {"type":"modularmachinery:will","io-type":"output","amount":2.5,"will-type":"corrosive","max":100}]}
                    """).getAsJsonObject();
            json.addProperty("machine", id); json.addProperty("registryName", id); Files.writeString(recipeFile, json.toString()); MachineReloadListener.reloadNow();
            machine = MachineReloadListener.getLoader().findByName(id); var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var remote = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
            require(remote.requirements().get(2) instanceof RequirementWill r && r.amount() == 1.25 && r.willName().equals("raw"), "fractional will JSON survives network snapshot");
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var tick = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var fstart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            var ftick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            for (var m : List.of(start, tick, fstart, ftick)) m.setAccessible(true);
            for (boolean factory : List.of(false, true)) {
                input.take(EnumDemonWillType.DEFAULT, Double.MAX_VALUE, false); output.add(EnumDemonWillType.DEFAULT, 10, false);
                input.take(EnumDemonWillType.CORROSIVE, Double.MAX_VALUE, false); lp.take(Long.MAX_VALUE, false); lp.add(100, false); lpOut.take(Long.MAX_VALUE, false);
                context = new MachineContext(level, factory ? origin.offset(1, 0, 0) : origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
                context.overrideComponentPositions(List.of(lp.getBlockPos(), lpOut.getBlockPos(), input.getBlockPos(), output.getBlockPos(), origin.offset(6, 0, 0)));
                var controller = (TileController) level.getBlockEntity(origin); controller.setMachineName(id);
                var fc = (TileFactoryController) level.getBlockEntity(origin.offset(1, 0, 0)); fc.setMachineName(id);
                FactoryRecipeThread active = null;
                if (factory) { fc.setFactoryCeiling(2, 2); fstart.invoke(null, fc, machine, context, List.of(recipe)); active = fc.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                    fstart.invoke(null, fc, machine, context, List.of(recipe)); require(fc.getActiveThreadCount() == 1 && active.getParallelism() == 2, "one identity per factory thread with LP batch"); }
                else { start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe)); require(controller.hasActiveRecipe() && controller.getParallelism() == 2, "ordinary mixed recipe starts with LP batch"); }
                require(lp.amount() == 80 && close(input.amount(EnumDemonWillType.DEFAULT), 8.75), "LP scales by two while source will is charged once");
                for (int n = 0; n < 3; n++) {
                    if (factory) { if (active.hasActiveRecipe()) ftick.invoke(null, active, fc, machine, context, List.of(recipe)); }
                    else if (controller.hasActiveRecipe()) tick.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                }
                require(lpOut.amount() == 8 && close(output.amount(EnumDemonWillType.CORROSIVE), 2.5), "ordinary/factory mixed outputs use exact native costs");
            }
            Modularmachinery.LOG.info("[MMCE][will-regression] PASS: all native chunk will types, fractional costs, same-chunk dedup, min/max, selectors, JSON/snapshot validation, mixed LP preflight, ordinary/factory recipe lifecycle and source non-batched will costs");
        } finally {
            Files.deleteIfExists(machineFile); Files.deleteIfExists(recipeFile); MachineReloadListener.reloadNow();
            for (var type : EnumDemonWillType.values()) {
                WorldDemonWillHandler.drainWill(level, origin, type, Double.MAX_VALUE, true);
                WorldDemonWillHandler.fillWill(level, origin, type, oldWill.get(type), true);
            }
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
