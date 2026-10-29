/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.upgrade.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Opt-in real inventory checks for multiple upgrade types attached to one item, including legacy migration. */
public final class MultiDynamicUpgradeRegressionChecks {
    public static final String TARGET = "mmc_multi_dynamic_regression";
    public static final String A = TARGET + "_a", B = TARGET + "_b", FIXED = TARGET + "_static";
    private MultiDynamicUpgradeRegressionChecks() { }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines(); var previousMachines = new LinkedHashMap<>(machines);
        var field = RegistryMachineUpgrade.class.getDeclaredField("ITEMS"); field.setAccessible(true); Map items = (Map) field.get(null);
        field = RegistryMachineUpgrade.class.getDeclaredField("PROTOTYPES"); field.setAccessible(true); Map prototypes = (Map) field.get(null);
        var previousItems = new LinkedHashMap(items); var previousPrototypes = new LinkedHashMap(prototypes);
        var a = new SimpleDynamicMachineUpgrade(new UpgradeType(A, A, 1, 1));
        var b = new SimpleDynamicMachineUpgrade(new UpgradeType(B, B, 1, 1));
        var fixed = SimpleMachineUpgrade.of(new UpgradeType(FIXED, FIXED, 1, 16));
        var calls = new int[2];
        a.addEventHandler(MachineTickEvent.class, (event, upgrade) -> { calls[0]++; tick((DynamicMachineUpgrade) upgrade, "a_tick", 2); });
        b.addEventHandler(MachineTickEvent.class, (event, upgrade) -> { calls[1]++; tick((DynamicMachineUpgrade) upgrade, "b_tick", 3); });
        try {
            items.remove(Items.DIAMOND_PICKAXE); items.remove(Items.GOLDEN_PICKAXE); items.remove(Items.AMETHYST_SHARD);
            for (MachineUpgrade type : List.of(a, b, fixed)) RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.DIAMOND_PICKAXE), type);
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.AMETHYST_SHARD), fixed);
            RegistryMachineUpgrade.addSupportedItem(new ItemStack(Items.GOLDEN_PICKAXE));
            for (boolean factory : List.of(false, true)) {
                Arrays.fill(calls, 0); var pos = origin.offset(factory ? 32 : 0, 0, 0);
                var pattern = new MachinePattern();
                for (int x = 1; x <= 2; x++) pattern.addPart(new MachinePattern.Part(x, 0, 0, "modularmachinery:blockupgradebus"));
                var machine = new DynamicMachine(TARGET, TARGET, pattern);
                machines.put(new ResourceLocation("modularmachinery", TARGET), machine);
                for (int x = 0; x <= 2; x++) {
                    var at = pos.east(x); saved.put(at, level.getBlockState(at));
                    level.setBlock(at, (x == 0 ? (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get())
                            : RegistryBlocks.upgradeBus.get()).defaultBlockState(), Block.UPDATE_ALL);
                }
                var controller = (TileController) level.getBlockEntity(pos); controller.setMachineName(TARGET);
                require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "multi-upgrade fixture failed formation");
                controller.setFormed(true);
                var bus = (TileUpgradeBus) level.getBlockEntity(pos.east()); var other = (TileUpgradeBus) level.getBlockEntity(pos.east(2));
                ItemStack legacy = new ItemStack(Items.DIAMOND_PICKAXE);
                legacy.getOrCreateTag().putString("pack_marker", "keep");
                var oldData = new CompoundTag(); oldData.putInt("legacy", 7); legacy.getTag().put(DynamicMachineUpgrade.ITEM_DATA_TAG, oldData);
                inventory(bus).setStackInSlot(0, legacy);
                ItemStack typed = new ItemStack(Items.DIAMOND_PICKAXE); var map = new CompoundTag(); var alpha = new CompoundTag(); alpha.putInt("only_a", 8);
                map.put(A, alpha); var unknown = new CompoundTag(); unknown.putInt("future", 9); map.put("unknown_type", unknown);
                typed.getOrCreateTag().put(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG, map); typed.getTag().put(DynamicMachineUpgrade.ITEM_DATA_TAG, oldData.copy());
                inventory(bus).setStackInSlot(1, typed);
                ItemStack supported = RegistryMachineUpgrade.addUpgradeToStack(new ItemStack(Items.GOLDEN_PICKAXE), A);
                supported = RegistryMachineUpgrade.addUpgradeToStack(supported, B); inventory(other).setStackInSlot(0, supported);
                inventory(other).setStackInSlot(1, new ItemStack(Items.AMETHYST_SHARD, 2));
                inventory(other).setStackInSlot(2, new ItemStack(Items.AMETHYST_SHARD, 3));
                check(controller, bus, other, prototypes, b, calls);
                if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
                    hellfirepvp.modularmachinery.integration.crafttweaker.MultiDynamicUpgradeScriptRegressionChecks.run(controller, bus);
            }
            Modularmachinery.LOG.info("[MMC][multi-dynamic] PASS: ordinary/factory typed data, legacy migration, current-slot wear, all identities, static mixed effects, reload, removal and sibling lifecycle");
        } finally {
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(previousMachines); items.clear(); items.putAll(previousItems); prototypes.clear(); prototypes.putAll(previousPrototypes);
            MachineReloadListener.refreshRecipes();
        }
    }
    private static void tick(DynamicMachineUpgrade upgrade, String key, int wear) {
        var data = upgrade.getItemDataTag(); data.putInt(key, data.getInt(key) + 1); upgrade.setItemDataTag(data); upgrade.decrementItemDurability(wear);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void check(TileController controller, TileUpgradeBus bus, TileUpgradeBus other, Map prototypes,
                              MachineUpgrade bPrototype, int[] calls) throws Exception {
        var a = query(controller, A, 0); var b = query(controller, B, 0);
        require(controller.getMachineUpgrade(A).length == 3 && controller.getMachineUpgrade(B).length == 3
                && a.getItemDataTag().getInt("legacy") == 7 && b.getItemDataTag().getInt("legacy") == 7,
                "flat legacy data did not initialize both types independently");
        require(query(controller, A, 1).getItemDataTag().getInt("only_a") == 8 && query(controller, B, 1).getItemDataTag().isEmpty(),
                "typed data fell back to the unrelated flat legacy root");
        int revision = bus.getUpgradeRevision(); a.getParentStack(); a.setItemDataTag(a.getItemDataTag());
        require(bus.getUpgradeRevision() == revision, "reads/equal writes changed inventory or migrated data");
        var alpha = a.getItemDataTag(); alpha.putInt("value", 10); a.setItemDataTag(alpha);
        var beta = b.getItemDataTag(); beta.putInt("value", 20); b.setItemDataTag(beta);
        a.decrementItemDurability(2); b.decrementItemDurability(3);
        alpha.putInt("value", 11); a.setItemDataTag(alpha); b.decrementItemDurability(4);
        var stack = inventory(bus).getStackInSlot(0); var data = stack.getTag().getCompound(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG);
        require(stack.getDamageValue() == 9 && a.getParentStack().getDamageValue() == 9 && b.getParentStack().getDamageValue() == 9
                && data.getCompound(A).getInt("value") == 11 && data.getCompound(B).getInt("value") == 20
                && stack.getTag().getString("pack_marker").equals("keep"), "sequential writes overwrote sibling data, tags or current durability");
        new MachineTickEvent(controller, Phase.START).postEvent();
        require(calls[0] == 3 && calls[1] == 3 && inventory(bus).getStackInSlot(0).getDamageValue() == 14
                && inventory(bus).getStackInSlot(1).getDamageValue() == 5 && inventory(other).getStackInSlot(0).getDamageValue() == 5,
                "multiple upgrade callbacks did not all consume the current item's durability");
        require(query(controller, A, 0).getItemDataTag().getInt("a_tick") == 1 && !query(controller, B, 0).getItemDataTag().contains("a_tick")
                && query(controller, B, 0).getItemDataTag().getInt("b_tick") == 1, "callback itemData crossed types");
        require(inventory(bus).getStackInSlot(1).getTag().getCompound(DynamicMachineUpgrade.ITEM_DATA_BY_TYPE_TAG).getCompound("unknown_type").getInt("future") == 9,
                "write dropped an inactive type's saved data");
        var saved = bus.saveWithoutMetadata(); bus.load(saved); a.decrementItemDurability(90); b.setItemDataTag(new CompoundTag());
        require(query(controller, A, 0).getItemDataTag().getInt("value") == 11 && query(controller, B, 0).getItemDataTag().getInt("value") == 20
                && inventory(bus).getStackInSlot(0).getDamageValue() == 14, "reload lost typed data or accepted old views");
        var worn = inventory(bus).getStackInSlot(0).copy();
        require(RegistryMachineUpgrade.upgradesFor(worn).size() == 3 && controller.getMachineUpgrade(FIXED)[0].getStackSize() == 7,
                "wear/reload lost a sibling fixed binding or the static effect");
        inventory(bus).setStackInSlot(0, ItemStack.EMPTY); inventory(bus).setStackInSlot(0, worn);
        prototypes.remove(B); bus.refreshUpgrades();
        require(bus.getInstalledUpgrades().stream().noneMatch(upgrade -> B.equals(upgrade.getType().getName())), "removed type still resolved from worn item");
        prototypes.put(B, bPrototype); bus.refreshUpgrades();
        require(query(controller, B, 0).getItemDataTag().getInt("value") == 20, "restoring a type lost its saved item data");
        var retained = query(controller, A, 0); var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> { try { retained.decrementItemDurability(90); retained.getParentStack();
            require(controller.getMachineUpgrade(A).length == 0, "async query traversed world");
        } catch (Throwable failure) { error.set(failure); } }); worker.start(); worker.join();
        require(error.get() == null && inventory(bus).getStackInSlot(0).getDamageValue() == 14, "async wear/read guard failed");
        var broken = inventory(bus).getStackInSlot(0).copy(); broken.setDamageValue(broken.getMaxDamage() - 1); inventory(bus).setStackInSlot(0, broken);
        var brokenA = query(controller, A, 0); var brokenB = query(controller, B, 0);
        int aBefore = calls[0], bBefore = calls[1], otherBefore = inventory(other).getStackInSlot(0).getDamageValue();
        new MachineTickEvent(controller, Phase.START).postEvent();
        require(inventory(bus).getStackInSlot(0).isEmpty() && !brokenA.isValid() && !brokenB.isValid()
                && calls[0] == aBefore + 3 && calls[1] == bBefore + 2
                && inventory(bus).getStackInSlot(1).getDamageValue() == 10 && inventory(other).getStackInSlot(0).getDamageValue() == otherBefore + 5
                && controller.getMachineUpgrade(FIXED)[0].getStackSize() == 6, "broken mixed item kept effects, ran a stale sibling, or invalidated another slot");
        brokenB.decrementItemDurability(1); brokenA.setItemDataTag(new CompoundTag()); require(inventory(bus).getStackInSlot(0).isEmpty(), "broken slot resurrected");
        inventory(bus).setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
        brokenB.setItemDataTag(beta); require(query(controller, B, 0).getItemDataTag().isEmpty(), "old type view mutated replacement item");
    }
    private static SimpleDynamicMachineUpgrade query(TileController controller, String type, int index) {
        var found = controller.getMachineUpgrade(type);
        require(index < found.length, "missing multi-upgrade provider: " + type + " at " + index);
        return (SimpleDynamicMachineUpgrade) found[index];
    }
    private static IItemHandlerModifiable inventory(TileUpgradeBus bus) { return (IItemHandlerModifiable) bus.getUpgradeInventory(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
