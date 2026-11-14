/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet.server;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerAssemblyLayout.*;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerModuleDefinition.*;
import static hellfirepvp.modularmachinery.common.hypernet.server.ServerAssemblyPreview.Fault;

/** Real Forge registry and disk-catalog checks, excluded from normal builds. */
public final class ServerModuleVerification {
    private static final Map<Type, Item> ITEMS = Map.ofEntries(
            Map.entry(Type.CPU, Items.REDSTONE), Map.entry(Type.GPU, Items.DIAMOND),
            Map.entry(Type.SOUL_CARD, Items.EMERALD), Map.entry(Type.QUANTUM_CARD, Items.QUARTZ),
            Map.entry(Type.RAM, Items.PAPER), Map.entry(Type.CPU_EXTENSION, Items.IRON_BLOCK),
            Map.entry(Type.CALCULATE_EXTENSION, Items.IRON_INGOT), Map.entry(Type.EXTENSION_EXTENSION, Items.COAL),
            Map.entry(Type.CPU_HEATSINK, Items.COPPER_INGOT), Map.entry(Type.RAM_HEATSINK, Items.GOLD_INGOT),
            Map.entry(Type.CALCULATE_HEATSINK, Items.AMETHYST_SHARD), Map.entry(Type.EXTENSION_HEATSINK, Items.CHARCOAL),
            Map.entry(Type.PSU, Items.REPEATER), Map.entry(Type.CAPACITOR, Items.COMPARATOR));
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static JsonObject json(Type type) {
        var json = new JsonObject(); json.addProperty("id", "fixture:" + type.name().toLowerCase(Locale.ROOT));
        json.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ITEMS.get(type)).toString());
        json.addProperty("type", type.name().toLowerCase(Locale.ROOT));
        if (type.calculable()) {
            json.addProperty("generation", switch (type) { case CPU -> 100; case GPU -> 50; case SOUL_CARD -> 10; default -> 20; });
            json.addProperty("energy-ratio", 10);
        }
        if (type.hasBandwidth()) json.addProperty("bandwidth", switch (type) {
            case CPU -> 6; case RAM -> 8; case GPU -> 2; case CPU_EXTENSION -> 123; default -> 1;
        });
        if (type == Type.PSU) json.addProperty("energy-provision", 100000L);
        if (type == Type.CAPACITOR) { json.addProperty("energy-capacity", 5000000L); json.addProperty("energy-consumption", 20000L); }
        var display = new JsonObject(); display.addProperty("tier", "&bT1");
        var tips = new JsonArray(); tips.add("仅用于显示，能力由参数决定"); display.add("tooltips", tips); json.add("display-info", display);
        return json;
    }
    private static List<ServerModuleDefinition> definitions() {
        return Arrays.stream(Type.values()).map(type -> ServerModuleDefinition.parse(json(type))).toList();
    }
    private static Address a(Bank bank, int slot) { return new Address(bank, slot); }
    private static void put(ServerAssemblyInventory assembly, Bank bank, int slot, Type type) {
        check(assembly.inventory(bank).insertItem(slot, new ItemStack(ITEMS.get(type)), false).isEmpty(), "install " + bank + "/" + slot);
    }
    private static ServerAssemblyInventory populated(ServerModuleRegistry.Catalog catalog) {
        var assembly = catalog.open(new ItemStack(Items.CHEST));
        put(assembly, Bank.CPU, 0, Type.CPU); put(assembly, Bank.CPU, 21, Type.CPU_HEATSINK);
        put(assembly, Bank.CPU, 4, Type.RAM); put(assembly, Bank.CPU, 25, Type.RAM_HEATSINK);
        put(assembly, Bank.POWER, 0, Type.PSU); put(assembly, Bank.POWER, 4, Type.CAPACITOR);
        put(assembly, Bank.CALCULATE_CARD, 8, Type.CALCULATE_EXTENSION); put(assembly, Bank.CALCULATE_CARD, 9, Type.CALCULATE_EXTENSION);
        put(assembly, Bank.CALCULATE_CARD, 0, Type.GPU); put(assembly, Bank.CALCULATE_CARD, 1, Type.SOUL_CARD);
        put(assembly, Bank.CALCULATE_CARD, 2, Type.QUANTUM_CARD);
        for (int i = 0; i < 3; i++) put(assembly, Bank.CALCULATE_CARD, 12 + i, Type.CALCULATE_HEATSINK);
        return assembly;
    }
    public static void run() throws Exception {
        check(ServerModuleRegistry.current().valid() && ServerModuleRegistry.current().definitions().containsKey(id("fixture:boot")), "real machine reload hook did not load server_modules");
        var activeDirectory = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("server_modules");
        try { definitionsAndParser(); preview(); disk(); }
        finally { check(ServerModuleRegistry.reload(activeDirectory), "restore original catalog"); }
        Modularmachinery.LOG.info("[MMC][server-modules] PASS: source efficiencies/bandwidth/energy, immutable author definitions, strict bounded JSON, real startup/reload catalogs, read-only typed preview and physical-item recovery");
    }
    private static void definitionsAndParser() throws Exception {
        var definitions = definitions(); check(definitions.size() == 14, "concrete source module types");
        for (var type : Type.values()) {
            var parsed = ServerModuleDefinition.parse(ServerModuleRegistry.parse(json(type).toString()));
            check(parsed.type() == type && parsed.type().kind() == type.kind(), "module type parse");
            check(parsed.display().tier().equals("&bT1") && parsed.display().tooltips().size() == 1, "display-only metadata");
        }
        check(ServerModuleDefinition.parse(json(Type.CPU_EXTENSION)).bandwidthConsumption() == 0, "source CPU extension effective zero bandwidth");
        check(Type.CPU.efficiency(Calculation.NEURON) == .025 && Type.GPU.efficiency(Calculation.LOGIC) == 5
                && Type.QUANTUM_CARD.efficiency(Calculation.QBIT) == 1 && Type.SOUL_CARD.efficiency(Calculation.QBIT) == .0001, "source efficiency matrix");
        rejects(() -> ServerModuleRegistry.parse("{\"id\":\"fixture:a\",\"id\":\"fixture:b\"}"));
        rejects(() -> ServerModuleRegistry.parse("{} {}")); rejects(() -> ServerModuleRegistry.parse("{unquoted:1}"));
        rejects(() -> ServerModuleRegistry.parse("{\"x\":NaN}"));
        rejects(() -> ServerModuleRegistry.parse("[".repeat(1000) + "0" + "]".repeat(1000)));
        rejects(() -> ServerModuleRegistry.parse(" ".repeat(ServerModuleRegistry.MAX_FILE_BYTES + 1)));
        for (var key : List.of("generation", "energy-ratio", "bandwidth")) {
            var missing = json(Type.CPU); missing.remove(key); rejects(() -> ServerModuleDefinition.parse(missing));
            var negative = json(Type.CPU); negative.addProperty(key, -1); rejects(() -> ServerModuleDefinition.parse(negative));
            var string = json(Type.CPU); string.addProperty(key, "1"); rejects(() -> ServerModuleDefinition.parse(string));
        }
        var fractional = json(Type.CPU); fractional.addProperty("bandwidth", 1.5); rejects(() -> ServerModuleDefinition.parse(fractional));
        var overflow = json(Type.CPU); overflow.addProperty("bandwidth", 2147483648L); rejects(() -> ServerModuleDefinition.parse(overflow));
        var nonfinite = json(Type.CPU); nonfinite.add("generation", new JsonPrimitive(new java.math.BigDecimal("1e999"))); rejects(() -> ServerModuleDefinition.parse(nonfinite));
        var zero = json(Type.CPU); zero.addProperty("generation", 0); rejects(() -> ServerModuleDefinition.parse(zero));
        var gpuOverflow = json(Type.GPU); gpuOverflow.addProperty("generation", Double.MAX_VALUE); rejects(() -> ServerModuleDefinition.parse(gpuOverflow));
        var irrelevant = json(Type.RAM); irrelevant.addProperty("generation", 5); rejects(() -> ServerModuleDefinition.parse(irrelevant));
        var typo = json(Type.PSU); typo.addProperty("energy_provision", 4); rejects(() -> ServerModuleDefinition.parse(typo));
        var unknownType = json(Type.CPU); unknownType.addProperty("type", "extension_card"); rejects(() -> ServerModuleDefinition.parse(unknownType));
        var air = json(Type.CPU); air.addProperty("item", "minecraft:air"); rejects(() -> ServerModuleDefinition.parse(air));
        var unnamed = json(Type.CPU); unnamed.addProperty("id", "cpu"); rejects(() -> ServerModuleDefinition.parse(unnamed));
        var capFraction = json(Type.CAPACITOR); capFraction.addProperty("energy-capacity", .5); rejects(() -> ServerModuleDefinition.parse(capFraction));
        var capOverflow = json(Type.CAPACITOR); capOverflow.add("energy-capacity", new JsonPrimitive(new java.math.BigDecimal("9223372036854775808"))); rejects(() -> ServerModuleDefinition.parse(capOverflow));
        var duplicateId = new ArrayList<>(definitions); duplicateId.add(definitions.get(0)); rejects(() -> ServerModuleRegistry.validate(duplicateId));
        var duplicateItem = json(Type.GPU); duplicateItem.addProperty("item", "minecraft:redstone");
        rejects(() -> ServerModuleRegistry.validate(List.of(definitions.get(0), ServerModuleDefinition.parse(duplicateItem))));
        rejects(() -> ServerModuleRegistry.validate(Collections.nCopies(ServerModuleRegistry.MAX_FILES + 1, definitions.get(0))));
        var absent = json(Type.CPU); absent.addProperty("item", "absent_mod:cpu");
        var catalog = ServerModuleRegistry.validate(List.of(ServerModuleDefinition.parse(absent)));
        check(catalog.definitions().size() == 1 && catalog.find(new ItemStack(Items.REDSTONE)) == null && catalog.find(ItemStack.EMPTY) == null, "unknown item granted fallback capability");
        try { catalog.definitions().clear(); throw new AssertionError("mutable catalog"); } catch (UnsupportedOperationException expected) { }
    }
    private static void preview() {
        var catalog = ServerModuleRegistry.validate(definitions()); var assembly = populated(catalog);
        var stack = assembly.stack(); var before = stack.save(new CompoundTag());
        var snapshot = ServerAssemblyPreview.inspect(stack, catalog);
        check(snapshot.assemblyReady() && snapshot.totalInstalled() == 14 && snapshot.installed(Kind.CALCULATE_CARD) == 3, "complete assembly readiness");
        check(snapshot.bandwidthProvided() == 8 && snapshot.bandwidthUsed() == 12, "bandwidth aggregation incl. calculate extensions");
        double efficiency = (float) 8 / 12; close(snapshot.bandwidthEfficiency(), efficiency, "source float bandwidth efficiency");
        close(snapshot.expected().get(Calculation.INTRICATE), 120.1 * efficiency, "intricate output");
        close(snapshot.expected().get(Calculation.LOGIC), 355.05 * efficiency, "logic output");
        close(snapshot.expected().get(Calculation.NEURON), 23.5 * efficiency, "neuron output");
        close(snapshot.expected().get(Calculation.QBIT), 20.351 * efficiency, "qbit output");
        close(snapshot.averageEfficiency().get(Calculation.LOGIC), (1 + 5 + .005 + .25) / 4, "source average efficiency");
        check(snapshot.energyProvision().equals(BigInteger.valueOf(100000)) && snapshot.energyCapacity().equals(BigInteger.valueOf(5000000))
                && snapshot.energyConsumption().equals(BigInteger.valueOf(20000)), "power and capacitor aggregation");
        for (int i = 0; i < 100; i++) check(ServerAssemblyPreview.inspect(stack, catalog).equals(snapshot), "preview unstable");
        check(stack.save(new CompoundTag()).equals(before), "preview mutated physical stack");
        var tampered = stack.copy();
        var item = tampered.getTag().getCompound("server").getCompound("cpuInv").getList("Items", Tag.TAG_COMPOUND).getCompound(0);
        item.getCompound("tag").putDouble("generation", 1e200); var forged = new CompoundTag();
        forged.putDouble("generation", 1e200); forged.putInt("bandwidth", Integer.MAX_VALUE); item.put("tag", forged);
        check(ServerAssemblyPreview.inspect(tampered, catalog).equals(snapshot), "item NBT supplied fake capabilities");
        var empty = ServerAssemblyPreview.inspect(new ItemStack(Items.CHEST), catalog);
        check(empty.faults().containsAll(Set.of(Fault.MISSING_CPU, Fault.MISSING_RAM, Fault.MISSING_PSU, Fault.MISSING_CAPACITOR)) && empty.expected().isEmpty(), "missing essentials diagnostics");
        assembly.inventory(Bank.CPU).extractItem(21, 1, false);
        var cooling = ServerAssemblyPreview.inspect(assembly.stack(), catalog);
        check(cooling.faults().contains(Fault.DEPENDENCIES) && cooling.expected().isEmpty() && cooling.dependencies().stream().anyMatch(d -> d.cooling() && d.slot().equals(a(Bank.CPU, 0))), "missing cooler diagnostics");
        put(assembly, Bank.CPU, 21, Type.CPU_HEATSINK);
        put(assembly, Bank.CPU, 20, Type.CPU_EXTENSION);
        var expanded = ServerAssemblyPreview.inspect(assembly.stack(), catalog);
        check(expanded.bandwidthUsed() == 12 && expanded.assemblyReady(), "CPU extension source zero bandwidth");
        put(assembly, Bank.CPU, 1, Type.CPU); put(assembly, Bank.CPU, 22, Type.CPU_HEATSINK);
        var over = ServerAssemblyPreview.inspect(assembly.stack(), catalog);
        check(over.faults().contains(Fault.HARDWARE_BANDWIDTH) && over.expected().isEmpty() && over.bandwidthEfficiency() == .5, "source 150% readiness limit and 50% floor");
        var definitions = definitions(); var noRam = definitions.stream().filter(d -> d.type() != Type.RAM).toList();
        var lost = ServerAssemblyPreview.inspect(stack, ServerModuleRegistry.validate(noRam));
        check(lost.faults().containsAll(Set.of(Fault.INVALID_MODULE, Fault.MISSING_RAM)) && lost.invalidModules().contains(a(Bank.CPU, 4)), "removed definition diagnostics");
        check(stack.save(new CompoundTag()).equals(before), "catalog removal changed physical items");
        var future = stack.copy(); future.getTag().getCompound("server").putInt("mmcAssemblyVersion", 19);
        check(ServerAssemblyPreview.inspect(future, catalog).faults().equals(Set.of(Fault.ASSEMBLY_INVALID)), "future assembly diagnostics");
        numericBounds();
    }
    private static void numericBounds() {
        var definitions = new ArrayList<>(definitions());
        for (var type : List.of(Type.PSU, Type.CAPACITOR)) {
            var large = json(type);
            if (type == Type.PSU) large.addProperty("energy-provision", Long.MAX_VALUE);
            else { large.addProperty("energy-capacity", Long.MAX_VALUE); large.addProperty("energy-consumption", Long.MAX_VALUE); }
            definitions.removeIf(d -> d.type() == type); definitions.add(ServerModuleDefinition.parse(large));
        }
        var catalog = ServerModuleRegistry.validate(definitions); var assembly = populated(catalog);
        put(assembly, Bank.POWER, 1, Type.PSU); put(assembly, Bank.POWER, 5, Type.CAPACITOR);
        var large = ServerAssemblyPreview.inspect(assembly.stack(), catalog); var twice = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        check(large.energyProvision().equals(twice) && large.energyCapacity().equals(twice) && large.energyConsumption().equals(twice), "long aggregate overflow");
        var hugeCpu = json(Type.CPU); hugeCpu.addProperty("generation", Double.MAX_VALUE); hugeCpu.addProperty("bandwidth", 0);
        var ram = json(Type.RAM); ram.addProperty("bandwidth", 0);
        var zeroDefinitions = definitions.stream().filter(d -> d.type() != Type.CPU && d.type() != Type.RAM).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        zeroDefinitions.add(ServerModuleDefinition.parse(hugeCpu)); zeroDefinitions.add(ServerModuleDefinition.parse(ram));
        var zeroCatalog = ServerModuleRegistry.validate(zeroDefinitions);
        var zeroAssembly = zeroCatalog.open(new ItemStack(Items.CHEST));
        put(zeroAssembly, Bank.CPU, 0, Type.CPU); put(zeroAssembly, Bank.CPU, 21, Type.CPU_HEATSINK);
        put(zeroAssembly, Bank.CPU, 4, Type.RAM); put(zeroAssembly, Bank.CPU, 25, Type.RAM_HEATSINK);
        put(zeroAssembly, Bank.POWER, 0, Type.PSU); put(zeroAssembly, Bank.POWER, 4, Type.CAPACITOR);
        var zero = ServerAssemblyPreview.inspect(zeroAssembly.stack(), zeroCatalog);
        check(zero.assemblyReady() && zero.bandwidthEfficiency() == 0 && zero.expected().values().stream().allMatch(v -> v == 0), "source zero provided/zero consumed bandwidth");
        var overflowDefinitions = new ArrayList<>(definitions()); overflowDefinitions.removeIf(d -> d.type() == Type.CPU);
        var largeCpu = json(Type.CPU); largeCpu.addProperty("generation", Double.MAX_VALUE); largeCpu.addProperty("bandwidth", 0);
        overflowDefinitions.add(ServerModuleDefinition.parse(largeCpu)); var overflowCatalog = ServerModuleRegistry.validate(overflowDefinitions);
        var overflowAssembly = overflowCatalog.open(new ItemStack(Items.CHEST));
        for (int i = 0; i < 2; i++) { put(overflowAssembly, Bank.CPU, i, Type.CPU); put(overflowAssembly, Bank.CPU, 21 + i, Type.CPU_HEATSINK); }
        put(overflowAssembly, Bank.CPU, 4, Type.RAM); put(overflowAssembly, Bank.CPU, 25, Type.RAM_HEATSINK);
        put(overflowAssembly, Bank.POWER, 0, Type.PSU); put(overflowAssembly, Bank.POWER, 4, Type.CAPACITOR);
        var overflow = ServerAssemblyPreview.inspect(overflowAssembly.stack(), overflowCatalog);
        check(overflow.faults().contains(Fault.OUTPUT_OVERFLOW) && overflow.expected().isEmpty(), "nonfinite aggregate output advertised");
    }
    private static void disk() throws Exception {
        var directory = Path.of("server-module-fixture"); Files.createDirectories(directory);
        for (var type : Type.values()) Files.writeString(directory.resolve(type.name() + ".json"), json(type).toString(), StandardCharsets.UTF_8);
        var first = ServerModuleRegistry.readCatalog(directory);
        check(first.definitions().size() == 14 && first.revision().equals(ServerModuleRegistry.readCatalog(directory).revision()), "disk catalog revision stability");
        check(ServerModuleRegistry.reload(directory) && ServerModuleRegistry.current().definitions().size() == 14, "real catalog publication");
        var physical = populated(first).stack(); var before = physical.save(new CompoundTag());
        var changed = json(Type.CPU); changed.addProperty("generation", 200);
        Files.writeString(directory.resolve("CPU.json"), changed.toString(), StandardCharsets.UTF_8);
        check(ServerModuleRegistry.reload(directory) && !first.revision().equals(ServerModuleRegistry.current().revision()), "changed config revision");
        var oldValue = ServerAssemblyPreview.inspect(physical, first).expected().get(Calculation.LOGIC);
        var newValue = ServerAssemblyPreview.inspect(physical, ServerModuleRegistry.current()).expected().get(Calculation.LOGIC);
        check(newValue > oldValue && first.find(new ItemStack(Items.REDSTONE)).generation() == 100, "immutable pinned catalog/new parameters");
        Files.writeString(directory.resolve("broken.json"), "{bad", StandardCharsets.UTF_8);
        Modularmachinery.LOG.info("[MMC][server-modules] expecting one invalid-catalog error for fail-closed reload check");
        check(!ServerModuleRegistry.reload(directory) && !ServerModuleRegistry.current().valid() && ServerModuleRegistry.current().definitions().isEmpty(), "bad reload retained stale capabilities");
        check(ServerAssemblyPreview.inspect(physical, ServerModuleRegistry.current()).faults().equals(Set.of(Fault.CATALOG_INVALID)), "bad catalog preview");
        rejects(() -> ServerModuleRegistry.current().open(physical));
        check(physical.save(new CompoundTag()).equals(before), "bad reload changed item");
        Files.writeString(directory.resolve("broken.json"), json(Type.CPU).toString(), StandardCharsets.UTF_8);
        rejects(() -> ServerModuleRegistry.readCatalog(directory));
        // Keep the invalid-file fixture; recover using a separate fresh directory.
        var recovery = directory.resolveSibling("server-module-recovery"); Files.createDirectories(recovery);
        for (var type : Type.values()) Files.writeString(recovery.resolve(type.name() + ".json"), json(type).toString(), StandardCharsets.UTF_8);
        check(ServerModuleRegistry.reload(recovery) && ServerAssemblyPreview.inspect(physical, ServerModuleRegistry.current()).assemblyReady(), "catalog recovery without restart");
        var badUtf = directory.resolveSibling("server-module-bad-utf"); Files.createDirectories(badUtf);
        Files.write(badUtf.resolve("module.json"), new byte[]{(byte)0xC3, (byte)0x28}); rejects(() -> ServerModuleRegistry.readCatalog(badUtf));
        var oversized = directory.resolveSibling("server-module-large"); Files.createDirectories(oversized);
        Files.write(oversized.resolve("module.json"), new byte[ServerModuleRegistry.MAX_FILE_BYTES + 1]); rejects(() -> ServerModuleRegistry.readCatalog(oversized));
        var tooMany = directory.resolveSibling("server-module-many"); Files.createDirectories(tooMany);
        for (int i = 0; i <= ServerModuleRegistry.MAX_FILES; i++) Files.writeString(tooMany.resolve(i + ".json"), "{}");
        rejects(() -> ServerModuleRegistry.readCatalog(tooMany));
        var batch = directory.resolveSibling("server-module-batch"); Files.createDirectories(batch);
        for (int i = 0; i < 270; i++) {
            var value = json(Type.CPU); value.addProperty("id", "fixture:batch_" + i); value.addProperty("item", "fixture:item_" + i);
            String text = value.toString(); Files.writeString(batch.resolve(i + ".json"), text + " ".repeat(ServerModuleRegistry.MAX_FILE_BYTES - text.getBytes(StandardCharsets.UTF_8).length));
        }
        rejects(() -> ServerModuleRegistry.readCatalog(batch));
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected invalid server module rejection");
    }
    private static void close(double actual, double expected, String label) {
        check(Math.abs(actual - expected) <= Math.max(1e-12, Math.abs(expected) * 1e-12), label + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
