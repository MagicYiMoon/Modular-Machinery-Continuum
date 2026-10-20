/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.extrautilities;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class RainbowRegressionChecks {
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block generator = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("extrautilitiesrebirth", "rainbow_generator"));
        require(generator != null && generator != net.minecraft.world.level.block.Blocks.AIR, "actual rainbow generator registered");
        Block[] blocks = {RegistryModularMagicBlocks.blockRainbowProvider.get(), generator, RegistryBlocks.energyInputHatch.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        try {
            require(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ICE).is(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, new ResourceLocation("forge", "frosty_gen_tag")))
                    && new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PINK_DYE).is(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, new ResourceLocation("forge", "pink_items"))),
                    "optional Rebirth repair datapack restores valid frosty and pink generator item tags");
            for (int i = 0; i < blocks.length; i++) { before[i] = level.getBlockState(origin.offset(i, 0, 0)); level.setBlock(origin.offset(i, 0, 0), blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            var tile = level.getBlockEntity(origin.offset(1, 0, 0)); var provider = (TileRainbowProvider) level.getBlockEntity(origin);
            require(tile != null && provider != null && !provider.isRunning(), "inactive real generator does not satisfy condition");
            var update = Class.forName("inzhefop.extrautilitiesrebirth.procedures.RainbowGeneratorUpdateTickProcedure")
                    .getMethod("execute", LevelAccessor.class, double.class, double.class, double.class);
            for (String name : List.of("culinary", "death", "disenchantment", "ender", "tnt", "frosty", "furnace", "halitosis", "neatherstar", "overclocked", "pink", "potion", "slimey", "survival", "redstone", "magma")) tile.getPersistentData().putDouble(name, 1);
            update.invoke(null, level, (double) origin.getX() + 1, (double) origin.getY(), (double) origin.getZ());
            require(provider.isRunning(), "actual generator procedure marks all 16 active types as running");
            var pattern = new MachinePattern(); var machine = new DynamicMachine("rainbow_regression", "Rainbow", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            context.overrideComponentPositions(List.of(origin, origin.offset(2, 0, 0)));
            var requirement = new RequirementRainbow();
            var recipe = new MachineRecipe("rainbow_regression", "rainbow", 2, List.of(requirement), null, false, -1, "");
            require(requirement.canStart(context) && MachineTicker.processPerTick(recipe, recipe.requirements(), context, 64, false) == null, "running generator satisfies condition regardless of batch");
            var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of())); require(decoded.requirements().get(0) instanceof RequirementRainbow, "rainbow snapshot has no fictitious amount");
            var selected = new RequirementRainbow(); selected.setSelectorTag("absent"); require(!selected.canStart(context), "selector cannot use other detector");
            var energy = (TileEnergyHatch) level.getBlockEntity(origin.offset(2, 0, 0)); energy.produceInternal(1000, false); int stored = energy.getStoredEnergy();
            update.invoke(null, level, (double) origin.getX() + 1, (double) origin.getY(), (double) origin.getZ());
            require(!provider.isRunning(), "actual next generator update clears running when inputs cease");
            require(MachineTicker.processPerTick(recipe, List.of(requirement, new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy(IOType.INPUT, 10)), context, 1, false) != null
                    && energy.getStoredEnergy() == stored, "lost rainbow condition fails before FE cost");
            try { new RequirementRainbow.Type().createRequirement(IOType.OUTPUT, new JsonObject()); throw new AssertionError("output rainbow accepted"); } catch (JsonParseException expected) { }
            Modularmachinery.LOG.info("[MMCE][rainbow-regression] PASS: repaired native input tags, actual Rebirth generator tick/16 types/running reset, adjacent detector, tick preflight before FE, selector, snapshots and input-only JSON");
        } finally { for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
