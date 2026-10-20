/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;
import java.util.Map;

/** Exercises recipe parsing, matching, modifiers, sync and real hatch IO together. */
public final class NbtRegressionChecks {
    private NbtRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        var input = item(IOType.INPUT, """
                {"item":"minecraft:diamond","amount":2,
                 "nbt":{"quality":">=3","label":"batch-\\\\d+","enabled":"true","ratio":"1.5d"}}
                """);
        CompoundTag good = new CompoundTag();
        good.putInt("quality", 4);
        good.putString("label", "batch-12");
        good.putBoolean("enabled", true);
        good.putDouble("ratio", 1.5D);
        good.putString("extra", "allowed");
        ItemStack tagged = new ItemStack(Items.DIAMOND, 4);
        tagged.setTag(good);
        require(input.matches(tagged), "object NBT, regex escaping, booleans or double suffix failed");
        ItemStack wrong = tagged.copy();
        wrong.getTag().putInt("quality", 2);
        require(!input.matches(wrong), "input NBT condition ignored");
        require(!input.matches(new ItemStack(Items.DIAMOND)), "missing input NBT accepted");
        var scaledInput = (RequirementItem) input.scaled(2, 1, IOType.INPUT, false);
        require(scaledInput.matches(tagged) && !scaledInput.matches(wrong), "modifier dropped NBT filter");

        var output = item(IOType.OUTPUT, """
                {"item":"minecraft:diamond","amount":2,"nbt":{"quality":"3","nested":{"small":"2b"}},
                 "nbt-display":{"quality":"99","display":{"Name":"NBT preview"}}}
                """);
        var fluidOut = fluid(IOType.OUTPUT, """
                {"fluid":"minecraft:water","amount":200,"nbt":{"quality":"3"},
                 "nbt-display":{"quality":"99"}}
                """);
        require(output.asStack().getTag().getInt("quality") == 3
                && output.asStack().getTag().getCompound("nested").contains("small", Tag.TAG_BYTE), "output NBT types lost");
        require(output.asDisplayStack().getTag().getInt("quality") == 99
                && fluidOut.asDisplayStack().getTag().getInt("quality") == 99, "display NBT not separate");
        ItemStack changed = output.asStack();
        changed.getTag().putInt("quality", 88);
        require(output.asStack().getTag().getInt("quality") == 3, "mutable output tag leaked into recipe");
        var scaledOut = (RequirementItem) output.scaled(2, 1, IOType.OUTPUT, false);
        var chanceOut = (RequirementItem) output.scaled(0.5F, 1, IOType.OUTPUT, true);
        require(scaledOut.asStack().getTag().equals(output.asStack().getTag())
                && chanceOut.asDisplayStack().getTag().equals(output.asDisplayStack().getTag()), "modifier lost output/display NBT");
        var recipe = new MachineRecipe("nbt_regression", "nbt_regression", 1,
                List.of(scaledInput, scaledOut, fluidOut), null, false, -1, "");
        var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
        require(!((RequirementItem) decoded.requirements().get(0)).matches(wrong), "snapshot broadened NBT input");
        require(((RequirementItem) decoded.requirements().get(1)).asDisplayStack().getTag()
                .equals(scaledOut.asDisplayStack().getTag()), "snapshot lost display NBT");
        require(((RequirementFluid) decoded.requirements().get(2)).asStack().isFluidEqual(fluidOut.asStack()),
                "snapshot lost fluid tag");

        for (String malformed : List.of("{\"nbt\":[]}", "{\"nbt\":null}",
                "{\"nbt\":{\"list\":[\"1\",\"text\"]}}", "{\"nbt-display\":42}")) {
            boolean rejected = false;
            try { RecipeNbt.read(JsonParser.parseString(malformed).getAsJsonObject()); }
            catch (RuntimeException expected) { rejected = true; }
            require(rejected, "malformed NBT was silently accepted: " + malformed);
        }
        var legacy = item(IOType.INPUT, "{\"item\":\"minecraft:diamond\",\"nbt\":\"{quality:>=3}\"}");
        require(legacy.matches(tagged) && !legacy.matches(wrong), "string NBT compatibility regressed");

