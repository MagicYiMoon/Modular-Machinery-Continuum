/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Edits only visual/registration options, retaining structure and all unrelated JSON fields. */
public final class ControllerSettingsEditor {
    private ControllerSettingsEditor() {}
    public static void save(MachineLoader machines, String name, int color, boolean factory) throws java.io.IOException {
        if (name == null || name.isBlank() || color < 0 || color > 0xFFFFFF)
            throw new IllegalArgumentException("颜色必须是 000000～FFFFFF 的 RGB 值");
        String source = machines.getSourcePath(name);
        if (source == null) throw new IllegalArgumentException("该机器没有可编辑的 JSON 源文件");
        Path file = new java.io.File(source).getCanonicalFile().toPath();
        Path root = ModDataHolder.INSTANCE.getMachineryDirectory().getCanonicalFile().toPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("机器来源只读，未保存修改");
        JsonObject json;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { json = JsonParser.parseReader(reader).getAsJsonObject(); }
        if (!json.has("registryname") || !name.equals(json.get("registryname").getAsString()))
            throw new IllegalArgumentException("机器文件已发生变化，请重新加载后再编辑");
        json.addProperty("color", String.format(java.util.Locale.ROOT, "%06X", color));
        json.addProperty("has-factory", factory);
        Path temp = file.resolveSibling(file.getFileName() + ".mmc.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
