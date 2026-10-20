
/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads machine definitions and variable lists from {@code config/modularmachinery/machinery/}.
 *
 * <p><b>Architectural decision (carried from the plan).</b> These definitions are <b>not</b> vanilla
 * recipes and are deliberately kept in the mod's own loader rather than moved into a datapack. They
 * describe a multiblock pattern plus per-recipe requirement components — JSON multiblock layouts,
 * energy/fluid/item requirements with counts, and command callbacks — none of which vanilla's
 * {@code RecipeType}/{@code RecipeSerializer} can express. Moving them into {@code data/} would force
 * them through that machinery and lose the loader's own validation, which existing addon packs depend
 * on.
 *
 * <p><b>What changed in this round (PLAN 636, 641 steps 1-3).</b> This loader used to read a fixed
 * list of names out of its own jar, which made a file that was not in the list <b>invisible</b> — no
 * error and no log line. It now reads the config directory that
 * {@link hellfirepvp.modularmachinery.common.data.ModDataHolder} creates and seeds from the jar on the
 * first run, and discovers the files by walking it. That is upstream's own model (upstream
 * MachineLoader:56-79 plus ModDataHolder:71-134), and it is what makes a pack able to add a machine by
 * dropping a JSON in, and the in-game authoring tool (PLAN 641 step 4) able to write one out.
 *
 * <p>The {@code assets/} copies of these files are therefore not dead weight: they are the
 * <b>template</b> the first run extracts. A future round can register this loader as a
 * {@code PreparableReloadListener} through {@code AddReloadListenerEvent} to gain {@code /reload}
 * support without changing the format.
 *
 * <p>Loading is <b>non-fatal by design</b>: a malformed machine file must not stop the server from
 * starting. Every problem is collected and reported, and the affected definition is skipped.
 */
public final class MachineLoader {

    // ------------------------------------------------------------------
    // Discovery (PLAN 636, 641 step 3)
    // ------------------------------------------------------------------
    //
    // THE HARDCODED NAME LISTS ARE GONE. Until this change the loader held List.of("alloy_furnace", ...)
    // and read each name out of the jar, so a file that was not in the list was skipped with NO ERROR
    // AND NO LOG LINE -- which is how a sixth machine file was added, the suite passed 142/142, and the
    // machine did not exist (PLAN 636.1). UPSTREAM NEVER HAD THAT LIST: it extracted its shipped files
    // into config/modularmachinery/machinery/ and WALKED THE DIRECTORY, so a pack adds a machine by
    // dropping a file in (upstream MachineLoader:56-79, ModDataHolder:71-80).
    //
    // The discovery below is upstream's, read line by line. What it is NOT is a second code path: the
    // same Map it hands back is what the self-test's cross-check reads, so the check cannot disagree
    // with the loader about what was found.

