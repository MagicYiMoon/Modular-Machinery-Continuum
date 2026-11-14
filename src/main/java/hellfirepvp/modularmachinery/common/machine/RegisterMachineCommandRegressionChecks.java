/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.command.CommandModularMachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import hellfirepvp.modularmachinery.common.network.DefinitionSync;
import hellfirepvp.modularmachinery.common.network.DefinitionSyncAssembler;
import hellfirepvp.modularmachinery.common.network.DefinitionSyncPacket;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorMachineEdit;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRequest;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import io.netty.buffer.Unpooled;

/** Real command parsing and disk export, including unquoted Unicode and JSON escaping. */
public final class RegisterMachineCommandRegressionChecks {
    private RegisterMachineCommandRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var controllerBefore = level.getBlockState(origin);
        var corner = origin.east();
        var cornerBefore = level.getBlockState(corner);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RegisterTest")) {
            @Override
            public boolean hasPermissions(int required) { return required <= 2; }

            @Override
            public HitResult pick(double distance, float partialTick, boolean fluids) {
                return new BlockHitResult(Vec3.atCenterOf(origin), Direction.NORTH, origin, false);
            }
        };
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        CommandModularMachinery.register(dispatcher);
        var source = player.createCommandSourceStack().withPermission(2);
        String prefix = "register_test_" + UUID.randomUUID().toString().replace("-", "");
        var root = ModDataHolder.INSTANCE.getMachineryDirectory().toPath();
        try {
            level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState(), Block.UPDATE_ALL);
            String[] names = {null, "合金炉", "高级 合金炉 Alloy Smelter", "合金\"炉\\试验", "§b彩色合金炉"};
            Block[] components = {RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(),
                    RegistryBlocks.fluidInputHatch.get(), RegistryBlocks.energyInputHatch.get(), Blocks.CHEST};
            for (int i = 0; i < names.length; i++) {
                level.setBlock(corner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(corner, components[i].defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(corner) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
                    chest.setCustomName(net.minecraft.network.chat.Component.literal("中文 \"引号\"\\路径\n换行\t制表"));
                var actualNbt = level.getBlockEntity(corner).saveWithoutMetadata();
                String id = prefix + "_" + i;
                var file = root.resolve(id + ".json");
                mark(player, origin, corner);
                String command = "modularmachinery register " + id + (names[i] == null ? "" : " " + names[i]);
                long revision = MachineReloadListener.getServerDefinitionRevision();
                require(dispatcher.execute(command, source) == 1, "register rejected display name: " + names[i]);
                var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                require(saved.get("registryname").getAsString().equals(id), "display name changed registry ID");
                String expected = names[i] == null ? id : names[i];
                require(saved.get("localizedname").getAsString().equals(expected), "display name did not survive JSON");
                var parsed = new MachineLoader().parseMachineFile(file.toFile());
                require(parsed.isPresent() && parsed.get().getLocalizedName().equals(expected), "exported name failed loader roundtrip");
                var part = saved.getAsJsonArray("parts").get(0).getAsJsonObject();
                require(net.minecraft.nbt.TagParser.parseTag(part.get("nbt").getAsString()).equals(actualNbt),
                        "export changed block entity data or injected structure palette/data");
                require(parsed.get().getPattern().getParts().get(0).structureNbt().matches(actualNbt),
                        "exported hatch NBT does not match original block entity");
                checkExportedFormation(level, origin, id, components[i], actualNbt);
                require(MachineReloadListener.getServerDefinitionRevision() > revision
                        && MachineReloadListener.getLoader().findByName(id) != null, "register did not load new machine immediately");
                require(file.toRealPath().equals(java.nio.file.Path.of(MachineReloadListener.getLoader().getSourcePath(id)).toRealPath()),
                        "new machine editor source is missing or incorrect");
                checkEditorAndWire(id, expected);
                require(PlayerStructureSelectionHelper.selectionOf(player).isEmpty(), "successful register kept selection");
                // A listed machine must also support an actual editor save without /reload or restart.
                String snapshot = PktMachineEditorRequest.snapshot();
                var menu = new MenuMachineEditor(73, player.getInventory(), snapshot);
                player.containerMenu = menu;
                String renamed = "已编辑 " + i;
                var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(snapshot),
                        new PktMachineEditorMachineEdit(id, renamed));
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    PktMachineEditorRequest.encode(request, buffer);
                    request = PktMachineEditorRequest.decode(buffer);
                } finally { buffer.release(); }
                var reply = PktMachineEditorRequest.process(player, request);
                require(reply.result().success(), "new machine could not be edited: " + reply.result().message());
                require(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("localizedname").getAsString().equals(renamed),
                        "new machine editor did not save to disk");
                checkEditorAndWire(id, renamed);
                player.containerMenu = player.inventoryMenu;
                String before = Files.readString(file);
                mark(player, origin, corner);
                require(dispatcher.execute(command, source) == 0 && Files.readString(file).equals(before), "register overwrote existing machine");
            }
            // Match the old construction-tool format, including literal newlines inside JSON strings.
            var legacy = new com.google.gson.JsonObject();
            legacy.addProperty("registryname", prefix + "_legacy");
            legacy.addProperty("localizedname", "旧版舱室导出");
            var legacyPart = new com.google.gson.JsonObject();
            legacyPart.addProperty("x", 0); legacyPart.addProperty("y", 1); legacyPart.addProperty("z", 0);
            legacyPart.addProperty("elements", "modularmachinery:blockinputbus");
            legacyPart.addProperty("nbt", "{\n    data: [],\n    palette: [],\n    ForgeCaps: {},\n    groupInput: 0b,\n    inventory: {Items: [],Size: 6}\n}");
            var legacyParts = new com.google.gson.JsonArray(); legacyParts.add(legacyPart); legacy.add("parts", legacyParts);
            var legacyFile = root.resolve(prefix + "_legacy.json");
            Files.writeString(legacyFile, legacy.toString().replace("\\n", "\n"));
            MachineReloadListener.reloadNow();
            require(MachineReloadListener.getLoader().findByName(prefix + "_legacy") != null,
                    "old raw-newline SNBT export was rejected");
            checkEditorAndWire(prefix + "_legacy", "旧版舱室导出");
            mark(player, origin, corner);
            require(dispatcher.execute("modularmachinery register " + prefix + "_long " + "中".repeat(257), source) == 0,
                    "overlong display name accepted");
            require(!Files.exists(root.resolve(prefix + "_long.json"))
                    && PlayerStructureSelectionHelper.selectionOf(player).isComplete(), "failed register wrote file or lost selection");
            Modularmachinery.LOG.info("[MMC][register-command] PASS: real dispatcher with item/fluid/energy/chest block entities, exact escaped SNBT, real automatic formation in four facings, legacy multiline export, immediate GUI load/save and client wire roundtrip, duplicate and selection guards");
        } finally {
            player.containerMenu = player.inventoryMenu;
            PlayerStructureSelectionHelper.purgeSelection(player);
            for (int i = 0; i < 5; i++) Files.deleteIfExists(root.resolve(prefix + "_" + i + ".json"));
            Files.deleteIfExists(root.resolve(prefix + "_long.json"));
            Files.deleteIfExists(root.resolve(prefix + "_legacy.json"));
            level.setBlock(origin, controllerBefore, Block.UPDATE_ALL);
            level.setBlock(corner, cornerBefore, Block.UPDATE_ALL);
            MachineReloadListener.reloadNow();
        }
    }

    private static void checkExportedFormation(ServerLevel level, BlockPos origin, String id,
                                                Block component, net.minecraft.nbt.CompoundTag nbt) {
        var machine = MachineReloadListener.getLoader().findByName(id);
        var part = machine.getPattern().getParts().get(0);
        var previous = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        previous.put(origin, level.getBlockState(origin));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
            previous.putIfAbsent(pos, level.getBlockState(pos));
        }
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                level.setBlock(origin, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState()
                        .setValue(BlockController.FACING, facing), Block.UPDATE_ALL);
                var pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos, component.defaultBlockState(), Block.UPDATE_ALL);
                level.getBlockEntity(pos).load(nbt.copy());
                var controller = (TileController) level.getBlockEntity(origin);
                var blueprint = new net.minecraft.world.item.ItemStack(RegistryItems.BLUEPRINT.get());
                ItemBlueprint.setAssociatedMachine(blueprint, machine);
                ((net.minecraftforge.items.IItemHandlerModifiable) controller.getBlueprintInventory()).setStackInSlot(0, blueprint);
                MachineTicker.tick(level, origin, controller);
                require(controller.isFormed() && id.equals(controller.getMachineName()),
                        "command-exported machine failed actual formation at " + facing + ": " + id);
                if (level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.common.tiles.base.TileItemBus bus) {
                    ((net.minecraftforge.items.IItemHandlerModifiable) bus.getInventory()).setStackInSlot(0,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
                    controller.requestStructureCheck();
                    MachineTicker.tick(level, origin, controller);
                    require(controller.isFormed(), "exported empty Items list rejected a working bus at " + facing);
                }
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } finally {
            previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            level.getBlockEntity(origin.east()).load(nbt.copy());
        }
    }

    private static void checkEditorAndWire(String id, String expected) {
        var defs = MachineReloadListener.serverDefinitions();
        var editor = JsonParser.parseString(MachineEditorSnapshot.create(defs.machines(), defs.recipes())).getAsJsonObject();
        boolean listed = false;
        for (var entry : editor.getAsJsonArray("machines")) {
            var machine = entry.getAsJsonObject();
            if (machine.get("name").getAsString().equals(id)) {
                listed = machine.get("editableName").getAsBoolean()
                        && machine.get("localizedName").getAsString().equals(expected);
                require(!machine.get("controllerRegistered").getAsBoolean(), "test machine unexpectedly has a startup controller");
            }
        }
        require(listed, "new machine missing or not editable in GUI snapshot");
        byte[] bytes = DefinitionSync.snapshot(defs).toString().getBytes(StandardCharsets.UTF_8);
        var assembler = new DefinitionSyncAssembler();
        UUID transfer = UUID.randomUUID();
        byte[] complete = null;
        for (int offset = 0; offset < bytes.length; offset += DefinitionSyncPacket.CHUNK_BYTES) {
            var packet = new DefinitionSyncPacket(transfer, bytes.length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + DefinitionSyncPacket.CHUNK_BYTES)));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DefinitionSyncPacket.encode(packet, buffer);
                complete = assembler.accept(DefinitionSyncPacket.decode(buffer));
            } finally { buffer.release(); }
        }
        require(complete != null && Arrays.equals(bytes, complete), "new machine snapshot did not survive wire chunks");
        var wire = JsonParser.parseString(new String(complete, StandardCharsets.UTF_8)).getAsJsonObject();
        var client = MachineLoader.fromSnapshot(wire.getAsJsonObject("definitions"));
        require(client.findByName(id) != null && client.findByName(id).getLocalizedName().equals(expected),
                "new machine/name missing from client definitions");
    }

    private static void mark(FakePlayer player, BlockPos first, BlockPos second) {
        PlayerStructureSelectionHelper.setFirstCorner(player, first);
        PlayerStructureSelectionHelper.setSecondCorner(player, second);
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
