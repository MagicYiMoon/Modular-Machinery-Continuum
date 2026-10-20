/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.List;

/** Presentation and source recipe options exposed by the advanced properties panel. */
public final class RecipeOptionsEditor {
    private RecipeOptionsEditor() {}
    public record Options(int priority, boolean cancel, String threadName, int maxThreads, boolean parallelized, boolean loadJEI, List<String> tooltips) {
        public Options { tooltips = List.copyOf(tooltips); }
    }
    public static Options read(JsonObject snapshot) {
        return new Options(snapshot.has("priority") ? snapshot.get("priority").getAsInt() : 0,
                snapshot.has("cancel") && snapshot.get("cancel").getAsBoolean(),
                snapshot.has("threadName") ? snapshot.get("threadName").getAsString() : "",
                snapshot.has("maxThreads") ? snapshot.get("maxThreads").getAsInt() : -1,
                snapshot.has("parallelized") ? snapshot.get("parallelized").getAsBoolean()
                        : hellfirepvp.modularmachinery.common.data.Config.recipeParallelizeEnabledByDefault(),
                !snapshot.has("loadJEI") || snapshot.get("loadJEI").getAsBoolean(), RecipeTooltipEditor.read(snapshot));
    }
    public static void validateThreads(String name, int limit) {
        if (name == null || name.length() > 256 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("线程名称最多 256 字，不能含换行或控制字符");
        if (limit < -1) throw new IllegalArgumentException("配方线程上限须为 -1～2147483647 的整数");
    }
    public static JsonObject merge(JsonObject source, String name, int ticks, Options options) {
        if (source == null || options == null || name == null || name.length() > 200 || ticks < 1 || ticks > 2_000_000_000)
            throw new IllegalArgumentException("配方名称或运行时间无效");
        validateThreads(options.threadName(), options.maxThreads());
        RecipeTooltipEditor.validate(options.tooltips());
        var result = source.deepCopy();
        if (name.isBlank()) result.remove("displayName");
        else result.addProperty("displayName", MachineNameFormatting.display(name));
        result.addProperty("recipeTime", ticks);
        if (source.has("priority") || options.priority() != 0) result.addProperty("priority", options.priority());
        if (source.has("cancelIfPerTickFails") || options.cancel()) result.addProperty("cancelIfPerTickFails", options.cancel());
        if (source.has("threadName") || !options.threadName().isEmpty()) result.addProperty("threadName", options.threadName());
        if (source.has("maxThreads") || options.maxThreads() != -1) result.addProperty("maxThreads", options.maxThreads());
        if (source.has("parallelized") || options.parallelized() != hellfirepvp.modularmachinery.common.data.Config.recipeParallelizeEnabledByDefault()) result.addProperty("parallelized", options.parallelized());
        if (source.has("loadJEI") || !options.loadJEI()) result.addProperty("loadJEI", options.loadJEI());
        if (source.has("tooltips") || !options.tooltips().isEmpty()) {
            var lines = new JsonArray(); options.tooltips().forEach(lines::add); result.add("tooltips", lines);
        }
        return result;
    }
}
