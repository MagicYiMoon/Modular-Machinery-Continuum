/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.client.screen.ScreenBlueprint;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.gui.screens.Screen;
import java.util.Optional;

/** Native JEI focus supports user-configured recipe, use and bookmark keys on every preview slot. */
final class BlueprintScreenHandler implements IScreenHandler<ScreenBlueprint> {
    @Override public IGuiProperties apply(ScreenBlueprint screen) {
        return new Properties(screen.getGuiLeft(), screen.getGuiTop(), screen.getGuiWidth(), screen.getGuiHeight(),
                screen.width, screen.height);
    }

    @Override public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory factory, ScreenBlueprint screen, double x, double y) {
        var slot = screen.ingredientAt(x, y);
        return slot == null ? Optional.empty() : factory.createBuilder(slot.stack().copyWithCount(1)).buildWithArea(slot.area());
    }

    private record Properties(int getGuiLeft, int getGuiTop, int getGuiXSize, int getGuiYSize,
                              int getScreenWidth, int getScreenHeight) implements IGuiProperties {
        @Override public Class<? extends Screen> getScreenClass() { return ScreenBlueprint.class; }
    }
}
