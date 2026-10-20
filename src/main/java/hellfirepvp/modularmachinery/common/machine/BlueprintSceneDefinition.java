/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's preview pattern and upgrade ingredients.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.block.BlockController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Preview-only copies: optional upgrade cells must never become required gameplay pattern cells. */
public final class BlueprintSceneDefinition {
    public record Cell(MachinePattern.Part part, List<BlockState> states) { }
    public record Upgrade(String name, List<String> descriptions, List<RecipeModifier> modifiers,
                          Map<BlockPos, List<BlockState>> cells) { }
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int layerCount() { return maxY - minY + 1; }
        public int clampLayer(int layer) { return Math.max(minY, Math.min(maxY, layer)); }
    }
    private final Map<BlockPos, Cell> cells = new LinkedHashMap<>();
    private final List<Upgrade> upgrades = new ArrayList<>();
    private final Bounds bounds;

    public BlueprintSceneDefinition(DynamicMachine machine, MachinePattern expandedPattern,
                                    MachineVariables variables, BlockStateResolver resolver) {
        for (var part : expandedPattern.getParts()) {
            cells.put(new BlockPos(part.x(), part.y(), part.z()), new Cell(part,
                    List.copyOf(new LinkedHashSet<>(StructureMatcher.acceptedStates(
                            part, variables, resolver, machine.getModifierReplacements())))));
        }
        // The port allows both controller types for every machine; preserve that existing rule here.
        cells.put(BlockPos.ZERO, new Cell(new MachinePattern.Part(0, 0, 0, "modularmachinery:blockcontroller"),
                List.of(RegistryBlocks.blockController.get().defaultBlockState(),
                        RegistryBlocks.blockFactoryController.get().defaultBlockState())));
        for (var replacement : machine.getModifierReplacements()) {
            Map<BlockPos, List<BlockState>> offsets = new LinkedHashMap<>();
            offsets.put(new BlockPos(replacement.getX(), replacement.getY(), replacement.getZ()),
                    resolve(replacement.getBlockId(), variables, resolver));
            addUpgrade(new Upgrade(null, replacement.getDescription() == null ? List.of()
                    : List.of(replacement.getDescription()), replacement.getModifiers(), offsets));
        }
        for (var replacement : machine.getMultiBlockModifierReplacements()) {
            Map<BlockPos, List<BlockState>> offsets = new LinkedHashMap<>();
            for (var cell : replacement.getCells()) {
                offsets.put(cell.offset(), resolve(cell.blockId(), variables, resolver));
            }
            addUpgrade(new Upgrade(replacement.getName(), replacement.getDescriptions(),
                    replacement.getModifiers(), offsets));
        }
        // Include the origin controller and optional exterior upgrades, even when the base pattern omits them.
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (BlockPos pos : cells.keySet()) {
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }
        bounds = new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<BlockState> resolve(String expression, MachineVariables variables, BlockStateResolver resolver) {
        Set<BlockState> states = new LinkedHashSet<>();
        for (String bound : variables.resolve(expression)) states.addAll(resolver.resolveAccepted(bound));
        return List.copyOf(states);
    }

    private void addUpgrade(Upgrade upgrade) {
        upgrades.add(new Upgrade(upgrade.name(), List.copyOf(upgrade.descriptions()),
                List.copyOf(upgrade.modifiers()), Collections.unmodifiableMap(upgrade.cells())));
        upgrade.cells().forEach((pos, states) -> {
            if (pos.equals(BlockPos.ZERO)) return; // Keep the preview controller at its origin.
            Cell old = cells.get(pos);
            Set<BlockState> merged = new LinkedHashSet<>(old == null ? List.of() : old.states());
            merged.addAll(states);
            cells.put(pos, new Cell(old == null ? new MachinePattern.Part(pos.getX(), pos.getY(), pos.getZ(),
                    states.isEmpty() ? "minecraft:air" : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(states.get(0).getBlock()).toString()) : old.part(), List.copyOf(merged)));
        });
    }

    public Map<BlockPos, Cell> cells() { return Collections.unmodifiableMap(cells); }
    public List<Upgrade> upgrades() { return Collections.unmodifiableList(upgrades); }
    public Bounds bounds() { return bounds; }

    /** All alternatives for recipe lookup, distinct from the sampled, counted material summary. */
    public List<ItemStack> ingredientStacks() {
        List<ItemStack> result = new ArrayList<>();
        cells.forEach((pos, cell) -> {
            if (pos.equals(BlockPos.ZERO)) return; // The origin controller is already a JEI output.
            for (BlockState state : cell.states()) {
                addIngredient(result, state);
            }
        });
        return List.copyOf(result);
    }

    /** A stable JEI index must also include zero-minimum segments and materials hidden by overlaps at a given size. */
    public static List<ItemStack> ingredientStacks(DynamicMachine machine, MachineVariables variables, BlockStateResolver resolver) {
        List<ItemStack> result = new ArrayList<>(new BlueprintSceneDefinition(machine, machine.getPattern(), variables, resolver)
                .ingredientStacks());
        for (var dynamic : machine.getDynamicPatterns().values()) {
            for (MachinePattern template : new MachinePattern[]{dynamic.getPattern(), dynamic.getPatternEnd()}) {
                if (template == null) continue;
                for (var part : template.getParts()) {
                    // A segment's local origin is a real component after translation, not the machine controller.
                    for (BlockState state : StructureMatcher.acceptedStates(part, variables, resolver, java.util.List.of())) {
                        addIngredient(result, state);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addIngredient(List<ItemStack> result, BlockState state) {
        ItemStack stack = materialStack(state);
        if (!stack.isEmpty() && result.stream().noneMatch(s -> ItemStack.isSameItemSameTags(s, stack))) result.add(stack);
    }

    /** Flattened tiers share an Item; retain their placement tag so icons and names show the requested variant. */
    public static ItemStack materialStack(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            var bucket = state.getFluidState().getType().getBucket();
            if (bucket != net.minecraft.world.item.Items.AIR) return new ItemStack(bucket);
        }
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return stack;
        // These names describe MM component variants. Vanilla's chest/slab "type" is placement state,
        // and third-party items do not necessarily interpret BlockStateTag as an item variant either.
        if (!(state.getBlock() instanceof hellfirepvp.modularmachinery.common.block.BlockMachineComponent)) return stack;
        for (var property : state.getProperties()) {
            if (hellfirepvp.modularmachinery.common.block.BlockMachineComponent.VARIANT_PROPERTY_NAMES
                    .contains(property.getName())) {
                CompoundTag properties = stack.getOrCreateTagElement("BlockStateTag");
                properties.putString(property.getName(), propertyValue(state, property));
            }
        }
        return stack;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public Map<BlockPos, BlockState> sampleStates(long tick, boolean formed) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        cells.forEach((pos, cell) -> {
            BlockState state = PreviewCellDecision.sampleStateForPreview(cell.states(), tick);
            if (state == null || state.isAir()) return;
            if (pos.equals(BlockPos.ZERO) && state.hasProperty(BlockController.FORMED)) {
                state = state.setValue(BlockController.FORMED, formed);
            }
            states.put(pos, state);
        });
        return states;
    }

    public static List<BlockPos> visiblePositions(Map<BlockPos, BlockState> states, boolean allLayers,
                                                int layer, boolean formed, boolean hideComponents) {
        // Keep the detached controller data for GeckoLib, while suppressing its ordinary block model too.
        if (formed && hideComponents) return List.of();
        return states.keySet().stream().filter(pos -> allLayers || pos.getY() == layer).toList();
    }

    /** Recompute the union so disabling one upgrade cannot remove another upgrade's overlapping overlay. */
    public Set<BlockPos> highlightedPositions(Set<Integer> enabled) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int index : enabled) if (index >= 0 && index < upgrades.size()) {
            positions.addAll(upgrades.get(index).cells().keySet());
        }
        return positions;
    }
}
