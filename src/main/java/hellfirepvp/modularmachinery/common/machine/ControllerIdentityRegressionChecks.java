/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;

/** Local profile cache and online UUID index fixtures; no connections, authentication or player logins. */
public final class ControllerIdentityRegressionChecks {
    public static final String TARGET = "mmc_controller_identity_regression";
    private ControllerIdentityRegressionChecks() { }
    @SuppressWarnings("unchecked")
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var server = level.getServer(); var cache = server.getProfileCache(); var playerList = server.getPlayerList();
        var onlineField = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); onlineField.setAccessible(true);
        var online = (Map<UUID, ServerPlayer>) onlineField.get(playerList);
        var cacheById = net.minecraft.server.players.GameProfileCache.class.getDeclaredField("profilesByUUID"); cacheById.setAccessible(true);
        var cacheByName = net.minecraft.server.players.GameProfileCache.class.getDeclaredField("profilesByName"); cacheByName.setAccessible(true);
        var idMap = (Map<Object, Object>) cacheById.get(cache); var nameMap = (Map<Object, Object>) cacheByName.get(cache);
        var savedIds = new LinkedHashMap<>(idMap); var savedNames = new LinkedHashMap<>(nameMap);
        var savedOnline = new LinkedHashMap<>(online);
        var machines = MachineReloadListener.getLoader().getMachines(); var savedMachines = new LinkedHashMap<>(machines);
        var saved = new LinkedHashMap<ServerLevel, Map<BlockPos, BlockState>>();
        UUID owner = UUID.randomUUID(), carriedOwner = UUID.randomUUID(), unknown = UUID.randomUUID();
        String ownerName = "MmcA" + owner.toString().substring(0, 6), carriedName = "MmcB" + carriedOwner.toString().substring(0, 6);
        var placer = new FakePlayer(level, new GameProfile(owner, ownerName));
        var carriedPlayer = new FakePlayer(level, new GameProfile(carriedOwner, carriedName));
        boolean scripts = net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker");
        var blocks = new ArrayList<Block>(List.of(RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get()));
        for (boolean factory : List.of(false, true)) blocks.add(DedicatedControllers.entries().stream()
                .filter(e -> e.factory() == factory).findFirst().orElseThrow().block().get());
        try {
            cache.add(placer.getGameProfile()); cache.add(carriedPlayer.getGameProfile());
            var dimensions = List.of(level, Objects.requireNonNull(server.getLevel(Level.NETHER)));
            for (ServerLevel world : dimensions) for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i); BlockPos pos = origin.offset(i * 8, 0, 0);
                put(world, pos, block.defaultBlockState().setValue(BlockController.FACING, Direction.NORTH), saved);
                put(world, pos.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), saved);
                put(world, pos.offset(0, 2, 0), Blocks.AIR.defaultBlockState(), saved);
                var tile = (TileController) world.getBlockEntity(pos);
                String target = Optional.ofNullable(DedicatedControllers.binding(block)).orElse(TARGET);
                var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
                var machine = new DynamicMachine(target, target, pattern);
                machines.put(new ResourceLocation("modularmachinery", target), machine); tile.setMachineName(target);
                require(tile.getIWorld() == world && tile.getOwnerUUIDString() == null && tile.getOwnerName() == null
                        && tile.getOwnerIPlayer() == null, "unformed world/empty owner query differs");
                block.setPlacedBy(world, pos, tile.getBlockState(), placer, new ItemStack(block));
                require(owner.toString().equals(tile.getOwnerUUIDString()) && ownerName.equals(tile.getOwnerName())
                        && tile.getOwnerIPlayer() == null, "placement UUID/cache/offline queries differ");
                var dropped = new ItemStack(block); dropped.getOrCreateTag().putUUID("owner", carriedOwner);
                block.setPlacedBy(world, pos, tile.getBlockState(), placer, dropped);
                require(carriedOwner.equals(tile.getOwner()) && carriedName.equals(tile.getOwnerName()), "tagged controller lost original owner on placement");
                tile.setOwner(unknown);
                require(unknown.toString().equals(tile.getOwnerUUIDString()) && tile.getOwnerName() == null && tile.getOwnerIPlayer() == null,
                        "unknown UUID fabricated a cached name or player");
                tile.setOwner(carriedOwner);
                online.put(owner, placer); // An unrelated online player must not satisfy the controller's owner.
                require(tile.getOwnerIPlayer() == null, "online query matched the wrong UUID");
                online.remove(owner);
                online.put(carriedOwner, carriedPlayer);
                require(tile.getOwnerIPlayer() == carriedPlayer, "online query did not use global player UUID index across dimensions");
                var attempt = MachineFormation.tryForm(world, pos, Direction.NORTH, machine, MachineReloadListener.getLoader());
                require(attempt.formed(), "identity fixture did not form: " + attempt.report()); tile.setFormed(true);
                require(new MachineEvent(tile).getIMachineController().getIWorld() == world, "event did not expose its controller's own dimension");
                if (scripts) hellfirepvp.modularmachinery.integration.crafttweaker.ControllerIdentityScriptRegressionChecks.run(tile, target, carriedOwner, carriedName, true);
                online.remove(carriedOwner);
                require(tile.getOwnerIPlayer() == null && carriedName.equals(tile.getOwnerName()), "offline player retained live handle or lost cached name");
                if (scripts) hellfirepvp.modularmachinery.integration.crafttweaker.ControllerIdentityScriptRegressionChecks.run(tile, target, carriedOwner, carriedName, false);
                var disk = tile.getUpdateTag();
                require(disk.hasUUID("owner") && disk.getUUID("owner").equals(carriedOwner), "query changed owner persistence format");
                TileController observation = tile instanceof TileFactoryController ? new TileFactoryController(pos, tile.getBlockState()) : new TileController(pos, tile.getBlockState());
                observation.load(disk);
                require(carriedOwner.toString().equals(observation.getOwnerUUIDString()) && observation.getOwnerName() == null
                        && observation.getOwnerIPlayer() == null && observation.getIWorld() == null, "detached snapshot reached a live world/player/cache");
                tile.load(disk);
                require(tile.getIWorld() == world && carriedName.equals(tile.getOwnerName()) && tile.getOwnerIPlayer() == null, "loaded owner queries differ");
                CompoundTag beforeRead = tile.getUpdateTag();
                tile.getOwnerName(); tile.getOwnerIPlayer(); tile.getIWorld(); tile.getOwnerUUIDString();
                require(beforeRead.equals(tile.getUpdateTag()), "queries wrote controller NBT");
                require(java.util.concurrent.CompletableFuture.supplyAsync(() -> tile.getIWorld() == null
                        && tile.getOwnerName() == null && tile.getOwnerIPlayer() == null
                        && carriedOwner.toString().equals(tile.getOwnerUUIDString())).get(), "async query accessed server world or player cache");
                var stale = tile;
                put(world, pos, Blocks.AIR.defaultBlockState(), saved);
                require(stale.getIWorld() == null && stale.getOwnerName() == null && stale.getOwnerIPlayer() == null,
                        "removed controller still exposed live server objects");
                var unloaded = new TileController(new BlockPos(1_700_000, pos.getY(), pos.getZ()), block.defaultBlockState());
                unloaded.setLevel(world); unloaded.setOwner(carriedOwner);
                require(!world.hasChunkAt(unloaded.getBlockPos()) && unloaded.getIWorld() == null && unloaded.getOwnerName() == null
                        && unloaded.getOwnerIPlayer() == null && !world.hasChunkAt(unloaded.getBlockPos()), "query loaded an absent controller chunk");
            }
            Modularmachinery.LOG.info("[MMC][controller-identity] PASS: generic/dedicated ordinary/factory, overworld/nether, placer/tagged owner, UUID/cache/online/offline, save/snapshot, stale/async/unloaded guards");
        } finally {
            online.clear(); online.putAll(savedOnline);
            idMap.clear(); idMap.putAll(savedIds); nameMap.clear(); nameMap.putAll(savedNames);
            saved.forEach((world, states) -> states.forEach((pos, state) -> world.setBlock(pos, state, Block.UPDATE_CLIENTS)));
            machines.clear(); machines.putAll(savedMachines); MachineReloadListener.refreshRecipes();
        }
    }
    private static void put(ServerLevel level, BlockPos pos, BlockState state, Map<ServerLevel, Map<BlockPos, BlockState>> saved) {
        saved.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).putIfAbsent(pos, level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
