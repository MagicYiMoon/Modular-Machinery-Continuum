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
package hellfirepvp.modularmachinery.common.command;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers {@code /modularmachinery} on the game bus.
 *
 * <p>1.12.2 did this from {@code FMLServerStartingEvent#registerServerCommand} with a
 * {@code ServerCommandManager}. On 1.20.1 the equivalent is {@link RegisterCommandsEvent}, which
 * fires for both integrated and dedicated servers and hands over a Brigadier
 * {@code CommandDispatcher<CommandSourceStack>}. This event belongs on the <b>game</b> bus, not the
 * mod bus — a distinction that produces no error when wrong, just a command that never appears.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandRegistration {

    private CommandRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandModularMachinery.register(event.getDispatcher());
        // A separate top-level command, because that is what the jar declares -- see CommandGetBluePrint.
        CommandGetBluePrint.register(event.getDispatcher());
        Modularmachinery.LOG.debug("[MMCE] commands registered: /{} and /mm-get_blueprint",
                Modularmachinery.MODID);
    }
}
