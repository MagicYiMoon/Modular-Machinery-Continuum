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
import mcjty.theoneprobe.api.ITheOneProbe;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

import java.util.function.Function;

/**
 * Registers the machine progress provider with The One Probe.
 *
 * <h2>Why registration goes through InterModComms</h2>
 *
 * <p>TOP exposes no annotation and no registry for providers. The only supported route is the
 * <b>inter-mod communication</b> channel: the mod asks TOP for its {@code ITheOneProbe} instance by
 * sending the {@code "getTheOneProbe"} message, and TOP calls back with it. That is inherently a
 * <b>runtime, load-order-dependent</b> handshake, which is exactly why this must be driven from an event
 * rather than from a static initialiser: at class-load time TOP may not have registered its own handler
 * yet, and the message would go nowhere with no error.
 *
 * <p>{@link InterModEnqueueEvent} is the correct phase — it fires after all mods have registered their IMC
 * handlers, so TOP is guaranteed to be listening.
 *
 * <h2>Soft dependency</h2>
 *
 * <p>Same discipline as the JEI plugin: separate package, never referenced from {@code @Mod} or any
 * ordinary code path. With TOP absent the message is simply sent to nobody — {@code InterModComms.sendTo}
 * is a no-op for a mod that is not loaded — and no TOP class is ever touched. The dedicated-server run is
 * the test that proves it.
 */
public final class TopIntegration {

    private TopIntegration() {
    }

    /** The IMC method name TOP listens on. A bare string, and a typo here is silently ignored. */
    private static final String GET_THE_ONE_PROBE = "getTheOneProbe";

    private static boolean registered = false;

    /**
     * Asks TOP for its API and registers the provider.
     *
     * <p>Guarded rather than idempotent-by-nature: a second registration would add the provider twice, and
     * TOP would then draw the machine's information twice in the overlay.
     */
    public static void enqueue(InterModEnqueueEvent event) {
        if (registered) {
            return;
        }
        registered = true;

        // The sender must be a lambda returning the transformer, because TOP invokes it when it processes
        // the message — not now. Returning null from the transformer is TOP's documented convention for
        // "nothing to send back".
        InterModComms.sendTo("theoneprobe", GET_THE_ONE_PROBE,
                () -> (Function<ITheOneProbe, Void>) probe -> {
                    probe.registerProvider(new MachineProbeProvider());
                    Modularmachinery.LOG.info(
                            "[MMCE] The One Probe: machine progress provider registered");
                    return null;
                });
    }
}
