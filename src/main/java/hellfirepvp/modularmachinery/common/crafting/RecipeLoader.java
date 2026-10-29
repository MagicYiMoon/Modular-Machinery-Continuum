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
package hellfirepvp.modularmachinery.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.requirement.ItemMetadataShim;
import hellfirepvp.modularmachinery.common.crafting.requirement.ItemTagShim;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
























 
public final class RecipeLoader {

    // ------------------------------------------------------------------
     
    // ------------------------------------------------------------------
    //
    // THE HARDCODED RECIPE-FILE LIST IS GONE, for the reason MachineLoader records in full: a file that
    // was not in the list was skipped with no error and no log line. Upstream RecipeLoader:54-80 walks
    // config/modularmachinery/recipes/ instead, which is the directory ModDataHolder seeds from the jar
     

    /**
     * Every {@code .json} under {@code directory}, recursively. Upstream RecipeLoader:54-80, read line by line.
     *
     * <p><b>Upstream splits its finds into two kinds and this does not, and the difference is upstream's own
     * absent half.</b> Upstream's {@code FileType.ADAPTER} accepts {@code *.adapter.json} and {@code RECIPE}
     * accepts the rest, because upstream routes the two through two different loaders
     * ({@code loadRecipes} and {@code loadAdapterRecipes}, upstream:82-136). <b>This port has only one
     * reader</b>: it parses an adapter template through the same {@link #parse} as a recipe, branching on the
     * file's own {@code "adapter"} key (RecipeLoader:180-186, and {@code MachineTicker:348} reports the result
     * as an "unexpanded adapter recipe"). An ADAPTER bucket here would be a second list with nothing to send
     * its contents to, so the file-type question stays where the port already answers it. The shipped adapter
     * file therefore still loads, exactly as it does today.
     *
     * <p>Subdirectories are walked (upstream's BFS), and the result is sorted by name for the reason
     * MachineLoader.discoverDirectory records: {@code listFiles()} order is filesystem-dependent and upstream's
     * parallel streams give no order at all, while this port's ticker picks the FIRST runnable recipe of a
     * machine.
     *
     * @param directory the recipe directory; may be null, which yields an empty list
     */
    public static List<File> discoverDirectory(File directory) {
        List<File> candidates = new ArrayList<>();
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
                } else if (file.getName().endsWith(".json")) {
                    candidates.add(file);
                }
            }
        }
        candidates.sort(java.util.Comparator.comparing(File::getName));
        return candidates;
    }

    /** {@code modularmachinery:energy} */
    private static final String TYPE_ENERGY = Modularmachinery.MODID + ":energy";
    /** {@code modularmachinery:item} */
    private static final String TYPE_ITEM = Modularmachinery.MODID + ":item";

    /** Fluid held in a hatch's tank. Distinct from the fluid BLOCK declared inside a structure pattern. */
    private static final String TYPE_FLUID = Modularmachinery.MODID + ":fluid";

    /**
     * Every loaded recipe by its {@code registryName} -- upstream's {@code RECIPE_REGISTRY} and
     * {@code RecipeRegistry.getRecipe} (U48, U69-U72).
     *
     * <p><b>It is a {@link LinkedHashMap} for TWO reasons, and the second one is why it is not a HashMap.</b>
     * It preserves the order the files were walked in, which makes a diagnostic listing stable between runs;
     * and it makes {@link #getRecipe} and the duplicate check <b>the same question asked once</b> --
     * {@code containsKey} IS the duplicate test, so the lookup this class was missing and the detection it
     * was missing cannot disagree about who owns a name.</p>
     */
    private final Map<String, MachineRecipe> byName = new LinkedHashMap<>();

    private final Map<String, List<MachineRecipe>> byMachine = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> unknownRequirementTypes = new ArrayList<>();
    private int filesRead;

    /**
     * One entry per recipe name that more than one loaded recipe claimed.
     *
     * <p>Upstream's collision report (U136-U144) and this one carry the same four facts: the name, the file
     * that was DISCARDED, the file that KEPT it, and -- upstream's own wording -- the fact that there were
     * two offending files. Kept as structured entries rather than pre-formatted strings so the self-test can
     * assert the FILES and not only that some line mentioned the name.</p>
     */
    private final List<Collision> collisions = new ArrayList<>();

    /**
     * Which file each recipe name came from, so a collision can name both files.
     *
     * <p>Upstream keeps the path on the recipe itself ({@code MachineRecipe.recipeFilePath}, printed at
     * U140). This port's {@link MachineRecipe} deliberately does not carry a path -- it is a value the
     * registry works with, not a file record -- so the path lives here, beside the name, which is the only
     * thing the collision report needs it for.</p>
     */
    private final Map<String, String> sourceByRecipeName = new LinkedHashMap<>();
    /** True only for recipes authored directly in a JSON file; generated/script recipes are read-only. */
    private final Map<String, Boolean> sourceEditableByRecipeName = new LinkedHashMap<>();

    /** One discarded duplicate. */
    private record Collision(String registryName, String discardedFile, String survivingFile) {
    }

    /**
     * What the last load's adapter expansions did, per adapter.
     *
     * <p>Kept rather than logged and forgotten because the interesting answer is a NUMBER: "the furnace
     * adapter converted 42 of 61 vanilla smelting recipes, 19 of them because their input is a tag" is a
     * statement about how much of the vanilla recipe set this machine can actually run, and it is the only
     * thing that would ever show a target mod's recipe set silently shrinking.</p>
     */
    private final Map<ResourceLocation, hellfirepvp.modularmachinery.common.crafting.adapter
            .AdapterMinecraftFurnace.Probe> adapterProbes = new LinkedHashMap<>();

    /**
     * Adapter templates that produced NOTHING, each with its reason and its file.
     *
     * <p>This is the list the contract turns on: a recipe carrying an {@code "adapter"} name that cannot be
     * resolved must be REPORTED at load time, never silently absent. It is a separate list from
     * {@link #problems} because the two need different fixes -- a problem is a malformed file, this is a
     * generator that could not run -- and because merging them would make "the shipped content has no
     * problems" stop meaning what every existing check reads it as.</p>
     */
    private final Map<String, java.util.List<String>> templateReports = new LinkedHashMap<>();
    private final Map<String, Integer> templateImported = new LinkedHashMap<>();
    private final List<String> notExpandedAdapters = new ArrayList<>();

    /** @return the templates that named an adapter that produced no recipes, with reasons; empty when none */
    public List<String> getNotExpandedAdapters() {
        return List.copyOf(notExpandedAdapters);
    }

    /** @return what the last load's adapters generated, per adapter, for the load report and the self-test */
    public Map<ResourceLocation, hellfirepvp.modularmachinery.common.crafting.adapter
            .AdapterMinecraftFurnace.Probe> getAdapterProbes() {
        return Map.copyOf(adapterProbes);
    }

    public Map<String, List<MachineRecipe>> getByMachine() {
        return byMachine;
    }

    private final Map<Requirement, JsonObject> requirementSources = new java.util.IdentityHashMap<>();
    private final Map<String, JsonObject> recipeSourceDocuments = new LinkedHashMap<>();

    /** Exact loaded document for isolated editor operations; generated recipes remain read-only. */
    public JsonObject getRecipeSource(String recipeId) {
        var source = isSourceEditable(recipeId) ? recipeSourceDocuments.get(getSourcePath(recipeId)) : null;
        return source == null ? null : source.deepCopy();
    }

    /** Defensive source copy used to verify an editor index still identifies the exact loaded requirement. */
    public JsonObject getRequirementSource(Requirement requirement) {
        var source = requirementSources.get(requirement);
        return source == null ? null : source.deepCopy();
    }

    /** Sends resolved recipes, so the remote client never needs a local server to expand adapters. */
    public JsonArray snapshot() {
        JsonArray result = new JsonArray();
        for (MachineRecipe recipe : byName.values()) {
            result.add(RecipeSnapshotCodec.encode(recipe, requirementSources));
        }
        return result;
    }

    public static RecipeLoader fromSnapshot(JsonArray snapshot, MachineLoader machines) {
        RecipeLoader loader = new RecipeLoader();
        for (JsonElement entry : snapshot) {
            MachineRecipe recipe = RecipeSnapshotCodec.decode(entry.getAsJsonObject());
            if (machines.findByName(recipe.machine()) == null) {
                throw new IllegalArgumentException("Server recipe references unknown machine: " + recipe.machine());
            }
            if (!loader.accept(recipe, "server")) {
                throw new IllegalArgumentException("Duplicate server recipe: " + recipe.registryName());
            }
        }
        loader.sortRecipesByPriority();
        return loader;
    }

    public List<String> getProblems() {
        return problems;
    }

    /**
     * The GLOBAL by-name lookup -- upstream {@code RecipeRegistry.getRecipe(ResourceLocation)} (U69-U72).
     *
     * <p><b>Rule A0: the parameter is the bare {@code registryName} STRING, not a {@code ResourceLocation}.</b>
     * Upstream keys its registry on {@code new ResourceLocation(ModularMachinery.MODID, registryName)}, which
     * makes every one of its lookups namespace-qualified by construction. This port's {@link MachineRecipe}
     * holds the bare string (see its own javadoc), and its controller stores a bare name for the recipe it is
     * running ({@code TileController.getRecipeName}), so a lookup that wanted a {@code ResourceLocation}
     * would need a namespace invented for it -- and the two spellings would then be able to disagree, which is
     * the failure mode that makes a lookup worse than none.</p>
     *
     * @param registryName the name as the recipe declares it, e.g. {@code alloy_furnace_diamond}
     * @return the recipe registered under that name, or {@code null} when there is none
     */
    @org.jetbrains.annotations.Nullable
    public MachineRecipe getRecipe(String registryName) {
        return registryName == null ? null : byName.get(registryName);
    }

    /** @return the source file of a loaded recipe, or {@code null} for a generated/client-only entry */
    public String getSourcePath(String registryName) {
        return registryName == null ? null : sourceByRecipeName.get(registryName);
    }

    /** @return whether the source path is a user-editable JSON recipe rather than an adapter/script result */
    public boolean isSourceEditable(String registryName) {
        return registryName != null && Boolean.TRUE.equals(sourceEditableByRecipeName.get(registryName));
    }

    /** @return every loaded recipe name, in load order; used by the command and by the self-test */
    public List<String> getRecipeNames() {
        return List.copyOf(byName.keySet());
    }

    /**
     * @return the names more than one recipe file claimed, each with the file that was DISCARDED and the one
     *         that kept the name -- an empty list when the shipped content has no duplicate
     */
    public List<String> getCollisions() {
        List<String> out = new ArrayList<>(collisions.size());
        for (Collision collision : collisions) {
            out.add(collision.registryName() + ": discarded '" + collision.discardedFile()
                    + "', kept '" + collision.survivingFile() + "'");
        }
        return out;
    }

    /**
     * Accepts one parsed recipe into the registry, or rejects it as a duplicate.
     *
     * <p><b>UPSTREAM'S RULE, AND WHY IT IS THE LATER ONE THAT GOES.</b> {@code RecipeRegistry
     * .loadAndValidateRecipes} (U136-U144) tests {@code sharedLoadRegistry.containsKey(mr.getRegistryName())}
     * and, when it is already there, logs "MachineRecipe with registryName &lt;name&gt; already exists!" plus
     * "Offending files: '&lt;new&gt;' and '&lt;existing&gt;'!" and <b>continues to the next recipe</b> --
     * i.e. it keeps the FIRST and drops the SECOND. The port kept both silently, which made
     * {@code MachineTicker.findByName} return whichever of the two happened to come first in the machine's
     * candidate list, so which recipe ran depended on file order and on nothing anything reported.</p>
     *
     * <p>The first-wins rule is not arbitrary: the walk is name-sorted ({@link #discoverDirectory}), so
     * "first" is the alphabetically first FILE, which is at least deterministic and repackable.</p>
     *
     * @return true when the recipe was registered, false when it was discarded as a duplicate
     */
    private boolean accept(MachineRecipe recipe, String sourcePath) {
        return accept(recipe, sourcePath, true);
    }

    private boolean accept(MachineRecipe recipe, String sourcePath, boolean sourceEditable) {
        String name = recipe.registryName();
        MachineRecipe existing = byName.get(name);
        if (existing != null) {
            String surviving = sourceByRecipeName.get(name);
            collisions.add(new Collision(name, sourcePath, surviving));
            Modularmachinery.LOG.warn("[MMCE] MachineRecipe with registryName {} already exists!", name);
            Modularmachinery.LOG.warn("[MMCE] Offending files: '{}' and '{}' !", sourcePath, surviving);
            Modularmachinery.LOG.warn("[MMCE]   the file '%s' is DISCARDED; '%s' keeps the name",
                    sourcePath, surviving);
            return false;
        }
        byName.put(name, recipe);
        sourceByRecipeName.put(name, sourcePath);
        sourceEditableByRecipeName.put(name, sourceEditable);
        byMachine.computeIfAbsent(recipe.machine(), k -> new ArrayList<>()).add(recipe);
        return true;
    }

    










 
    public int filesRead() {
        return filesRead;
    }

    /** @return requirement type names this loader does not implement, deduplicated */
    public List<String> getUnknownRequirementTypes() {
        return unknownRequirementTypes;
    }

    /**
     * Reads every recipe file in {@code config/modularmachinery/recipes/} and validates it against the
     * loaded machine definitions.
     *
     * @param machineLoader supplies the definitions a recipe may refer to; a recipe naming a machine
     *                      that did not load is rejected, because it could never run
     */
    public void load(MachineLoader machineLoader) {
        byName.clear();
        requirementSources.clear();
        byMachine.clear();
        problems.clear();
        unknownRequirementTypes.clear();
        collisions.clear();
        sourceByRecipeName.clear();
        sourceEditableByRecipeName.clear();
        recipeSourceDocuments.clear();
        templateReports.clear(); templateImported.clear();
        adapterProbes.clear();
        notExpandedAdapters.clear();
        filesRead = 0;

        // THE ADAPTER EXPANSION STARTS FROM AN EMPTY SLATE, and it must: the probe map is what tells a
        // "this adapter generated nothing" apart from "this adapter was never asked", and a reload that
        // carried the previous pass's entries forward could never make that distinction.
        hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapters.captureProbes();
        hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapters.captureNotExpanded();

        File directory = ModDataHolder.INSTANCE.getRecipeDirectory();
        List<File> candidates = discoverDirectory(directory);
        if (directory == null || !directory.isDirectory()) {
            // Same reasoning as MachineLoader's: "no recipes loaded" must not be able to mean "the
            // directory was never created" without saying so.
            problems.add("recipe directory is not available: " + directory
                    + " (ModDataHolder.setup() creates it at common setup)");
        }

        // PASS 1 -- READ AND PARSE. Templates are held back rather than registered: an adapter template is a
        // GENERATOR, and the recipes it stands for do not exist until the adapter runs. Registering the
        // template would put a recipe in the machine's list that MachineTicker refuses as
         
        List<ParsedFile> parsed = new ArrayList<>();
        for (File file : candidates) {
            filesRead++;
            try (BufferedReader reader = java.nio.file.Files.newBufferedReader(
                    file.toPath(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                RecipeDefinition definition = parse(file.getPath(), root, machineLoader);
                if (definition == null) {
                    continue;
                }
                parsed.add(new ParsedFile(file.getPath(), definition));
                recipeSourceDocuments.put(file.getPath(), root.deepCopy());
            } catch (Exception e) {
                // A file that cannot be READ is still reported, and the message still carries the path: the
                // whole point of this catch is that one bad file cannot take the rest down.
                problems.add(file.getPath() + ": " + e);
            }
        }

        // PASS 2 -- REGISTER ORDINARY RECIPES FIRST. The upstream registry loads normal recipes before
        // adapter templates; this ordering is required by the dynamic-machine adapter, whose source set is
        // another machine's already-loaded recipes.
        int total = 0;
        for (ParsedFile file : parsed) {
            RecipeDefinition definition = file.definition();
            if (definition.adapter() != null) continue;
            for (MachineDefinition machine : definition.machines()) {
                if (machineLoader.findByName(machine.name()) == null) {
                    // MACHINE ARRAYS REACH HERE WITH REAL WORK ALREADY DONE, and this is where "the array
                    // produced nothing for one of its machines" is said out loud rather than being three
                    // recipes when the author wrote four.
                    problems.add(file.path() + ": names machine '" + machine.name() + "' which did not load"
                            + " (its '" + definition.registryName() + "' variant is not registered)");
                    continue;
                }
                if (accept(new MachineRecipe(machine.name(), machine.recipeName(), definition.recipeTime(),
                        definition.requirements(), null, definition.cancelOnPerTickFailure(),
                        definition.maxThreads(), definition.threadName(), definition.priority(),
                        definition.commands(), definition.tooltips(), definition.parallelized(), definition.loadJEI(),
                        definition.displayName()), file.path())) {
                    total++;
                }
            }
        }

        // Script/CraftTweaker recipes are early registrations in the upstream lifecycle. Install them before
        // dynamic adapters so a script-authored source recipe is visible to a copy adapter on the same reload.
        total += installScriptRecipes(machineLoader);

        // PASS 3 -- EXPAND ADAPTER TEMPLATES. Generated recipes enter the SAME registry, through the SAME
        // duplicate rule, as authored recipes. Registering the dynamic machine adapters at this point also
        // makes the lifecycle match the 1.12 CE implementation while allowing /reload to replace stale ones.
        hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterRegistry
                .registerDynamicMachineAdapters(machineLoader.all());
        for (ParsedFile file : parsed) {
            RecipeDefinition definition = file.definition();
            if (definition.adapter() != null) {
                total += expandAdapter(file.path(), definition, machineLoader);
            }
        }

        sortRecipesByPriority();
        report(total);
    }

    /** Scripts use the same collision, priority, execution and network paths as JSON recipes. */
    public int installScriptRecipes(MachineLoader machines) {
        int added = 0;
        for (JsonObject snapshot : ScriptRecipeRegistry.snapshots()) {
            String name = snapshot.get("name").getAsString();
            String source = "CraftTweaker:" + name;
            // A script action may run after the reload listener; merging twice must be harmless.
            if (source.equals(sourceByRecipeName.get(name))) continue;
            try {
                MachineRecipe recipe = RecipeSnapshotCodec.decode(snapshot);
                ScriptRecipeRegistry.applyCallbacks(name, recipe);
                if (machines.findByName(recipe.machine()) == null)
                    throw new IllegalArgumentException("Unknown machine: " + recipe.machine());
                if (accept(recipe, source, false)) added++;
            } catch (RuntimeException invalid) {
                problems.add(source + ": " + invalid.getMessage());
                Modularmachinery.LOG.error("[MMCE] rejected script recipe {}: {}", name, invalid.getMessage());
            }
        }
        sortRecipesByPriority();
        return added;
    }

    /** Source order: ascending priority, then sortId minus input weights, then registration order. */
    private void sortRecipesByPriority() {
        // One sequence across ALL machines and priorities, reconstructed from the insertion-ordered registry.
        // Snapshots preserve that order. Re-sorting after a script merge must not number an already sorted list.
        Map<String, Integer> sortIds = new LinkedHashMap<>();
        for (String name : byName.keySet()) sortIds.put(name, sortIds.size());
        byMachine.values().forEach(recipes -> recipes.sort(
                java.util.Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparingInt(recipe -> sortIds.get(recipe.registryName()) - recipe.sortingWeight())
                        // Source TreeSet treats an equal score as a duplicate. Keep both recipe identities.
                        .thenComparingInt(recipe -> sortIds.get(recipe.registryName()))));
    }

    /** One file read into the pieces the expansion and registration passes need. */
    private record ParsedFile(String path, RecipeDefinition definition) {
    }

    /**
     * Runs one template's adapter and registers what it generates.
     *
     * <p><b>This is the whole of defect C's expansion path</b>: a recipe carrying an adapter name either
     * becomes a set of runnable recipes here, or is named in {@link #getNotExpandedAdapters()} and in the
     * load-time report. There is no third outcome -- which is the contract, because the field being read,
     * stored and judged while producing nothing runnable is the state that "LOOKS SUPPORTED".</p>
     *
     * @return how many recipes were registered
     */
    private int expandAdapter(String path, RecipeDefinition definition, MachineLoader machineLoader) {
        int registered = 0;
        var report = templateReports.computeIfAbsent(path, key -> new ArrayList<>());
        for (MachineDefinition machine : definition.machines()) {
            if (machineLoader.findByName(machine.name()) == null) {
                problems.add(path + ": names machine '" + machine.name() + "' which did not load"
                        + " (adapter " + definition.adapter() + " cannot be expanded for it)");
                continue;
            }
            hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterRegistry.registerBuiltIns();
            int before = notExpandedAdapters.size();
            java.util.Collection<MachineRecipe> generated =
                    hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterRegistry
                            .createRecipesFor(definition.adapter(), ModDataHolder.INSTANCE.getRecipeDirectory()
                                            .toPath().toAbsolutePath().normalize().relativize(
                                                    java.nio.file.Path.of(path).toAbsolutePath().normalize())
                                            .toString().replace('\\', '/') + "\0" + machine.recipeName(), machine.name(),
                                    definition.modifiers(), definition.requirements());
            // The registry records its own reason; the FILE path is this class's to add, and without it the
            // report names an adapter and not the file an author can open.
            List<String> recorded = hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapters
                    .captureNotExpanded();
            for (String entry : recorded) {
                notExpandedAdapters.add(entry + "  [template: " + path + "]"); report.add(machine.name() + ": " + entry);
            }
            var probes = hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapters.captureProbes();
            adapterProbes.putAll(probes);
            probes.forEach((id, probe) -> report.add(machine.name() + ": " + probe.converted + "/" + probe.candidates + " 已转换；跳过输入不可表示=" + probe.skippedNoSingleInput + "，空输出=" + probe.skippedNoOutput + "，非有限修改值=" + probe.skippedNonFiniteModifier));
            if (generated == null) {
                continue;
            }
            for (MachineRecipe recipe : generated) {
                if (!definition.displayName().isBlank()) recipe = recipe.withDisplayName(definition.displayName());
                if (accept(recipe.withPriority(definition.priority()).withCommands(definition.commands())
                                .withMetadata(definition.tooltips(), definition.parallelized(), definition.loadJEI()),
                        "internal/adapter/" + definition.adapter(), false)) {
                    registered++;
                }
            }
        }
        templateImported.put(path, registered);
        return registered;
    }

    /** Immutable loaded template documents; editor IDs are relative paths, never personal absolute paths. */
    public JsonArray editorTemplates() {
        var array = new JsonArray();var root=ModDataHolder.INSTANCE.getRecipeDirectory().toPath().toAbsolutePath().normalize();
        recipeSourceDocuments.forEach((path, source) -> {
            if(!source.has("adapter"))return;var absolute=java.nio.file.Path.of(path).toAbsolutePath().normalize();if(!absolute.startsWith(root))return;
            var entry=new JsonObject();entry.addProperty("path",root.relativize(absolute).toString().replace('\\','/'));entry.add("source",source.deepCopy());
            entry.addProperty("digest",hellfirepvp.modularmachinery.common.editor.EditorSaveState.revision(source.toString()));
            entry.addProperty("expanded",templateImported.containsKey(path));
            entry.addProperty("imported",templateImported.getOrDefault(path,0));var details=new JsonArray();templateReports.getOrDefault(path,List.of()).forEach(details::add);entry.add("report",details);array.add(entry);
        });return array;
    }
    public static Requirement validateEditorRequirement(JsonObject json) {
        var loader=new RecipeLoader();var result=loader.parseRequirement("<editor>",json);
        if(result==null)throw new IllegalArgumentException(String.join("; ",loader.getProblems()));return result;
    }

    /** The basename without {@code .json} — the same naming rule MachineLoader.nameOf applies. */
    private static String nameOf(File file) {
        String name = file.getName();
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    


















 
    private record MachineDefinition(String name, String recipeName) {
    }

    /**
     * One recipe file's parsed content.
     *
     * <p>It exists so that PARSING and REGISTERING are two passes. An adapter template names the machine it
     * belongs to but the recipes it stands for do not exist yet, so a single pass would have to register the
     * template itself -- which is the port's old state: a recipe in the machine's list that
     * {@code MachineTicker} refuses as an "unexpanded adapter recipe".</p>
     */
    private record RecipeDefinition(List<MachineDefinition> machines, String registryName, int recipeTime,
                                    List<Requirement> requirements, ResourceLocation adapter,
                                    List<RecipeModifier> modifiers, boolean cancelOnPerTickFailure,
                                    int maxThreads, String threadName, int priority,
                                    hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer commands,
                                    List<String> tooltips, boolean parallelized, boolean loadJEI, String displayName) {
    }

    private RecipeDefinition parse(String path, JsonObject root, MachineLoader machineLoader) {
        // THE 'machine' KEY IS A STRING **OR AN ARRAY OF STRINGS** (upstream MachineRecipe U338-U360, read
        // line by line). This was the port's worst recipe-loader defect of the two: `string(root, "machine",
        // null)` called `getAsString()` on whatever the key held, so an ARRAY threw
        // `UnsupportedOperationException: JsonArray` out of `parse`, and `load`'s catch turned that into ONE
        // problem line for the file. The file was dropped -- so the recipes it declared were not merely
        // mis-parsed, they were ABSENT, and the report said only that the file could not be read.
        //
        // Upstream's rules, in its own order:
        //   * an ARRAY must be non-empty, or "'machine' is an array, but it's empty! Provide at least 1
        //     owning-machine name!" (U341-U343 and again U352-U355);
        //   * every element must be a STRING, or "Found an element in the array specified in 'machine' that
        //     is not a string!" (U350);
        //   * a bare string is one owning machine (U356-U357);
        //   * anything else is "'machine' has to be either an array of strings or just a string!" (U359).
        if (!root.has("machine") || root.get("machine").isJsonNull()) {
            problems.add(path + ": no 'machine'");
            return null;
        }
        JsonElement elementMachine = root.get("machine");
        List<String> unqualifiedMachines = new ArrayList<>();
        if (elementMachine.isJsonArray()) {
            JsonArray array = elementMachine.getAsJsonArray();
            if (array.isEmpty()) {
                problems.add(path + ": 'machine' is an array, but it's empty! "
                        + "Provide at least 1 owning-machine name!");
                return null;
            }
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    problems.add(path + ": found an element in the array specified in 'machine' that is not"
                            + " a string! " + element);
                    return null;
                }
                unqualifiedMachines.add(element.getAsString());
            }
        } else if (elementMachine.isJsonPrimitive() && elementMachine.getAsJsonPrimitive().isString()) {
            unqualifiedMachines.add(elementMachine.getAsString());
        } else {
            problems.add(path + ": 'machine' has to be either an array of strings or just a string! —"
                    + " Found " + elementMachine + " instead!");
            return null;
        }

        // The machine MUST exist -- a recipe whose machine never loaded can never run, so keeping it would
        // only produce a machine that silently does nothing. THE CHECK STAYS IN PARSE rather than moving to
        // the registration pass, because it is the author's file that is wrong and the report has to name
        // the file. Which of an ARRAY's machines failed is named there instead.
        for (String machine : unqualifiedMachines) {
            if (machineLoader.findByName(machine) == null) {
                problems.add(path + ": names machine '" + machine + "' which did not load");
                return null;
            }
        }
        List<MachineDefinition> machines = new ArrayList<>(unqualifiedMachines.size());
        // (the per-machine names are built below, once registryName is known: an array's copies are named
        //  `<registryName>_sub_N`, and registryName is read a few lines further down.)

         
        //
        // Upstream (MachineRecipe:332-334) rejects a recipe with neither key. The port accepted either one OR
        // NEITHER and fell back to the FILE PATH, which produced a recipe whose identity depended on where it
        // was stored -- so moving a file renamed a recipe, and two files could collide on one name.
        //
        // Both spellings are accepted because upstream accepts both; requiring one of them is what upstream
        // does with the other.
        String registryName = root.has("adapter")
                // An ADAPTER file is a TEMPLATE, not a recipe: upstream ships alloy_smelter_furnaces.adapter.json
                // with neither registryName nor recipeTime, and routes it through RecipeAdapterAccessor rather
                // than through the deserializer that requires those keys. The file's basename is upstream's own
                // fallback shape here, and the name it produces is only ever used in reports: the recipes the
                // template stands for are named by the ADAPTER, from the vanilla recipe each one came from.
                ? string(root, "registryName", string(root, "registryname", nameOf(new File(path))))
                : string(root, "registryName", string(root, "registryname", null));
        if (registryName == null) {
            problems.add(path + ": no registryName (upstream rejects this)");
            return null;
        }
        // THE PER-MACHINE NAMES, built here because the array's copies carry `<registryName>_sub_N`.
        if (unqualifiedMachines.size() == 1) {
            // RULE A0 -- a single machine keeps the recipe's OWN name. Upstream would rename it to
            // `<name>_sub_0`, because its deserialiser turns even a bare string into a one-element list
            // before the container numbers them (MachineRecipe U340-U360 then U295-U305). That rename would
            // reach every shipped recipe file, the JEI page, the factory's selection, and every check in this
            // project's suite -- for a recipe whose name no pack author could then predict from the file. The
            // array is the case that NEEDS the names disambiguated, so the array is where they are.
            machines.add(new MachineDefinition(unqualifiedMachines.get(0), registryName));
        } else {
            // Upstream's own numbering (U297-U299), indexed exactly as upstream indexes it: the FIRST
            // machine's copy is `<name>_sub_0`. The index is per GENERATED recipe, so two arrays sharing a
            // registryName collide on `_sub_0` -- a duplicate, reported and discarded by the registry's own
            // rule rather than silently kept.
            for (int i = 0; i < unqualifiedMachines.size(); i++) {
                machines.add(new MachineDefinition(unqualifiedMachines.get(i), registryName + "_sub_" + i));
            }
        }
        // (the block below requires it: see section 313)
         
        //
        // Upstream (MachineRecipe:335-337) throws "No recipeTime-entry specified!". The port defaulted it to 0,
        // and 0 is a duration no recipe can ever reach -- so a recipe that forgot the key loaded without error,
        // appeared in JEI, and could never run. A silent never-runnable recipe is harder to diagnose than a
        // rejected file, and the file is the thing the author can fix.
        if (!root.has("adapter") && (!root.has("recipeTime") || !root.get("recipeTime").isJsonPrimitive())) {
            problems.add(path + ": no recipeTime (upstream rejects this)");
            return null;
        }
        int recipeTime = root.has("recipeTime") ? root.get("recipeTime").getAsInt() : 0;
        // (the block above requires it: see section 313)

        ResourceLocation adapter = null;
        if (root.has("adapter") && !root.get("adapter").isJsonNull()) {
            adapter = ResourceLocation.tryParse(root.get("adapter").getAsString());
        }

        List<Requirement> requirements = new ArrayList<>();
        if (root.has("requirements") && root.get("requirements").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("requirements");
            for (JsonElement element : array) {
                Requirement requirement = parseRequirement(path, element.getAsJsonObject());
                if (requirement == null) {
                    // UPSTREAM THROWS, which rejects the WHOLE recipe (MachineRecipe:459-461 for an unknown
                    // type, and MachineRecipe:446-449 for a missing io-type). The port skipped the requirement
                    // and kept the recipe, so a machine would craft WITHOUT that input -- the most dangerous
                    // way to be wrong here, because nothing reports it and the recipe appears to work.
                    return null;
                }
                requirements.add(requirement);
            }
        }

        if (!root.has("adapter") && requirements.isEmpty()) {
            // A recipe with neither requirements nor an adapter can never do anything.
            problems.add(path + ": no 'requirements' and no 'adapter'");
            return null;
        }

        // The template's `modifiers`, parsed HERE rather than inside the adapter: the adapter's
        // createRecipesFor takes them as a parameter, and a modifier this class could not parse is a problem
        // with THIS FILE, which only this class can report.
        List<RecipeModifier> modifiers = new ArrayList<>();
        if (adapter != null && root.has("modifiers")) {
            if (!root.get("modifiers").isJsonArray()) {
                problems.add(path + ": 'modifiers' has to be an array of modifier objects!"
                        + " (upstream rejects this)");
                return null;
            }
            for (JsonElement element : root.getAsJsonArray("modifiers")) {
                if (!element.isJsonObject()) {
                    problems.add(path + ": elements of 'modifiers' have to be modifier objects!");
                    return null;
                }
                RecipeModifier modifier = RecipeModifier.deserialize(element.getAsJsonObject());
                if (modifier == null) {
                    // RecipeModifier.deserialize returns null for a modifier missing target/io/multiplier/
                    // operation or with an out-of-range operation. Upstream rejects the file; the port
                    // reports it, which is this class's contract for every other rejected value.
                    problems.add(path + ": a 'modifiers' entry is not a complete modifier (target, io,"
                            + " multiplier and an operation in 0..1 are all required) — skipped");
                    continue;
                }
                modifiers.add(modifier);
            }
        }

        // The sixth component is false here, and that is upstream own default rather than a placeholder:
        // a recipe does not void its inputs on a per-tick failure unless it says so. THE JSON KEY THAT WOULD
         
         
        // "cancelIfPerTickFails": default FALSE, must be a boolean primitive, and anything else throws with that
        // exact message. THE DEFAULT LIVES IN THE FIELD NAME THERE (voidPerTickFailure) and it is the same here:
        // a recipe does NOT destroy its inputs on a per-tick failure unless it says so.
        boolean cancelOnPerTickFailure = false;
        if (root.has("cancelIfPerTickFails")) {
            if (!root.get("cancelIfPerTickFails").isJsonPrimitive()
                    || !root.get("cancelIfPerTickFails").getAsJsonPrimitive().isBoolean()) {
                problems.add(path + ": 'cancelIfPerTickFails' has to be a boolean! (if specified)");
            } else {
                cancelOnPerTickFailure = root.get("cancelIfPerTickFails").getAsBoolean();
            }
        }

         
        //
         
        // upstream's ONLY writers for MachineRecipe.threadName and MachineRecipe.maxThreads are CraftTweaker
        // (RecipePrimer.setThreadName L982-L984 / setMaxThreads L966-L968, plus mergeAdapter L139-L143 for the
        // adapter path), so a JSON recipe could only ever hold the defaults "" and -1 -- and this port has no
        // script layer at all. Their ONLY reader upstream is FactoryRecipeSearchTask.canCheck L89-U99, the
        // factory's recipe filter. Reading them here is what gives that reader a producer; the KEY NAMES are
        // upstream's own (its field and setter names), not invented.
        //
        // THE DEFAULTS ARE UPSTREAM'S (L93-L94: "" and -1), and -1 means UNLIMITED -- see the field docs on
        // MachineRecipe for why the default is not 1 and why that is not a defect.
        int maxThreads = -1;
        if (root.has("maxThreads")) {
            if (!root.get("maxThreads").isJsonPrimitive()
                    || !root.get("maxThreads").getAsJsonPrimitive().isNumber()) {
                // The same "has to be a number! (if specified)" contract upstream applies to its own numeric
                // keys (MachineRecipe U373-U379 for priority), reported instead of thrown.
                problems.add(path + ": 'maxThreads' has to be a number! (if specified)");
            } else {
                maxThreads = root.get("maxThreads").getAsInt();
            }
        }
        String threadName = "";
        if (root.has("threadName")) {
            if (!root.get("threadName").isJsonPrimitive()
                    || !root.get("threadName").getAsJsonPrimitive().isString()) {
                problems.add(path + ": 'threadName' has to be a string! (if specified)");
            } else {
                threadName = root.get("threadName").getAsString();
            }
        }

        int priority = 0;
        if (root.has("priority")) {
            JsonElement value = root.get("priority");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                problems.add(path + ": 'priority' has to be a number! (if specified)");
                return null;
            }
            priority = value.getAsInt();
        }

        hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer commands;
        try {
            commands = hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer.parse(root);
        } catch (IllegalArgumentException invalidCommands) {
            problems.add(path + ": " + invalidCommands.getMessage());
            return null;
        }

        List<String> tooltips = new ArrayList<>();
        if (root.has("tooltips")) {
            JsonElement value = root.get("tooltips");
            if (!value.isJsonArray()) {
                problems.add(path + ": 'tooltips' has to be an array of strings! (if specified)");
                return null;
            }
            for (JsonElement tip : value.getAsJsonArray()) {
                if (!tip.isJsonPrimitive() || !tip.getAsJsonPrimitive().isString()) {
                    problems.add(path + ": every 'tooltips' entry has to be a string!");
                    return null;
                }
                tooltips.add(tip.getAsString());
            }
        }
        boolean parallelized = hellfirepvp.modularmachinery.common.data.Config.recipeParallelizeEnabledByDefault();
        if (root.has("parallelized")) {
            if (!root.get("parallelized").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("parallelized").isBoolean()) {
                problems.add(path + ": 'parallelized' has to be a boolean! (if specified)");
                return null;
            }
            parallelized = root.get("parallelized").getAsBoolean();
        }
        boolean loadJEI = true;
        if (root.has("loadJEI")) {
            if (!root.get("loadJEI").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("loadJEI").isBoolean()) {
                problems.add(path + ": 'loadJEI' has to be a boolean! (if specified)");
                return null;
            }
            loadJEI = root.get("loadJEI").getAsBoolean();
        }
        String displayName = "";
        if (root.has("displayName")) {
            JsonElement value = root.get("displayName");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || value.getAsString().length() > 200) {
                problems.add(path + ": 'displayName' must be a string of at most 200 characters");
                return null;
            }
            displayName = value.getAsString();
        }
        return new RecipeDefinition(machines, registryName, recipeTime, requirements, adapter, modifiers,
                cancelOnPerTickFailure, maxThreads, threadName, priority, commands,
                tooltips, parallelized, loadJEI, displayName);
    }

    /**
     * An item requirement's amount, per upstream RequirementTypeItem:52-58.
     *
     * <p>Absent means 1, and a present value is CLAMPED to 1..64. The port read it unclamped, so
     * "amount": 0 was a FREE INPUT and "amount": 9999 was an impossible one -- both silently, and both
     * differing from the mod.
     */
    private static int clampedItemAmount(JsonObject requirement) {
        if (!requirement.has("amount") || !requirement.get("amount").isJsonPrimitive()) {
            return 1;
        }
        return Math.max(1, Math.min(64, requirement.get("amount").getAsInt()));
    }

    /**
     * A chance, applying upstream's rule: OUT OF RANGE IS IGNORED, not clamped.
     *
     * <p>Upstream applies the value only when it is between 0 and 1 and otherwise leaves the requirement at
     * its default, which is 1.0 -- always. The port CLAMPED, so "chance": -1 meant NEVER here and ALWAYS in
     * the mod: the same inversion-instead-of-refusal that io-type had (section 311).
     */
    private static float chanceOf(JsonObject requirement) {
        if (!requirement.has("chance") || !requirement.get("chance").isJsonPrimitive()) {
            return 1.0F;
        }
        float chance = requirement.get("chance").getAsFloat();
        return (chance >= 0F && chance <= 1F) ? chance : 1.0F;
    }

    /**
     * A fluid requirement's amount, per upstream RequirementTypeFluid:38-48.
     *
     * <p>REQUIRED upstream -- a fluid requirement with no amount is a rejected file. The port defaulted it
     * to 1000 mB, which is a plausible-looking number that the author never wrote. A present value is
     * floored at 0, which is upstream's only modification of it.
     */
    private static int requiredFluidAmount(String path, JsonObject requirement) {
        if (!requirement.has("amount") || !requirement.get("amount").isJsonPrimitive()) {
            throw new RequiredKeyMissing(path + ": fluid requirement has no amount");
        }
        return Math.max(0, requirement.get("amount").getAsInt());
    }

    private Requirement parseRequirement(String path, JsonObject requirement) {
        try {
            Requirement parsed = parseRequirementBody(path, requirement);
            if (parsed != null) {
                hellfirepvp.modularmachinery.common.editor.RequirementBatchEditor.applySource(parsed, requirement);
                parsed.setSelectorTag(hellfirepvp.modularmachinery.common.machine.SelectorTags.read(requirement));
                if (requirement.has("triggerTime")) {
                    if (!requirement.get("triggerTime").isJsonPrimitive()
                            || !requirement.getAsJsonPrimitive("triggerTime").isNumber()) {
                        throw new IllegalArgumentException("'triggerTime' has to be a number");
                    }
                    parsed.setTriggerTime(Math.max(0, requirement.get("triggerTime").getAsInt()));
                }
                if (requirement.has("triggerRepeatable")) {
                    if (!requirement.get("triggerRepeatable").isJsonPrimitive()
                            || !requirement.getAsJsonPrimitive("triggerRepeatable").isBoolean()) {
                        throw new IllegalArgumentException("'triggerRepeatable' has to be a boolean");
                    }
                    parsed.setTriggerRepeatable(requirement.get("triggerRepeatable").getAsBoolean());
                }
                if (requirement.has("ignoreOutputCheck")) {
                    if (!requirement.get("ignoreOutputCheck").isJsonPrimitive()
                            || !requirement.getAsJsonPrimitive("ignoreOutputCheck").isBoolean()) {
                        throw new IllegalArgumentException("'ignoreOutputCheck' has to be a boolean");
                    }
                    parsed.setIgnoreOutputCheck(requirement.get("ignoreOutputCheck").getAsBoolean());
                }
                requirementSources.put(parsed, requirement.deepCopy());
            }
            return parsed;
        } catch (IllegalArgumentException | com.google.gson.JsonParseException invalid) {
            problems.add(path + ": " + invalid.getMessage());
            return null;
        }
    }

    private Requirement parseRequirementBody(String path, JsonObject requirement) {
        String type = string(requirement, "type", null);
        if (type == null) {
            problems.add(path + ": a requirement has no 'type'");
            return null;
        }

         
        //
        // Upstream (MachineRecipe:446-449) throws when the key is missing or is not a string, which rejects the
        // WHOLE recipe. The port defaulted it to input, and the consequence was worse than a rejected recipe:
        // a requirement whose author forgot the key had its DIRECTION INVERTED -- an intended OUTPUT became an
        // INPUT, so the machine would consume what it was meant to produce.
        //
        // A default that silently means the opposite of the intent is the one kind of default this loader must
        // not have. The message names the requirement type rather than quoting the file: the path is already
        // the first thing in every problem line.
        String ioTypeName = string(requirement, "io-type", null);
        if (ioTypeName == null) {
            problems.add(path + ": a requirement of type " + type + " has no io-type");
            return null;
        }
        IOType ioType = IOType.getByString(ioTypeName);
        // (the block above makes io-type required, so this is never a defaulted value)
        if (ioType == null) {
            problems.add(path + ": requirement '" + type + "' has an invalid io-type");
            return null;
        }

        try {
             
            //
            // This is the change section 374 called for: until now no code outside this class could add a requirement
            // type, which is why the five upstream types the port does not implement were collected into
            // `unknownRequirementTypes` and REPORTED rather than loaded, and why D7 measured the twenty modularmagic
            // providers as blocked by "the port's own deleted layer" rather than by their target mods.
            //
            // The three built-ins are registered, so this path handles them; the switch below remains as the fallback
            // for a name the registry does not know, which keeps this step behaviour-preserving while the three branches
            // are migrated one at a time into the implementations that already exist beside this class.
            //
            // Registration is idempotent and is called HERE, not from a static block: a registry consulted before its
            // built-ins were in it would report every one of them as unknown, and that failure would look like a
            // parsing bug.
            hellfirepvp.modularmachinery.common.crafting.requirement.type.Requirements.registerBuiltIns();
            ResourceLocation typeId = ResourceLocation.tryParse(type);
            if (typeId != null) {
                hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType registered =
                        hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementTypeRegistry.get(typeId);
                if (registered != null) {
                    try {
                        return registered.createRequirement(ioType, requirement);
                    } catch (hellfirepvp.modularmachinery.common.crafting.requirement.type
                            .RequirementParseException parseFailure) {
                        // The implementation names the KEY; this method names the FILE. Neither can do the other's job:
                        // a type has no path, and the loader does not know which key mattered.
                        problems.add(path + ": " + parseFailure.getMessage());
                        return null;
                    }
                }
            }

            return switch (type) {
            case TYPE_ENERGY -> {
                 
                //
                // Upstream's RequirementEnergy takes it as a mandatory constructor argument (and holds it as a
                // long). The port read it with a 0 default, and 0 for energy is a requirement that costs
                // nothing -- a machine that runs on free power, with nothing in the log to say so.
                //
                // A block rather than an expression so the failure can yield null, which the caller now treats
                // as "reject the recipe" (section 312).
                if (!requirement.has("energyPerTick") || !requirement.get("energyPerTick").isJsonPrimitive()) {
                    problems.add(path + ": energy requirement has no energyPerTick");
                    yield null;
                }
                yield new RequirementEnergy(ioType, requirement.get("energyPerTick").getAsInt());
            }
            case TYPE_ITEM -> {
                // The raw string is passed straight through rather than parsed here: a legacy id such
                // as 'ore:ingotIron' (an ore-dictionary name) or 'minecraft:dye@3' (item + metadata) is
                // NOT a valid resource location, and rejecting it at this point would discard the
                // shims' chance to resolve it. RequirementItem owns resolution and reports what it could
                // not map.
                String raw = string(requirement, "item", null);
                if (raw == null || raw.isEmpty()) {
                    problems.add(path + ": item requirement has no 'item' key");
                    yield null;
                }
                yield new RequirementItem(ioType, raw,
                        clampedItemAmount(requirement),
                        // 'chance' was previously not read at all, so a recipe declaring
                        // "chance": 0.6 -- as centrifuge_wash_redstone does -- always produced its
                        // output. Silently giving more than the pack asked for is the worst kind of
                        // divergence: nothing fails and nothing warns.
                        chanceOf(requirement));
            }

            case TYPE_FLUID -> {
                // 'fluid' is the tank contents, not the fluid BLOCK declared inside a structure pattern
                // (that is the matcher's business). Keyed on 'fluid', with 'amount' in millibuckets.
                String raw = string(requirement, "fluid", null);
                if (raw == null || raw.isEmpty()) {
                    problems.add(path + ": fluid requirement has no 'fluid' key");
                    yield null;
                }
                yield new RequirementFluid(ioType, raw,
                        requiredFluidAmount(path, requirement), chanceOf(requirement));
            }

            default -> {
                // Recorded by name so the unimplemented-type worklist is explicit rather than a count.
                if (!unknownRequirementTypes.contains(type)) {
                    unknownRequirementTypes.add(type);
                }
                yield null;
            }
        };
        } catch (RequiredKeyMissing missing) {
            problems.add(missing.getMessage());
            return null;
        }
    }

    private void report(int total) {
        Modularmachinery.LOG.info("[MMCE] machine recipes loaded: {} from {} file(s) read, across {} machine(s)",
                total, filesRead, byMachine.size());
        for (Map.Entry<String, List<MachineRecipe>> entry : byMachine.entrySet()) {
            Modularmachinery.LOG.info("[MMCE]   {} — {} recipe(s)", entry.getKey(), entry.getValue().size());
        }

        // DUPLICATE NAMES ARE REPORTED BEFORE ANYTHING ELSE, because a collision is the one finding that
        // changes which recipe a machine RUNS. The wording is upstream's own (RecipeRegistry U139-U140), so a
        // pack author's existing search for "already exists" still finds it.
        if (!collisions.isEmpty()) {
            Modularmachinery.LOG.warn("[MMCE] {} recipe name(s) claimed by more than one file; the LATER file"
                    + " is discarded, exactly as upstream does:", collisions.size());
            getCollisions().forEach(c -> Modularmachinery.LOG.warn("[MMCE]   {}", c));
        }

        if (!unknownRequirementTypes.isEmpty()) {
            Modularmachinery.LOG.warn("[MMCE] {} requirement type(s) not implemented by this port "
                    + "(recipes using them are skipped):", unknownRequirementTypes.size());
            unknownRequirementTypes.forEach(t -> Modularmachinery.LOG.warn("[MMCE]   {}", t));
        }

        // THE ADAPTER EXPANSION'S OWN REPORT, and it is the half that may not be silent: a template naming an
        // adapter that produced nothing is a machine that has NO recipes, and "the file is on disk and was
        // read without error" is exactly the state that would look like it works.
        for (Map.Entry<ResourceLocation, hellfirepvp.modularmachinery.common.crafting.adapter
                .AdapterMinecraftFurnace.Probe> entry : adapterProbes.entrySet()) {
            Modularmachinery.LOG.info("[MMCE] adapter {} generated recipes: {}", entry.getKey(),
                    entry.getValue());
        }
        if (!notExpandedAdapters.isEmpty()) {
            Modularmachinery.LOG.warn("[MMCE] {} adapter template(s) produced NO recipes — the machines they"
                    + " name have none from these files:", notExpandedAdapters.size());
            notExpandedAdapters.forEach(entry -> Modularmachinery.LOG.warn("[MMCE]   {}", entry));
        }

        if (!problems.isEmpty()) {
            Modularmachinery.LOG.warn("[MMCE] {} recipe problem(s):", problems.size());
            problems.forEach(p -> Modularmachinery.LOG.warn("[MMCE]   {}", p));
        }

        // Report unresolvable item ids separately: the recipe loaded, but its item does not exist,
        // which usually means a mod is missing rather than that the recipe is malformed.
        List<String> unresolvedItems = new ArrayList<>();
        List<String> aliased = new ArrayList<>();
        for (List<MachineRecipe> recipes : byMachine.values()) {
            for (MachineRecipe recipe : recipes) {
                for (Requirement requirement : recipe.requirements()) {
                    if (requirement instanceof RequirementItem item) {
                        if (!item.isResolvable()) {
                            unresolvedItems.add(recipe.registryName() + " -> " + item.getRawId());
                        } else if (item.getResolutionNote() != null) {
                            aliased.add(recipe.registryName() + ": " + item.getRawId()
                                    + " -> " + item.getResolutionNote());
                        }
                    }
                }
            }
        }
        if (!aliased.isEmpty()) {
            // Reported because an automatic substitution is a decision the port made on the pack's
            // behalf, and a silent one would be indistinguishable from the ids having matched.
            Modularmachinery.LOG.info("[MMCE] {} recipe item id(s) mapped to 1.20.1 by the legacy id "
                    + "shims (metadata: {} / ore-dict: {} entries):",
                    aliased.size(), ItemMetadataShim.mappedCount(), ItemTagShim.mappedCount());
            aliased.forEach(a -> Modularmachinery.LOG.info("[MMCE]   {}", a));
        }
        if (!unresolvedItems.isEmpty()) {
            Modularmachinery.LOG.warn("[MMCE] {} recipe item id(s) do not resolve (mod missing?), "
                    + "those recipes cannot run:", unresolvedItems.size());
            unresolvedItems.forEach(u -> Modularmachinery.LOG.warn("[MMCE]   {}", u));
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    /**
     * Thrown when a key upstream REQUIRES is missing, so the reading switch can report it from anywhere.
     *
     * <p>Upstream throws {@code JsonParseException} from the requirement-type classes; the port flattens all
     * of them into one switch, so the path from a nested read back to "reject the recipe" needs a carrier.
     * Caught immediately in {@link #parseRequirement}, which returns null -- the same contract as every other
     * rejection in this file.
     */
    private static final class RequiredKeyMissing extends RuntimeException {
        RequiredKeyMissing(String message) {
            super(message);
        }
    }
}
