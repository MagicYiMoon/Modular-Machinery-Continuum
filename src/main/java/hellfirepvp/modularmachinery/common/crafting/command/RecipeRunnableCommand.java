/* SPDX-License-Identifier: GPL-3.0-only */
/* Modular Machinery CE — Forge 1.20.1 port. */
package hellfirepvp.modularmachinery.common.crafting.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** One recipe command and its optional processing interval. */
public record RecipeRunnableCommand(String command, int interval) {
    public RecipeRunnableCommand(String command) {
        this(command, -1);
    }

    public RecipeRunnableCommand {
        command = command == null ? "" : command;
        interval = Math.max(-1, interval);
    }

    public void run(CommandSourceStack source, int tick) {
        MinecraftServer server = source.getServer();
        if (server == null || command.isBlank()) return;
        if (interval != -1 && (interval <= 0 || tick % interval != 0)) return;
        server.getCommands().performPrefixedCommand(source, command);
    }
}

