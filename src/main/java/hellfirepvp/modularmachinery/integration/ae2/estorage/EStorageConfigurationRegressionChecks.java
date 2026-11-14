/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.*;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.core.definitions.*;
import appeng.menu.implementations.CellWorkbenchMenu;
import appeng.menu.slot.RestrictedInputSlot;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayer;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

/** Uses the actual AE workbench, menu, partition actions and upgrade inventories. */
public final class EStorageConfigurationRegressionChecks {
    private static final IActionSource SOURCE = IActionSource.empty();
    private EStorageConfigurationRegressionChecks() { }
    private static EStorageCellInventory open(ItemStack stack) {
        return new EStorageCellInventory(((ItemEStorageCell) stack.getItem()).specification(), stack.getOrCreateTag(), null);
    }
    public static void run(ServerLevel world, BlockPos pos) {
        require(world.getBlockState(pos).isAir(), "workbench fixture not empty");
        world.setBlockAndUpdate(pos, AEBlocks.CELL_WORKBENCH.block().defaultBlockState());
        var bench = (CellWorkbenchBlockEntity) world.getBlockEntity(pos);
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EsConfigCheck"));
        var cells = bench.getSubInventory(ISegmentedInventory.CELLS);
        try {
            for (var entry : EStorageRegistration.CELLS.entrySet()) {
                var stack = new ItemStack(entry.getValue().get());
                if (!entry.getKey().backendAvailable()) {
                    require(!(stack.getItem() instanceof ICellWorkbenchItem), "unavailable gas backend exposed editable workbench"); continue;
                }
                require(stack.getItem() instanceof ICellWorkbenchItem, "matrix not editable in AE workbench");
                var item = (ICellWorkbenchItem) stack.getItem();
                require(item.getConfigInventory(stack).size() == 63, "native configuration slot count");
                AEKey key = switch (entry.getKey().kind()) {
                    case ITEM -> AEItemKey.of(Items.DIAMOND); case FLUID -> AEFluidKey.of(Fluids.WATER);
                    case GAS -> hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(0);
                };
                AEKey other = switch (entry.getKey().kind()) {
                    case ITEM -> AEItemKey.of(Items.DIRT); case FLUID -> AEFluidKey.of(Fluids.LAVA);
                    case GAS -> hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(1);
                };
                var original = open(stack); require(original.insert(key, 1200, Actionable.MODULATE, SOURCE) == 1200, "partition seed"); original.close();
                var menu = new CellWorkbenchMenu(91, player.getInventory(), bench);
                require(menu.slots.stream().filter(slot -> slot instanceof RestrictedInputSlot).findFirst().orElseThrow().mayPlace(stack), "native workbench slot rejected matrix");
                cells.setItemDirect(0, stack);
                require(bench.getCell() == item && bench.getUpgrades().size() == 2
                        && menu.isSlotEnabled(0) && menu.isSlotEnabled(1) && !menu.isSlotEnabled(2), "two upgrade slots not enabled correctly");
                require(menu.isPartitionSlotEnabled(62) && !menu.isPartitionSlotEnabled(63), "native last ghost filter slot bounds");
                menu.partition();
                require(item.getConfigInventory(stack).keySet().equals(java.util.Set.of(key)), "native partition action did not use read-only matrix contents");
                CompoundTag before = stack.getTag().copy();
                var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
                stack.getItem().appendHoverText(stack, world, tooltip, TooltipFlag.Default.NORMAL);
                require(stack.getTag().equals(before) && tooltip.stream().anyMatch(line ->
                        line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                                && text.getKey().equals("tooltip.modularmachinery.es.usage") && text.getArgs().length == 4),
                        "matrix tooltip missing usage or modified content");
                var data = open(stack);
                require(stack.getTag().equals(before), "opening configuration changed caller NBT");
                require(data.insert(other, 5, Actionable.SIMULATE, SOURCE) == 0
                        && data.insert(other, 5, Actionable.MODULATE, SOURCE) == 0
                        && data.isPreferredStorageFor(key, SOURCE) && !data.isPreferredStorageFor(other, SOURCE), "whitelist filter/routing");
                require(data.extract(key, 1200, Actionable.MODULATE, SOURCE) == 1200, "partition blocked extraction"); data.close();
                var upgrades = bench.getUpgrades();
                require(upgrades.insertItem(0, AEItems.INVERTER_CARD.stack(), false).isEmpty(), "inverter unsupported");
                require(upgrades.insertItem(1, AEItems.INVERTER_CARD.stack(), false).getCount() == 1, "duplicate inverter allowed");
                require(upgrades.insertItem(1, AEItems.VOID_CARD.stack(), false).getCount() == 1
                        && upgrades.insertItem(1, AEItems.EQUAL_DISTRIBUTION_CARD.stack(), false).getCount() == 1, "unsupported destructive/new upgrades allowed");
                data = open(stack);
                require(data.insert(key, 1, Actionable.MODULATE, SOURCE) == 0
                        && data.insert(other, 5, Actionable.MODULATE, SOURCE) == 5, "blacklist inversion failed"); data.close();
                upgrades.extractItem(0, 1, false);
                data = open(stack);
                require(data.extract(other, 5, Actionable.MODULATE, SOURCE) == 5, "changed filter trapped existing contents"); data.close();
                menu.clear();
                require(item.getConfigInventory(stack).isEmpty(), "native clear action did not clear matrix config");
                var config = item.getConfigInventory(stack);
                var wrong = entry.getKey().kind() == EStorageCellSpec.Kind.ITEM ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.DIRT);
                config.setStack(62, new GenericStack(wrong, 0));
                require(config.getKey(62) == null, "workbench accepted wrong resource channel");
                config.setStack(62, new GenericStack(key, 0));
                require(item.getConfigInventory(stack).getKey(62).equals(key), "last ghost filter not persisted");
                menu.clear();
                require(item.getFuzzyMode(stack) == FuzzyMode.IGNORE_ALL, "source default fuzzy mode");
                menu.setCellFuzzyMode(FuzzyMode.PERCENT_50);
                require(item.getFuzzyMode(stack) == FuzzyMode.PERCENT_50, "native menu fuzzy action not saved");
                stack.getOrCreateTag().putString("FuzzyMode", "not-a-mode");
                require(item.getFuzzyMode(stack) == FuzzyMode.IGNORE_ALL, "bad fuzzy mode fallback");
                item.getConfigInventory(stack).addFilter(key);
                var loaded = ItemStack.of(stack.save(new CompoundTag()));
                require(((ICellWorkbenchItem) loaded.getItem()).getConfigInventory(loaded).keySet().contains(key), "config lost on item save/reload");
                data = open(loaded); require(data.insert(other, 1, Actionable.MODULATE, SOURCE) == 0, "reloaded whitelist ignored"); data.close();
                if (entry.getKey().kind() == EStorageCellSpec.Kind.ITEM) checkFuzzy(stack);
                else {
                    require(upgrades.insertItem(1, AEItems.FUZZY_CARD.stack(), false).getCount() == 1, "fluid matrix accepted ineffective fuzzy card");
                    var tag = new CompoundTag(); tag.putInt("Variant", 1);
                    if (entry.getKey().kind() == EStorageCellSpec.Kind.FLUID) {
                        data = open(stack);
                        require(data.insert(AEFluidKey.of(Fluids.WATER, tag), 1, Actionable.MODULATE, SOURCE) == 0, "fluid filter ignored NBT"); data.close();
                    }
                }
                cells.setItemDirect(0, ItemStack.EMPTY);
                require(bench.getUpgrades().size() == 0, "removed matrix left upgrade inventory exposed");
                bench.getConfig().clear();
            }
            checkInvalidFilters();
            Modularmachinery.LOG.info("[MMC][es-config] PASS: all available matrices in native AE workbench/menu, 63 ghost filters, two upgrade slots, exact item/fluid partitions, inverter/duplicate/unsupported cards, fuzzy NBT and durability groups, no content merging, extraction after filter changes, source default/fallback, NBT conservation and malformed filter refusal");
        } finally {
            cells.setItemDirect(0, ItemStack.EMPTY);
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
    private static void checkFuzzy(ItemStack stack) {
        var item = (ICellWorkbenchItem) stack.getItem();
        var config = item.getConfigInventory(stack); config.clear();
        var first = new ItemStack(Items.STONE); first.getOrCreateTag().putInt("Variant", 1);
        var second = new ItemStack(Items.STONE); second.getOrCreateTag().putInt("Variant", 2);
        var a = AEItemKey.of(first); var b = AEItemKey.of(second);
        config.addFilter(a);
        var data = open(stack);
        require(data.insert(b, 1, Actionable.MODULATE, SOURCE) == 0, "precise item filter ignored NBT"); data.close();
        var upgrades = item.getUpgrades(stack);
        require(upgrades.insertItem(0, AEItems.FUZZY_CARD.stack(), false).isEmpty(), "fuzzy unsupported");
        require(upgrades.insertItem(1, AEItems.FUZZY_CARD.stack(), false).getCount() == 1, "duplicate fuzzy allowed");
        require(upgrades.insertItem(1, AEItems.INVERTER_CARD.stack(), false).isEmpty(), "two distinct upgrades do not fit");
        upgrades.extractItem(1, 1, false);
        item.setFuzzyMode(stack, FuzzyMode.IGNORE_ALL);
        data = open(stack);
        require(data.insert(a, 5, Actionable.MODULATE, SOURCE) == 5 && data.insert(b, 7, Actionable.MODULATE, SOURCE) == 7
                && data.storedTypes() == 2 && data.extract(a, 10, Actionable.MODULATE, SOURCE) == 5
                && data.extract(b, 10, Actionable.MODULATE, SOURCE) == 7, "fuzzy filter merged distinct resource keys"); data.close();
        config.clear();
        var tool = new ItemStack(Items.IRON_PICKAXE); tool.setDamageValue(10); config.addFilter(AEItemKey.of(tool));
        item.setFuzzyMode(stack, FuzzyMode.PERCENT_50);
        data = open(stack);
        tool.setDamageValue(20); require(data.insert(AEItemKey.of(tool), 1, Actionable.SIMULATE, SOURCE) == 1, "same durability group rejected");
        tool.setDamageValue(200); require(data.insert(AEItemKey.of(tool), 1, Actionable.SIMULATE, SOURCE) == 0, "different durability group accepted"); data.close();
        upgrades.extractItem(0, 1, false);
    }
    private static void checkInvalidFilters() {
        var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.A);
        var root = new CompoundTag();
        var list = new ListTag(); var unknown = new CompoundTag(); unknown.putString("#modid", "missing"); list.add(unknown);
        root.put("list", list);
        for (int scenario = 0; scenario < 4; scenario++) {
            if (scenario == 1) root.putString("list", "broken");
            if (scenario == 2) { list = new ListTag(); list.add(GenericStack.writeTag(new GenericStack(AEFluidKey.of(Fluids.WATER), 0))); root.put("list", list); }
            if (scenario == 3) { list = new ListTag(); for (int i = 0; i < 64; i++) list.add(new CompoundTag()); root.put("list", list); }
            var before = root.copy(); boolean rejected = false;
            try { new EStorageCellInventory(spec, root, null); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && root.equals(before), "invalid partition opened or original data changed");
        }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
