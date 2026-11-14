/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.common.menu.MenuFluidHatch;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;

/**
 * The fluid hatch screen: a fill gauge plus amount/capacity in millibuckets, with the fluid's name.
 *
 * <p>The fluid's name comes from the registry id the menu synced back into a {@code Fluid} — see
 * {@link MenuFluidHatch} for why the identity travels as a registry id rather than through a second sync
 * path.
 */
public class ScreenFluidHatch extends ScreenGaugeBase<MenuFluidHatch> {

    private static final int COLOR_FILL = 0xFF2E6FD4;
    private static final int COLOR_EMPTY_FILL = 0xFF6E6E6E;

    public ScreenFluidHatch(MenuFluidHatch menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * The upstream texture. Both hatch screens use {@code guibar.png} — the energy gauge is at texture
     * x=196 and the fluid frame at x=176, with the fluid's own still texture drawn inside it.
     */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guibar.png");

    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        // Panel, then the gauge in upstream's exact position: a 20-wide, 61-tall well at GUI (15,10),
        // which is what GuiContainerEnergyHatch and GuiContainerFluidHatch both draw.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int gaugeX = leftPos + 15;
        int gaugeY = topPos + 10;
        float fraction = Math.max(0F, Math.min(1F, fillFraction()));
        // THE WELL'S ACTUAL INTERIOR, MEASURED FROM THE TEXTURE (PLAN section 258).
        //
        // Scanning guibar.png across the fluid well (u=176..195) gives:
        //
        //   x=176        (104,104,104)   left frame
        //   x=177..183   (113/123,..)    a 7px TICK RAIL -- where the graduation marks are
        //   x=184..194   fully transparent, 11px wide   <- the INTERIOR
        //   x=195        (104,104,104)   right frame
        //
        // and down the interior column the art runs y=0..60 with the frame at 0 and 60, so the interior is
        // 59 tall.
        //
        // The fill was drawn at gaugeX+2, 16 wide and 61 tall -- which COVERED the tick rail and spilled
        // over the frame. The client saw exactly that: the fluid was not inside its box and the scale marks
        // were not where they belong.
        int filled = (int) (59 * fraction);

        // THE FLUID WELL, NOT THE ENERGY GAUGE (PLAN section 255).
        //
        // This blitted u=196, which is the ENERGY gauge's art -- measured from the shipped guibar.png:
        // u=176 is the grey fluid well, u=196 is dark red. So the fluid hatch drew the energy bar, which is
        // exactly what the client reported ("why is it an ENERGY bar instead of the fluid's colour").
        //
        // The port's own comment above already said which column is which; the blit did not agree with it.
        // That is section 196/205's shape again -- the explanation present and the code answering a different
        // question.
        graphics.blit(TEXTURE, gaugeX, gaugeY, 176, 0, 20, 61, 256, 256);

        // THEN THE FLUID, IN THE FLUID'S OWN COLOUR (PLAN section 256).
        //
        // The first attempt bound the fluid's still SPRITE as if it were a standalone texture
        // (`graphics.blit(getStillTexture(), ...)`), and the client saw the purple-and-black missing-texture
        // checkerboard: a still texture is a sprite inside the BLOCK ATLAS, not a texture a GUI can bind.
        // Drawing it properly means resolving it through the atlas and blitting by UV, which is a larger
        // change than this round can carry.
        //
        // So the gauge is filled with the FLUID'S OWN TINT -- the colour vanilla itself multiplies the
        // still texture by -- which is what the client asked for ("should it not be the fluid's colour?").
        // The texture detail is lost; the colour, the level and the readout are not. The faithful
        // atlas-sprite version is recorded as the remaining work rather than claimed here.
        net.minecraft.world.level.material.Fluid fluid = menu.getFluid();
        if (menu.isGas() && filled > 0) {
            graphics.fill(gaugeX + 1, gaugeY + 1 + 59 - filled, gaugeX + 19,
                    gaugeY + 1 + 59, 0xFFB7D7E8);
        }
        if (filled > 0 && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
            int tint = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                    .of(fluid).getTintColor();
            // The still texture, drawn AS A SPRITE WITH ITS OWN TINT (PLAN section 257).
            //
            // The previous version filled the well with a flat rectangle in the fluid's tint: the right
            // COLOUR and the wrong surface. Upstream draws the fluid's own still texture, and a flat block
            // reads as a placeholder -- which is what the client said ("this solid fill looks abrupt").
            //
            // A still texture is a sprite inside the BLOCK ATLAS, so it must be resolved through the atlas
            // rather than bound as a texture. Binding it is the mistake that produced the missing-texture
            // checkerboard two rounds ago.
            //
            // The blit overload below was NOT guessed: the compiler rejected two other shapes and named it
            // -- blit(int x, int y, int blitOffset, int width, int height, TextureAtlasSprite, r, g, b, a).
            // It takes the sprite and an RGBA tint directly, so no shader state is touched.
            net.minecraft.resources.ResourceLocation still =
                    net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                            .of(fluid).getStillTexture();
            if (still != null) {
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                        net.minecraft.client.Minecraft.getInstance()
                                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                                .apply(still);
                // The well's interior is 16 wide, inset 2 from the 20-wide frame, drawn bottom-up.
                // FULL inner width, then the scale rail drawn OVER it (PLAN section 259).
                //
                // The previous version filled only x=184..194 -- the part of the well to the RIGHT of the
                // tick rail. The client saw the result immediately and said what was wrong with it: the
                // left looked emptied out. Upstream draws the fluid across the WHOLE well and then puts the
                // graduation marks on top, which is exactly what the client remembered ("刻度线在上").
                //
                // The interior between the 1px frames is x=177..194, so 18 wide starting one pixel in.
                graphics.blit(gaugeX + 1, gaugeY + 1 + 59 - filled, 0, 18, filled, sprite,
                        ((tint >> 16) & 0xFF) / 255.0F,
                        ((tint >> 8) & 0xFF) / 255.0F,
                        (tint & 0xFF) / 255.0F,
                        1.0F);
            }
        }

        // And the scale rail back ON TOP (PLAN section 259), so the graduation marks read OVER the fluid
        // rather than the fluid being squeezed in beside them. The rail is the 7-pixel band at u=177..183,
        // drawn over the well's full 59-pixel height. This blit is the same kind the frame used, so it needs
        // no shader state and no tint -- it is the art as authored.
        graphics.blit(TEXTURE, gaugeX + 1, gaugeY + 1, 177, 1, 7, 59, 256, 256);
    }

