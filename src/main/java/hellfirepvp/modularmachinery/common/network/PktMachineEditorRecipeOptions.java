/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.RecipeOptionsEditor;
import hellfirepvp.modularmachinery.common.editor.RecipeTooltipEditor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A single save for presentation and advanced recipe policies; identity and requirements are retained. */
public record PktMachineEditorRecipeOptions(String recipeId, String displayName, int ticks, int priority, boolean cancel,
                                          String threadName, int maxThreads, boolean parallelized, boolean loadJEI, List<String> tooltips) {
    public static void encode(PktMachineEditorRecipeOptions packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId, 256); buffer.writeUtf(packet.displayName, 200);
        buffer.writeVarInt(packet.ticks); buffer.writeInt(packet.priority); buffer.writeBoolean(packet.cancel);
        buffer.writeUtf(packet.threadName, 256); buffer.writeInt(packet.maxThreads);
        buffer.writeBoolean(packet.parallelized); buffer.writeBoolean(packet.loadJEI);
        RecipeTooltipEditor.validate(packet.tooltips);
        buffer.writeVarInt(packet.tooltips.size()); for (var line : packet.tooltips) buffer.writeUtf(line, RecipeTooltipEditor.MAX_LINE_LENGTH);
    }
    public static PktMachineEditorRecipeOptions decode(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(256), name = buffer.readUtf(200); int ticks = buffer.readVarInt(), priority = buffer.readInt(); boolean cancel = buffer.readBoolean();
        String thread = buffer.readUtf(256); int limit = buffer.readInt(); boolean batch = buffer.readBoolean(), jei = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > RecipeTooltipEditor.MAX_LINES) throw new IllegalArgumentException("附加说明行数无效");
        var lines = new ArrayList<String>(count); int total = 0;
        for (int index = 0; index < count; index++) {
            String line = buffer.readUtf(RecipeTooltipEditor.MAX_LINE_LENGTH); total += line.length();
            if (total > RecipeTooltipEditor.MAX_CHARACTERS) throw new IllegalArgumentException("附加说明过长");
            lines.add(line);
        }
        return new PktMachineEditorRecipeOptions(id, name, ticks, priority, cancel, thread, limit, batch, jei, List.copyOf(lines));
    }
    static String apply(ServerPlayer player, PktMachineEditorRecipeOptions packet) {
        try {
            var recipes = MachineReloadListener.getRecipeLoader();
            if (!player.hasPermissions(2) || !(player.containerMenu instanceof MenuMachineEditor)
                    || !recipes.isSourceEditable(packet.recipeId)) return "配方不存在、来源只读或权限不足，未保存修改";
            var loadedSource = recipes.getRecipeSource(packet.recipeId);
            if (loadedSource == null) return "配方源文档不可用，请重新打开编辑器";
            var root = ModDataHolder.INSTANCE.getRecipeDirectory().getCanonicalFile().toPath();
            var file = new java.io.File(recipes.getSourcePath(packet.recipeId)).getCanonicalFile().toPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return "配方源文件无效";
            JsonObject source;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { source = JsonParser.parseReader(reader).getAsJsonObject(); }
            if (!loadedSource.equals(source)) return "配方源文件已发生变化，请重新加载并打开编辑器";
            var result = RecipeOptionsEditor.merge(source, packet.displayName, packet.ticks,
                    new RecipeOptionsEditor.Options(packet.priority, packet.cancel, packet.threadName, packet.maxThreads, packet.parallelized, packet.loadJEI, packet.tooltips));
            var temp = Files.createTempFile(root, "recipe-options-", ".mmce.tmp");
            try {
                Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
            MachineReloadListener.reloadNow();
            return null;
        } catch (Exception invalid) { return "配方高级设置保存失败：" + invalid.getMessage(); }
    }
}
