/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Routing checks with resources deliberately present in the wrong group. */
public final class SelectorTagRegressionChecks {
    private SelectorTagRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var parse = RecipeLoader.class.getDeclaredMethod("parseRequirement", String.class, JsonObject.class);
        parse.setAccessible(true);
        RecipeLoader recipes = new RecipeLoader();
        List<Requirement> requirements = new ArrayList<>();
        for (String raw : List.of(
                "{\"type\":\"modularmachinery:item\",\"item\":\"minecraft:coal\",\"amount\":2,\"io-type\":\"input\"}",
                "{\"type\":\"modularmachinery:item\",\"item\":\"minecraft:diamond\",\"amount\":2,\"io-type\":\"output\"}",
                "{\"type\":\"modularmachinery:fluid\",\"fluid\":\"minecraft:water\",\"amount\":30,\"io-type\":\"input\"}",
                "{\"type\":\"modularmachinery:fluid\",\"fluid\":\"minecraft:lava\",\"amount\":20,\"io-type\":\"output\"}",
                "{\"type\":\"modularmachinery:fluid_pertick\",\"fluid\":\"minecraft:water\",\"amount\":5,\"io-type\":\"input\"}",
                "{\"type\":\"modularmachinery:energy\",\"energyPerTick\":10,\"io-type\":\"input\"}")) {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            json.addProperty("selector-tag", "red");
            Requirement r = (Requirement) parse.invoke(recipes, "selector_fixture", json);
            require(r != null && "red".equals(r.getSelectorTag()), "loader ignored selector");
            require("red".equals(r.scaled(2, 1, r.getIOType(), false).getSelectorTag()), "modifier lost selector");
            requirements.add(r);
        }
        MachineRecipe recipe = new MachineRecipe("selector_fixture", "selector_recipe", 2, requirements, null, false, -1, "");
        MachineRecipe decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
        require(decoded.requirements().stream().allMatch(r -> "red".equals(r.getSelectorTag())), "snapshot dropped selector");
        for (String invalid : List.of("null", "[]", "12", "false", "{}")) {
            JsonObject bad = JsonParser.parseString("{\"type\":\"modularmachinery:item\",\"item\":\"minecraft:coal\",\"io-type\":\"input\"}").getAsJsonObject();
            bad.add("selector-tag", JsonParser.parseString(invalid));
            require(parse.invoke(recipes, "bad_selector", bad) == null, "invalid recipe selector accepted");
            boolean rejected = false;
            try { SelectorTags.read(bad); } catch (JsonParseException expected) { rejected = true; }
            require(rejected, "invalid structure selector accepted");
        }
        require(SelectorTags.read(JsonParser.parseString("{\"selector-tag\":\"\"}").getAsJsonObject()) == null,
                "empty selector must be unrestricted");
        checkDynamicTags();

        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemInputBus.get(),
                RegistryBlocks.itemOutputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.fluidInputHatch.get(),
                RegistryBlocks.fluidInputHatch.get(), RegistryBlocks.fluidOutputHatch.get(), RegistryBlocks.fluidOutputHatch.get(),
                RegistryBlocks.energyInputHatch.get(), RegistryBlocks.energyInputHatch.get(), RegistryBlocks.smartInterface.get(),
                RegistryBlocks.smartInterface.get(), RegistryBlocks.blockFactoryController.get()};
        var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
        var start = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
        var tick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
        advance.setAccessible(true); start.setAccessible(true); tick.setAccessible(true);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos[] positions = new BlockPos[blocks.length];
            var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
            JsonArray parts = new JsonArray();
            for (int i = 0; i < positions.length; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, facing, i, 0, 0);
                before[i] = level.getBlockState(positions[i]);
                if (i > 0 && i < 13) {
                    JsonObject part = new JsonObject(); part.addProperty("x", i);
                    part.addProperty("elements", net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blocks[i]).toString());
                    part.addProperty("selector-tag", i % 2 == 0 ? "red" : "blue"); parts.add(part);
                }
            }
            JsonObject definition = new JsonObject(); definition.addProperty("registryname", "selector_fixture"); definition.add("parts", parts);
            MachineLoader loader = machineLoader(definition);
            DynamicMachine machine = loader.getMachines().values().iterator().next();
            var clientMachine = MachineLoader.fromSnapshot(loader.snapshot()).getMachines().values().iterator().next();
            require(clientMachine.getPattern().getParts().equals(machine.getPattern().getParts()), "structure tags lost in sync");
            try {
                for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
                MachineContext context = new MachineContext(level, origin, facing, machine, loader.getVariables(), machine.getPattern());
                require(context.forSelector("red").getComponentPositions().size() == 6
                        && context.forSelector("red").forSelector("blue").getComponentPositions().size() == 6
                        && context.forSelector("missing").getComponentPositions().isEmpty(), "selector views leaked or fell back");
                var blueIn = bus(level, positions[1]); var redIn = bus(level, positions[2]);
                var blueOut = bus(level, positions[3]); var redOut = bus(level, positions[4]);
                var blueFluid = (TileFluidTank) level.getBlockEntity(positions[5]);
                var redFluid = (TileFluidTank) level.getBlockEntity(positions[6]);
                var blueFluidOut = (TileFluidTank) level.getBlockEntity(positions[7]);
                var redFluidOut = (TileFluidTank) level.getBlockEntity(positions[8]);
                var blueEnergy = (TileEnergyHatch) level.getBlockEntity(positions[9]);
                var redEnergy = (TileEnergyHatch) level.getBlockEntity(positions[10]);
                blueIn.setStackInSlot(0, new ItemStack(Items.COAL, 16));
                blueFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 400));
                int blueFE = blueEnergy.getEnergyStorage().receiveEnergy(1000, false);
                require(!requirements.get(0).canStart(context) && !requirements.get(2).canStart(context)
                        && !requirements.get(5).canStart(context), "wrong-group input/energy accepted");
                var wildcard = new RequirementItem(IOType.INPUT, "minecraft:coal", 1);
                require(wildcard.canStart(context), "unselected requirement excluded tagged bus");
                wildcard.setSelectorTag("Red"); require(!wildcard.canStart(context), "selector matching is not exact");
                redIn.setStackInSlot(0, new ItemStack(Items.COAL, 8));
                redFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 200));
                int redFE = redEnergy.getEnergyStorage().receiveEnergy(1000, false);
                require(MachineTicker.effectiveParallelism(recipe, requirements, context, 8) == 4, "wrong-group stock increased batch size");
                MachineTicker.setParallelism(requirements, 2);
                require(MachineTicker.consumeInputs(requirements, context, 2)
                        && redIn.getStackInSlot(0).getCount() == 4 && blueIn.getStackInSlot(0).getCount() == 16
                        && redFluid.getTank().getFluidAmount() == 140 && blueFluid.getTank().getFluidAmount() == 400,
                        "selected startup consumed another group");
                var controller = (TileController) level.getBlockEntity(origin);
                controller.beginRecipe(recipe.registryName(), 2, 2);
                for (int i = 0; i < redOut.getSlots(); i++) redOut.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
                for (int i = 0; i < 3; i++) advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                require(controller.hasActiveRecipe() && controller.getRecipeTicks() == 2 && count(blueOut, Items.DIAMOND) == 0
                        && redFluid.getTank().getFluidAmount() == 120 && blueFluid.getTank().getFluidAmount() == 400
                        && redEnergy.getEnergyStorage().getEnergyStored() == redFE - 40
                        && blueEnergy.getEnergyStorage().getEnergyStored() == blueFE, "selected tick/output waiting leaked resources");
                controller.load(controller.saveWithoutMetadata()); clear(redOut);
                context = new MachineContext(level, origin, facing, clientMachine, loader.getVariables(), clientMachine.getPattern());
                advance.invoke(null, controller, context, List.of(decoded), new RecipeCraftingContext(decoded));
                require(!controller.hasActiveRecipe() && count(redOut, Items.DIAMOND) == 4 && count(blueOut, Items.DIAMOND) == 0
                        && redFluidOut.getTank().getFluidAmount() == 40 && blueFluidOut.getTank().getFluidAmount() == 0,
                        "completion after reload ignored output selectors");
                checkSharedInputs(context, redIn, blueIn, redFluid, requirements);
                checkAdvancedInputs(context, redIn, blueIn);
                checkEnergyAndRate(context, redEnergy, blueEnergy, redFluid, redFluidOut);
                for (int i : new int[]{11,12}) {
                    CompoundTag data = new CompoundTag(), entry = new CompoundTag(); ListTag list = new ListTag();
                    entry.putString("key", "voltage"); entry.putDouble("number", i == 12 ? 3 : 99); list.add(entry);
                    data.put("boundData", list); ((TileSmartInterface) level.getBlockEntity(positions[i])).load(data);
                }
                var number = new RequirementInterfaceNumInput("voltage", 2, 4); number.setSelectorTag("red");
                require(number.canStart(context), "smart interface selector chose wrong datum");
                number.setSelectorTag("blue"); require(!number.canStart(context), "smart interface selector ignored range");
                // Drive real factory search and advancement without modifying the installed definition registry.
                redIn.setStackInSlot(0, new ItemStack(Items.COAL, 8)); clear(redOut);
                var factory = (TileFactoryController) level.getBlockEntity(positions[13]);
                var simple = new MachineRecipe("selector_fixture", "selector_factory", 1,
                        List.of(requirements.get(0), requirements.get(1)), null, false, -1, "");
                machine.setInternalParallelism(2); factory.setFactoryCeiling(2, 2);
                start.invoke(null, factory, machine, context, List.of(simple));
                start.invoke(null, factory, machine, context, List.of(simple));
                require(factory.getActiveThreadCount() == 1 && redIn.getStackInSlot(0).getCount() == 4,
                        "factory selector batch or unique recipe rule failed");
                factory.load(factory.saveWithoutMetadata());
                for (var thread : factory.getFactoryRecipeThreadList()) tick.invoke(null, thread, factory, machine, context, List.of(simple));
                require(count(redOut, Items.DIAMOND) == 4 && count(blueOut, Items.DIAMOND) == 0,
                        "factory selector lost on saved job");
            } finally {
                for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
            }
        }
    }

    private static void checkSharedInputs(MachineContext context, IItemHandlerModifiable red, IItemHandlerModifiable blue,
                                          TileFluidTank redFluid, List<Requirement> requirements) {
        red.setStackInSlot(0, new ItemStack(Items.COAL, 1)); clear(blue);
        var chosen = new RequirementItem(IOType.INPUT, "minecraft:coal", 1); chosen.setSelectorTag("red");
        var any = new RequirementItem(IOType.INPUT, "minecraft:coal", 1);
        require(!ItemInputPlan.plan(context, List.of(chosen, any), 1, false).valid()
                && !MachineTicker.consumeInputs(List.of(chosen, any), context, 1) && red.getStackInSlot(0).getCount() == 1,
                "tagged/untagged item requirements double-spent a slot");
        redFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 50));
        var same = new RequirementFluid(IOType.INPUT, "minecraft:water", 30); same.setSelectorTag("red");
        require(!FluidInputPlan.plan(context, List.of(same, same), 1).valid()
                && !MachineTicker.consumeInputs(List.of(same, same), context, 1) && redFluid.getTank().getFluidAmount() == 50,
                "selected fluid inputs double-spent a tank");
        redFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 50));
        require(((RequirementFluid) requirements.get(2)).getMaxParallelism(context, 8, List.of(requirements.get(2))) == 1,
                "selected fluid batch borrowed another group");
    }

    private static void checkAdvancedInputs(MachineContext context, IItemHandlerModifiable red, IItemHandlerModifiable blue) {
        clear(red); clear(blue); blue.setStackInSlot(0, new ItemStack(Items.COAL, 16));
        var fuel = new RequirementFuel(1600, 1, hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.EMPTY); fuel.setSelectorTag("red");
        var array = new RequirementIngredientArray(List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1)), 1); array.setSelectorTag("red");
        require(!fuel.canStart(context) && !array.canStart(context), "fuel/array borrowed wrong group");
        red.setStackInSlot(0, new ItemStack(Items.COAL, 2));
        require(ItemInputPlan.plan(context, List.of(fuel, array), 1, false).execute()
                        && red.getStackInSlot(0).isEmpty() && blue.getStackInSlot(0).getCount() == 16,
                "fuel/array routing or shared reservation incorrect");
        var tool = new RequirementItem(IOType.INPUT, "minecraft:iron_pickaxe", 1).withConsumeDurability(3); tool.setSelectorTag("red");
        red.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE)); blue.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
        require(ItemInputPlan.plan(context, List.of(tool), 2, false).execute()
                        && red.getStackInSlot(0).getDamageValue() == 6 && blue.getStackInSlot(0).getDamageValue() == 0,
                "durability routed to wrong tool");
        MachineRecipe extras = new MachineRecipe("selector_fixture", "extras", 1, List.of(tool, fuel, array), null, false, -1, "");
        var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(extras, Map.of()));
        require(decoded.requirements().stream().allMatch(r -> "red".equals(r.getSelectorTag())
                        && "red".equals(r.scaled(2, 1, r.getIOType(), false).getSelectorTag())), "advanced selector lost on sync/modifier");
    }

    private static void checkEnergyAndRate(MachineContext context, TileEnergyHatch red, TileEnergyHatch blue,
                                           TileFluidTank input, TileFluidTank output) {
        red.consumeInternal(Integer.MAX_VALUE, false);
        var energy = new RequirementEnergy(IOType.INPUT, 10); energy.setSelectorTag("red");
        MachineRecipe recipe = new MachineRecipe("selector_fixture", "energy", 1, List.of(energy), null, false, -1, "");
        int blueBefore = blue.getEnergyStorage().getEnergyStored();
        require(!MachineTicker.consumeEnergyTick(recipe, context, 1)
                        && blue.getEnergyStorage().getEnergyStored() == blueBefore, "energy-only path borrowed wrong group");
        red.getEnergyStorage().receiveEnergy(15, false);
        require(MachineTicker.processPerTick(recipe, List.of(energy, energy), context, 1, false) != null
                        && red.getEnergyStorage().getEnergyStored() == 15, "overlapping selected energy partly paid");
        input.getTank().setFluid(new FluidStack(Fluids.WATER, 10)); output.getTank().setFluid(FluidStack.EMPTY);
        var rate = new RequirementFluidPerTick(new RequirementFluid(IOType.INPUT, "minecraft:water", 5)); rate.setSelectorTag("red");
        var product = new RequirementFluidPerTick(new RequirementFluid(IOType.OUTPUT, "minecraft:lava", 5)); product.setSelectorTag("red");
        require(FluidTickIO.plan(context, List.of(rate, product), 2).execute()
                        && input.getTank().getFluidAmount() == 0 && output.getTank().getFluidAmount() == 10,
                "per-tick selected fluid routing incorrect");
        require(FluidTickIO.plan(context, List.of(rate), 1).failure() != null, "fluid rate borrowed wrong group");
    }

    private static void checkDynamicTags() {
        JsonObject json = JsonParser.parseString("""
                {"registryname":"dynamic_tags","parts":[{"x":3,"elements":"minecraft:stone","selector-tag":"old"}],
                 "dynamic-patterns":[{"name":"line","faces":["north"],"minSize":1,"maxSize":2,
                   "structure-size-offset-start":{"x":3},"structure-size-offset":{"z":2},
                   "parts":[{"x":[0,1],"elements":"minecraft:iron_block","selector-tag":"red"}],
                   "parts-end":[{"x":[0,1],"elements":"minecraft:gold_block","selector-tag":"blue"}]}]}
                """).getAsJsonObject();
        DynamicMachine machine = machineLoader(json).getMachines().values().iterator().next();
        var merged = MachineFormation.effectivePattern(machine, Map.of("line", 2));
        require(merged.size() == 6 && merged.getParts().stream().filter(p -> "red".equals(p.selectorTag())).count() == 4
                && merged.getParts().stream().filter(p -> "blue".equals(p.selectorTag())).count() == 2,
                "dynamic arrays/merge lost or retained stale tags");
    }

    private static MachineLoader machineLoader(JsonObject definition) {
        JsonObject snapshot = new JsonObject(), machines = new JsonObject(); machines.add("selector_fixture", definition);
        snapshot.add("variables", new JsonObject()); snapshot.add("machines", machines); return MachineLoader.fromSnapshot(snapshot);
    }
    private static IItemHandlerModifiable bus(ServerLevel level, BlockPos pos) { return (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory(); }
    private static void clear(IItemHandlerModifiable bus) { for (int i = 0; i < bus.getSlots(); i++) bus.setStackInSlot(i, ItemStack.EMPTY); }
    private static int count(IItemHandlerModifiable bus, Item item) { int n = 0; for (int i = 0; i < bus.getSlots(); i++) if (bus.getStackInSlot(i).is(item)) n += bus.getStackInSlot(i).getCount(); return n; }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
