/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.assembly;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.BlockStateResolver;
import hellfirepvp.modularmachinery.common.machine.BlueprintPreviewState;
import hellfirepvp.modularmachinery.common.machine.BlueprintSceneDefinition;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachinePattern;
import hellfirepvp.modularmachinery.common.machine.MachineVariables;
import hellfirepvp.modularmachinery.common.machine.StructureMatcher;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.ArrayList;
import java.util.List;

/**
 * One source-compatible survival assembly job.  The original MMCE places one missing part every few
 * ticks and consumes the player's inventory; it does not replace occupied blocks or silently create
 * components.  The queue is server-side and is deliberately independent from machine formation.
 */
public final class MachineAssembly {
    /** A placement may be paid with a normal block item or with a filled fluid container. */
    private record Candidate(BlockState state, ItemStack material, FluidStack fluid) { }
    private record Task(BlockPos pos, MachinePattern.Part part, List<Candidate> candidates) { }

    private final ServerLevel level;
    private final BlockPos controllerPos;
    private final ServerPlayer player;
    private final java.util.Deque<Task> tasks = new java.util.ArrayDeque<>();
    private boolean paidWithFluid;
    private int ticks;

    private MachineAssembly(ServerLevel level, BlockPos controllerPos, ServerPlayer player) {
        this.level = level;
        this.controllerPos = controllerPos;
        this.player = player;
    }

    /** Starts a job, or reports why it cannot start. */
    public static MachineAssembly start(ServerLevel level, BlockPos controllerPos, ServerPlayer player,
                                        DynamicMachine machine, Direction facing, MachineLoader loader) {
        return start(level, controllerPos, player, machine, facing, loader, 0, false);
    }

    /** Starts a job, optionally using the source's creative-mode overwrite path. */
    public static MachineAssembly start(ServerLevel level, BlockPos controllerPos, ServerPlayer player,
                                        DynamicMachine machine, Direction facing, MachineLoader loader,
                                        boolean creative) {
        return start(level, controllerPos, player, machine, facing, loader, 0, creative);
    }

    /** Starts a job using the size selected by the client's blueprint projection. */
    public static MachineAssembly start(ServerLevel level, BlockPos controllerPos, ServerPlayer player,
                                        DynamicMachine machine, Direction facing, MachineLoader loader,
                                        int dynamicPatternSize, boolean creative) {
        if (machine == null || machine.getPattern() == null || machine.getPattern().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.assembly.tip.no_machine"), false);
            return null;
        }
        MachinePattern pattern = BlueprintPreviewState.buildPattern(machine, dynamicPatternSize);
        MachineAssembly assembly = new MachineAssembly(level, controllerPos, player);
        MachineVariables variables = loader.getVariables();
        BlockStateResolver resolver = loader.getResolver();

        for (MachinePattern.Part part : pattern.getParts()) {
            BlockPos pos = StructureMatcher.toWorldOffset(controllerPos, facing, part.x(), part.y(), part.z());
            // The controller is already in the world.  A definition may declare it at the origin.
            if (pos.equals(controllerPos)) continue;

            List<BlockState> accepted = StructureMatcher.acceptedStates(part, variables, resolver,
                    machine.getModifierReplacements());
            if (accepted.isEmpty()) {
                assembly.reportSkipped(pos, "unresolvable element " + part.elements());
                continue;
            }

            if (Config.autoAssemblySkipBlockContainingNbt()
                    && (part.nbtPattern() != null || part.previewNbt() != null)) {
                assembly.reportSkipped(pos, "part declares block-entity NBT");
                continue;
            }

            BlockState current = level.getBlockState(pos);
            if (!creative && StructureMatcher.matchesAny(current, accepted, facing)
                    && StructureMatcher.matchesPartNbt(level, pos, part)) {
                continue;
            }

            if (!creative && level.isOutsideBuildHeight(pos)) {
                player.displayClientMessage(Component.translatable("message.assembly.tip.too_high",
                        pos.toShortString()), false);
                assembly.reportSkipped(pos, "outside build height");
                continue;
            }
            if (!creative && !isReplaceable(level, pos, current)) {
                assembly.reportSkipped(pos, "occupied by " + current.getBlock().getName().getString());
                continue;
            }

            List<Candidate> candidates = new ArrayList<>();
            for (BlockState authored : accepted) {
                BlockState target = StructureMatcher.rotateState(authored, facing);
                ItemStack material = BlueprintSceneDefinition.materialStack(target);
                FluidStack fluid = fluidFor(target);
                if ((!material.isEmpty() || fluid != null) && target.getBlock() != Blocks.AIR) {
                    candidates.add(new Candidate(target, material, fluid));
                }
            }
            if (candidates.isEmpty()) {
                assembly.reportSkipped(pos, "no placeable item for " + part.elements());
                continue;
            }
            assembly.tasks.add(new Task(pos, part, List.copyOf(candidates)));
        }

        if (assembly.tasks.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.assembly.tip.success"), false);
            return null;
        }

        // Source AssemblyEventHandler takes the creative path before inspecting inventory.
        if (!creative) {
            List<Task> missing = assembly.missingMaterials();
            if (!missing.isEmpty()) {
                assembly.reportMaterials(missing);
                if (Config.autoAssemblyNeedAll()) return null;
                player.displayClientMessage(Component.translatable("message.assembly.tip.partial_assembly"), false);
            }
        }
        return assembly;
    }

