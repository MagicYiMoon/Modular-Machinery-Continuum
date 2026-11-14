/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Exercises the GUI save envelope and real automatic formation with the legacy cszz geometry/NBT. */
public final class BlueprintSettingRegressionChecks {
    private BlueprintSettingRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String name = "blueprint_setting_" + UUID.randomUUID().toString().replace("-", "");
        Path file = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(name + ".json");
        var before = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int y = 0; y <= 2; y++) before.put(origin.above(y), level.getBlockState(origin.above(y)));
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BlueprintSettingTest")) {
            @Override public boolean hasPermissions(int permission) { return permission <= 2; }
        };
        JsonObject json = new JsonObject();
        json.addProperty("registryname", name);
        json.addProperty("localizedname", "创世之柱蓝图测试");
        json.addProperty("has-factory", true);
        var parts = new JsonArray();
        for (int y = 1; y <= 2; y++) {
            var part = new JsonObject();
            part.addProperty("x", 0); part.addProperty("y", y); part.addProperty("z", 0);
            part.addProperty("elements", "modularmachinery:block" + (y == 1 ? "input" : "output") + "bus");
            part.addProperty("nbt", "{data:[],palette:[],ForgeCaps:{},definedColor:0,groupId:0,groupInput:0b,inventory:{Items:[],Size:6}}");
            parts.add(part);
        }
        json.add("parts", parts);
        try {
            Files.writeString(file, json.toString());
            MachineReloadListener.reloadNow();
            require(!MachineReloadListener.getLoader().findByName(name).isRequiresBlueprint(), "omitted key must retain optional default");
            level.setBlock(origin.above(), RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.above(2), RegistryBlocks.itemOutputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var loader = MachineReloadListener.getLoader();
            var broken = loader.findByName(name);
            var mismatch = new StructureMatcher().match(level, origin, Direction.NORTH, broken.getPattern(), loader.getVariables(), loader.getResolver());
            require(!mismatch.matched(), "legacy data/palette constraints unexpectedly matched real hatches");

            // Correct the legacy structure-file fields and the absent empty capability compound.
            for (var entry : parts) {
                var part = entry.getAsJsonObject();
                var nbt = TagParser.parseTag(part.get("nbt").getAsString());
                nbt.remove("data"); nbt.remove("palette"); nbt.remove("ForgeCaps");
                part.addProperty("nbt", nbt.toString());
            }
            Files.writeString(file, json.toString());
            MachineReloadListener.reloadNow();
            loader = MachineReloadListener.getLoader();
            var fixed = loader.findByName(name);
            var matched = new StructureMatcher().match(level, origin, Direction.NORTH, fixed.getPattern(), loader.getVariables(), loader.getResolver());
            require(matched.matched(), "corrected cszz NBT still mismatches: " + matched.mismatches()
                    + "; actual input=" + level.getBlockEntity(origin.above()).saveWithoutMetadata());

            save(player, name, false);
            checkFormation(level, origin, name, false, false, true);
            checkFormation(level, origin, name, true, false, true);
            checkLiveToggle(level, origin, player, name, false);
            checkLiveToggle(level, origin, player, name, true);
            save(player, name, true);
            checkFormation(level, origin, name, false, false, false);
            checkFormation(level, origin, name, true, false, false);
            checkFormation(level, origin, name, false, true, true);
            checkFormation(level, origin, name, true, true, true);
            save(player, name, false);
            // Saving the setting must not remove unrelated structure NBT constraints.
            var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            require(saved.get("parts").equals(parts), "blueprint toggle changed structure conditions");
            var nbt = level.getBlockEntity(origin.above()).saveWithoutMetadata();
            nbt.putInt("groupId", 42);
            level.getBlockEntity(origin.above()).load(nbt);
            checkFormation(level, origin, name, false, false, false);
            Modularmachinery.LOG.info("[MMC][blueprint-setting-regression] PASS: optional default, GUI envelope codec/save/reload/editor and client snapshots, cszz legacy NBT diagnosis/correction, ordinary/factory automatic formation and live policy changes with/without blueprint, unrelated NBT constraints retained");
        } finally {
            player.containerMenu = player.inventoryMenu;
            for (var pos : before.keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            Files.deleteIfExists(file);
            MachineReloadListener.reloadNow();
        }
    }

    private static void checkLiveToggle(ServerLevel level, BlockPos pos, FakePlayer player,
                                        String name, boolean factory) throws Exception {
        save(player, name, false);
        var loader = MachineReloadListener.getLoader();
        var machines = new LinkedHashMap<>(loader.getMachines());
        try {
            loader.getMachines().entrySet().removeIf(e -> !e.getValue().getRegistryName().equals(name));
            level.setBlock(pos, (factory ? RegistryBlocks.blockFactoryController : RegistryBlocks.blockController)
                    .get().defaultBlockState(), Block.UPDATE_ALL);
            var tile = (TileController) level.getBlockEntity(pos);
            if (tile instanceof TileFactoryController f) FactoryTicker.tick(level, pos, f);
            else MachineTicker.tick(level, pos, tile);
            require(tile.isFormed(), "live-toggle fixture did not form");
            loader.getMachines().clear(); loader.getMachines().putAll(machines);
            save(player, name, true);
            require(!tile.isFormed(), "enabling required blueprint left a formed machine running without one");
            var blueprint = new ItemStack(RegistryItems.BLUEPRINT.get());
            ItemBlueprint.setAssociatedMachine(blueprint, MachineReloadListener.getLoader().findByName(name));
            ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, blueprint);
            if (tile instanceof TileFactoryController f) FactoryTicker.tick(level, pos, f);
            else MachineTicker.tick(level, pos, tile);
            require(tile.isFormed(), "inserting the required blueprint did not restore formation");
            save(player, name, false);
            ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, ItemStack.EMPTY);
            require(tile.isFormed(), "removing optional blueprint unformed the machine");
        } finally {
            loader.getMachines().clear(); loader.getMachines().putAll(machines);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void save(FakePlayer player, String name, boolean required) throws Exception {
        var loader = MachineReloadListener.getLoader();
        var machine = loader.findByName(name);
        var menu = new MenuMachineEditor(91, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        var request = new PktMachineEditorRequest(91, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()),
                new PktMachineEditorControllerSettings(name, machine.getDefinedColor() & 0xFFFFFF, machine.isHasFactory(), required));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            require(request.equals(PktMachineEditorRequest.decode(buffer)) && buffer.readableBytes() == 0, "settings codec lost flag");
        } finally { buffer.release(); }
        var reply = PktMachineEditorRequest.process(player, request);
        require(reply.result().success(), "GUI save failed: " + reply.result().message());
        loader = MachineReloadListener.getLoader();
        require(loader.findByName(name).isRequiresBlueprint() == required, "server flag stale after save");
        require(MachineLoader.fromSnapshot(loader.snapshot()).findByName(name).isRequiresBlueprint() == required,
                "definition snapshot lost blueprint setting");
        boolean found = false;
        for (var entry : JsonParser.parseString(PktMachineEditorRequest.snapshot()).getAsJsonObject().getAsJsonArray("machines")) {
            var json = entry.getAsJsonObject();
            if (json.get("name").getAsString().equals(name)) {
                found = true;
                require(json.get("requiresBlueprint").getAsBoolean() == required, "editor flag stale after save");
            }
        }
        require(found, "edited machine missing from GUI snapshot");
    }

    private static void checkFormation(ServerLevel level, BlockPos pos, String name,
                                       boolean factory, boolean blueprint, boolean expected) {
        var loader = MachineReloadListener.getLoader();
        var machines = new LinkedHashMap<>(loader.getMachines());
        var machine = loader.findByName(name);
        try {
            // Prevent another machine's matching pattern from hiding a failed blueprint gate.
            loader.getMachines().entrySet().removeIf(e -> e.getValue() != machine);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos, (factory ? RegistryBlocks.blockFactoryController : RegistryBlocks.blockController)
                    .get().defaultBlockState(), Block.UPDATE_ALL);
            var tile = (TileController) level.getBlockEntity(pos);
            if (blueprint) {
                var stack = new ItemStack(RegistryItems.BLUEPRINT.get());
                ItemBlueprint.setAssociatedMachine(stack, machine);
                ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, stack);
            }
            if (tile instanceof TileFactoryController f) FactoryTicker.tick(level, pos, f);
            else MachineTicker.tick(level, pos, tile);
            require(tile.isFormed() == expected, "automatic formation factory=" + factory + ", blueprint=" + blueprint
                    + ", required=" + machine.isRequiresBlueprint() + ", expected=" + expected);
            if (expected) require(name.equals(tile.getMachineName()), "formed the wrong machine");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            loader.getMachines().clear(); loader.getMachines().putAll(machines);
        }
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError("Blueprint setting: " + message);
    }
}
