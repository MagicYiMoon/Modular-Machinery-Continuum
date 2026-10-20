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
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.machine.MachineFormation;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.SimpleMenuProvider;

import java.io.File;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * The {@code /modularmachinery} command.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream used {@code CommandBase}/{@code ICommand} with
 * {@code ServerCommandManager} and an {@code ITabCompleter}; those are gone. On 1.20.1 commands are
 * Mojang's Brigadier, registered through {@code RegisterCommandsEvent} with a
 * {@code CommandDispatcher<CommandSourceStack>}, and {@code ICommandSender} is replaced by
 * {@code CommandSourceStack}.
 *
 * <p>This exists before the GUI does, and deliberately so: it makes machine formation
 * <b>observable without a client</b>. A dedicated server can be told to attempt formation at the
 * controller a player is looking at and report exactly which parts mismatched — which is the only way
 * to verify the matcher against a real world from an automated run.
 */
public final class CommandModularMachinery {

    private CommandModularMachinery() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Modularmachinery.MODID)
                .requires(source -> source.hasPermission(2))

                // --- open the pack-author visual editor ---
                .then(Commands.literal("gui").executes(CommandModularMachinery::gui))

                // --- list the definitions that loaded ---
                .then(Commands.literal("list").executes(CommandModularMachinery::list))

                // --- list loaded recipes, optionally filtered to one machine ---
                .then(Commands.literal("recipes")
                        .executes(ctx -> recipes(ctx, null))
                        .then(Commands.argument("machine", StringArgumentType.string())
                                .executes(ctx -> recipes(ctx, StringArgumentType.getString(ctx, "machine")))))

                // --- bind the controller under the crosshair to a definition ---
                .then(Commands.literal("bind")
                        .then(Commands.argument("machine", StringArgumentType.string())
                                .executes(CommandModularMachinery::bind)))

                // --- save the current player's two-corner selection as a named machine ---
                .then(Commands.literal("register")
                        .then(Commands.argument("machine", StringArgumentType.word())
                                .executes(CommandModularMachinery::registerMachine)))

                // --- attempt formation on the controller under the crosshair ---
                .then(Commands.literal("form").executes(CommandModularMachinery::form))

                // --- evaluate every recipe of this machine against its current contents ---
                .then(Commands.literal("check").executes(CommandModularMachinery::check))

                // --- report the controller under the crosshair ---
                .then(Commands.literal("status").executes(CommandModularMachinery::status)));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        MachineLoader loader = MachineReloadListener.getLoader();
        List<DynamicMachine> machines = List.copyOf(loader.all());
        if (machines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("no machine definitions loaded"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                machines.size() + " machine definition(s):"), false);
        for (DynamicMachine machine : machines) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + machine.getRegistryName()
                    + " — " + machine.getPattern().size() + " part(s)"), false);
        }
        return machines.size();
    }

    private static int gui(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("只有玩家可以打开机器编辑器"));
            return 0;
        }
        // Command execution errors are otherwise reduced by vanilla to the unhelpful
        // commands.generic.exception message.  Keep the whole operation in one guarded
        // section because both snapshot encoding and menu creation run on the server thread.
        // A malformed optional requirement or an oversized custom snapshot must report its
        // actual cause to the pack author and to latest.log instead of looking like a command
        // parser failure.
        try {
            Modularmachinery.LOG.debug("[MMCE] opening machine editor for {}", player.getGameProfile().getName());
            String snapshot = MachineEditorSnapshot.create(MachineReloadListener.getLoader(),
                    MachineReloadListener.getRecipeLoader());
            int snapshotBytes = snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            Modularmachinery.LOG.debug("[MMCE] machine editor snapshot: {} bytes", snapshotBytes);
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (windowId, inventory, ignored) -> new MenuMachineEditor(windowId, inventory, snapshot),
                    Component.literal("Modular Machinery Editor")),
                // Forge limits the extra open-menu buffer to roughly 32 KiB.  The editor document
                // contains every resolved recipe and is commonly larger than that, so only send a
                // tiny placeholder here; the real snapshot follows over our chunked play channel.
                data -> MenuMachineEditor.writeSnapshot(data, MenuMachineEditor.EMPTY_SNAPSHOT));
            if (player.containerMenu instanceof MenuMachineEditor editor) {
                byte[] bytes = snapshot.getBytes(StandardCharsets.UTF_8);
                UUID transfer = UUID.randomUUID();
                for (int offset = 0; offset < bytes.length;
                     offset += ModularmachineryNetwork.PktMachineEditorSnapshot.CHUNK_BYTES) {
                    int end = Math.min(bytes.length,
                            offset + ModularmachineryNetwork.PktMachineEditorSnapshot.CHUNK_BYTES);
                    ModularmachineryNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new ModularmachineryNetwork.PktMachineEditorSnapshot(
                                    editor.containerId, transfer, bytes.length, offset,
                                    Arrays.copyOfRange(bytes, offset, end)));
                }
            } else {
                throw new IllegalStateException("machine editor menu was not created");
            }
            return 1;
        } catch (Throwable failure) {
            Modularmachinery.LOG.error("[MMCE] failed to open machine editor", failure);
            String detail = failure.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = failure.getClass().getSimpleName();
            }
            ctx.getSource().sendFailure(Component.literal("机器编辑器打开失败：" + detail));
            return 0;
        }
    }

    private static int recipes(CommandContext<CommandSourceStack> ctx, String machineFilter) {
        var byMachine = MachineReloadListener.getRecipeLoader().getByMachine();
        int shown = 0;
        for (var entry : byMachine.entrySet()) {
            if (machineFilter != null && !machineFilter.equals(entry.getKey())) {
                continue;
            }
            String machine = entry.getKey();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    machine + " — " + entry.getValue().size() + " recipe(s):"), false);
            for (var recipe : entry.getValue()) {
                // Full description: requirement lines are where a pack author's mistake actually shows
                // up, so they are worth printing rather than summarising to a count.
                for (String line : recipe.describe().split("\n")) {
                    ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
                }
                shown++;
            }
        }
        if (shown == 0) {
            ctx.getSource().sendFailure(Component.literal(machineFilter == null
                    ? "no recipes loaded"
                    : "no recipes for machine '" + machineFilter + "'"));
            return 0;
        }
        return shown;
    }

    /**
     * Evaluates every recipe of the machine under the crosshair against its current contents.
     *
     * <p>This is the observable form of {@code canStart}/{@code canFinish}. Those two methods are the
     * heart of the recipe layer — a machine that starts a recipe it cannot finish destroys the player's
     * items — and they cannot be verified by reading a log at startup, because they depend on what is
     * actually sitting in the multiblock. Without this command the only way to test them is to build a
     * machine by hand and watch it.
     */
    private static int check(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = controllerPos(ctx);
        if (pos == null) {
            return 0;
        }
        TileController controller = controllerAt(ctx, pos);
        if (controller == null) {
            return 0;
        }
        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(controller.getMachineName());
        if (machine == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "controller is not bound to a machine; use /modularmachinery bind <machine> first"));
            return 0;
        }

        ServerLevel level = ctx.getSource().getLevel();
        Direction facing = controllerFacing(level, pos);
        // THE FORMED PATTERN, NOT THE DEFINITION'S (PLAN §750): `/modularmachinery check` exists to answer "can this
        // machine run", and a diagnostic that reads the static half reports the dynamic sections' busses as
        // unreachable on a machine whose recipe layer can see them perfectly well -- the report would be wrong in
        // the direction that hides the defect rather than exposing it.
        MachineContext context = new MachineContext(
                level, pos, facing, machine, loader.getVariables(), controller.getFormedPattern(machine));

        ctx.getSource().sendSuccess(() -> Component.literal("machine '" + machine.getRegistryName()
                + "': " + context.getComponentPositions().size() + " component(s) reachable"), false);

        var recipes = MachineReloadListener.getRecipeLoader().getByMachine().get(machine.getRegistryName());
        if (recipes == null || recipes.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no recipes loaded for this machine"));
            return 0;
        }

        int runnable = 0;
        for (var recipe : recipes) {
            boolean canStart = true;
            boolean canFinish = true;
            var blocked = new java.util.ArrayList<String>();

            for (var requirement : recipe.requirements()) {
                if (!requirement.canStart(context)) {
                    canStart = false;
                    canFinish = false;
                    blocked.add("start: " + requirement.describe());
                } else if (!requirement.canFinish(context)) {
                    canFinish = false;
                    blocked.add("finish: " + requirement.describe());
                }
            }

            if (canFinish) {
                runnable++;
            }
            boolean start = canStart;
            boolean finish = canFinish;
            ctx.getSource().sendSuccess(() -> Component.literal("  " + recipe.registryName()
                    + " — canStart=" + start + " canFinish=" + finish), false);
            for (String line : blocked) {
                ctx.getSource().sendSuccess(() -> Component.literal("      blocked " + line), false);
            }
        }

        int total = runnable;
        int recipeCount = recipes.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                total + "/" + recipeCount + " recipe(s) could run to completion right now"), false);
        return total;
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = controllerPos(ctx);
        if (pos == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "machine");
        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(name);
        if (machine == null) {
            ctx.getSource().sendFailure(Component.literal("no machine definition named '" + name + "'"));
            return 0;
        }
        TileController controller = controllerAt(ctx, pos);
        if (controller == null) {
            return 0;
        }
        controller.setMachineName(machine.getRegistryName());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "bound controller at " + pos.toShortString() + " to '" + machine.getRegistryName() + "'"), true);
        return 1;
    }

    private static int form(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = controllerPos(ctx);
        if (pos == null) {
            return 0;
        }
        TileController controller = controllerAt(ctx, pos);
        if (controller == null) {
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(controller.getMachineName());

        MachineFormation.Attempt attempt = MachineFormation.tryForm(
                level, pos, controllerFacing(level, pos), machine, loader);

        if (attempt.formed()) {
            controller.setFormed(true);
            ctx.getSource().sendSuccess(() -> Component.literal("machine '"
                    + attempt.machine().getRegistryName() + "' formed; " + attempt.boundCount()
                    + " component(s) bound"), true);
            return 1;
        }

        ctx.getSource().sendFailure(Component.literal("machine did not form:"));
        for (String line : attempt.report()) {
            ctx.getSource().sendFailure(Component.literal("  " + line));
        }
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = controllerPos(ctx);
        if (pos == null) {
            return 0;
        }
        TileController controller = controllerAt(ctx, pos);
        if (controller == null) {
            return 0;
        }
        String name = controller.getMachineName();
        ctx.getSource().sendSuccess(() -> Component.literal("controller " + pos.toShortString()
                + ": machine=" + (name == null ? "<unbound>" : name)
                + " formed=" + controller.isFormed()), false);
        return 1;
    }

    /**
     * Saves the executing player's current two-corner selection under a stable machine name.
     *
     * <p>The selection is deliberately still owned by {@link PlayerStructureSelectionHelper}; the command
     * only supplies the name and controller position.  This keeps the item and command paths byte-for-byte
     * consistent for block states, block-entity NBT, facing rotation and the volume limit.</p>
     */
    private static int registerMachine(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(
                    "只有玩家可以使用 register；请先用建造选择工具标记两个角"));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "machine");
        if (!PlayerStructureSelectionHelper.isValidMachineName(name)) {
            ctx.getSource().sendFailure(Component.literal(
                    "机器名只能使用小写字母、数字、点、下划线和连字符"));
            return 0;
        }

        MachineLoader loader = MachineReloadListener.getLoader();
        if (loader.findByName(name) != null) {
            ctx.getSource().sendFailure(Component.literal(
                    "machine '" + name + "' is already loaded; choose another name"));
            return 0;
        }
        File directory = ModDataHolder.INSTANCE.getMachineryDirectory();
        if (directory == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "machine directory is not ready yet"));
            return 0;
        }
        File target = new File(directory, name + ".json");
        if (target.exists()) {
            ctx.getSource().sendFailure(Component.literal(
                    "machine file already exists: " + target.getName()));
            return 0;
        }

        BlockPos pos = controllerPos(ctx);
        if (pos == null) {
            return 0;
        }
        boolean written = PlayerStructureSelectionHelper.finalizeSelection(
                controllerFacing(ctx.getSource().getLevel(), pos),
                ctx.getSource().getLevel(), pos, player, name);
        if (!written) {
            return 0;
        }
        PlayerStructureSelectionHelper.purgeSelection(player);
        PlayerStructureSelectionHelper.sendSelection(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "saved machine '" + name + "'; run /reload to load it"), true);
        return 1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * @return the position of the controller the source is looking at, or {@code null} after sending a
     *         failure — the caller returns 0 in that case
     */
    private static BlockPos controllerPos(CommandContext<CommandSourceStack> ctx) {
        HitResult hit = ctx.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player
                ? player.pick(8.0D, 0.0F, false)
                : null;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal("look at a machine controller within 8 blocks"));
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = ctx.getSource().getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof BlockController)) {
            ctx.getSource().sendFailure(Component.literal(
                    "the block at " + pos.toShortString() + " is not a machine controller"));
            return null;
        }
        return pos;
    }

    private static TileController controllerAt(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        if (ctx.getSource().getLevel().getBlockEntity(pos) instanceof TileController controller) {
            return controller;
        }
        ctx.getSource().sendFailure(Component.literal("no controller block entity at " + pos.toShortString()));
        return null;
    }

    private static Direction controllerFacing(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(BlockController.FACING)
                ? state.getValue(BlockController.FACING)
                : Direction.NORTH;
    }
}
