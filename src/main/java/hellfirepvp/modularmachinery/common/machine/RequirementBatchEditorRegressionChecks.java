/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Real file edits followed by actual inventory, fluid, energy, rate, tool and fuel payments. */
public final class RequirementBatchEditorRegressionChecks {
    private RequirementBatchEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "requirement_batch_" + UUID.randomUUID().toString().replace("-", ""), other = id + "_other";
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        var otherFile = machineFile.resolveSibling(other + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RequirementBatch")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}"); Files.writeString(otherFile, Files.readString(machineFile));
            var source = new JsonObject(); source.addProperty("machine", id); source.addProperty("registryName", id);
            source.addProperty("recipeTime", 40); source.addProperty("authorNote", "recipe metadata");
            var tips = new JsonArray(); tips.add("independent explanation"); source.add("tooltips", tips);
            var requirements = new JsonArray();
            var advanced = BasicRequirementType.ITEM.create("minecraft:coal", false, 2, 1);
            advanced.add("nbt", JsonParser.parseString("{\"quality\":\">=3\"}")); advanced.add("nbt-display", JsonParser.parseString("{\"quality\":3}"));
            advanced.addProperty("selector-tag", "red"); advanced.addProperty("triggerTime", 4); advanced.addProperty("triggerRepeatable", true);
            advanced.addProperty("authorNote", "preserve advanced matching"); requirements.add(advanced);
            requirements.add(BasicRequirementType.ITEM.create("minecraft:iron_ingot", false, 2, 1));
            requirements.add(BasicRequirementType.ITEM.create("minecraft:diamond", true, 2, 1));
            requirements.add(BasicRequirementType.FLUID.create("minecraft:water", false, 100, 1));
            requirements.add(BasicRequirementType.FLUID.create("minecraft:lava", true, 50, 1));
            requirements.add(BasicRequirementType.ENERGY.create("", false, 20, 1));
            requirements.add(BasicRequirementType.ENERGY.create("", true, 10, 1));
            requirements.add(FluidTickEditor.create("minecraft:water", false, 10));
            requirements.add(FluidTickEditor.create("minecraft:lava", true, 5));
            requirements.add(DurabilityEditor.create("minecraft:iron_pickaxe", 2, 3, 1));
            requirements.add(FuelEditor.create(1600, 1));
            var inputGroup = IngredientGroupEditor.empty(); var coal = IngredientGroupEditor.candidate("minecraft:coal"); coal.addProperty("amount", 2);
            inputGroup.getAsJsonArray("items").add(coal); requirements.add(inputGroup);
            var outputGroup = IngredientGroupEditor.empty(true); var gold = IngredientGroupEditor.candidate("minecraft:gold_ingot", true);
            gold.addProperty("amount", 2); gold.addProperty("minAmount", 2); gold.addProperty("maxAmount", 2); outputGroup.getAsJsonArray("items").add(gold); requirements.add(outputGroup);
            var catalyst = CatalystEditor.empty(); catalyst.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:emerald")); requirements.add(catalyst);
            requirements.add(SmartInterfaceEditor.create("mode", 0, 1));
            if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
                for (String type : List.of("gas", "gas_pertick")) {
                    var gas = new JsonObject(); gas.addProperty("type", "modularmachinery:" + type); gas.addProperty("io-type", "input");
                    gas.addProperty("gas", "mekanism:hydrogen"); gas.addProperty("amount", 10); requirements.add(gas);
                }
            }
            source.add("requirements", requirements); Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(34, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            require(MachineReloadListener.getRecipeLoader().getRecipe(id) != null, "fixture including advanced input loads");
            require(req(snapshot(menu, id), 0).get("editableBatch").getAsBoolean(), "NBT and trigger requirements permit isolated batch edits");
            require(!req(snapshot(menu, id), 13).get("editableBatch").getAsBoolean()
                    && !req(snapshot(menu, id), 14).get("editableBatch").getAsBoolean(), "catalyst and smart conditions excluded");
            require(((RequirementCatalyst) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(13)).isParallelizeUnaffected(), "catalyst constructor default stays exempt");
            require(RequirementBatchEditor.policy(advanced) == RequirementBatchEditor.DEFAULT, "omitted policy distinct from explicit follow");
            String before = Files.readString(file); var local = RequirementBatchEditor.merge(advanced, RequirementBatchEditor.EXEMPT);
            require(!advanced.has(RequirementBatchEditor.KEY) && local.get(RequirementBatchEditor.KEY).getAsBoolean() && Files.readString(file).equals(before), "cancelled draft never writes");

            for (int policy : List.of(RequirementBatchEditor.EXEMPT, RequirementBatchEditor.FOLLOW, RequirementBatchEditor.DEFAULT)) {
                for (int index = 0; index < requirements.size(); index++) {
                    if (index == 13 || index == 14) continue;
                    var previous = read(file); save(player, new PktMachineEditorRequirementBatch(id, index, policy), true);
                    var expected = previous.deepCopy(); expected.getAsJsonArray("requirements").set(index, RequirementBatchEditor.merge(req(previous, index), policy));
                    require(read(file).equals(expected), "only batch field changed, metadata and all other rows preserved");
                    var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id); var runtime = (Requirement.Parallelizable) loaded.requirements().get(index);
                    require(runtime.isParallelizeUnaffected() == (policy == RequirementBatchEditor.EXEMPT), "JSON reload applies batch policy");
                    for (boolean chance : List.of(false, true)) {
                        var scaled = (Requirement.Parallelizable) loaded.requirements().get(index).scaled(1F, 1, loaded.requirements().get(index).getIOType(), chance);
                        require(scaled.isParallelizeUnaffected() == runtime.isParallelizeUnaffected(), "amount/chance modifier copies preserve exemption");
                    }
                    require(((Requirement.Parallelizable) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(index))
                            .isParallelizeUnaffected() == runtime.isParallelizeUnaffected(), "runtime snapshot preserves resolved exemption");
                    require(req(snapshot(menu, id), index).get("batchPolicy").getAsInt() == policy, "editor remembers default vs explicit setting");
                }
                checkRuntime(level, origin, MachineReloadListener.getRecipeLoader().getRecipe(id), policy == RequirementBatchEditor.EXEMPT);
            }
            require(!req(read(file), 1).has(RequirementBatchEditor.KEY), "restore default removes source field");
            before = Files.readString(file);
            for (int index : List.of(-1, 13, 14, requirements.size(), 512)) save(player, new PktMachineEditorRequirementBatch(id, index, 2), false);
            for (int policy : List.of(-1, 3, Integer.MAX_VALUE)) save(player, new PktMachineEditorRequirementBatch(id, 1, policy), false);
            require(Files.readString(file).equals(before), "invalid index/policy/unsupported types cannot write");
            var stale = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), new PktMachineEditorRequirementBatch(id, 1, 1));
            save(player, new PktMachineEditorRequirementBatch(id, 1, 2), true);
            require(!PktMachineEditorRequest.process(player, stale).result().success(), "stale editor revision rejected");
            var external = read(file); external.addProperty("authorNote", "external edit"); Files.writeString(file, external.toString());
            save(player, new PktMachineEditorRequirementBatch(id, 1, 1), false); require(read(file).equals(external), "full-source external change rejected"); reload(menu);
            for (var invalid : List.of(new JsonPrimitive("true"), new JsonPrimitive(1), JsonNull.INSTANCE, new JsonArray())) {
                var bad = external.deepCopy(); req(bad, 1).add(RequirementBatchEditor.KEY, invalid); Files.writeString(file, bad.toString()); reload(menu);
                require(MachineReloadListener.getRecipeLoader().getRecipe(id) == null, "invalid JSON boolean rejects recipe rather than silently ignoring field");
            }
            Files.writeString(file, external.toString()); reload(menu);
            var shared = read(file); var owners = new JsonArray(); owners.add(id); owners.add(other); shared.add("machine", owners);
            Files.writeString(file, shared.toString()); reload(menu); save(player, new PktMachineEditorRequirementBatch(id + "_sub_0", 1, 2), true);
            require(((Requirement.Parallelizable) MachineReloadListener.getRecipeLoader().getRecipe(id + "_sub_1").requirements().get(1)).isParallelizeUnaffected(), "shared file updates both variants");
            before = Files.readString(file);
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream().filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, new PktMachineEditorRequirementBatch(readonly, 0, 2), false);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BatchDenied")) { @Override public boolean hasPermissions(int n) { return false; } };
            denied.containerMenu = new MenuMachineEditor(34, denied.getInventory(), menu.snapshot()); save(denied, new PktMachineEditorRequirementBatch(id + "_sub_0", 1, 1), false);
            player.containerMenu = oldMenu;
            require(!PktMachineEditorRequest.process(player, new PktMachineEditorRequest(34, UUID.randomUUID(), "", new PktMachineEditorRequirementBatch(id + "_sub_0", 1, 1))).result().success(), "closed menu rejected");
            require(Files.readString(file).equals(before), "read-only/permission/window failures preserve source");
            Modularmachinery.LOG.info("[MMCE][requirement-batch-editor-regression] PASS: real wire/atomic field-only save/reload/default-vs-explicit/editor+runtime codecs, "
                    + "actual item/fluid/FE/rate/durability/fuel/alternative/weighted-output payments at batch four, metadata/NBT/triggers/selectors preserved, "
                    + "catalyst default/smart exclusion, optional gas policies, shared source and invalid/type/stale/external/permission/window/read-only rejection; UI needs client verification");
        } finally {
            player.containerMenu = oldMenu; for (var path : List.of(file, machineFile, otherFile)) Files.deleteIfExists(path); MachineReloadListener.reloadNow();
        }
    }

    private static void checkRuntime(ServerLevel level, BlockPos origin, MachineRecipe loaded, boolean exempt) {
        var blocks = List.of(RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.fluidInputHatch.get(),
                RegistryBlocks.fluidOutputHatch.get(), RegistryBlocks.energyInputHatch.get(), RegistryBlocks.energyOutputHatch.get());
        var positions = new BlockPos[6]; var saved = new BlockState[6]; int copies = exempt ? 1 : 4;
        try {
            var pattern = new MachinePattern();
            for (int i = 0; i < blocks.size(); i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i + 1, 0, 0); saved[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], blocks.get(i).defaultBlockState(), Block.UPDATE_ALL); pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, "minecraft:air"));
            }
            var machine = new DynamicMachine("batch_runtime", "Batch", pattern); var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[0])).getInventory();
            var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            for (int i = 0; i < input.getSlots(); i++) input.setStackInSlot(i, ItemStack.EMPTY);
            for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, ItemStack.EMPTY);
            var inFluid = (TileFluidTank) level.getBlockEntity(positions[2]); var outFluid = (TileFluidTank) level.getBlockEntity(positions[3]);
            var inEnergy = (TileEnergyHatch) level.getBlockEntity(positions[4]); var outEnergy = (TileEnergyHatch) level.getBlockEntity(positions[5]);
            var reqs = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements();
            for (var requirement : reqs) if (requirement instanceof Requirement.Parallelizable parallel) parallel.setParallelism(4);
            input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 32));
            require(MachineTicker.consumeInputs(List.of(reqs.get(1)), context, 4) && input.getStackInSlot(0).getCount() == 32 - 2 * copies, "saved item input exemption controls actual quantity");
            MachineTicker.emitOutputs(loaded, List.of(reqs.get(2)), context, 4);
            require(output.getStackInSlot(0).getCount() == 2 * copies, "saved item output exemption controls actual product count");
            for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            output.setStackInSlot(0, new ItemStack(Items.DIAMOND, 62));
            require((MachineTicker.outputAllocationFailure(context, List.of(reqs.get(2)), 4) == null) == exempt,
                    "output allocation reserves one exempt quantity rather than batch quantity");
            inFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 1000)); outFluid.getTank().setFluid(FluidStack.EMPTY);
            require(MachineTicker.consumeInputs(List.of(reqs.get(3)), context, 4) && inFluid.getTank().getFluidAmount() == 1000 - 100 * copies, "one-time fluid input exemption");
            MachineTicker.emitOutputs(loaded, List.of(reqs.get(4)), context, 4); require(outFluid.getTank().getFluidAmount() == 50 * copies, "one-time fluid output exemption");
            outFluid.getTank().setFluid(new FluidStack(Fluids.LAVA, outFluid.getTank().getCapacity() - 50));
            require((MachineTicker.outputAllocationFailure(context, List.of(reqs.get(4)), 4) == null) == exempt,
                    "fluid output allocation uses the same exempt quantity as emission");
            inFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 1000)); outFluid.getTank().setFluid(FluidStack.EMPTY);
            var rates = List.of(reqs.get(7), reqs.get(8));
            require(MachineTicker.processPerTick(single(rates), rates, context, 4, false) == null
                    && inFluid.getTank().getFluidAmount() == 1000 - 10 * copies && outFluid.getTank().getFluidAmount() == 5 * copies, "saved input/output mB/t exemptions");
            inEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(1000));
            outEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(0));
            require(reqs.get(6).canStart(context), "empty output hatch permits generator startup");
            var energy = List.of(reqs.get(5), reqs.get(6));
            require(MachineTicker.processPerTick(single(energy), energy, context, 4, false) == null
                    && inEnergy.getStoredEnergy() == 1000 - 20 * copies && outEnergy.getStoredEnergy() == 10 * copies,
                    "saved input/output FE/t exemptions: in=" + inEnergy.getStoredEnergy() + ", out=" + outEnergy.getStoredEnergy());
            inEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(1000));
            require(MachineTicker.processPerTick(single(List.of(reqs.get(5))), List.of(reqs.get(5)), context, 4, false) == null
                    && inEnergy.getStoredEnergy() == 1000 - 20 * copies, "simple input-only energy path honors exemption too");
            inEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(1000));
            outEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(outEnergy.getCapacity()));
            inFluid.getTank().setFluid(new FluidStack(Fluids.WATER, 1000)); outFluid.getTank().setFluid(FluidStack.EMPTY);
            var joint = List.of(reqs.get(5), reqs.get(6), reqs.get(7), reqs.get(8));
            require("craftcheck.failure.energy.output.space".equals(MachineTicker.processPerTick(single(joint), joint, context, 4, false))
                    && inEnergy.getStoredEnergy() == 1000 && inFluid.getTank().getFluidAmount() == 1000 && outFluid.getTank().isEmpty(),
                    "full energy output pauses before paying energy or fluid inputs");
            outEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(0));
            require(MachineTicker.processPerTick(single(joint), joint, context, 4, false) == null
                    && inEnergy.getStoredEnergy() == 1000 - 20 * copies && outEnergy.getStoredEnergy() == 10 * copies
                    && inFluid.getTank().getFluidAmount() == 1000 - 10 * copies, "energy generation and joint rates resume together");
            int heldEnergy = inEnergy.getStoredEnergy(), heldFluid = inFluid.getTank().getFluidAmount();
            require(MachineTicker.processPerTick(single(joint), joint, context, 4, true) == null
                    && inEnergy.getStoredEnergy() == heldEnergy && inFluid.getTank().getFluidAmount() == heldFluid
                    && outEnergy.getStoredEnergy() == 10 * copies, "completed waiting does not generate or pay another tick");
            outEnergy.getEnergyStorage().deserializeNBT(net.minecraft.nbt.IntTag.valueOf(outEnergy.getCapacity()));
            reqs.get(6).setIgnoreOutputCheck(true);
            require(MachineTicker.processPerTick(single(energy), energy, context, 4, false) == null
                    && outEnergy.getStoredEnergy() == outEnergy.getCapacity(), "ignore-output-check permits voiding excess FE");
            input.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
            require(MachineTicker.consumeInputs(List.of(reqs.get(9)), context, 4) && input.getStackInSlot(0).getDamageValue() == 6 * copies, "saved tool-use exemption");
            input.setStackInSlot(0, new ItemStack(Items.COAL, 8));
            require(MachineTicker.consumeInputs(List.of(reqs.get(10)), context, 4) && input.getStackInSlot(0).getCount() == 8 - copies, "saved fuel-budget exemption");
            input.setStackInSlot(0, new ItemStack(Items.COAL, 32));
            require(MachineTicker.consumeInputs(List.of(reqs.get(11)), context, 4) && input.getStackInSlot(0).getCount() == 32 - 2 * copies, "saved alternative-input exemption");
            for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, ItemStack.EMPTY);
            MachineTicker.emitOutputs(loaded, List.of(reqs.get(12)), context, 4); require(output.getStackInSlot(0).getCount() == 2 * copies, "saved weighted-output exemption");
        } finally { for (int i = 0; i < saved.length; i++) if (saved[i] != null) level.setBlock(positions[i], saved[i], Block.UPDATE_ALL); }
    }
    private static MachineRecipe single(List<Requirement> requirements) { return new MachineRecipe("batch_runtime", "batch_runtime", 40, requirements, null, false, -1, ""); }
    private static JsonObject req(JsonObject json, int index) { return json.getAsJsonArray("requirements").get(index).getAsJsonObject(); }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes")) if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing batch-editor snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu; var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation), buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "reload and authoritative synchronization precede confirmation");
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError("Requirement batch editor: " + message); }
}
