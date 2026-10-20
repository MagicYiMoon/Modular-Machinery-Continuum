/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

/** Exercises the same Level and entity creation used by client BERs, without loading any client classes. */
public final class BlueprintPreviewLevelRegressionChecks {
    private BlueprintPreviewLevelRegressionChecks() { }

    public static void run(ServerLevel source, BlockPos probe) {
        BlockState liveState = source.getBlockState(probe);
        BlockEntity liveEntity = source.getBlockEntity(probe);
        int liveSpawnX = source.getLevelData().getXSpawn();
        boolean liveRule = source.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, BlockEntity> entities = new HashMap<>();
        BlueprintPreviewLevel preview = new BlueprintPreviewLevel(source, probe, states, entities);
        int loadedChunks = source.getChunkSource().getLoadedChunksCount();
        require(preview.isClientSide && preview.getServer() == null, "preview linked to gameplay server");
        require(preview.getLevelData() != source.getLevelData() && preview.getGameRules() != source.getGameRules(), "mutable world data shared");

        BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);
        BlockPos first = new BlockPos(-35, -129, -4), second = first.relative(ChestBlock.getConnectedDirection(left));
        states.put(first, left);
        states.put(second, right);
        CompoundTag chestNbt = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag diamonds = new ItemStack(Items.DIAMOND, 7).save(new CompoundTag());
        diamonds.putByte("Slot", (byte) 0);
        items.add(diamonds);
        chestNbt.put("Items", items);
        ChestBlockEntity chest = (ChestBlockEntity) preview.createBlockEntity(first, left, chestNbt);
        entities.put(first, chest);
        entities.put(second, preview.createBlockEntity(second, right, null));
        require(chest.getLevel() == preview && chest.getBlockState().getValue(ChestBlock.FACING) == Direction.EAST,
                "chest renderer would lose its world/facing");
        require(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7, "preview chest inventory NBT lost");
        chestNbt.getList("Items", Tag.TAG_COMPOUND).getCompound(0).putByte("Count", (byte) 1);
        require(chest.getItem(0).getCount() == 7, "preview entity retained mutable pattern NBT");
        boolean combined = ((ChestBlock) left.getBlock()).combine(left, preview, first, true)
                .apply(new DoubleBlockCombiner.Combiner<ChestBlockEntity, Boolean>() {
                    @Override public Boolean acceptDouble(ChestBlockEntity a, ChestBlockEntity b) { return a != b; }
                    @Override public Boolean acceptSingle(ChestBlockEntity entity) { return false; }
                    @Override public Boolean acceptNone() { return false; }
                });
        require(combined, "double chest neighbor resolution did not use preview states/entities");

        BlockPos bannerPos = first.above(2);
        BlockState banner = Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST);
        states.put(bannerPos, banner);
        CompoundTag bannerNbt = new CompoundTag(), stripe = new CompoundTag();
        stripe.putString("Pattern", "bs");
        stripe.putInt("Color", DyeColor.RED.getId());
        ListTag patterns = new ListTag();
        patterns.add(stripe);
        bannerNbt.put("Patterns", patterns);
        BannerBlockEntity bannerEntity = (BannerBlockEntity) preview.createBlockEntity(bannerPos, banner, bannerNbt);
        entities.put(bannerPos, bannerEntity);
        require(bannerEntity.getLevel() == preview && bannerEntity.getBlockState().is(Blocks.WHITE_WALL_BANNER)
                && bannerEntity.getBlockState().getValue(WallBannerBlock.FACING) == Direction.WEST, "wall banner orientation lost");
        require(bannerEntity.getPatterns().size() == 2 && bannerEntity.getPatterns().get(1).getSecond() == DyeColor.RED,
                "banner pattern/color NBT lost");
        preview.setPreviewTime(12345L);
        require(bannerEntity.getLevel().getGameTime() == 12345L, "banner animation has no scene clock");

        var chunk = preview.getChunkAt(first);
        require(chunk.getLevel() == preview && chunk.getBlockState(first).equals(left)
                && chunk.getBlockEntity(second) == entities.get(second), "chunk reads escaped scene");
        require(preview.getBlockState(probe).isAir() && preview.getBlockEntity(probe) == null, "missing position read live world");
        states.put(probe, Blocks.GOLD_BLOCK.defaultBlockState());
        require(preview.getBlockState(probe).is(Blocks.GOLD_BLOCK) && source.getBlockState(probe).equals(liveState), "scene overrode real world");
        require(!preview.setBlock(first, Blocks.AIR.defaultBlockState(), 3)
                && !preview.destroyBlock(first, true) && !preview.removeBlock(first, false), "renderer mutated scene blocks");
        chunk.setBlockState(first, Blocks.AIR.defaultBlockState(), false);
        preview.removeBlockEntity(first);
        preview.setBlockEntity(new ChestBlockEntity(first, left));
        require(chunk.getBlockState(first).equals(left) && preview.getBlockEntity(first) == chest, "chunk/entity writes changed scene");
        preview.scheduleTick(first, Blocks.CHEST, 1);
        preview.scheduleTick(first, Fluids.WATER, 1);
        require(preview.getBlockTicks().count() == 0 && preview.getFluidTicks().count() == 0, "preview scheduled gameplay ticks");
        preview.getLevelData().setXSpawn(liveSpawnX + 1000);
        preview.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(!liveRule, null);
        require(source.getLevelData().getXSpawn() == liveSpawnX
                && source.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) == liveRule, "renderer changed real world metadata");
        states.put(first, Blocks.STONE.defaultBlockState());
        entities.remove(first);
        require(chunk.getBlockState(first).is(Blocks.STONE) && chunk.getBlockEntity(first) == null, "cached chunk retained previous candidate");
        require(source.getChunkSource().getLoadedChunksCount() == loadedChunks, "preview generated real chunks at relative positions");
        require(source.getBlockState(probe).equals(liveState) && source.getBlockEntity(probe) == liveEntity, "real state/entity changed");
        require(preview.players().isEmpty() && preview.getEntity(1) == null, "preview exposes live entities");
        preview.close();
        Modularmachinery.LOG.info("[MMCE][regression] PASS: blueprint isolated Level/chunks/metadata/ticks, negative positions, "
                + "world-aware chest facing/double neighbor lookup/inventory NBT, wall banner patterns/color/clock, live-world isolation/candidate refresh");
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
