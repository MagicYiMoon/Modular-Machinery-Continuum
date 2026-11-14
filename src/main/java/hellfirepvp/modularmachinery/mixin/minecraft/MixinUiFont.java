/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolve measuring and drawing through the same font, including native buttons and edit boxes. */
@Mixin(Font.class)
public abstract class MixinUiFont {
    @ModifyVariable(method = "getFontSet", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation modularmachinery$smoothUi(ResourceLocation requested) {
        return Style.DEFAULT_FONT.equals(requested) && SmoothUiFonts.inModScreen() ? SmoothUiFonts.UI : requested;
    }
}
