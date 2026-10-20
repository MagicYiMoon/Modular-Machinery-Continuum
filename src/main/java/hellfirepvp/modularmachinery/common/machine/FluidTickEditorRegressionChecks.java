/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorAction;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import java.nio.file.*;
import java.util.*;

/** Save continuous input/output rates, then exercise actual grouped tick payments against real hatches. */
public final class FluidTickEditorRegressionChecks {
    private FluidTickEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "fluid_tick_editor_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var invalidFile = file.resolveSibling(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FluidTickEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        var menu = new MenuMachineEditor(25, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
        try {
            var input = FluidTickEditor.create("minecraft:water", false, 100);
            var output = FluidTickEditor.create("minecraft:lava", true, 50);
            save(player, new PktMachineEditorFluidTick(id, -1, input.toString(), machine, 80, "持续流体测试"), true);
            require(read(file).get("recipeTime").getAsInt() == 80 && req(read(file), 0).equals(input)
                    && !req(read(file), 0).has("chance"), "first save uses continuous type and rate without chance");
            save(player, new PktMachineEditorFluidTick(id, -1, output.toString(), "", 20, ""), true);
            var snap = snapshot(menu, id);
            require(FluidTickEditor.canEdit(req(snap, 0)) && RequirementResourceSelection.canReplace(req(snap, 1))
                    && RequirementResourceSelection.key(req(snap, 0)).equals("fluid"), "both snapshot directions support resource reselection");
            var source = read(file); source.addProperty("authorNote", "recipe metadata");
            for (int index = 0; index < 2; index++) {
                var req = req(source, index); req.addProperty("selector-tag", index == 0 ? "red" : "blue");
                req.addProperty("triggerTime", 7); req.addProperty("triggerRepeatable", true);
                req.addProperty("authorNote", "rate metadata"); req.addProperty("parallelizeUnaffected", false);
                req.addProperty("chance", 1); req.addProperty("ignoreOutputCheck", false);
            }
            Files.writeString(file, source.toString()); reload(menu);
            String before = Files.readString(file);
            var draft = RequirementResourceSelection.replace(req(snapshot(menu, id), 0), "minecraft:lava"); draft.addProperty("amount", 150);
            require(FluidTickEditor.isRate(draft) && Files.readString(file).equals(before), "reselection/cancel retains type and leaves source untouched");
            save(player, edit(id, 0, draft), true);
            save(player, edit(id, 1, FluidTickEditor.create("minecraft:water", true, 75)), true);
            req(source, 0).addProperty("fluid", "minecraft:lava"); req(source, 0).addProperty("amount", 150);
            req(source, 1).addProperty("fluid", "minecraft:water"); req(source, 1).addProperty("amount", 75);
            require(read(file).equals(source), "rate/fluid edits preserve legacy chance=1, ID, direction, selectors, triggers and metadata");
            var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var synced = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
            var syncedRate = (RequirementFluidPerTick) synced.requirements().get(0);
            require(syncedRate.fluid().getAmount() == 150 && syncedRate.fluid().getRawId().equals("minecraft:lava")
                    && syncedRate.getSelectorTag().equals("red") && syncedRate.getTriggerTime() == 7 && syncedRate.isTriggerRepeatable(),
                    "runtime snapshot retains updated rate, fluid and source settings");
            checkRuntime(level, origin, id);
            before = Files.readString(file);
            for (String badAmount : List.of("0", "-1", "1.5", "2000000001", "2147483648", "null", "\"100\"", "[]")) {
                var bad = input.deepCopy(); bad.add("amount", JsonParser.parseString(badAmount)); save(player, edit(id, 0, bad), false);
            }
            for (String badChance : List.of("0", "0.5", "1.0000000001", "1e100", "\"1\"", "null")) {
                var bad = input.deepCopy(); bad.add("chance", JsonParser.parseString(badChance)); save(player, edit(id, 0, bad), false);
            }
            for (String resource : List.of("missing:fluid", "minecraft:empty", "tag:minecraft:water", "invalid id")) {
                var bad = input.deepCopy(); bad.addProperty("fluid", resource); save(player, edit(id, 0, bad), false);
            }
            for (String key : List.of("type", "io-type", "fluid", "amount")) {
                var bad = input.deepCopy(); bad.remove(key); save(player, edit(id, 0, bad), false);
            }
            for (String key : List.of("nbt", "nbt-display")) {
                var bad = input.deepCopy(); bad.add(key, new JsonObject()); save(player, edit(id, 0, bad), false);
            }
            for (int index : List.of(-2, 2, 512)) save(player, edit(id, index, input), false);
            for (String payload : List.of("{}", "[]", "{")) save(player, new PktMachineEditorFluidTick(id, 0, payload, "", 20, ""), false);
            save(player, edit(id, 0, output), false); save(player, edit(id, 1, input), false);
            save(player, edit(id, 0, BasicRequirementType.FLUID.create("minecraft:water", false, 100, 1)), false);
            save(player, new ModularmachineryNetwork.PktMachineEditorEdit(id, 0, 100, 1, "minecraft:water"), false);
            require(Files.readString(file).equals(before), "invalid numbers/resources/NBT/direction/type and old editor route cannot overwrite source");
            save(player, new PktMachineEditorFluidTick(id + "_invalid", -1, "{}", machine, 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first request creates no file");
            for (String mode : List.of("nbt", "nbt-display", "ordinary")) {
                var advanced = JsonParser.parseString(before).getAsJsonObject();
                if (mode.equals("ordinary")) advanced.getAsJsonArray("requirements").set(0, BasicRequirementType.FLUID.create("minecraft:water", false, 100, 1));
                else req(advanced, 0).add(mode, new JsonObject());
                Files.writeString(file, advanced.toString()); reload(menu);
                require(!FluidTickEditor.canEdit(req(advanced, 0)), "advanced source is not flattened");
                save(player, edit(id, 0, input), false); require(read(file).equals(advanced), "server validates actual source: " + mode);
            }
            Files.writeString(file, before); reload(menu);
            var external = read(file); req(external, 0).addProperty("amount", 200); Files.writeString(file, external.toString());
            save(player, edit(id, 0, input), false);
            save(player, new PktMachineEditorFluidTick(id, -1, input.toString(), "", 20, ""), false);
            require(read(file).equals(external), "unreloaded disk changes rejected for append and replacement");
            Files.writeString(file, before); reload(menu);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FluidTickDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(25, denied.getInventory(), menu.snapshot()); save(denied, edit(id, 0, input), false);
            for (boolean wrongWindow : List.of(false, true)) require(!PktMachineEditorRequest.process(player,
                    new PktMachineEditorRequest(wrongWindow ? 26 : 25, UUID.randomUUID(), wrongWindow
                            ? EditorSaveState.revision(menu.snapshot()) : "stale", edit(id, 0, input))).result().success(), "stale window/revision rejected");
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, edit(readonly, 0, input), false);
            require(Files.readString(file).equals(before), "denied/stale/generated-source writes leave source intact");
            save(player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_REQUIREMENT, "", id, 1, "", false), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().size() == 1, "continuous output supports normal deletion");
            Modularmachinery.LOG.info("[MMCE][fluid-tick-editor-regression] PASS: real wire/first save/append/delete/reload/snapshots/reselection/cancel, "
                    + "actual grouped input/output ticks/batch/selectors/pause/resume/completed waiting, source metadata/legacy chance preservation, "
                    + "invalid/range/NBT/direction/type/stale-source/permission/window/read-only rejection");
        } finally {
            player.containerMenu = oldMenu; Files.deleteIfExists(file); Files.deleteIfExists(invalidFile); MachineReloadListener.reloadNow();
        }
    }
    private static void checkRuntime(ServerLevel level, BlockPos origin, String id) {
        var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
        var positions = new BlockPos[4]; var saved = new net.minecraft.world.level.block.state.BlockState[4];
        try {
            var pattern = new MachinePattern();
            for (int i = 0; i < 4; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i + 1, 0, 0); saved[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], (i < 2 ? RegistryBlocks.fluidInputHatch : RegistryBlocks.fluidOutputHatch).get().defaultBlockState(), Block.UPDATE_ALL);
                pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, "minecraft:air", i == 0 ? "red" : i == 2 ? "blue" : "wrong"));
            }
            var machine = new DynamicMachine("rate_editor_runtime", "Rate editor runtime", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var in = (TileFluidTank) level.getBlockEntity(positions[0]); var wrongIn = (TileFluidTank) level.getBlockEntity(positions[1]);
            var out = (TileFluidTank) level.getBlockEntity(positions[2]); var wrongOut = (TileFluidTank) level.getBlockEntity(positions[3]);
            var input = (RequirementFluidPerTick) recipe.requirements().get(0); var output = (RequirementFluidPerTick) recipe.requirements().get(1);
            in.getTank().setFluid(new FluidStack(input.fluid().asStack(), 600)); wrongIn.getTank().setFluid(new FluidStack(input.fluid().asStack(), 1000));
            out.getTank().setFluid(FluidStack.EMPTY); wrongOut.getTank().setFluid(FluidStack.EMPTY);
            require(input.getMaxParallelism(context, 10, recipe.requirements()) == 4 && in.getTank().getFluidAmount() == 600,
                    "simulation uses edited rate and only selected input without draining");
            require(MachineTicker.consumeInputs(recipe.requirements(), context, 2), "starting does not prepay continuous fluid");
            MachineTicker.emitOutputs(recipe, recipe.requirements(), context, 2);
            require(in.getTank().getFluidAmount() == 600 && out.getTank().isEmpty(), "one-time start/finish paths do not duplicate rates");
            require(MachineTicker.processPerTick(recipe, recipe.requirements(), context, 2, false) == null
                    && in.getTank().getFluidAmount() == 300 && out.getTank().getFluidAmount() == 150, "edited 150/75 rates pay at batch two");
            in.getTank().setFluid(new FluidStack(input.fluid().asStack(), 299));
            require(MachineTicker.processPerTick(recipe, recipe.requirements(), context, 2, false) != null
                    && in.getTank().getFluidAmount() == 299 && out.getTank().getFluidAmount() == 150, "shortfall pauses atomically despite wrong-group stock");
            in.getTank().setFluid(new FluidStack(input.fluid().asStack(), 600));
            out.getTank().setFluid(new FluidStack(output.fluid().asStack(), out.getTank().getCapacity()));
            require(MachineTicker.processPerTick(recipe, recipe.requirements(), context, 2, false) != null
                    && in.getTank().getFluidAmount() == 600 && wrongOut.getTank().isEmpty(), "selected full output pauses rather than filling wrong group");
            out.getTank().setFluid(FluidStack.EMPTY);
            require(MachineTicker.processPerTick(recipe, recipe.requirements(), context, 2, false) == null
                    && in.getTank().getFluidAmount() == 300 && out.getTank().getFluidAmount() == 150, "rate resumes after space returns");
            require(MachineTicker.processPerTick(recipe, recipe.requirements(), context, 2, true) == null
                    && in.getTank().getFluidAmount() == 300 && out.getTank().getFluidAmount() == 150
                    && wrongIn.getTank().getFluidAmount() == 1000 && wrongOut.getTank().isEmpty(), "completed waiting and other groups remain untouched");
        } finally { for (int i = 0; i < 4; i++) if (saved[i] != null) level.setBlock(positions[i], saved[i], Block.UPDATE_ALL); }
    }
    private static JsonObject req(JsonObject source, int index) { return source.getAsJsonArray("requirements").get(index).getAsJsonObject(); }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static PktMachineEditorFluidTick edit(String id, int index, JsonObject value) { return new PktMachineEditorFluidTick(id, index, value.toString(), "", 20, ""); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing fluid tick editor snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer); var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "reload and authoritative sync precede acknowledgement");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Fluid tick editor: " + message); }
}
