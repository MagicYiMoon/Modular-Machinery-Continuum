/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 ******************************************************************************/
package hellfirepvp.modularmachinery.integration.top;

import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.block.prop.ItemBusSize;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.tiles.TileParallelController;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import mcjty.theoneprobe.api.ElementAlignment;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** The One Probe provider, following the original MMCE owner/structure/status layout. */
public class MachineProbeProvider implements IProbeInfoProvider {
    @Override
    public ResourceLocation getID() {
        // Keep the source provider id so TOP can replace/identify the integration consistently.
        return new ResourceLocation("modularmachinery", "dynamic_machine_info_provider");
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player, Level level,
                             BlockState state, IProbeHitData hitData) {
        // Component tiers are useful when probing a bus or hatch and preserve the original provider's extra help.
        var variantProperty = BlockMachineComponent.variantProperty(state);
        if (variantProperty != null) {
            for (var property : state.getProperties()) {
                if (!property.getName().equals(variantProperty.getName())) continue;
                Object value = state.getValue(property);
                String name = value instanceof net.minecraft.util.StringRepresentable sr
                        ? sr.getSerializedName() : String.valueOf(value).toLowerCase(Locale.ROOT);
                if (value instanceof ItemBusSize bus) {
                    info.text(Component.translatable("top.modularmachinery.variant_slots", name, bus.getSlots()));
                } else if (value instanceof EnergyHatchData energy) {
                    info.text(Component.translatable("top.modularmachinery.variant_energy", name, energy.getMaxEnergy()));
                } else {
                    info.text(Component.translatable("top.modularmachinery.variant", name));
                }
                break;
            }
        }
        var tile = level.getBlockEntity(hitData.getPos());
        if (tile instanceof TileParallelController parallel && Config.topShowParallel()) {
            addParallel(box(info), parallel.getParallelism(), parallel.getMaxParallelism());
        }
        if (!(tile instanceof TileController controller)) return;

        String machineName = controller.getMachineName();
        info.text(Component.translatable("top.modularmachinery.machine",
                machineName == null || machineName.isEmpty() ? "-" : machineName));
        addOwnerAndStructure(info, controller, player, level);
        if (!controller.isFormed()) return;
        addEnergy(info, controller, player, level);
        addPerformance(info, controller);
        if (controller instanceof TileFactoryController factory) addFactory(info, factory, player);
        else addSingle(info, controller, player);
    }

