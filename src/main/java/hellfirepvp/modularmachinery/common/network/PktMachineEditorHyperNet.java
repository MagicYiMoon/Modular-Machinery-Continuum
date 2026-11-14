/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.editor.HyperNetSettingsEditor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Only dispatched by the window/permission/revision checked request envelope. */
public record PktMachineEditorHyperNet(String machine,String settings) {
    public static void encode(PktMachineEditorHyperNet p,FriendlyByteBuf b){b.writeUtf(p.machine,256);b.writeUtf(p.settings,32767);}
    public static PktMachineEditorHyperNet decode(FriendlyByteBuf b){return new PktMachineEditorHyperNet(b.readUtf(256),b.readUtf(32767));}
    static String apply(ServerPlayer player,PktMachineEditorHyperNet p) {
        try {
            var expected=HyperNetSettingsEditor.validate(JsonParser.parseString(p.settings).getAsJsonObject());
            HyperNetSettingsEditor.save(MachineReloadListener.getLoader(),p.machine,expected);
            MachineReloadListener.reloadNow();
            if(MachineReloadListener.getLoader().findByName(p.machine)==null
                    ||!HyperNetSettingsEditor.profile(MachineReloadListener.getLoader(),p.machine).equals(expected))
                return "文件已保存，但机器设置重新加载失败，请检查服务端日志";
            return null;
        } catch(Exception e){return "算力／提示设置保存失败："+e.getMessage();}
    }
}
