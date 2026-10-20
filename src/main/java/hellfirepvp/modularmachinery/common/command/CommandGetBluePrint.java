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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /mm-get_blueprint <machine>} — hands the running player a blueprint for a machine.
 *
 * <h2>Faithful to the jar, including the name</h2>
 *
 * <p>The jar's {@code CommandGetBluePrint} is a <b>separate top-level command</b>, not a subcommand of the
 * mod's own command, and its name is the literal {@code "mm-get_blueprint"}. This port already has a
 * {@code /modularmachinery} command with its own subcommands, so folding this in as
 * {@code /modularmachinery blueprint} would have been tidier — and would have been wrong, because a player
 * following the mod's documentation would type {@code /mm-get_blueprint} and find nothing.
 *
 * <p>The permission level is <b>2</b>, matching upstream's {@code getRequiredPermissionLevel} override.
 *
 * <h2>Behaviour, read from the jar's bytecode</h2>
 *
 * <ol>
 *   <li>the sender must be a player — otherwise {@code ...get_blueprint.player_only};</li>
 *   <li>resolve {@code new ResourceLocation("modularmachinery", args[0])} against the machine registry;</li>
 *   <li>if absent, {@code ...get_blueprint.not_found};</li>
 *   <li>otherwise build a blueprint stack, bind the machine to it, and add it to the player's inventory;</li>
 *   <li>report {@code ...get_blueprint.success}.</li>
 * </ol>
 *
 * <p>The lookup goes through {@link MachineReloadListener} on every invocation rather than a cached map:
 * definitions are reloaded on datapack and CraftTweaker runs, so a command that captured the registry at
 * startup would hand out blueprints for machines that no longer exist.
 */
public final class CommandGetBluePrint {

    private CommandGetBluePrint() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mm-get_blueprint")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("machine", StringArgumentType.string())
                        .executes(CommandGetBluePrint::giveBlueprint)));
    }

    private static int giveBlueprint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // Upstream is player-only: the blueprint has to go into an inventory, and there is no sensible
        // console behaviour for an item that can only be held.
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.modularmachinery.get_blueprint.player_only"));
            return 0;
        }

        String machineName = StringArgumentType.getString(ctx, "machine");
        ResourceLocation key = new ResourceLocation(Modularmachinery.MODID, machineName);
        DynamicMachine machine = MachineReloadListener.getLoader().getMachines().get(key);
        if (machine == null) {
            source.sendFailure(Component.translatable("command.modularmachinery.get_blueprint.not_found",
                    machineName));
            return 0;
        }

        ItemStack stack = new ItemStack(RegistryItems.BLUEPRINT.get());
        ItemBlueprint.setAssociatedMachine(stack, machine);

        // addOrDrop rather than add, so a full inventory does not silently destroy the item. A command that
        // reported success while dropping the stack on the floor would be lying about what happened.
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        source.sendSuccess(() -> Component.translatable("command.modularmachinery.get_blueprint.success",
                machine.getTranslationKey()), false);
        return 1;
    }
}
