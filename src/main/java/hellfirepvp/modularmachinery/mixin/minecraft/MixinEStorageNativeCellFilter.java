/* SPDX-License-Identifier: GPL-3.0-only
 * Source NECO also excludes ES matrices from ordinary ME drives and ME chests.
 */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.common.estorage.ItemEStorageCell;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.api.storage.StorageCells", remap = false)
public abstract class MixinEStorageNativeCellFilter {
    @Inject(method = "isCellHandled", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mmc$requireESHost(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.getItem() instanceof ItemEStorageCell) callback.setReturnValue(false);
    }
}
