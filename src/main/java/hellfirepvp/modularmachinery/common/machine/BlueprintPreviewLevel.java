/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's isolated DummyWorld scene design.
 */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.*;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * A rendering-only Level for world-aware block entity renderers. State and entity reads use the scene;
 * all gameplay writes, scheduled ticks, sounds and events are discarded. No live Level is retained.
 * Kept free of client classes so isolation and vanilla neighbor lookups can run in server regressions.
 */
public final class BlueprintPreviewLevel extends Level {
    private final Map<BlockPos, BlockState> states;
    private final Map<BlockPos, BlockEntity> entities;
    private final Holder<Biome> biome;
    private final FeatureFlagSet features;
    private final Scoreboard scoreboard = new Scoreboard();
    private final RecipeManager recipes = new RecipeManager();
    private final LevelEntityGetter<Entity> emptyEntities = new LevelEntityGetterAdapter<>(new EntityLookup<>(),
            new EntitySectionStorage<>(Entity.class, key -> Visibility.HIDDEN));
    private final PreviewChunks chunks = new PreviewChunks();

    public BlueprintPreviewLevel(Level template, BlockPos biomeOrigin,
                                 Map<BlockPos, BlockState> states, Map<BlockPos, BlockEntity> entities) {
        super(new Data(template.getDifficulty(), template.getLevelData().isHardcore()), template.dimension(),
                template.registryAccess(), template.dimensionTypeRegistration(), () -> InactiveProfiler.INSTANCE,
                true, false, 0L, 0);
        this.states = states;
        this.entities = entities;
        biome = template.getBiome(biomeOrigin);
        features = template.enabledFeatures();
    }

    public void setPreviewTime(long tick) { ((Data) levelData).time = tick; }
    @Override public WritableLevelData getLevelData() { return levelData; }

