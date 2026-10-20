/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Real envelope/file/reload tests plus client acknowledgement ordering without loading client classes. */
public final class EditorSaveConfirmationRegressionChecks {
    private EditorSaveConfirmationRegressionChecks() {}

    public static void run(ServerLevel level) throws Exception {
        checkAcknowledgementOrdering();
        checkDraftRefresh();
        String machine = "save_confirmation_" + UUID.randomUUID().toString().replace("-", "");
        String recipe = machine + "_recipe";
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(machine + ".json");
        var recipeFile = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(recipe + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "EditorSaveTest")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var original = player.containerMenu;
        try {
            var clientMenu = new MenuMachineEditor(6, player.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
            clientMenu.acceptSnapshot(UUID.randomUUID(), "initial");
            UUID pending = clientMenu.saves().begin();
            clientMenu.acceptSnapshot(UUID.randomUUID(), "wrong transfer");
            require(clientMenu.snapshot().equals("initial"), "unrelated snapshot cannot replace an in-flight editor");
            clientMenu.acceptSnapshot(pending, "confirmed");
            clientMenu.saves().receive(new EditorSaveState.Result(pending, true, "saved"));
            require(clientMenu.saves().takeReady().success() && clientMenu.snapshot().equals("confirmed"), "menu receives matching authoritative document");
            clientMenu.acceptSnapshot(pending, "late duplicate");
            require(clientMenu.snapshot().equals("confirmed"), "late snapshot cannot overwrite a completed operation");
            Files.writeString(machineFile, "{\"registryname\":\"" + machine + "\",\"localizedname\":\"Save Test\","
                    + "\"parts\":[{\"x\":1,\"y\":0,\"z\":0,\"elements\":\"minecraft:stone\"}]}", StandardCharsets.UTF_8);
            MachineReloadListener.reloadNow();
            require(MachineReloadListener.getLoader().findByName(machine) != null, "fixture machine loads");
            var menu = new MenuMachineEditor(7, player.getInventory(), PktMachineEditorRequest.snapshot());
            player.containerMenu = menu;
            success(player, new PktMachineEditorMachineEdit(machine, "&a确认保存"));
            require(MachineReloadListener.getLoader().findByName(machine).getLocalizedName().equals("\u00a7a确认保存"), "machine name reload precedes success");
            success(player, new PktMachineEditorRequirementAdd(recipe, "energy", "", false, 32, 1,
                    machine, 40, "&6配方"));
            success(player, new PktMachineEditorRecipeEdit(recipe, "&b新名称", 80));
            success(player, new PktMachineEditorEdit(recipe, 0, 64, 1, ""));
            success(player, new PktMachineEditorRequirementAdd(recipe, "item", "minecraft:diamond", true, 2, 0.5F, "", 20, ""));
            success(player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_REQUIREMENT, "", recipe, 1, "", false));
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(recipe);
            require(loaded != null && loaded.recipeTime() == 80 && loaded.displayName().equals("\u00a7b新名称")
                    && loaded.requirements().size() == 1, "acknowledged values are actually loaded");
            String before = Files.readString(recipeFile, StandardCharsets.UTF_8);
            failure(player, new PktMachineEditorEdit(recipe, 0, 0, 1, ""));
            failure(player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_REQUIREMENT, "", recipe, 0, "", false));
            var staleWindow = new PktMachineEditorRequest(8, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()),
                    new PktMachineEditorRecipeEdit(recipe, "wrong window", 20));
            require(!PktMachineEditorRequest.process(player, staleWindow).result().success(), "closed/different window cannot write");
            var staleRevision = new PktMachineEditorRequest(7, UUID.randomUUID(), EditorSaveState.revision("old document"),
                    new PktMachineEditorRecipeEdit(recipe, "stale", 20));
            require(!PktMachineEditorRequest.process(player, staleRevision).result().success(), "stale document cannot overwrite new content");
            require(before.equals(Files.readString(recipeFile, StandardCharsets.UTF_8)), "rejected requests leave file intact");

            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SaveDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(7, denied.getInventory(), menu.snapshot());
            failure(denied, new PktMachineEditorRecipeEdit(recipe, "denied", 20));
            require(before.equals(Files.readString(recipeFile, StandardCharsets.UTF_8)), "permission failure does not write");
            Files.delete(recipeFile);
            failure(player, new PktMachineEditorRecipeEdit(recipe, "missing file", 20));
            require(!Files.exists(recipeFile), "missing source is not recreated by an edit");
            Files.writeString(recipeFile, "{", StandardCharsets.UTF_8);
            failure(player, new PktMachineEditorRecipeEdit(recipe, "bad JSON", 20));
            require(Files.readString(recipeFile).equals("{"), "malformed source is not replaced");
            Files.writeString(recipeFile, before, StandardCharsets.UTF_8);
            var invalidSource = JsonParser.parseString(before).getAsJsonObject();
            invalidSource.addProperty("machine", "missing_machine");
            Files.writeString(recipeFile, invalidSource.toString(), StandardCharsets.UTF_8);
            var notLoaded = failure(player, new PktMachineEditorRecipeEdit(recipe, "cannot load", 20));
            require(notLoaded.result().message().contains("文件已写入"), "written-but-unloadable result must not claim success");
            Files.writeString(recipeFile, before, StandardCharsets.UTF_8);
            MachineReloadListener.reloadNow();
            menu.setSnapshot(PktMachineEditorRequest.snapshot());
            var duplicate = request(menu, new PktMachineEditorRequirementAdd(recipe, "fluid", "minecraft:water", true, 1000, 1, "", 20, ""));
            require(PktMachineEditorRequest.process(player, duplicate).result().success(), "first request succeeds");
            String once = Files.readString(recipeFile, StandardCharsets.UTF_8);
            require(!PktMachineEditorRequest.process(player, duplicate).result().success()
                    && once.equals(Files.readString(recipeFile, StandardCharsets.UTF_8)), "duplicate request must not append twice");
            success(player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_RECIPE, "", recipe, -1, "", false));
            require(!Files.exists(recipeFile) && MachineReloadListener.getRecipeLoader().getRecipe(recipe) == null, "recipe deletion is loaded before acknowledgement");
            success(player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_MACHINE, machine, "", -1, "", false));
            require(!Files.exists(machineFile) && MachineReloadListener.getLoader().findByName(machine) == null, "machine deletion is loaded before acknowledgement");
            Modularmachinery.LOG.info("[MMCE][editor-save-confirmation-regression] PASS: all five mutations, actual disk/reload snapshots, "
                    + "failures, permissions, stale windows/revisions, duplicate requests and reply/snapshot ordering");
        } finally {
            player.containerMenu = original;
            Files.deleteIfExists(machineFile);
            Files.deleteIfExists(recipeFile);
            Files.deleteIfExists(machineFile.resolveSibling(machineFile.getFileName() + ".mmce.tmp"));
            Files.deleteIfExists(recipeFile.resolveSibling(recipeFile.getFileName() + ".mmce.tmp"));
            MachineReloadListener.reloadNow();
        }
    }

    private static PktMachineEditorRequest request(MenuMachineEditor menu, Object mutation) {
        return new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
    }
    private static PktMachineEditorRequest.Reply success(ServerPlayer player, Object mutation) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        PktMachineEditorRequest decoded;
        try {
            PktMachineEditorRequest.encode(request(menu, mutation), buffer);
            decoded = PktMachineEditorRequest.decode(buffer);
        } finally { buffer.release(); }
        var reply = PktMachineEditorRequest.process(player, decoded);
        require(reply.result().success() && reply.snapshot() != null, "success with refreshed document: " + reply.result().message());
        require(reply.snapshot().equals(PktMachineEditorRequest.snapshot()) && menu.snapshot().equals(reply.snapshot()), "acknowledgement reflects actual server registry");
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorResult.encode(reply.result(), buffer);
            require(PktMachineEditorResult.decode(buffer).equals(reply.result()), "result wire roundtrip");
        } finally { buffer.release(); }
        return reply;
    }
    private static PktMachineEditorRequest.Reply failure(ServerPlayer player, Object mutation) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var reply = PktMachineEditorRequest.process(player, request(menu, mutation));
        require(!reply.result().success() && reply.snapshot() == null && !reply.result().message().isBlank(), "failure has explicit cause and no success snapshot");
        return reply;
    }
    private static void checkAcknowledgementOrdering() {
        var state = new EditorSaveState();
        UUID first = state.begin();
        state.receive(new EditorSaveState.Result(UUID.randomUUID(), true, "wrong reply"));
        require(state.takeReady() == null && state.pending(), "unrelated reply cannot unlock UI");
        state.receive(new EditorSaveState.Result(first, true, "saved"));
        require(state.takeReady() == null, "success waits for its snapshot");
        state.snapshotReceived(UUID.randomUUID());
        require(state.takeReady() == null, "unrelated snapshot cannot confirm save");
        state.snapshotReceived(first);
        state.receive(new EditorSaveState.Result(first, false, "late duplicate"));
        require(state.takeReady().success() && !state.pending(), "reply plus matching snapshot completes save");
        UUID second = state.begin();
        state.snapshotReceived(second);
        require(state.takeReady() == null, "snapshot alone cannot claim success");
        state.receive(new EditorSaveState.Result(first, true, "late reply"));
        require(state.takeReady() == null, "late prior reply cannot finish next operation");
        state.receive(new EditorSaveState.Result(second, false, "rejected"));
        require(!state.takeReady().success() && !state.pending(), "failure unlocks without requiring snapshot");
    }
    private static void checkDraftRefresh() {
        String server = "{\"machines\":[{\"name\":\"test\",\"recipes\":[{\"name\":\"committed\",\"duration\":80}]}]}";
        var local = JsonParser.parseString("{\"machines\":[{\"name\":\"test\",\"recipes\":["
                + "{\"name\":\"committed\",\"newRecipe\":true,\"duration\":20},"
                + "{\"name\":\"other_draft\",\"newRecipe\":true,\"duration\":100},"
                + "{\"name\":\"deleted_on_server\",\"duration\":20}]}]}").getAsJsonObject();
        String before = local.toString();
        var merged = MachineEditorSnapshot.withLocalDrafts(server, local).getAsJsonArray("machines").get(0).getAsJsonObject().getAsJsonArray("recipes");
        require(merged.size() == 2 && merged.get(0).getAsJsonObject().get("duration").getAsInt() == 80
                && !merged.get(0).getAsJsonObject().has("newRecipe")
                && merged.get(1).getAsJsonObject().get("name").getAsString().equals("other_draft")
                && local.toString().equals(before), "refresh keeps unrelated drafts, accepts committed values and does not resurrect deleted entries");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Editor save confirmation: " + message);
    }
}
