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
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.selection;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;































































































































 
public final class PlayerStructureSelectionHelper {

    /** Per-player selection state. Upstream L55, a {@code Map<UUID, StructureSelection>}. */
    private static final Map<UUID, StructureSelection> activeSelectionMap = new HashMap<>();

    /**
     * The client-side mirror, written by the sync packet and read by the box renderer. Upstream L56.
     *
     * <p>Deliberately <b>not</b> client-gated, and upstream's is not either: the field holds block
     * positions and this class has to load on a dedicated server, because the server half lives in it.
     * What is client-only is the renderer that consumes this, and that class is dist-gated.
     */
    public static StructureSelection clientSelection = null;

    /**
     * The largest box this tool will export: a 40x40x40 selection and nothing bigger.
     *
     * <p>Not an upstream constant — upstream had none to have, because a clicked list only ever holds as
     * many positions as a player is willing to click. A two-corner box is unbounded by its own shape, and
     * every consumer of it iterates: {@link #finalizeSelection} reads a block state per position and
     * {@code SelectionBoxRenderer} walks the same volume on the client every frame. 64Ki positions is far
     * beyond any machine this port's content ships (its largest shipped pattern is well under a thousand
     * parts) while still being a bound a server tick can walk once.
     */
    public static final int MAX_BOX_VOLUME = 65536;

    private PlayerStructureSelectionHelper() {
    }

    // ------------------------------------------------------------------
    // Per-player state (upstream L55-L75, L128-L134)
    // ------------------------------------------------------------------

    /**
     * Sets this player's FIRST corner, the plain right-click arm.
     *
     * <p>The Refoxed's {@code MultiblockDetectorItem} does the same thing through
     * {@code selection.withFirst(clickedPos)}. The name differs because the storage does: that record
     * returns a new immutable value, this mutates the player's entry.
     */
    public static void setFirstCorner(Player player, BlockPos pos) {
        activeSelectionMap.computeIfAbsent(player.getUUID(), uuid -> new StructureSelection())
                .setFirstCorner(pos);
    }

    /** Sets this player's SECOND corner, the modifier + right-click arm — the Refoxed's {@code withSecond}. */
    public static void setSecondCorner(Player player, BlockPos pos) {
        activeSelectionMap.computeIfAbsent(player.getUUID(), uuid -> new StructureSelection())
                .setSecondCorner(pos);
    }

    /** Drops this player's selection, and the null-check is upstream's (L62-L67). */
    public static void purgeSelection(Player player) {
        if (player == null) {
            return;
        }
        activeSelectionMap.remove(player.getUUID());
    }

