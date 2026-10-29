/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Production registries and tickers; only executed in the isolated opt-in server suite. */
public final class RecipeSortingRegressionChecks {
    private static final String TARGET = "sorting_regression", OTHER = "sorting_regression_source";
    private RecipeSortingRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var machines = MachineReloadListener.getLoader();
        var definitions = new LinkedHashMap<>(machines.getMachines());
        var scripts = ScriptRecipeRegistry.snapshots();
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        var directory = Files.createTempDirectory(ModDataHolder.INSTANCE.getRecipeDirectory().toPath(), "sorting-regression-");
        List<Path> files = new ArrayList<>();
        try {
            var pattern = new MachinePattern();
            Block[] parts = {RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.fluidInputHatch.get()};
            for (int i = 0; i < parts.length; i++)
                pattern.addPart(new MachinePattern.Part(i + 1, 0, 0,
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(parts[i]).toString()));
            var machine = new DynamicMachine(TARGET, "Sorting regression", pattern);
            machine.setMaxThreads(1);
            machines.getMachines().put(new ResourceLocation("modularmachinery", TARGET), machine);
            machines.getMachines().put(new ResourceLocation("modularmachinery", OTHER), new DynamicMachine(OTHER, "Source", pattern));
            checkWeights();
            checkRegistryOrdering(machines);
            checkSelection(level, origin, false, machines, before);
            checkSelection(level, origin.offset(0, 0, 8), true, machines, before);
            checkAuthoringPaths(machines, directory, files);
            if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) checkGasOrdering(machines);
            Modularmachinery.LOG.info("[MMC][recipe-sorting] PASS: requirement contract, priority/combined score/global order/ties, ordinary+factory conflicting recipes and paid IO, JSON+script+adapter reloads, client snapshots; gas checks run when Mekanism is installed");
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            for (Path file : files) Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
            machines.getMachines().clear(); machines.getMachines().putAll(definitions);
            // Remove only our declaration; clearing the registry would also discard live script callbacks.
            if (scripts.stream().noneMatch(script -> "sorting_script_item".equals(script.get("name").getAsString()))) {
                var field = ScriptRecipeRegistry.class.getDeclaredField("recipes");
                field.setAccessible(true);
                synchronized (ScriptRecipeRegistry.class) {
                    ((Map<?, ?>) field.get(null)).remove("sorting_script_item");
                }
            }
            MachineReloadListener.getRecipeLoader().load(machines);
        }
    }

    private static void checkWeights() {
        var item = new RequirementItem(IOType.INPUT, "minecraft:coal", 1);
        var fluid = new RequirementFluid(IOType.INPUT, "minecraft:water", 100);
        require(item.getSortingWeight() == 50_000 && new RequirementEnergy(IOType.INPUT, 1).getSortingWeight() == 50_000_000
                && fluid.getSortingWeight() == 100, "source item/energy/fluid weights differ");
        require(array().getSortingWeight() == 0 && new RequirementCatalyst(item).getSortingWeight() == 0
                && new RequirementFluidPerTick(fluid).getSortingWeight() == 0,
                "array/catalyst/per-tick wrapper inherited an incorrect weight");
        require(new RequirementFuel(1, 1F, RecipeNbt.EMPTY).getSortingWeight() == 50_000
                && item.withConsumeDurability(1).getSortingWeight() == 50_000,
                "fuel or item durability field lost its source item weight");
        var outputOnly = recipe(TARGET, "outputs", 0, new RequirementEnergy(IOType.OUTPUT, 1),
                new RequirementItem(IOType.OUTPUT, "minecraft:coal", 64), new RequirementFluid(IOType.OUTPUT, "minecraft:water", 100));
        require(outputOnly.sortingWeight() == 0, "outputs affected recipe weight");
        Requirement extension = new SelectorRequirement() {
            @Override public IOType getIOType() { return IOType.INPUT; }
            @Override public String typeId() { return "modularmachinery:energy"; }
            @Override public int getSortingWeight() { return 7; }
            @Override public String describe() { return "sorting extension"; }
            @Override public boolean canStart(MachineContext context) { return true; }
        };
        require(recipe(TARGET, "extension", 0, extension).sortingWeight() == 7, "type ID overrode requirement's own weight contract");
    }

    private static void checkRegistryOrdering(MachineLoader machines) {
        var plain = recipe(TARGET, "plain", 0, array());
        var rate = recipe(TARGET, "rate", 0, new RequirementFluidPerTick(new RequirementFluid(IOType.INPUT, "minecraft:water", 1)));
        var fluid = recipe(TARGET, "fluid", 0, new RequirementFluid(IOType.INPUT, "minecraft:water", 1));
        var item = recipe(TARGET, "item", 0, new RequirementItem(IOType.INPUT, "minecraft:coal", 1));
        var energy = recipe(TARGET, "energy", 0, new RequirementEnergy(IOType.INPUT, 1));
        var loader = registry(machines, List.of(plain, rate, fluid, item, energy));
        require(names(loader).equals(List.of("energy", "item", "fluid", "plain", "rate")), "incorrect source requirement order: " + names(loader));
        require(names(registry(machines, List.of(energy.withPriority(1), plain.withPriority(-1))))
                .equals(List.of("plain", "energy")), "input weight defeated explicit priority");
        require(names(registry(machines, List.of(energy.withPriority(Integer.MAX_VALUE), plain.withPriority(Integer.MIN_VALUE))))
                .equals(List.of("plain", "energy")), "priority comparison overflowed");
        // A 100-point fluid advantage must not outrank a recipe registered 101 positions earlier.
        for (int filler : new int[]{99, 100}) {
            List<MachineRecipe> recipes = new ArrayList<>(); recipes.add(plain);
            for (int i = 0; i < filler; i++) recipes.add(recipe(OTHER, "filler_" + i, 0));
            recipes.add(fluid);
            var global = registry(machines, recipes);
            require(names(global).equals(List.of("plain", "fluid")), "sort ID was replaced by weight-only or per-machine numbering");
            require(global.getRecipe("fluid") != null && global.getByMachine().get(TARGET).size() == 2,
                    "equal build scores discarded a valid recipe identity");
            require(names(RecipeLoader.fromSnapshot(global.snapshot(), machines)).equals(names(global)), "snapshot lost global registration order");
        }
        var reverse = registry(machines, List.of(rate, plain));
        require(names(reverse).equals(List.of("rate", "plain")), "zero-weight requirements did not preserve registration order");
    }

    private static void checkSelection(ServerLevel level, BlockPos origin, boolean factory, MachineLoader machines,
                                       Map<BlockPos, BlockState> before) {
        Block[] blocks = {factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get(),
                RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.fluidInputHatch.get()};
        for (int i = 0; i < blocks.length; i++) {
            BlockPos pos = origin.offset(i, 0, 0);
            before.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, blocks[i].defaultBlockState(), Block.UPDATE_ALL);
        }
        var controller = (TileController) level.getBlockEntity(origin);
        var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(origin.offset(1, 0, 0))).getInventory();
        var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(origin.offset(2, 0, 0))).getInventory();
        var tank = ((TileFluidTank) level.getBlockEntity(origin.offset(3, 0, 0))).getTank();
        input.setStackInSlot(0, new ItemStack(Items.COAL, 16));
        tank.fill(new FluidStack(Fluids.WATER, 100), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var ordinary = recipe(TARGET, "choose_array", 0, array(), new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1));
        var preferred = recipe(TARGET, "choose_fluid", 0, new RequirementFluid(IOType.INPUT, "minecraft:water", 100),
                new RequirementItem(IOType.OUTPUT, "minecraft:emerald", 1));
        var ordered = registry(machines, List.of(ordinary, preferred));
        var candidates = ordered.getByMachine().get(TARGET);
        var machine = machines.findByName(TARGET);
        controller.setMachineName(TARGET);
        require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, machines).formed(), "sorting fixture did not form");
        controller.setFormed(true);
        MachineReloadListener.getRecipeLoader().getByMachine().put(TARGET, candidates);
        tick(level, origin, controller);
        require("choose_fluid".equals(controller.getRecipeName()) && input.getStackInSlot(0).getCount() == 16
                && tank.getFluidAmount() == 0, "ordinary/factory chose wrong conflicting recipe or consumed losing inputs");
        for (int i = 0; i < 8 && output.getStackInSlot(0).isEmpty(); i++) tick(level, origin, controller);
        require(output.getStackInSlot(0).is(Items.EMERALD) && output.getStackInSlot(0).getCount() == 1
                && input.getStackInSlot(0).getCount() == 16, "chosen recipe did not emit its own output exactly once");
    }

    private static void checkAuthoringPaths(MachineLoader machines, Path directory, List<Path> files) throws Exception {
        var jsonRecipe = recipe(TARGET, "sorting_json_array", 0, array());
        var source = recipe(OTHER, "sorting_source_fluid", 0, new RequirementFluid(IOType.INPUT, "minecraft:water", 100));
        write(directory, files, "sorting_00_array.json", authoring(jsonRecipe));
        write(directory, files, "sorting_01_source.json", authoring(source));
        var adapter = new JsonObject(); adapter.addProperty("machine", TARGET);
        adapter.addProperty("adapter", "modularmachinery:" + OTHER);
        // This fixture checks ordering across authoring paths, independent of external recipe counts.
        // GT can add thousands of earlier furnace adapters, outweighing the fluid's 100-point score.
        // Global sort-ID/weight competition is tested separately with controlled 99/100 fillers above.
        adapter.addProperty("priority", -1);
        write(directory, files, "sorting_02_copy.adapter.json", adapter);
        var scripted = recipe(TARGET, "sorting_script_item", -2, new RequirementItem(IOType.INPUT, "minecraft:coal", 1));
        ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(scripted, Map.of()));
        var loader = MachineReloadListener.getRecipeLoader();
        for (int reload = 0; reload < 2; reload++) {
            loader.load(machines);
            var recipes = loader.getByMachine().get(TARGET);
            require(recipes != null && recipes.size() == 3 && recipes.get(0).registryName().equals(scripted.registryName())
                    && loader.getSourcePath(recipes.get(1).registryName()).startsWith("internal/adapter/")
                    && recipes.get(2).registryName().equals(jsonRecipe.registryName()),
                    "JSON/script/generated adapter did not share ordering across reload: " + names(loader));
            var expected = names(loader);
            require(loader.installScriptRecipes(machines) == 0 && names(loader).equals(expected), "repeat script merge reordered or duplicated recipes");
            var client = RecipeLoader.fromSnapshot(loader.snapshot(), machines);
            require(names(client).equals(expected) && client.getRecipe("sorting_script_item").sortingWeight() == 50_000,
                    "server/client recipe order or resolved weights differ");
        }
    }

    private static void checkGasOrdering(MachineLoader machines) {
        var gas = new hellfirepvp.modularmachinery.integration.mekanism.RequirementGas(IOType.INPUT, "mekanism:oxygen", 1, 1F);
        var rate = new hellfirepvp.modularmachinery.integration.mekanism.RequirementGasPerTick(IOType.INPUT, "mekanism:oxygen", 1);
        require(gas.getSortingWeight() == 100 && rate.getSortingWeight() == 0, "gas and per-tick gas weights differ from source");
        var loader = registry(machines, List.of(recipe(TARGET, "gas_array", 0, array()),
                recipe(TARGET, "gas_rate", 0, rate), recipe(TARGET, "gas_once", 0, gas)));
        require(names(loader).equals(List.of("gas_once", "gas_array", "gas_rate")), "gas recipe still sorted behind an unweighted wrapper");
        Modularmachinery.LOG.info("[MMC][recipe-sorting-gas] PASS: real Mekanism gas/per-tick types and snapshot ordering");
    }

    private static MachineRecipe recipe(String machine, String name, int priority, Requirement... requirements) {
        return new MachineRecipe(machine, name, 3, List.of(requirements), null, false, -1, "", priority);
    }
    private static RequirementIngredientArray array() {
        return new RequirementIngredientArray(List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1)), 1F);
    }
    private static RecipeLoader registry(MachineLoader machines, List<MachineRecipe> recipes) {
        var snapshot = new JsonArray();
        recipes.forEach(recipe -> snapshot.add(RecipeSnapshotCodec.encode(recipe, Map.of())));
        return RecipeLoader.fromSnapshot(snapshot, machines);
    }
    private static List<String> names(RecipeLoader loader) {
        return loader.getByMachine().getOrDefault(TARGET, List.of()).stream().map(MachineRecipe::registryName).toList();
    }
    private static void tick(ServerLevel level, BlockPos origin, TileController controller) {
        if (controller instanceof TileFactoryController factory) FactoryTicker.tick(level, origin, factory);
        else MachineTicker.tick(level, origin, controller);
    }
    private static JsonObject authoring(MachineRecipe recipe) {
        var json = RecipeSnapshotCodec.encode(recipe, Map.of());
        json.addProperty("registryName", json.remove("name").getAsString());
        json.addProperty("recipeTime", json.remove("duration").getAsInt());
        return json;
    }
    private static void write(Path directory, List<Path> files, String name, JsonObject json) throws Exception {
        Path file = directory.resolve(name); files.add(file); Files.writeString(file, json.toString());
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
