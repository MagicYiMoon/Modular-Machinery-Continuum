/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.registry;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.event.AddPackFindersEvent;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Generated fallback assets stay below user resource packs, which may override each controller ID. */
public final class ControllerResourcePack {
    private ControllerResourcePack() {}
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) { addDataPack(event); return; }
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path root = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("generated_controller_assets");
        try {
            write(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"MMC dedicated controller fallback models\"}}");
            // Optional Blood Magic art uses its modern block path; missing dependencies retain a vanilla fallback.
            for (String direction : java.util.List.of("input", "output")) {
                String model = "blocklifeessenceprovider" + direction;
                try (var in = ControllerResourcePack.class.getResourceAsStream("/assets/modularmagic/models/block/" + model + ".json")) {
                    if (in == null) throw new IllegalStateException("Missing life essence model " + model);
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    if (net.minecraftforge.fml.ModList.get().isLoaded("bloodmagic"))
                        json = json.replace("minecraft:block/polished_andesite", "bloodmagic:block/blankrune");
                    write(root.resolve("assets/modularmagic/models/block/" + model + ".json"), json);
                }
            }
            for (var entry : DedicatedControllers.entries()) {
                String base = entry.factory() ? "blockfactorycontroller" : "blockcontroller";
                String path = entry.block().getId().getPath();
                try (var in = ControllerResourcePack.class.getResourceAsStream("/assets/modularmachinery/blockstates/" + base + ".json")) {
                    if (in == null) throw new IllegalStateException("Missing controller base model " + base);
                    write(root.resolve("assets/modularmachinery/blockstates/" + path + ".json"), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                JsonObject model = new JsonObject(); model.addProperty("parent", "modularmachinery:block/" + base);
                write(root.resolve("assets/modularmachinery/models/item/" + path + ".json"), model.toString());
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot build dedicated controller resource pack", e); }
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("modularmachinery:controllers", Component.literal("MMC Controller Models"), true,
                    id -> new PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.BOTTOM, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }
    private static void addDataPack(AddPackFindersEvent event) {
        Path root = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("generated_controller_data");
        try {
            write(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"MMC controller loot and tool tags\"}}");
            var values = new com.google.gson.JsonArray();
            for (var entry : DedicatedControllers.entries()) {
                String path = entry.block().getId().getPath();
                String base = entry.factory() ? "blockfactorycontroller" : "blockcontroller";
                try (var in = ControllerResourcePack.class.getResourceAsStream("/data/modularmachinery/loot_tables/blocks/" + base + ".json")) {
                    if (in == null) throw new IllegalStateException("Missing controller loot " + base);
                    write(root.resolve("data/modularmachinery/loot_tables/blocks/" + path + ".json"),
                            new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("modularmachinery:" + base, "modularmachinery:" + path));
                }
            }
            // Upstream machine components declare pickaxe harvest level 1 (stone or better).
            for (var block : net.minecraftforge.registries.ForgeRegistries.BLOCKS) {
                var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
                if (key != null && (key.getNamespace().equals("modularmachinery") || key.getNamespace().equals("modularmagic"))) values.add(key.toString());
            }
            Files.deleteIfExists(root.resolve("data/minecraft/tags/blocks/needs_iron_tool.json"));
            var tag = new JsonObject(); tag.addProperty("replace", false); tag.add("values", values);
            write(root.resolve("data/minecraft/tags/blocks/mineable/pickaxe.json"), tag.toString());
            write(root.resolve("data/minecraft/tags/blocks/needs_stone_tool.json"), tag.toString());
        } catch (Exception e) { throw new IllegalStateException("Cannot build dedicated controller data pack", e); }
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate("modularmachinery:controller_data", Component.literal("MMC Controller Data"), true,
                    id -> new PathPackResources(id, root, true), PackType.SERVER_DATA, Pack.Position.BOTTOM, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
    }

    private static void write(Path path, String text) throws java.io.IOException {
        Files.createDirectories(path.getParent()); Files.writeString(path, text, StandardCharsets.UTF_8);
    }
}
