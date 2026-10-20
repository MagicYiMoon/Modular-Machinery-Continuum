/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

public final class DurabilityRegressionChecks {
    private DurabilityRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) {
        Requirements.registerBuiltIns();
        var type = RequirementTypeRegistry.get(new ResourceLocation("modularmachinery:item_durability"));
        require(type != null, "item_durability alias not registered");
        var tool = (RequirementItem) type.createRequirement(IOType.INPUT, JsonParser.parseString("""
                {"item":"minecraft:iron_pickaxe","amount":1,"consumeDurability":10,
                 "nbt":{"quality":">=3"},"nbt-display":{"quality":"3"}}
                """).getAsJsonObject());
        var ordinary = (RequirementItem) new ItemRequirementType().createRequirement(IOType.INPUT,
                JsonParser.parseString("{\"item\":\"minecraft:iron_pickaxe\",\"consumeDurability\":10}").getAsJsonObject());
        require(ordinary.getConsumeDurability() == 10, "normal item consumeDurability route missing");
        var scaled = (RequirementItem) tool.scaled(2, 1, IOType.INPUT, false);
        require(scaled.getAmount() == 2 && scaled.getConsumeDurability() == 10 && scaled.getNbt().pattern() != null,
                "modifier lost durability/NBT or scaled damage instead of use count");
        var recipe = new MachineRecipe("durability_test", "durability_test", 1, List.of(scaled), null, false, -1, "");
        var synced = (RequirementItem) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of())).requirements().get(0);
        require(synced.getAmount() == 2 && synced.getConsumeDurability() == 10 && synced.getNbt().pattern() != null,
                "snapshot lost durability behavior");
        for (String value : List.of("-1", "1.5", "2147483648", "\"10\"", "null")) {
            boolean refused = false;
            try { new ItemRequirementType().createRequirement(IOType.INPUT, JsonParser.parseString(
                    "{\"item\":\"minecraft:iron_pickaxe\",\"consumeDurability\":" + value + "}").getAsJsonObject()); }
            catch (RuntimeException expected) { refused = true; }
            require(refused, "invalid durability accepted: " + value);
        }
        boolean refused = false;
        try { type.createRequirement(IOType.OUTPUT, JsonParser.parseString(
                "{\"item\":\"minecraft:iron_pickaxe\",\"consumeDurability\":10}").getAsJsonObject()); }
        catch (RuntimeException expected) { refused = true; }
        require(refused, "durability output accepted");

        BlockPos busPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(busPos);
        try {
            level.setBlock(busPos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(busPos)).getInventory();
            var pattern = new MachinePattern();
            pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("durability_test", "Durability test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            inventory.setStackInSlot(0, pick(0, 2));
            require(!tool.canStart(context), "wrong NBT tool accepted");
            inventory.setStackInSlot(0, pick(20, 3));
            require(tool.canStart(context) && inventory.getStackInSlot(0).getDamageValue() == 20, "simulation changed tool");
            require(MachineTicker.consumeInputs(List.of(tool), context, 2), "parallel tool input failed");
            require(inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(0).getDamageValue() == 40,
                    "tool consumed as an item or parallel damage incorrect");
            ItemStack nearlyBroken = pick(245, 3); // iron pickaxe max damage 250
            inventory.setStackInSlot(0, nearlyBroken);
            require(tool.getMaxParallelism(context, 8, List.of(tool)) == 1, "last partial use did not count exactly once");
            require(MachineTicker.consumeInputs(List.of(tool), context, 1) && inventory.getStackInSlot(0).isEmpty(),
                    "last use did not break tool");
            inventory.setStackInSlot(0, pick(245, 3));
            inventory.setStackInSlot(1, pick(235, 3));
            require(tool.getMaxParallelism(context, 9, List.of(tool)) == 3, "uses not summed across tools");
            require(MachineTicker.consumeInputs(List.of(tool), context, 3)
                    && inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty(), "multiple-tool break failed");
            inventory.setStackInSlot(0, pick(245, 3));
            inventory.setStackInSlot(1, new ItemStack(Items.DIAMOND, 2));
            var material = new RequirementItem(IOType.INPUT, "minecraft:diamond", 1);
            require(!MachineTicker.consumeInputs(List.of(material, tool, tool), context, 1)
                    && inventory.getStackInSlot(1).getCount() == 2 && inventory.getStackInSlot(0).getDamageValue() == 245,
                    "shared durability shortfall partially consumed material/tool");
            var consumedTool = new RequirementItem(IOType.INPUT, "minecraft:iron_pickaxe", 1);
            require(!ItemInputPlan.plan(context, List.of(consumedTool, tool), 1, false).valid(),
                    "same tool reserved as both consumed material and durability input");
            require(tool.getMaxParallelism(context, 8, List.of(tool, tool)) == 0, "parallel grant double-counted tool");
            inventory.setStackInSlot(0, pick(0, 3));
            var snapshot = ItemInputPlan.plan(context, List.of(tool), 1, false);
            inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            require(!snapshot.execute() && inventory.getStackInSlot(0).is(Items.DIAMOND), "stale plan overwrote changed inventory");
            ItemStack enchanted = pick(0, 3);
            enchanted.enchant(Enchantments.UNBREAKING, 3);
            inventory.setStackInSlot(0, enchanted);
            require(MachineTicker.consumeInputs(List.of(tool), context, 1)
                    && inventory.getStackInSlot(0).getDamageValue() == 10, "explicit recipe damage was randomized by Unbreaking");
            ItemStack unbreakable = pick(0, 3);
            unbreakable.getTag().putBoolean("Unbreakable", true);
            inventory.setStackInSlot(0, unbreakable);
            require(!tool.canStart(context), "unbreakable item supplied durability");
            var noCharge = (RequirementItem) tool.scaled(0, 1, IOType.INPUT, true);
            inventory.setStackInSlot(0, pick(0, 3));
            require(MachineTicker.consumeInputs(List.of(noCharge), context, 1)
                    && inventory.getStackInSlot(0).getDamageValue() == 0, "chance zero damaged tool");
            var stacked = pick(245, 3);
            stacked.setCount(3);
            inventory.setStackInSlot(0, stacked);
            require(MachineTicker.consumeInputs(List.of(tool), context, 3)
                    && inventory.getStackInSlot(0).getCount() == 2 && inventory.getStackInSlot(0).getDamageValue() == 20,
                    "stacked damageable items did not reset damage after break");
            var oneUse = (RequirementItem) tool.scaled(1, 1, IOType.INPUT, false);
            oneUse.setParallelizeUnaffected(true);
            inventory.setStackInSlot(0, pick(0, 3));
            require(MachineTicker.consumeInputs(List.of(oneUse), context, 8)
                    && inventory.getStackInSlot(0).getDamageValue() == 10, "unaffected tool was multiplied");
            var saved = level.getBlockEntity(busPos).saveWithoutMetadata();
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            level.getBlockEntity(busPos).load(saved);
            require(((TileItemBus) level.getBlockEntity(busPos)).getInventory().getStackInSlot(0).getDamageValue() == 10,
                    "tool damage not persisted in bus NBT");
        } finally {
            level.setBlock(busPos, before, Block.UPDATE_ALL);
        }
    }

    private static ItemStack pick(int damage, int quality) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        CompoundTag tag = new CompoundTag();
        tag.putInt("quality", quality);
        stack.setTag(tag);
        stack.setDamageValue(damage);
        return stack;
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