    /** Construct and load a detached entity; callers install it only into their private scene map. */
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state, CompoundTag previewNbt) {
        if (!(state.getBlock() instanceof EntityBlock block)) return null;
        BlockEntity entity = block.newBlockEntity(pos, state);
        if (entity != null) {
            entity.setLevel(this);
            if (previewNbt != null) entity.load(previewNbt.copy());
        }
        return entity;
    }
    @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return entities.get(pos); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return biome; }
    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return resolver.getColor(biome.value(), pos.getX(), pos.getZ()); }
    @Override public float getShade(Direction direction, boolean shade) { return 1; }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }
    @Override public int getRawBrightness(BlockPos pos, int darkness) { return 15; }
    @Override public boolean canSeeSky(BlockPos pos) { return true; }
    @Override public int getHeight() { return 4096; }
    @Override public int getMinBuildHeight() { return -2048; }
    @Override public int getSeaLevel() { return 0; }
    @Override public FeatureFlagSet enabledFeatures() { return features; }
    @Override public ChunkSource getChunkSource() { return chunks; }
    @Override public boolean hasChunk(int x, int z) { return true; }
    @Override public List<? extends Player> players() { return List.of(); }
    @Override public Entity getEntity(int id) { return null; }
    @Override protected LevelEntityGetter<Entity> getEntities() { return emptyEntities; }
    @Override public Scoreboard getScoreboard() { return scoreboard; }
    @Override public RecipeManager getRecipeManager() { return recipes; }
    @Override public MapItemSavedData getMapData(String name) { return null; }
    @Override public void setMapData(String name, MapItemSavedData data) { }
    @Override public int getFreeMapId() { return 0; }
    @Override public long nextSubTickCount() { return 0; }
    @Override public LevelTickAccess<Block> getBlockTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public String gatherChunkSourceStats() { return "Blueprint preview"; }

    @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) { return false; }
    @Override public boolean removeBlock(BlockPos pos, boolean moving) { return false; }
    @Override public boolean destroyBlock(BlockPos pos, boolean drop, Entity entity, int recursion) { return false; }
    @Override public void setBlockEntity(BlockEntity entity) { }
    @Override public void removeBlockEntity(BlockPos pos) { }
    @Override public void addBlockEntityTicker(TickingBlockEntity ticker) { }
    @Override public void addFreshBlockEntities(Collection<BlockEntity> entities) { }
    @Override public void blockEntityChanged(BlockPos pos) { }
    @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    @Override public void updateNeighborsAt(BlockPos pos, Block block) { }
    @Override public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, Direction direction) { }
    @Override public void updateNeighbourForOutputSignal(BlockPos pos, Block block) { }
    @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { }
    @Override public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
                                          SoundSource source, float volume, float pitch, long seed) { }
    @Override public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
                                          SoundSource source, float volume, float pitch, long seed) { }
    @Override public void addParticle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) { }
    @Override public void levelEvent(Player player, int event, BlockPos pos, int data) { }
    @Override public void gameEvent(GameEvent event, Vec3 pos, GameEvent.Context context) { }
    @Override public void close() { chunks.cache.clear(); }

    /** Chunk lookups stay local too; never generate or query chunks at blueprint-relative world coordinates. */
    private final class PreviewChunks extends ChunkSource {
        private final Map<Long, LevelChunk> cache = new LinkedHashMap<>(16, .75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, LevelChunk> entry) { return size() > 16; }
        };
        private final LevelLightEngine light = new LevelLightEngine(this, false, false);
        @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
            return cache.computeIfAbsent(ChunkPos.asLong(x, z), key -> new PreviewChunk(new ChunkPos(x, z)));
        }
        @Override public void tick(BooleanSupplier moreTime, boolean tickChunks) { }
        @Override public String gatherStats() { return "Blueprint preview"; }
        @Override public int getLoadedChunksCount() { return cache.size(); }
        @Override public LevelLightEngine getLightEngine() { return light; }
        @Override public BlockGetter getLevel() { return BlueprintPreviewLevel.this; }
    }

    private final class PreviewChunk extends EmptyLevelChunk {
        private PreviewChunk(ChunkPos pos) { super(BlueprintPreviewLevel.this, pos, biome); }
        @Override public BlockState getBlockState(BlockPos pos) { return BlueprintPreviewLevel.this.getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return BlueprintPreviewLevel.this.getFluidState(pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creation) {
            return BlueprintPreviewLevel.this.getBlockEntity(pos);
        }
        @Override public Map<BlockPos, BlockEntity> getBlockEntities() {
            Map<BlockPos, BlockEntity> inChunk = new HashMap<>();
            entities.forEach((pos, entity) -> { if (new ChunkPos(pos).equals(getPos())) inChunk.put(pos, entity); });
            return Collections.unmodifiableMap(inChunk);
        }
        @Override public void addEntity(Entity entity) { }
    }

    private static final class Data implements WritableLevelData {
        private final Difficulty difficulty;
        private final boolean hardcore;
        private final GameRules rules = new GameRules();
        private long time;
        private Data(Difficulty difficulty, boolean hardcore) { this.difficulty = difficulty; this.hardcore = hardcore; }
        @Override public int getXSpawn() { return 0; }
        @Override public int getYSpawn() { return 0; }
        @Override public int getZSpawn() { return 0; }
        @Override public float getSpawnAngle() { return 0; }
        @Override public long getGameTime() { return time; }
        @Override public long getDayTime() { return time; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isRaining() { return false; }
        @Override public void setRaining(boolean raining) { }
        @Override public boolean isHardcore() { return hardcore; }
        @Override public GameRules getGameRules() { return rules; }
        @Override public Difficulty getDifficulty() { return difficulty; }
        @Override public boolean isDifficultyLocked() { return true; }
        @Override public void setXSpawn(int x) { }
        @Override public void setYSpawn(int y) { }
        @Override public void setZSpawn(int z) { }
        @Override public void setSpawnAngle(float angle) { }
    }
}
