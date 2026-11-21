/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.block.BlockEnergyHatch;
import hellfirepvp.modularmachinery.common.block.BlockFluidHatch;
import hellfirepvp.modularmachinery.common.block.BlockUpgradeBus;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.block.prop.FluidHatchSize;
import hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData;
import hellfirepvp.modularmachinery.common.event.AutoAssemblyEvents;
import hellfirepvp.modularmachinery.common.menu.MenuUpgradeBus;
import hellfirepvp.modularmachinery.common.network.UpgradeBusInfoPacket;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ComponentFeedbackRegressionChecks {
    private ComponentFeedbackRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        level.getChunk(origin);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FeedbackFixture")) {
            @Override public void displayClientMessage(Component message, boolean overlay) {}
        };
        player.setPos(origin.getX() - 2.5, origin.getY(), origin.getZ() + 0.5);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        checkTierRecipes(level, origin, player);
        checkAssembly(level, origin, player);
        checkUpgradeMenu(level, origin, player);
        checkDisassembly(level, origin, player);
        Modularmachinery.LOG.info("[MMC][component-feedback] OVERALL: PASS");
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(Modularmachinery.MODID, name);
    }

    private static void checkTierRecipes(ServerLevel level, BlockPos pos, FakePlayer player) {
        for (String family : new String[]{"energy", "fluid", "item"}) {
            for (String io : new String[]{"input", "output"}) {
                for (String tier : new String[]{"tiny", "small"}) {
                    String name = family + "_" + io + "_" + tier;
                    var recipe = (ShapedRecipe) level.getRecipeManager().byKey(id(name)).orElseThrow();
                    var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
                    for (int i = 0; i < recipe.getIngredients().size(); i++) {
                        var ingredient = recipe.getIngredients().get(i);
                        var options = ingredient.getItems();
                        if (!ingredient.isEmpty() && options.length != 0)
                            grid.setItem((i / recipe.getWidth()) * 3 + i % recipe.getWidth(), options[0].copy());
                    }
                    require(recipe.matches(grid, level), "grid did not match " + name);
                    ItemStack result = recipe.assemble(grid, level.registryAccess());
                    var tag = result.getTagElement("BlockStateTag");
                    require(tag != null && tier.equals(tag.getString("size")), "wrong recipe output " + name);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, result.copy());
                    var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos.below()));
                    require(result.getItem().useOn(context).consumesAction(), "BlockItem refused placement " + name);
                    var state = level.getBlockState(pos);
                    require(state.getProperties().stream().anyMatch(p -> p.getName().equals("size")
                            && state.toString().contains("size=" + tier + "]")), "placed wrong tier " + name + " " + state);
                    var tile = level.getBlockEntity(pos);
                    if (tile instanceof TileEnergyHatch energy) {
                        var size = state.getValue(BlockEnergyHatch.SIZE);
                        require(energy.getCapacity() == size.getMaxEnergy()
                                && energy.getMachineTransferLimit() == size.getMaxTransfer(), "placed wrong FE limits " + name);
                    }
                    if (tile instanceof TileFluidTank fluid)
                        require(fluid.getTank().getCapacity() == state.getValue(BlockFluidHatch.SIZE).getCapacity(),
                                "placed wrong fluid capacity " + name);
                    if (tile instanceof TileItemBus items)
                        require(items.getInventory().getSlots() == (tier.equals("tiny") ? 1 : 4),
                                "placed wrong item slots " + name);
                    require(Block.getDrops(state, level, pos, tile).stream().anyMatch(d -> ItemStack.isSameItemSameTags(d, result)),
                            "wrong survival drop " + name);
                }
            }
        }
        Modularmachinery.LOG.info("[MMC][component-feedback] PASS: 12 real shaped recipes, tier placement, capacities and loot");
    }

    private static void checkAssembly(ServerLevel level, BlockPos origin, FakePlayer player) {
        var loader = MachineReloadListener.serverDefinitions().machines();
        var machineId = id("feedback_energy");
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockenergyinputhatch@0"));
        var machine = new DynamicMachine(machineId.getPath(), "Energy test", pattern);
        loader.getMachines().put(machineId, machine);
        try {
            var controller = putController(level, origin, machine);
            var hatchPos = origin.east();
            level.setBlock(hatchPos, RegistryBlocks.energyInputHatch.get().defaultBlockState()
                    .setValue(BlockEnergyHatch.SIZE, EnergyHatchData.ULTIMATE), Block.UPDATE_ALL);
            var tile = (TileEnergyHatch) level.getBlockEntity(hatchPos);
            var cap = tile.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
            cap.receiveEnergy(1_000_000, false);
            require(tile.getStoredEnergy() > EnergyHatchData.TINY.getMaxEnergy(), "fixture did not fill ultimate hatch");
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            AutoAssemblyEvents.processAssemblyRequest(level, player, origin, 0);
            var after = (TileEnergyHatch) level.getBlockEntity(hatchPos);
            require(after == tile && level.getBlockState(hatchPos).getValue(BlockEnergyHatch.SIZE) == EnergyHatchData.TINY,
                    "wooden stick did not exercise same-block ultimate to tiny replacement");
            require(cap.getMaxEnergyStored() == EnergyHatchData.TINY.getMaxEnergy()
                    && cap.getEnergyStored() == cap.getMaxEnergyStored()
                    && after.getMachineTransferLimit() == EnergyHatchData.TINY.getMaxTransfer(), "stale FE size/rate");
            var nbt = after.saveWithoutMetadata();
            nbt.putInt("energy", Integer.MAX_VALUE);
            after.load(nbt);
            require(after.getStoredEnergy() == after.getCapacity(), "stale FE saved data exceeded capacity");
            require(controller.getLevel() == level, "controller was replaced");
        } finally {
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            loader.getMachines().remove(machineId);
        }
        Modularmachinery.LOG.info("[MMC][component-feedback] PASS: real creative wooden-stick replacement, held capability and saved FE");
    }

    private static void checkUpgradeMenu(ServerLevel level, BlockPos origin, FakePlayer player) {
        var component = RegistryBlocks.upgradeBus.get();
        var speed = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id("upgrade_speed"));
        for (var tier : UpgradeBusData.values()) {
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin, component.defaultBlockState().setValue(BlockUpgradeBus.TYPE, tier), Block.UPDATE_ALL);
            var bus = (TileUpgradeBus) level.getBlockEntity(origin);
            var stack = new ItemStack(speed, 2);
            require(bus.getUpgradeInventory().insertItem(tier.getUpgradeSlots() - 1, stack, false).isEmpty(), "install speed");
            var server = MenuUpgradeBus.forBus(1, player.getInventory(), bus);
            FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer());
            FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                MenuUpgradeBus.writeOpenData(payload, bus);
                var client = RegistryMenus.UPGRADE_BUS.get().create(1, player.getInventory(), payload);
                require(client.getTier() == tier && client.getUpgradeSlotCount() == server.getUpgradeSlotCount()
                        && client.slots.size() == tier.getUpgradeSlots() + 36, "incorrect open slot count " + tier);
                var lines = MenuUpgradeBus.describe(bus);
                require(lines.stream().anyMatch(line -> line.getString().contains("2x ")), "installed module absent");
                UpgradeBusInfoPacket.encode(new UpgradeBusInfoPacket(1, origin, lines), wire);
                require(lines.equals(UpgradeBusInfoPacket.decode(wire).lines()), "module lines lost on wire");
            } finally {
                payload.release();
                wire.release();
            }
        }
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Modularmachinery.LOG.info("[MMC][component-feedback] PASS: five menu payloads and authoritative module descriptions");
    }

    private static void checkDisassembly(ServerLevel level, BlockPos origin, FakePlayer player) {
        var loader = MachineReloadListener.serverDefinitions().machines();
        var machineId = id("feedback_disassembly");
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockupgradebus"));
        pattern.addPart(new MachinePattern.Part(2, 0, 0, "minecraft:gold_block"));
        pattern.addPart(new MachinePattern.Part(3, 0, 0, "minecraft:lapis_block"));
        pattern.addPart(new MachinePattern.Part(4, 0, 0, "minecraft:obsidian"));
        pattern.addPart(new MachinePattern.Part(0, 0, 1, "minecraft:iron_bars"));
        pattern.addPart(new MachinePattern.Part(1, 0, 1, "minecraft:iron_bars"));
        var machine = new DynamicMachine(machineId.getPath(), "Disassembly test", pattern);
        loader.getMachines().put(machineId, machine);
        BlockPos protectedPos = origin.east(2), sharedPos = origin.east(3), otherPos = origin.east(5);
        var speed = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id("upgrade_speed"));
        Consumer<BlockEvent.BreakEvent> preventBreak = event -> {
            if (event.getPlayer() == player && event.getPos().equals(protectedPos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(preventBreak);
        try {
            var controller = putController(level, origin, machine);
            var other = putController(level, otherPos, machine);
            var second = new MachinePattern();
            second.addPart(new MachinePattern.Part(-2, 0, 0, "minecraft:lapis_block"));
            other.recordFormedStructure(machine, second, Map.of(), Direction.NORTH);
            other.setFormed(true);
            level.setBlock(origin.east(), RegistryBlocks.upgradeBus.get().defaultBlockState()
                    .setValue(BlockUpgradeBus.TYPE, UpgradeBusData.ULTIMATE), 3);
            var bus = (TileUpgradeBus) level.getBlockEntity(origin.east());
            ItemStack tagged = new ItemStack(speed, 2);
            tagged.getOrCreateTag().putString("fixture", "persist");
            bus.getUpgradeInventory().insertItem(17, tagged, false);
            level.setBlock(protectedPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            level.setBlock(sharedPos, Blocks.LAPIS_BLOCK.defaultBlockState(), 3);
            level.setBlock(origin.east(4), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(origin.south(), Blocks.IRON_BARS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, true), 3);
            level.setBlock(origin.south().east(), Blocks.IRON_BARS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, true), 3);
            controller.recordFormedStructure(machine, pattern, Map.of(), Direction.NORTH);
            controller.setFormed(true);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RegistryItems.DISASSEMBLY_TOOL.get()));
            player.setShiftKeyDown(true);
            var result = player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                    InteractionHand.MAIN_HAND, hit(origin));
            require(result.consumesAction(), "tool did not intercept controller click");
            require(level.getBlockEntity(origin) == controller && level.getBlockState(origin.east()).isAir()
                    && level.getBlockState(origin.east(4)).isAir()
                    && level.getBlockState(origin.south()).isAir() && level.getBlockState(origin.south().east()).isAir()
                    && level.getBlockState(protectedPos).is(Blocks.GOLD_BLOCK)
                    && level.getBlockState(sharedPos).is(Blocks.LAPIS_BLOCK), "wrong dismantling boundary");
            require(player.getInventory().countItem(Items.OBSIDIAN) == 1
                    && player.getInventory().countItem(Items.IRON_BARS) == 2
                    && player.getInventory().items.stream().anyMatch(stack -> stack.is(RegistryBlocks.upgradeBus.get().asItem())
                    && stack.getTagElement("BlockStateTag") != null
                    && "ultimate".equals(stack.getTagElement("BlockStateTag").getString("type")))
                    && player.getInventory().items.stream().anyMatch(stack -> ItemStack.matches(stack, tagged)),
                    "dismantling did not return exact tier, material or module data");
            require(level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6)).isEmpty(),
                    "unexpected duplicate drops");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(preventBreak);
            loader.getMachines().remove(machineId);
        }
        Modularmachinery.LOG.info("[MMC][component-feedback] PASS: real survival disassembly, Forge cancellation, shared casing, tagged last-slot modules and inventory routing");
    }

    private static TileController putController(ServerLevel level, BlockPos pos, DynamicMachine machine) {
        level.setBlock(pos, RegistryBlocks.blockController.get().defaultBlockState()
                .setValue(BlockController.FACING, Direction.NORTH), 3);
        var controller = (TileController) level.getBlockEntity(pos);
        controller.setMachineName(machine.getRegistryName());
        return controller;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
