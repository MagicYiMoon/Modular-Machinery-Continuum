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
package hellfirepvp.modularmachinery.common.network;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.tiles.TileParallelController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.Arrays;
import java.util.UUID;

/**
 * The mod's network channel and the five packet types it carries.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>Every GUI built before this one was <b>read-only</b>, and the parallel controller's screen said so on
 * its own panel. The reason was not a design preference: changing anything from a screen requires a
 * <b>client-to-server</b> message, and this port had no channel to send one on. That absence blocked not
 * just this control but every future one — the smart interface, the upgrade bus, and any blueprint
 * interaction all need the same thing. So the channel is the prerequisite, and it is built here rather
 * than the control being faked.
 *
 * <h2>Soft-dependency note</h2>
 *
 * <p>This package is <b>not</b> an integration: it uses only Forge's own networking, so it is always
 * present and references no optional mod. The {@code integration.*} packages keep their own disjoint rule
 * for the opposite reason.
 *
 * <h2>Server authority</h2>
 *
 * <p>The handler <b>validates before applying</b>, and does not trust the sent value:
 *
 * <ul>
 *   <li>the block entity must still be a parallel controller — the player may have broken it while the
 *       screen was open, so the position is looked up rather than assumed;</li>
 *   <li>the value is <b>clamped to the controller's own maximum</b> rather than accepted — a modified
 *       client could otherwise set an arbitrary parallelism and multiply a machine's output;</li>
 *   <li>the player must still be in range, matching {@code stillValid}, so a client cannot keep editing a
 *       machine it has walked away from.</li>
 * </ul>
 *
 * <p>This is the general shape every future packet in this channel should follow: **treat the client as
 * untrusted** and re-derive from the world rather than applying what was sent.
 *
 * <p><b>The rule is about what a packet CHANGES, not about the direction it travels.</b> Every rule
 * above exists because those three packets write world state, which a modified client could otherwise
 * use as remote control over other players' machines. {@link PktSyncSelection} is this channel's only
 * server-to-client packet and changes nothing on the far side, so it validates nothing — and its own
 * javadoc says why that is the same rule satisfied rather than the rule waived.
 */
public final class ModularmachineryNetwork {

    private ModularmachineryNetwork() {
    }