    /**
     * Sends this player their own two corners so the client can draw the box between them.
     *
     * <p>Upstream L69-L75 sent the whole position list. The payload is now the two corners, and that is
     * <b>smaller and more honest at once</b>: the client derives the same cuboid from the same pair, so
     * the packet cannot carry a volume the server did not mean — the accumulator is gone from the wire as
     * well as from memory.
     */
    public static void sendSelection(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            StructureSelection selection = selectionOf(serverPlayer);
            ModularmachineryNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ModularmachineryNetwork.PktSyncSelection(
                            selection.getFirstCorner(), selection.getSecondCorner()));
        }
    }

    /** @return this player's selection, created empty on first use. Upstream L59's lambda, as a method. */
    public static StructureSelection selectionOf(Player player) {
        return activeSelectionMap.computeIfAbsent(player.getUUID(), uuid -> new StructureSelection());
    }

    /**
     * Drops a leaving player's selection, which is upstream's {@code onDisconnect} (L128-L134).
     *
     * <p>Upstream subscribed to {@code FMLNetworkEvent.ServerDisconnectionFromClientEvent} and dug the
     * player out of the net handler. 1.20.1 has the event that means the same thing and carries the
     * player directly, so the handler is one line and there is no net-handler cast to get wrong.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        purgeSelection(event.getEntity());
    }

    /**
     * Registers {@link #onLogout} on the game bus. Called once from {@code Modularmachinery}'s constructor,
     * beside the channel registration.
     *
     * <p><b>{@code @EventBusSubscriber} cannot do this, and the reason is not a preference.</b> That
     * annotation is processed for classes Forge scans out of the mod file, and the game-bus path
     * registers them at {@code EventBusSubscriber.Bus.GAME}; this class is reached through the item a
     * player holds rather than through the mod class, so declaring the annotation here would be a
     * promise the scanner does not have to keep. Registering the class explicitly, from a call site that
     * runs unconditionally, is the version of this that a future refactor cannot silently drop — an
     * unregistered handler fails as "the map grows for the server's lifetime", which nothing notices.
     */
    public static void register(net.minecraftforge.eventbus.api.IEventBus gameBus) {
        gameBus.register(PlayerStructureSelectionHelper.class);
    }

    // ------------------------------------------------------------------
     
    // ------------------------------------------------------------------

    /** @return the component-wise smaller of the two corners, so callers need no min/max per axis. */
    public static BlockPos boxMin(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
    }

    /** @return the component-wise larger of the two corners. */
    public static BlockPos boxMax(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    /**
     * How many positions the box between two corners holds.
     *
     * <p><b>Long, and saturating, because the naive product is not safe here.</b> Two corners in opposite
     * corners of the world are 6x10^7 apart on each axis; the product of the three sizes overflows
     * {@code long} (2.1x10^23 against 9.2x10^18) and would come back <i>negative</i> — which compares as
     * "smaller than {@link #MAX_BOX_VOLUME}" and would send the iterating callers into a loop over a box
     * that cannot be walked. So each axis is bounded first and the saturation is explicit.
     *
     * @return the volume, or {@link Long#MAX_VALUE} when any single axis already exceeds
     *         {@link #MAX_BOX_VOLUME}
     */
    public static long boxVolume(BlockPos a, BlockPos b) {
        long dx = (long) boxMax(a, b).getX() - boxMin(a, b).getX() + 1L;
        long dy = (long) boxMax(a, b).getY() - boxMin(a, b).getY() + 1L;
        long dz = (long) boxMax(a, b).getZ() - boxMin(a, b).getZ() + 1L;
        if (dx > MAX_BOX_VOLUME || dy > MAX_BOX_VOLUME || dz > MAX_BOX_VOLUME) {
            return Long.MAX_VALUE;
        }
        return dx * dy * dz;   // each factor <= 65536, so the product <= 2.8x10^14 and cannot overflow
    }

    /**
     * Every position in the box between two corners, in y/x/z order.
     *
     * <p><b>An empty list means "this box is not walkable", not "an empty box".</b> The two cases are
     * distinguished by the caller checking {@link #boxVolume} first — {@link #finalizeSelection} does,
     * and reports {@code message.structurebuild.toobig} rather than an empty pattern. Returning an empty
     * list instead of throwing is deliberate: a box that is too large is a player's mistake with a
     * message attached, not a bug.
     *
     * <p>The loops count up from the minimum rather than incrementing the absolute coordinate, so a box
     * whose maximum sits at {@link Integer#MAX_VALUE} cannot wrap into an endless loop.
     */
    public static List<BlockPos> boxPositions(BlockPos a, BlockPos b) {
        long volume = boxVolume(a, b);
        if (volume > MAX_BOX_VOLUME) {
            return List.of();
        }
        BlockPos min = boxMin(a, b);
        BlockPos max = boxMax(a, b);
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        List<BlockPos> out = new ArrayList<>((int) volume);
        for (int iy = 0; iy < sizeY; iy++) {
            for (int ix = 0; ix < sizeX; ix++) {
                for (int iz = 0; iz < sizeZ; iz++) {
                    out.add(new BlockPos(min.getX() + ix, min.getY() + iy, min.getZ() + iz));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Authoring: world -> JSON (upstream L77-L126, L159-L179)
    // ------------------------------------------------------------------

    /**
     * Reads what the player selected, rotates it so the controller's facing becomes NORTH, and writes a
     * machine JSON into the machinery directory.
     *
     * <p>Upstream L77-L126 step for step, with the selection now read as a box:
     * <ol>
     *   <li>an empty selection is reported and nothing is written (L79-L82);</li>
     *   <li><b>a selection with only one corner is reported and nothing is written</b> — the case
     *       upstream could not have, where a player has said "here" and not yet "to here";</li>
     *   <li><b>a box larger than {@link #MAX_BOX_VOLUME} is reported and nothing is written</b>;</li>
     *   <li>the controller's facing is reported back (L83);</li>
     *   <li>the selection is read into controller-relative offsets (L84) — from the box, cell by cell;</li>
     *   <li>if the controller does not face NORTH, the array is rotated {@code rotateYCCW()} until it
     *       does, and the total is reported (L85-L94);</li>
     *   <li>the file name is {@code machine-<player>-<timestamp>}, suffixed while that name is taken
     *       (L103-L112);</li>
     *   <li>the JSON is written into the machinery directory (L114-L124).</li>
     * </ol>
     *
     * @param controllerFacing the clicked controller's {@code FACING}, the value
     *                         {@code ItemConstructTool} passes
     * @param level            the level the blocks are in
     * @param controllerPos    the controller's world position — the pattern's origin
     * @param player           the authoring player
     * @return <b>whether a file was written.</b> Upstream returned {@code void} and its caller purged the
     *         selection unconditionally. With a refusal path that is wrong: a player whose box was too big
     *         would lose both corners and have to re-click them to fix a number. The item purges on
     *         {@code true} only, which is the same behaviour as upstream's on every path upstream had.
     */
    public static boolean finalizeSelection(Direction controllerFacing, Level level, BlockPos controllerPos,
                                            Player player) {
        return finalizeSelection(controllerFacing, level, controllerPos, player, null);
    }

    /**
     * Finalises the current two-corner selection, optionally using an explicit machine name.
     *
     * <p>The item path passes {@code null} and keeps the source-compatible timestamp name.  The
     * command path supplies a validated name so an author can create a stable file without editing the
     * generated JSON first.  Both paths share the same world scan, facing rotation, NBT capture and
     * size guard.</p>
     */
    public static boolean finalizeSelection(Direction controllerFacing, Level level, BlockPos controllerPos,
                                            Player player, @Nullable String requestedName) {
        StructureSelection sel = activeSelectionMap.get(player.getUUID());
        if (sel == null || sel.isEmpty()) {
            // Upstream L80. The lang key already ships in en_us and zh_cn with upstream's own text.
            player.displayClientMessage(Component.translatable("message.structurebuild.empty"), false);
            return false;
        }
        if (!sel.isComplete()) {
            // A key this port adds, because the state is one upstream could not enter: a two-corner tool
            // can be half-used, and "you have nothing selected" would be a lie about it.
            player.displayClientMessage(
                    Component.translatable("message.structurebuild.incomplete"), false);
            return false;
        }
        long volume = sel.volume();
        if (volume > MAX_BOX_VOLUME) {
            // Both numbers come from the values, not from literals: the cap is a constant a later change
            // must not be able to leave behind in a message that still says 65536.
            player.displayClientMessage(Component.translatable("message.structurebuild.toobig",
                    String.valueOf(volume), String.valueOf(MAX_BOX_VOLUME)), false);
            return false;
        }
        // Upstream L83.
        player.displayClientMessage(
                Component.translatable("message.structurebuild.confirmrotation", controllerFacing), false);

        // THE ROTATION IS A DIRECTION CHANGE HERE, NOT A TRANSFORM (upstream L85-L94).
        //
        // Upstream looped `out = out.rotateYCCW(); face = face.rotateYCCW();` until the face reached
        // NORTH, adding 90 to a degree counter each time. This port has no BlockArray to rotate:
        // StructureMatcher.toWorldOffset maps a pattern offset through the controller's facing, and
         
        // a position stored relative to the controller on the NORTH axis is exactly the offset, and all
        // the loop has to decide is which facing that is and what to report. `getCounterClockWise()` is
        // 1.20.1's name for the same Y rotation upstream's `rotateYCCW()` performs.
        Direction face = controllerFacing;
        int degrees = 0;
        while (face != Direction.NORTH) {
            face = face.getCounterClockWise();
            degrees += 90;
        }
        if (degrees != 0) {
            // Upstream L93, its own key and its own wording.
            player.displayClientMessage(Component.translatable(
                    "message.structurebuild.confirmrotation.rotating", String.valueOf(degrees)), false);
        }

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getServer() != null && serverLevel.getServer().isDedicatedServer()) {
            // Upstream L99-L101, its own key: on a dedicated server the file lands on the server's disk
            // and the author has to go and get it.
            player.displayClientMessage(
                    Component.translatable("message.structurebuild.warndedicated"), false);
        }

        File directory = ModDataHolder.INSTANCE.getMachineryDirectory();
        if (directory == null) {
            // Reachable only before ModDataHolder.setup(), which runs at common setup — i.e. before any
            // player can hold an item. Reported rather than thrown, so a future call site that is
            // ordered wrongly produces a message instead of a crash in someone's world.
            Modularmachinery.LOG.error("[MMCE] no machinery directory to write into: "
                    + "ModDataHolder.setup() has not run");
            player.displayClientMessage(Component.translatable("message.structurebuild.fail"), false);
            return false;
        }

        // THE NAME IS THE FILE'S, AND IT IS READ FROM THE FILE FOR THE SAME REASON (the warning in the
         
        // machine by its file's basename (MachineLoader.nameOf), so the registryname written inside the
        // file and the basename are made the same string from one source rather than from two literals
        // that agree today.
        String baseName = requestedName == null ? machineFileName(player) : requestedName;
        if (requestedName != null && !isValidMachineName(requestedName)) {
            player.displayClientMessage(Component.literal(
                    "机器名只能使用小写字母、数字、点、下划线和连字符"), false);
            return false;
        }
        Selection selection = selectionAt(level, controllerPos, sel.positions());
        String json = writeJson(baseName, baseName, selection);
        Optional<File> written = write(directory, baseName, json);

        if (written.isPresent()) {
            // Upstream L116. The name reported is the file's real one, suffix and all.
            player.displayClientMessage(Component.translatable(
                    "message.structurebuild.save", written.get().getName()), false);
            return true;
        }
        player.displayClientMessage(Component.translatable("message.structurebuild.fail"), false);
        return false;
    }

    /**
     * Reads the world at every selected position and turns it into a selection in
     * <b>controller-relative</b> coordinates.
     *
     * <p>Upstream L159-L179, which is where {@code pos.subtract(center)} comes from. The controller's own
     * position is skipped — see the class javadoc, and note that with a box selection the controller is
     * normally <i>inside</i> the box rather than a position the player had to add by hand.
     *
     * <p><b>What the world has nothing nameable at is skipped</b>, which is where air inside a box goes.
     * A position whose block has no registry name is logged; a position holding air is not, because air
     * inside a machine's shell is the normal case and a log line per cell would be noise rather than
     * evidence.
     *
     * @return the selection, never null
     */
    public static Selection selectionAt(Level level, BlockPos controllerPos, List<BlockPos> selected) {
        List<BlockPos> positions = new ArrayList<>();
        Map<BlockPos, String> elements = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> blockEntityData = new LinkedHashMap<>();

        for (BlockPos worldPos : selected) {
            if (worldPos.equals(controllerPos)) {
                continue;   // the controller is not part of its own pattern; see the class javadoc
            }
            BlockPos offset = worldPos.subtract(controllerPos);
            BlockState state = level.getBlockState(worldPos);
            if (state.isAir()) {
                // A box selection describes a volume, and a machine's volume contains air. Upstream
                // could not reach this case -- it only ever held positions a player clicked -- so this is
                // the one place where "read the box" and "read the clicks" could differ in what they
                // emit, and the answer is upstream's: only blocks that are there are written.
                continue;
            }
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getKey(state.getBlock());
            if (id == null) {
                // Not nameable in a definition at all. Skipped and logged rather than written as a broken
                // element: a part that resolves to nothing is a machine that can never match, which is a
                // worse failure than a machine that is missing one block.
                Modularmachinery.LOG.warn("[MMCE] selected block at {} has no registry name, so it is "
                        + "left out of the generated machine", worldPos);
                continue;
            }
            positions.add(offset);
            elements.put(offset, id.toString());

            // Upstream L165-L175: the block entity's tag, without its coordinates.
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be != null) {
                CompoundTag tag = be.saveWithoutMetadata();
                tag.remove("x");
                tag.remove("y");
                tag.remove("z");
                blockEntityData.put(offset, tag);
            }
        }
        return new Selection(positions, elements, blockEntityData);
    }

    /**
     * Serialises a selection as a machine definition this port's loader reads back.
     *
     * <p>Sorted by position and pretty-printed. Upstream iterated a {@code Map} and got an arbitrary but
     * stable order; sorting makes two saves of the same structure byte-identical, which is what an author
     * diffing their own files wants. The order is not load-bearing for either loader.
     *
     * @param registryName  the {@code registryname} to write — the caller passes the same string it named
     *                      the file with, never an invented one
     * @param localizedName the {@code localizedname} to write; empty or null omits the key
     * @param selection     the blocks, in controller-relative offsets
     * @return the JSON text
     */
    public static String writeJson(String registryName, String localizedName, Selection selection) {
        List<BlockPos> positions = new ArrayList<>(selection.positions());
        positions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        String newline = System.lineSeparator();
        String move = "    ";
        StringBuilder sb = new StringBuilder();
        sb.append('{').append(newline);
        sb.append(move).append("\"registryname\": \"").append(registryName).append("\",")
                .append(newline);
        if (localizedName != null && !localizedName.isEmpty()) {
            // The file's basename IS the machine's identity in this loader, so the localised name cannot
            // be a translation key: nothing has an entry for a name nobody has written down yet. Writing
            // the same string is what makes a generated machine's title its own name in the GUI instead
             
            sb.append(move).append("\"localizedname\": \"").append(localizedName).append("\",")
                    .append(newline);
        }
        sb.append(move).append("\"parts\": [").append(newline);
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            sb.append(move).append(move).append('{').append(newline);
            sb.append(move).append(move).append(move).append("\"x\": ").append(pos.getX())
                    .append(',').append(newline);
            sb.append(move).append(move).append(move).append("\"y\": ").append(pos.getY())
                    .append(',').append(newline);
            sb.append(move).append(move).append(move).append("\"z\": ").append(pos.getZ())
                    .append(',').append(newline);
            CompoundTag tag = selection.blockEntityData().get(pos);
            if (tag != null && !tag.isEmpty()) {
                // NbtUtils.structureToSnbt quotes what it must, but the result is an NBT literal, not a
                // JSON string: backslashes and quotes are escaped here so the value survives JsonParser.
                sb.append(move).append(move).append(move).append("\"nbt\": \"")
                        .append(escapeForJson(NbtUtils.structureToSnbt(tag))).append("\",")
                        .append(newline);
            }
            // A STRING, not upstream's one-element ARRAY. readElements accepts both and joins the array
            // form with commas; one position can only ever produce one element, and a single-element
            // array is the same value to everything downstream.
            sb.append(move).append(move).append(move).append("\"elements\": \"")
                    .append(selection.elements().getOrDefault(pos, "")).append('"').append(newline);
            sb.append(move).append(move).append('}');
            if (i < positions.size() - 1) {
                sb.append(',');
            }
            sb.append(newline);
        }
        sb.append(move).append(']').append(newline).append('}');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // The file (upstream L103-L124)
    // ------------------------------------------------------------------

    


















 
    public static String machineFileName(Player player) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
        return "machine-" + safeForResourcePath(player.getScoreboardName()) + "-" + timestamp;
    }

    /** Stable command names are file basenames, so separators and uppercase resource-path characters are refused. */
    public static boolean isValidMachineName(String name) {
        return name != null && !name.isBlank() && name.matches("[a-z0-9._-]+");
    }

    /**
     * @return {@code name} with every character a {@link ResourceLocation} path does not accept replaced by
     *         {@code _}, and empty when there was nothing legal in it
     */
    static String safeForResourcePath(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '_' || c == '-';
            sb.append(allowed ? c : '_');
        }
        return sb.toString();
    }

    /**
     * Writes {@code json} into {@code directory} under {@code baseName + ".json"}, never overwriting.
     *
     * <p>Upstream L107-L112: {@code new File(directory, fileName + ".json")}, then the {@code " (n)"}
     * suffix while that name exists. The increment is upstream's.
     *
     * <p><b>The write is {@code Files.write(..., UTF_8)} — upstream L115 — opened with
     * {@code CREATE_NEW}.</b> Upstream's existence loop and its write are not one operation, so a second
     * save in the same second can find the name taken between the check and the write, and overwrite an
     * authored machine. {@code CREATE_NEW} merges the two: a race is a
     * {@link FileAlreadyExistsException} and the loop increments again, which is what upstream's loop
     * would have done had it seen the file.
     *
     * @return the file written, or empty after a failure that has been logged with its path
     */
    public static Optional<File> write(File directory, String baseName, String json) {
        for (int increment = 0; increment < 1000; increment++) {
            String suffix = increment == 0 ? "" : " (" + increment + ")";
            File out = new File(directory, baseName + suffix + ".json");
            if (out.exists()) {
                continue;
            }
            try {
                Files.write(out.toPath(), json.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return Optional.of(out);
            } catch (FileAlreadyExistsException taken) {
                continue;   // taken between the check and the write; upstream's loop increments
            } catch (IOException e) {
                Modularmachinery.LOG.error("[MMCE] couldn't write the machine file {}", out, e);
                // Upstream L121-L123, and its guard: a partially written machine JSON is a machine that
                // fails to load with a parse error pointing at a file the author never wrote.
                if (out.exists() && !out.delete()) {
                    Modularmachinery.LOG.warn("[MMCE] couldn't clean up the incomplete file {}", out);
                }
                return Optional.empty();
            }
        }
        Modularmachinery.LOG.error("[MMCE] couldn't find a free file name for {}", baseName);
        return Optional.empty();
    }

    /** Escapes what {@code NbtUtils.structureToSnbt} emits that a JSON string cannot carry raw. */
    private static String escapeForJson(String snbt) {
        StringBuilder sb = new StringBuilder(snbt.length() + 8);
        for (int i = 0; i < snbt.length(); i++) {
            char c = snbt.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * The blocks a definition will carry, with the two derived views the writer needs.
     *
     * <p>Upstream's {@code StructureSelection} computed the rest inside {@code compressAsArray}. The three
     * travel together here because a selection is meaningless without the level it was read from, and
     * handing a level into a record so that every reader recomputes the same two maps is how the two ends
     * up disagreeing about them.
     *
     * <p>{@link #elements} and {@link #blockEntityData} are keyed by <b>controller-relative</b> offset,
     * which is also what {@link #positions} holds. {@code elements} has no entry for a position the tool
     * could not name; {@code selectionAt} logs those.
     */
    public record Selection(List<BlockPos> positions, Map<BlockPos, String> elements,
                            Map<BlockPos, CompoundTag> blockEntityData) {

        public Selection {
            positions = List.copyOf(positions);
            elements = Map.copyOf(elements);
            blockEntityData = Map.copyOf(blockEntityData);
        }

        public boolean isEmpty() {
            return positions.isEmpty();
        }

        public int size() {
            return positions.size();
        }
    }

    /**
     * One player's two corners — the only mutable state in this class.
     *
     * <p><b>The shape is the Refoxed's, the storage is upstream's.</b>
     * {@code MultiblockDetectorSelection} is a record of (controllerPos, controllerFace, firstPos,
     * secondPos) and is immutable; this is a mutable pair held in the per-player map, because the port
     * keeps upstream's per-player state (see the class javadoc for why, and for what that costs). The
     * controller is not stored here: it is an argument to {@link #finalizeSelection}, which is where
     * upstream put it.
     *
     * <p><b>Both corners, one corner and no corner are all reachable states.</b> The modifier arm can be
     * used first, exactly as the Refoxed's {@code withSecond} allows, and {@link #isComplete} is the only
     * test that says "this is a box".
     */
    public static final class StructureSelection {

        private BlockPos firstCorner;
        private BlockPos secondCorner;

        /** The per-player constructor: nothing selected. Upstream L140-L141. */
        StructureSelection() {
        }

        /**
         * The packet's constructor, from two decoded corners. Upstream L143-L145 took a position list.
         *
         * <p>{@code immutable()} on the way in, because these come off a {@code FriendlyByteBuf} and a
         * mutable {@link BlockPos.MutableBlockPos} that a later read reuses would turn a stored corner
         * into a different position.
         */
        public StructureSelection(@Nullable BlockPos firstCorner, @Nullable BlockPos secondCorner) {
            this.firstCorner = firstCorner == null ? null : firstCorner.immutable();
            this.secondCorner = secondCorner == null ? null : secondCorner.immutable();
        }

        /** The plain right-click arm. The Refoxed's {@code withFirst}. */
        public void setFirstCorner(BlockPos pos) {
            this.firstCorner = pos.immutable();
        }

        /** The modifier + right-click arm. The Refoxed's {@code withSecond}. */
        public void setSecondCorner(BlockPos pos) {
            this.secondCorner = pos.immutable();
        }

        public @Nullable BlockPos getFirstCorner() {
            return firstCorner;
        }

        public @Nullable BlockPos getSecondCorner() {
            return secondCorner;
        }

        /** The Refoxed's {@code isComplete()} without its controller half: both corners are set. */
        public boolean isComplete() {
            return firstCorner != null && secondCorner != null;
        }

        /** True when no corner has been marked; the authoring tool reports an empty selection. */
        public boolean isEmpty() {
            return firstCorner == null && secondCorner == null;
        }

        /** @return this selection's box size, or 0 while it is not a box yet. */
        public long volume() {
            return isComplete() ? boxVolume(firstCorner, secondCorner) : 0L;
        }

        /** @return a copy of the box's positions, or an empty list when there is no box or it is too large. */
        public List<BlockPos> positions() {
            return isComplete() ? boxPositions(firstCorner, secondCorner) : List.of();
        }
    }
}
