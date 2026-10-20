/* SPDX-License-Identifier: GPL-3.0-only
 * MMC 1.1: startup-registered controllers, following MMCE's per-machine registry names.
 */
package hellfirepvp.modularmachinery.common.registry;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Registry identities are fixed at startup; names, colours and recipes are resolved at runtime. */
public final class DedicatedControllers {
    public record Definition(String name, String displayName, int color, boolean factory) {}
    public record Entry(Definition definition, boolean factory, RegistryObject<? extends BlockController> block,
                        RegistryObject<? extends Item> item) {}
    private static final Map<String, Definition> DEFINITIONS = new TreeMap<>();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private DedicatedControllers() {}

    public static List<Entry> entries() { return Collections.unmodifiableList(ENTRIES); }
    public static String binding(Block block) {
        return block instanceof BlockController controller ? controller.getDedicatedMachine() : null;
    }
    public static Definition definition(String name) { return DEFINITIONS.get(name); }

    public static void register() {
        ModDataHolder data = ModDataHolder.INSTANCE;
        data.setup(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile());
        if (data.requiresDefaultMachinery()) data.copyDefaultMachinery();
        Path manifest = data.getMachineryDirectory().toPath().getParent().resolve("controller-registry.json");
        // Keep historical entries as tombstones so removing a definition cannot erase placed blocks.
        if (Files.exists(manifest)) {
            try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
                for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject object = element.getAsJsonObject();
                    Definition d = parse(object);
                    if (DEFINITIONS.putIfAbsent(d.name(), d) != null) throw new IllegalArgumentException("Duplicate machine " + d.name());
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read " + manifest + "; restore this controller registry before opening existing worlds", e);
            }
        }
        boolean defaultFactory = startupFactoryDefault();
        for (var file : MachineLoader.discoverDirectory(data.getMachineryDirectory()).get(MachineLoader.FileType.MACHINE)) {
            try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Definition parsed = parse(json);
                Definition old = DEFINITIONS.get(parsed.name());
                DEFINITIONS.put(parsed.name(), new Definition(parsed.name(), parsed.displayName(), parsed.color(),
                        parsed.factory() || defaultFactory || old != null && old.factory()));
            } catch (Exception e) {
                Modularmachinery.LOG.error("[MMC] Cannot register controllers from {}: {}", file, e.toString());
            }
        }
        Set<String> ids = new HashSet<>();
        RegistryBlocks.BLOCKS.getEntries().forEach(r -> ids.add(r.getId().getPath()));
        RegistryItems.ITEMS.getEntries().forEach(r -> ids.add(r.getId().getPath()));
        for (Definition definition : DEFINITIONS.values()) {
            add(definition, false, ids);
            if (definition.factory()) add(definition, true, ids);
        }
        try {
            JsonArray json = new JsonArray();
            for (Definition d : DEFINITIONS.values()) {
                JsonObject object = new JsonObject();
                object.addProperty("registryname", d.name()); object.addProperty("localizedname", d.displayName());
                object.addProperty("color", String.format(Locale.ROOT, "%06X", d.color()));
                object.addProperty("has-factory", d.factory()); json.add(object);
            }
            Path temp = manifest.resolveSibling(manifest.getFileName() + ".tmp");
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try { Files.move(temp, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, manifest, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { throw new IllegalStateException("Cannot preserve controller registry " + manifest, e); }
        Modularmachinery.LOG.info("[MMC] Registered {} dedicated controllers for {} machine identities", ENTRIES.size(), DEFINITIONS.size());
    }

    public static Definition parse(JsonObject json) {
        String name = json.get("registryname").getAsString();
        if (!name.matches("[a-z0-9_./-]+") || name.startsWith("/") || name.contains(".."))
            throw new IllegalArgumentException("Invalid machine registryname: " + name);
        String display = json.has("localizedname") ? json.get("localizedname").getAsString() : name;
        int color = hellfirepvp.modularmachinery.common.data.Config.machineColor();
        if (json.has("color")) {
            String hex = json.get("color").getAsString();
            if (hex.matches("[a-fA-F0-9]{1,6}")) color = Integer.parseInt(hex, 16);
        }
        return new Definition(name, display, color, json.has("has-factory") && json.get("has-factory").getAsBoolean());
    }

    private static boolean startupFactoryDefault() {
        Path path = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("modularmachinery-common.toml");
        if (!Files.isRegularFile(path)) return false;
        try (var config = com.electronwill.nightconfig.core.file.CommentedFileConfig.of(path)) {
            config.load(); return config.getOrElse("factory-system.enable-factory-controller-bydefault", false);
        } catch (Exception e) { throw new IllegalStateException("Cannot read factory registration setting from " + path, e); }
    }

    private static void add(Definition definition, boolean factory, Set<String> ids) {
        String id = definition.name() + (factory ? "_factory_controller" : "_controller");
        if (!ids.add(id)) throw new IllegalStateException("Dedicated controller registry collision: modularmachinery:" + id);
        var block = RegistryBlocks.BLOCKS.register(id, () -> factory
                ? new BlockFactoryController(RegistryBlocks.componentProperties(), definition.name())
                : new BlockController(RegistryBlocks.componentProperties(), definition.name()));
        var item = RegistryItems.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()) {
            @Override public Component getName(ItemStack stack) { return block.get().getName(); }
        });
        ENTRIES.add(new Entry(definition, factory, block, item));
    }

    public static Block[] validBlocks(boolean factory, Block generic) {
        List<Block> blocks = new ArrayList<>(); blocks.add(generic);
        ENTRIES.stream().filter(e -> e.factory() == factory).forEach(e -> blocks.add(e.block().get()));
        return blocks.toArray(Block[]::new);
    }

    public static Component displayName(String machineName, boolean factory) {
        var machine = MachineReloadListener.getLoader().findByName(machineName);
        var startup = DEFINITIONS.get(machineName);
        String name = machine != null ? machine.getLocalizedName() : startup != null ? startup.displayName() : machineName;
        return Component.translatable(factory ? "block.modularmachinery.dedicated_factory" : "block.modularmachinery.dedicated_controller",
                Component.translatableWithFallback("machine.modularmachinery." + machineName + ".name", name));
    }

    public static int color(String name) {
        var machine = MachineReloadListener.getLoader().findByName(name);
        var startup = DEFINITIONS.get(name);
        return machine != null ? machine.getDefinedColor() & 0xFFFFFF : startup != null ? startup.color() : 0xFFFFFF;
    }

    /** Display dedicated identities first, retaining legacy controllers as accepted alternatives. */
    public static java.util.List<net.minecraft.world.level.block.state.BlockState> controllerStates(String name) {
        var states = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
        for (Entry e : ENTRIES) if (e.definition().name().equals(name)) states.add(e.block().get().defaultBlockState());
        states.add(RegistryBlocks.blockController.get().defaultBlockState());
        states.add(RegistryBlocks.blockFactoryController.get().defaultBlockState());
        return java.util.List.copyOf(states);
    }

    public static ItemStack stackOrGeneric(String name, boolean factory) {
        ItemStack stack = stack(name, factory);
        return stack.isEmpty() ? new ItemStack(factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get()) : stack;
    }

    public static ItemStack stack(String name, boolean factory) {
        for (Entry entry : ENTRIES)
            if (entry.factory() == factory && entry.definition().name().equals(name)) return new ItemStack(entry.item().get());
        return ItemStack.EMPTY;
    }
}
