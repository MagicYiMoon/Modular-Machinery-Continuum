/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.SelectorRequirement;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.SelectorBindingEditor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;

/** Changes only selector-tag on an existing source requirement, including advanced NBT requirements. */
public record PktMachineEditorSelector(String recipeId, int index, String tag) {
    public static void encode(PktMachineEditorSelector packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index); buffer.writeUtf(packet.tag, 256);
    }
    public static PktMachineEditorSelector decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorSelector(buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(256));
    }
    static String apply(ServerPlayer player, PktMachineEditorSelector packet) {
        try {
            var recipes = MachineReloadListener.getRecipeLoader();
            var recipe = recipes.getRecipe(packet.recipeId);
            if (!player.hasPermissions(2) || recipe == null || !recipes.isSourceEditable(packet.recipeId)
                    || packet.index < 0 || packet.index >= 512 || packet.index >= recipe.requirements().size()
                    || !(recipe.requirements().get(packet.index) instanceof SelectorRequirement))
                return "需求不存在、来源只读或不支持接口分组，未保存修改";
            var root = ModDataHolder.INSTANCE.getRecipeDirectory().getCanonicalFile().toPath();
            var path = new java.io.File(recipes.getSourcePath(packet.recipeId)).getCanonicalFile().toPath();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) return "配方源文件无效";
            JsonObject source;
            try (var reader = Files.newBufferedReader(path)) { source = JsonParser.parseReader(reader).getAsJsonObject(); }
            var requirements = source.getAsJsonArray("requirements");
            var loadedSource = recipes.getRequirementSource(recipe.requirements().get(packet.index));
            if (requirements.size() != recipe.requirements().size() || loadedSource == null
                    || !loadedSource.equals(requirements.get(packet.index)))
                return "源需求与已加载配方不一致，请重新加载并打开编辑器";
            requirements.set(packet.index, SelectorBindingEditor.merge(loadedSource, packet.tag,
                    SelectorBindingEditor.options(packet.recipeId, MachineReloadListener.getLoader(), recipes)));
            var temp = Files.createTempFile(root, "selector-", ".mmce.tmp");
            try {
                Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(source));
                try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
            MachineReloadListener.reloadNow();
            return null;
        } catch (Exception invalid) { return "接口分组保存失败：" + invalid.getMessage(); }
    }
}
