/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.assembly;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import hellfirepvp.modularmachinery.common.data.Config;

/** Optional material source. No AE2 types may cross this boundary. */
public interface AssemblyNetwork extends AutoCloseable {
    interface Budget {
        boolean take(ItemStack item, FluidStack fluid);
    }

    /** Payment owns the exact withdrawn resource, including tags, until placement commits. */
    record Payment(boolean fluid, Runnable refund) { }

    Budget snapshot();
    boolean canExtract(ItemStack item, FluidStack fluid);
    Payment extract(ItemStack item, FluidStack fluid);
    boolean request(ItemStack item, FluidStack fluid);
    void tick();
    boolean available();
    boolean waiting();
    @Override void close();

    static AssemblyNetwork open(ServerPlayer player) {
        if (!Config.autoAssemblyWireless() || !net.minecraftforge.fml.ModList.get().isLoaded("ae2")) return null;
        return hellfirepvp.modularmachinery.integration.ae2.WirelessAssemblySource.open(player);
    }
}
