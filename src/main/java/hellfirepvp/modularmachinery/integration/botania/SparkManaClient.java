/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.botania;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import vazkii.botania.api.BotaniaForgeClientCapabilities;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.client.gui.HUDHandler;

/** Loaded exclusively by the client setup with Botania installed. */
public final class SparkManaClient {
    private SparkManaClient() { }
    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, SparkManaClient::attach);
    }
    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof TileSparkManaProvider tile)) return;
        LazyOptional<WandHUD> hud = LazyOptional.of(() -> (gui, mc) -> HUDHandler.drawSimpleManaHUD(gui,
                0x4444FF, tile.getCurrentMana(), tile.capacity(), tile.getBlockState().getBlock().getName().getString()));
        event.addCapability(new ResourceLocation("modularmachinery", "spark_mana_hud"), new ICapabilityProvider() {
            @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, net.minecraft.core.Direction side) {
                return cap == BotaniaForgeClientCapabilities.WAND_HUD ? hud.cast() : LazyOptional.empty();
            }
        });
        event.addListener(hud::invalidate);
    }
}