    /**
     * Every machine and variable file under {@code directory}, recursively. Upstream MachineLoader:56-79.
     *
     * <p>Two kinds come out of one walk because they live in one tree: variables are accepted by the
     * {@code .var.json} suffix and everything else ending in {@code .json} is a machine. <b>The order
     * of the two tests is upstream's and is load-bearing</b>: {@code casings.var.json} also ends with
     * {@code .json}, so a machine-first check would try to parse it as a machine. Upstream's comment on
     * the same ordering is "I am *not* taking chances with this ordering".
     *
     * <p>Two details are upstream's, deliberately:
     * <ul>
     *   <li><b>Subdirectories are walked</b>, which is how {@code machinery/variables/casings.var.json}
     *       — where {@code copyDefaultMachinery} puts the variable files — is found at all.</li>
     *   <li><b>{@code listFiles()} is null-checked.</b> This is upstream's OWN other implementation
     *       (RecipeLoader:63-66 does check it) rather than MachineLoader:65, which dereferences it
     *       unguarded. Taking the checked form from the same upstream revision is the faithful reading
     *       of one codebase with two opinions; reproducing a NullPointerException on an unreadable
     *       directory is not a behaviour any pack depends on.</li>
     * </ul>
     *
     * <p><b>The result is sorted, and that is a deliberate addition.</b> {@code listFiles()} order is
     * filesystem-dependent, and upstream cannot be said to have an order at all: its callers feed these
     * lists through {@code parallelStream()} (upstream MachineLoader:84, RecipeLoader:86), so machine
     * and recipe registration order varies per run there. This port's ticker picks the FIRST runnable
     * recipe of a machine, so an unordered walk would make recipe selection depend on the filesystem.
     * Sorting by name is deterministic AND preserves today's per-machine recipe order exactly.
     *
     * @param directory the machinery directory; may be null, which yields empty lists
     */
    public static Map<FileType, List<File>> discoverDirectory(File directory) {
        Map<FileType, List<File>> candidates = new java.util.EnumMap<>(FileType.class);
        for (FileType type : FileType.values()) {
            candidates.put(type, new ArrayList<>());
        }
        if (directory == null || !directory.isDirectory()) {
            return candidates;
        }
        java.util.LinkedList<File> directories = new java.util.LinkedList<>();
        directories.add(directory);
        while (!directories.isEmpty()) {
            File dir = directories.remove(0);
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (file.isDirectory()) {
                    directories.addLast(file);
                } else if (FileType.VARIABLES.accepts(file.getName())) {
                    candidates.get(FileType.VARIABLES).add(file);
                } else if (FileType.MACHINE.accepts(file.getName())) {
                    candidates.get(FileType.MACHINE).add(file);
                }
            }
        }
        for (List<File> list : candidates.values()) {
            list.sort(java.util.Comparator.comparing(File::getName));
        }
        return candidates;
    }

    /** The two kinds of file the machinery directory holds. Upstream MachineLoader:142-154, verbatim. */
    public enum FileType {

        VARIABLES,
        MACHINE;

        public boolean accepts(String fileName) {
            return switch (this) {
                case VARIABLES -> fileName.endsWith(".var.json");
                default -> fileName.endsWith(".json");
            };
        }
    }

    private final Map<ResourceLocation, DynamicMachine> machines = new LinkedHashMap<>();
    private final MachineVariables variables = new MachineVariables();
    private final BlockStateResolver resolver = new BlockStateResolver();
    private final List<String> problems = new ArrayList<>();
    private final Map<String, JsonObject> definitions = new LinkedHashMap<>();
    private final Map<String, String> sourceByRegistryName = new LinkedHashMap<>();

    /** Accepted definitions, including their file keys used by blueprint associations. */
    public JsonObject snapshot() {
        JsonObject snapshot = new JsonObject();
        JsonObject variableJson = new JsonObject();
        variables.asMap().forEach((name, values) -> {
            JsonArray array = new JsonArray();
            values.forEach(array::add);
            variableJson.add(name, array);
        });
        JsonObject machineJson = new JsonObject();
        definitions.forEach((name, definition) -> {
            JsonObject effective = definition.deepCopy();
            DynamicMachine machine = machines.get(new ResourceLocation(Modularmachinery.MODID, name));
            // This default comes from the server config, which can differ on a remote client.
            effective.addProperty("color", Integer.toHexString(machine.getDefinedColor()));
            // MachineModifier.setMachinePrefix changes client presentation, so carry the resolved
            // value in the same server snapshot as the other script machine settings.
            effective.addProperty("prefix", machine.getPrefix());
            effective.addProperty("parallelizable", machine.isParallelizable());
            effective.addProperty("max-parallelism", machine.getMaxParallelism());
            effective.addProperty("parallelism", machine.getInternalParallelism());
            // Scripts can change factory settings after the source JSON was parsed.
            JsonObject factory = new JsonObject();
            factory.addProperty("maxThreads", machine.getMaxThreads());
            JsonObject presets = new JsonObject();
            machine.getCoreThreadPresets().forEach((thread, recipes) -> {
                JsonArray names = new JsonArray();
                recipes.forEach(names::add);
                presets.add(thread, names);
            });
            factory.add("coreThreads", presets);
            effective.add("factoryRuntime", factory);
            machineJson.add(name, effective);
        });
        snapshot.add("variables", variableJson);
        snapshot.add("machines", machineJson);
        return snapshot;
    }

    /** Builds a fresh client view without reading or writing the client's config directory. */
    public static MachineLoader fromSnapshot(JsonObject snapshot) {
        MachineLoader loader = new MachineLoader();
        loader.readVariableFile("server", snapshot.getAsJsonObject("variables"));
        snapshot.getAsJsonObject("machines").entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            DynamicMachine machine = loader.readMachineFile(entry.getKey(), json);
            if (machine == null) {
                throw new IllegalArgumentException("Invalid server machine: " + entry.getKey());
            }
            if (json.has("factoryRuntime")) {
                JsonObject factory = json.getAsJsonObject("factoryRuntime");
                machine.setMaxThreads(factory.get("maxThreads").getAsInt());
                factory.getAsJsonObject("coreThreads").entrySet().forEach(preset -> {
                    List<String> names = new ArrayList<>();
                    preset.getValue().getAsJsonArray().forEach(name -> names.add(name.getAsString()));
                    machine.addCoreThreadPreset(preset.getKey(), names);
                });
            }
        });
        return loader;
    }

    public Map<ResourceLocation, DynamicMachine> getMachines() {
        return machines;
    }

    public MachineVariables getVariables() {
        return variables;
    }

    public List<String> getProblems() {
        return problems;
    }

    /** @return the user-editable JSON file for a machine, or {@code null} when unavailable */
    public String getSourcePath(String registryName) {
        return registryName == null ? null : sourceByRegistryName.get(registryName);
    }

    /** @return the resolver, whose {@link BlockStateResolver#getUnresolved()} lists metadata the port
     *          could not translate — the R3 worklist */
    public BlockStateResolver getResolver() {
        return resolver;
    }

    /**
     * Reads every variable file under the machinery directory first, then every machine file.
     *
     * <p>Order matters: a machine's {@code elements} may name a variable, so the variable table must be
     * populated before definitions are validated against it.
     *
     * <p><b>The source is {@code config/modularmachinery/machinery/}, not the jar.</b> That is the change
     * of PLAN 636/641: the jar is now only what {@code ModDataHolder.copyDefaultMachinery} SEEDS the
     * directory from, once, on the first run. Everything after that — a pack's edit, a pack's addition,
     * and later the in-game authoring tool's output — is read from the directory.
     *
     * @param resourceManager unused, and this is upstream's shape rather than an oversight: upstream read
     *                        the same files from a {@code File} (upstream MachineRegistry:65) because
     *                        they are the mod's own config templates and not part of the server's resource
     *                        pack stack. The parameter is kept so that the reload-listener contract
     *                        ({@code ResourceManagerReloadListener}) and {@code MachineReloadListener}'s
     *                        call site stay unchanged.
     */
    public void load(ResourceManager resourceManager) {
        machines.clear();
        problems.clear();
        definitions.clear();
        sourceByRegistryName.clear();
        variables.asMap().clear();

        File directory = ModDataHolder.INSTANCE.getMachineryDirectory();
        Map<FileType, List<File>> candidates = discoverDirectory(directory);
        if (directory == null || !directory.isDirectory()) {
            // Reported, not silent. An empty load looks identical to "all the files were deleted" in
            // the log otherwise, and this is the state a broken copyDefaultMachinery produces.
            problems.add("machinery directory is not available: " + directory
                    + " (ModDataHolder.setup() creates it at common setup)");
        }

        loadVariables(candidates.get(FileType.VARIABLES));
        loadMachines(candidates.get(FileType.MACHINE));

        // Geometry invariants are checked here, not in a test source set: the build environment cannot
        // resolve a test dependency, and a wrong rotation is silent in game. See MatcherGeometryCheck.
        MatcherGeometryCheck.verifyAndReport();

        reportPatternResolvability();

        Modularmachinery.LOG.info("[MMCE] machine definitions loaded: {} (variables: {}) from {}",
                machines.size(), variables.size(), directory);
        Modularmachinery.LOG.info("[MMCE] discovered {} machine file(s) and {} variable file(s) under it",
                candidates.get(FileType.MACHINE).size(), candidates.get(FileType.VARIABLES).size());
        for (DynamicMachine machine : machines.values()) {
            Modularmachinery.LOG.info("[MMCE]   {} — {} part(s), bounds {}x{}x{}",
                    machine.getRegistryName(), machine.getPattern().size(),
                    machine.getPattern().getMaxX() - machine.getPattern().getMinX() + 1,
                    machine.getPattern().getMaxY() - machine.getPattern().getMinY() + 1,
                    machine.getPattern().getMaxZ() - machine.getPattern().getMinZ() + 1);
        }
        if (!problems.isEmpty()) {
            Modularmachinery.LOG.warn("[MMCE] {} problem(s) while loading definitions:", problems.size());
            problems.forEach(p -> Modularmachinery.LOG.warn("[MMCE]   {}", p));
        }
        if (resolver.hasUnresolved()) {
            resolver.reportUnresolved();
        }
    }

    // ------------------------------------------------------------------
    // Variables
    // ------------------------------------------------------------------

    private void loadVariables(List<File> files) {
        for (File file : files) {
            readJson(file).ifPresent(root -> readVariableFile(nameOf(file), root));
        }
    }

    private void readVariableFile(String name, JsonObject root) {
        for (Map.Entry<String, JsonElement> variable : root.entrySet()) {
            if (!variable.getValue().isJsonArray()) {
                problems.add(name + ": variable '" + variable.getKey() + "' is not an array");
                continue;
            }
            JsonArray array = variable.getValue().getAsJsonArray();
            List<String> elements = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                elements.add(element.getAsString());
            }
            variables.put(variable.getKey(), elements);
        }
    }

    // ------------------------------------------------------------------
    // Machines
    // ------------------------------------------------------------------

    private void loadMachines(List<File> files) {
        for (File file : files) {
            readJson(file).ifPresent(root -> {
                DynamicMachine machine = readMachineFile(nameOf(file), root);
                if (machine != null) sourceByRegistryName.put(machine.getRegistryName(), file.getPath());
            });
        }
    }

    /**
     * Parses <b>one</b> machine file without touching the loaded set — the same parse
     * {@link #loadMachines} runs, and the same name rule, by construction rather than by agreement.
     *
     * <p>Extracted for PLAN 641 step 5, whose check has to answer "does the file the authoring tool just
     * wrote come back as the machine that was authored?". That question needs the parse and the name and
     * nothing else: calling {@link #load} instead would make a check of the <i>writer</i> into a check of
     * the <i>directory</i>, and it would swap the loaded definitions out from under every other check in
     * the suite. Two paths that both go through {@code readMachineFile} can still disagree about a file,
     * but they cannot disagree about how a file is read, which is the thing a round-trip check is for.
     *
     * @param file a machine file, at any path
     * @return the definition the loader would build from it, or empty when it cannot be read or parsed —
     *         in which case the reason is in {@link #getProblems()} exactly as it would be after a load
     */
    public Optional<DynamicMachine> parseMachineFile(File file) {
        return readJson(file).map(root -> readMachineFile(nameOf(file), root));
    }

    /**
     * The definition's identity in this loader: the file's basename without {@code .json}.
     *
     * <p>Public so that a caller holding only the file can ask what the loader will call the machine —
     * the authoring check does, rather than re-deriving the name from the file name itself and risking a
     * second rule that happens to agree. Same string {@link #nameOf} produces.
     */
    public static String machineFileToken(File file) {
        return nameOf(file);
    }

    /**
     * Parses one machine definition and registers it under the <b>file's</b> basename.
     *
     * <p>{@code name} is that basename, and it is deliberately the key rather than the JSON's
     * {@code registryname}: the file is what a pack adds, renames and deletes, so keying the map by the
     * file is what makes "one file per machine" true. The {@code registryname} inside the file is the
     * machine's display identity and is what a controller stores.
     *
     * @return the definition, or {@code null} when the file does not describe one — in which case the
     *         reason has been added to {@link #getProblems()} and nothing is registered
     */
    private DynamicMachine readMachineFile(String name, JsonObject root) {
        try {
            String registryName = getString(root, "registryname", name);
            String localizedName = getString(root, "localizedname", registryName);

            MachinePattern pattern = new MachinePattern();
            if (root.has("parts") && root.get("parts").isJsonArray()) {
                Map<BlockPos, MachinePattern.Part> expanded = new LinkedHashMap<>();
                for (JsonElement partElement : root.getAsJsonArray("parts")) {
                    JsonObject part = partElement.getAsJsonObject();
                    String elements = readElements(part);
                    if (elements == null) {
                        problems.add(name + ": a part has no usable 'elements'");
                        continue;
                    }
                    expandPart(expanded, part, elements);

                    // Validate every reference now, so unresolvable content is reported at load rather
                    // than discovered the first time a player builds the machine.
                    for (String bound : variables.resolve(elements)) {
                        resolver.resolve(bound);
                    }
                }
                expanded.values().forEach(pattern::addPart);
            } else {
                problems.add(name + ": no 'parts' array");
            }

            ResourceLocation id = new ResourceLocation(Modularmachinery.MODID, name);
            DynamicMachine machine = new DynamicMachine(registryName, localizedName, pattern);

            // Source DynamicMachinePreDeserializer accepts an optional localization prefix.
            if (root.has("prefix") && root.get("prefix").isJsonPrimitive()) {
                machine.setPrefix(root.get("prefix").getAsString());
            }

            // requires-blueprint IS READ (PLAN section 292).
            //
            // The key has been in this port own machine JSON since the asset conversion and nothing ever read
            // it (section 263), while upstream AbstractMachine carried the same flag all along.
            //
            // ABSENT MEANS TRUE. Only a definition that says false opts into structure detection, and that
            // is what the shipped data does too: assembly_line declares it, the other three do not.
            if (root.has("requires-blueprint") && root.get("requires-blueprint").isJsonPrimitive()) {
                machine.setRequiresBlueprint(root.get("requires-blueprint").getAsBoolean());
            }

            // Client presentation option from the source definition.  Keeping it in the parsed
            // definition also makes server snapshots and optional client integrations agree.
            if (root.has("hide-components-when-formed")
                    && root.get("hide-components-when-formed").isJsonPrimitive()) {
                machine.setHideComponentsWhenFormed(
                        root.get("hide-components-when-formed").getAsBoolean());
            }

        // Explicit definition settings override server defaults; scripts can override these afterwards.
        if (root.has("parallelism") && root.get("parallelism").isJsonPrimitive()) {
            machine.setInternalParallelism(root.get("parallelism").getAsInt());
        }
        if (root.has("max-parallelism") && root.get("max-parallelism").isJsonPrimitive()) {
            machine.setMaxParallelism(root.get("max-parallelism").getAsInt());
        }
        if (root.has("parallelizable") && root.get("parallelizable").isJsonPrimitive()) {
            machine.setParallelizable(root.get("parallelizable").getAsBoolean());
        }

            // MODIFIERS ARE READ, NOT IGNORED (PLAN sections 224-225).
            //
            // The shipped machinery JSON declares a `modifiers` array and even describes its effect to the
            // player in a `description` string ("Replace block above controller with machine vent for double

            // failure-action IS READ (PLAN section 310).
            //
            // Upstream reads it in DynamicMachine and applies it in ActiveMachineRecipe.doFailureAction; the
            // port read neither, so its own shipped assembly_line.json asked for decrease and got still.
            //
            // AN UNKNOWN NAME FALLS BACK TO THE DEFAULT rather than failing the load, because that is what
            // upstream does. This is deliberately unlike requires-blueprint above, which upstream rejects --
            // the difference is upstream own, and copying it faithfully matters more than making the two
            // keys agree with each other.
            if (root.has("failure-action") && root.get("failure-action").isJsonPrimitive()) {
                String failureName = root.get("failure-action").getAsString();
                RecipeFailureActions parsedAction = RecipeFailureActions.byName(failureName);
                machine.setFailureAction(parsedAction);
                if (parsedAction == RecipeFailureActions.getDefaultAction()
                        && !RecipeFailureActions.getDefaultAction().getName().equals(failureName)) {
                    problems.add(name + ": unknown failure-action " + failureName + ", using "
                            + RecipeFailureActions.getDefaultAction().getName());
                }
            }

            // `color` IS READ (PLAN section 319).
            //
            // Upstream reads it as a HEX STRING and parses with Integer.parseInt(hexStr, 16), rejecting
            // anything that is not a hex number; the machine default is the CONFIG value, not zero
            // (AbstractMachine:19). The port read neither, so a machine declaring a colour came out the
            // config colour and nothing said the key had been ignored.
            //
            // A bad value is reported and the config colour is kept, rather than throwing: the port's
            // loader collects problems and keeps loading, which is its contract for every other key.
            if (root.has("color") && root.get("color").isJsonPrimitive()) {
                String colorStr = root.get("color").getAsString();
                try {
                    machine.setDefinedColor(Integer.parseInt(colorStr, 16));
                } catch (NumberFormatException notHex) {
                    problems.add(name + ": color " + colorStr + " is not a hexadecimal number");
                }
            }
            // MODIFIERS ARE READ, NOT IGNORED (PLAN sections 224-225).
            //
            // The shipped machinery JSON declares a `modifiers` array and even describes its effect to the player
            // in a `description` string ("Replace block above controller with machine vent for double item
            // output!"). Until now the loader never looked at the key, so the mod advertised a bonus it did not
            // grant -- the defect section 224 found by asking who calls getModifiers() and getting no answer.
            // Reading it here is the first half of the fix; the structure-time test that decides WHICH modifiers
            // are active is the second half and is recorded, not implied.
            if (root.has("modifiers") && root.get("modifiers").isJsonArray()) {
                List<hellfirepvp.modularmachinery.common.modifier.ModifierReplacement> parsed = new ArrayList<>();
                for (com.google.gson.JsonElement element : root.getAsJsonArray("modifiers")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    parsed.addAll(hellfirepvp.modularmachinery.common.modifier.ModifierReplacement
                            .deserializeAll(element.getAsJsonObject()));
                    if (parsed.size() > PatternCoordinates.MAX_POSITIONS)
                        throw new com.google.gson.JsonParseException("Too many modifier positions");
                }
                machine.setModifierReplacements(parsed);
                // AND A MODIFIER WHOSE TARGET NOTHING CAN APPLY IS REPORTED HERE (PLAN section 353.3's duration
                // gap, the "no longer silent" half). The reader above is total: it accepts any non-null `target`
                // string, and until now a target the port has no path for loaded, parsed, named a type that
                // cannot match, and scaled nothing -- with no warning anywhere. `modularmachinery:duration` was
                // the one that shipped content actually used; it now has an application path
                // (MachineTicker.effectiveRecipeDuration), and anything still unmatched is named here instead of
                // being discovered by reading the JSON by hand. This is the SAME problems list the loader already
                // logs as warnings (F234-F236) and exposes through getProblems() (F173-F175).
                String unmatched = hellfirepvp.modularmachinery.common.modifier.RecipeModifier
                        .unmatchedTarget(machine.getModifiers());
                if (unmatched != null) {
                    problems.add(name + ": a modifier targets '" + unmatched + "', which no application path in"
                            + " this port has; it will scale nothing (known targets: "
                            + hellfirepvp.modularmachinery.common.modifier.RecipeModifier.DURATION_TARGET + ", "
                            + String.join(", ",
                                    hellfirepvp.modularmachinery.common.modifier.RecipeModifier
                                            .REQUIREMENT_TARGETS) + ")");
                }
            }
            // dynamic-patterns IS READ (PLAN sections 321-325).
            //
            // The shipped assembly_line declares a repeating line of 5 to 15 sections, and upstream refuses to
            // form that machine when the line is wrong. The port read nothing, so it formed from the wall alone.
            readDynamicPatterns(name, root, machine);

            // smart-interface-types IS READ (PLAN 663, B2). UPSTREAM HAS NO JSON KEY FOR THIS AND THE
            // EVIDENCE IS ITS WRITER LIST: DynamicMachine.smartInterfaces is written only by
            // MachineBuilder.addSmartInterfaceType (U337-U342) and MachineModifier.addSmartInterfaceType
            // (U27-U40), and cleared only by ModIntegrationCrafttweaker U84 -- all three CraftTweaker. This
            // port has no script-facing machine builder (CraftTweakerIntegration's own javadoc), so the
            // machine definition is the only place a pack can declare these, exactly as §592-§598 concluded
            // for parallelism and §662 for factory capacity.
            //
            // WITHOUT AT LEAST ONE DECLARED TYPE NOTHING IS EVER PUBLISHED: the binding walk that hands an
            // interface its type and default value is gated on this list being non-empty (upstream
            // checkAndAddSmartInterface's first line, U903), so an empty list is upstream's own "this
            // machine has no smart interfaces" and not a silent failure.
            readSmartInterfaceTypes(name, root, machine);

            machines.put(id, machine);
            definitions.put(name, root.deepCopy());
            return machine;
        } catch (Exception e) {
            problems.add(name + ": " + e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Resource helpers
    // ------------------------------------------------------------------

    /**
     * Reads one discovered definition file from disk.
     *
     * <p><b>Why a file and not a resource path.</b> This used to read
     * {@code /assets/modularmachinery/<dir>/<name>.json} through the classloader, with a name taken from
     * a hardcoded list. Both halves of that are gone: {@link #discoverDirectory} says WHAT the files are,
     * and this reads them from where a pack can see and edit them. A file that cannot be read is a
     * reported problem with its own path attached, which is the difference between "this machine is
     * missing" and "this directory cannot be read".
     *
     * <p>The alternative — reading the extracted file and falling back to the jar when it is absent —
     * was considered and rejected: it would make "the extraction did not run" indistinguishable from
     * "the player deleted this machine on purpose", and the second one is a thing packs do.
     */
    private Optional<JsonObject> readJson(File file) {
        try (BufferedReader reader = java.nio.file.Files.newBufferedReader(
                file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            problems.add(file.getPath() + ": " + e);
            return Optional.empty();
        }
    }

    /**
     * The name a discovered file is known by: its basename without {@code .json}.
     *
     * <p>{@code alloy_furnace.json} is the machine {@code alloy_furnace}, which is also the value written
     * into the definition's {@link ResourceLocation}. Flattened, because {@code ModDataHolder.copy}
     * flattens the shipped tree into the config directory, and because upstream keys a machine by the
     * {@code registryname} inside the file and not by where the file happens to sit.
     */
    private static String nameOf(File file) {
        String name = file.getName();
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    /**
     * Reads a part's {@code elements}, which the shipped content writes in <b>two forms</b>.
     *
     * <p>A part may carry a single name:
     * <pre>"elements": "minecraft:lava"</pre>
     * or an <b>array</b> of acceptable names:
     * <pre>"elements": [ "modularmachinery:blockinputbus" ]</pre>
     *
     * <p>The array form is the same mechanism as a named variable — "any of these satisfies this
     * position" — just written inline instead of through {@code default_variables}. The loader
     * originally accepted only the string form, which rejected every part using the array with
     * {@code IllegalStateException: Array must have size 1, but has size 2} from Gson's
     * {@code getAsString()} on a single-element array. That is what made {@code assembly_line} and
     * {@code alloy_furnace} fail to load.
     *
     * <p>The array is flattened to a <b>comma-separated</b> string so the rest of the pipeline keeps a
     * single representation; {@link MachineVariables#resolve} splits it back into alternatives. Storing
     * the raw list here instead would mean two code paths everywhere downstream.
     *
     * @return the element name(s), or {@code null} when the field is absent or malformed
     */
    private static String readElements(JsonObject part) {
        if (!part.has("elements") || part.get("elements").isJsonNull()) {
            return null;
        }
        JsonElement elements = part.get("elements");
        if (elements.isJsonPrimitive()) {
            return elements.getAsString();
        }
        if (elements.isJsonArray()) {
            JsonArray array = elements.getAsJsonArray();
            if (array.isEmpty()) {
                return null;
            }
            List<String> names = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    names.add(element.getAsString());
                }
            }
            return names.isEmpty() ? null : String.join(",", names);
        }
        return null;
    }

    /**
     * Reads the {@code smart-interface-types} array (PLAN 663, B2).
     *
     * <p>An ARRAY of type objects, one per declared type. The keys are the FIELD NAMES of upstream's
     * {@code common.util.SmartInterfaceType}, in the kebab-case this loader already uses for the other keys
     * it reads ({@code requires-blueprint}, {@code failure-action}, {@code max-parallelism}):</p>
     *
     * <pre>
     *   type                required, a primitive — the name a recipe's interface requirement names
     *   default-value       optional, a number — absent means 0, which is upstream's own field initialiser
     *   priority            optional, an integer — higher wins; see SmartInterfaceType#compareTo
     *   header-info         optional, a string — GUI line above the value
     *   value-info          optional, a string — GUI format for the value
     *   footer-info         optional, a string — GUI line below the value
     *   not-equal-message   optional, a string — the failure line when a value is out of range
     *   jei-tooltip         optional, a string — the JEI line's format
     *   jei-tooltip-args    optional, an integer — how many arguments that format takes (upstream: 2)
     * </pre>
     *
     * <p><b>Upstream rejects nothing here because upstream reads nothing here</b> (this key exists only in this
     * port — see the call site comment). So every rule above is a choice, and the two that matter are: a
     * MISSING {@code type} is reported and that entry is skipped, because a nameless type can never be looked
     * up and would silently never publish; and a duplicate name REPLACES the earlier entry, which is what
     * {@code addSmartInterfaceType} does on the machine. A malformed entry does NOT reject the machine: this
     * loader's contract for every other key is to collect a problem and keep loading.</p>
     */
    private void readSmartInterfaceTypes(String name, JsonObject root, DynamicMachine machine) {
        if (!root.has("smart-interface-types") || !root.get("smart-interface-types").isJsonArray()) {
            return;     // absent is the normal case, and it means "this machine has no smart interfaces"
        }
        for (JsonElement element : root.getAsJsonArray("smart-interface-types")) {
            if (!element.isJsonObject()) {
                problems.add(name + ": a smart-interface-type is not an object");
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            String typeName = getString(json, "type", null);
            if (typeName == null || typeName.isEmpty()) {
                problems.add(name + ": a smart-interface-type has no name");
                continue;
            }
            float defaultValue = json.has("default-value") && json.get("default-value").isJsonPrimitive()
                    ? json.get("default-value").getAsFloat()
                    : 0F;
            hellfirepvp.modularmachinery.common.util.SmartInterfaceType type =
                    new hellfirepvp.modularmachinery.common.util.SmartInterfaceType(typeName, defaultValue);
            if (json.has("priority") && json.get("priority").isJsonPrimitive()) {
                type.setPriority(json.get("priority").getAsInt());
            }
            // The four texts keep upstream's "empty means use the default" rule, so an empty string in the
            // JSON is the same as an absent key -- which is what a pack author writing "" expects.
            type.setHeaderInfo(getString(json, "header-info", ""));
            type.setValueInfo(getString(json, "value-info", ""));
            type.setFooterInfo(getString(json, "footer-info", ""));
            type.setNotEqualMessage(getString(json, "not-equal-message", ""));
            String jeiTooltip = getString(json, "jei-tooltip", "");
            int argsCount = json.has("jei-tooltip-args") && json.get("jei-tooltip-args").isJsonPrimitive()
                    ? json.get("jei-tooltip-args").getAsInt()
                    : 2;
            type.setJeiTooltip(jeiTooltip, argsCount);

            machine.addSmartInterfaceType(type);
        }
    }

    /**
     * Reads the `dynamic-patterns` array (PLAN sections 321-325).
     *
     * <p>An ARRAY of pattern objects, one per repeating structure segment. Every rule here is upstream's, from
     * DynamicMachine:329-490, and the required/optional split is its own:
     *
     * <pre>
     *   name                          required, a primitive
     *   faces                         required, an array of direction names
     *   maxSize                       required, a number
     *   minSize                       optional, a number if present
     *   parts                         required, an array of parts
     *   parts-end                     optional, the same shape as parts
     *   structure-size-offset-start   optional, an OBJECT
     *   structure-size-offset         optional, an OBJECT
     * </pre>
     *
     * <p>Upstream THROWS on every one of those failures, rejecting the whole machine. The port collects a problem
     * and CONTINUES, which is its contract for every other key in this loader -- but the difference is recorded here
     * rather than glossed, because a rejected machine and a machine with a missing dynamic pattern behave
     * differently: the first does not exist, the second forms without its line.
     *
     * <p>The template is built as a MachinePattern, so its element strings go through the same reader, the same
     * variable resolution and the same resolver as the machine's own parts. A second representation would be a
     * second set of rules for what an element string means.
     */
    private void readDynamicPatterns(String name, JsonObject root, DynamicMachine machine) {
        if (!root.has("dynamic-patterns") || !root.get("dynamic-patterns").isJsonArray()) {
            return;     // absent is the normal case: one shipped machine of five declares one
        }
        JsonArray patterns = root.getAsJsonArray("dynamic-patterns");
        if (patterns.size() == 0) {
            problems.add(name + ": dynamic-patterns is empty");
            return;
        }
        for (JsonElement element : patterns) {
            if (!element.isJsonObject()) {
                problems.add(name + ": a dynamic pattern is not an object");
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            String patternName = getString(json, "name", null);
            if (patternName == null) {
                problems.add(name + ": a dynamic pattern has no name");
                continue;
            }
            DynamicPattern pattern = new DynamicPattern(patternName);

            if (!json.has("faces") || !json.get("faces").isJsonArray()) {
                problems.add(name + ": dynamic pattern " + patternName + " has no faces");
                continue;
            }
            for (JsonElement faceElement : json.getAsJsonArray("faces")) {
                Direction face = Direction.byName(faceElement.getAsString().toLowerCase(java.util.Locale.ROOT));
                if (face == null) {
                    problems.add(name + ": dynamic pattern " + patternName + " has an unknown face "
                            + faceElement.getAsString());
                } else {
                    pattern.addFace(face);
                }
            }

            if (!json.has("maxSize") || !json.get("maxSize").isJsonPrimitive()) {
                problems.add(name + ": dynamic pattern " + patternName + " has no maxSize");
                continue;
            }
            pattern.setMaxSize(json.get("maxSize").getAsInt());
            if (json.has("minSize") && json.get("minSize").isJsonPrimitive()) {
                pattern.setMinSize(json.get("minSize").getAsInt());
            }
            // Upstream performs this cross-check inside its maxSize reader, against the minSize parsed so far.
            if (pattern.getMinSize() > pattern.getMaxSize()) {
                problems.add(name + ": dynamic pattern " + patternName + " has minSize above maxSize");
                continue;
            }

            if (!readDynamicParts(name, json, "parts", pattern.getPattern())) {
                continue;
            }
            if (json.has("parts-end")) {
                MachinePattern end = new MachinePattern();
                if (!readDynamicParts(name, json, "parts-end", end)) {
                    continue;
                }
                pattern.setPatternEnd(end);
            }

            if (json.has("structure-size-offset-start")) {
                BlockPos start = readOffset(name, json, "structure-size-offset-start");
                if (start != null) {
                    pattern.setStructureSizeOffsetStart(start);
                }
            }
            if (json.has("structure-size-offset")) {
                BlockPos offset = readOffset(name, json, "structure-size-offset");
                if (offset != null) {
                    pattern.setStructureSizeOffset(offset);
                }
            }

            machine.addDynamicPattern(pattern);
        }
        Modularmachinery.LOG.info("[MMCE]   {} dynamic pattern(s) on {}", machine.getDynamicPatterns().size(), name);
    }

    /** Reads one of the two part arrays, with the same element handling the machine's own parts use. */
    private boolean readDynamicParts(String name, JsonObject json, String key, MachinePattern target) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            problems.add(name + ": dynamic pattern has no usable " + key);
            return false;
        }
        Map<BlockPos, MachinePattern.Part> expanded = new LinkedHashMap<>();
        for (JsonElement partElement : json.getAsJsonArray(key)) {
            JsonObject part = partElement.getAsJsonObject();
            String elements = readElements(part);
            if (elements == null) {
                problems.add(name + ": a dynamic part has no usable elements");
                return false;
            }
            expandPart(expanded, part, elements);
            for (String bound : variables.resolve(elements)) {
                resolver.resolve(bound);
            }
        }
        expanded.values().forEach(target::addPart);
        return true;
    }

    /** Last declaration at an offset wins, as in upstream's position-indexed BlockArray. */
    private static void expandPart(Map<BlockPos, MachinePattern.Part> target, JsonObject part, String elements) {
        String selector = SelectorTags.read(part);
        StructureNbt structureNbt = StructureNbt.read(part);
        for (BlockPos pos : PatternCoordinates.expand(part)) {
            target.put(pos, new MachinePattern.Part(pos.getX(), pos.getY(), pos.getZ(), elements, selector, structureNbt));
            if (target.size() > PatternCoordinates.MAX_POSITIONS)
                throw new com.google.gson.JsonParseException("Pattern exceeds " + PatternCoordinates.MAX_POSITIONS + " positions");
        }
    }

    /** Reads one of the two offset objects, or null when it cannot be read. */
    private BlockPos readOffset(String name, JsonObject json, String key) {
        if (!json.get(key).isJsonObject()) {
            problems.add(name + ": " + key + " is not an object");
            return null;
        }
        JsonObject offset = json.getAsJsonObject(key);
        return new BlockPos(getInt(offset, "x"), getInt(offset, "y"), getInt(offset, "z"));
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject object, String key) {
        return PatternCoordinates.scalar(object, key);
    }

    /** Package-private for the reload-listener work in a later round. */
    Optional<DynamicMachine> find(ResourceLocation id) {
        return Optional.ofNullable(machines.get(id));
    }

    /**
     * Verifies that every part of every loaded pattern can be resolved to at least one block state.
     *
     * <p>This is the strongest check available without a live world, and it answers the question that
     * actually matters before a player ever places a block: <b>could this machine ever form?</b> A
     * pattern with even one unresolvable part can never match — {@link StructureMatcher} reports such a
     * part as a mismatch by design rather than accepting anything — so the machine would be silently
     * unbuildable, discoverable only by placing every block correctly and watching it not form.
     *
     * <p>Reported per machine so the number is actionable: "3 of 26 parts unresolvable" points straight
     * at the definitions needing attention.
     */
    private void reportPatternResolvability() {
        for (DynamicMachine machine : machines.values()) {
            int total = machine.getPattern().size();
            int resolvable = 0;
            java.util.List<String> bad = new java.util.ArrayList<>();

            for (MachinePattern.Part part : machine.getPattern().getParts()) {
                boolean anyResolved = false;
                for (String bound : variables.resolve(part.elements())) {
                    if (!resolver.resolveAccepted(bound).isEmpty()) {
                        anyResolved = true;
                    }
                }
                if (anyResolved) {
                    resolvable++;
                } else if (bad.size() < 5) {
                    bad.add(part.elements());
                }
            }

            if (resolvable == total) {
                Modularmachinery.LOG.info("[MMCE]   {} — all {} part(s) resolvable, machine is buildable",
                        machine.getRegistryName(), total);
            } else {
                // Not an error in the loader's own terms: a part may legitimately reference a mod that
                // is simply not installed. It is a warning because the machine cannot form until it is
                // addressed, whichever of the two causes applies.
                Modularmachinery.LOG.warn("[MMCE]   {} — only {}/{} part(s) resolvable; "
                                + "this machine cannot form until fixed (e.g. {})",
                        machine.getRegistryName(), resolvable, total, String.join(", ", bad));
            }
        }
    }

    /**
     * Looks a definition up by the name stored on a controller.
     *
     * <p>Matching is by the JSON's {@code registryname} (e.g. {@code transformer}) rather than by file
     * name, because that is the value upstream wrote onto the tile and the value a pack author sees in
     * the machine's own config. A file named {@code power_transformer.json} declaring
     * {@code "registryname": "transformer"} must be found as {@code transformer}.
     *
     * @return the definition, or {@code null} when no loaded machine carries that name
     */
    /**
     * Places the blocks a machine is missing, and leaves every block that already matches alone.
     *
     * <h2>Why "missing only" and not "set every part"</h2>
     *
     * <p>A builder that writes every position would overwrite whatever the player put there -- including
     * <p>the parts they deliberately placed and the blocks they had not intended to give up. Filling only the
     * mismatched positions means the button is IDEMPOTENT: pressing it twice is the same as pressing it once,
     * and it can never destroy work. That is the property a one-click tool needs, and it is why the client
     * chose this behaviour over an overwrite.
     *
     * <h2>The part resolution is the loader's own, not a second implementation</h2>
     *
     * <p>{@code variables.resolve} and {@code resolver.resolveAccepted} are the same two calls the startup
     * buildability report makes. Writing a second expansion here would let the builder and the matcher
     * disagree about what a part means -- and the failure mode would be a machine that the builder says is
     * complete and the matcher refuses to form.
     *
     * @return how many positions were placed, or -1 when the machine has no pattern
     */
    public int fillMissing(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos controllerPos,
                           net.minecraft.core.Direction facing, DynamicMachine machine) {
        if (machine == null) {
            return 0;
        }
        int placed = 0;
        for (MachinePattern.Part part : machine.getPattern().getParts()) {
            net.minecraft.core.BlockPos world = StructureMatcher.toWorldOffset(
                    controllerPos, facing, part.x(), part.y(), part.z());

            // Already one of the accepted states? Then this position is the player's, and it stays.
            net.minecraft.world.level.block.state.BlockState current = level.getBlockState(world);
            boolean satisfied = StructureMatcher.matchesAny(current,
                    StructureMatcher.acceptedStates(part, variables, resolver, machine.getModifierReplacements()), facing)
                    && StructureMatcher.matchesPartNbt(level, world, part);
            if (satisfied) {
                continue;
            }

            // Otherwise place the first state the part resolves to. A part that resolves to NOTHING is
            // skipped rather than reported: the loader has already logged that machine as unbuildable, and
            // repeating it here would print the same complaint on every click.
            for (String bound : variables.resolve(part.elements())) {
                java.util.Collection<net.minecraft.world.level.block.state.BlockState> accepted =
                        resolver.resolveAccepted(bound);
                if (!accepted.isEmpty()) {
                    // THE DEFAULT VARIANT, NOT "THE FIRST ACCEPTED ONE" (PLAN section 269).
                    //
                    // A bare element name (no @meta) resolves to EVERY state of the block -- that is what
                    // makes one definition accept every tier of a hatch. But getPossibleStates() has no
                    // meaningful order, so taking the first would place an arbitrary tier -- a ludicrous bus
                    // where a normal one was expected, decided by iteration order.
                    //
                    // The default state is the tier a player would place by hand, so it is the right choice
                    // when the definition does not care which tier it gets.
                    net.minecraft.world.level.block.state.BlockState chosen = accepted.iterator().next();
                    for (net.minecraft.world.level.block.state.BlockState candidate : accepted) {
                        if (candidate.equals(candidate.getBlock().defaultBlockState())) {
                            chosen = candidate;
                            break;
                        }
                    }
                    chosen = StructureMatcher.rotateState(chosen, facing);
                    level.setBlock(world, chosen, net.minecraft.world.level.block.Block.UPDATE_ALL);
                    // preview-nbt is also the concrete data used by the authoring builder.  Apply it
                    // after placement so a newly-created block entity receives the configured contents.
                    if (part.previewNbt() != null) {
                        net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(world);
                        if (entity != null) {
                            entity.load(part.previewNbt());
                            entity.setChanged();
                        }
                    }
                    placed++;
                    break;
                }
            }
        }
        return placed;
    }

    /**
     * Looks a definition up by the name stored on a controller -- the JSON's `registryname`, verbatim.
     *
     * <p>The comparison is against the BARE name, not a resource id: a machine whose JSON says
     * {@code "registryname": "transformer"} is found as {@code transformer}, never as
     * {@code modularmachinery:transformer}. Callers holding a {@code ResourceLocation} must pass its
     * {@code getPath()} -- which is what {@code ItemBlueprint} does.
     *
     * @param name the definition's name, or null
     * @return the definition, or null when the name is absent or unknown
     */
    public DynamicMachine findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (DynamicMachine machine : machines.values()) {
            if (name.equals(machine.getRegistryName())) {
                return machine;
            }
        }
        return null;
    }

    /** @return the loaded definitions, keyed by their resource id */
    public java.util.Collection<DynamicMachine> all() {
        return java.util.Collections.unmodifiableCollection(machines.values());
    }
}
