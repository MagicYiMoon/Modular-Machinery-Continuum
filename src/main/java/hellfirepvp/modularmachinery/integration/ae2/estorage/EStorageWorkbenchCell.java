/* SPDX-License-Identifier: GPL-3.0-only
 * NECO EStorageCell workbench contract, KasumiNova and contributors, b37fbb53.
 * Adapted to AE2 15's optional workbench and upgrade interfaces.
 */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.*;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.world.item.ItemStack;

/** Editable in AE's existing cell workbench, still rejected by native drives/chests. */
public final class EStorageWorkbenchCell extends ItemEStorageCell implements ICellWorkbenchItem {
    public EStorageWorkbenchCell(EStorageCellSpec spec) { super(spec); }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level world,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flags) {
        super.appendHoverText(stack, world, lines, flags);
        try {
            var data = new EStorageCellInventory(specification(), stack.hasTag() ? stack.getTag() : new net.minecraft.nbt.CompoundTag(), null);
            var numbers = java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT);
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.modularmachinery.es.usage",
                    numbers.format(data.usedBytes()), numbers.format(specification().totalBytes()), data.storedTypes(), specification().maxTypes()));
            lines.add(net.minecraft.network.chat.Component.translatable(data.isInverted()
                    ? "tooltip.modularmachinery.es.blacklist" : "tooltip.modularmachinery.es.whitelist", data.filterTypes()));
        } catch (IllegalArgumentException invalid) {
            lines.add(net.minecraft.network.chat.Component.translatable("tooltip.modularmachinery.es.invalid")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.modularmachinery.es.workbench"));
    }
    @Override public IUpgradeInventory getUpgrades(ItemStack stack) { return UpgradeInventories.forItem(stack, 2); }
    @Override public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(key -> EStorageKeySupport.accepts(specification(), key), stack);
    }
    @Override public FuzzyMode getFuzzyMode(ItemStack stack) {
        return EStorageCellConfiguration.fuzzyMode(stack.getTag());
    }
    @Override public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        stack.getOrCreateTag().putString("FuzzyMode", mode.name());
    }
}
