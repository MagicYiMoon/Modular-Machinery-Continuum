/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Font ids are resource-pack replaceable; explicit third-party Component fonts remain intact. */
public final class SmoothUiFonts {
    public static final ResourceLocation UI = new ResourceLocation("modularmachinery", "ui");
    public static final ResourceLocation COMPACT = new ResourceLocation("modularmachinery", "ui_compact");
    private SmoothUiFonts() { }
    public static boolean inModScreen() {
        var screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().startsWith("hellfirepvp.modularmachinery.");
    }
    public static Component compact(Component text) { return text.copy().withStyle(style -> style.withFont(COMPACT)); }
}
