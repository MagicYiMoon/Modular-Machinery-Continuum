/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.modifier.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.block.prop.ItemBusSize;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Geometry, optional-cell isolation and formed-state decisions run on a dedicated server without client classes. */
public final class BlueprintPreviewRegressionChecks {
    private BlueprintPreviewRegressionChecks() { }
    public static void run() {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockGetter view = new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return 4096; }
            @Override public int getMinBuildHeight() { return -2048; }
        };
        BlockPos back = BlockPos.ZERO, front = new BlockPos(0, 0, 2);
        blocks.put(back, Blocks.STONE.defaultBlockState());
        blocks.put(front, Blocks.GLASS.defaultBlockState());
        require(front.equals(BlueprintRayPicker.pick(new Matrix4f(), .5, .5, view, blocks.keySet())), "nearest visible shape");
        require(front.equals(BlueprintRayPicker.pick(new Matrix4f(), .5, .5, view, List.of(front, back))), "iteration order changes hit");
        require(back.equals(BlueprintRayPicker.pick(new Matrix4f().rotateY((float) Math.PI), -.5, .5, view, blocks.keySet())),
                "rotated depth order");
        require(BlueprintRayPicker.pick(new Matrix4f(), 1.1, .5, view, blocks.keySet()) == null, "empty space selected");
        blocks.put(front, Blocks.STONE_SLAB.defaultBlockState());
        require(back.equals(BlueprintRayPicker.pick(new Matrix4f(), .5, .75, view, blocks.keySet())), "empty upper half of slab occludes back block");
        require(front.equals(BlueprintRayPicker.pick(new Matrix4f(), .5, .25, view, blocks.keySet())), "lower slab shape not selected");
        require(back.equals(BlueprintRayPicker.pick(new Matrix4f(), .5, .25, view, List.of(back))), "excluded layer still picked");
        require(BlueprintRayPicker.pick(new Matrix4f(), .5, .25, view, List.of()) == null, "hidden component still picked");
        blocks.clear();
        BlockPos negative = new BlockPos(-7, -3, 2);
        blocks.put(negative, Blocks.STONE.defaultBlockState());
        for (int angle : new int[]{0, 45, 90, 135, 180, 270}) {
            Matrix4f transform = new Matrix4f().translate(173, 102, 100).scale(17, -17, -17)
                    .rotateX(.45F).rotateY((float) Math.toRadians(angle)).translate(7, 3, -2);
            Vector3f center = transform.transformPosition(new Vector3f(-6.5F, -2.5F, 2.5F));
            require(negative.equals(BlueprintRayPicker.pick(transform, center.x, center.y, view, blocks.keySet())),
                    "GUI scale, pan, pitch, negative offsets and yaw " + angle);
            for (float outerScale : new float[]{0.75F, 1F, 2F}) {
                Matrix4f panel = new Matrix4f().translate(341, 227, 12).scale(outerScale, outerScale, 1);
                Matrix4f screen = new Matrix4f(panel).mul(transform);
                Matrix4f relative = BlueprintRayPicker.relativeTransform(screen, panel);
                require(negative.equals(BlueprintRayPicker.pick(relative, center.x, center.y, view, blocks.keySet())),
                        "JEI local picking changed with recipe offset/scale, yaw " + angle);
                require(BlueprintRayPicker.pick(screen, center.x, center.y, view, blocks.keySet()) == null,
                        "JEI coordinate regression fixture does not distinguish raw screen coordinates");
                Vector3f absolute = screen.transformPosition(new Vector3f(-6.5F, -2.5F, 2.5F));
                require(negative.equals(BlueprintRayPicker.pick(screen, absolute.x, absolute.y, view, blocks.keySet())),
                        "relative picking conversion mutated the rendered transform");
            }
        }
        blocks.put(negative, Blocks.WATER.defaultBlockState());
        require(negative.equals(BlueprintRayPicker.pick(new Matrix4f(), -6.5, -2.5, view, blocks.keySet())),
                "fluid outline fallback did not select the visible water cell");
        blocks.put(negative, Blocks.AIR.defaultBlockState());
        require(BlueprintRayPicker.pick(new Matrix4f(), -6.5, -2.5, view, blocks.keySet()) == null, "air selected");

        MachinePattern pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        DynamicMachine machine = new DynamicMachine("blueprint_scene_regression", "Blueprint", pattern);
        var single = ModifierReplacement.deserialize(JsonParser.parseString("""
                {"elements":"minecraft:gold_block","x":1,"y":0,"z":0,"description":"Single upgrade",
                 "modifier":{"target":"modularmachinery:duration","io":"input","operation":1,"multiplier":0.5}}
                """).getAsJsonObject());
        machine.setModifierReplacements(List.of(single));
        BlockPos shared = new BlockPos(1, 0, 0), external = new BlockPos(-4, 2, 1);
        machine.addMultiBlockModifierReplacement(new MultiBlockModifierReplacement("Multi upgrade",
                List.of(new MultiBlockModifierReplacement.Cell(shared, "minecraft:diamond_block"),
                        new MultiBlockModifierReplacement.Cell(external, "minecraft:emerald_block")),
                single.getModifiers(), List.of("A two-cell condition")));
        BlueprintSceneDefinition scene = new BlueprintSceneDefinition(machine, pattern,
                new MachineVariables(), new BlockStateResolver());
        require(pattern.size() == 1 && machine.getPattern().size() == 1, "preview changed gameplay pattern");
        require(scene.cells().containsKey(BlockPos.ZERO) && scene.cells().containsKey(external), "missing controller/external upgrade cells");
        require(scene.cells().get(shared).states().containsAll(List.of(Blocks.STONE.defaultBlockState(),
                Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.DIAMOND_BLOCK.defaultBlockState())), "replacement candidates lost");
        require(scene.upgrades().size() == 2 && scene.upgrades().get(1).cells().size() == 2
                && scene.upgrades().get(1).descriptions().contains("A two-cell condition"), "multi-cell upgrade details lost");
        Set<Integer> enabled = new LinkedHashSet<>(List.of(0, 1));
        require(scene.highlightedPositions(enabled).equals(Set.of(shared, external)), "overlay union wrong");
        enabled.remove(0);
        require(scene.highlightedPositions(enabled).contains(shared), "overlapping overlay disappeared");
        enabled.clear();
        require(scene.highlightedPositions(enabled).isEmpty(), "disabled overlay persisted");
        var unformed = scene.sampleStates(0, false);
        var formed = scene.sampleStates(0, true);
        require(!unformed.get(BlockPos.ZERO).getValue(BlockController.FORMED)
                && formed.get(BlockPos.ZERO).getValue(BlockController.FORMED), "formed controller texture state not simulated");
        require(scene.cells().get(BlockPos.ZERO).states().stream().noneMatch(s -> s.getValue(BlockController.FORMED)),
                "simulation mutated controller candidates");
        require(BlueprintSceneDefinition.visiblePositions(formed, true, 0, true, true).isEmpty(), "hide-components includes ordinary controller model");
        require(BlueprintSceneDefinition.visiblePositions(unformed, true, 0, false, true).contains(external), "unformed did not restore hidden components");
        require(BlueprintSceneDefinition.visiblePositions(formed, false, 2, true, false).equals(List.of(external)), "layer filtering wrong");
        require(scene.sampleStates(39, false).equals(unformed), "candidate cycling too fast");
        require(!scene.sampleStates(40, false).equals(unformed), "candidate cycle stopped");
        BlockState vent = new BlockStateResolver().resolveAccepted("modularmachinery:blockcasing@1").get(0);
        require(BlueprintSceneDefinition.materialStack(vent).getTagElement("BlockStateTag") != null
                && BlueprintSceneDefinition.materialStack(vent).getTagElement("BlockStateTag").getString("casing")
                        .equals("vent"), "upgrade icon lost casing variant");
        BlockState bus = hellfirepvp.modularmachinery.common.registry.RegistryBlocks.itemInputBus.get().defaultBlockState();
        require(BlueprintSceneDefinition.materialStack(bus).getTagElement("BlockStateTag") != null
                && BlueprintSceneDefinition.materialStack(bus).getTagElement("BlockStateTag").contains("size"),
                "material icon lost bus size");
        checkMaterials(scene, external);
        checkMaterialPanel(scene, shared, external);
        checkMaterialRefreshLifecycle(scene, shared, external);
        checkIngredientIndex(scene);
        checkPreviewControls();
        checkPreviewInteraction(scene, shared, external);
        checkPreviewCamera();
        checkMachineInfo();
        checkLayerNavigation();
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint shape ray picking/occlusion/rotations/slabs/negative offsets, "
                + "optional scene isolation, controller formed texture, hidden components/layers, upgrade entries/overlapping overlays, 40-tick cycling");
    }

    private static void checkMaterials(BlueprintSceneDefinition scene, BlockPos external) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int i = 1; i <= 72; i++) states.put(new BlockPos(i, 0, 0), Blocks.STONE.defaultBlockState());
        states.put(new BlockPos(1, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        states.put(new BlockPos(2, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        states.put(new BlockPos(3, 1, 0), Blocks.GLASS.defaultBlockState());
        states.put(BlockPos.ZERO, RegistryBlocks.blockController.get().defaultBlockState());
        ItemStack shared = new ItemStack(Items.STONE, 32);
        var summary = BlueprintMaterials.summarize(states, (pos, state) -> state.is(Blocks.STONE)
                ? shared : BlueprintSceneDefinition.materialStack(state));
        require(summary.size() == 4 && summary.get(0).is(RegistryBlocks.blockController.get().asItem()), "controller not first");
        require(summary.get(1).is(Items.STONE) && summary.get(1).getCount() == 72, "large material count truncated or pick count multiplied");
        require(summary.get(2).is(Items.GOLD_BLOCK) && summary.get(2).getCount() == 2, "descending material counts wrong");
        require(shared.getCount() == 32, "aggregation modified pick stack");

        BlockState bus = RegistryBlocks.itemInputBus.get().defaultBlockState();
        Map<BlockPos, BlockState> tiers = Map.of(BlockPos.ZERO, bus.setValue(ItemBusSize.createProperty(), ItemBusSize.TINY),
                new BlockPos(1, 0, 0), bus.setValue(ItemBusSize.createProperty(), ItemBusSize.NORMAL));
        var tierSummary = BlueprintMaterials.summarize(tiers, (pos, state) -> BlueprintSceneDefinition.materialStack(state));
        require(tierSummary.size() == 2 && !ItemStack.isSameItemSameTags(tierSummary.get(0), tierSummary.get(1)),
                "different bus tiers merged");
        require(BlueprintSceneDefinition.materialStack(Blocks.WATER.defaultBlockState()).is(Items.WATER_BUCKET), "water bucket missing");
        require(BlueprintSceneDefinition.materialStack(Blocks.LAVA.defaultBlockState()).is(Items.LAVA_BUCKET), "lava bucket missing");
        require(BlueprintSceneDefinition.materialStack(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)).is(Items.OAK_SLAB),
                "waterlogged block mistaken for bucket");
        for (long tick : new long[]{0, 39, 40, 80}) {
            var sampled = scene.sampleStates(tick, false);
            var materials = BlueprintMaterials.summarize(sampled, (pos, state) -> BlueprintSceneDefinition.materialStack(state));
            require(materials.stream().mapToInt(ItemStack::getCount).sum() == sampled.size(), "counted every candidate instead of one per cell");
            require(materials.stream().anyMatch(stack -> stack.is(Items.EMERALD_BLOCK)), "external upgrade material omitted");
            require(materials.stream().noneMatch(stack -> stack.is(Items.GOLD_BLOCK)) || !sampled.get(new BlockPos(1, 0, 0)).is(Blocks.STONE),
                    "inactive replacement added to materials");
            require(BlueprintSceneDefinition.visiblePositions(sampled, false, external.getY(), false, false).size() == 1,
                    "test's filtered layer changed");
            require(materials.stream().mapToInt(ItemStack::getCount).sum() > 1, "material list incorrectly restricted to visible layer");
        }
        require(!ItemStack.isSameItemSameTags(
                        BlueprintMaterials.summarize(scene.sampleStates(0, false), (pos, state) -> BlueprintSceneDefinition.materialStack(state)).get(0),
                        BlueprintMaterials.summarize(scene.sampleStates(40, false), (pos, state) -> BlueprintSceneDefinition.materialStack(state)).get(0)),
                "material summary did not cycle controller candidate");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint material counts >64, controller/count ordering, immutable picks, "
                + "tier NBT separation, fluid buckets/waterlogged blocks, sampled alternatives/40-tick refresh, full-scene external upgrades/layers");
    }

    private static void checkIngredientIndex(BlueprintSceneDefinition upgrades) {
        var upgradeItems = upgrades.ingredientStacks();
        require(upgradeItems.size() == 4 && upgradeItems.stream().anyMatch(s -> s.is(Items.STONE))
                && upgradeItems.stream().anyMatch(s -> s.is(Items.GOLD_BLOCK))
                && upgradeItems.stream().anyMatch(s -> s.is(Items.DIAMOND_BLOCK))
                && upgradeItems.stream().anyMatch(s -> s.is(Items.EMERALD_BLOCK)),
                "JEI index lost inactive alternatives or external multi-block upgrade materials");
        require(upgradeItems.stream().noneMatch(s -> s.is(RegistryBlocks.blockController.get().asItem())
                || s.is(RegistryBlocks.blockFactoryController.get().asItem())), "origin controller indexed as input");
        require(upgrades.bounds().equals(new BlueprintSceneDefinition.Bounds(-4, 0, 0, 1, 2, 1))
                && upgrades.bounds().layerCount() == 3 && upgrades.bounds().clampLayer(99) == 2
                && upgrades.bounds().clampLayer(-99) == 0, "exterior upgrades missing from JEI bounds/layers");

        MachinePattern pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(4, -3, -2, "minecraft:oak_stairs"));
        pattern.addPart(new MachinePattern.Part(5, -3, -2, "minecraft:oak_stairs"));
        pattern.addPart(new MachinePattern.Part(6, -3, -2, "minecraft:water"));
        pattern.addPart(new MachinePattern.Part(7, -3, -2, "minecraft:lava"));
        pattern.addPart(new MachinePattern.Part(8, -3, -2, "modularmachinery:blockcasing@1"));
        pattern.addPart(new MachinePattern.Part(9, -3, -2,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(RegistryBlocks.itemInputBus.get()).toString()));
        pattern.addPart(new MachinePattern.Part(10, -3, -2, "minecraft:air"));
        pattern.addPart(new MachinePattern.Part(11, -3, -2, "minecraft:chest"));
        pattern.addPart(new MachinePattern.Part(12, -3, -2, "minecraft:oak_slab"));
        DynamicMachine machine = new DynamicMachine("jei_scene_index_regression", "JEI scene", pattern);
        BlueprintSceneDefinition definition = new BlueprintSceneDefinition(machine, pattern,
                new MachineVariables(), new BlockStateResolver());
        var inputs = definition.ingredientStacks();
        require(inputs.size() == 13 && inputs.stream().allMatch(s -> !s.isEmpty() && s.getCount() == 1),
                "JEI alternatives include air, repeated orientations/positions or wrong stack counts");
        require(inputs.stream().filter(s -> s.is(Items.OAK_STAIRS)).count() == 1
                && inputs.stream().filter(s -> s.is(Items.WATER_BUCKET)).count() == 1
                && inputs.stream().filter(s -> s.is(Items.LAVA_BUCKET)).count() == 1,
                "JEI stair orientation or fluid level alternatives did not deduplicate");
        require(inputs.stream().filter(s -> s.is(Items.CHEST)).count() == 1
                && inputs.stream().filter(s -> s.is(Items.OAK_SLAB)).count() == 1
                && inputs.stream().filter(s -> s.is(Items.CHEST) || s.is(Items.OAK_SLAB))
                        .allMatch(s -> s.getTag() == null), "vanilla placement type mistaken for an item variant");
        require(inputs.stream().filter(s -> s.is(RegistryBlocks.blockCasing.get().asItem())).anyMatch(s ->
                s.getTagElement("BlockStateTag") != null && s.getTagElement("BlockStateTag").getString("casing").equals("vent")),
                "JEI casing variant lost");
        Set<String> tiers = new HashSet<>();
        for (var stack : inputs) if (stack.is(RegistryBlocks.itemInputBus.get().asItem())) {
            var properties = stack.getTagElement("BlockStateTag");
            require(properties != null && properties.getAllKeys().equals(Set.of("size")),
                    "JEI bus input retained facing or other placement properties");
            tiers.add(properties.getString("size"));
        }
        require(tiers.equals(Set.of("tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous")),
                "JEI bus tiers collapsed or enum names were serialized in uppercase");
        inputs.get(0).setCount(32);
        inputs.get(0).getOrCreateTag().putString("test", "mutated");
        require(definition.ingredientStacks().stream().allMatch(s -> s.getCount() == 1
                && (s.getTag() == null || !s.getTag().contains("test"))), "JEI ingredient mutation changed shared scene data");
        require(definition.bounds().equals(new BlueprintSceneDefinition.Bounds(0, -3, -2, 12, 0, 0))
                && definition.bounds().layerCount() == 4 && definition.bounds().clampLayer(-99) == -3,
                "origin omitted from off-axis pattern bounds or negative layer lost");
        MachinePattern empty = new MachinePattern();
        var emptyDefinition = new BlueprintSceneDefinition(new DynamicMachine("empty_preview_regression", "Empty", empty),
                empty, new MachineVariables(), new BlockStateResolver());
        require(emptyDefinition.bounds().layerCount() == 1 && emptyDefinition.bounds().clampLayer(1) == 0
                && emptyDefinition.ingredientStacks().isEmpty(), "controller-only scene has invalid bounds/ingredients");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: JEI shared-scene ingredient index: all replacement/multi-block alternatives, "
                + "bucket/orientation/chest/slab deduplication, seven lowercase bus tiers, casing variants, controller/air exclusion, "
                + "immutable index picks, exterior/origin/negative/empty bounds and layer limits");
    }
    private static void checkPreviewControls() {
        MachineVariables variables = new MachineVariables();
        BlockStateResolver resolver = new BlockStateResolver();
        MachinePattern base = new MachinePattern();
        base.addPart(new MachinePattern.Part(1, -1, 0, "minecraft:stone"));
        base.addPart(new MachinePattern.Part(1, -2, 0, "minecraft:gold_block"));
        base.addPart(new MachinePattern.Part(4, 0, 0, "minecraft:iron_block"));
        DynamicMachine machine = new DynamicMachine("jei_preview_controls_regression", "Controls", base);
        DynamicPattern dynamic = new DynamicPattern("column");
        dynamic.setMinSize(1);
        dynamic.setMaxSize(3);
        dynamic.setStructureSizeOffsetStart(new BlockPos(1, -1, 0));
        dynamic.setStructureSizeOffset(new BlockPos(0, -1, 0));
        dynamic.getPattern().addPart(new MachinePattern.Part(0, 0, 0, "minecraft:emerald_block"));
        MachinePattern cap = new MachinePattern();
        cap.addPart(new MachinePattern.Part(0, 0, 0, "minecraft:diamond_block"));
        dynamic.setPatternEnd(cap);
        machine.addDynamicPattern(dynamic);
        BlueprintPreviewState state = new BlueprintPreviewState(machine, variables, resolver);
        require(state.dynamicSize() == 1 && state.layer() == -2 && state.cycling() && !state.formed(), "JEI preview defaults");
        state.updateClock(39);
        var frozen = state.definition().sampleStates(state.cycleTick(), state.formed());
        var projected = state.projectionSnapshot(40);
        require(projected.sampleTick() == 39 && projected.dynamicSize() == 1,
                "projection click resampled the next candidate phase or lost dynamic size");
        require(frozen.get(new BlockPos(1, -1, 0)).is(Blocks.EMERALD_BLOCK)
                && frozen.get(new BlockPos(1, -2, 0)).is(Blocks.DIAMOND_BLOCK), "minimum segment/cap layout");
        state.toggleCycle(40);
        state.updateClock(240);
        require(state.projectionSnapshot(240).sampleTick() == 39,
                "paused projection captured the live world clock");
        require(!state.cycling() && state.cycleTick() == 39
                && frozen.equals(state.definition().sampleStates(state.cycleTick(), state.formed())),
                "pause at cycle boundary changed the last displayed candidate");
        state.toggleFormed();
        require(state.definition().sampleStates(state.cycleTick(), state.formed()).get(BlockPos.ZERO).getValue(BlockController.FORMED),
                "JEI formed mode did not reach controller state");
        require(state.changeSize(Integer.MAX_VALUE) && state.dynamicSize() == 3, "size overflow/upper clamp");
        var large = state.definition().sampleStates(state.cycleTick(), state.formed());
        require(large.get(new BlockPos(1, -3, 0)).is(Blocks.EMERALD_BLOCK)
                && large.get(new BlockPos(1, -4, 0)).is(Blocks.DIAMOND_BLOCK)
                && large.get(new BlockPos(4, 0, 0)).is(Blocks.IRON_BLOCK), "expanded segment/cap/static wall layout");
        require(state.formed() && !state.cycling() && state.cycleTick() == 39, "resize lost formed or frozen mode");
        var projectedPattern = BlueprintPreviewState.buildPattern(machine, projected.dynamicSize());
        require(projected.sampleTick() == 39 && projectedPattern.getParts().stream().anyMatch(
                        p -> p.y() == -2 && p.elements().equals("minecraft:diamond_block"))
                        && projectedPattern.getParts().stream().noneMatch(p -> p.y() < -2),
                "resizing the source panel changed a captured projection's section/cap layout");
        var unchanged = state.definition();
        require(!state.changeSize(1) && unchanged == state.definition(), "upper-bound click rebuilt the scene");
        state.setLayer(-4);
        require(state.changeSize(-1) && state.dynamicSize() == 2 && state.layer() == -3
                && state.definition().sampleStates(39, true).get(new BlockPos(1, -3, 0)).is(Blocks.DIAMOND_BLOCK),
                "shrink left an old cap or invalid layer");
        require(state.changeSize(Integer.MIN_VALUE) && state.dynamicSize() == 1 && state.layer() == -2,
                "size underflow/lower clamp or negative-layer clamp");
        require(!state.changeSize(-1) && base.size() == 3 && base.getParts().stream().anyMatch(
                p -> p.y() == -2 && p.elements().equals("minecraft:gold_block")) && dynamic.getPattern().size() == 1,
                "preview resize mutated gameplay templates");
        state.toggleCycle(240);
        state.updateClock(280);
        require(state.cycling() && state.cycleTick() == 280 && state.formed(), "resume did not advance world clock or lost formed mode");
        require(projected.sampleTick() == 39 && state.projectionSnapshot(280).sampleTick() == 280,
                "resuming the source changed an old projection or failed to refresh a new projection");
        state.toggleFormed();
        require(!state.definition().sampleStates(state.cycleTick(), state.formed()).get(BlockPos.ZERO).getValue(BlockController.FORMED),
                "JEI unformed mode did not restore controller state");

        DynamicMachine multiple = new DynamicMachine("jei_multi_size_regression", "Multiple sizes", base);
        multiple.addDynamicPattern(dynamic);
        DynamicPattern fixed = new DynamicPattern("fixed");
        fixed.setMinSize(2);
        fixed.setMaxSize(2);
        fixed.setStructureSizeOffsetStart(new BlockPos(8, 0, 0));
        fixed.setStructureSizeOffset(new BlockPos(1, 0, 0));
        fixed.getPattern().addPart(new MachinePattern.Part(0, 0, 0, "minecraft:glass"));
        multiple.addDynamicPattern(fixed);
        BlueprintPreviewState multi = new BlueprintPreviewState(multiple, variables, resolver);
        require(multi.dynamicSize() == 2 && multi.definition().cells().containsKey(new BlockPos(1, -3, 0)),
                "source panel's shared maximum-of-minimums size not applied");
        multi.changeSize(1);
        var multiStates = multi.definition().sampleStates(0, false);
        require(multi.dynamicSize() == 3 && multiStates.containsKey(new BlockPos(1, -4, 0))
                && multiStates.get(new BlockPos(9, 0, 0)).is(Blocks.GLASS) && !multiStates.containsKey(new BlockPos(10, 0, 0)),
                "per-segment maximum clamp ignored");

        DynamicMachine zeroMachine = new DynamicMachine("jei_zero_size_regression", "Zero-size", new MachinePattern());
        DynamicPattern zero = new DynamicPattern("optional");
        zero.setMinSize(0);
        zero.setMaxSize(2);
        zero.setStructureSizeOffsetStart(new BlockPos(1, 0, 0));
        zero.setStructureSizeOffset(new BlockPos(1, 0, 0));
        zero.getPattern().addPart(new MachinePattern.Part(0, 0, 0, "minecraft:water"));
        MachinePattern zeroCap = new MachinePattern();
        zeroCap.addPart(new MachinePattern.Part(0, 0, 0, "minecraft:lava"));
        zero.setPatternEnd(zeroCap);
        zeroMachine.addDynamicPattern(zero);
        BlueprintPreviewState zeroState = new BlueprintPreviewState(zeroMachine, variables, resolver);
        require(zeroState.dynamicSize() == 0 && zeroState.definition().ingredientStacks().stream().noneMatch(s -> s.is(Items.WATER_BUCKET)),
                "zero-length preview contains a repeating section");
        var stableIndex = BlueprintSceneDefinition.ingredientStacks(zeroMachine, variables, resolver);
        require(stableIndex.size() == 2 && stableIndex.stream().anyMatch(s -> s.is(Items.WATER_BUCKET))
                && stableIndex.stream().anyMatch(s -> s.is(Items.LAVA_BUCKET)), "JEI index omitted zero-minimum template or cap");
        zeroState.changeSize(1);
        require(zeroState.definition().sampleStates(0, false).get(new BlockPos(1, 0, 0)).is(Blocks.WATER)
                && zeroState.definition().sampleStates(0, false).get(new BlockPos(2, 0, 0)).is(Blocks.LAVA),
                "zero-to-one segment expansion/cap layout");
        var allIndex = BlueprintSceneDefinition.ingredientStacks(machine, variables, resolver);
        require(allIndex.stream().anyMatch(s -> s.is(Items.STONE)) && allIndex.stream().anyMatch(s -> s.is(Items.GOLD_BLOCK))
                && allIndex.stream().anyMatch(s -> s.is(Items.EMERALD_BLOCK)) && allIndex.stream().anyMatch(s -> s.is(Items.DIAMOND_BLOCK)),
                "stable index depends on currently displayed overlaps/size");
        DynamicMachine plain = new DynamicMachine("jei_plain_size_regression", "Plain", new MachinePattern());
        BlueprintPreviewState plainState = new BlueprintPreviewState(plain, variables, resolver);
        require(plainState.dynamicSize() == 0 && !plainState.changeSize(1) && plainState.layer() == 0
                && BlueprintPreviewState.buildPattern(null, 0) == null, "non-dynamic/empty preview bounds");
        var beforeFirstFrame = new BlueprintPreviewState(machine, variables, resolver);
        beforeFirstFrame.toggleCycle(79);
        beforeFirstFrame.updateClock(80);
        beforeFirstFrame.toggleFormed();
        beforeFirstFrame.changeSize(1);
        require(beforeFirstFrame.cycleTick() == 79 && !beforeFirstFrame.cycling() && beforeFirstFrame.formed()
                        && beforeFirstFrame.dynamicSize() == 2, "pause before first draw lost its real clock/formed mode on rebuild");
        require(state.cycling() && !state.formed() && state.dynamicSize() == 1,
                "separate blueprint/JEI controls shared pause/formed/dynamic state");
        var undisplayed = new BlueprintPreviewState(plain, variables, resolver);
        require(undisplayed.projectionSnapshot(79).sampleTick() == 79,
                "projection before first draw used an uninitialized clock");
        undisplayed.updateClock(80);
        require(undisplayed.projectionSnapshot(120).sampleTick() == 80 && projected.sampleTick() == 39,
                "new projection retained another panel's candidate tick or advanced beyond its last draw");
        var atZero = new BlueprintPreviewState(plain, variables, resolver);
        require(atZero.projectionSnapshot(0).sampleTick() == 0 && atZero.projectionSnapshot(40).sampleTick() == 0,
                "tick zero treated as an unset or live-cycling snapshot");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: world projection snapshots: last displayed phase at boundary, "
                + "paused/unrendered/tick-zero capture, independent panels, immutable size/cap and tick after resize/resume");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint/JEI preview controls: formed/unformed, pause before first frame/at cycle boundary/resume, "
                + "resize while frozen/formed, segment/cap replacement, negative-layer clamp, size overflow/bounds, "
                + "source template isolation, shared/per-segment sizes, zero-minimum/stable ingredient index");
    }

    private static void checkPreviewInteraction(BlueprintSceneDefinition scene, BlockPos shared, BlockPos external) {
        BlueprintPreviewInteraction interaction = new BlueprintPreviewInteraction();
        require(!interaction.release(20, 30), "release without press selects a cell");
        interaction.press(20, 30);
        interaction.drag(23, 34);
        require(interaction.release(23, 34) && !interaction.pressed(), "small click jitter rejected");
        interaction.press(20, 30);
        interaction.drag(30, 30);
        interaction.drag(20, 30);
        require(!interaction.release(20, 30), "drag returning to its origin was mistaken for a click");
        interaction.press(20, 30);
        require(!interaction.release(26, 30), "release displacement ignored without a drag event");
        interaction.press(20, 30);
        interaction.cancel();
        require(!interaction.pressed() && !interaction.release(20, 30), "canceled/outside-panel gesture selected a block");
        interaction.press(20, 30, 1);
        require(interaction.pressedButton() == 1 && !interaction.release(20, 30, 0) && interaction.pressed(),
                "left release terminated a right-button pan");
        interaction.press(20, 30, 0);
        require(interaction.pressedButton() == 1, "second mouse button replaced the active gesture");
        require(interaction.release(23, 34, 1) && interaction.pressedButton() == -1, "right click jitter rejected");
        interaction.press(20, 30, 1);
        interaction.drag(40, 30);
        interaction.drag(20, 30);
        require(!interaction.release(20, 30, 1), "right drag returning to its origin selected a block");
        interaction.press(20, 30, 1);
        interaction.cancel();
        require(interaction.pressedButton() == -1 && !interaction.release(20, 30, 1), "canceled right pan remained active");
        interaction.press(20, 30, 2);
        require(!interaction.pressed(), "unsupported mouse button started a camera gesture");
        for (int button : new int[]{0, 1}) {
            for (boolean inside : new boolean[]{false, true}) for (boolean held : new boolean[]{false, true}) {
                interaction.press(20, 30, button);
                interaction.validateGesture(inside, held);
                require(interaction.pressed() == (inside && held), "viewport/physical release guard failed for button " + button);
                // Re-entering the viewport or pressing again cannot revive the canceled gesture.
                interaction.validateGesture(true, true);
                require(interaction.release(20, 30, button) == (inside && held), "canceled gesture selected after re-entry");
            }
        }

        var mutable = new BlockPos.MutableBlockPos(external.getX(), external.getY(), external.getZ());
        interaction.select(mutable, scene);
        mutable.set(90, 90, 90);
        require(external.equals(interaction.selected()), "selection retained a mutable ray position");
        interaction.retain(scene);
        require(external.equals(interaction.selected()), "unchanged scene discarded its selection");
        interaction.toggleUpgrade(0, scene);
        interaction.toggleUpgrade(1, scene);
        interaction.press(20, 30, 1);
        interaction.validateGesture(false, false);
        require(external.equals(interaction.selected()) && interaction.upgradeEnabled(0) && interaction.upgradeEnabled(1),
                "canceling a lost gesture discarded persistent selection/upgrades");
        require(interaction.highlightedPositions(scene, List.of(shared, external)).equals(Set.of(shared, external)),
                "single/multi-block upgrade overlay union wrong");
        interaction.toggleUpgrade(0, scene);
        require(interaction.upgradeEnabled(1) && interaction.highlightedPositions(scene, List.of(shared, external)).contains(shared),
                "disabling one upgrade removed another's overlapping position");
        require(interaction.highlightedPositions(scene, List.of(external)).equals(Set.of(external))
                && interaction.highlightedPositions(scene, List.of()).isEmpty() && interaction.upgradeEnabled(1),
                "filtered/hidden layers leaked overlays or cleared enabled upgrades");
        interaction.toggleUpgrade(-1, scene);
        interaction.toggleUpgrade(99, scene);
        require(!interaction.upgradeEnabled(-1) && !interaction.upgradeEnabled(99) && interaction.upgradeEnabled(1),
                "invalid upgrade index corrupted highlight state");
        BlueprintPreviewInteraction otherPanel = new BlueprintPreviewInteraction();
        require(otherPanel.selected() == null && !otherPanel.upgradeEnabled(1), "JEI entries shared selection/highlight state");
        interaction.select(null, scene);
        require(interaction.selected() == null, "empty-space click did not deselect");
        interaction.select(new BlockPos(90, 90, 90), scene);
        require(interaction.selected() == null, "nonexistent cell selected");
        interaction.select(external, scene);
        MachinePattern smallerPattern = new MachinePattern();
        smallerPattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        BlueprintSceneDefinition smaller = new BlueprintSceneDefinition(new DynamicMachine("selection_resize_regression", "Small", smallerPattern),
                smallerPattern, new MachineVariables(), new BlockStateResolver());
        interaction.retain(smaller);
        require(interaction.selected() == null && !interaction.upgradeEnabled(1), "removed cells/upgrades survived scene rebuild");
        interaction.select(shared, smaller);
        interaction.retain(smaller);
        require(shared.equals(interaction.selected()), "surviving selection cleared by scene rebuild");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint/JEI preview interaction: local/screen ray transforms at multiple offsets/scales/rotations, "
                + "fluid picking, click jitter/drag-return/release/cancel, stable/immutable/deselected/removed selection, "
                + "multi-upgrade overlap and visible-layer filtering, invalid-index cleanup, independent entries and both-button viewport/physical-release guards");
    }

    private static void checkMaterialPanel(BlueprintSceneDefinition scene, BlockPos shared, BlockPos external) {
        BlueprintMaterialState panel = new BlueprintMaterialState();
        require(panel.primary().isEmpty() && panel.material(0).isEmpty() && panel.alternative(0).isEmpty(), "new panel has stale materials");
        ItemStack clicked = new ItemStack(Items.STONE, 32);
        ItemStack variant = new ItemStack(Items.STONE);
        variant.getOrCreateTag().putInt("variant", 2);
        List<ItemStack> candidates = new ArrayList<>(List.of(clicked, variant, new ItemStack(Items.GOLD_BLOCK),
                new ItemStack(Items.DIAMOND_BLOCK), new ItemStack(Items.EMERALD_BLOCK), new ItemStack(Items.GLASS),
                new ItemStack(Items.GLASS), ItemStack.EMPTY));
        BlockPos.MutableBlockPos mutable = shared.mutable();
        panel.select(mutable, clicked, candidates);
        mutable.set(90, 90, 90);
        clicked.setCount(64);
        variant.getOrCreateTag().putInt("variant", 3);
        require(shared.equals(panel.selected()) && panel.primary().getCount() == 1 && panel.primary().is(Items.STONE),
                "main material did not snapshot the immutable click position/count");
        require(panel.alternative(0).getTag().getInt("variant") == 2 && panel.maxAlternativeScroll() == 2,
                "alternatives lost NBT variants, deduplication or snapshot isolation");
        panel.primary().setCount(99);
        panel.alternative(0).getOrCreateTag().putInt("variant", 99);
        require(panel.primary().getCount() == 1 && panel.alternative(0).getTag().getInt("variant") == 2,
                "material view exposed mutable snapshots");
        panel.scrollAlternatives(Integer.MAX_VALUE);
        require(panel.alternativeScroll() == 2 && panel.alternative(2).is(Items.GLASS), "last alternative page inaccessible");
        panel.scrollAlternatives(Integer.MIN_VALUE);
        require(panel.alternativeScroll() == 0 && panel.alternative(-1).isEmpty() && panel.alternative(3).isEmpty(),
                "alternative scrolling escaped bounds");

        Map<BlockPos, BlockState> sampled = new LinkedHashMap<>();
        for (int i = 0; i < 28; i++) sampled.put(new BlockPos(i, 0, 0), Blocks.STONE.defaultBlockState());
        panel.updateSummary(sampled, (pos, state) -> {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.getOrCreateTag().putInt("variant", pos.getX());
            return stack;
        });
        require(panel.maxMaterialScroll() == 2 && panel.material(17).getTag().getInt("variant") == 17,
                "two-row summary pagination wrong");
        panel.scrollMaterials(Integer.MAX_VALUE);
        require(panel.materialScroll() == 2 && panel.material(9).getTag().getInt("variant") == 27 && panel.material(10).isEmpty(),
                "partial last summary row incorrect");
        panel.scrollMaterials(1);
        require(panel.materialScroll() == 2, "summary scroll overflowed end");
        panel.scrollMaterials(Integer.MIN_VALUE);
        require(panel.materialScroll() == 0, "summary scroll overflowed beginning");
        panel.scrollMaterialTo(1.5);
        require(panel.materialScroll() == 2, "scrollbar did not reach last row");
        panel.scrollMaterialTo(-1);
        require(panel.materialScroll() == 0, "scrollbar did not clamp first row");
        require(panel.material(-1).isEmpty() && panel.material(18).isEmpty(), "invisible summary index exposed a material");
        panel.material(0).getOrCreateTag().putInt("variant", 99);
        require(panel.material(0).getTag().getInt("variant") == 0, "summary slot mutation changed source");

        for (int tick : new int[]{0, 40, 80}) {
            panel.updateSummary(scene.sampleStates(tick, true), (pos, state) -> BlueprintSceneDefinition.materialStack(state));
            require(panel.primary().is(Items.STONE) && panel.alternative(0).getTag().getInt("variant") == 2,
                    "cycling/formed summary changed captured selected materials");
            require(panel.materialScroll() == 0, "shorter summary retained an unreachable scroll row");
        }
        panel.retain(scene);
        require(shared.equals(panel.selected()) && panel.primary().is(Items.STONE) && panel.material(0).isEmpty(),
                "scene rebuild lost surviving selection or retained old totals");
        panel.refreshSelection(new ItemStack(Items.GOLD_BLOCK), List.of(new ItemStack(Items.STONE), new ItemStack(Items.GOLD_BLOCK)));
        require(panel.primary().is(Items.STONE) && panel.alternative(0).is(Items.GOLD_BLOCK) && panel.maxAlternativeScroll() == 0,
                "scene rebuild changed an accepted primary or retained old alternatives");
        panel.refreshSelection(new ItemStack(Items.DIAMOND_BLOCK), List.of(new ItemStack(Items.DIAMOND_BLOCK), new ItemStack(Items.GLASS)));
        require(shared.equals(panel.selected()) && panel.primary().is(Items.DIAMOND_BLOCK) && panel.alternative(0).is(Items.GLASS),
                "replacement cell retained an unaccepted primary material");
        panel.select(null, ItemStack.EMPTY, candidates);
        require(panel.selected() == null && panel.primary().isEmpty() && panel.alternative(0).isEmpty(), "deselect left material slots");
        panel.select(external, new ItemStack(Items.EMERALD_BLOCK), List.of(new ItemStack(Items.EMERALD_BLOCK)));
        MachinePattern smallerPattern = new MachinePattern();
        smallerPattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        panel.retain(new BlueprintSceneDefinition(new DynamicMachine("material_resize_regression", "Small", smallerPattern),
                smallerPattern, new MachineVariables(), new BlockStateResolver()));
        require(panel.selected() == null && panel.primary().isEmpty() && panel.maxAlternativeScroll() == 0,
                "removed cell retained material selection");
        require(new BlueprintMaterialState().primary().isEmpty(), "independent material panels shared selection");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: JEI material panel: clicked candidate/NBT snapshots, deduplicated alternatives, "
                + "immutable views, row/scrollbar boundaries and partial rows, cycling/formed totals preserve selection, "
                + "resize retains valid cells, refreshes replacement candidates and clears removed cells, empty/independent panels");
    }

    private static void checkMaterialRefreshLifecycle(BlueprintSceneDefinition definition, BlockPos shared, BlockPos external) {
        var source = new MaterialSourceFixture(definition);
        source.sample(0, false);
        var panel = new BlueprintMaterialState();
        require(panel.update(source), "initial scene did not populate its material summary");
        panel.select(source, shared);
        require(panel.primary().is(Items.STONE), "shared material source did not capture the displayed candidate");
        int lookups = source.lookups;
        require(!panel.update(source) && source.lookups == lookups, "unchanged scene repeated material pick hooks");
        source.sample(40, false);
        require(panel.update(source) && panel.primary().is(Items.STONE), "candidate cycle changed the captured main material");
        lookups = source.lookups;
        source.sample(40, true);
        require(panel.update(source) && source.lookups > lookups && panel.primary().is(Items.STONE),
                "formed change in the same candidate phase did not refresh totals or changed selection");
        source.materialVersion = 1;
        source.revision++;
        require(panel.update(source) && panel.material(0).getTag().getInt("material_version") == 1
                        && !panel.primary().hasTag(), "same-phase resource/model material change left stale totals or rewrote snapshot");
        require(!panel.update(source), "resource revision caused a refresh on every frame");

        var rebuilt = new MaterialSourceFixture(definition);
        rebuilt.sample(80, true);
        require(panel.update(rebuilt) && panel.primary().is(Items.STONE),
                "new scene with an already-used revision skipped refresh or replaced an accepted primary");
        lookups = rebuilt.lookups;
        panel.invalidate();
        require(panel.update(rebuilt) && rebuilt.lookups > lookups && panel.primary().is(Items.STONE),
                "invalidating a closed scene lost selection or did not force refresh");
        var independent = new BlueprintMaterialState();
        require(independent.update(rebuilt) && independent.selected() == null && panel.primary().is(Items.STONE),
                "independent blueprint/JEI material caches shared selection or refresh state");

        MachinePattern replacement = new MachinePattern();
        replacement.addPart(new MachinePattern.Part(shared.getX(), shared.getY(), shared.getZ(), "minecraft:emerald_block"));
        var replaced = new BlueprintSceneDefinition(new DynamicMachine("material_refresh_replacement", "Replaced", replacement),
                replacement, new MachineVariables(), new BlockStateResolver());
        panel.retain(replaced);
        var resized = new MaterialSourceFixture(replaced);
        resized.sample(80, true);
        require(panel.update(resized) && panel.primary().is(Items.EMERALD_BLOCK) && panel.alternative(0).isEmpty(),
                "resize retained an unaccepted clicked material or stale alternatives");
        panel.select(resized, external);
        require(panel.selected() == null && panel.primary().isEmpty(), "material source accepted a nonexistent selected cell");
        panel.select(source, external);
        require(external.equals(panel.selected()), "external upgrade material not selectable");
        require(panel.update(resized) && panel.selected() == null && panel.primary().isEmpty(),
                "switching sources left a removed selected cell");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint/JEI material refresh: revision caching and no repeated pick hooks, "
                + "cycle/formed/same-phase resource updates preserve clicked snapshots, same-revision scene replacement, "
                + "invalidation, accepted/replaced/removed cells and independent caches");
    }

    private static final class MaterialSourceFixture implements BlueprintMaterialState.Source {
        private final BlueprintSceneDefinition definition;
        private Map<BlockPos, BlockState> states = Map.of();
        private long revision;
        private int lookups, materialVersion;
        private MaterialSourceFixture(BlueprintSceneDefinition definition) { this.definition = definition; }
        private void sample(long tick, boolean formed) { states = definition.sampleStates(tick, formed); revision++; }
        @Override public BlueprintSceneDefinition definition() { return definition; }
        @Override public long revision() { return revision; }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public ItemStack materialStack(BlockPos pos, BlockState state) {
            lookups++;
            ItemStack stack = BlueprintSceneDefinition.materialStack(state);
            if (!stack.isEmpty() && materialVersion > 0) stack.getOrCreateTag().putInt("material_version", materialVersion);
            return stack;
        }
        @Override public List<ItemStack> materialStacks(BlockPos pos) {
            var cell = definition.cells().get(pos);
            return cell == null ? List.of() : cell.states().stream().map(state -> materialStack(pos, state)).toList();
        }
    }

    private static void checkPreviewCamera() {
        var bounds = new BlueprintSceneDefinition.Bounds(-7, -3, -2, 4, 8, 6);
        BlockPos target = new BlockPos(-7, -3, 2);
        BlockGetter view = new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) { return target.equals(pos) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return 4096; }
            @Override public int getMinBuildHeight() { return -2048; }
        };
        Vector3f point = new Vector3f(-6.5F, -2.5F, 2.5F);
        for (int sceneSpan : new int[]{85, 96}) {
            float anchorX = sceneSpan == 85 ? 90 : 303, anchorY = sceneSpan == 85 ? 74 : 229;
            var original = new Matrix4f().translate(anchorX, anchorY, 100);
            double x = 12, y = 12, z = 9;
            float oldScale = (float) (sceneSpan / Math.sqrt(x * x + y * y + z * z));
            original.scale(oldScale, -oldScale, -oldScale).rotateX((float) Math.toRadians(25)).rotateY((float) Math.toRadians(135))
                    .translate(1, -3, -2.5F);
            require(original.equals(cameraMatrix(new BlueprintPreviewCamera(sceneSpan), bounds, anchorX, anchorY), .0001F),
                    "shared camera changed the default framing/orientation for viewport " + sceneSpan);
            for (int yaw : new int[]{0, 45, 90, 135, 180, 270}) for (int pitch : new int[]{-80, 0, 25, 80})
                for (double zoomDelta : new double[]{-100, 0, 100}) {
                BlueprintPreviewCamera camera = new BlueprintPreviewCamera(sceneSpan);
                camera.rotate(yaw - camera.yaw(), pitch - camera.pitch());
                camera.zoomBy(zoomDelta);
                Matrix4f before = cameraMatrix(camera, bounds, anchorX, anchorY);
                Vector3f initial = before.transformPosition(new Vector3f(point));
                camera.pan(17, -11, bounds);
                Matrix4f after = cameraMatrix(camera, bounds, anchorX, anchorY);
                Vector3f moved = after.transformPosition(new Vector3f(point));
                require(Math.abs(moved.x - initial.x - 17) < .003 && Math.abs(moved.y - initial.y + 11) < .003
                                && Math.abs(moved.z - initial.z) < .003,
                        "right pan changed direction, distance or depth at yaw=" + yaw + ", pitch=" + pitch + ", zoom=" + camera.zoom());
                require(target.equals(BlueprintRayPicker.pick(after, moved.x, moved.y, view, List.of(target))),
                        "ray selection no longer follows a panned camera");
                Vector3f pannedCenter = camera.center(bounds);
                camera.rotate(37, 4);
                camera.zoomBy(1);
                require(camera.center(bounds).distance(pannedCenter) < .0001F, "rotating/zooming moved the displaced world center");
                float savedYaw = camera.yaw(), savedPitch = camera.pitch(), savedZoom = camera.zoom();
                camera.resetCenter();
                require(camera.yaw() == savedYaw && camera.pitch() == savedPitch && camera.zoom() == savedZoom,
                        "resetCenter discarded the chosen rotation/zoom");
                require(camera.center(bounds).distance(new BlueprintPreviewCamera().center(bounds)) < .0001F,
                        "resetCenter did not restore the structure's center");
                }
        }
        BlueprintPreviewCamera camera = new BlueprintPreviewCamera();
        Vector3f center = camera.center(bounds);
        camera.pan(12, 9, bounds);
        camera.pan(-12, -9, bounds);
        require(camera.center(bounds).distance(center) < .0001F, "opposite pan did not return to the original center");
        camera.pan(12, 9, bounds);
        Vector3f offset = camera.center(bounds).sub(center);
        var larger = new BlueprintSceneDefinition.Bounds(-7, -3, -2, 4, 8, 20);
        Vector3f resizedOffset = camera.center(larger).sub(new BlueprintPreviewCamera().center(larger));
        require(resizedOffset.distance(offset) < .0001F, "resizing discarded the camera center offset");
        camera.rotate(720000, 1000);
        camera.zoomBy(1000);
        require(camera.yaw() >= 0 && camera.yaw() < 360 && camera.pitch() == 89.9F && camera.zoom() == 3F,
                "camera rotation/zoom exceeded bounds");
        camera.rotate(0, -2000);
        camera.zoomBy(-2000);
        require(camera.pitch() == -89.9F && camera.zoom() == .3F, "negative camera limits wrong");
        require(new BlueprintPreviewCamera().center(bounds).distance(center) < .0001F, "independent entries shared a camera center");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint/JEI preview camera: both viewport framings/default transforms, "
                + "world-center pan and ray picking at 6 yaw/4 pitch/3 zoom settings in both viewports, "
                + "rotation/zoom preserve displaced center, recenter preserves angles/zoom, opposite drags, resize and bounds");
    }

    private static Matrix4f cameraMatrix(BlueprintPreviewCamera camera, BlueprintSceneDefinition.Bounds bounds, float x, float y) {
        float scale = camera.scale(bounds);
        Vector3f center = camera.center(bounds);
        return new Matrix4f().translate(x, y, 100).scale(scale, -scale, -scale).rotate(camera.rotation())
                .translate(-center.x, -center.y, -center.z);
    }

    private static void checkMachineInfo() {
        MachinePattern pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(-2, -3, -4, "minecraft:stone"));
        pattern.addPart(new MachinePattern.Part(2, 1, 6, "minecraft:gold_block"));
        DynamicMachine machine = new DynamicMachine("preview_machine_info_regression", "Info", pattern);
        var initial = new BlueprintSceneDefinition(machine, pattern, new MachineVariables(), new BlockStateResolver());
        Map<String, Object[]> defaults = infoArguments(machine, initial);
        require(Arrays.equals(defaults.get("xyz.1"), new Object[]{5L, 5L, 11L})
                        && Arrays.equals(defaults.get("controller_y_pos"), new Object[]{3L}), "basic machine dimensions/controller height wrong");
        require(!defaults.containsKey("internal_parallelism") && !defaults.containsKey("max_parallelism")
                        && !defaults.containsKey("core_threads") && !defaults.containsKey("dynamic_pattern")
                        && !defaults.containsKey("requires_blueprint"), "default machine displays inapplicable source info");
        require(Arrays.equals(defaults.get("max_threads"), new Object[]{machine.getMaxThreads()}), "factory-capable machine lost its thread count");
        machine.setInternalParallelism(8);
        machine.setMaxParallelism(64);
        machine.setMaxThreads(0);
        machine.addCoreThreadPreset("core_a", List.of("recipe_a"));
        machine.addCoreThreadPreset("core_b", List.of());
        machine.setRequiresBlueprint(true);
        machine.addMultiBlockModifierReplacement(new MultiBlockModifierReplacement("Outer",
                List.of(new MultiBlockModifierReplacement.Cell(new BlockPos(-8, -9, 7), "minecraft:emerald_block")),
                List.of(), List.of()));
        DynamicPattern dynamic = new DynamicPattern("tail");
        dynamic.setMinSize(1);
        dynamic.setMaxSize(3);
        dynamic.setStructureSizeOffsetStart(new BlockPos(0, 0, 8));
        dynamic.setStructureSizeOffset(new BlockPos(0, 0, 1));
        dynamic.getPattern().addPart(new MachinePattern.Part(0, 0, 0, "minecraft:stone"));
        machine.addDynamicPattern(dynamic);
        BlueprintPreviewState state = new BlueprintPreviewState(machine, new MachineVariables(), new BlockStateResolver());
        Map<String, Object[]> small = infoArguments(machine, state.definition());
        require(Arrays.equals(small.get("xyz.1"), new Object[]{11L, 11L, 13L})
                        && Arrays.equals(small.get("controller_y_pos"), new Object[]{3L}),
                "optional/dynamic bounds ignored or optional cells changed controller height");
        require(Arrays.equals(small.get("internal_parallelism"), new Object[]{8})
                        && Arrays.equals(small.get("max_parallelism"), new Object[]{64})
                        && Arrays.equals(small.get("max_threads"), new Object[]{0})
                        && Arrays.equals(small.get("core_threads"), new Object[]{2})
                        && small.containsKey("dynamic_pattern") && small.containsKey("requires_blueprint"),
                "configured machine info lost batching, zero dynamic threads, core thread count or feature flags");
        state.changeSize(2);
        require(Arrays.equals(infoArguments(machine, state.definition()).get("xyz.1"), new Object[]{11L, 11L, 15L}),
                "machine info dimensions did not follow expanded dynamic size");
        require(pattern.size() == 2 && machine.getInternalParallelism() == 8 && machine.getMaxThreads() == 0,
                "preview info changed gameplay definitions");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint/JEI machine info: current dynamic/optional dimensions, original controller height, "
                + "default field omission, configured parallelism, factory/core thread counts and blueprint/dynamic flags");
    }

    private static Map<String, Object[]> infoArguments(DynamicMachine machine, BlueprintSceneDefinition definition) {
        Map<String, Object[]> values = new LinkedHashMap<>();
        for (var line : BlueprintMachineInfo.tooltip(machine, definition)) {
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                    && translated.getKey().startsWith("gui.preview.button.machine_info")) {
                values.put(translated.getKey().replace("gui.preview.button.machine_info.", ""), translated.getArgs());
            }
        }
        return values;
    }

    private static void checkLayerNavigation() {
        for (var range : List.of(new BlueprintLayerRange(-12, -3), new BlueprintLayerRange(-4, 6),
                new BlueprintLayerRange(2, 17), new BlueprintLayerRange(0, 0), new BlueprintLayerRange(-7, -7))) {
            require(range.atFraction(0) == range.max() && range.atFraction(1) == range.min(), "layer scrollbar endpoints reversed");
            require(range.atFraction(-2) == range.max() && range.atFraction(3) == range.min(), "drag outside scrollbar wraps layer");
            require(range.step(range.max(), 1) == range.max() && range.step(range.min(), -1) == range.min(), "layer arrows exceed bounds");
            for (int y = range.min(); y <= range.max(); y++) {
                require(range.atFraction(range.fraction(y)) == y, "drag thumb cannot recover a relative Y layer");
                require(range.step(y, 1) >= y && range.step(y, -1) <= y, "layer arrows/wheel move in the wrong direction");
                if (y < range.max()) require(range.fraction(y + 1) < range.fraction(y), "higher layer moves thumb down");
            }
        }
        var wide = new BlueprintLayerRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        require(wide.count() == 4294967296L && wide.atFraction(0) == Integer.MAX_VALUE
                        && wide.atFraction(1) == Integer.MIN_VALUE, "wide layer coordinates overflow");
        require(wide.step(Integer.MAX_VALUE - 1, 8) == Integer.MAX_VALUE
                        && wide.step(Integer.MIN_VALUE + 1, -8) == Integer.MIN_VALUE, "layer stepping overflow");
        require(wide.atFraction(Double.POSITIVE_INFINITY) == Integer.MIN_VALUE
                        && wide.atFraction(Double.NEGATIVE_INFINITY) == Integer.MAX_VALUE
                        && wide.atFraction(Double.NaN) == Integer.MAX_VALUE, "invalid scroll coordinates escape bounds");
        var resized = new BlueprintLayerRange(-2, 3);
        require(resized.clamp(-12) == -2 && resized.clamp(17) == 3, "resize does not clamp old layer");
        require(new BlueprintLayerRange(1, 5).step(2, 1) == 3 && new BlueprintLayerRange(-6, -2).step(-4, -1) == -5,
                "separate previews share layer ranges");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint/JEI layer scrollbar: top-high/bottom-low, negative/positive/single layers, "
                + "bidirectional arrows/wheel, drag round trips and endpoints, outside drags, overflow, resize and independent ranges");
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
