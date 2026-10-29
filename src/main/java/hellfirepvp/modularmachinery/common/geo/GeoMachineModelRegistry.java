/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Named models and explicit machine bindings, as in the source mod. No client renderer types. */
public final class GeoMachineModelRegistry {
    private record Definitions(Map<String, MachineGeoModel> models, Map<String, String> bindings) {}
    private static final Definitions SCRIPT = new Definitions(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    private static volatile Definitions remote;
    private static final Map<String, List<Consumer<ControllerModelAnimationEvent>>> HANDLERS = new ConcurrentHashMap<>();
    private static final Set<String> FAILED_HANDLERS = ConcurrentHashMap.newKeySet();
    private static long revision;

    private GeoMachineModelRegistry() {}

    public static MachineGeoModel registerModel(String modelName, String model, String texture, String animation) {
        if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName cannot be empty");
        MachineGeoModel definition = new MachineGeoModel(modelName, location(model), location(texture), location(animation));
        SCRIPT.models.put(modelName, definition);
        revision++;
        return definition;
    }

    public static void bindMachine(String machineName, String modelName) {
        if (machineName == null || machineName.isBlank()) throw new IllegalArgumentException("machineName cannot be empty");
        if (!SCRIPT.models.containsKey(modelName)) throw new IllegalArgumentException("unknown GeckoLib model: " + modelName);
        SCRIPT.bindings.put(machineName, modelName);
        revision++;
    }

    /** Script-side lookup. Registering a model does not automatically bind a machine. */
    public static MachineGeoModel get(String machineName) { return find(SCRIPT, machineName); }

    /** Resolves a named model from the same client snapshot used by a rendered controller. */
    public static MachineGeoModel getForController(TileController controller, String modelName) {
        if (controller == null || modelName == null || modelName.isBlank()) return null;
        Definitions definitions = (controller.isBlueprintPreview()
                || controller.getLevel() != null && controller.getLevel().isClientSide) && remote != null
                ? remote : SCRIPT;
        return definitions.models.get(modelName);
    }

    /** Separate client snapshots prevent an integrated client from overwriting the server registry. */
    public static MachineGeoModel getForController(TileController controller) {
        Definitions definitions = (controller.isBlueprintPreview()
                || controller.getLevel() != null && controller.getLevel().isClientSide) && remote != null
                ? remote : SCRIPT;
        return find(definitions, controller.getMachineName());
    }

    private static MachineGeoModel find(Definitions definitions, String machineName) {
        if (machineName == null) return null;
        String modelName = definitions.bindings.get(machineName);
        return modelName == null ? null : definitions.models.get(modelName);
    }

    /** One selection rule for geometry, texture, animation resources and animation predicates. */
    public static MachineGeoModel selectedForController(TileController controller) {
        if (controller == null) return null;
        MachineGeoModel base = getForController(controller);
        if (base == null) return null;
        var event = new hellfirepvp.modularmachinery.common.event.machine.ControllerModelGetEvent(
                controller, base.getMachineName());
        event.postEvent();
        MachineGeoModel selected = getForController(controller, event.getModelName());
        return selected == null ? base : selected;
    }

    public static long revision() { return revision; }

    public static void clear() {
        SCRIPT.models.clear();
        SCRIPT.bindings.clear();
        HANDLERS.clear();
        FAILED_HANDLERS.clear();
        revision++;
    }

    public static void clearClientSnapshot() { remote = null; revision++; }

    /**
     * Invalidates GeckoLib-backed controller animation state after the client resource manager
     * finishes a reload.  GeckoLib replaces its baked geometry and animation maps in place, while
     * existing block entities keep their AnimationController instances; the revision lets those
     * instances request one clean reset on their next render tick.
     */
    public static void markClientResourcesReloaded() { revision++; }

    public static void addAnimationHandler(String machineName, Consumer<ControllerModelAnimationEvent> callback) {
        if (machineName == null || machineName.isBlank() || callback == null)
            throw new IllegalArgumentException("machine and animation handler must be supplied");
        HANDLERS.computeIfAbsent(machineName, key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(callback);
        FAILED_HANDLERS.remove(machineName);
    }

    public static void dispatchAnimation(String machineName, ControllerModelAnimationEvent event) {
        if (machineName == null) return;
        if (FAILED_HANDLERS.contains(machineName)) {
            event.setPlayState(1);
            return;
        }
        try {
            for (var callback : HANDLERS.getOrDefault(machineName, List.of())) callback.accept(event);
        } catch (RuntimeException failure) {
            // Render predicates run every frame; one broken script must not flood the log or crash rendering.
            FAILED_HANDLERS.add(machineName);
            Modularmachinery.LOG.error("[MMCE] disabled animation handler for '{}' until script reload", machineName, failure);
            event.setPlayState(1);
        }
    }

    public static JsonArray snapshot() {
        JsonArray array = new JsonArray();
        SCRIPT.models.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value -> {
            MachineGeoModel model = value.getValue();
            JsonObject entry = new JsonObject();
            entry.addProperty("name", value.getKey());
            entry.addProperty("model", model.getModel().toString());
            entry.addProperty("texture", model.getTexture().toString());
            entry.addProperty("animation", model.getAnimation().toString());
            JsonArray animations = new JsonArray();
            model.getAnimations().forEach(animation -> {
                JsonObject a = new JsonObject();
                a.addProperty("name", animation.name());
                a.addProperty("loop", animation.loop());
                animations.add(a);
            });
            entry.add("animations", animations);
            array.add(entry);
        });
        SCRIPT.bindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(binding -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("binding", binding.getKey());
            entry.addProperty("modelName", binding.getValue());
            array.add(entry);
        });
        return array;
    }

    /** Parses off to the side; a bad packet never partially replaces the live registry. */
    public static void installSnapshot(JsonArray snapshot) {
        Definitions next = new Definitions(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        List<JsonObject> bindings = new ArrayList<>();
        if (snapshot != null) for (var element : snapshot) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("binding")) { bindings.add(entry); continue; }
            String name = entry.get("name").getAsString();
            MachineGeoModel model = new MachineGeoModel(name, location(entry.get("model").getAsString()),
                    location(entry.get("texture").getAsString()), location(entry.get("animation").getAsString()));
            if (entry.has("animations")) entry.getAsJsonArray("animations").forEach(value -> {
                JsonObject animation = value.getAsJsonObject();
                model.addAnimation(animation.get("name").getAsString(), animation.get("loop").getAsBoolean());
            });
            next.models.put(name, model);
        }
        for (JsonObject entry : bindings) {
            String name = entry.get("modelName").getAsString();
            if (!next.models.containsKey(name)) throw new IllegalArgumentException("missing model in snapshot: " + name);
            next.bindings.put(entry.get("binding").getAsString(), name);
        }
        remote = next;
        revision++;
    }

    private static ResourceLocation location(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("resource cannot be empty");
        ResourceLocation location = new ResourceLocation(value);
        // Source API's namespace fallback, including explicitly supplied minecraft paths.
        return "minecraft".equals(location.getNamespace())
                ? new ResourceLocation(Modularmachinery.MODID, location.getPath()) : location;
    }
}
