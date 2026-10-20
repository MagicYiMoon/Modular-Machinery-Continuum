/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.botania;

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
import vazkii.botania.api.*;
import java.nio.file.*;
import java.util.*;

/** Real Botania capabilities/network membership and ordinary/factory mana recipe lifecycles. */
public final class ManaRegressionChecks {
    private ManaRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(),
                RegistryModularMagicBlocks.blockManaProviderInput.get(), RegistryModularMagicBlocks.blockManaProviderOutput.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        String id = "mana_regression_" + UUID.randomUUID().toString().replace("-", "");
        Path machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        Path recipeFile = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        try {
            for (int i = 0; i < blocks.length; i++) { before[i] = level.getBlockState(origin.offset(i, 0, 0)); level.setBlock(origin.offset(i, 0, 0), blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            var input = (TileManaProvider) level.getBlockEntity(origin.offset(2, 0, 0));
            var output = (TileManaProvider.Output) level.getBlockEntity(origin.offset(3, 0, 0));
            require(input != null && output != null && input.capacity() == 100000, "source default provider capacity and real tiles");
            var receiver = input.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER).orElseThrow(IllegalStateException::new);
            require(receiver.canReceiveManaFromBursts() && !output.canReceiveManaFromBursts() && output.isOutputtingPower(), "source external IO direction");
            receiver.receiveMana(Integer.MAX_VALUE); receiver.receiveMana(Integer.MAX_VALUE);
            require(input.getCurrentMana() == 100000, "mana addition saturates without overflow");
            var saved = input.saveWithoutMetadata(); input.take(Long.MAX_VALUE, false); input.load(saved);
            require(input.getCurrentMana() == 100000, "mana persists with controller binding");
            output.onLoad();
            require(BotaniaAPI.instance().getManaNetworkInstance().getAllPoolsInWorld(level).contains(output), "output provider joins Botania pool network");
            output.onChunkUnloaded();
            require(!BotaniaAPI.instance().getManaNetworkInstance().getAllPoolsInWorld(level).contains(output), "chunk unload removes pool"); output.onLoad();
            Files.writeString(machineFile, "{\"parallelizable\":true,\"parallelism\":2,\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\"}]}");
            var source = JsonParser.parseString("""
                    {"recipeTime":2,"parallelized":true,"requirements":[
                      {"type":"modularmachinery:mana","io-type":"input","amount":100},
                      {"type":"modularmachinery:mana","io-type":"output","amount":40}]}
                    """).getAsJsonObject();
            source.addProperty("machine", id); source.addProperty("registryName", id);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var tick = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var fstart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            var ftick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            for (var m : List.of(start, tick, fstart, ftick)) m.setAccessible(true);
            var controller = (TileController) level.getBlockEntity(origin); controller.setMachineName(id);
            var factory = (TileFactoryController) level.getBlockEntity(origin.offset(1, 0, 0)); factory.setMachineName(id);
            for (boolean rate : List.of(false, true)) for (boolean useFactory : List.of(false, true)) {
                source.getAsJsonArray("requirements").forEach(r -> r.getAsJsonObject().addProperty("perTick", rate));
                Files.writeString(recipeFile, source.toString()); MachineReloadListener.reloadNow();
                var machine = MachineReloadListener.getLoader().findByName(id); var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
                require(recipe != null && recipe.requirements().size() == 2, "source mana JSON loads");
                var remote = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
                require(remote.requirements().get(0) instanceof RequirementMana r && r.rate() == rate && r.amount() == 100, "mana snapshot retains amount and perTick");
                input.take(Long.MAX_VALUE, false); input.add(2000, false); output.take(Long.MAX_VALUE, false);
                var context = new MachineContext(level, useFactory ? origin.offset(1, 0, 0) : origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
                context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos()));
                FactoryRecipeThread active = null;
                if (useFactory) {
                    factory.setFactoryCeiling(2, 2); fstart.invoke(null, factory, machine, context, List.of(recipe));
                    active = factory.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                    fstart.invoke(null, factory, machine, context, List.of(recipe));
                    require(factory.getActiveThreadCount() == 1 && active.getParallelism() == 2, "factory keeps distinct recipe and batch policy");
                } else {
                    start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                    require(controller.hasActiveRecipe() && controller.getParallelism() == 2, "ordinary mana recipe starts with batch");
                }
                require(input.getCurrentMana() == (rate ? 2000 : 1800), "rate is not prepaid and one-time input is paid once");
                for (int n = 0; n < 3; n++) {
                    if (useFactory) { if (active.hasActiveRecipe()) ftick.invoke(null, active, factory, machine, context, List.of(recipe)); }
                    else if (controller.hasActiveRecipe()) tick.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                }
                require(input.getCurrentMana() == (rate ? 1600 : 1800) && output.getCurrentMana() == (rate ? 160 : 80), "ordinary/factory one-time and per-tick output counts");
                var demands = List.<Requirement>of(new RequirementMana.Rate(IOType.INPUT, 1000), new RequirementMana.Rate(IOType.INPUT, 1000));
                require(MachineTicker.processPerTick(recipe, demands, context, 1, false) != null && input.getCurrentMana() == (rate ? 1600 : 1800), "shared rate shortage never partly consumes mana");
                output.add(Long.MAX_VALUE, false); int held = input.getCurrentMana();
                require(MachineTicker.processPerTick(recipe, List.of(new RequirementMana.Rate(IOType.INPUT, 10), new RequirementMana.Rate(IOType.OUTPUT, 10)), context, 1, false) != null
                        && input.getCurrentMana() == held, "output-full rate preflight preserves input");
            }
            var machine = MachineReloadListener.getLoader().findByName(id);
            var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos()));
            class CountedOutput extends RequirementMana {
                int placed;
                CountedOutput(int amount) { super(IOType.OUTPUT, amount); }
                @Override public int produce(MachineContext ctx, int copies) { return placed = super.produce(ctx, copies); }
            }
            var discardable = new CountedOutput(20); discardable.setIgnoreOutputCheck(true);
            var required = new CountedOutput(10);
            var outputs = List.<Requirement>of(discardable, required);
            output.take(Long.MAX_VALUE, false); output.add(output.capacity() - 10, false);
            require(required.outputFailure(context, outputs, 1) == null && required.getMaxParallelism(context, 5, outputs) == 1,
                    "discardable mana never steals mandatory output reservation or reduces its batch");
            MachineTicker.emitOutputs(recipe, outputs, context, 1);
            require(required.placed == 10 && discardable.placed == 0 && output.isFull(),
                    "mandatory mana emits before a leading discardable output");
            var discardedRate = new RequirementMana.Rate(IOType.OUTPUT, 20); discardedRate.setIgnoreOutputCheck(true);
            output.take(10, false);
            require(MachineTicker.processPerTick(recipe, List.of(discardedRate, new RequirementMana.Rate(IOType.OUTPUT, 10)), context, 1, false) == null
                            && output.isFull(), "rate outputs reserve mandatory mana before discardable mana");
            var parser = new ManaRequirementType();
            for (String invalid : List.of("{\"amount\":-1}", "{\"amount\":1.5}", "{\"amount\":2147483648}", "{\"amount\":1,\"perTick\":\"yes\"}")) {
                try { parser.createRequirement(IOType.INPUT, JsonParser.parseString(invalid).getAsJsonObject()); throw new AssertionError("invalid mana JSON accepted"); }
                catch (JsonParseException expected) {}
            }
            Modularmachinery.LOG.info("[MMCE][mana-regression] PASS: native Botania receiver/pool network lifecycle, capacity/overflow/NBT, source JSON and snapshots, ordinary+factory batch counts, one-shot and per-tick IO, shared shortage/output-full no partial mana payment, mandatory output priority over discardable output");
        } finally {
            Files.deleteIfExists(machineFile); Files.deleteIfExists(recipeFile); MachineReloadListener.reloadNow();
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
