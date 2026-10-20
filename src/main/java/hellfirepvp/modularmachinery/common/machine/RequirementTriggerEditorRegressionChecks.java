/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
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
import java.util.function.*;

/** Real authenticated saves followed by input payments using both controller trigger ledgers. */
public final class RequirementTriggerEditorRegressionChecks {
    private RequirementTriggerEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "requirement_trigger_" + UUID.randomUUID().toString().replace("-", ""), other = id + "_other";
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        var otherFile = machineFile.resolveSibling(other + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TriggerEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}"); Files.writeString(otherFile, Files.readString(machineFile));
            var source = new JsonObject(); source.addProperty("machine", id); source.addProperty("registryName", id);
            source.addProperty("recipeTime", 40); source.addProperty("authorNote", "keep recipe metadata");
            var rows = new JsonArray();
            var advanced = BasicRequirementType.ITEM.create("minecraft:coal", false, 2, 1);
            advanced.add("nbt", JsonParser.parseString("{\"quality\":\">=3\"}"));
            advanced.addProperty("selector-tag", "red"); advanced.addProperty("parallelizeUnaffected", true);
            advanced.addProperty("authorNote", "keep requirement metadata"); rows.add(advanced);
            rows.add(BasicRequirementType.ITEM.create("minecraft:iron_ingot", false, 2, 1));
            rows.add(BasicRequirementType.FLUID.create("minecraft:water", false, 100, 1));
            rows.add(DurabilityEditor.create("minecraft:iron_pickaxe", 1, 3, 1));
            rows.add(FuelEditor.create(1600, 1));
            var group = IngredientGroupEditor.empty(); group.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:coal")); rows.add(group);
            rows.add(BasicRequirementType.ITEM.create("minecraft:diamond", true, 1, 1));
            rows.add(BasicRequirementType.ENERGY.create("", false, 10, 1));
            rows.add(FluidTickEditor.create("minecraft:water", false, 5));
            var catalyst = CatalystEditor.empty(); catalyst.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:emerald")); rows.add(catalyst);
            rows.add(SmartInterfaceEditor.create("mode", 0, 1));
            if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) {
                for (String type : List.of("gas", "gas_pertick")) {
                    var gas = new JsonObject(); gas.addProperty("type", "modularmachinery:" + type); gas.addProperty("io-type", "input");
                    gas.addProperty("gas", "mekanism:hydrogen"); gas.addProperty("amount", 10); rows.add(gas);
                }
            }
            source.add("requirements", rows); Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            require(MachineReloadListener.getRecipeLoader().getRecipe(id) != null, "fixture loads");
            var menu = new MenuMachineEditor(35, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            var supported = new ArrayList<Integer>();
            for (int i = 0; i < rows.size(); i++) {
                boolean editable = i < 6 || i == 11;
                require(req(snapshot(menu, id), i).get("editableTrigger").getAsBoolean() == editable, "only supported one-shot inputs enabled");
                if (editable) supported.add(i);
            }
            String before = Files.readString(file); var draft = RequirementTriggerEditor.merge(advanced, 4, 2, 40);
            require(!advanced.has("triggerTime") && draft.get("triggerTime").getAsInt() == 4 && Files.readString(file).equals(before), "cancelled independent draft never writes");
            for (int tick : List.of(4, 0, -1)) for (int repeat : List.of(2, 1, 0)) for (int index : supported) {
                var previous = read(file); save(player, new PktMachineEditorRequirementTrigger(id, index, tick, repeat), true);
                var expected = previous.deepCopy(); expected.getAsJsonArray("requirements").set(index, RequirementTriggerEditor.merge(req(previous, index), tick, repeat, 40));
                require(read(file).equals(expected), "only two trigger fields changed; NBT/selectors/batch/other rows preserved");
                var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id); var requirement = loaded.requirements().get(index);
                require(requirement.getTriggerTime() == Math.max(0, tick) && requirement.isTriggerRepeatable() == (repeat == 2), "reload applies both trigger fields");
                var synced = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(index);
                require(synced.getTriggerTime() == requirement.getTriggerTime() && synced.isTriggerRepeatable() == requirement.isTriggerRepeatable(), "runtime codec preserves triggers");
                var shown = req(snapshot(menu, id), index);
                require(shown.has("triggerTime") == (tick != -1) && shown.has("triggerRepeatable") == (repeat != 0), "editor distinguishes omitted and explicit zero/false");
                var scaled = requirement.scaled(1, Requirement.OPERATION_MULTIPLY, IOType.INPUT, false);
                require(scaled.getTriggerTime() == requirement.getTriggerTime() && scaled.isTriggerRepeatable() == requirement.isTriggerRepeatable(), "modifier copies preserve triggers");
            }
            save(player, new PktMachineEditorRequirementTrigger(id, 1, 4, 1), true);
            checkRuntime(level, origin, MachineReloadListener.getRecipeLoader().getRecipe(id));
            checkLifecycle(level, origin.offset(0, 0, 5), MachineReloadListener.getRecipeLoader().getRecipe(id));
            before = Files.readString(file);
            for (int index : List.of(-1, 6, 7, 8, 9, 10, rows.size(), 512)) save(player, new PktMachineEditorRequirementTrigger(id, index, 4, 2), false);
            if (rows.size() > 11) save(player, new PktMachineEditorRequirementTrigger(id, 12, 4, 2), false);
            for (int tick : List.of(-2, 40, 41, Integer.MAX_VALUE)) save(player, new PktMachineEditorRequirementTrigger(id, 1, tick, 0), false);
            for (int repeat : List.of(-1, 3)) save(player, new PktMachineEditorRequirementTrigger(id, 1, 4, repeat), false);
            require(Files.readString(file).equals(before), "invalid bounds and unsupported requirements cannot write");
            var stale = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), new PktMachineEditorRequirementTrigger(id, 1, 3, 1));
            save(player, new PktMachineEditorRequirementTrigger(id, 1, 2, 2), true);
            require(!PktMachineEditorRequest.process(player, stale).result().success(), "stale revision rejected");
            var external = read(file); external.addProperty("authorNote", "external edit"); Files.writeString(file, external.toString());
            save(player, new PktMachineEditorRequirementTrigger(id, 1, 3, 1), false); require(read(file).equals(external), "external full-source edit preserved"); reload(menu);
            var shared = read(file); var owners = new JsonArray(); owners.add(id); owners.add(other); shared.add("machine", owners);
            Files.writeString(file, shared.toString()); reload(menu); save(player, new PktMachineEditorRequirementTrigger(id + "_sub_0", 1, 5, 2), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id + "_sub_1").requirements().get(1).getTriggerTime() == 5, "shared machine variants update together");
            before = Files.readString(file);
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream().filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, new PktMachineEditorRequirementTrigger(readonly, 0, 2, 1), false);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TriggerDenied")) { @Override public boolean hasPermissions(int n) { return false; } };
            denied.containerMenu = new MenuMachineEditor(35, denied.getInventory(), menu.snapshot()); save(denied, new PktMachineEditorRequirementTrigger(id + "_sub_0", 1, 2, 1), false);
            player.containerMenu = oldMenu;
            require(!PktMachineEditorRequest.process(player, new PktMachineEditorRequest(35, UUID.randomUUID(), "", new PktMachineEditorRequirementTrigger(id + "_sub_0", 1, 2, 1))).result().success(), "closed window rejected");
            require(Files.readString(file).equals(before), "permission/window/read-only failures preserve file");
            Modularmachinery.LOG.info("[MMCE][requirement-trigger-editor-regression] PASS: wire/atomic two-field save/reload/default-vs-explicit/editor+runtime codec, "
                    + "real ordinary+factory startup/pause/resume/persistent ledgers and delayed item/fluid payments, batch shortfall without partial payment, re-entry/repeat/retry/scaled ticks, "
                    + "PerTick exclusion, NBT/selectors/batch/metadata preservation, optional gas, shared variants and invalid/stale/external/permission/window/read-only rejection; UI needs client verification");
        } finally {
            player.containerMenu = oldMenu; for (var path : List.of(file, machineFile, otherFile)) Files.deleteIfExists(path); MachineReloadListener.reloadNow();
        }
    }
    private static void checkRuntime(ServerLevel level, BlockPos origin, MachineRecipe loaded) {
        var blocks = List.of(RegistryBlocks.itemInputBus.get(), RegistryBlocks.fluidInputHatch.get());
        var positions = new BlockPos[2]; var saved = new BlockState[2];
        try {
            var pattern = new MachinePattern();
            for (int i = 0; i < 2; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i + 1, 0, 0); saved[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], blocks.get(i).defaultBlockState(), Block.UPDATE_ALL); pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, "minecraft:air"));
            }
            var machine = new DynamicMachine("trigger_runtime", "Triggers", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[0])).getInventory();
            var fluid = (TileFluidTank) level.getBlockEntity(positions[1]);
            var ordinary = new TileController(origin, RegistryBlocks.blockController.get().defaultBlockState());
            var factory = new TileFactoryController(origin, RegistryBlocks.blockFactoryController.get().defaultBlockState());
            for (boolean useFactory : List.of(false, true)) {
                var thread = new FactoryRecipeThread(factory); ordinary.clearTriggeredRequirements();
                IntPredicate triggered = useFactory ? thread::hasTriggeredRequirement : ordinary::hasTriggeredRequirement;
                IntConsumer mark = useFactory ? thread::markTriggeredRequirement : ordinary::markTriggeredRequirement;
                var item = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(1);
                var recipe = single(List.of(item)); var run = new RecipeCraftingContext(recipe);
                for (int i = 0; i < input.getSlots(); i++) input.setStackInSlot(i, ItemStack.EMPTY);
                require(run.canStart(context), "delayed input may be supplied after startup");
                require(pay(run, context, 4, 3, triggered, mark) == null && !triggered.test(0), "no early payment");
                input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
                require(pay(run, context, 4, 4, triggered, mark) != null && input.getStackInSlot(0).getCount() == 5 && !triggered.test(0), "insufficient whole batch waits without partial extraction or marking");
                input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 32));
                require(pay(run, context, 4, 4, triggered, mark) == null && input.getStackInSlot(0).getCount() == 24 && triggered.test(0), "supply restored pays full batch at trigger");
                require(pay(run, context, 4, 4, triggered, mark) == null && input.getStackInSlot(0).getCount() == 24, "nonrepeat does not pay again at held tick");
                require(pay(run, context, 4, 3, triggered, mark) == null && pay(run, context, 4, 4, triggered, mark) == null && input.getStackInSlot(0).getCount() == 24, "nonrepeat survives rewind and re-entry");
                item.setTriggerRepeatable(true);
                require(pay(run, context, 4, 4, triggered, mark) == null && input.getStackInSlot(0).getCount() == 16, "repeat permits re-entry at same tick");
                require(pay(run, context, 4, 5, triggered, mark) == null && input.getStackInSlot(0).getCount() == 16, "repeat is not an interval");
                var scaled = RecipeCraftingContext.withModifiers(recipe, List.of(new RecipeModifier("modularmachinery:duration", null, 2, Requirement.OPERATION_MULTIPLY, false)));
                require(scaled.triggerTime(scaled.getRequirements().get(0)) == 8, "duration modifier scales trigger tick");
                require(pay(scaled, context, 4, 4, triggered, mark) == null && input.getStackInSlot(0).getCount() == 16, "unscaled tick does not trigger");
                require(pay(scaled, context, 4, 8, triggered, mark) == null && input.getStackInSlot(0).getCount() == 8, "scaled tick pays batch");
            }
            // A failed later row retries without charging an earlier successful nonrepeat row again.
            var item = loaded.requirements().get(1); var water = loaded.requirements().get(2); item.setTriggerTime(4); item.setTriggerRepeatable(false); water.setTriggerTime(4);
            ordinary.clearTriggeredRequirements(); input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 32)); fluid.getTank().setFluid(FluidStack.EMPTY);
            var both = new RecipeCraftingContext(single(List.of(item, water)));
            require(pay(both, context, 4, 4, ordinary::hasTriggeredRequirement, ordinary::markTriggeredRequirement) != null
                    && input.getStackInSlot(0).getCount() == 24 && ordinary.hasTriggeredRequirement(0) && !ordinary.hasTriggeredRequirement(1), "failed later row preserves successful earlier payment");
            fluid.getTank().setFluid(new FluidStack(Fluids.WATER, 1000));
            require(pay(both, context, 4, 4, ordinary::hasTriggeredRequirement, ordinary::markTriggeredRequirement) == null
                    && input.getStackInSlot(0).getCount() == 24 && fluid.getTank().getFluidAmount() == 600, "retry pays only missing fluid row");
            var continuous = loaded.requirements().get(8); continuous.setTriggerTime(4);
            var rate = new RecipeCraftingContext(single(List.of(continuous)));
            int before = fluid.getTank().getFluidAmount();
            require(pay(rate, context, 4, 4, index -> false, index -> { throw new AssertionError("PerTick marked as delayed"); }) == null
                    && fluid.getTank().getFluidAmount() == before, "PerTick is never charged through one-shot trigger path");
        } finally { for (int i = 0; i < saved.length; i++) if (saved[i] != null) level.setBlock(positions[i], saved[i], Block.UPDATE_ALL); }
    }
    private static void checkLifecycle(ServerLevel level, BlockPos origin, MachineRecipe loaded) throws Exception {
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.itemInputBus.get(), RegistryBlocks.blockFactoryController.get()};
        var positions = new BlockPos[3]; var saved = new BlockState[3];
        try {
            for (int i = 0; i < 3; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i, 0, 0); saved[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            }
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("trigger_runtime", "Trigger lifecycle", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            var ordinary = (TileController) level.getBlockEntity(origin); var factory = (TileFactoryController) level.getBlockEntity(positions[2]);
            factory.setFactoryCeiling(1, 1);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class); start.setAccessible(true);
            var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class); advance.setAccessible(true);
            var factoryStart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class); factoryStart.setAccessible(true);
            var factoryTick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class); factoryTick.setAccessible(true);
            for (boolean useFactory : List.of(false, true)) {
                var item = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(1);
                var recipe = single(List.of(item)); var run = new RecipeCraftingContext(recipe);
                for (int i = 0; i < input.getSlots(); i++) input.setStackInSlot(i, ItemStack.EMPTY);
                FactoryRecipeThread thread = null;
                if (useFactory) {
                    factoryStart.invoke(null, factory, machine, context, List.of(recipe));
                    thread = factory.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                } else { start.invoke(null, ordinary, context, List.of(recipe), run); require(ordinary.hasActiveRecipe(), "ordinary starts before delayed supplies arrive"); }
                // With supplies present before the trigger tick, neither start path may take them early.
                input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
                for (int i = 0; i < 4; i++) {
                    if (useFactory) factoryTick.invoke(null, thread, factory, machine, context, List.of(recipe));
                    else advance.invoke(null, ordinary, context, List.of(recipe), run);
                }
                require(input.getStackInSlot(0).getCount() == 8 && (useFactory ? thread.getRecipeTicks() : ordinary.getRecipeTicks()) == 4, "full tick path waits until trigger before payment");
                input.setStackInSlot(0, ItemStack.EMPTY);
                if (useFactory) factoryTick.invoke(null, thread, factory, machine, context, List.of(recipe));
                else advance.invoke(null, ordinary, context, List.of(recipe), run);
                require((useFactory ? thread.getRecipeTicks() : ordinary.getRecipeTicks()) == 4
                        && (useFactory ? thread.getBlockedReason() : ordinary.getStatusMessage()) != null, "missing delayed input holds actual progress and reports reason");
                input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
                if (useFactory) factoryTick.invoke(null, thread, factory, machine, context, List.of(recipe));
                else advance.invoke(null, ordinary, context, List.of(recipe), run);
                require(input.getStackInSlot(0).getCount() == 6 && (useFactory ? thread.getRecipeTicks() : ordinary.getRecipeTicks()) == 5, "actual run resumes and pays exactly once");
                if (useFactory) {
                    var restored = FactoryRecipeThread.deserialize(thread.serialize(), factory);
                    require(restored != null && restored.hasTriggeredRequirement(0), "factory trigger ledger persists across save/load");
                    restored.rewindRecipeTick(); factoryTick.invoke(null, restored, factory, machine, context, List.of(recipe));
                } else {
                    var restored = new TileController(origin, ordinary.getBlockState()); restored.load(ordinary.saveWithoutMetadata());
                    require(restored.hasTriggeredRequirement(0), "ordinary trigger ledger persists across save/load");
                    ordinary.rewindRecipeTick(); advance.invoke(null, ordinary, context, List.of(recipe), run);
                }
                require(input.getStackInSlot(0).getCount() == 6, "rewinding after payment never duplicates a nonrepeat charge");
                if (useFactory) factory.abandonAllRecipes(); else ordinary.abandonRecipe();
            }
        } finally { for (int i = 0; i < saved.length; i++) if (saved[i] != null) level.setBlock(positions[i], saved[i], Block.UPDATE_ALL); }
    }
    private static String pay(RecipeCraftingContext run, MachineContext context, int copies, int tick, IntPredicate triggered, IntConsumer mark) {
        return MachineTicker.processTriggeredInputs(run.getRequirements(), run, context, copies, tick, triggered, mark);
    }
    private static MachineRecipe single(List<Requirement> rows) { return new MachineRecipe("trigger_runtime", "trigger_runtime", 40, rows, null, false, -1, ""); }
    private static JsonObject req(JsonObject source, int index) { return source.getAsJsonArray("requirements").get(index).getAsJsonObject(); }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes")) if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing trigger-editor snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu; var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation), buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "authoritative reload precedes confirmation");
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError("Requirement trigger editor: " + message); }
}
