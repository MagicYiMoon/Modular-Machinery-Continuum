/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.machine.SelectorTags;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/** Copies authoritative JSON, never a resolved display snapshot or a script's callbacks. */
public final class RecipeCopyEditor {
    private RecipeCopyEditor() {}

    public static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,120}") || id.endsWith("."))
            throw new IllegalArgumentException("新 ID 须为 1～121 位小写字母、数字、下划线、点或横线，首位为字母或数字，末尾不能为点");
        String stem = id.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))
            throw new IllegalArgumentException("新 ID 不能使用系统保留文件名");
    }

    public static String suggestId(String sourceId) {
        String base = sourceId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (base.isEmpty() || !Character.isLetterOrDigit(base.charAt(0))) base = "recipe_" + base;
        if (base.length() > 105) base = base.substring(0, 105);
        // The prefix also keeps reserved source IDs such as CON from becoming reserved filenames.
        if (base.split("\\.", 2)[0].matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) base = "recipe_" + base;
        return base + "_copy";
    }

    public static JsonObject duplicate(JsonObject source, String machine, String newId) {
        validateId(newId);
        if (source == null || source.has("adapter")) throw new IllegalArgumentException("仅支持普通 JSON 配方复制");
        var copy = source.deepCopy();
        copy.remove("registryname");
        copy.addProperty("registryName", newId);
        // A multi-machine source produces one selected instance, not a second machine array.
        copy.addProperty("machine", machine);
        return copy;
    }

    /** Shared preview/authoritative checks; never drops source references to make a copy appear valid. */
    public static JsonObject profile(hellfirepvp.modularmachinery.common.machine.DynamicMachine machine) {
        var result = new JsonObject();
        var selectors = new JsonArray(); SelectorBindingEditor.options(machine).forEach(selectors::add); result.add("selectorTags", selectors);
        var types = new JsonArray(); SmartInterfaceEditor.options(machine).forEach(types::add); result.add("smartInterfaceTypes", types);
        var threads = new JsonObject(); machine.getCoreThreadPresets().forEach((name, ids) -> { var array = new JsonArray(); ids.forEach(array::add); threads.add(name, array); }); result.add("coreThreads", threads);
        return result;
    }
    public static void validateReferences(JsonObject recipe, JsonObject target, String newId) {
        if (recipe.has("requirements")) for (var entry : recipe.getAsJsonArray("requirements")) {
            var req = entry.getAsJsonObject(); String tag = SelectorTags.read(req);
            if (tag != null && !tag.isEmpty() && !contains(target.getAsJsonArray("selectorTags"), tag)) throw new IllegalArgumentException("目标机器缺少接口分组：" + tag);
            if (SmartInterfaceEditor.isSmart(req) && !contains(target.getAsJsonArray("smartInterfaceTypes"), req.get("interface").getAsString()))
                throw new IllegalArgumentException("目标机器缺少智能接口类型：" + req.get("interface").getAsString());
        }
        String thread = recipe.has("threadName") ? recipe.get("threadName").getAsString() : "";
        if (!thread.isEmpty()) {
            var threads = target.getAsJsonObject("coreThreads");
            if (threads == null || !threads.has(thread)) throw new IllegalArgumentException("目标机器缺少核心线程：" + thread);
            var allowed = threads.getAsJsonArray(thread);
            if (!allowed.isEmpty() && !contains(allowed, newId)) throw new IllegalArgumentException("核心线程的配方白名单不包含新 ID，请先修改目标机器定义");
        }
    }
    private static boolean contains(JsonArray values, String value) {
        return values != null && values.contains(new JsonPrimitive(value));
    }

    /** Called only after the request envelope has validated permissions, window and revision. */
    public static void copy(String sourceId, String machine, String newId) throws Exception {
        validateId(newId);
        var recipes = MachineReloadListener.getRecipeLoader();
        MachineRecipe original = recipes.getRecipe(sourceId);
        if (original == null || !recipes.isSourceEditable(sourceId))
            throw new IllegalArgumentException("仅支持已保存的普通 JSON 配方；脚本和适配器生成配方不能直接复制");
        var targetMachine = MachineReloadListener.getLoader().findByName(machine);
        if (targetMachine == null) throw new IllegalArgumentException("目标机器不存在，请重新打开编辑器");
        if (recipes.getRecipe(newId) != null) throw new IllegalArgumentException("新配方 ID 已存在，请更换 ID");
        Path root = ModDataHolder.INSTANCE.getRecipeDirectory().getCanonicalFile().toPath();
        Path sourceFile = new java.io.File(recipes.getSourcePath(sourceId)).getCanonicalFile().toPath();
        Path target = root.resolve(newId + ".json");
        if (!sourceFile.startsWith(root) || !Files.isRegularFile(sourceFile))
            throw new IllegalArgumentException("源配方文件不存在或不在配方目录内");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("目标文件已存在，请更换 ID");
        JsonObject source;
        try (var reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            source = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!source.equals(recipes.getRecipeSource(sourceId)))
            throw new IllegalArgumentException("源文件已在磁盘上修改，请先重新加载并打开编辑器");
        if (!original.machine().equals(machine)) validateReferences(source, profile(targetMachine), newId);
        String content = new GsonBuilder().setPrettyPrinting().create().toJson(duplicate(source, machine, newId));
        Path temporary = Files.createTempFile(root, ".mmc-copy-", ".tmp");
        boolean published = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            // No REPLACE_EXISTING, including in the fallback path: a concurrent file must never be lost.
            Files.move(temporary, target);
            published = true;
            MachineReloadListener.reloadNow();
            var reloaded = MachineReloadListener.getRecipeLoader();
            var loaded = reloaded.getRecipe(newId);
            String loadedPath = reloaded.getSourcePath(newId);
            if (loaded == null || !machine.equals(loaded.machine()) || loadedPath == null
                    || !new java.io.File(loadedPath).getCanonicalFile().toPath().equals(target))
                throw new IllegalArgumentException("新配方未成功加载，已撤销本次复制，请检查服务器日志");
        } catch (Exception failure) {
            if (published) {
                // Only undo our own unchanged file; never remove a concurrent author's edit.
                if (Files.isRegularFile(target) && content.equals(Files.readString(target, StandardCharsets.UTF_8))) {
                    Files.delete(target);
                    MachineReloadListener.reloadNow();
                } else throw new IllegalStateException("复制失败且目标文件已被其他操作更改，请检查服务器日志和目标文件", failure);
            }
            throw failure;
        } finally { Files.deleteIfExists(temporary); }
    }
}
