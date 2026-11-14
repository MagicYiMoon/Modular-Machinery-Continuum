/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.common.estorage.ItemEStorageCell;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE2 15 chests use getHandler rather than the drive's isCellHandled check. */
@Pseudo
@Mixin(targets = "appeng.blockentity.storage.ChestBlockEntity$CellInventoryFilter", remap = false)
public abstract class MixinEStorageNativeChestFilter {
    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true, remap = false)
    private void mmc$requireESHost(@Coerce Object inventory, int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.getItem() instanceof ItemEStorageCell) callback.setReturnValue(false);
    }
}