        Block[] blocks = {RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(),
                RegistryBlocks.fluidInputHatch.get(), RegistryBlocks.fluidOutputHatch.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var positions = new BlockPos[blocks.length];
        MachinePattern pattern = new MachinePattern();
        for (int i = 0; i < blocks.length; i++) {
            positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i + 1, 0, 0);
            before[i] = level.getBlockState(positions[i]);
            pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, "minecraft:air"));
        }
        try {
            for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            var machine = new DynamicMachine("nbt_regression", "NBT regression", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var in = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[0])).getInventory();
            var out = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            var inTank = (TileFluidTank) level.getBlockEntity(positions[2]);
            var outTank = (TileFluidTank) level.getBlockEntity(positions[3]);
            in.setStackInSlot(0, wrong.copy());
            in.setStackInSlot(1, tagged.copy());
            require(MachineTicker.consumeInputs(List.of(input), context, 1), "NBT item consumption failed");
            require(in.getStackInSlot(0).getCount() == 4 && in.getStackInSlot(1).getCount() == 2,
                    "item input consumed wrong NBT variant");
            var fluidIn = fluid(IOType.INPUT, "{\"fluid\":\"minecraft:water\",\"amount\":200,\"nbt\":{\"quality\":\">=3\"}}");
            inTank.getTank().setFluid(new FluidStack(Fluids.WATER, 1000, wrong.getTag().copy()));
            require(!fluidIn.canStart(context) && !fluidIn.consume(context)
                    && inTank.getTank().getFluidAmount() == 1000, "fluid consumed wrong NBT");
            inTank.getTank().setFluid(new FluidStack(Fluids.WATER, 1000, good.copy()));
            require(fluidIn.canStart(context) && fluidIn.consume(context)
                    && inTank.getTank().getFluidAmount() == 800, "fluid subset matching/drain failed");
            var scaledFluid = (RequirementFluid) fluidIn.scaled(2, 1, IOType.INPUT, false);
            require(!scaledFluid.matches(new FluidStack(Fluids.WATER, 1000, wrong.getTag())), "fluid modifier lost NBT");
            require(MachineTicker.outputAllocationFailure(context, List.of(output, fluidOut), 1) == null,
                    "tagged products refused empty outputs");
            MachineTicker.emitOutputs(recipe, List.of(output, fluidOut), context, 1);
            require(out.getStackInSlot(0).getCount() == 2 && out.getStackInSlot(0).getTag().getInt("quality") == 3,
                    "item output writer lost/replaced NBT");
            require(outTank.getTank().getFluidAmount() == 200
                    && outTank.getTank().getFluid().getTag().getInt("quality") == 3, "fluid writer lost NBT");

            for (int i = 0; i < out.getSlots(); i++) out.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            out.setStackInSlot(0, ItemStack.EMPTY);
            var other = item(IOType.OUTPUT, "{\"item\":\"minecraft:diamond\",\"nbt\":{\"quality\":\"4\"}}");
            require(MachineTicker.outputAllocationFailure(context, List.of(output, other), 1) != null,
                    "different tagged items reserved same empty slot");
            require(MachineTicker.outputAllocationFailure(context, List.of(output, output), 1) == null,
                    "identical tagged outputs cannot share a slot");
            out.setStackInSlot(0, new ItemStack(Items.DIAMOND, 1));
            require(output.countSpareRoom(context) == 0, "tagged output counted incompatible occupied slot");
            outTank.getTank().setFluid(FluidStack.EMPTY);
            var otherFluid = fluid(IOType.OUTPUT, "{\"fluid\":\"minecraft:water\",\"amount\":200,\"nbt\":{\"quality\":\"4\"}}");
            require(MachineTicker.outputAllocationFailure(context, List.of(fluidOut, otherFluid), 1) != null,
                    "different tagged fluids reserved same empty tank");
        } finally {
            for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
        }
    }

    private static RequirementItem item(IOType io, String json) {
        return (RequirementItem) new ItemRequirementType().createRequirement(io, JsonParser.parseString(json).getAsJsonObject());
    }
    private static RequirementFluid fluid(IOType io, String json) {
        return (RequirementFluid) new FluidRequirementType().createRequirement(io, JsonParser.parseString(json).getAsJsonObject());
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
