/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.modifier.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.upgrade.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Real component queries, source upgrade identity and authoritative bus/item data edits. Opt-in only. */
public final class UpgradeRuntimeRegressionChecks {
    public static final String TARGET = "mmc_upgrade_runtime_regression";
    public static final String STATIC = TARGET + "_static", DYNAMIC = TARGET + "_dynamic";
    private UpgradeRuntimeRegressionChecks() { }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines(); var oldMachines = new LinkedHashMap<>(machines);
        var itemField = RegistryMachineUpgrade.class.getDeclaredField("ITEMS"); itemField.setAccessible(true);
        var prototypeField = RegistryMachineUpgrade.class.getDeclaredField("PROTOTYPES"); prototypeField.setAccessible(true);
        Map items = (Map) itemField.get(null), prototypes = (Map) prototypeField.get(null);
        var oldItems = new LinkedHashMap(items); var oldPrototypes = new LinkedHashMap(prototypes);
        var staticUpgrade = SimpleMachineUpgrade.of(new UpgradeType(STATIC, STATIC, 1, 3));
        var dynamicUpgrade = new SimpleDynamicMachineUpgrade(new UpgradeType(DYNAMIC, DYNAMIC, 1, 1));
        var denied = SimpleMachineUpgrade.of(new UpgradeType(TARGET + "_denied", "Denied", 1, 1));
        denied.getType().addCompatibleMachine("another_machine");
        var calls = new int[2];
        staticUpgrade.addEventHandler(MachineTickEvent.class, (event, upgrade) -> {
            calls[0]++; var data = upgrade.getCustomData(); data.putInt("events", data.getInt("events") + 1); upgrade.setCustomData(data);
        });
        dynamicUpgrade.addEventHandler(MachineTickEvent.class, (event, upgrade) -> {
            calls[1]++; ((DynamicMachineUpgrade) upgrade).decrementItemDurability(2);
        });
        try {
            // Own each fixture binding, without clearing other script registrations.
            for (Item item : List.of(Items.AMETHYST_SHARD, Items.IRON_PICKAXE, Items.GOLD_NUGGET)) items.remove(item);
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.AMETHYST_SHARD), staticUpgrade);
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.IRON_PICKAXE), dynamicUpgrade);
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.GOLD_NUGGET), denied);
            for (boolean factory : List.of(false, true)) {
                Arrays.fill(calls, 0); var pos = origin.offset(factory ? 32 : 0, 0, 0);
                var pattern = new MachinePattern();
                for (int x = 1; x <= 3; x++) pattern.addPart(new MachinePattern.Part(x, 0, 0, "modularmachinery:blockupgradebus"));
                var machine = new DynamicMachine(TARGET, TARGET, pattern);
                var rule = new RecipeModifier("modularmachinery:item", IOType.OUTPUT, 2F, 1, false);
                var json = com.google.gson.JsonParser.parseString("""
                        {"elements":"minecraft:gold_block","x":1,"y":1,"z":0,
                         "modifier":{"target":"modularmachinery:item","io":"output","operation":1,"multiplier":2}}
                        """).getAsJsonObject();
                var single = ModifierReplacement.deserialize(json); machine.setModifierReplacements(List.of(single));
                machine.addMultiBlockModifierReplacement(new MultiBlockModifierReplacement("gold_pair", List.of(
                        new MultiBlockModifierReplacement.Cell(new BlockPos(1, 1, 0), "minecraft:gold_block"),
                        new MultiBlockModifierReplacement.Cell(new BlockPos(2, 1, 0), "minecraft:gold_block")), List.of(rule), List.of()));
                machines.put(new ResourceLocation("modularmachinery", TARGET), machine);
                for (int x = 0; x <= 3; x++) {
                    var at = pos.offset(x, 0, 0); saved.put(at, level.getBlockState(at));
                    level.setBlock(at, (x == 0 ? (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get())
                            : RegistryBlocks.upgradeBus.get()).defaultBlockState(), Block.UPDATE_ALL);
                }
                for (int x = 1; x <= 2; x++) { var at = pos.offset(x, 1, 0); saved.put(at, level.getBlockState(at)); level.setBlock(at, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL); }
                var tile = (TileController) level.getBlockEntity(pos); tile.setMachineName(TARGET);
                require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "upgrade fixture failed real formation");
                tile.setFormed(true);
                var a = (TileUpgradeBus) level.getBlockEntity(pos.east()); var b = (TileUpgradeBus) level.getBlockEntity(pos.east(2));
                var c = (TileUpgradeBus) level.getBlockEntity(pos.east(3));
                inventory(a).setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD, 2));
                inventory(a).setStackInSlot(1, new ItemStack(Items.GOLD_NUGGET));
                inventory(b).setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD, 3));
                inventory(c).setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE)); inventory(c).setStackInSlot(1, new ItemStack(Items.IRON_PICKAXE));
                check(level, tile, machine, single.getName(), a, b, c, calls);
                if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
                    hellfirepvp.modularmachinery.integration.crafttweaker.UpgradeRuntimeScriptRegressionChecks.run(tile, a, c);
            }
            Modularmachinery.LOG.info("[MMC][upgrade-runtime] PASS: ordinary/factory replacements, capped static counts, per-slot dynamic identity, data/item persistence, events and stale/thread guards");
        } finally {
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(oldMachines); items.clear(); items.putAll(oldItems); prototypes.clear(); prototypes.putAll(oldPrototypes);
            MachineReloadListener.refreshRecipes();
        }
    }
    private static void check(ServerLevel level, TileController tile, DynamicMachine machine, String single,
                              TileUpgradeBus a, TileUpgradeBus b, TileUpgradeBus c, int[] calls) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            var context = new MachineContext(level, tile.getBlockPos(), tile.getFacing(), machine, new MachineVariables(), machine.getPattern());
            var snapshot = MachineTicker.upgradeSnapshotOf(context);
            require(snapshot.stream().filter(upgrade -> STATIC.equals(upgrade.getType().getName())).findFirst().orElseThrow().getStackSize() == 3
                    && snapshot.stream().filter(upgrade -> DYNAMIC.equals(upgrade.getType().getName())).count() == 2
                    && a.getInstalledUpgrade(STATIC).getStackSize() == 2 && b.getInstalledUpgrade(STATIC).getStackSize() == 3,
                    "real crafting snapshot mutated bus counts or merged dynamic slots");
        }
        var stackB = inventory(b).getStackInSlot(0).copy(); inventory(b).setStackInSlot(0, ItemStack.EMPTY);
        var withoutB = new MachineContext(level, tile.getBlockPos(), tile.getFacing(), machine, new MachineVariables(), machine.getPattern());
        require(MachineTicker.upgradeSnapshotOf(withoutB).stream().filter(upgrade -> STATIC.equals(upgrade.getType().getName()))
                .findFirst().orElseThrow().getStackSize() == 2, "removing another bus left an inflated stack count");
        inventory(b).setStackInSlot(0, stackB);
        require(Arrays.equals(tile.getFoundModifierReplacements(), new String[]{single, "gold_pair"})
                && tile.hasModifierReplacement(single) && tile.hasModifierReplacement("gold_pair")
                && !tile.hasModifierReplacement("missing") && !tile.hasModifierReplacement(null), "structure query missed conditions");
        var gold = tile.getBlockPos().offset(2, 1, 0); level.setBlock(gold, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        require(tile.hasModifierReplacement(single) && !tile.hasModifierReplacement("gold_pair"), "replacement query ignored a missing cell");
        level.setBlock(gold, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        var all = tile.getMachineUpgrade(STATIC); require(all.length == 1 && all[0].getStackSize() == 3
                && tile.hasMachineUpgrade(STATIC) && !tile.hasMachineUpgrade(TARGET + "_denied")
                && tile.getMachineUpgrade("missing").length == 0 && tile.getMachineUpgrade(null).length == 0, "static cap/filter/empty query failed");
        var staticView = (SimpleMachineUpgrade) all[0]; all[0] = null;
        require(a.getInstalledUpgrade(STATIC).getStackSize() == 2 && b.getInstalledUpgrade(STATIC).getStackSize() == 3
                && tile.getMachineUpgrade(STATIC)[0] != null, "query leaked installed counts/array");
        var custom = staticView.getCustomData(); custom.putInt("count", 8); staticView.setCustomData(custom);
        require(a.getUpgradeCustomData(STATIC).getInt("count") == 8 && b.getUpgradeCustomData(STATIC).getInt("count") == 0,
                "custom data failed writeback or broadcast to another bus");
        custom.putInt("unassigned", 1);
        require(!a.getUpgradeCustomData(STATIC).contains("unassigned"), "input tag leaked into bus data");
        staticView.setCustomData(new CompoundTag());
        var dynamic = tile.getMachineUpgrade(DYNAMIC); require(dynamic.length == 2, "dynamic slots were merged by type");
        var first = (SimpleDynamicMachineUpgrade) dynamic[0]; var second = (SimpleDynamicMachineUpgrade) dynamic[1];
        var itemData = first.getItemDataTag(); itemData.putInt("mode", 6); first.setItemDataTag(itemData);
        require(inventory(c).getStackInSlot(0).getTag().getCompound(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG).getCompound(DYNAMIC).getInt("mode") == 6
                && !second.getItemDataTag().contains("mode"), "item data crossed source slots or did not save");
        new MachineTickEvent(tile, Phase.START).postEvent();
        require(calls[0] == 1 && calls[1] == 2 && a.getUpgradeCustomData(STATIC).getInt("events") == 1
                && inventory(c).getStackInSlot(0).getDamageValue() == 2 && inventory(c).getStackInSlot(1).getDamageValue() == 2,
                "upgrade events lost bus data, counts or dynamic slot/durability");
        var retained = (SimpleDynamicMachineUpgrade) tile.getMachineUpgrade(DYNAMIC)[0];
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> { try { retained.decrementItemDurability(99);
            require(tile.getMachineUpgrade(STATIC).length == 0 && tile.getFoundModifierReplacements().length == 0, "async query traversed world");
        } catch (Throwable problem) { failure.set(problem); } }); worker.start(); worker.join();
        require(failure.get() == null && inventory(c).getStackInSlot(0).getDamageValue() == 2, "async runtime mutation was accepted");
        tile.setFormed(false); retained.decrementItemDurability(99); require(tile.getMachineUpgrade(DYNAMIC).length == 0
                && inventory(c).getStackInSlot(0).getDamageValue() == 2, "unformed query/write guard failed"); tile.setFormed(true);
        c.setControllerPos(tile.getBlockPos().north(20)); retained.decrementItemDurability(99);
        require(tile.getMachineUpgrade(DYNAMIC).length == 0 && inventory(c).getStackInSlot(0).getDamageValue() == 2, "foreign bus remained reachable"); c.setControllerPos(tile.getBlockPos());
        var saved = c.saveWithoutMetadata(); c.load(saved); retained.decrementItemDurability(99);
        require(inventory(c).getStackInSlot(0).getDamageValue() == 2 && ((DynamicMachineUpgrade) tile.getMachineUpgrade(DYNAMIC)[0]).getItemDataTag().getInt("mode") == 6,
                "reload lost item data or stale view mutated replacement");
        var worn = inventory(c).getStackInSlot(0).copy();
        require(RegistryMachineUpgrade.supportsUpgrade(worn) && RegistryMachineUpgrade.upgradesFor(worn).size() == 1,
                "worn dynamic item lost its registered identity or duplicated its fixed binding");
        inventory(c).setStackInSlot(0, ItemStack.EMPTY); inventory(c).setStackInSlot(0, worn);
        var current = (SimpleDynamicMachineUpgrade) tile.getMachineUpgrade(DYNAMIC)[0];
        var oldName = tile.getMachineName(); tile.setMachineName(oldName + "_other"); current.decrementItemDurability(99);
        require(inventory(c).getStackInSlot(0).getDamageValue() == 2, "old controller binding remained writable"); tile.setMachineName(oldName);
        tile.setRemoved(); current.decrementItemDurability(99);
        require(inventory(c).getStackInSlot(0).getDamageValue() == 2 && tile.getMachineUpgrade(DYNAMIC).length == 0,
                "removed controller retained runtime authority"); tile.clearRemoved();
        var recorded = tile.getBoundPositions(); tile.setBoundPositions(List.of(a.getBlockPos(), b.getBlockPos())); current.decrementItemDurability(99);
        require(inventory(c).getStackInSlot(0).getDamageValue() == 2 && tile.getMachineUpgrade(DYNAMIC).length == 0,
                "unrecorded bus retained runtime authority"); tile.setBoundPositions(recorded);
        staticView.setCustomData(new CompoundTag()); var disk = a.saveWithoutMetadata(); a.load(disk); staticView.setCustomData(saved);
        require(a.getUpgradeCustomData(STATIC).isEmpty(), "pre-load static view changed replacement");
        var bound = tile.getBoundPositions(); var remote = new BlockPos(2_000_000, tile.getBlockPos().getY(), 2_000_000);
        require(!level.hasChunkAt(remote), "remote fixture already loaded"); var expanded = new ArrayList<>(bound); expanded.add(remote); tile.setBoundPositions(expanded);
        require(tile.getMachineUpgrade(STATIC).length == 1 && !level.hasChunkAt(remote), "query loaded a remote chunk"); tile.setBoundPositions(bound);
        MachineReloadListener.refreshRecipes(); require(tile.hasMachineUpgrade(STATIC), "definition refresh lost installed upgrade");
        var removed = (SimpleDynamicMachineUpgrade) tile.getMachineUpgrade(DYNAMIC)[1]; inventory(c).setStackInSlot(1, ItemStack.EMPTY);
        removed.decrementItemDurability(1); var removedData = new CompoundTag(); removedData.putInt("stale", 1); removed.setItemDataTag(removedData);
        require(tile.getMachineUpgrade(DYNAMIC).length == 1 && inventory(c).getStackInSlot(1).isEmpty(), "removed item view recreated or damaged another slot");
        inventory(c).setStackInSlot(1, new ItemStack(Items.IRON_PICKAXE));
        ((DynamicMachineUpgrade) tile.getMachineUpgrade(DYNAMIC)[1]).decrementItemDurability(Integer.MAX_VALUE);
        require(inventory(c).getStackInSlot(1).isEmpty() && tile.getMachineUpgrade(DYNAMIC).length == 1, "durability overflow failed to remove broken item");
        inventory(c).setStackInSlot(1, new ItemStack(Items.IRON_PICKAXE));
    }
    private static IItemHandlerModifiable inventory(TileUpgradeBus bus) { return (IItemHandlerModifiable) bus.getUpgradeInventory(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
