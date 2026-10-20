/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.event.recipe.RecipeEvent;
import java.util.*;
import java.util.function.Consumer;

/** Server script declarations, stored independently of loader reloads and optional scripting classes. */
public final class ScriptRecipeRegistry {
    private static final Map<String, JsonObject> recipes = new LinkedHashMap<>();
    private static final Map<String, Map<Class<?>, List<Consumer<Object>>>> handlers = new LinkedHashMap<>();
    private static final Map<String, ScriptRecipeCallbacks> callbacks = new LinkedHashMap<>();
    private ScriptRecipeRegistry() { }

    public static synchronized void clear() { recipes.clear(); handlers.clear(); callbacks.clear(); }
    public static synchronized void registerCallbacks(String name, ScriptRecipeCallbacks value) {
        if (name != null && value != null) callbacks.put(name, value);
    }
    public static synchronized void applyCallbacks(String name, MachineRecipe recipe) {
        ScriptRecipeCallbacks value = callbacks.get(name);
        if (value != null && recipe != null) value.apply(recipe.requirements());
    }
    public static synchronized void register(JsonObject recipe) {
        String name = recipe.get("name").getAsString();
        if (recipes.containsKey(name)) throw new IllegalArgumentException("Duplicate script recipe: " + name);
        recipes.put(name, recipe.deepCopy());
    }
    /** Installs live server-side callbacks belonging to one CraftTweaker recipe declaration. */
    public static synchronized void registerHandlers(String name,
                                                      Map<Class<?>, List<Consumer<?>>> declared) {
        if (declared == null || declared.isEmpty()) return;
        Map<Class<?>, List<Consumer<Object>>> copy = handlers.computeIfAbsent(name, ignored -> new LinkedHashMap<>());
        declared.forEach((type, callbacks) -> {
            if (callbacks == null) return;
            List<Consumer<Object>> target = copy.computeIfAbsent(type, ignored -> new ArrayList<>());
            for (Consumer<?> callback : callbacks) {
                if (callback != null) target.add((Consumer<Object>) callback);
            }
        });
    }
    /** Dispatches the exact event class, preserving source order and cancellation short-circuiting. */
    public static void dispatch(RecipeEvent event) {
        if (event == null || event.getContext() == null || event.getContext().getRecipe() == null) return;
        String name = event.getContext().getRecipe().registryName();
        List<Consumer<Object>> callbacks;
        synchronized (ScriptRecipeRegistry.class) {
            Map<Class<?>, List<Consumer<Object>>> byType = handlers.get(name);
            callbacks = byType == null ? List.of() : List.copyOf(byType.getOrDefault(event.getClass(), List.of()));
        }
        for (Consumer<Object> callback : callbacks) {
            try {
                callback.accept(event);
            } catch (Throwable failure) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                        "[MMCE] CraftTweaker recipe event handler failed for {}", name, failure);
            }
            if (event.isCanceled()) break;
        }
    }
    public static synchronized List<JsonObject> snapshots() {
        return recipes.values().stream().map(JsonObject::deepCopy).toList();
    }
}
