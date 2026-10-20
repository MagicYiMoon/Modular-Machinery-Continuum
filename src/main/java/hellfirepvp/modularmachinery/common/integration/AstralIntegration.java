/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.integration;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraftforge.fml.ModList;

/** API probe stays free of optional API symbols so absent/incompatible mods retain placeholders. */
public final class AstralIntegration {
    private static Boolean supported;
    public static boolean available() {
        if (supported == null) {
            supported = false;
            if (ModList.get().isLoaded("astralsorcery")) {
                try {
                    var loader = AstralIntegration.class.getClassLoader();
                    Class.forName("hellfirepvp.astralsorcery.common.starlight.IStarlightReceiver", false, loader)
                            .getMethod("receiveStarlight", double.class, net.minecraft.resources.ResourceLocation.class);
                    Class.forName("hellfirepvp.astralsorcery.common.starlight.StarlightNetworkHelper", false, loader)
                            .getMethod("removeNode", net.minecraft.world.level.Level.class, net.minecraft.core.BlockPos.class);
                    Class.forName("hellfirepvp.astralsorcery.common.constellation.world.ConstellationHandler", false, loader);
                    supported = true;
                } catch (ReflectiveOperationException | LinkageError ex) {
                    Modularmachinery.LOG.warn("[MMCE] Astral Sorcery lacks the verified community 1.20.1 API; providers remain placeholders", ex);
                }
            }
        }
        return supported;
    }
}
