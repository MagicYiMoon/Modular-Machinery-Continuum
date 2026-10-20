/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.extrautilities;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicit adjacent-generator adapter for Extra Utilities Rebirth 1.7.3, not an XU2 frequency network. */
public final class RainbowIntegration {
    private static Boolean supported;
    public static boolean available() {
        if (supported == null) {
            supported = ModList.get().getModContainerById("extrautilitiesrebirth")
                    .map(c -> c.getModInfo().getVersion().toString().equals("1.7.3")).orElse(false);
            if (!supported && ModList.get().isLoaded("extrautilitiesrebirth"))
                Modularmachinery.LOG.warn("[MMCE] Rainbow adapter needs the verified Extra Utilities Rebirth 1.7.3; retaining placeholder behavior");
        }
        return supported;
    }
    public static boolean isRunning(Level level, BlockPos detector) {
        if (!available() || level == null || level.isClientSide()) return false;
        for (Direction side : Direction.values()) {
            BlockPos at = detector.relative(side);
            if (!level.hasChunkAt(at)) continue;
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(at).getBlock());
            if (id == null || !id.equals(new ResourceLocation("extrautilitiesrebirth", "rainbow_generator"))) continue;
            var tile = level.getBlockEntity(at);
            if (tile != null && !tile.isRemoved() && tile.getPersistentData().getBoolean("rgb_running")) return true;
        }
        return false;
    }
}
