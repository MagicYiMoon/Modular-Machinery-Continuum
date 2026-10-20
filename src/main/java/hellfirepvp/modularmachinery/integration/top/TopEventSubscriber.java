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
package hellfirepvp.modularmachinery.integration.top;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Forge-side hook that hands {@link TopIntegration} its registration window.
 *
 * <h2>Why the {@code isLoaded} check is load-bearing, not defensive</h2>
 *
 * <p>{@code TopIntegration.enqueue} builds a lambda whose <b>body</b> references {@code ITheOneProbe}. The
 * class itself does not: the only TOP type in {@code TopIntegration} sits inside that lambda, which the
 * compiler emits as a separate synthetic method. So loading this subscriber and calling {@code enqueue}
 * touches no TOP class — and the guard matters because if TOP is absent, {@code InterModComms.sendTo}
 * selects no handler and the lambda is simply never invoked, which is why an absent TOP is a no-op rather
 * than a crash.
 *
 * <p>Stated explicitly because the alternative — calling TOP's API directly from a
 * {@code @Mod} class or a common event handler — is the standard way a "soft" dependency silently becomes
 * a hard one: it works on the developer's machine (TOP installed) and crashes for everyone else.
 *
 * <p>The subscriber lives in {@code integration.top} rather than beside the other event code so that any
 * reference to it from {@code common.*} is visible in review.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TopEventSubscriber {

    private TopEventSubscriber() {
    }

    @SubscribeEvent
    public static void onInterModEnqueue(InterModEnqueueEvent event) {
        if (!ModList.get().isLoaded("theoneprobe")) {
            // Logged at debug, which is invisible at the default level — deliberately. An absent optional
            // integration is the normal case for most players, so announcing it on every startup would be
            // noise. It is logged at all so that someone debugging "why is there no HUD information" can
            // raise the level and see the reason.
            Modularmachinery.LOG.debug("[MMCE] The One Probe not present; skipping HUD integration");
            return;
        }
        TopIntegration.enqueue(event);
    }
}
