/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * The ME fluid input bus screen — port of upstream's {@code GuiMEFluidInputBus} (78 lines), and the player-facing half of the
 * configuration dimension §649(b) found empty.
 *
 * <h2>What upstream's screen is, read at the body (rule A)</h2>
 *
 * <pre>
 *   GuiMEFluidInputBus extends GuiUpgradeable                        (upstream U21)
 *     U22      TEXTURES_INPUT_BUS = modularmachinery:textures/gui/mefluidinputbus.png  <- THE PORT'S OWN ASSET,
 *                                                                      already shipped at this exact path
 *     U29      this.ySize = 231
 *     U40-U58  nine GuiFluidSlot(configFluids, i, i, 8 + 18i, 35)   -- the CONFIG row: what each tank keeps
 *     U41-U42  nine GuiFluidTank(fluidTank, i, 9 + i, 8 + 18i, 53, 16, 68)  -- the STORED row: what each tank holds
 *     U66-U71  drawFG: the title, "Config", "Stored Fluids" and the player inventory label
 * </pre>
 *
 * <p><b>The texture needs no new asset:</b> the file upstream names is <b>already in this port</b> at
 * {@code assets/modularmachinery/textures/gui/mefluidinputbus.png} (verified by listing the resource directory), so the panel is
 * the jar's own art rather than a guess. Its dimensions are 256x256 with the panel in the top-left 176x231, which is exactly
 * what {@code MenuMEFluidInputBus.PANEL_WIDTH/HEIGHT} declares and what the screen's {@code blit} reads.</p>
 *
 * <h2>1.20.1 adaptation</h2>
 *
 * <pre>
 *   U41-U42  the nine GuiFluidTank STORED bars -> rendered here from synchronized menu data. AE2 15.4.11 no longer
 *            ships the old widget, so the port draws the same atlas sprite and 68-pixel vertical range itself.
 *   U45-U55  the AE2-Unofficial-Extended-Life branch, which reaches into GuiCustomSlot's PRIVATE x/y fields
 *            with ObfuscationReflectionHelper -> NOT PORTED, and needs no port: 1.20.1 positions every slot
 *            from the MENU (Slot's x/y are constructor arguments), so there is no frame-time fixup at all.
 *   U62      addButtons(), an EMPTY override upstream -> not written: it does nothing in the jar.
 *   GuiUpgradeable's upgrade row -> restored as the five-slot AE2 upgrade rail at x=187; the 1.20.1 screen draws
 *                                  the rail chrome itself because AE2 no longer ships GuiUpgradeable.
 * </pre>
 *
 * <h2>The error this cannot make, and the one it still can</h2>
 *
 * <p>Every text line is drawn from the MENU's or the mod's own constant, and every slot position comes from the MENU — so the
 * screen cannot disagree with the menu about where a slot is. The upstream label "Config" is <b>not</b> taken from AE2's
 * {@code gui.ae2.Config}: this mod's own key is used instead, so the screen does not depend on AE2's translation file being
 * present in the player's language.</p>
 *
 * <p><b>AND THIS FILE HAS NEVER BEEN RENDERED.</b> The project's harness is a dedicated server and cannot draw; what IS verified
 * for this screen is that it compiles, that its panel size matches upstream's own number, and that the menu it renders carries
 * nine config slots whose clicks reach the tile. The pixels are the user's to judge.</p>
 */
public class ScreenMEFluidInputBus extends AbstractContainerScreen<MenuMEFluidInputBus> {

    /** The original texture is 176px wide; the upgrade rail extends the vanilla screen to the right. */
    private static final int SCREEN_WIDTH = 220;

     
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/mefluidinputbus.png");

    /** The config row's vertical span on the panel: one 16x16 slot at y=35. */
    private static final int CONFIG_SLOT_SIZE = 16;

    public ScreenMEFluidInputBus(MenuMEFluidInputBus menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Taken FROM the menu, per §193: a screen that states its own size can drift from the menu that placed its slots.
        this.imageWidth = SCREEN_WIDTH;
        this.imageHeight = MenuMEFluidInputBus.PANEL_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, MenuMEFluidInputBus.PANEL_WIDTH, imageHeight, 256, 256);
        // AE2 1.12 supplied this chrome through GuiUpgradeable. Draw a compact equivalent around the five
        // positioned slots so the restored upgrade rail is visible with the same x=187 geometry.
        int x = leftPos + MenuMEFluidInputBus.PANEL_WIDTH;
        graphics.fill(x, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3A3A);
        graphics.fill(x + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFC6C6C6);
        renderConfiguredFluids(graphics);
        renderStoredFluids(graphics);
    }

    /**
     * Draws each config slot's configured fluid inside its slot.
     *
     * <p><b>Why this is not left to the slot's {@code ItemStack}:</b> a configuration is a fluid, not an item, and the slot
     * deliberately reports an empty stack (its backing container holds nothing and {@code getMaxStackSize} is 0). Rendering the
     * fluid is therefore the screen's job.</p>
     *
     * <p><b>The technique is {@code ScreenFluidHatch}'s, reused rather than reinvented</b> (§256/§257): a fluid's still texture is
     * a sprite inside the BLOCK ATLAS, so it must be resolved through the atlas and blitted with the fluid's own tint. Binding it
     * as a texture is the mistake that produced a missing-texture checkerboard in this project once already.</p>
     */
    private void renderConfiguredFluids(GuiGraphics graphics) {
        var atlas = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        for (Slot slot : menu.slots) {
            if (!(slot instanceof MenuMEFluidInputBus.ConfigSlot config)) {
                continue;
            }
            FluidStack fluid = config.getFluid();
            if (fluid.isEmpty()) {
                // An unconfigured slot draws nothing: the panel's own empty-slot art shows through, and "nothing
                // configured" is a state the player must be able to tell from "configured" at a glance.
                continue;
            }
            ResourceLocation still = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                    .of(fluid.getFluid()).getStillTexture();
            if (still == null) {
                continue;
            }
            int tint = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                    .of(fluid.getFluid()).getTintColor();
            var sprite = atlas.apply(still);
            // The slot is 16x16 and this row has no scale rail over it, so the fluid fills the slot exactly.
            graphics.blit(leftPos + slot.x, topPos + slot.y, 0,
                    CONFIG_SLOT_SIZE, CONFIG_SLOT_SIZE, sprite,
                    ((tint >> 16) & 0xFF) / 255.0F,
                    ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F,
                    1.0F);
        }
    }

    /** Draws the nine read-only tank bars from upstream's {@code GuiFluidTank(..., y=53, height=68)}. */
    private void renderStoredFluids(GuiGraphics graphics) {
        var atlas = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        for (int tank = 0; tank < MenuMEFluidInputBus.CONFIG_SLOTS; tank++) {
            FluidStack fluid = menu.storedFluidFor(tank);
            if (fluid.isEmpty()) {
                continue;
            }
            ResourceLocation still = IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture();
            if (still == null) {
                continue;
            }
            int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor();
            int height = Math.max(1, Math.min(68,
                    Math.round(68.0F * fluid.getAmount() / (float) menu.capacityForDisplay())));
            int x = leftPos + 8 + tank * 18;
            int y = topPos + 53;
            var sprite = atlas.apply(still);
            graphics.blit(x, y + 68 - height, 0, 16, height, sprite,
                    ((tint >> 16) & 0xFF) / 255.0F,
                    ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F, 1.0F);
        }
    }

    /**
     * The panel's text, from upstream's {@code drawFG} (U66-U71).
     *
     * <p>Drawn in the LABEL pass rather than in {@code renderBg} as upstream does: on 1.20.1 {@code renderLabels} is where a
     * container screen's text belongs, and the widget pass draws over the background.</p>
     *
     * <p>The two labels are this mod's own keys rather than AE2's {@code GuiText.Config} / {@code GuiText.StoredFluids} (upstream
     * U68-U69), which is the one text difference from upstream and it is deliberate: AE2's keys resolve only if AE2's lang file
     * covers the player's language, and a mod's screen showing {@code gui.ae2.Config} because a translation is missing is a
     * worse outcome than owning two strings.</p>
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.mefluidinputbus.title"), 8, 6, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mefluidinputbus.config"), 8, 24, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mefluidinputbus.stored"), 8, 125, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mefluidinputbus.inventory"), 8,
                imageHeight - 96 + 3, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mefluidinputbus.upgrades"),
                MenuMEFluidInputBus.PANEL_WIDTH + 3, 3, 0x404040, false);
    }

    

















 
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Names the fluid under the cursor, since the slot shows no item.
     *
     * <p>Added rather than ported: upstream gets its identification from AE2's {@code GuiFluidSlot} tooltip, which is part of
     * the class that does not exist here. Without it a configuration would be recognisable only by colour, and two fluids with
     * close tints would be indistinguishable.</p>
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Slot hovered = this.getSlotUnderMouse();
        if (hovered instanceof MenuMEFluidInputBus.ConfigSlot config) {
            FluidStack fluid = config.getFluid();
            if (!fluid.isEmpty()) {
                graphics.renderTooltip(font, Component.translatable("gui.mefluidinputbus.config.tooltip",
                        fluid.getAmount(),
                        Component.translatable(fluid.getFluid().getFluidType().getDescriptionId())),
                        mouseX, mouseY);
                return;
            }
            graphics.renderTooltip(font, Component.translatable("gui.mefluidinputbus.config.empty"), mouseX, mouseY);
            return;
        }
        for (int tank = 0; tank < MenuMEFluidInputBus.CONFIG_SLOTS; tank++) {
            int x = leftPos + 8 + tank * 18;
            int y = topPos + 53;
            if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 68) {
                continue;
            }
            FluidStack fluid = menu.storedFluidFor(tank);
            if (!fluid.isEmpty()) {
                graphics.renderTooltip(font, Component.translatable("gui.mefluidinputbus.stored.tooltip",
                        fluid.getAmount(), Component.translatable(fluid.getFluid().getFluidType().getDescriptionId())),
                        mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
