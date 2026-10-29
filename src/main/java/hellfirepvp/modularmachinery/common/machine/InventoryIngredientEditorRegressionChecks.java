/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Exercises authoring copies through the actual server save envelope and reload. */
public final class InventoryIngredientEditorRegressionChecks {
    public static void run(ServerLevel level) throws Exception {
        String id = "inventory_editor_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "InventoryEditor")) {
            @Override public boolean hasPermissions(int n) { return true; }
        };
        var oldMenu = player.containerMenu;
        var stack = new ItemStack(Items.DIAMOND, 7);
        stack.getOrCreateTag().putInt("quality", 5);
        stack.getTag().putString("label", "literal.*[text]\\E");
        var list = new ListTag(); list.add(StringTag.valueOf("first")); list.add(StringTag.valueOf("second"));
        stack.getTag().put("list", list);
        player.getInventory().setItem(0, stack);
        var before = stack.copy();
        try {
            var source = new JsonObject();
            source.addProperty("machine", MachineReloadListener.getLoader().all().iterator().next().getRegistryName());
            source.addProperty("registryName", id); source.addProperty("recipeTime", 2);
            source.add("requirements", new JsonArray());
            source.getAsJsonArray("requirements").add(BasicRequirementType.ITEM.create("minecraft:stone", true, 1, 1F));
            Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            player.containerMenu = new MenuMachineEditor(46, player.getInventory(), PktMachineEditorRequest.snapshot());
            for (boolean output : new boolean[] { false, true }) {
                var nbt = InventoryIngredientEditor.data(stack, output);
                var parsed = RecipeNbt.read(nbt);
                require(output ? stack.getTag().equals(parsed.output()) : parsed.matches(stack.getTag()), "inventory NBT conversion");
                var changed = stack.getTag().copy(); changed.putString("label", "literal-other");
                if (!output) require(!parsed.matches(changed), "inventory string interpreted as regex");
                var req = BasicRequirementType.ITEM.create("minecraft:diamond", output, stack.getCount(), 0.75F);
                nbt.entrySet().forEach(e -> req.add(e.getKey(), e.getValue()));
                var menu = (MenuMachineEditor) player.containerMenu;
                var reply = PktMachineEditorRequest.process(player, new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(),
                        EditorSaveState.revision(menu.snapshot()), new PktMachineEditorItem(id, -1, req.toString(), "", 2, "")));
                require(reply.result().success(), reply.result().message());
                var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("requirements");
                require(saved.get(saved.size() - 1).equals(ItemRequirementEditor.validate(req)), "saved inventory draft differs");
                require(MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().size() == saved.size(), "inventory recipe failed reload");
                require(ItemStack.matches(before, player.getInventory().getItem(0)), "authoring consumed or changed inventory");
            }
            var plain = BasicRequirementType.ITEM.create("minecraft:emerald", false, 3, 0.25F);
            var menu = (MenuMachineEditor) player.containerMenu;
            var reply = PktMachineEditorRequest.process(player, new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(),
                    EditorSaveState.revision(menu.snapshot()), new PktMachineEditorItem(id, 1, plain.toString(), "", 2, "")));
            require(reply.result().success(), reply.result().message());
            var replaced = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("requirements").get(1).getAsJsonObject();
            require(replaced.equals(plain), "NBT-off replacement retained previous NBT");
            var arrayStack = stack.copy(); arrayStack.getTag().putIntArray("array", new int[] {1, 2});
            try { InventoryIngredientEditor.data(arrayStack, false); throw new AssertionError("NBT array silently discarded"); }
            catch (IllegalArgumentException expected) { /* The picker offers an explicit NBT-off fallback. */ }
            require(InventoryIngredientEditor.data(new ItemStack(Items.STONE), false).size() == 0, "untagged stack gained NBT");
            Modularmachinery.LOG.info("[MMC][inventory-ingredient-editor] PASS: inventory count, literal input strings/lists, exact output NBT, actual envelope/save/reload, inventory conservation and unsupported-array guard");
        } finally {
            player.containerMenu = oldMenu; Files.deleteIfExists(file); MachineReloadListener.reloadNow();
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
