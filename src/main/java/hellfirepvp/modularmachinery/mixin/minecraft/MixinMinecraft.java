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
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase-1 mechanism probe, kept as the loader-wiring proof for the Forge 1.20.1 mixin pipeline.
 *
 * <p>Verified facts this class exists to lock down:
 * <ul>
 *   <li>Forge 1.20.1 (47.3.0) provides Mixin 0.8.5 itself, so no runtime mixin dependency is declared.</li>
 *   <li>Mixin configs are announced through the {@code MixinConfigs} jar manifest attribute -- the Forge
 *       1.20.1 {@code mods.toml} schema has no {@code [[mixins]]} block (unlike NeoForge).</li>
 *   <li>The refmap is produced by the sponge-mixin annotation processor ({@code -AdefaultObfuscationEnv=searge},
 *       {@code -AoutRefMapFile=...}) and shipped in the jar.</li>
 * </ul>
 *
 * <p>This replaces the 1.12.2 coremod bootstrap. Upstream 2.3.2 used
 * {@code FMLCorePlugin: github.kasuminova.mmce.mixin.MMCEEarlyMixinLoader} (an {@code IFMLLoadingPlugin},
 * removed in 1.20.1) plus {@code zone.rong.mixinbooter.ILateMixinLoader} (a third-party mod that is not a
 * dependency of this port). Both are deleted; Mixin is applied by FML for every mod on 1.20.1.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    // NOTE: targeting a method with no SRG name (e.g. Minecraft#run) makes the Mixin
    // annotation processor fail with "Unable to locate obfuscation mapping" -- there is
    // nothing to emit into the refmap. setScreen has a real SRG name (m_91152_).
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void modularmachinery$onSetScreen(Screen screen, CallbackInfo ci) {
        Modularmachinery.LOG.debug("[MMCE] mixin pipeline active (Minecraft#setScreen -> {})", screen);
    }
}
