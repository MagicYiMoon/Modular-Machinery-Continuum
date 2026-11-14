/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO EStorageCell by KasumiNova and contributors, revision b37fbb53.
 */
package hellfirepvp.modularmachinery.common.estorage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** The physical matrix is registered without AE interfaces, so its saved data remains recoverable. */
public class ItemEStorageCell extends Item {
    private final EStorageCellSpec spec;
    public ItemEStorageCell(EStorageCellSpec spec) { super(new Properties().stacksTo(1)); this.spec = spec; }
    public EStorageCellSpec specification() { return spec; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("tooltip.modularmachinery.es.cell", spec.maxTypes(), spec.tier().minimumHostLevel()));
        lines.add(Component.translatable("tooltip.modularmachinery.es.insert"));
        if (spec.kind() == EStorageCellSpec.Kind.GAS && !spec.backendAvailable())
            lines.add(Component.translatable("tooltip.modularmachinery.es.gas_dependency").withStyle(net.minecraft.ChatFormatting.YELLOW));
    }
}