    /**
     * Protocol version. Mismatched versions refuse the connection rather than mis-decoding packets, which
     * is the failure mode that produces silent corruption instead of a clear disconnect.
     *
     * <p>Version 3 adds server-to-client machine/recipe definition snapshots. Both ends must update.
     * Version 2 changed {@link PktSyncSelection}'s payload to two corners (PLAN 655.2). The
     * id did not move — the rule below about appending rather than inserting is unchanged — but the bytes
     * of packet 3 did: an older half would read a corner count where a corner's boolean now sits and
     * decode a plausible, wrong box. That is precisely the "silent desync against any older half" the
     * append rule exists to prevent, and the version handshake is the mechanism that turns it into a
     * refusal instead. Cheap here because both halves ship in one jar; recorded because the next payload
     * change has the same choice to make.
     */
    // Version 6 preserves item durability-use semantics in snapshots.
    // Version 5 adds the fluid_pertick requirement to client snapshots.
    // Version 4 requires clients to preserve object NBT, output tags and display tags in snapshots.
    // Version 11 adds the client-to-server auto-assembly request and its selected dynamic size.
    // Version 12 adds the server-to-client chunked machine-editor snapshot packet.
    // Version 13 adds the server-authoritative add/delete/duration editor actions.
    // Version 14 adds recipe display names and the combined recipe-properties editor request.
    // Version 15 adds complete item/fluid/energy requirements and atomic first-requirement recipe creation.
    // Version 16 adds window-bound editor requests and authoritative save confirmations.
    // Version 25 includes factory thread name/limit in the combined recipe-options editor request.
    // Version 26 adds recipe-level batch and JEI visibility options to that request.
    // Version 27 adds a bounded recipe-tooltip list to that request.
    // Version 28 preserves and edits catalyst tooltip strings (including source blank lines).
    // Version 29 applies JSON batch exemptions and adds the independent requirement-batch editor.
    // Version 31 splits the energy-hatch menu's int values into two short words per logical field.
    private static final String PROTOCOL = "31";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Modularmachinery.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    /** Registers the packets. Called once, from the mod constructor on the mod bus. */
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, PktSetParallelism.class,
                PktSetParallelism::encode, PktSetParallelism::decode, PktSetParallelism::handle);
        CHANNEL.registerMessage(id++, PktGroupInputConfig.class,
                PktGroupInputConfig::encode, PktGroupInputConfig::decode, PktGroupInputConfig::handle);
        CHANNEL.registerMessage(id++, PktOpenGroupInputConfig.class,
                PktOpenGroupInputConfig::encode, PktOpenGroupInputConfig::decode,
                PktOpenGroupInputConfig::handle);
        // Appended rather than inserted, so the three ids above stay the ids they were: this is a
        // client<->server protocol and a renumbering is a silent desync against any older half.
        CHANNEL.registerMessage(id++, PktSyncSelection.class,
                PktSyncSelection::encode, PktSyncSelection::decode, PktSyncSelection::handle);
        // PLAN 663: the smart interface's value write, upstream's PktSmartInterfaceUpdate. Appended for the
        // same reason as the packet above, and it is a NEW payload rather than a changed one -- so PROTOCOL
        // does not move for it (an older half simply has no handler for id 4 and never receives it, because
        // nothing sends it unless the screen exists on both sides in the same jar).
        CHANNEL.registerMessage(id++, PktSmartInterfaceUpdate.class,
                PktSmartInterfaceUpdate::encode, PktSmartInterfaceUpdate::decode, PktSmartInterfaceUpdate::handle);
        // The ME fluid input bus's configuration write. Appended rather than inserted, for the reason the line above gives;
        // and it is a NEW payload rather than a changed one, so PROTOCOL does not move for it either.
        //
        // The id alone does not make this safe: the HANDLER names a class whose bodies name an ME tile, so those bodies are
        // verified when this class links -- and this class links unconditionally, AE2 or no AE2. That is why the entry point is
        // PktMEFluidInputBusConfig.apply and why the "which bus does the player have open" question is asked through
        // MenuMEFluidInputBus.isOpenFor(AbstractContainerMenu, BlockPos): both signatures name only vanilla types, so this line
        // cannot reintroduce the section 452/478 NoClassDefFoundError for IInWorldGridNodeHost on a standalone server.
        CHANNEL.registerMessage(id++, PktMEFluidInputBusConfig.class,
                PktMEFluidInputBusConfig::encode, PktMEFluidInputBusConfig::decode,
                PktMEFluidInputBusConfig::handle);
        // PLAN 737: the ME pattern provider's two top-left buttons, upstream's PktMEPatternProviderAction
        // (upstream L12-L60). Appended for the reason the line above gives, and it is a NEW payload rather than a
        // changed one, so PROTOCOL does not move for it.
        //
        // THE SAME CLASS-LOAD RULE APPLIES, and it is why the entry point is PktMEPatternProviderAction.apply and
        // why the "which block does the player have open" question is asked through
        // MenuMEPatternProvider.isOpenFor(AbstractContainerMenu, BlockPos): both signatures name only vanilla types.
        // Naming MenuMEPatternProvider is safe because THAT class is AE2-free by construction -- the pattern slots it
        // builds take AE2's inventory through its public Forge bridge, so the menu itself imports no appeng type.
        CHANNEL.registerMessage(id++, PktMEPatternProviderAction.class,
                PktMEPatternProviderAction::encode, PktMEPatternProviderAction::decode,
                PktMEPatternProviderAction::handle);
        // AUDIT_COMPONENTS row 11: the ME item output bus's stack-size cap. Appended rather than inserted, for the
        // reason the lines above give, and it is a NEW payload rather than a changed one, so PROTOCOL does not move
        // for it either. The same class-load rule applies and is the reason the entry point is
        // PktMEItemOutputBusStackSize.apply with a vanilla-typed signature.
        CHANNEL.registerMessage(id++, PktMEItemOutputBusStackSize.class,
                PktMEItemOutputBusStackSize::encode, PktMEItemOutputBusStackSize::decode,
                PktMEItemOutputBusStackSize::handle);
        // Source parity: the output-bus stack-size screen accepts an absolute number (and an expression),
        // whereas the quick +/- buttons above intentionally use deltas. Keep this as a separate payload so a
        // stale client cannot turn an absolute edit into an accidental delta.
        CHANNEL.registerMessage(id++, PktMEItemOutputBusStackSizeSet.class,
                PktMEItemOutputBusStackSizeSet::encode, PktMEItemOutputBusStackSizeSet::decode,
                PktMEItemOutputBusStackSizeSet::handle);
        // Source parity: the ME item input bus's config count can be changed with the mouse wheel. The packet carries
        // the requested absolute count, while the server re-reads the currently marked stack and validates the open menu.
        CHANNEL.registerMessage(id++, PktMEItemInputBusCount.class,
                PktMEItemInputBusCount::encode, PktMEItemInputBusCount::decode,
                PktMEItemInputBusCount::handle);
        CHANNEL.registerMessage(id++, PktMEItemInputBusGhost.class,
                PktMEItemInputBusGhost::encode, PktMEItemInputBusGhost::decode,
                PktMEItemInputBusGhost::handle);
        CHANNEL.registerMessage(id++, DefinitionSyncPacket.class,
                DefinitionSyncPacket::encode, DefinitionSyncPacket::decode, DefinitionSyncPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PktAutoAssemblyRequest.class,
                PktAutoAssemblyRequest::encode, PktAutoAssemblyRequest::decode, PktAutoAssemblyRequest::handle);
        CHANNEL.registerMessage(id++, PktMachineEditorEdit.class,
                PktMachineEditorEdit::encode, PktMachineEditorEdit::decode, PktMachineEditorEdit::handle);
        CHANNEL.registerMessage(id++, PktMachineEditorMachineEdit.class,
                PktMachineEditorMachineEdit::encode, PktMachineEditorMachineEdit::decode,
                PktMachineEditorMachineEdit::handle);
        // The editor document is larger than Forge's vanilla open-menu payload limit, so it is sent
        // after the menu opens as bounded PLAY_TO_CLIENT chunks. Appended to preserve every existing id.
        CHANNEL.registerMessage(id++, PktMachineEditorSnapshot.class,
                PktMachineEditorSnapshot::encode, PktMachineEditorSnapshot::decode,
                PktMachineEditorSnapshot::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PktMachineEditorAction.class,
                PktMachineEditorAction::encode, PktMachineEditorAction::decode,
                PktMachineEditorAction::handle);
        CHANNEL.registerMessage(id++, PktMachineEditorRecipeEdit.class,
                PktMachineEditorRecipeEdit::encode, PktMachineEditorRecipeEdit::decode,
                PktMachineEditorRecipeEdit::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PktMachineEditorRequirementAdd.class,
                PktMachineEditorRequirementAdd::encode, PktMachineEditorRequirementAdd::decode,
                PktMachineEditorRequirementAdd::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PktMachineEditorRequest.class,
                PktMachineEditorRequest::encode, PktMachineEditorRequest::decode, PktMachineEditorRequest::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PktMachineEditorResult.class,
                PktMachineEditorResult::encode, PktMachineEditorResult::decode, PktMachineEditorResult::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        Modularmachinery.LOG.info("[MMCE] network channel registered ({} packet type(s))", id);
    }

    /** One bounded chunk of the pack-author editor document, sent after the vanilla menu opens. */
    public record PktMachineEditorSnapshot(int containerId, UUID transfer, int totalBytes,
                                           int offset, byte[] data) {
        public static final int CHUNK_BYTES = 24 * 1024;
        public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;

        public PktMachineEditorSnapshot {
            if (containerId < 0 || transfer == null || totalBytes < 1
                    || totalBytes > MAX_SNAPSHOT_BYTES || offset < 0 || offset >= totalBytes
                    || offset % CHUNK_BYTES != 0
                    || data == null || data.length != Math.min(CHUNK_BYTES, totalBytes - offset)) {
                throw new IllegalArgumentException("Invalid machine editor snapshot chunk");
            }
        }

        public static void encode(PktMachineEditorSnapshot packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeUUID(packet.transfer);
            buffer.writeVarInt(packet.totalBytes);
            buffer.writeVarInt(packet.offset);
            buffer.writeByteArray(packet.data);
        }

        public static PktMachineEditorSnapshot decode(FriendlyByteBuf buffer) {
            return new PktMachineEditorSnapshot(buffer.readVarInt(), buffer.readUUID(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES));
        }

        public static void handle(PktMachineEditorSnapshot packet,
                                  Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.ClientMachineEditorSnapshot.receive(packet)));
            context.setPacketHandled(true);
        }
    }

    /** Server-authoritative structural edits made by the pack-author GUI. */
    public static final class PktMachineEditorAction {
        public static final byte DELETE_MACHINE = 1;
        public static final byte DELETE_RECIPE = 2;
        public static final byte DELETE_REQUIREMENT = 3;
        public static final byte ADD_ITEM_REQUIREMENT = 4;
        public static final byte SET_RECIPE_TIME = 5;
        public static final byte CREATE_RECIPE = 6;

        final byte action;
        final String machineName;
        final String recipeName;
        final int requirementIndex;
        final String value;
        final boolean output;

        public PktMachineEditorAction(byte action, String machineName, String recipeName,
                                      int requirementIndex, String value, boolean output) {
            this.action = action;
            this.machineName = machineName == null ? "" : machineName;
            this.recipeName = recipeName == null ? "" : recipeName;
            this.requirementIndex = requirementIndex;
            this.value = value == null ? "" : value;
            this.output = output;
        }

        public static void deleteMachine(String machineName) {
            send(new PktMachineEditorAction(DELETE_MACHINE, machineName, "", -1, "", false));
        }
        public static void deleteRecipe(String recipeName) {
            send(new PktMachineEditorAction(DELETE_RECIPE, "", recipeName, -1, "", false));
        }
        public static void deleteRequirement(String recipeName, int index) {
            send(new PktMachineEditorAction(DELETE_REQUIREMENT, "", recipeName, index, "", false));
        }
        public static void addItemRequirement(String recipeName, String itemId, boolean output) {
            send(new PktMachineEditorAction(ADD_ITEM_REQUIREMENT, "", recipeName, -1, itemId, output));
        }
        public static void setRecipeTime(String recipeName, int ticks) {
            send(new PktMachineEditorAction(SET_RECIPE_TIME, "", recipeName, -1, Integer.toString(ticks), false));
        }
        public static void createRecipe(String machineName, String recipeName) {
            send(new PktMachineEditorAction(CREATE_RECIPE, machineName, "", -1, recipeName, false));
        }
        private static void send(PktMachineEditorAction message) { CHANNEL.sendToServer(message); }

        public static void encode(PktMachineEditorAction message, FriendlyByteBuf buffer) {
            buffer.writeByte(message.action);
            buffer.writeUtf(message.machineName, 256);
            buffer.writeUtf(message.recipeName, 256);
            buffer.writeVarInt(message.requirementIndex);
            buffer.writeUtf(message.value, 256);
            buffer.writeBoolean(message.output);
        }
        public static PktMachineEditorAction decode(FriendlyByteBuf buffer) {
            return new PktMachineEditorAction(buffer.readByte(), buffer.readUtf(256), buffer.readUtf(256),
                    buffer.readVarInt(), buffer.readUtf(256), buffer.readBoolean());
        }
        public static void handle(PktMachineEditorAction message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("请使用新版编辑器重新提交修改"), true);
            });
            context.setPacketHandled(true);
        }

        static String apply(ServerPlayer player, PktMachineEditorAction message) {
            if (!player.hasPermissions(2)
                    || !(player.containerMenu instanceof hellfirepvp.modularmachinery.common.menu.MenuMachineEditor)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
            if (message.value.length() > 256 || message.requirementIndex < -1 || message.requirementIndex > 512) return "编辑数据无效、来源只读或文件不存在，未保存修改";
            try {
                java.io.File file;
                java.io.File root;
                if (message.action == DELETE_MACHINE) {
                    String source = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                            .getSourcePath(message.machineName);
                    file = source == null ? null : new java.io.File(source).getCanonicalFile();
                    root = hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE
                            .getMachineryDirectory().getCanonicalFile();
                    if (file == null || !file.toPath().startsWith(root.toPath()) || !file.isFile()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                    java.nio.file.Files.delete(file.toPath());
                } else if (message.action == CREATE_RECIPE) {
                    if (message.machineName.isBlank() || message.value.isBlank()
                            || hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                            .findByName(message.machineName) == null) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                    root = hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE
                            .getRecipeDirectory().getCanonicalFile();
                    String safeName = message.value.replaceAll("[^A-Za-z0-9_.-]", "_");
                    if (!safeName.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,120}")) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                    file = new java.io.File(root, safeName + ".json").getCanonicalFile();
                    if (!file.toPath().startsWith(root.toPath()) || file.exists()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                    com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                    json.addProperty("machine", message.machineName);
                    json.addProperty("registryName", safeName);
                    json.addProperty("recipeTime", 20);
                    json.add("requirements", new com.google.gson.JsonArray());
                    java.nio.file.Files.writeString(file.toPath(),
                            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json),
                            java.nio.charset.StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE_NEW);
                } else {
                    var recipes = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader();
                    if (!recipes.isSourceEditable(message.recipeName)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                    String source = recipes.getSourcePath(message.recipeName);
                    file = source == null ? null : new java.io.File(source).getCanonicalFile();
                    root = hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE
                            .getRecipeDirectory().getCanonicalFile();
                    if (file == null || !file.toPath().startsWith(root.toPath()) || !file.isFile()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                    if (message.action == DELETE_RECIPE) {
                        java.nio.file.Files.delete(file.toPath());
                    } else {
                        com.google.gson.JsonObject json;
                        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file.toPath(),
                                java.nio.charset.StandardCharsets.UTF_8)) {
                            json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        }
                        if (message.action == DELETE_REQUIREMENT) {
                            if (!json.has("requirements") || !json.get("requirements").isJsonArray()
                                    || message.requirementIndex < 0
                                    || message.requirementIndex >= json.getAsJsonArray("requirements").size()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                            if (json.getAsJsonArray("requirements").size() == 1) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                        "配方至少需要一个需求，请先添加替代需求，或删除整个配方"), true);
                                return "配方至少需要一个需求，请先添加替代需求，或删除整个配方";
                            }
                            json.getAsJsonArray("requirements").remove(message.requirementIndex);
                        } else if (message.action == ADD_ITEM_REQUIREMENT) {
                            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(message.value);
                            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                            com.google.gson.JsonObject requirement = new com.google.gson.JsonObject();
                            requirement.addProperty("type", "modularmachinery:item");
                            requirement.addProperty("io-type", message.output ? "output" : "input");
                            requirement.addProperty("item", id.toString());
                            requirement.addProperty("amount", 1);
                            requirement.addProperty("chance", 1.0F);
                            if (!json.has("requirements") || !json.get("requirements").isJsonArray())
                                json.add("requirements", new com.google.gson.JsonArray());
                            json.getAsJsonArray("requirements").add(requirement);
                        } else if (message.action == SET_RECIPE_TIME) {
                            int ticks;
                            try { ticks = Integer.parseInt(message.value); } catch (NumberFormatException e) { return "编辑数据无效、来源只读或文件不存在，未保存修改"; }
                            if (ticks < 1 || ticks > 2_000_000_000) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                            json.addProperty("recipeTime", ticks);
                        } else return "编辑数据无效、来源只读或文件不存在，未保存修改";
                        java.nio.file.Path temp = file.toPath().resolveSibling(file.getName() + ".mmce.tmp");
                        java.nio.file.Files.writeString(temp,
                                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json),
                                java.nio.charset.StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                        try {
                            java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                            java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("编辑已保存，正在重新加载"), true);
                hellfirepvp.modularmachinery.common.machine.MachineReloadListener.reloadNow();
                return null;
            } catch (Exception failure) {
                Modularmachinery.LOG.error("[MMCE] editor action failed", failure);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("编辑失败：" + failure.getMessage()), true);
                return "保存失败：" + failure.getMessage();
            }
        }
    }

    /** Source-compatible client request generated by the wooden-stick assembler. */
    public static final class PktAutoAssemblyRequest {
        private final BlockPos pos;
        private final int dynamicPatternSize;

        public PktAutoAssemblyRequest(BlockPos pos, int dynamicPatternSize) {
            this.pos = pos;
            this.dynamicPatternSize = dynamicPatternSize;
        }

        public static void send(BlockPos pos, int dynamicPatternSize) {
            CHANNEL.sendToServer(new PktAutoAssemblyRequest(pos, dynamicPatternSize));
        }

        public static void encode(PktAutoAssemblyRequest message, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(message.pos);
            buffer.writeVarInt(message.dynamicPatternSize);
        }

        public static PktAutoAssemblyRequest decode(FriendlyByteBuf buffer) {
            return new PktAutoAssemblyRequest(buffer.readBlockPos(), buffer.readVarInt());
        }

        public static void handle(PktAutoAssemblyRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                context.enqueueWork(() -> hellfirepvp.modularmachinery.common.event.AutoAssemblyEvents
                        .processAssemblyRequest(player.serverLevel(), player, message.pos,
                                Math.max(0, message.dynamicPatternSize)));
            }
            context.setPacketHandled(true);
        }
    }

    /** A validated edit from the pack-author visual editor. */
    public static final class PktMachineEditorEdit {
        final String recipeName;
        final int requirementIndex;
        final int amount;
        final float chance;
        final String itemId;

        public PktMachineEditorEdit(String recipeName, int requirementIndex, int amount,
                                    float chance, String itemId) {
            this.recipeName = recipeName == null ? "" : recipeName;
            this.requirementIndex = requirementIndex;
            this.amount = amount;
            this.chance = chance;
            this.itemId = itemId == null ? "" : itemId;
        }

        public static void send(String recipeName, int requirementIndex, int amount,
                                float chance, String itemId) {
            CHANNEL.sendToServer(new PktMachineEditorEdit(recipeName, requirementIndex, amount, chance, itemId));
        }

        public static void encode(PktMachineEditorEdit message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.recipeName, 256);
            buffer.writeVarInt(message.requirementIndex);
            buffer.writeVarInt(message.amount);
            buffer.writeFloat(message.chance);
            buffer.writeUtf(message.itemId, 256);
        }

        public static PktMachineEditorEdit decode(FriendlyByteBuf buffer) {
            return new PktMachineEditorEdit(buffer.readUtf(256), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readFloat(), buffer.readUtf(256));
        }

        public static void handle(PktMachineEditorEdit message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("请使用新版编辑器重新提交修改"), true);
            });
            context.setPacketHandled(true);
        }

        static String apply(ServerPlayer player, PktMachineEditorEdit message) {
            if (!player.hasPermissions(2)
                    || !(player.containerMenu instanceof hellfirepvp.modularmachinery.common.menu.MenuMachineEditor)) {
                return "编辑数据无效、来源只读或文件不存在，未保存修改";
            }
            if (message.requirementIndex < 0 || message.requirementIndex > 512
                    || message.amount < 1 || message.amount > 2_000_000_000
                    || !Float.isFinite(message.chance) || message.chance < 0F || message.chance > 1F) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("编辑值无效"), true);
                return "编辑数据无效、来源只读或文件不存在，未保存修改";
            }
            var recipes = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader();
            if (!recipes.isSourceEditable(message.recipeName)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "该配方来自适配器或脚本，必须修改其源文件"), true);
                return "编辑数据无效、来源只读或文件不存在，未保存修改";
            }
            String source = recipes.getSourcePath(message.recipeName);
            if (source == null || source.startsWith("internal/") || source.startsWith("CraftTweaker:")) return "编辑数据无效、来源只读或文件不存在，未保存修改";
            try {
                java.io.File file = new java.io.File(source).getCanonicalFile();
                java.io.File root = hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE
                        .getRecipeDirectory().getCanonicalFile();
                if (!file.toPath().startsWith(root.toPath()) || !file.isFile()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                com.google.gson.JsonObject json;
                try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
                if (!json.has("requirements") || !json.get("requirements").isJsonArray()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                var requirements = json.getAsJsonArray("requirements");
                if (message.requirementIndex >= requirements.size()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                com.google.gson.JsonObject requirement = requirements.get(message.requirementIndex).getAsJsonObject();
                String type = requirement.has("type") ? requirement.get("type").getAsString() : "";
                if (hellfirepvp.modularmachinery.common.editor.DurabilityEditor.isDurability(requirement))
                    return "请使用耐久需求编辑窗口提交修改";
                if (hellfirepvp.modularmachinery.common.editor.FuelEditor.isFuel(requirement))
                    return "请使用燃料需求编辑窗口提交修改";
                if (!"modularmachinery:item".equals(type) && !"modularmachinery:fluid".equals(type)
                        && !"modularmachinery:energy".equals(type)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "当前版本只允许编辑普通物品和流体需求"), true);
                    return "编辑数据无效、来源只读或文件不存在，未保存修改";
                }
                boolean energy = "modularmachinery:energy".equals(type);
                if ("modularmachinery:item".equals(type) && message.amount > 64) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "普通物品需求数量为 1～64，可通过批量倍率放大"), true);
                    return "普通物品需求数量为 1～64，可通过批量倍率放大";
                }
                if (!message.itemId.isBlank()) {
                    requirement = hellfirepvp.modularmachinery.common.editor.RequirementResourceSelection
                            .replace(requirement, message.itemId);
                    requirements.set(message.requirementIndex, requirement);
                }
                if (energy) requirement.addProperty("energyPerTick", message.amount);
                if (!energy) {
                    requirement.addProperty("amount", message.amount);
                    requirement.addProperty("chance", message.chance);
                }
                java.nio.file.Path temp = file.toPath().resolveSibling(file.getName() + ".mmce.tmp");
                String output = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json);
                java.nio.file.Files.writeString(temp, output, java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "已保存配方，正在重新加载"), true);
                hellfirepvp.modularmachinery.common.machine.MachineReloadListener.reloadNow();
                return null;
            } catch (IllegalArgumentException invalid) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(invalid.getMessage()), true);
                return invalid.getMessage();
            } catch (Exception failure) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.error("[MMCE] editor could not save recipe {}",
                        message.recipeName, failure);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("保存失败：" + failure.getMessage()), true);
                return "保存失败：" + failure.getMessage();
            }
        }
    }

    /** Saves a machine's literal localizedname field from the visual editor. */
    public static final class PktMachineEditorMachineEdit {
        final String machineName;
        final String localizedName;

        public PktMachineEditorMachineEdit(String machineName, String localizedName) {
            this.machineName = machineName == null ? "" : machineName;
            this.localizedName = localizedName == null ? "" : localizedName;
        }

        public static void send(String machineName, String localizedName) {
            CHANNEL.sendToServer(new PktMachineEditorMachineEdit(machineName, localizedName));
        }
        public static void encode(PktMachineEditorMachineEdit message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.machineName, 256);
            buffer.writeUtf(message.localizedName, 256);
        }
        public static PktMachineEditorMachineEdit decode(FriendlyByteBuf buffer) {
            return new PktMachineEditorMachineEdit(buffer.readUtf(256), buffer.readUtf(256));
        }
        public static void handle(PktMachineEditorMachineEdit message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("请使用新版编辑器重新提交修改"), true);
            });
            context.setPacketHandled(true);
        }
        static String apply(ServerPlayer player, PktMachineEditorMachineEdit message) {
            if (!player.hasPermissions(2)
                    || !(player.containerMenu instanceof hellfirepvp.modularmachinery.common.menu.MenuMachineEditor)
                    || message.machineName.isBlank() || message.localizedName.isBlank()
                    || message.localizedName.length() > 200) return "编辑数据无效、来源只读或文件不存在，未保存修改";
            try {
                String source = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                        .getSourcePath(message.machineName);
                java.io.File file = source == null ? null : new java.io.File(source).getCanonicalFile();
                java.io.File root = hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE
                        .getMachineryDirectory().getCanonicalFile();
                if (file == null || !file.toPath().startsWith(root.toPath()) || !file.isFile()) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                com.google.gson.JsonObject json;
                try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
                json.addProperty("localizedname",
                        hellfirepvp.modularmachinery.common.editor.MachineNameFormatting.display(message.localizedName));
                java.nio.file.Path temp = file.toPath().resolveSibling(file.getName() + ".mmce.tmp");
                java.nio.file.Files.writeString(temp,
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json),
                        java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("已保存机器名称，正在重新加载"), true);
                hellfirepvp.modularmachinery.common.machine.MachineReloadListener.reloadNow();
                return null;
            } catch (Exception failure) {
                hellfirepvp.modularmachinery.Modularmachinery.LOG.error("[MMCE] editor could not save machine {}",
                        message.machineName, failure);
                return "保存失败：" + failure.getMessage();
            }
        }
    }

    /** Client-to-server: change a parallel controller's parallelism. */
    public static final class PktSetParallelism {

        private final BlockPos pos;
        private final int value;

        public PktSetParallelism(BlockPos pos, int value) {
            this.pos = pos;
            this.value = value;
        }

        /**
         * Clamps a requested parallelism to what the controller actually permits.
         *
         * <p>Extracted and made static so it can be asserted directly. The rule is the security-relevant
         * one in this packet — a modified client sending an out-of-range value must be corrected, not
         * obeyed — and a rule that can only be exercised by connecting a real client is a rule nobody
         * will ever test.
         *
         * @return the requested value, held within {@code [1, max]}
         *
         * <p><b>The floor is 1, not 0</b> (PLAN section 250). Zero is not a usable parallelism: the
         * controller would exist and be unable to run anything. The client reported the value reaching 0 and
         * below, which is a state the block cannot meaningfully be in -- so 0 is treated as out of range, the
         * same way a negative is.
         */
        public static int clampParallelism(int requested, int max) {
            // The ceiling is at least 1 even if max is 0, so the floor and the ceiling cannot cross and the
            // result is always inside [1, ceiling].
            int ceiling = Math.max(1, max);
            return Math.min(Math.max(requested, 1), ceiling);
        }

        /** Sends this change to the server. Called from the screen, on the client. */
        public static void send(BlockPos pos, int value) {
            CHANNEL.sendToServer(new PktSetParallelism(pos, value));
        }

        public static void encode(PktSetParallelism msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.value);
        }

        public static PktSetParallelism decode(FriendlyByteBuf buf) {
            return new PktSetParallelism(buf.readBlockPos(), buf.readVarInt());
        }

        /**
         * Applies the change on the server thread.
         *
         * <p>{@code enqueueWork} rather than acting immediately: the packet arrives on a network thread,
         * and touching a block entity from there is a data race against the server tick. This is the
         * single most common way a mod's networking corrupts world state.</p>
         *
         * <p>Its body is {@link #apply}, extracted for the reason
         * {@link PktSmartInterfaceUpdate#apply} states: the write half of this feature can then be driven
         * without a connected client, and the self-test does drive it — that is how "the tile was always
         * right" was established for defect A rather than assumed.</p>
         */
        public static void handle(PktSetParallelism msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;   // a client-only send; nothing to apply
                }
                apply((net.minecraft.server.level.ServerLevel) player.level(), player, msg.pos, msg.value);
            });
            context.setPacketHandled(true);
        }

        /**
         * Applies a requested parallelism, re-deriving everything from the world.
         *
         * <p><b>EXTRACTED AND STATIC SO THE RULES CAN BE ASSERTED WITHOUT A CONNECTED CLIENT</b>, the
         * convention {@link #clampParallelism} established in this class and
         * {@link PktSmartInterfaceUpdate#apply} / {@link PktMEFluidInputBusConfig#apply} repeated. It is not
         * a test-only entry point: it IS the handler's body, so what the self-test drives is what a player's
         * client drives.</p>
         *
         * @return true when the value was applied to the controller
         */
        public static boolean apply(net.minecraft.server.level.ServerLevel level, ServerPlayer player,
                                    BlockPos pos, int value) {
            if (level == null || player == null) {
                return false;
            }
            if (!(level.getBlockEntity(pos) instanceof TileParallelController ctrl)) {
                // Broken or replaced while the screen was open. Silently ignoring is right: the client
                // will find out through stillValid on its next tick, and there is nothing to apply.
                return false;
            }
            // Re-check range here. The menu's stillValid runs on the server each tick, but a packet
            // can arrive between ticks from a client that has already moved out of range.
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return false;
            }
            // Clamp to the controller's real maximum rather than trusting the value. A modified client
            // sending Integer.MAX_VALUE would otherwise multiply the machine's throughput arbitrarily.
            ctrl.setParallelism(clampParallelism(value, ctrl.getMaxParallelism()));
            return true;
        }
    }
    /**
     * Client-to-server: configure a machine component as a group input.
     *
     * <p>Port of upstream's {@code PktGroupInputConfig} (§155.4). <b>One deliberate difference:</b> upstream's
     * packet carries no position — it applies to whatever container the player has open — and this one carries
     * {@link BlockPos} and re-derives the target from the world, following {@link PktSetParallelism} and the
     * rule stated at the top of this class: *treat the client as untrusted and re-derive rather than applying
     * what was sent.*
     *
     * <p>Upstream also splits the change across two constructors discriminated by a {@code byte}; this sends
     * both fields in one message, because a configuration screen sets both at once and a discriminator would
     * be a second way for the two sides to disagree.
     */
    public static final class PktGroupInputConfig {

        private final BlockPos pos;
        private final int groupId;
        private final boolean groupInput;

        public PktGroupInputConfig(BlockPos pos, int groupId, boolean groupInput) {
            this.pos = pos;
            this.groupId = groupId;
            this.groupInput = groupInput;
        }

        /**
         * Holds a requested group id within the range the interface can honour.
         *
         * <p>Extracted and static so the rule can be asserted without a connected client, for the reason
         * {@link PktSetParallelism#clampParallelism} gives: a rule only exercisable by a real client is a rule
         * nobody tests. A negative id is meaningless — 0 already means "unset" — and a modified client sending
         * {@code Integer.MAX_VALUE} must be corrected rather than obeyed.
         *
         * @return the requested id, or 0 when it is negative
         */
        public static int clampGroupId(int requested) {
            return Math.max(0, requested);
        }

        /** Sends this change to the server. Called from the screen, on the client. */
        public static void send(BlockPos pos, int groupId, boolean groupInput) {
            CHANNEL.sendToServer(new PktGroupInputConfig(pos, groupId, groupInput));
        }

        public static void encode(PktGroupInputConfig msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.groupId);
            buf.writeBoolean(msg.groupInput);
        }

        public static PktGroupInputConfig decode(FriendlyByteBuf buf) {
            return new PktGroupInputConfig(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean());
        }

        /** Applies the change on the server thread; see {@link PktSetParallelism#handle} for the why. */
        public static void handle(PktGroupInputConfig msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                if (!(player.level().getBlockEntity(msg.pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineGroupInput machine)) {
                    // Broken, replaced, or simply not a group-input-capable component. Ignoring is right: the
                    // menu's stillValid will close the screen on its next tick.
                    return;
                }
                if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5,
                        msg.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                // Re-derive the capability rather than trusting the message: a component that cannot group
                // input must not be switched on by a modified client.
                machine.setGroupInput(msg.groupInput && machine.canGroupInput());
                machine.setGroupId(clampGroupId(msg.groupId));
            });
            context.setPacketHandled(true);
        }
    }
    /**
     * Client-to-server: open the group-input configuration screen for a component at {@code pos}.
     *
     * <p><b>Why this packet exists at all.</b> §166 found that {@code MenuGroupInputConfig} had no entry point
     * — a container, a screen, a packet and an interface that were all complete and all unreachable, because
     * nothing opened it. A menu type being registered is not the same as a menu being openable, and no check in
     * this port drew that distinction.
     *
     * <p>Upstream opens the config from a component's own GUI (there is no group-input block — §157), so this
     * is the client half of that: a button sends the position, and the server decides.
     *
     * <p>Same discipline as the other two packets: re-derive the target, check it implements
     * {@link hellfirepvp.modularmachinery.common.tiles.base.MachineGroupInput}, and check the range.
     */
    public static final class PktOpenGroupInputConfig {

        private final BlockPos pos;

        public PktOpenGroupInputConfig(BlockPos pos) {
            this.pos = pos;
        }

        /** Sends the request. Called from the component screen, on the client. */
        public static void send(BlockPos pos) {
            CHANNEL.sendToServer(new PktOpenGroupInputConfig(pos));
        }

        public static void encode(PktOpenGroupInputConfig msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
        }

        public static PktOpenGroupInputConfig decode(FriendlyByteBuf buf) {
            return new PktOpenGroupInputConfig(buf.readBlockPos());
        }

        public static void handle(PktOpenGroupInputConfig msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                if (!(player.level().getBlockEntity(msg.pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineGroupInput)) {
                    return;   // not a group-input component; nothing to configure
                }
                if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5,
                        msg.pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                net.minecraftforge.network.NetworkHooks.openScreen(player,
                        new net.minecraft.world.SimpleMenuProvider(
                                (windowId, inventory, ignored) ->
                                        new hellfirepvp.modularmachinery.common.menu.MenuGroupInputConfig(
                                                windowId, inventory, msg.pos),
                                net.minecraft.network.chat.Component.translatable(
                                        "gui.groupinputconfig.title")),
                        msg.pos);
            });
            context.setPacketHandled(true);
        }
    }

    /**
     * Server-to-client: the authoring player's two selection corners, so their client can draw the box
     * between them, or outline the one corner marked so far.
     *
     * <p>Port of upstream's {@code PktSyncSelection} (64 lines, upstream L28-L64). Upstream's own
     * {@code fromBytes} wrote a count and then three ints per position and its {@code toBytes} read them
     * back the same way; this is that shape over {@link FriendlyByteBuf}, with the payload changed from a
     * position list to <b>two nullable corners</b> when the selection became a box (PLAN 655.2, and see
     * {@link ModularmachineryNetwork#PROTOCOL} for why the version moved with it). Two nullable positions
     * are strictly less than the list they replace and cannot express a volume the server did not mean:
     * the client derives the same cuboid from the same pair, through
     * {@link hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper#boxPositions}.
     *
     * <p><b>This is the channel's only SERVER-TO-CLIENT packet</b>, and it is the one place this class's
     * server-authority rule does not apply — not because the rule is suspended, but because it is
     * satisfied differently. The other three re-derive the target from the world and validate before
     * applying because they CHANGE the world, so a modified client could otherwise use them as a
     * remote-control for other players' machines. This one changes nothing: it writes one static value on
     * the client that asked for it, the server computes it from its own view of the world, and the client
     * has no way to put anything into it. There is no position to validate and no range to check, and the
     * recipient is chosen with {@code PacketDistributor.PLAYER} rather than broadcast: a selection is one
     * player's private sketch, not a state of the world.
     *
     * <p>Upstream's handler (upstream L59-L62) replaced the client's selection with a new one from the
     * packet, and this does the same — the pair is the whole state, so a merge would be a second
     * representation of it. <b>A null corner is a value, not an absence</b>: "no second corner yet" is the
     * normal state after one click, and the packet has to be able to say it.
     */
    public static final class PktSyncSelection {

        private final @Nullable BlockPos firstCorner;
        private final @Nullable BlockPos secondCorner;

        public PktSyncSelection(@Nullable BlockPos firstCorner, @Nullable BlockPos secondCorner) {
            this.firstCorner = firstCorner == null ? null : firstCorner.immutable();
            this.secondCorner = secondCorner == null ? null : secondCorner.immutable();
        }

        public static void encode(PktSyncSelection msg, FriendlyByteBuf buf) {
            // THE PARAMETER ORDER IS THE BUFFER FIRST, and that is worth stating because it is the opposite
            // of what the two lambdas in the first draft assumed: Forge declares
            // `Writer<T> extends BiConsumer<FriendlyByteBuf, T>` (read from the mapped 1.20.1 jar), so the
            // functional descriptor is (buf, value) and the method reference form binds the buffer as its
            // receiver. The compiler caught the wrong order; this comment is so the next reader does not
            // have to.
            buf.writeNullable(msg.firstCorner, FriendlyByteBuf::writeBlockPos);
            buf.writeNullable(msg.secondCorner, FriendlyByteBuf::writeBlockPos);
        }

        public static PktSyncSelection decode(FriendlyByteBuf buf) {
            return new PktSyncSelection(
                    buf.readNullable(FriendlyByteBuf::readBlockPos),
                    buf.readNullable(FriendlyByteBuf::readBlockPos));
        }

        /**
         * Applies the selection on the thread the packet arrived on, deliberately.
         *
         * <p>The other three handlers wrap their work in {@code context.enqueueWork} because they touch
         * block entities, and a block entity touched from a network thread is a data race against the
         * server tick — the reason is stated on {@link PktSetParallelism#handle}. This one touches a
         * single static field that only this mod's renderer reads, and the renderer runs on the same
         * client thread the handler is already on. Enqueuing it would only move the assignment to later
         * in the tick while the field is still read on that thread, so it would buy nothing and cost a
         * tick of latency on a box the player is drawing.
         */
        public static void handle(PktSyncSelection msg, Supplier<NetworkEvent.Context> ctx) {
            hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper.clientSelection =
                    new hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper
                            .StructureSelection(msg.firstCorner, msg.secondCorner);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Client-to-server: set a smart interface's published value.
     *
     * <p>Port of upstream's {@code PktSmartInterfaceUpdate} (60 lines), which is the WRITE half of the smart
     * interface channel: the interface's value is what a recipe requirement reads, and until a player can
     * change it the requirement could only ever see the value the machine's definition declared. Kept: the
     * name, the direction, the payload's three parts (which interface, which type, which value), the "only an
     * EXISTING datum is updated" rule, the event fire and the client sync underneath it.</p>
     *
     * <h2>RULE A0 — two structural differences, both this channel's own convention rather than a preference</h2>
     *
     * <p><b>1. IT IS NESTED, LIKE THE CHANNEL'S OTHER FOUR PACKETS.</b> Upstream keeps one class per file and
     * registers them by class; this port's channel owns its packets (see the class javadoc) — all four before
     * this one are nested, and a fifth in its own file would be the second convention for one rule.</p>
     *
     * <p><b>2. THE PAYLOAD CARRIES THE INTERFACE POSITION, AND THE VALIDATION IS RE-DERIVED FROM THE WORLD.</b>
     * Upstream's packet carries a {@code SmartInterfaceData} whose {@code pos} is the CONTROLLER's position, and
     * finds the interface it is editing through the player's OPEN CONTAINER
     * ({@code player.openContainer instanceof ContainerSmartInterface}, U44-U46). It can do that because its
     * datum carries a position at all; this port's datum does not (see {@code TileSmartInterface}'s javadoc), so
     * the target travels in the packet instead — and the open-container rule is kept as an ADDITIONAL check
     * rather than replaced by the position, because "the client sent a position" is not authorization
     * (a modified client could send any interface's position in range). Both must hold: the interface exists at
     * that position, AND the player has that interface's screen open.</p>
     */
    public static final class PktSmartInterfaceUpdate {

        private final BlockPos interfacePos;
        private final String type;
        private final float value;

        public PktSmartInterfaceUpdate(BlockPos interfacePos, String type, float value) {
            this.interfacePos = interfacePos.immutable();
            this.type = type;
            this.value = value;
        }

        /** Sends this change to the server. Called from the smart interface screen, on the client. */
        public static void send(BlockPos interfacePos, String type, float value) {
            CHANNEL.sendToServer(new PktSmartInterfaceUpdate(interfacePos, type, value));
        }

        public static void encode(PktSmartInterfaceUpdate msg, FriendlyByteBuf buf) {
            // The buffer is the FIRST parameter: Forge declares Writer<T> extends BiConsumer<FriendlyByteBuf, T>
            // (PLAN 655.2(b)), so a method reference binds the buffer as its receiver.
            buf.writeBlockPos(msg.interfacePos);
            buf.writeUtf(msg.type);
            buf.writeFloat(msg.value);
        }

        public static PktSmartInterfaceUpdate decode(FriendlyByteBuf buf) {
            return new PktSmartInterfaceUpdate(buf.readBlockPos(), buf.readUtf(), buf.readFloat());
        }

        public static void handle(PktSmartInterfaceUpdate msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;   // a client-only send; nothing to apply
                }
                apply((net.minecraft.server.level.ServerLevel) player.level(), player,
                        msg.interfacePos, msg.type, msg.value);
            });
            context.setPacketHandled(true);
        }

        /**
         * Applies a requested value, re-deriving everything from the world.
         *
         * <p><b>EXTRACTED AND STATIC SO THE RULES CAN BE ASSERTED WITHOUT A CONNECTED CLIENT</b>, which is the
         * convention {@code PktSetParallelism.clampParallelism} established in this channel: a rule that can
         * only be exercised by a live client is a rule nobody tests. The self-test drives exactly this method
         * with a fake player and a real open menu, so the whole authorization path runs — it is not a test-only
         * entry point, it is the handler's own body.</p>
         *
         * <p>Upstream's three steps, in its own order (U49-U55): look the interface up, look the DATUM up by
         * type and update only if it exists, then fire the event and mark the tile for the clients. A datum that
         * does not exist means the machine never declared that type, and creating one here would publish a value
         * no requirement could ever name.</p>
         *
         * @return true when the value was applied
         */
        public static boolean apply(net.minecraft.server.level.ServerLevel level, ServerPlayer player,
                                    BlockPos interfacePos, String type, float value) {
            if (level == null || player == null || type == null) {
                return false;
            }
            // The menu rule, upstream U44-U46, adapted: upstream infers the target from the open container, so
            // the container IS the authorization; here the target travels in the packet, and the container says
            // whether the player is entitled to edit it.
            if (!(player.containerMenu
                    instanceof hellfirepvp.modularmachinery.common.menu.MenuSmartInterface menu)
                    || !menu.getInterfacePos().equals(interfacePos)) {
                return false;
            }
            // Range, as every other packet in this channel checks: the menu's stillValid runs once a tick, and
            // a packet can arrive between ticks from a client that has already walked away.
            if (player.distanceToSqr(interfacePos.getX() + 0.5, interfacePos.getY() + 0.5,
                    interfacePos.getZ() + 0.5) > 64.0) {
                return false;
            }
            if (!(level.getBlockEntity(interfacePos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.TileSmartInterface iface)) {
                return false;   // broken or replaced while the screen was open
            }

            hellfirepvp.modularmachinery.common.tiles.TileSmartInterface.SmartInterfaceProvider provider =
                    iface.provideComponent();
            hellfirepvp.modularmachinery.common.tiles.TileSmartInterface.Data data =
                    provider.getMachineData(type);
            if (data == null) {
                // Upstream U52: only a datum that already exists is updated. The type must be one the machine
                // declares and the interface already publishes.
                return false;
            }
            data.setNumber(value);
            hellfirepvp.modularmachinery.common.tiles.TileSmartInterface.onDataUpdate(iface, data);
            // Upstream's markForUpdateSync: the screen reads the value from the synced tag, so without this the
            // number a player just typed would not come back to them.
            iface.markForUpdate();
            return true;
        }
    }

    /**
     * Client-to-server: configure one tank of the ME fluid input bus the player has open.
     *
     * <p><b>Why this packet is the port's own rather than a port of upstream's.</b> Upstream does not have one: AE2 1.12.2's
     * fluid-config containers move a config slot's contents down AE2's own channel ({@code FluidSyncHelper} over
     * {@code IFluidSyncContainer}), and that whole package is gone from AE2 15.4.11 — see {@code MenuMEFluidInputBus}'s rule A0
     * record. The port's equivalent of "AE2's own channel" is <b>this channel</b>, so the write is a packet here, following
     * {@link PktSmartInterfaceUpdate} for shape and {@link PktSetParallelism} for the server-authority rules.</p>
     *
     * <h2>The payload, and which half of it is authoritative</h2>
     *
     * <p>Three fields: the bus position, the config slot index, and the fluid the client claims it is configuring. <b>The fluid
     * is the only field that could have been dropped, and the reason it is kept is that the packet must be able to express
     * say <b>"clear this slot"</b>.</p>
     *
     * <p><b>THE TWO ENTRY POINTS SHARE ONE WRITE PATH BUT NOT ONE VALUE RULE, AND AN EARLIER VERSION OF THIS COMMENT
     * CLAIMED OTHERWISE (PLAN 671).</b> {@code ConfigSlot} has two {@code configureFrom} arities and each entry point uses
     * exactly one of them:</p>
     *
     * <pre>
     *   MenuMEFluidInputBus.clicked  ->  configureFrom(Player)             RE-DERIVES the fluid from what the player is
     *                                                                      carrying, which is what a screen click means
     *   this packet apply            ->  configureFrom(Player, FluidStack) USES THE PAYLOAD, and never looks at the
     *                                                                      carried item at all
     * </pre>
     *
     * <p>SO IT IS NOT "re-derive first, fall back to the claim". The payload is the whole story on this path, and that is
     * deliberate: if {@code apply} also consulted the carried container, the payload field would NEVER TAKE EFFECT, and it
     * is the only source that write has. <b>WHAT THE TWO ARITIES DO SHARE IS WHERE THE WRITE LANDS</b> -- the same
     * {@code setConfiguredFluid} call, the same slot resolution, the same distance and open-menu guards -- WHICH IS WHAT
     * KEEPS THEM FROM DISAGREEING ABOUT WHAT A CONFIGURATION IS.</p>
     *
     * <p><b>What a modified client can and cannot do.</b> It cannot touch another player's bus (the position must be the one the
     * player's own open menu names, re-derived through {@code MenuMEFluidInputBus.isOpenFor(...)}, not taken from the payload),
     * cannot reach past its own screen's range (the 64-block check every packet in this channel makes), cannot configure a slot
     * that is not a ghost config slot, and cannot choose an amount (the tile normalises it to the tank's capacity). What it CAN
     * do is name a fluid it is not holding, which is exactly the capability it already has by holding a bucket of that fluid —
     * this is a filter on the player's own machine, not a privilege.</p>
     */
    public static final class PktMEFluidInputBusConfig {

        /** Sentinel for "no fluid" on the wire. Zero is a valid registry id, so zero cannot mean absence. */
        private static final int FLUID_NONE = -1;

        private final BlockPos pos;
        private final int slot;
        private final FluidStack fluid;

        public PktMEFluidInputBusConfig(BlockPos pos, int slot, FluidStack fluid) {
            this.pos = pos.immutable();
            this.slot = slot;
            this.fluid = fluid == null ? FluidStack.EMPTY : fluid.copy();
        }

        /** Sends this change to the server. Called from the config slot, on the client. */
        public static void send(BlockPos pos, int slot, FluidStack fluid) {
            CHANNEL.sendToServer(new PktMEFluidInputBusConfig(pos, slot, fluid));
        }

        public static void encode(PktMEFluidInputBusConfig msg, FriendlyByteBuf buf) {
            // The buffer is the FIRST parameter: Forge declares Writer<T> extends BiConsumer<FriendlyByteBuf, T>
            // (PLAN 655.2(b)), so a method reference binds the buffer as its receiver.
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.slot);
            writeFluid(buf, msg.fluid);
        }

        public static PktMEFluidInputBusConfig decode(FriendlyByteBuf buf) {
            return new PktMEFluidInputBusConfig(buf.readBlockPos(), buf.readVarInt(), readFluid(buf));
        }

        /**
         * A fluid on the wire, and WHY IT IS NOT {@code buf.writeFluidStack}.
         *
         * <p><b>Read from the mapped 1.20.1 class, not from memory:</b> {@code javap -p} on
         * {@code net.minecraft.network.FriendlyByteBuf} out of the joined 1.20.1 jar lists no fluid method at all — no
         * {@code writeFluidStack}, no {@code readFluidStack}. A fluid's identity is therefore written by hand, as the pair the
         * registry already uses: its registry id and its amount.</p>
         *
         * <p>The amount is the sentinel: a non-positive amount means "this configuration is being CLEARED", which is a value
         * the packet has to be able to express — see the class javadoc. Zero cannot be used, because a real fluid's registry id
         * can be zero.</p>
         */
        public static void writeFluid(FriendlyByteBuf buf, FluidStack fluid) {
            if (fluid == null || fluid.isEmpty()) {
                buf.writeVarInt(FLUID_NONE);
                buf.writeVarInt(0);
                return;
            }
            buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(fluid.getFluid()));
            buf.writeVarInt(fluid.getAmount());
        }

        /** @return the fluid on the wire, or {@link FluidStack#EMPTY} for the clear sentinel or an unresolvable id */
        public static FluidStack readFluid(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int amount = buf.readVarInt();
            if (id == FLUID_NONE || amount <= 0) {
                return FluidStack.EMPTY;
            }
            net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(id);
            if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(fluid, amount);
        }

        public static void handle(PktMEFluidInputBusConfig msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;   // a client-only send; nothing to apply
                }
                apply(player, msg.pos, msg.slot, msg.fluid);
            });
            context.setPacketHandled(true);
        }

        /**
         * Applies a configuration request, re-deriving everything from the world and the player's own open menu.
         *
         * <p><b>EXTRACTED AND STATIC SO THE RULES CAN BE ASSERTED WITHOUT A CONNECTED CLIENT</b>, which is the convention
         * {@code PktSetParallelism.clampParallelism} established in this channel and {@code PktSmartInterfaceUpdate.apply}
         * repeated: a rule that can only be exercised by a live client is a rule nobody tests. The self-test drives exactly this
         * method with a fake server player and a real open menu, so the whole authorization path runs.</p>
         *
         * <p><b>The slot lookup goes THROUGH the menu rather than recomputing an index</b>
         * ({@code MenuMEFluidInputBus.configSlotAt}), because the menu's own slot list is the only thing that knows where its
         * config slots are — a {@code slot < 9} test here would be a second copy of a layout rule, which is §629's shape.</p>
         *
         * @return true when the write reached the tile
         */
        public static boolean apply(ServerPlayer player, BlockPos pos, int slot, FluidStack fluid) {
            net.minecraft.world.inventory.AbstractContainerMenu open = player.containerMenu;
            if (!hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus.isOpenFor(open, pos)) {
                // The message names a bus the player does not have open. Ignoring is right rather than correcting:
                // there is no bus this request could be redirected to.
                return false;
            }
            hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus menu =
                    (hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus) open;
            hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus.ConfigSlot config = menu.configSlotAt(slot);
            if (config == null) {
                // Not one of the nine config slots -- a player inventory index, or out of range entirely.
                return false;
            }
            return config.configureFrom(player, fluid);
        }
    }

    /**
     * Client-to-server: the ME pattern provider's two action buttons, upstream's {@code PktMEPatternProviderAction}
     * (upstream L12-L60).
     *
     * <h2>RULE A0 -- THE ONE DELIBERATE DIFFERENCE FROM UPSTREAM, with its three parts</h2>
     *
     * <p><b>(1) UPSTREAM:</b> {@code toBytes} writes {@code action.ordinal()} as one byte and nothing else
     * (upstream L29-L31); the handler reads {@code ctx.getServerHandler().player}, checks that player's
     * {@code openContainer} is a {@code ContainerMEPatternProvider} (L36), and then applies the action to
     * {@code patternProvider.getOwner()} -- <b>the container's own field, not a position</b> (L40-L47). Its
     * {@code Action} enum is {@code ENABLE_BLOCKING_MODE, ENABLE_DEFAULT_MODE, ENABLE_CRAFTING_LOCK_MODE,
     * ENABLE_ENHANCED_BLOCKING_MODE, ENABLE_ENHANCED_ISOLATION_INPUT, RETURN_ITEMS} (L53-L60), and each of the first
     * five maps to one {@code setWorkMode} call.</p>
     *
     * <p><b>(2) WHY THAT EXACT PAYLOAD CANNOT BE USED HERE:</b> the ordinal it writes is the ordinal of UPSTREAM'S
     * enum, and this port's enum is a different type in a different package -- so the same byte would be read by a
     * different {@code values()} array, and the two orders happening to agree today is not a rule either side
     * enforces. This channel's own stated rule is the other half: <i>"treat the client as untrusted and re-derive from
     * the world rather than applying what was sent"</i> ({@code PktSetParallelism}'s javadoc). Upstream's container
     * reference IS a re-derivation (the server looks up its own open container); a position plus that same lookup is
     * the same guarantee in this port's own convention.</p>
     *
     * <p><b>(3) WHAT THIS DOES INSTEAD:</b> the payload is a {@link BlockPos} plus TWO varints -- the action, and the
     * WORK MODE ORDINAL the action carries -- read back through
     * {@code WorkModeSetting.fromOrdinal}, <b>the port's own enum, never a literal</b>, and the
     * position is checked against the player's own open menu ({@code MenuMEPatternProvider.isOpenFor}) before anything
     * is touched. A mode this port does not have resolves to {@code DEFAULT}, which is upstream's own default, so the
     * payload cannot name a constant that does not exist.</p>
     *
     * <p><b>The second varint is upstream's own field, carried rather than derived.</b> Upstream's byte IS its mode
     * (its five {@code ENABLE_} cases each name one constant); splitting the action from the mode keeps that byte's
     * meaning while letting ONE action serve all five, and it means the mode travels on the wire instead of the server
     * having to infer which one was clicked from a state the client may have read a tick late.</p>
     *
     * <p><b>AND THE WORK MODE IS NOT A "BUTTON ID".</b> The screen does not send "the button was clicked, cycle"; it
     * sends the mode it means, which is what makes the server-side value re-derivable and independently testable --
     * {@code apply} can be driven with any mode, and the self-test drives it with one that is NOT the default.</p>
     */
    public static final class PktMEPatternProviderAction {

        private final BlockPos pos;
        private final int action;
        private final int workMode;

        public PktMEPatternProviderAction(BlockPos pos, int action) {
            this(pos, action, 0);
        }

        public PktMEPatternProviderAction(BlockPos pos, int action, int workMode) {
            this.pos = pos;
            this.action = action;
            this.workMode = workMode;
        }

        /** @return the payload for "set this provider's work mode", the action the mode button sends */
        public static PktMEPatternProviderAction workMode(BlockPos pos, int modeOrdinal) {
            return new PktMEPatternProviderAction(pos, SET_WORK_MODE, modeOrdinal);
        }

        /** @return the payload for "return the independent storage to the ME network", the first button */
        public static PktMEPatternProviderAction returnItems(BlockPos pos) {
            return new PktMEPatternProviderAction(pos, RETURN_ITEMS);
        }

        /** Upstream's {@code Action.RETURN_ITEMS} (L59): push what the provider holds back into the grid. */
        public static final int RETURN_ITEMS = 0;

        /**
         * The five {@code ENABLE_*} actions of upstream's enum (L54-L58) collapse into ONE here.
         *
         * <p>Upstream needs five because its {@code setWorkMode} call sites are five separate {@code case} labels
         * naming five constants. This port carries the mode in the payload, so the five are one action plus an
         * ordinal -- <b>a smaller enum that covers the same five outcomes without a switch that has to be extended
         * whenever the mode list grows.</b></p>
         */
        public static final int SET_WORK_MODE = 1;

        /** Sends a mode change. Called from the screen, on the client. */
        public static void sendWorkMode(BlockPos pos, int modeOrdinal) {
            CHANNEL.sendToServer(new PktMEPatternProviderAction(pos, SET_WORK_MODE, modeOrdinal));
        }

        /** Asks the server to return the provider's contents. Called from the screen, on the client. */
        public static void sendReturnItems(BlockPos pos) {
            CHANNEL.sendToServer(returnItems(pos));
        }

        public static void encode(PktMEPatternProviderAction msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.action);
            buf.writeVarInt(msg.workMode);
        }

        public static PktMEPatternProviderAction decode(FriendlyByteBuf buf) {
            return new PktMEPatternProviderAction(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }

        /** Applies the change on the server thread; see {@link PktSetParallelism#handle} for the why. */
        public static void handle(PktMEPatternProviderAction msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;   // a client-only send; nothing to apply
                }
                apply((net.minecraft.server.level.ServerLevel) player.level(), player, msg.pos, msg.action,
                        msg.workMode);
            });
            context.setPacketHandled(true);
        }

        /**
         * Applies an action, re-deriving the target from the world and the player's own open menu.
         *
         * <p><b>EXTRACTED AND STATIC SO THE RULES CAN BE ASSERTED WITHOUT A CONNECTED CLIENT</b>, the convention every
         * other packet in this class follows ({@code PktSetParallelism.apply}, {@code PktSmartInterfaceUpdate.apply},
         * {@code PktMEFluidInputBusConfig.apply}). The self-test opens a real menu for a fake server player and drives
         * this method, so what it measures is the handler's body rather than a copy of it.</p>
         *
         * <p><b>THE MENU CHECK IS THE AUTHORIZATION RULE, and it is not decoration:</b> without it any client could
         * set any provider's mode or empty it into the grid from anywhere in the world. Upstream's guard is the same
         * question asked of its own container field (L36).</p>
         *
         * @param modeOrdinal the mode to set, when {@code action} is {@link #SET_WORK_MODE}; ignored otherwise
         * @return true when the tile was reached and changed
         */
        public static boolean apply(net.minecraft.server.level.ServerLevel level, ServerPlayer player,
                                    BlockPos pos, int action, int modeOrdinal) {
            if (level == null || player == null) {
                return false;
            }
            net.minecraft.world.inventory.AbstractContainerMenu open = player.containerMenu;
            if (!hellfirepvp.modularmachinery.common.menu.MenuMEPatternProvider.isOpenFor(open, pos)) {
                return false;
            }
            if (!(level.getBlockEntity(pos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternProvider provider)) {
                // Broken or replaced while the screen was open. Silently ignoring is right: the client finds out
                // through stillValid on its next tick, and there is nothing to apply.
                return false;
            }
            switch (action) {
                case SET_WORK_MODE -> {
                    // THE MODE IS READ FROM THE ENUM, NOT FROM A LITERAL: `fromOrdinal` is the same reader load() uses,
                    // so the packet path and the save path cannot disagree about what a byte means.
                    hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting mode =
                            hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting
                                    .fromOrdinal(modeOrdinal);
                    provider.setWorkMode(mode);
                    return provider.getWorkMode() == mode;
                }
                case RETURN_ITEMS -> {
                    provider.returnToNetwork();
                    return true;
                }
                default -> {
                    // An action this half does not know. Refusing rather than guessing keeps a future payload from
                    // silently becoming a no-op that looks like success.
                    return false;
                }
            }
        }

        /**
         * The overload the wire handler and a self-test both use, so there is one body rather than two.
         *
         * @param modeOrdinal the mode to set, when {@code action} is {@link #SET_WORK_MODE}; ignored otherwise
         */
        public static boolean apply(ServerPlayer player, BlockPos pos, int action, int modeOrdinal) {
            return apply(player == null ? null
                            : (net.minecraft.server.level.ServerLevel) player.level(),
                    player, pos, action, modeOrdinal);
        }
    }

    /**
     * Client-to-server: the ME ITEM OUTPUT bus's stack-size cap (AUDIT_COMPONENTS row 11).
     *
     * <h2>What upstream sends, and why this is a delta rather than a value</h2>
     *
     * <p>Upstream's stack-size GUI sends {@code PktMEOutputBusStackSizeChange(pos, stackSize)} — an ABSOLUTE value the
     * player typed or built with the eight +/- buttons — and its container also writes
     * {@code setConfiguredStackSize(stackSize)} back in {@code onContainerClosed} (C40-C46). The port keeps the eight
     * buttons and sends <b>the step the button means</b>, so the server computes the new value from the value it
     * already holds: <b>a stale client cannot install a number the bus never agreed to</b>, and the clamp
     * ({@link #clampStackSize}) applies to one step rather than to an arbitrary payload. The screen displays the value
     * through the menu's data slot, so the button and the number it moves cannot disagree about what "current" is
     * ({@code PktSetParallelism}'s rule: re-derive from the world, apply what it says).</p>
     *
     * <p><b>THE SAME THREE FIREWALL RULES AS THE OTHER ME PACKETS</b> (see {@link PktMEFluidInputBusConfig}'s
     * registration comment): the handler's entry point is this class's static {@code apply}, whose signature names only
     * vanilla types, and the "which bus does the player have open" question is asked through
     * {@code MenuMEItemOutputBus.isOpenFor(AbstractContainerMenu, BlockPos)} — also vanilla-typed. So registering this
     * payload cannot pull an AE2-connected type into a class that loads without AE2.</p>
     */
    public static final class PktMEItemOutputBusStackSize {

        private final BlockPos pos;
        private final int delta;

        public PktMEItemOutputBusStackSize(BlockPos pos, int delta) {
            this.pos = pos.immutable();
            this.delta = delta;
        }

        /** Sends one step. Called from the screen, on the client. */
        public static void send(BlockPos pos, int delta) {
            CHANNEL.sendToServer(new PktMEItemOutputBusStackSize(pos, delta));
        }

        public static void encode(PktMEItemOutputBusStackSize msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.delta);
        }

        public static PktMEItemOutputBusStackSize decode(FriendlyByteBuf buf) {
            return new PktMEItemOutputBusStackSize(buf.readBlockPos(), buf.readVarInt());
        }

        public static void handle(PktMEItemOutputBusStackSize msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;   // a client-only send; nothing to apply
                }
                apply(player, msg.pos, msg.delta);
            });
            context.setPacketHandled(true);
        }

        /**
         * The clamp, in upstream's own rule: {@code setConfiguredStackSize} does {@code Math.max(1, size)} (U148) and
         * the stack-size screen repeats the same floor and ceiling around every button (G100-G138).
         *
         * <p>Extracted and static for the reason {@code PktSetParallelism.clampParallelism} is: a rule that can only be
         * exercised by a live client is a rule nobody tests.</p>
         */
        public static int clampStackSize(int size) {
            return Math.max(1, size);
        }

        /**
         * Applies one step to the bus the player has open, re-deriving everything from the world.
         *
         * <p>Three rules, and each one is a rule this channel already states: the position must be the bus the player's
         * OWN open menu names ({@code MenuMEItemOutputBus.isOpenFor}, never taken from the payload for granted), the
         * player must still be in range (the same 64.0-squared test the menus' {@code stillValid} makes), and what the
         * write lands on is the tile looked up in the level rather than a reference carried in the payload.</p>
         *
         * @return true when the value reached the tile
         */
        public static boolean apply(ServerPlayer player, BlockPos pos, int delta) {
            if (player == null) {
                return false;
            }
            net.minecraft.world.inventory.AbstractContainerMenu open = player.containerMenu;
            if (!hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus.isOpenFor(open, pos)) {
                // The message names a bus the player does not have open. Ignoring is right rather than correcting:
                // there is no bus this request could be redirected to.
                return false;
            }
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                // Out of range: the menu's own stillValid will close the screen on its next tick.
                return false;
            }
            if (!(player.level().getBlockEntity(pos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.me.MEItemOutputBus bus)) {
                return false;
            }
            // LONG ARITHMETIC, because the payload is a client's int and `current + delta` can overflow: a
            // Math.min(a + b, Integer.MAX_VALUE) is written in int and would wrap to a negative first, which
            // clampStackSize would then floor to 1 -- i.e. the LARGEST possible request would set the SMALLEST limit.
            long step = Math.max(-(long) Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, (long) delta));
            long wanted = bus.getConfiguredStackSize() + step;
            int target = (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, wanted));
            bus.setConfiguredStackSize(target);
            return bus.getConfiguredStackSize() == target;
        }
    }

    /** Client-to-server absolute stack-size edit used by the source GUI's text/expression field. */
    public static final class PktMEItemOutputBusStackSizeSet {

        private final BlockPos pos;
        private final int value;

        public PktMEItemOutputBusStackSizeSet(BlockPos pos, int value) {
            this.pos = pos.immutable();
            this.value = value;
        }

        public static void send(BlockPos pos, int value) {
            CHANNEL.sendToServer(new PktMEItemOutputBusStackSizeSet(pos, value));
        }

        public static void encode(PktMEItemOutputBusStackSizeSet msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.value);
        }

        public static PktMEItemOutputBusStackSizeSet decode(FriendlyByteBuf buf) {
            return new PktMEItemOutputBusStackSizeSet(buf.readBlockPos(), buf.readVarInt());
        }

        public static void handle(PktMEItemOutputBusStackSizeSet msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    apply(player, msg.pos, msg.value);
                }
            });
            context.setPacketHandled(true);
        }

        /** Applies an absolute value after the same menu, range and world checks as the delta packet. */
        public static boolean apply(ServerPlayer player, BlockPos pos, int value) {
            if (player == null) {
                return false;
            }
            if (!hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus.isOpenFor(player.containerMenu, pos)) {
                return false;
            }
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return false;
            }
            if (!(player.level().getBlockEntity(pos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.me.MEItemOutputBus bus)) {
                return false;
            }
            int target = Math.max(1, value);
            bus.setConfiguredStackSize(target);
            return bus.getConfiguredStackSize() == target;
        }
    }

    /** Client-to-server absolute count edit for one ME item input bus configuration cell. */
    public static final class PktMEItemInputBusCount {
        private final BlockPos pos;
        private final int slot;
        private final int count;

        public PktMEItemInputBusCount(BlockPos pos, int slot, int count) {
            this.pos = pos.immutable();
            this.slot = slot;
            this.count = count;
        }

        public static void send(BlockPos pos, int slot, int count) {
            CHANNEL.sendToServer(new PktMEItemInputBusCount(pos, slot, count));
        }

        public static void encode(PktMEItemInputBusCount msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.slot);
            buf.writeVarInt(msg.count);
        }

        public static PktMEItemInputBusCount decode(FriendlyByteBuf buf) {
            return new PktMEItemInputBusCount(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(PktMEItemInputBusCount msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    apply(player, msg.pos, msg.slot, msg.count);
                }
            });
            context.setPacketHandled(true);
        }

        /** Applies a requested count only to the config cell in the menu the player actually has open. */
        public static boolean apply(ServerPlayer player, BlockPos pos, int slot, int count) {
            if (player == null || count < 1) {
                return false;
            }
            if (!hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus
                    .isOpenFor(player.containerMenu, pos)
                    || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64.0) {
                return false;
            }
            if (!(player.level().getBlockEntity(pos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.me.MEItemInputBus bus)) {
                return false;
            }
            var menu = (hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus) player.containerMenu;
            var config = menu.configSlotAt(slot);
            if (config == null) {
                return false;
            }
            ItemStack current = config.getItem();
            if (current.isEmpty()) {
                return false;
            }
            int target = Math.max(1, count);
            ItemStack updated = current.copy();
            updated.setCount(target);
            config.setMarked(updated);
            return bus.getConfigInventory().getStackInSlot(slot).getCount() == target;
        }
    }

    /** Client-to-server JEI ghost-item write for one ME item input bus configuration cell. */
    public static final class PktMEItemInputBusGhost {
        private final BlockPos pos;
        private final int slot;
        private final ItemStack stack;

        public PktMEItemInputBusGhost(BlockPos pos, int slot, ItemStack stack) {
            this.pos = pos.immutable();
            this.slot = slot;
            this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        public static void send(BlockPos pos, int slot, ItemStack stack) {
            CHANNEL.sendToServer(new PktMEItemInputBusGhost(pos, slot, stack));
        }

        public static void encode(PktMEItemInputBusGhost msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.slot);
            buf.writeItem(msg.stack);
        }

        public static PktMEItemInputBusGhost decode(FriendlyByteBuf buf) {
            return new PktMEItemInputBusGhost(buf.readBlockPos(), buf.readVarInt(), buf.readItem());
        }

        public static void handle(PktMEItemInputBusGhost msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    apply(player, msg.pos, msg.slot, msg.stack);
                }
            });
            context.setPacketHandled(true);
        }

        /** Applies a JEI item only to the configuration menu the player actually has open. */
        public static boolean apply(ServerPlayer player, BlockPos pos, int slot, ItemStack stack) {
            if (player == null || stack == null || stack.isEmpty()
                    || !hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus
                    .isOpenFor(player.containerMenu, pos)
                    || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64.0) {
                return false;
            }
            if (!(player.level().getBlockEntity(pos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.me.MEItemInputBus bus)) {
                return false;
            }
            var menu = (hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus) player.containerMenu;
            var config = menu.configSlotAt(slot);
            if (config == null) {
                return false;
            }
            config.setMarked(stack.copy());
            return ItemStack.matches(bus.getConfigInventory().getStackInSlot(slot), stack);
        }
    }
}
