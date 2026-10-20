/* SPDX-License-Identifier: GPL-3.0-only */
/* Modular Machinery CE — Forge 1.20.1 port. */
package hellfirepvp.modularmachinery.common.crafting.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Server commands attached to a recipe. Commands run as the server at the controller position,
 * matching the upstream start/tick/finish phases.
 */
public final class RecipeCommandContainer {
    private final List<RecipeRunnableCommand> startCommands;
    private final List<RecipeRunnableCommand> processingCommands;
    private final List<RecipeRunnableCommand> finishCommands;

    public RecipeCommandContainer() {
        this(List.of(), List.of(), List.of());
    }

    public RecipeCommandContainer(List<RecipeRunnableCommand> startCommands,
                                  List<RecipeRunnableCommand> processingCommands,
                                  List<RecipeRunnableCommand> finishCommands) {
        this.startCommands = List.copyOf(startCommands);
        this.processingCommands = List.copyOf(processingCommands);
        this.finishCommands = List.copyOf(finishCommands);
    }

    public static RecipeCommandContainer empty() {
        return new RecipeCommandContainer();
    }

    public void runStartCommands(TileController controller) {
        run(startCommands, controller, 0);
    }

    public void runTickCommands(TileController controller, int craftTick) {
        run(processingCommands, controller, craftTick);
    }

    public void runFinishCommands(TileController controller) {
        run(finishCommands, controller, 0);
    }

    private static void run(List<RecipeRunnableCommand> commands, TileController controller, int tick) {
        if (commands.isEmpty() || controller.getLevel() == null || controller.getLevel().isClientSide()
                || !(controller.getLevel() instanceof ServerLevel level)
                || level.getServer() == null) {
            return;
        }
        var source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(controller.getBlockPos()))
                .withPermission(2)
                .withSuppressedOutput();
        for (RecipeRunnableCommand command : commands) {
            command.run(source, tick);
        }
    }

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        write(out, "startCommands", startCommands);
        write(out, "processingCommands", processingCommands);
        write(out, "finishCommands", finishCommands);
        return out;
    }

    private static void write(JsonObject out, String key, List<RecipeRunnableCommand> commands) {
        if (commands.isEmpty()) return;
        JsonArray array = new JsonArray();
        for (RecipeRunnableCommand command : commands) {
            JsonObject value = new JsonObject();
            value.addProperty("command", command.command());
            if (command.interval() != -1) value.addProperty("interval", command.interval());
            array.add(value);
        }
        out.add(key, array);
    }

    public static RecipeCommandContainer parse(JsonObject root) {
        return new RecipeCommandContainer(parseList(root, "startCommands"),
                parseList(root, "processingCommands"), parseList(root, "finishCommands"));
    }

    private static List<RecipeRunnableCommand> parseList(JsonObject root, String key) {
        if (!root.has(key)) return List.of();
        JsonElement element = root.get(key);
        if (!element.isJsonArray()) throw new IllegalArgumentException("'" + key + "' has to be an array");
        List<RecipeRunnableCommand> result = new ArrayList<>();
        for (JsonElement command : element.getAsJsonArray()) {
            if (command.isJsonPrimitive() && command.getAsJsonPrimitive().isString()) {
                result.add(new RecipeRunnableCommand(command.getAsString()));
                continue;
            }
            if (!command.isJsonObject()) throw new IllegalArgumentException("invalid command in '" + key + "'");
            JsonObject object = command.getAsJsonObject();
            if (!object.has("command") || !object.get("command").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("command").isString()) {
                throw new IllegalArgumentException("command entry in '" + key + "' has no string 'command'");
            }
            int interval = -1;
            if (object.has("interval")) {
                if (!object.get("interval").isJsonPrimitive() || !object.getAsJsonPrimitive("interval").isNumber()) {
                    throw new IllegalArgumentException("interval in '" + key + "' has to be a number");
                }
                interval = object.get("interval").getAsInt();
            }
            result.add(new RecipeRunnableCommand(object.get("command").getAsString(), interval));
        }
        return result;
    }

    public boolean isEmpty() {
        return startCommands.isEmpty() && processingCommands.isEmpty() && finishCommands.isEmpty();
    }
}

