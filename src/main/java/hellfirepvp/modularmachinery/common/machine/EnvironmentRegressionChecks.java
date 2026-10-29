/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in tests using real detector blocks, world conditions and both recipe lifecycles. */
public final class EnvironmentRegressionChecks {
    private EnvironmentRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        long oldTime = level.getDayTime(); float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        var weatherData = level.getServer().getWorldData().overworldData();
        int clearTime = weatherData.getClearWeatherTime(), rainTime = weatherData.getRainTime(), thunderTime = weatherData.getThunderTime();
        boolean wasRain = weatherData.isRaining(), wasThunder = weatherData.isThundering();
        var machines = MachineReloadListener.getLoader().getMachines(); var oldMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var oldRecipes = new LinkedHashMap<>(recipes);
        try {
            checkParsing();
            BlockPos pos = origin.atY(200), detector = pos.offset(1, 2, 0);
            place(level, detector, RegistryBlocks.environmentDetector.get().defaultBlockState(), saved);
            place(level, detector.above(), Blocks.AIR.defaultBlockState(), saved);
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 2, 0, "modularmachinery:blockenvironmentdetector", "sensor"));
            var machine = new DynamicMachine("environment_checks", "Environment checks", pattern);
            var context = new MachineContext(level, pos, Direction.NORTH, machine, new MachineVariables(), pattern);
            var height = parse("{\"condition\":\"altitude\",\"min\":202,\"max\":202}");
            require(height.canStart(context), "altitude must use detector position, inclusive bounds");
            height.setSelectorTag("absent"); require(!height.canStart(context), "selector leaked sensor");
            height.setSelectorTag("sensor"); require(height.canStart(context), "selector lost sensor");
            require(!CompletableFuture.supplyAsync(() -> height.canStart(context)).join(), "async environment read allowed");
            var dim = parse("{\"condition\":\"dimensions\",\"values\":[\"minecraft:the_nether\",\"minecraft:overworld\"]}");
            require(dim.canStart(context), "dimension OR list");
            require(!parse("{\"condition\":\"dimensions\",\"values\":[\"missing:dimension\"]}").canStart(context), "unknown dimension matched");
            String biomeId = level.getBiome(detector).unwrapKey().orElseThrow().location().toString();
            require(parse("{\"condition\":\"biomes\",\"values\":[\"" + biomeId + "\"]}").canStart(context), "biome registry ID");
            require(!parse("{\"condition\":\"biomes\",\"values\":[\"#missing:tag\"]}").canStart(context), "unknown biome tag matched");
            var biomeTag = level.getBiome(detector).tags().findFirst();
            require(biomeTag.isPresent(), "fixture biome has no tags");
            require(parse("{\"condition\":\"biomes\",\"values\":[\"#" + biomeTag.get().location() + "\"]}").canStart(context), "biome tag lookup");
            var time = parse("{\"condition\":\"time\",\"min\":23000,\"max\":1000}");
            for (int t : new int[]{23000, 23999, 0, 1000}) { level.setDayTime(t); require(time.canStart(context), "wrap time rejected " + t); }
            for (int t : new int[]{1001, 12000, 22999}) { level.setDayTime(t); require(!time.canStart(context), "wrap time accepted " + t); }
            require(RequirementEnvironment.inTimeRange(-1, 23000, 1000), "negative day-time modulo");
            var moon = parse("{\"condition\":\"moonphase\",\"values\":[0]}");
            setWeather(level, false, false); level.setDayTime(18000); level.updateSkyBrightness();
            require(moon.canStart(context), "full moon at night");
            level.setDayTime(6000); level.updateSkyBrightness(); require(!moon.canStart(context), "moon allowed daytime");
            level.setDayTime(4 * 24000L + 18000); level.updateSkyBrightness(); require(!moon.canStart(context), "wrong moon phase");
            require(weather("sunny").canStart(context) && !weather("raining").canStart(context), "clear weather");
            setWeather(level, true, true);
            require(!weather("sunny").canStart(context) && weather("raining").canStart(context)
                    && weather("thundering").canStart(context), "rain includes thunder");
            checkSnow(level, detector, context, saved);
            var tile = (TileEnvironmentDetector) level.getBlockEntity(detector); tile.setControllerPos(pos);
            var tag = tile.saveWithoutMetadata(); level.removeBlockEntity(detector);
            var restored = new TileEnvironmentDetector(detector, RegistryBlocks.environmentDetector.get().defaultBlockState());
            level.setBlockEntity(restored); restored.load(tag);
            require(pos.equals(restored.getControllerPos()) && height.canStart(context), "sensor NBT/binding restore");
            place(level, detector, Blocks.AIR.defaultBlockState(), saved);
            require("component.missing.modularmachinery.environment".equals(height.describeFailure(context)), "removed sensor still matched");
            var unloaded = new MachineContext(level, pos, Direction.NORTH, machine, new MachineVariables(), pattern);
            BlockPos far = new BlockPos(24000000, 200, 24000000); require(!level.hasChunkAt(far), "unloaded fixture chunk is loaded");
            unloaded.overrideComponentPositions(List.of(far)); height.setSelectorTag(null);
            require(!height.canStart(unloaded) && !level.hasChunkAt(far), "environment query loaded a chunk");
            checkProcessing(level, pos.offset(0, 0, 16), saved);
            if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
                hellfirepvp.modularmachinery.integration.crafttweaker.EnvironmentScriptRegressionChecks.run();
            Modularmachinery.LOG.info("[MMC][environment] PASS: six conditions, detector-local position, selectors, wrap time/night moon/weather/biome tags, snow exposure, invalid JSON, snapshots/NBT, async/unloaded guards, ordinary+factory 2x crafting and pause/cancel/resume without tick payment");
        } finally {
            level.setWeatherParameters(clearTime, rainTime, wasRain, wasThunder); weatherData.setThunderTime(thunderTime);
            level.setDayTime(oldTime); level.setRainLevel(rain); level.setThunderLevel(thunder); level.updateSkyBrightness();
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(oldMachines); recipes.clear(); recipes.putAll(oldRecipes);
            MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkSnow(ServerLevel level, BlockPos pos, MachineContext context, Map<BlockPos, BlockState> saved) {
        // BiomeManager's fuzzy lookup can sample a neighboring chunk at this boundary.
        // Preserve and replace the full sampling neighborhood, independently of the seed.
        var biomes = new LinkedHashMap<net.minecraft.world.level.chunk.LevelChunk, Map<BlockPos, Holder<Biome>>>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var chunk = level.getChunk((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
            var original = new HashMap<BlockPos, Holder<Biome>>();
            int qx = chunk.getPos().x * 4, qz = chunk.getPos().z * 4;
            for (int x = qx; x < qx + 4; x++) for (int z = qz; z < qz + 4; z++)
                for (int y = level.getMinBuildHeight() >> 2; y < level.getMaxBuildHeight() >> 2; y++)
                    original.put(new BlockPos(x, y, z), chunk.getNoiseBiome(x, y, z));
            biomes.put(chunk, original);
        }
        var sampler = level.getChunkSource().randomState().sampler();
        try {
            var cold = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
            biomes.keySet().forEach(chunk -> chunk.fillBiomesFromNoise((x,y,z,s) -> cold, sampler));
            setWeather(level, true, false);
            require(weather("snowing").canStart(context), "snow in exposed snowy biome");
            place(level, pos.above(), Blocks.STONE.defaultBlockState(), saved);
            require(!weather("snowing").canStart(context), "roof failed to block local snow");
            place(level, pos.above(), Blocks.AIR.defaultBlockState(), saved);
            var hot = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
            biomes.keySet().forEach(chunk -> chunk.fillBiomesFromNoise((x,y,z,s) -> hot, sampler));
            require(!weather("snowing").canStart(context) && weather("raining").canStart(context), "snow biome vs world rain semantics");
        } finally { biomes.forEach((chunk, original) -> chunk.fillBiomesFromNoise((x,y,z,s) -> original.get(new BlockPos(x,y,z)), sampler)); }
    }
    private static void checkProcessing(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> saved) throws Exception {
        var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class); advance.setAccessible(true);
        var threadTick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class); threadTick.setAccessible(true);
        for (boolean factory : List.of(false, true)) {
            BlockPos pos = origin.offset(0, 0, factory ? 16 : 0);
            place(level, pos, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get()).defaultBlockState()
                    .setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING, Direction.NORTH), saved);
            Block[] blocks = {RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.environmentDetector.get(),
                    RegistryBlocks.energyInputHatch.get(), RegistryBlocks.fluidInputHatch.get()};
            var pattern = new MachinePattern();
            for (int i = 0; i < blocks.length; i++) {
                place(level, pos.offset(i + 1, 0, 0), blocks[i].defaultBlockState(), saved);
                pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blocks[i]).toString()));
            }
            var machine = new DynamicMachine("environment_job", "Environment job", pattern);
            machine.setMaxThreads(1); machine.setParallelizable(true); machine.setInternalParallelism(2); machine.setMaxParallelism(2);
            MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery:environment_job"), machine);
            var controller = (TileController) level.getBlockEntity(pos); controller.setMachineName("environment_job");
            var context = new MachineContext(level, pos, Direction.NORTH, machine, new MachineVariables(), pattern);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos.offset(1, 0, 0))).getInventory();
            var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos.offset(2, 0, 0))).getInventory();
            var energy = (TileEnergyHatch) level.getBlockEntity(pos.offset(4, 0, 0));
            var tank = (TileFluidTank) level.getBlockEntity(pos.offset(5, 0, 0));
            var requirements = List.<Requirement>of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                    new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1), new RequirementEnergy(IOType.INPUT, 2),
                    new RequirementFluidPerTick(new RequirementFluid(IOType.INPUT, "minecraft:water", 1)),
                    parse("{\"condition\":\"dimensions\",\"values\":[\"minecraft:overworld\"]}"), weather("sunny"));
            var recipe = new MachineRecipe("environment_job", "environment_batch", 3, requirements, null, false, -1, "");
            MachineReloadListener.getRecipeLoader().getByMachine().put("environment_job", List.of(recipe));
            require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "environment machine formation");
            controller.setFormed(true);
            input.setStackInSlot(0, new ItemStack(Items.COAL, 2));
            energy.getEnergyStorage().receiveEnergy(100, false); tank.getTank().setFluid(new FluidStack(Fluids.WATER, 100));
            setWeather(level, true, false);
            require(!new RecipeCraftingContext(recipe).canStart(context), "wet recipe started");
            require(MachineTicker.processPerTick(recipe, requirements, context, 2, false) != null
                    && energy.getStoredEnergy() == 100 && tank.getTank().getFluidAmount() == 100,
                    "second environment condition failed after energy/fluid debit");
            setWeather(level, false, false);
            for (int tick = 0; tick < 12 && output.getStackInSlot(0).isEmpty(); tick++) {
                if (factory) FactoryTicker.tick(level, pos, (TileFactoryController) controller); else MachineTicker.tick(level, pos, controller);
            }
            require(output.getStackInSlot(0).is(Items.DIAMOND) && output.getStackInSlot(0).getCount() == 2
                    && input.getStackInSlot(0).isEmpty() && energy.getStoredEnergy() == 88
                    && tank.getTank().getFluidAmount() == 94, "ordinary/factory 2x environment recipe did not finish or paid wrong amount");
            for (boolean cancel : List.of(false, true)) {
                var lifecycle = new MachineRecipe("environment_job", "environment_policy", 3, requirements, null, cancel, -1, "");
                MachineReloadListener.getRecipeLoader().getByMachine().put("environment_job", List.of(lifecycle));
                input.setStackInSlot(0, new ItemStack(Items.COAL));
                require(MachineTicker.consumeInputs(requirements, context, 1) && input.getStackInSlot(0).isEmpty(), "start input consumption");
                setWeather(level, true, false); int before = energy.getStoredEnergy(); int water = tank.getTank().getFluidAmount();
                var thread = factory ? new FactoryRecipeThread((TileFactoryController) controller) : null;
                if (factory) {
                    thread.beginRecipe(lifecycle.registryName(), 3, 1);
                    threadTick.invoke(null, thread, controller, machine, context, List.of(lifecycle));
                    require(thread.hasActiveRecipe() != cancel && thread.getRecipeTicks() == 0, "factory failure policy");
                } else {
                    controller.beginRecipe(lifecycle.registryName(), 3, 1);
                    advance.invoke(null, controller, context, List.of(lifecycle), new RecipeCraftingContext(lifecycle));
                    require(controller.hasActiveRecipe() != cancel && controller.getRecipeTicks() == 0, "ordinary failure policy");
                }
                require(energy.getStoredEnergy() == before && tank.getTank().getFluidAmount() == water
                        && input.getStackInSlot(0).isEmpty(), "failed environment tick spent resources/refunded start input");
                setWeather(level, false, false);
                if (!cancel) {
                    if (factory) { threadTick.invoke(null, thread, controller, machine, context, List.of(lifecycle)); require(thread.getRecipeTicks() == 1, "factory resume"); }
                    else { advance.invoke(null, controller, context, List.of(lifecycle), new RecipeCraftingContext(lifecycle)); require(controller.getRecipeTicks() == 1, "ordinary resume"); }
                    require(energy.getStoredEnergy() == before - 2 && tank.getTank().getFluidAmount() == water - 1, "resume paid wrong resources");
                }
            }
        }
    }
    private static void checkParsing() {
        var examples = List.of("{\"condition\":\"altitude\",\"min\":-64,\"max\":319}",
                "{\"condition\":\"time\",\"min\":23000,\"max\":1000}",
                "{\"condition\":\"biomes\",\"values\":[\"minecraft:plains\",\"#minecraft:is_overworld\"]}",
                "{\"condition\":\"dimensions\",\"values\":[\"minecraft:overworld\"]}",
                "{\"condition\":\"moonphase\",\"values\":[0,4]}", "{\"condition\":\"weather\",\"weather\":\"sunny\"}");
        for (String json : examples) {
            var requirement = parse(json); requirement.setSelectorTag("sensor");
            var recipe = new MachineRecipe("environment_checks", "environment_snapshot", 20, List.of(requirement), null, false, -1, "");
            var encoded = RecipeSnapshotCodec.encode(recipe, Map.of());
            require(encoded.equals(RecipeSnapshotCodec.encode(RecipeSnapshotCodec.decode(encoded), Map.of())), "snapshot roundtrip " + json);
            require(requirement.scaled(10, 0, IOType.INPUT, false) == requirement, "environment scaled by batch modifier");
        }
        for (String json : List.of("{}", "{\"condition\":\"oops\"}",
                "{\"condition\":\"time\",\"min\":-1,\"max\":1000}", "{\"condition\":\"time\",\"min\":0,\"max\":24000}",
                "{\"condition\":\"altitude\",\"min\":2,\"max\":1}", "{\"condition\":\"altitude\",\"min\":1.5,\"max\":2}",
                "{\"condition\":\"altitude\",\"min\":2147483648,\"max\":2147483649}",
                "{\"condition\":\"dimensions\",\"values\":[0]}", "{\"condition\":\"dimensions\",\"values\":[\"overworld\"]}",
                "{\"condition\":\"biomes\",\"values\":[]}", "{\"condition\":\"moonphase\",\"values\":[8]}",
                "{\"condition\":\"weather\",\"weather\":\"hail\"}")) {
            try { parse(json); throw new AssertionError("invalid environment JSON accepted: " + json); }
            catch (JsonParseException expected) { }
        }
        try { new RequirementEnvironment.Type().createRequirement(IOType.OUTPUT, JsonParser.parseString(examples.get(0)).getAsJsonObject()); throw new AssertionError("output environment accepted"); }
        catch (JsonParseException expected) { }
    }
    private static void setWeather(ServerLevel level, boolean rain, boolean thunder) {
        level.setWeatherParameters(rain ? 0 : 6000, 6000, rain, thunder);
        level.setRainLevel(rain ? 1 : 0); level.setThunderLevel(thunder ? 1 : 0);
    }
    private static RequirementEnvironment parse(String json) {
        return (RequirementEnvironment) new RequirementEnvironment.Type().createRequirement(IOType.INPUT, JsonParser.parseString(json).getAsJsonObject());
    }
    private static RequirementEnvironment weather(String value) { return parse("{\"condition\":\"weather\",\"weather\":\"" + value + "\"}"); }
    private static void place(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, BlockState> saved) {
        saved.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, Block.UPDATE_ALL);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError("Environment: " + message); }
}