    @Override
    protected float fillFraction() {
        return menu.getFillFraction();
    }

    /**
     * @return the menu's own panel size (§193). This screen states no size of its own, so it cannot disagree
     *         with the menu that places its slots.
     */
    @Override
    protected int[] panelSize() {
        return new int[]{hellfirepvp.modularmachinery.common.menu.MenuFluidHatch.PANEL_WIDTH,
                hellfirepvp.modularmachinery.common.menu.MenuFluidHatch.PANEL_HEIGHT};
    }

    @Override
    protected int gaugeColor() {
        // An empty tank uses a neutral grey rather than the water-blue: a blue sliver on an empty tank
        // reads as "a little water" when it means "none".
        return menu.getFluid() == Fluids.EMPTY ? COLOR_EMPTY_FILL : COLOR_FILL;
    }

    @Override
    protected net.minecraft.network.chat.Component readout() {
        // "mB", " (empty)" and the fluid's own name are all inside translatable strings now. This body
        // previously concatenated them onto a String, which is how " (empty)" became hard-coded English.
        if (menu.isGas()) {
            return Component.translatable("gui.modularmachinery.hatch.gas",
                    menu.getAmount(), menu.getCapacity());
        }
        if (menu.getFluid() == Fluids.EMPTY) {
            return Component.translatable("gui.modularmachinery.hatch.fluid.empty",
                    menu.getAmount(), menu.getCapacity());
        }
        return Component.translatable("gui.modularmachinery.hatch.fluid",
                menu.getAmount(), menu.getCapacity(),
                Component.translatable(menu.getFluid().getFluidType().getDescriptionId()));
    }
}