    /**
     * Creative mode follows MMCE's source path: all declared cells are placed immediately and no
     * inventory is consulted.  This is intentionally separate from {@link #tick()}, which remains
     * one block per interval for survival players.
     */
    public void assembleCreative() {
        for (Task task : tasks) {
            Candidate candidate = task.candidates().isEmpty() ? null : task.candidates().get(0);
            if (candidate == null) continue;
            if (!level.isOutsideBuildHeight(task.pos())) {
                level.setBlock(task.pos(), candidate.state(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                CompoundNbt.apply(level, task.pos(), task.part().previewNbt());
            }
        }
        tasks.clear();
        level.playSound(null, controllerPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.assembly.tip.success"), false);
    }

    public boolean isFinished() {
        return tasks.isEmpty();
    }

    public ServerPlayer owner() {
        return player;
    }

    public ServerLevel level() { return level; }

    public BlockPos controllerPos() { return controllerPos; }

    public boolean controllerValid() {
        return level.getBlockEntity(controllerPos) instanceof TileController;
    }

    /** Places at most one block, matching the source's tick-by-tick queue. */
    public void tick() {
        if (++ticks < Config.autoAssemblyInterval()) return;
        ticks = 0;
        if (tasks.isEmpty()) return;

        Task task = tasks.removeFirst();
        if (level.isOutsideBuildHeight(task.pos())) {
            player.displayClientMessage(Component.translatable("message.assembly.tip.too_high",
                    task.pos().toShortString()), false);
            reportSkipped(task.pos(), "outside build height");
            if (tasks.isEmpty()) player.displayClientMessage(Component.translatable("message.assembly.tip.success"), false);
            return;
        }
        BlockState current = level.getBlockState(task.pos());
        if (isReplaceable(level, task.pos(), current)) {
            Candidate candidate = chooseCandidate(task.candidates());
            if (candidate == null) {
                reportMissing(task);
            } else if (consume(candidate)) {
                BlockState previous = current;
                BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, task.pos());
                level.setBlock(task.pos(), candidate.state(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                BlockEvent.EntityPlaceEvent placeEvent = new BlockEvent.EntityPlaceEvent(snapshot, previous, player);
                if (MinecraftForge.EVENT_BUS.post(placeEvent)) {
                    // The current block is the new placement. Forge's non-forced restore refuses
                    // a changed state, leaving a free block behind while its payment is refunded.
                    snapshot.restore(true);
                    refund(candidate);
                    reportSkipped(task.pos(), "placement event cancelled");
                } else {
                    CompoundNbt.apply(level, task.pos(), task.part().previewNbt());
                    returnContainer(candidate);
                    level.playSound(null, task.pos(), candidate.fluid() == null
                            ? SoundEvents.STONE_PLACE : SoundEvents.BUCKET_EMPTY,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            } else {
                reportMissing(task);
            }
        } else {
            reportSkipped(task.pos(), "occupied by " + current.getBlock().getName().getString());
        }

        if (tasks.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.assembly.tip.success"), false);
        }
    }

    private Candidate chooseCandidate(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            if (canConsume(candidate)) return candidate;
        }
        return candidates.get(0);
    }

    private List<Task> missingMaterials() {
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) simulated.add(stack.copy());
        List<Task> missing = new ArrayList<>();
        for (Task task : tasks) {
            if (!consumeFrom(simulated, task.candidates())) missing.add(task);
        }
        return missing;
    }

    private boolean consume(Candidate candidate) {
        paidWithFluid = false;
        if (!candidate.material().isEmpty() && consumeItem(candidate.material(), player.getInventory().items)) {
            player.getInventory().setChanged();
            return true;
        }
        paidWithFluid = candidate.fluid() != null && consumeFluid(candidate.fluid(), player.getInventory().items);
        if (paidWithFluid) player.getInventory().setChanged();
        return paidWithFluid;
    }

    /** Restores a consumed ingredient when a Forge placement protection event rejects the block. */
    private void refund(Candidate candidate) {
        if (!paidWithFluid && !candidate.material().isEmpty()) {
            player.getInventory().add(candidate.material().copy());
            return;
        }
        if (candidate.fluid() != null) fillFluid(candidate.fluid(), player.getInventory().items);
    }

    private void returnContainer(Candidate candidate) {
        if (paidWithFluid || candidate.fluid() == null || !(candidate.material().getItem() instanceof BucketItem)) return;
        ItemStack empty = BucketItem.getEmptySuccessItem(candidate.material().copyWithCount(1), player);
        if (!empty.isEmpty() && !player.getInventory().add(empty)) player.drop(empty, false);
    }

    private boolean canConsume(Candidate candidate) {
        if (!candidate.material().isEmpty()) {
            for (ItemStack stack : player.getInventory().items) {
                if (matchesMaterial(stack, candidate.material())
                        && stack.getCount() >= candidate.material().getCount()) return true;
            }
        }
        return candidate.fluid() != null && canConsumeFluid(candidate.fluid(), player.getInventory().items);
    }

    private static boolean consumeItem(ItemStack wanted, List<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (matchesMaterial(stack, wanted) && stack.getCount() >= wanted.getCount()) {
                stack.shrink(wanted.getCount());
                return true;
            }
        }
        return false;
    }

    /** Flattened component tiers replace source metadata; an untagged base-tier item is still valid. */
    private static boolean matchesMaterial(ItemStack supplied, ItemStack wanted) {
        if (!supplied.is(wanted.getItem())) return false;
        if (!(net.minecraft.world.level.block.Block.byItem(wanted.getItem()) instanceof
                hellfirepvp.modularmachinery.common.block.BlockMachineComponent block))
            return ItemStack.isSameItemSameTags(supplied, wanted);
        var actual = supplied.getTagElement("BlockStateTag");
        var required = wanted.getTagElement("BlockStateTag");
        for (var property : block.defaultBlockState().getProperties()) {
            String name = property.getName();
            if (!hellfirepvp.modularmachinery.common.block.BlockMachineComponent.VARIANT_PROPERTY_NAMES.contains(name)) continue;
            String fallback = propertyValue(block.defaultBlockState(), property);
            String a = actual != null && actual.contains(name) ? actual.getString(name) : fallback;
            String b = required != null && required.contains(name) ? required.getString(name) : fallback;
            if (!a.equals(b)) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static boolean consumeFrom(List<ItemStack> inventory, List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            if (!candidate.material().isEmpty() && consumeItem(candidate.material(), inventory)) return true;
            if (candidate.fluid() != null && consumeFluid(candidate.fluid(), inventory)) return true;
        }
        return false;
    }

    private static boolean consumeFluid(FluidStack wanted, List<ItemStack> inventory) {
        return drainFluid(wanted, inventory, FluidAction.EXECUTE);
    }

    private static boolean canConsumeFluid(FluidStack wanted, List<ItemStack> inventory) {
        return drainFluid(wanted, inventory, FluidAction.SIMULATE);
    }

    private static boolean drainFluid(FluidStack wanted, List<ItemStack> inventory, FluidAction action) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            IFluidHandlerItem handler = FluidUtil.getFluidHandler(stack).resolve().orElse(null);
            if (handler == null) continue;
            // Never execute a partial drain while searching other containers.
            FluidStack drained = handler.drain(wanted.copy(), FluidAction.SIMULATE);
            if (drained != null && drained.getAmount() >= wanted.getAmount()
                    && drained.isFluidEqual(wanted)) {
                if (action.execute()) {
                    handler.drain(wanted.copy(), FluidAction.EXECUTE);
                    inventory.set(i, handler.getContainer());
                }
                return true;
            }
        }
        return false;
    }

    private static boolean fillFluid(FluidStack wanted, List<ItemStack> inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            IFluidHandlerItem handler = FluidUtil.getFluidHandler(stack).resolve().orElse(null);
            if (handler == null) continue;
            if (handler.fill(wanted.copy(), FluidAction.SIMULATE) >= wanted.getAmount()) {
                handler.fill(wanted.copy(), FluidAction.EXECUTE);
                inventory.set(i, handler.getContainer());
                return true;
            }
        }
        return false;
    }

    private static FluidStack fluidFor(BlockState state) {
        if (state == null || state.getFluidState().isEmpty()) return null;
        var fluid = state.getFluidState().getType();
        return fluid == null ? null : new FluidStack(fluid, 1000);
    }

    private static boolean isReplaceable(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isOutsideBuildHeight(pos)) return false;
        // BlockState#canBeReplaced covers ordinary air/plants and many mod blocks.  Fluid source and
        // flowing states are explicitly accepted by the source assembler even when a custom fluid
        // block reports false from canBeReplaced.
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    private void reportMissing(Task task) {
        player.displayClientMessage(Component.translatable("message.assembly.tip.missing", task.pos().toShortString()), false);
        reportMaterials(List.of(task));
        reportSkipped(task.pos(), "missing material");
    }

    /** Source PktAssemblyReport lists every outstanding group, including alternatives and fluid amounts. */
    private void reportMaterials(List<Task> missing) {
        player.displayClientMessage(Component.translatable("message.assembly.tip.required"), false);
        var groups = new java.util.LinkedHashMap<String, List<Task>>();
        for (Task task : missing) {
            String key = task.candidates().stream().map(candidate -> candidate.material().save(new net.minecraft.nbt.CompoundTag())
                    + ":" + (candidate.fluid() == null ? "" : candidate.fluid().writeToNBT(new net.minecraft.nbt.CompoundTag())))
                    .distinct().collect(java.util.stream.Collectors.joining("|"));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(task);
        }
        for (List<Task> group : groups.values()) {
            var alternatives = new java.util.LinkedHashMap<String, Component>();
            for (Candidate candidate : group.get(0).candidates()) {
                Component line;
                String key;
                if (candidate.fluid() != null) {
                    key = candidate.fluid().writeToNBT(new net.minecraft.nbt.CompoundTag()).toString();
                    line = Component.literal((long) group.size() * candidate.fluid().getAmount() + " mB ")
                            .append(candidate.fluid().getDisplayName());
                } else {
                    key = candidate.material().save(new net.minecraft.nbt.CompoundTag()).toString();
                    line = Component.literal((long) group.size() * candidate.material().getCount() + "x ")
                            .append(candidate.material().getHoverName());
                }
                alternatives.putIfAbsent(key, line);
            }
            var line = Component.empty();
            for (Component choice : alternatives.values()) {
                if (!line.getSiblings().isEmpty()) line.append(" ").append(Component.translatable("message.assembly.tip.or")).append(" ");
                line.append(choice);
            }
            player.displayClientMessage(line, false);
        }
    }

    private void reportSkipped(BlockPos pos, String reason) {
        player.displayClientMessage(Component.translatable("message.assembly.tip.skipped", pos.toShortString())
                .append(Component.literal(" (" + reason + ")")), false);
    }

    /** Small isolated NBT helper; malformed preview tags must not abort the queue. */
    private static final class CompoundNbt {
        private static void apply(ServerLevel level, BlockPos pos, net.minecraft.nbt.CompoundTag tag) {
            if (tag == null) return;
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null) return;
            try {
                entity.load(tag.copy());
                entity.setChanged();
            } catch (RuntimeException ex) {
                Modularmachinery.LOG.warn("[MMCE] Could not apply assembly preview NBT at {}", pos, ex);
            }
        }
    }
}