    private static void addOwnerAndStructure(IProbeInfo info, TileController controller, Player player, Level level) {
        info = box(info);
        java.util.UUID owner = controller.getOwner();
        if (owner != null) {
            if (owner.equals(player.getUUID())) {
                info.text(Component.translatable("top.machine.owner").withStyle(ChatFormatting.AQUA)
                        .append(Component.translatable("top.machine.owner.self").withStyle(ChatFormatting.GREEN)));
            } else {
                String name = null;
                if (level.getServer() != null && level.getServer().getProfileCache() != null) {
                    name = level.getServer().getProfileCache().get(owner)
                            .map(com.mojang.authlib.GameProfile::getName).orElse(null);
                }
                info.text(Component.translatable("top.machine.owner").withStyle(ChatFormatting.AQUA).append(
                        name == null ? Component.translatable("top.machine.owner.unknown").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(" (UUID: " + owner + ")"))
                                : Component.literal(name).withStyle(ChatFormatting.RED)));
            }
        }
        if (controller.isFormed()) {
            info.text(Component.translatable("top.machine.structure.found").withStyle(ChatFormatting.GREEN));
            info.text(Component.translatable("top.modularmachinery.components", controller.getBoundComponentCount()));
        } else {
            info.text(Component.translatable("top.machine.structure.none").withStyle(ChatFormatting.RED));
            if (controller.getStatusMessage() != null) info.text(Component.translatable(controller.getStatusMessage()));
        }
    }

    private static void addSingle(IProbeInfo info, TileController controller, Player player) {
        IProbeInfo status = box(info);
        boolean working = controller.isCrafting() && !controller.isRedstoneStopped() && controller.isStructureChunkLoaded();
        status.text(Component.translatable(statusKey(controller, working, controller.getStatusMessage()))
                .withStyle(working ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (Config.topShowParallel() && controller.getMaxParallelism() > 1) {
            addParallel(box(info), controller.getParallelism(), controller.getMaxParallelism());
        }
        if (!controller.hasActiveRecipe() || controller.getRecipeDuration() <= 0) {
            return;
        }
        addProgress(status, controller.getRecipeTicks(), controller.getRecipeDuration(), working, player);
    }

    private static void addFactory(IProbeInfo info, TileFactoryController factory, Player player) {
        boolean enabled = !factory.isRedstoneStopped() && factory.isStructureChunkLoaded();
        box(info).text(Component.translatable(statusKey(factory, enabled && factory.getRunningThreadCount() > 0,
                factory.getStatusMessage())).withStyle(enabled && factory.getRunningThreadCount() > 0
                ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (factory.getFactoryParallelism() > 1) {
            addParallel(box(info), factory.getFactoryParallelism(), factory.getFactoryMaxParallelism());
        }
        List<FactoryRecipeThread> threads = factory.getFactoryRecipeThreadList();
        box(info).text(Component.translatable("top.factory.thread.running").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(": " + factory.getThreadCount() + " / " + factory.getMaxThreads())));
        // Named core threads come first, like upstream's separate core-thread collection.
        threads = threads.stream().sorted(java.util.Comparator.comparing(t -> !t.isPresetThread())).toList();
        int shown = 0;
        for (int i = 0; i < threads.size() && shown < 6; i++) {
            FactoryRecipeThread thread = threads.get(i);
            IProbeInfo threadBox = box(info);
            boolean working = enabled && thread.isWorking();
            var name = thread.isPresetThread() ? Component.translatable(thread.getThreadName()).withStyle(ChatFormatting.BLUE)
                    : Component.translatable("top.factory.thread").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(Integer.toString(i)));
            threadBox.text(name.append(Component.literal(": ")).append(Component.translatable(
                    statusKey(factory, working, thread.getBlockedReason()))
                    .withStyle(working ? ChatFormatting.GREEN : ChatFormatting.RED)));
            if (thread.hasActiveRecipe() && thread.getRecipeDuration() > 0) {
                addProgress(threadBox, thread.getRecipeTicks(), thread.getRecipeDuration(), working, player);
            }
            shown++;
        }
        if (threads.isEmpty()) info.text(Component.translatable("top.modularmachinery.idle"));
    }

    private static void addParallel(IProbeInfo info, int current, int max) {
        info.text(Component.translatable("top.parallelism").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(Integer.toString(current)).withStyle(ChatFormatting.GREEN)));
        info.text(Component.translatable("top.max_parallelism").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(Integer.toString(max)).withStyle(ChatFormatting.YELLOW)));
    }

    private static void addPerformance(IProbeInfo info, TileController controller) {
        if (controller.getPerformanceSamples() == 0) return;
        IProbeInfo performance = box(info);
        performance.text(Component.translatable("top.performance.cpu", controller.getAveragePerformanceMicros())
                .withStyle(ChatFormatting.AQUA));
        performance.text(Component.translatable("top.performance.search", controller.getAverageRecipeSearchMicros())
                .withStyle(ChatFormatting.AQUA));
    }

    private static void addProgress(IProbeInfo info, int ticks, int duration, boolean working, Player player) {
        int safeDuration = Math.max(1, duration);
        int percent = Math.max(0, Math.min(100, (int) ((long) Math.max(0, ticks) * 100L / safeDuration)));
        double exact = Math.max(0d, Math.min(100d, ticks * 100d / safeDuration));
        String text = Config.topShowDecimal() && safeDuration >= 1200
                ? String.format(Locale.ROOT, "%.2f%%", exact)
                : String.format(Locale.ROOT, "%.0f%%", exact);
        if (player.isShiftKeyDown()) {
            text = String.format(Locale.ROOT, "%.1f s / %.1f s", Math.max(0, ticks) / 20d, safeDuration / 20d);
        }
        IProbeInfo row = info.horizontal(info.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER));
        row.text(Component.translatable("top.recipe.progress").withStyle(ChatFormatting.AQUA).append(Component.literal(":  ")));
        int filled = working ? Config.topRecipeFilledColor() : Config.topFailureFilledColor();
        int alternate = working ? Config.topRecipeAlternateColor() : Config.topFailureAlternateColor();
        int border = working ? Config.topRecipeBorderColor() : Config.topFailureBorderColor();
        row.progress(percent, 100, info.defaultProgressStyle().prefix(text).filledColor(filled)
                .alternateFilledColor(alternate).borderColor(border).backgroundColor(Config.topRecipeBackgroundColor())
                .showText(true).numberFormat(NumberFormat.NONE));
    }

    private static IProbeInfo box(IProbeInfo info) {
        return info.vertical(info.defaultLayoutStyle().spacing(0).borderColor(0x801E90FF));
    }

    private static String statusKey(TileController controller, boolean working, String reason) {
        if (controller.isRedstoneStopped()) return "gui.controller.status.redstone_stopped";
        if (!controller.isStructureChunkLoaded()) return "gui.controller.status.chunk_unloaded";
        if (working) return "top.machine.working";
        return reason == null ? "gui.controller.status.idle" : reason;
    }

    /** Resolve the same modifier and isolation inputs as the ticker; never inspect or consume IO here. */
    private static void addEnergy(IProbeInfo info, TileController controller, Player player, Level level) {
        var loader = MachineReloadListener.getLoader();
        var recipes = MachineReloadListener.getRecipeLoader();
        if (!(level instanceof ServerLevel server) || loader == null || recipes == null) return;
        var machine = loader.findByName(controller.getMachineName());
        if (machine == null || !controller.hasActiveRecipe()) return;
        var state = controller.getBlockState();
        var facing = state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ? state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
        var context = new MachineContext(server, controller.getBlockPos(), facing, machine,
                loader.getVariables(), controller.getFormedPattern(machine));
        long[] totals = new long[2];
        if (controller instanceof TileFactoryController factory) {
            for (var thread : factory.getFactoryRecipeThreadList()) {
                if (!thread.hasActiveRecipe()) continue;
                context.selectIsolationGroup(thread.getIsolationGroupKey());
                collectEnergy(totals, MachineTicker.resolvedRun(machine, recipes.getRecipe(thread.getRecipeName()),
                        context, thread.activeModifiers()), thread.getParallelism());
            }
        } else {
            context.selectIsolationGroup(controller.getIsolationGroupKey());
            collectEnergy(totals, MachineTicker.resolvedRun(machine, recipes.getRecipe(controller.getRecipeName()), context),
                    controller.getParallelism());
        }
        if (totals[0] == 0 && totals[1] == 0) return;
        IProbeInfo energyBox = box(info);
        var unit = hellfirepvp.modularmachinery.client.util.EnergyDisplayUtil.type(); // Pure common-safe formatter.
        for (int i = 0; i < totals.length; i++) {
            if (totals[i] <= 0) continue;
            long amount = unit.formatEnergyForDisplay(totals[i]);
            String value = player.isShiftKeyDown() ? String.format(Locale.ROOT, "%,d", amount) : compactEnergy(amount);
            energyBox.text(Component.translatable(i == 0 ? "top.energy.input" : "top.energy.output")
                    .withStyle(ChatFormatting.AQUA).append(Component.literal(value + " ").withStyle(ChatFormatting.RED))
                    .append(Component.translatable(unit.getUnlocalizedFormat()).withStyle(ChatFormatting.RED))
                    .append(Component.literal("/t").withStyle(ChatFormatting.RED)));
        }
    }

    private static void collectEnergy(long[] totals, RecipeCraftingContext run, int parallelism) {
        if (run == null) return;
        for (var requirement : run.getRequirements()) {
            if (!(requirement instanceof RequirementEnergy energy)) continue;
            long amount = (long) Math.max(0, energy.getEnergyPerTick())
                    * (energy.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism));
            int index = energy.getIOType() == IOType.INPUT ? 0 : 1;
            totals[index] = amount > Long.MAX_VALUE - totals[index] ? Long.MAX_VALUE : totals[index] + amount;
        }
    }

    private static String compactEnergy(long amount) {
        if (amount < 1_000) return Long.toString(amount);
        double value = amount;
        String[] units = {"", "k", "M", "G", "T", "P", "E"};
        int index = 0;
        while (value >= 1_000 && index < units.length - 1) { value /= 1_000; index++; }
        return String.format(Locale.ROOT, "%.1f%s", value, units[index]);
    }
}
