/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import java.util.*;

/** Recipe-level text only; requirement/catalyst tooltips remain independent. */
public final class RecipeTooltipEditor {
    private RecipeTooltipEditor() {}
    public static final int MAX_LINES = 64, MAX_LINE_LENGTH = 512, MAX_CHARACTERS = 8192;
    public static List<String> read(JsonObject snapshot) {
        var lines = new ArrayList<String>();
        if (snapshot.has("tooltips")) for (var line : snapshot.getAsJsonArray("tooltips")) lines.add(line.getAsString());
        return List.copyOf(lines);
    }
    public static void validate(List<String> lines) {
        if (lines == null || lines.size() > MAX_LINES) throw new IllegalArgumentException("附加说明最多 64 行");
        int total = 0;
        for (var line : lines) {
            if (line == null || line.length() > MAX_LINE_LENGTH || line.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("说明每行最多 512 字，不能含换行或控制字符");
            total += line.length();
        }
        if (total > MAX_CHARACTERS) throw new IllegalArgumentException("附加说明总长度最多 8192 字");
    }
    /** Unchanged raw ampersands and legacy section codes must survive opening other property pages. */
    public static String editedLine(String original, String text) {
        return text.equals(MachineNameFormatting.editable(original)) ? original : MachineNameFormatting.display(text);
    }
}
