/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Saves a recipe's presentation name and duration together; never changes its identity. */
public record PktMachineEditorRecipeEdit(String recipeId, String displayName, int ticks) {
    public static void send(String recipeId, String displayName, int ticks) {
        ModularmachineryNetwork.CHANNEL.sendToServer(new PktMachineEditorRecipeEdit(recipeId, displayName, ticks));
    }

    public static void encode(PktMachineEditorRecipeEdit message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.recipeId, 256);
        buffer.writeUtf(message.displayName, 200);
        buffer.writeVarInt(message.ticks);
    }

    public static PktMachineEditorRecipeEdit decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorRecipeEdit(buffer.readUtf(256), buffer.readUtf(200), buffer.readVarInt());
    }

    public static void handle(PktMachineEditorRecipeEdit message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) player.displayClientMessage(Component.literal("请使用新版编辑器重新提交修改"), true);
        });
        context.setPacketHandled(true);
    }

    static String apply(ServerPlayer player, PktMachineEditorRecipeEdit message) {
        if (!player.hasPermissions(2) || !(player.containerMenu instanceof MenuMachineEditor)
                || message.recipeId.isBlank() || message.displayName.length() > 200
                || message.ticks < 1 || message.ticks > 2_000_000_000) return "编辑数据无效、来源只读或文件不存在，未保存修改";
        var recipes = MachineReloadListener.getRecipeLoader();
        if (!recipes.isSourceEditable(message.recipeId)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
        try {
            String source = recipes.getSourcePath(message.recipeId);
            if (source == null) return "编辑数据无效、来源只读或文件不存在，未保存修改";
            var file = new java.io.File(source).getCanonicalFile().toPath();
            var root = hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE
                    .getRecipeDirectory().getCanonicalFile().toPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
            JsonObject json;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (message.displayName.isBlank()) json.remove("displayName");
            else json.addProperty("displayName", MachineNameFormatting.display(message.displayName));
            json.addProperty("recipeTime", message.ticks);
            var temp = file.resolveSibling(file.getFileName() + ".mmce.tmp");
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            player.displayClientMessage(Component.literal("已保存配方名称和运行时间，正在重新加载"), true);
            MachineReloadListener.reloadNow();
            return null;
        } catch (Exception failure) {
            Modularmachinery.LOG.error("[MMCE] editor could not save recipe {}", message.recipeId, failure);
            player.displayClientMessage(Component.literal("配方保存失败：" + failure.getMessage()), true);
            return "保存失败：" + failure.getMessage();
        }
    }
}
