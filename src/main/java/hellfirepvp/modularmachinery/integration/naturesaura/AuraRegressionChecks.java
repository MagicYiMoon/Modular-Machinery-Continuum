/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.naturesaura;

import com.google.gson.*;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import java.nio.file.*;
import java.util.*;

/** Real Nature's Aura drain spots, shared-area reservations and controller lifecycles. */
public final class AuraRegressionChecks {
    public static void run(ServerLevel level, BlockPos requested) throws Exception {
        BlockPos origin = new BlockPos((requested.getX() >> 4) * 16 + 13, requested.getY(), requested.getZ());
        int[] offsets = {0, 1, 2, 6, -1, 3, 35};
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(),
                RegistryModularMagicBlocks.blockAuraProviderInput.get(), RegistryModularMagicBlocks.blockAuraProviderOutput.get(),
                RegistryModularMagicBlocks.blockAuraProviderInput.get(), RegistryModularMagicBlocks.blockAuraProviderInput.get(),
                RegistryModularMagicBlocks.blockAuraProviderOutput.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var chunks = new LinkedHashMap<IAuraChunk, CompoundTag>();
        String id = "aura_regression_" + UUID.randomUUID().toString().replace("-", "");
        Path machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        Path recipeFile = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        // Preserve the affected chunks, including spots next to the tested providers.
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 1; z++) {
            var chunk = IAuraChunk.getAuraChunk(level, origin.offset(x * 16, 0, z * 16)); chunks.put(chunk, chunk.serializeNBT().copy());
        }
        try {
            clear(chunks);
            for (int i = 0; i < blocks.length; i++) { var p = origin.offset(offsets[i], 0, 0); before[i] = level.getBlockState(p); level.setBlock(p, blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            var input = (TileAuraProvider) level.getBlockEntity(origin.offset(2, 0, 0));
            var output = (TileAuraProvider) level.getBlockEntity(origin.offset(6, 0, 0));
            var adjacent = (TileAuraProvider) level.getBlockEntity(origin.offset(3, 0, 0));
            require(input != null && output != null && input.auraType().equals(NaturesAuraAPI.TYPE_OVERWORLD), "native provider registration and dimension type");
            var machine = new DynamicMachine(id, "Aura", new MachinePattern());
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of(input.getBlockPos(), input.getBlockPos(), origin.offset(-1, 0, 0)));
            setArea(input, 100);
            var a = req(IOType.INPUT, 60, 0, Integer.MAX_VALUE); var b = req(IOType.INPUT, 60, 0, Integer.MAX_VALUE);
            require(!a.planInputs(context, List.of(a, b), 1).valid() && input.amount() == 100, "same-chunk providers cannot double-count aura");
            context.overrideComponentPositions(List.of(input.getBlockPos(), adjacent.getBlockPos()));
            require(adjacent.amount() == 100 && !MachineTicker.consumeInputs(List.of(a, b), context, 1) && input.amount() == 100,
                    "adjacent cross-chunk areas share reservations without partial payment");
            require(a.consume(context, 1) && input.amount() == 40 && adjacent.amount() == 40, "exact native debit, shared world aura and no x100000 multiplier");
            context.overrideComponentPositions(List.of(input.getBlockPos()));
            require(a.getMaxParallelism(context, Integer.MAX_VALUE, List.of(a)) == 0, "batch ceiling observes the minimum");
            setArea(input, 1000);
            require(a.getMaxParallelism(context, 1000, List.of(a)) == 16, "batch capacity returns recipe copies");
            a.setParallelizeUnaffected(true);
            require(a.getMaxParallelism(context, 1000, List.of(a)) == 1000 && a.consume(context, 1000) && input.amount() == 940, "batch exemption is serialized and charged once");
            var stale = b.planInputs(context, List.of(b), 1); input.change(1, IOType.INPUT, false);
            require(!stale.execute() && input.amount() == 939, "stale reservations reject before mutation");
            var modified = (RequirementAura) b.scaled(2, 1, IOType.INPUT, false);
            require(modified.amount() == 120, "aura supports recipe amount modifiers");
            var selected = req(IOType.INPUT, 1, 0, Integer.MAX_VALUE); selected.setSelectorTag("absent");
            require(!selected.canStart(context), "selector cannot borrow an unrelated provider");
            var wrong = new RequirementAura(IOType.INPUT, 1, NaturesAuraAPI.TYPE_NETHER, 0, Integer.MAX_VALUE);
            require(!wrong.canStart(context), "dimension aura types are strict");
            setArea(input, -200); var negative = req(IOType.INPUT, 30, Integer.MIN_VALUE, Integer.MAX_VALUE);
            require(negative.consume(context, 1) && input.amount() == -230, "source default minimum permits depleted aura");
            clear(chunks); setArea(output, IAuraChunk.DEFAULT_AURA - 20);
            context.overrideComponentPositions(List.of(output.getBlockPos()));
            require(req(IOType.OUTPUT, 50, Integer.MIN_VALUE, Integer.MAX_VALUE).produce(context, 1) == 50 && output.amount() == IAuraChunk.DEFAULT_AURA + 30,
                    "exact output crosses a negative drain spot without aimForZero truncation");
            var full = req(IOType.OUTPUT, 20, Integer.MIN_VALUE, (int) output.amount() + 10);
            require(full.produce(context, 1) == 0, "output respects maximum without partial production");
            full.setIgnoreOutputCheck(true);
            require(full.produce(context, 1) == 10, "discardable output is capped by actual configured room");
            long beforePriority = output.amount();
            var requiredOutput = req(IOType.OUTPUT, 10, 0, (int) beforePriority + 10);
            var discardable = req(IOType.OUTPUT, 20, 0, (int) beforePriority + 10); discardable.setIgnoreOutputCheck(true);
            require(requiredOutput.outputFailure(context, List.of(discardable, requiredOutput), 1) == null,
                    "discardable output cannot steal mandatory aura space");
            context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos()));
            setArea(input, 100);
            var fullOutput = req(IOType.OUTPUT, 20, 0, (int) output.amount());
            require(fullOutput.outputFailure(context, List.of(req(IOType.INPUT, 10, 0, Integer.MAX_VALUE), fullOutput), 1) != null
                    && input.amount() == 100, "full output preflight does not mutate aura inputs");
            // A different input resource's failure cannot consume already available aura.
            var missing = new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem(IOType.INPUT, "minecraft:diamond", 1);
            for (var list : List.of(List.<Requirement>of(req(IOType.INPUT, 10, 0, Integer.MAX_VALUE), missing), List.<Requirement>of(missing, req(IOType.INPUT, 10, 0, Integer.MAX_VALUE))))
                require(!MachineTicker.consumeInputs(list, context, 1) && input.amount() == 100, "mixed resource shortage preserves aura in both orders");
            var parser = new AuraRequirementType();
            for (String invalid : List.of("{\"amount\":-1}", "{\"amount\":1.5}", "{\"amount\":2147483648}", "{\"amount\":1,\"aura-type\":\"wrong:missing\"}",
                    "{\"amount\":1,\"aura-type\":\"naturesaura:overworld\",\"min-aura\":0.5}", "{\"amount\":1,\"aura-type\":\"naturesaura:overworld\",\"perTick\":true}")) {
                try { parser.createRequirement(IOType.INPUT, JsonParser.parseString(invalid).getAsJsonObject()); throw new AssertionError("invalid aura JSON accepted"); }
                catch (JsonParseException expected) {}
            }
            Files.writeString(machineFile, "{\"parallelizable\":true,\"parallelism\":2,\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\"}]}");
            var json = JsonParser.parseString("""
                    {"recipeTime":2,"parallelized":true,"requirements":[
                      {"type":"modularmachinery:aura","io-type":"input","amount":10,"aura-type":"naturesaura:overworld","min-aura":0},
                      {"type":"modularmachinery:aura","io-type":"output","amount":4,"aura-type":"naturesaura:overworld","max-aura":1000000}]}
                    """).getAsJsonObject();
            json.addProperty("machine", id); json.addProperty("registryName", id); Files.writeString(recipeFile, json.toString()); MachineReloadListener.reloadNow();
            machine = MachineReloadListener.getLoader().findByName(id); var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
            require(recipe != null && recipe.requirements().size() == 2, "source aura JSON loads");
            var remote = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
            require(remote.requirements().get(0) instanceof RequirementAura r && r.amount() == 10 && r.auraName().equals("naturesaura:overworld"), "aura snapshot retains type and native units");
            var snapshotJson = new JsonObject(); a.writeJson(snapshotJson);
            require(((RequirementAura) parser.createRequirement(IOType.INPUT, snapshotJson)).isParallelizeUnaffected(), "batch exemption survives JSON roundtrip");
            clear(chunks); setArea(output, 100);
            var remoteOutput = (TileAuraProvider) level.getBlockEntity(origin.offset(35, 0, 0)); setArea(remoteOutput, 100);
            context.overrideComponentPositions(List.of(output.getBlockPos(), remoteOutput.getBlockPos()));
            var limited = req(IOType.OUTPUT, 10, 0, 110); var generous = req(IOType.OUTPUT, 30, 0, 200);
            require(limited.outputFailure(context, List.of(limited, generous), 1) == null, "different output limits have a valid shared allocation");
            MachineTicker.emitOutputs(recipe, List.of(limited, generous), context, 1);
            require(output.amount() == 110 && remoteOutput.amount() == 130, "grouped output commit preserves earlier limits and the preflight allocation");
            // Exercise the actual integer API limits without redirects or wrapped area queries.
            clear(chunks); output.change(Integer.MAX_VALUE, IOType.OUTPUT, false);
            context.overrideComponentPositions(List.of(output.getBlockPos()));
            require(output.amount() == (long) IAuraChunk.DEFAULT_AURA + Integer.MAX_VALUE
                    && req(IOType.OUTPUT, 1, 0, Integer.MAX_VALUE).produce(context, 1) == 0, "extreme positive area sums never wrap");
            clear(chunks); input.change(Integer.MAX_VALUE, IOType.INPUT, false);
            context.overrideComponentPositions(List.of(input.getBlockPos()));
            require(!req(IOType.INPUT, 2, Integer.MIN_VALUE, Integer.MAX_VALUE).consume(context, 1)
                    && req(IOType.INPUT, 1, Integer.MIN_VALUE, Integer.MAX_VALUE).consume(context, 1)
                    && input.spotAmount() == Integer.MIN_VALUE, "native drain spot bound prevents overflow redirects");
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var tick = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var fstart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            var ftick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            for (var method : List.of(start, tick, fstart, ftick)) method.setAccessible(true);
            for (boolean factory : List.of(false, true)) {
                clear(chunks); setArea(input, 100); setArea(output, 100);
                context = new MachineContext(level, factory ? origin.offset(1, 0, 0) : origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
                context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos()));
                var controller = (TileController) level.getBlockEntity(origin); controller.setMachineName(id);
                var fc = (TileFactoryController) level.getBlockEntity(origin.offset(1, 0, 0)); fc.setMachineName(id);
                FactoryRecipeThread active = null;
                if (factory) {
                    fc.setFactoryCeiling(2, 2); fstart.invoke(null, fc, machine, context, List.of(recipe));
                    active = fc.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                    fstart.invoke(null, fc, machine, context, List.of(recipe));
                    require(fc.getActiveThreadCount() == 1 && active.getParallelism() == 2, "factory distinct recipe policy keeps internal aura batching");
                } else {
                    start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                    require(controller.hasActiveRecipe() && controller.getParallelism() == 2, "ordinary aura recipe starts with batch");
                }
                require(input.amount() == 80 && output.amount() == 100, "input charged once on start");
                for (int n = 0; n < 3; n++) {
                    if (factory) { if (active.hasActiveRecipe()) ftick.invoke(null, active, fc, machine, context, List.of(recipe)); }
                    else if (controller.hasActiveRecipe()) tick.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                }
                require(input.amount() == 80 && output.amount() == 108, "ordinary/factory exact completion output");
            }
            Modularmachinery.LOG.info("[MMCE][aura-regression] PASS: native units, negative aura, min/max, same-chunk dedup and cross-chunk overlap, strict type/JSON/snapshots, modifiers/selectors/batch exemption, mixed preflight and ordinary/factory lifecycle");
        } finally {
            Files.deleteIfExists(machineFile); Files.deleteIfExists(recipeFile); MachineReloadListener.reloadNow();
            for (var e : chunks.entrySet()) { e.getKey().deserializeNBT(e.getValue()); e.getKey().markDirty(); }
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(offsets[i], 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static RequirementAura req(IOType io, int amount, int min, int max) { return new RequirementAura(io, amount, NaturesAuraAPI.TYPE_OVERWORLD, min, max); }
    private static void clear(Map<IAuraChunk, CompoundTag> chunks) { for (var c : chunks.keySet()) { c.deserializeNBT(new CompoundTag()); c.markDirty(); } }
    private static void setArea(TileAuraProvider tile, int amount) {
        long delta = amount - tile.amount();
        require(Math.abs(delta) <= Integer.MAX_VALUE, "test fixture delta fits native int API");
        tile.change((int) Math.abs(delta), delta < 0 ? IOType.INPUT : IOType.OUTPUT, false);
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
