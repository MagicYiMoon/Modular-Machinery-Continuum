/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

/** Window- and revision-bound envelope around supported editor mutations. */
public record PktMachineEditorRequest(int containerId, UUID requestId, String revision, Object mutation) {
    public static void encode(PktMachineEditorRequest packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeUUID(packet.requestId);
        buffer.writeUtf(packet.revision, 64);
        if (packet.mutation instanceof PktMachineEditorMachineEdit edit) { buffer.writeByte(0); PktMachineEditorMachineEdit.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorRecipeEdit edit) { buffer.writeByte(1); PktMachineEditorRecipeEdit.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorEdit edit) { buffer.writeByte(2); PktMachineEditorEdit.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorRequirementAdd edit) { buffer.writeByte(3); PktMachineEditorRequirementAdd.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorAction edit) { buffer.writeByte(4); PktMachineEditorAction.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorIngredientGroup edit) { buffer.writeByte(5); PktMachineEditorIngredientGroup.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorDurability edit) { buffer.writeByte(6); PktMachineEditorDurability.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorFuel edit) { buffer.writeByte(7); PktMachineEditorFuel.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorSelector edit) { buffer.writeByte(8); PktMachineEditorSelector.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorSmartInterface edit) { buffer.writeByte(9); PktMachineEditorSmartInterface.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorFluidTick edit) { buffer.writeByte(10); PktMachineEditorFluidTick.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorRecipeOptions edit) { buffer.writeByte(11); PktMachineEditorRecipeOptions.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorRequirementBatch edit) { buffer.writeByte(12); PktMachineEditorRequirementBatch.encode(edit, buffer); }
        else if (packet.mutation instanceof PktMachineEditorRequirementTrigger edit) { buffer.writeByte(13); PktMachineEditorRequirementTrigger.encode(edit, buffer); }
        else throw new IllegalArgumentException("Unknown editor operation");
    }
    public static PktMachineEditorRequest decode(FriendlyByteBuf buffer) {
        int window = buffer.readVarInt();
        UUID id = buffer.readUUID();
        String revision = buffer.readUtf(64);
        Object mutation = switch (buffer.readUnsignedByte()) {
            case 0 -> PktMachineEditorMachineEdit.decode(buffer);
            case 1 -> PktMachineEditorRecipeEdit.decode(buffer);
            case 2 -> PktMachineEditorEdit.decode(buffer);
            case 3 -> PktMachineEditorRequirementAdd.decode(buffer);
            case 4 -> PktMachineEditorAction.decode(buffer);
            case 5 -> PktMachineEditorIngredientGroup.decode(buffer);
            case 6 -> PktMachineEditorDurability.decode(buffer);
            case 7 -> PktMachineEditorFuel.decode(buffer);
            case 8 -> PktMachineEditorSelector.decode(buffer);
            case 9 -> PktMachineEditorSmartInterface.decode(buffer);
            case 10 -> PktMachineEditorFluidTick.decode(buffer);
            case 11 -> PktMachineEditorRecipeOptions.decode(buffer);
            case 12 -> PktMachineEditorRequirementBatch.decode(buffer);
            case 13 -> PktMachineEditorRequirementTrigger.decode(buffer);
            default -> throw new IllegalArgumentException("Unknown editor operation");
        };
        return new PktMachineEditorRequest(window, id, revision, mutation);
    }
    public static void handle(PktMachineEditorRequest packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            Reply reply = process(player, packet);
            if (reply.snapshot != null) sendSnapshot(player, packet.containerId, packet.requestId, reply.snapshot);
            ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply.result);
        });
        context.setPacketHandled(true);
    }

    public record Reply(PktMachineEditorResult result, String snapshot) {}

    /** Executed on the server thread; also used by the opt-in behavioral suite without a network connection. */
    public static Reply process(ServerPlayer player, PktMachineEditorRequest packet) {
        if (!(player.containerMenu instanceof MenuMachineEditor menu) || menu.containerId != packet.containerId)
            return failure(packet, "编辑窗口已关闭或失效，请重新打开");
        if (!player.hasPermissions(2)) return failure(packet, "权限不足，未保存修改");
        if (!menu.acceptRequest(packet.requestId)) return failure(packet, "重复请求已忽略，请重新打开编辑器");
        try {
            if (!EditorSaveState.revision(snapshot()).equals(packet.revision))
                return failure(packet, "机器或配方已发生变化，请重新打开编辑器后再修改");
            String error;
            if (packet.mutation instanceof PktMachineEditorMachineEdit edit) error = PktMachineEditorMachineEdit.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorRecipeEdit edit) error = PktMachineEditorRecipeEdit.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorEdit edit) error = PktMachineEditorEdit.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorRequirementAdd edit) error = PktMachineEditorRequirementAdd.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorIngredientGroup edit) error = PktMachineEditorIngredientGroup.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorDurability edit) error = PktMachineEditorDurability.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorFuel edit) error = PktMachineEditorFuel.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorSelector edit) error = PktMachineEditorSelector.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorSmartInterface edit) error = PktMachineEditorSmartInterface.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorFluidTick edit) error = PktMachineEditorFluidTick.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorRecipeOptions edit) error = PktMachineEditorRecipeOptions.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorRequirementBatch edit) error = PktMachineEditorRequirementBatch.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorRequirementTrigger edit) error = PktMachineEditorRequirementTrigger.apply(player, edit);
            else if (packet.mutation instanceof PktMachineEditorAction edit) {
                if (edit.action != PktMachineEditorAction.DELETE_MACHINE && edit.action != PktMachineEditorAction.DELETE_RECIPE
                        && edit.action != PktMachineEditorAction.DELETE_REQUIREMENT) return failure(packet, "不支持的编辑操作");
                error = PktMachineEditorAction.apply(player, edit);
            } else return failure(packet, "不支持的编辑操作");
            if (error != null) return failure(packet, error);
            // apply() reloads on the server thread before returning; check the target is still usable.
            String recipeId = packet.mutation instanceof PktMachineEditorRequirementAdd add ? add.recipeId()
                    : packet.mutation instanceof PktMachineEditorIngredientGroup edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorDurability edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorFuel edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorSelector edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorSmartInterface edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorFluidTick edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorRequirementBatch edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorRequirementTrigger edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorRecipeOptions edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorRecipeEdit edit ? edit.recipeId()
                    : packet.mutation instanceof PktMachineEditorEdit edit ? edit.recipeName
                    : packet.mutation instanceof PktMachineEditorAction edit && edit.action == PktMachineEditorAction.DELETE_REQUIREMENT
                    ? edit.recipeName : null;
            if (recipeId != null && MachineReloadListener.getRecipeLoader().getRecipe(recipeId) == null)
                return failure(packet, "文件已写入，但配方重新加载失败，请检查服务器日志");
            if (packet.mutation instanceof PktMachineEditorMachineEdit edit
                    && MachineReloadListener.getLoader().findByName(edit.machineName) == null)
                return failure(packet, "文件已写入，但机器重新加载失败，请检查服务器日志");
            if (packet.mutation instanceof PktMachineEditorAction edit) {
                if (edit.action == PktMachineEditorAction.DELETE_RECIPE && MachineReloadListener.getRecipeLoader().getRecipe(edit.recipeName) != null)
                    return failure(packet, "文件已删除，但同名配方仍由其他来源加载，请检查源文件");
                if (edit.action == PktMachineEditorAction.DELETE_MACHINE && MachineReloadListener.getLoader().findByName(edit.machineName) != null)
                    return failure(packet, "文件已删除，但同名机器仍由其他来源加载，请检查源文件");
            }
            String fresh = snapshot();
            if (fresh.getBytes(StandardCharsets.UTF_8).length > MenuMachineEditor.MAX_SNAPSHOT_BYTES)
                return failure(packet, "文件已保存，但内容超过同步上限，请重新打开编辑器");
            menu.setSnapshot(fresh);
            return new Reply(new PktMachineEditorResult(packet.containerId, packet.requestId, true, "已保存并重新加载"), fresh);
        } catch (Exception failure) {
            Modularmachinery.LOG.error("[MMCE] editor request failed", failure);
            return failure(packet, "保存或同步失败：" + failure.getMessage());
        }
    }
    private static Reply failure(PktMachineEditorRequest packet, String message) {
        String detail = message.length() <= 400 ? message : message.substring(0, 400);
        return new Reply(new PktMachineEditorResult(packet.containerId, packet.requestId, false, detail), null);
    }
    public static String snapshot() {
        return MachineEditorSnapshot.create(MachineReloadListener.getLoader(), MachineReloadListener.getRecipeLoader());
    }
    public static void sendSnapshot(ServerPlayer player, int containerId, UUID transfer, String snapshot) {
        byte[] bytes = snapshot.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MenuMachineEditor.MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("编辑器内容超过同步上限");
        for (int offset = 0; offset < bytes.length; offset += PktMachineEditorSnapshot.CHUNK_BYTES) {
            ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PktMachineEditorSnapshot(containerId, transfer, bytes.length, offset,
                            Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + PktMachineEditorSnapshot.CHUNK_BYTES))));
        }
    }
}
