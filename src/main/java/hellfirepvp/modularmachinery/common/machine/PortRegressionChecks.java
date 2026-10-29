/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.modifier.ModifierReplacement;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

 
public final class PortRegressionChecks {
    private PortRegressionChecks() {}

    public static boolean run(ServerLevel level, BlockPos origin) {
        try {
            hellfirepvp.modularmachinery.integration.recipes.ForeignRecipeRegressionChecks.run(level);
            EnergyOutputPortRegressionChecks.run(level, origin.offset(0, 0, 1952));
            if (net.minecraftforge.fml.ModList.get().isLoaded("embers"))
                hellfirepvp.modularmachinery.integration.embers.EmberRegressionChecks.run(level, origin.offset(0, 0, 1888));
            else {
                var block = hellfirepvp.modularmachinery.common.registry.RegistryBlocks.emberInput.get();
                if (block.newBlockEntity(origin, block.defaultBlockState()) != null)
                    throw new AssertionError("Ember placeholder created optional tile without Embers");
                for (var tier : hellfirepvp.modularmachinery.common.block.prop.EmberHatchSize.values())
                    if (level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("modularmachinery", "ember_input_" + tier.getSerializedName())).isPresent())
                        throw new AssertionError("Ember recipe loaded without Embers");
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft"))
                hellfirepvp.modularmachinery.integration.pneumatic.PressureRegressionChecks.run(level, origin.offset(0, 0, 1824));
            else {
                for (var block : List.of(hellfirepvp.modularmachinery.common.registry.RegistryBlocks.pressureInput.get(),
                        hellfirepvp.modularmachinery.common.registry.RegistryBlocks.pressureOutput.get()))
                    if (block.newBlockEntity(origin, block.defaultBlockState()) != null)
                        throw new AssertionError("Pressure placeholder created optional tile without PneumaticCraft");
                for (String id : List.of("pressure_input", "pressure_output"))
                    if (level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("modularmachinery", id)).isPresent())
                        throw new AssertionError("Pressure recipe loaded without PneumaticCraft: " + id);
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("botania"))
                hellfirepvp.modularmachinery.integration.botania.SparkManaRegressionChecks.run(level, origin.offset(0, 0, 1760));
            else {
                for (var block : List.of(hellfirepvp.modularmachinery.common.registry.RegistryBlocks.sparkManaInput.get(),
                        hellfirepvp.modularmachinery.common.registry.RegistryBlocks.sparkManaOutput.get()))
                    if (block.newBlockEntity(origin, block.defaultBlockState()) != null)
                        throw new AssertionError("Spark placeholder created optional tile without Botania");
                for (String id : List.of("spark_mana_input", "spark_mana_output"))
                    if (level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("modularmachinery", id)).isPresent())
                        throw new AssertionError("Spark recipe loaded without Botania: " + id);
            }
            EnvironmentRegressionChecks.run(level, origin.offset(0, 0, 1696));
            EnvironmentEditorRegressionChecks.run(level);
            if (net.minecraftforge.fml.ModList.get().isLoaded("gtceu"))
                hellfirepvp.modularmachinery.integration.gtceu.GTCEuEnergyRegressionChecks.run(level, origin.offset(0, 0, 1632));
            if (net.minecraftforge.fml.ModList.get().isLoaded("gtceu"))
                hellfirepvp.modularmachinery.integration.gtceu.GTCEuComponentRegressionChecks.run(level, origin.offset(0, 0, 1568));
            ComponentProxyRegressionChecks.run(level, origin.offset(0, 0, 1504));
            ControllerPerformanceRegressionChecks.run(level, origin.offset(0, 0, 1440));
            ControllerRuntimeLimitsRegressionChecks.run(level, origin.offset(0, 0, 1376));
            ControllerIdentityRegressionChecks.run(level, origin.offset(0, 0, 1312));
            StructureQueryRegressionChecks.run(level, origin.offset(0, 0, 1248));
            MultiDynamicUpgradeRegressionChecks.run(level, origin.offset(0, 0, 1184));
            UpgradeRuntimeRegressionChecks.run(level, origin.offset(0, 0, 1120));
            SmartInterfaceRuntimeRegressionChecks.run(level, origin.offset(0, 0, 1056));
            ControllerSpatialRegressionChecks.run(level, origin.offset(0, 0, 992));
            ControllerCustomDataRegressionChecks.run(level, origin.offset(0, 0, 928));
            RuntimeModifierRegressionChecks.run(level, origin.offset(0, 0, 864));
            RuntimeRecipeQueryRegressionChecks.run(level, origin.offset(0, 0, 800));
            GeoAnimationRegressionChecks.run(level, origin.offset(0, 0, 720));
            RecipeSortingRegressionChecks.run(level, origin.offset(0, 0, 640));
            StructureLifecycleRegressionChecks.run(level, origin.offset(0, 0, 560));
            AssemblyPerformanceRegressionChecks.run(level, origin.offset(0, 0, 272));
            checkReplacementPreserved(level, origin);
            checkSnapshots();
            checkRecipeValues();
            GlobalDefaultsRegressionChecks.run(level, origin.offset(0, 0, 240));
            RecipeDisplayNameRegressionChecks.run(level);
            BasicRequirementEditorRegressionChecks.run(level);
            EditorSaveConfirmationRegressionChecks.run(level);
            RecipeCopyRegressionChecks.run(level, origin.offset(0, 0, 2112));
            PneumaticEditorRegressionChecks.run(level);
            ResourceAmountEditorRegressionChecks.run(level);
            BloodAuraEditorRegressionChecks.run(level);
            RequirementNbtEditorRegressionChecks.run(level, origin.offset(0,0,2208));
            NestedNbtEditorRegressionChecks.run(level);
            InventoryIngredientEditorRegressionChecks.run(level);
            AdapterTemplateEditorRegressionChecks.run(level);
            ItemTagEditorRegressionChecks.run(level);
            IngredientGroupEditorRegressionChecks.run(level, origin.offset(0, 0, 32));
            RandomOutputEditorRegressionChecks.run(level, origin.offset(0, 0, 48));
            CatalystEditorRegressionChecks.run(level, origin.offset(0, 0, 64));
            DurabilityEditorRegressionChecks.run(level, origin.offset(0, 0, 80));
            FuelEditorRegressionChecks.run(level, origin.offset(0, 0, 96));
            SelectorEditorRegressionChecks.run(level, origin.offset(0, 0, 112));
            SmartInterfaceEditorRegressionChecks.run(level, origin.offset(0, 0, 128));
            FluidTickEditorRegressionChecks.run(level, origin.offset(0, 0, 144));
            RecipeOptionsEditorRegressionChecks.run(level, origin.offset(0, 0, 160));
            RecipeThreadEditorRegressionChecks.run(level, origin.offset(0, 0, 176));
            RecipePolicyEditorRegressionChecks.run(level, origin.offset(0, 0, 192));
            RecipeTooltipEditorRegressionChecks.run(level);
            CatalystTooltipEditorRegressionChecks.run(level);
            RequirementBatchEditorRegressionChecks.run(level, origin.offset(0, 0, 208));
            RequirementTriggerEditorRegressionChecks.run(level, origin.offset(0, 0, 224));
            ScriptRecipeRegressionChecks.run();
            if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                hellfirepvp.modularmachinery.integration.crafttweaker.RecipeBuilderRegressionChecks.run(
                        level, origin.offset(240, 0, 0));
            }
            if (hellfirepvp.modularmachinery.common.block.BlockMEGasBus.available()) {
                hellfirepvp.modularmachinery.integration.appmek.MEGasBusRegressionChecks.run(level, origin.offset(0, 0, 256));
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("botania")) {
                hellfirepvp.modularmachinery.integration.botania.ManaRegressionChecks.run(level, origin.offset(0, 0, 272));
                if (hellfirepvp.modularmachinery.common.block.BlockMEGasBus.available())
                    hellfirepvp.modularmachinery.integration.appmek.MixedResourceRegressionChecks.run(level, origin.offset(0, 0, 288));
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("bloodmagic")) {
                hellfirepvp.modularmachinery.integration.bloodmagic.LifeEssenceRegressionChecks.run(level, origin.offset(0, 0, 304));
                hellfirepvp.modularmachinery.integration.bloodmagic.WillRegressionChecks.run(level, origin.offset(0, 0, 336));
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("naturesaura"))
                hellfirepvp.modularmachinery.integration.naturesaura.AuraRegressionChecks.run(level, origin.offset(0, 0, 368));
            checkChunks();
            if (hellfirepvp.modularmachinery.common.integration.AstralIntegration.available())
                // Below the passive-collection height, so networking/decay checks do not depend on queued skylight updates.
                hellfirepvp.modularmachinery.integration.astral.AstralRegressionChecks.run(level, origin.offset(0, -80, 400));
            if (hellfirepvp.modularmachinery.integration.extrautilities.RainbowIntegration.available())
                hellfirepvp.modularmachinery.integration.extrautilities.RainbowRegressionChecks.run(level, origin.offset(0, 0, 432));
            EnergyHatchCapacityRegressionChecks.run(level, origin.offset(0, 0, 464));
            if (net.minecraftforge.fml.ModList.get().isLoaded("fluxnetworks"))
                hellfirepvp.modularmachinery.integration.fluxnetworks.FluxNetworksRegressionChecks.run(
                        level, origin.offset(0, 0, 528));
            DedicatedControllerRegressionChecks.run(level, origin.offset(0, 0, 496));
            NbtRegressionChecks.run(level, origin.offset(16, 0, 0));
            FluidTickRegressionChecks.run(level, origin.offset(32, 0, 0));
            DurabilityRegressionChecks.run(level, origin.offset(48, 0, 0));
            IngredientArrayRegressionChecks.run(level, origin.offset(64, 0, 0));
            FuelRegressionChecks.run(level, origin.offset(80, 0, 0));
            FactoryDistinctRecipeRegressionChecks.run(level, origin.offset(96, 0, 0));
            FactoryCoreThreadRegressionChecks.run(level, origin.offset(100, 0, 8));
            FactorySearchLifecycleRegressionChecks.run(level, origin.offset(108, 0, 8));
            CoordinateArrayRegressionChecks.run(level, origin.offset(112, 0, 0));
            MultiBlockModifierRegressionChecks.run(level, origin.offset(128, 0, 0));
            PerTickLifecycleRegressionChecks.run(level, origin.offset(136, 0, 0));
            SelectorTagRegressionChecks.run(level, origin.offset(144, 0, 0));
            StructureNbtRegressionChecks.run(level, origin.offset(160, 0, 0));
            UpgradeRegressionChecks.run();
            BlueprintPreviewRegressionChecks.run();
            BlueprintPreviewLevelRegressionChecks.run(level, origin);
            ControllerPreviewRegressionChecks.run(level, origin.offset(192, 0, 0));
            WorldProjectionRegressionChecks.run(level, origin.offset(200, 0, 8));
            WorldProjectionSceneRegressionChecks.run(level, origin.offset(208, 0, 8));
            StructureRotationRegressionChecks.run(level, origin.offset(216, 0, 8));
            if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
                MekanismGasRegressionChecks.run(level, origin.offset(224, 0, 0));
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
                hellfirepvp.modularmachinery.common.tiles.me.MEPatternMirrorRegressionChecks.run(
                        level, origin.offset(176, 0, 0));
                hellfirepvp.modularmachinery.common.tiles.me.MEFluidOutputMenuRegressionChecks.run(
                        level, origin.offset(184, 0, 0));
                hellfirepvp.modularmachinery.common.tiles.me.MEFluidInputMenuRegressionChecks.run(
                        level, origin.offset(188, 0, 0));
                hellfirepvp.modularmachinery.common.tiles.me.MEItemInputMenuRegressionChecks.run(
                        level, origin.offset(192, 0, 0));
                hellfirepvp.modularmachinery.common.tiles.me.MEItemOutputMenuRegressionChecks.run(
                        level, origin.offset(196, 0, 0));
                hellfirepvp.modularmachinery.common.tiles.me.MEFluidUpgradeRegressionChecks.run(
                        level, origin.offset(200, 0, 0));
                hellfirepvp.modularmachinery.common.tiles.me.MEBusComponentRegressionChecks.run(
                        level, origin.offset(208, 0, 0));
            }
            MachineLoader server = MachineReloadListener.getLoader();
            MachineReloadListener.installClientDefinitions(MachineReloadListener.Definitions.empty());
            require(MachineReloadListener.getLoader() == server,
                    "client snapshot must not replace integrated-server definitions");
            MachineReloadListener.clearClientDefinitions();
            // Must be safe even when the optional JEI classes are absent from the runtime.
            MachineReloadListener.notifyClientRecipesChanged();
            Modularmachinery.LOG.info("[MMCE][regression] PASS: replacement preservation, snapshot roundtrip, "
                    + "recipe values/NBT, bounded chunks, replacement/removal, side isolation, optional JEI dispatch, "
                    + "NBT parsing/modifiers/sync, real item/fluid IO, fluid-per-tick ordinary/factory lifecycle, "
                    + "durability input reservation/breakage/parallelism/persistence, ingredient arrays/mixed parallelism/shared reservation, fuel time/remainders/ordinary-factory lifecycle, one recipe identity per factory thread/batch multipliers/caps/reload, factory finish context/event destruction/reload wakeup/search backoff, coordinate arrays/static-dynamic-modifiers/rotation/build/sync, multiblock modifier all-cells/rotation semantics, generic per-tick lifecycle, selector tags/routed IO/shared reservations/ordinary-factory/reload, structure nbt matching/preview parsing, CraftTweaker upgrade builder/prototype/item-stack binding, AE2 fluid-bus five-slot capacity upgrades/NBT");
            return true;
        } catch (Throwable failure) {
            Modularmachinery.LOG.error("[MMCE][regression] FAIL", failure);
            return false;
        }
    }

    private static void checkReplacementPreserved(ServerLevel level, BlockPos origin) {
        MachineLoader loader = new MachineLoader();
        MachinePattern pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockcasing@2"));
        DynamicMachine machine = new DynamicMachine("regression", "Regression", pattern);
        ModifierReplacement replacement = ModifierReplacement.deserialize(JsonParser.parseString("""
                {"elements":"modularmachinery:blockcasing@1","x":1,"y":0,"z":0,
                 "modifier":{"target":"modularmachinery:item","io":"output","operation":1,"multiplier":2}}
                """).getAsJsonObject());
        machine.setModifierReplacements(List.of(replacement));
        var vent = loader.getResolver().resolveAccepted("modularmachinery:blockcasing@1").get(0);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos part = StructureMatcher.toWorldOffset(origin, facing, 1, 0, 0);
            var before = level.getBlockState(part);
            try {
                level.setBlock(part, vent, Block.UPDATE_ALL);
                require(loader.fillMissing(level, origin, facing, machine) == 0, "valid vent was rebuilt: " + facing);
                require(level.getBlockState(part).equals(vent), "vent was overwritten: " + facing);
                require(replacement.matches(level, origin, facing), "output modifier no longer applies");
                level.removeBlock(part, false);
                require(loader.fillMissing(level, origin, facing, machine) == 1, "missing base part was not built");
                require(new StructureMatcher().match(level, origin, facing, pattern, loader.getVariables(),
                        loader.getResolver(), machine.getModifierReplacements()).matched(), "rebuilt part does not match");
                require(loader.fillMissing(level, origin, facing, machine) == 0, "second build was not idempotent");
            } finally {
                level.setBlock(part, before, Block.UPDATE_ALL);
            }
        }
    }

    private static void checkSnapshots() {
        var server = MachineReloadListener.serverDefinitions();
        JsonObject wire = JsonParser.parseString(DefinitionSync.snapshot(server).toString()).getAsJsonObject();
        var clientMachines = MachineLoader.fromSnapshot(wire.getAsJsonObject("definitions"));
        var clientRecipes = RecipeLoader.fromSnapshot(wire.getAsJsonArray("recipes"), clientMachines);
        require(!clientMachines.getMachines().isEmpty(), "empty server snapshot cannot prove synchronization");
        require(clientMachines.snapshot().equals(server.machines().snapshot()), "machine definitions changed in transit");
        require(clientRecipes.getRecipeNames().equals(server.recipes().getRecipeNames()), "recipe set/order changed");
        int expanded = 0;
        for (String name : server.recipes().getRecipeNames()) {
            MachineRecipe a = server.recipes().getRecipe(name), b = clientRecipes.getRecipe(name);
            require(a != b && a.recipeTime() == b.recipeTime() && a.requirements().size() == b.requirements().size(),
                    "recipe was not copied independently: " + name);
            for (int i = 0; i < a.requirements().size(); i++) {
                require(a.requirements().get(i).describe().equals(b.requirements().get(i).describe()),
                        "requirement changed in transit: " + name);
            }
            if (name.contains("smelting_")) expanded++;
        }
        require(expanded > 0, "server did not publish expanded furnace recipes");
        JsonObject empty = new JsonObject();
        empty.add("variables", new JsonObject());
        empty.add("machines", new JsonObject());
        var cleared = MachineLoader.fromSnapshot(empty);
        require(cleared.getMachines().isEmpty() && cleared.getVariables().size() == 0
                        && RecipeLoader.fromSnapshot(new JsonArray(), cleared).getRecipeNames().isEmpty(),
                "empty reload did not remove previous definitions");
        require(!server.machines().getMachines().isEmpty(), "client clear mutated server registry");
    }

    private static void checkRecipeValues() {
        var item = new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 128, 0.25F);
        var recipe = new MachineRecipe("alloy_furnace", "snapshot_regression", 123,
                List.of(item, new RequirementFluid(IOType.INPUT, "minecraft:water", 3200, 0.5F),
                        new RequirementEnergy(IOType.INPUT, 1024), new RequirementInterfaceNumInput("voltage", 2, 5)),
                new ResourceLocation("minecraft:furnace"), true, 7, "core");
        JsonObject original = new JsonObject();
        original.addProperty("nbt", "{quality:3}");
        item.withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.read(original));
        MachineRecipe decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of(item, original)));
        var decodedItem = (RequirementItem) decoded.requirements().get(0);
        require(decodedItem.getAmount() == 128 && decodedItem.getChance() == 0.25F,
                "scaled adapter item count/probability truncated");
        ItemStack stack = new ItemStack(Items.DIAMOND);
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("quality", 3);
        stack.setTag(nbt);
        require(decodedItem.matches(stack), "NBT pattern lost after sync");
        nbt.putInt("quality", 4);
        require(!decodedItem.matches(stack), "NBT condition was broadened after sync");
        require(decoded.cancelOnPerTickFailure() && decoded.maxThreads() == 7
                && decoded.threadName().equals("core") && decoded.adapter().equals(recipe.adapter()), "recipe flags lost");
    }

    private static void checkChunks() {
        int size = DefinitionSyncPacket.CHUNK_BYTES + 73;
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) payload[i] = (byte) (i * 31);
        UUID transfer = UUID.randomUUID();
        var assembler = new DefinitionSyncAssembler();
        var first = new DefinitionSyncPacket(transfer, size, 0,
                Arrays.copyOf(payload, DefinitionSyncPacket.CHUNK_BYTES));
        var last = new DefinitionSyncPacket(transfer, size, DefinitionSyncPacket.CHUNK_BYTES,
                Arrays.copyOfRange(payload, DefinitionSyncPacket.CHUNK_BYTES, size));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DefinitionSyncPacket.encode(first, buffer);
            var decoded = DefinitionSyncPacket.decode(buffer);
            require(assembler.accept(decoded) == null, "partial snapshot published");
            require(Arrays.equals(payload, assembler.accept(last)), "chunk wire roundtrip changed bytes");
        } finally {
            buffer.release();
        }
        boolean refused = false;
        try { assembler.accept(last); } catch (IllegalArgumentException expected) { refused = true; }
        require(refused, "orphan chunk accepted");
        refused = false;
        try { new DefinitionSyncPacket(transfer, DefinitionSyncPacket.MAX_SNAPSHOT_BYTES + 1, 0, first.data()); }
        catch (IllegalArgumentException expected) { refused = true; }
        require(refused, "unbounded snapshot allocation accepted");
        assembler.accept(first);
        var replacement = new DefinitionSyncPacket(UUID.randomUUID(), 1, 0, new byte[]{42});
        require(Arrays.equals(new byte[]{42}, assembler.accept(replacement)), "new snapshot did not replace partial transfer");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
