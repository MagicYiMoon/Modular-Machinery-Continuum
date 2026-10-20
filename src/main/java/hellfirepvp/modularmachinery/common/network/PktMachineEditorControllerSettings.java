/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.common.editor.ControllerSettingsEditor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Sent only inside the permission-, window- and revision-checked editor request envelope. */
public record PktMachineEditorControllerSettings(String machine, int color, boolean factory) {
    public static void encode(PktMachineEditorControllerSettings packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.machine, 256); buffer.writeInt(packet.color); buffer.writeBoolean(packet.factory);
    }
    public static PktMachineEditorControllerSettings decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorControllerSettings(buffer.readUtf(256), buffer.readInt(), buffer.readBoolean());
    }
    static String apply(ServerPlayer player, PktMachineEditorControllerSettings packet) {
        if (!player.hasPermissions(2) || !(player.containerMenu instanceof MenuMachineEditor)) return "权限不足或编辑窗口已关闭";
        try {
            ControllerSettingsEditor.save(MachineReloadListener.getLoader(), packet.machine, packet.color, packet.factory);
            MachineReloadListener.reloadNow(); return null;
        } catch (Exception e) { return "控制器设置保存失败：" + e.getMessage(); }
    }
}
