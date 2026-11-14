/* SPDX-License-Identifier: GPL-3.0-only
 * NECO EStorageEnergyCellItemRenderer, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.client.world;

import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/** Vanilla item overrides replace the source ItemMeshDefinition; capacity belongs to the block tier. */
public final class EStorageEnergyItemModels {
    private EStorageEnergyItemModels() { }
    public static void register() {
        var property = new ResourceLocation("modularmachinery", "estorage_charge");
        EStorageRegistration.ENERGY_CELLS.values().forEach(entry -> {
            var block = entry.get();
            ItemProperties.register(block.asItem(), property, (stack, level, entity, seed) -> {
                double raw = stack.hasTag() ? stack.getTag().getDouble("energyStored") : 0;
                double stored = Double.isFinite(raw) ? Math.max(0, Math.min(block.capacity(), raw)) : 0;
                return BlockEStorageEnergyCell.Status.fromFraction(stored / block.capacity()).ordinal();
            });
        });
    }
}
