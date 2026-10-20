/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.RequirementBatchEditor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Independent existing-requirement save inside the authenticated editor envelope. */
public record PktMachineEditorRequirementBatch(String recipeId, int index, int policy) {
    public static void encode(PktMachineEditorRequirementBatch packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeVarInt(packet.index); buffer.writeVarInt(packet.policy);
    }
    public static PktMachineEditorRequirementBatch decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorRequirementBatch(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt());
    }
    static String apply(ServerPlayer player, PktMachineEditorRequirementBatch packet) {
        try {
            var recipes = MachineReloadListener.getRecipeLoader(); var recipe = recipes.getRecipe(packet.recipeId);
            if (!player.hasPermissions(2) || !(player.containerMenu instanceof MenuMachineEditor)
                    || recipe == null || !recipes.isSourceEditable(packet.recipeId)
                    || packet.index < 0 || packet.index >= 512 || packet.index >= recipe.requirements().size()
                    || !RequirementBatchEditor.supports(recipe.requirements().get(packet.index)))
                return "需求不存在、来源只读或不支持批量设置，未保存修改";
            var root = ModDataHolder.INSTANCE.getRecipeDirectory().getCanonicalFile().toPath();
            var path = new java.io.File(recipes.getSourcePath(packet.recipeId)).getCanonicalFile().toPath();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) return "配方源文件无效";
            JsonObject source;
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { source = JsonParser.parseReader(reader).getAsJsonObject(); }
            if (!source.equals(recipes.getRecipeSource(packet.recipeId))) return "配方源文件已发生变化，请重新加载并打开编辑器";
            var requirements = source.getAsJsonArray("requirements");
            if (requirements.size() != recipe.requirements().size()) return "源需求位置已失效，请重新加载并打开编辑器";
            requirements.set(packet.index, RequirementBatchEditor.merge(requirements.get(packet.index).getAsJsonObject(), packet.policy));
            var temp = Files.createTempFile(root, "requirement-batch-", ".mmce.tmp");
            try {
                Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(source), StandardCharsets.UTF_8);
                try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
            MachineReloadListener.reloadNow(); return null;
        } catch (Exception invalid) { return "需求批量设置保存失败：" + invalid.getMessage(); }
    }
}
