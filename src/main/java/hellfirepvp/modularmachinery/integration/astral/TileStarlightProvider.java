/* SPDX-License-Identifier: GPL-3.0-only
 * Source mechanics: ModularMagic TileStarlightInput/Output; modern community Astral API bridge.
 */
package hellfirepvp.modularmachinery.integration.astral;

import hellfirepvp.astralsorcery.common.auxiliary.link.LinkableTileEntity;
import hellfirepvp.astralsorcery.common.constellation.*;
import hellfirepvp.astralsorcery.common.constellation.world.CelestialHandler;
import hellfirepvp.astralsorcery.common.starlight.*;
import hellfirepvp.astralsorcery.common.util.world.SkyCollectionHelper;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public abstract class TileStarlightProvider extends TileMachineComponent implements LinkableTileEntity {
    private boolean registered;
    private final Set<BlockPos> links = new LinkedHashSet<>();
    protected TileStarlightProvider(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(() -> type, pos, state); }
    protected abstract void registerNode();
    protected final void ensureRegistered() {
        if (level instanceof ServerLevel && !registered && !isRemoved()) { registerNode(); registered = true; }
    }
    @Override public void tick() {
        ensureRegistered();
        if (level instanceof ServerLevel && level.getGameTime() % 20 == 0) {
            for (BlockPos at : List.copyOf(links)) {
                if (!level.hasChunkAt(at)) continue;
                var other = level.getBlockEntity(at);
                // A peer may have been unlinked while this chunk was unloaded. Do not resurrect its old saved edge.
                if (other instanceof TileStarlightProvider peer && !peer.links.contains(worldPosition)) {
                    links.remove(at); setChanged(); continue;
                }
                if (getIOType() == IOType.OUTPUT && (other instanceof IStarlightReceiver || other instanceof IStarlightTransmission)) StarlightNetworkHelper.addLink(level, worldPosition, at);
                else if (getIOType() == IOType.INPUT && (other instanceof IStarlightSource || other instanceof IStarlightTransmission)) StarlightNetworkHelper.addLink(level, at, worldPosition);
            }
        }
    }
    private void unregister() {
        if (level instanceof ServerLevel && registered) StarlightNetworkHelper.removeNode(level, worldPosition);
        registered = false;
    }
    @Override public void setRemoved() { unregister(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { unregister(); super.onChunkUnloaded(); }
    @Override public void onLoad() { super.onLoad(); registered = false; }
    @Override public Level getLinkWorld() { return level; }
    @Override public BlockPos getLinkPos() { return worldPosition; }
    public BlockPos getLocationPos() { return worldPosition; }
    @Override public Component getUnLocalizedDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public boolean doesAcceptLinks() { return true; }
    @Override public void onBlockLinkCreate(Player player, BlockPos other) { }
    @Override public void onEntityLinkCreate(Player player, Entity entity) { }
    @Override public boolean tryLinkEntity(Player player, Entity entity) { return false; }
    @Override public boolean onSelect(Player player) {
        if (player.isShiftKeyDown()) { for (BlockPos at : List.copyOf(links)) tryUnlink(player, at); return false; }
        return true;
    }
    @Override public boolean tryLinkBlock(Player player, BlockPos at) {
        if (!(level instanceof ServerLevel) || !level.hasChunkAt(at) || at.equals(worldPosition)
                || worldPosition.distSqr(at) > 64 * 64 || (!links.contains(at) && links.size() >= 16)) return false;
        var other = level.getBlockEntity(at);
        if (other instanceof TileStarlightProvider provider) {
            if (!provider.links.contains(worldPosition) && provider.links.size() >= 16) return false;
            provider.ensureRegistered();
        }
        ensureRegistered();
        boolean output = getIOType() == IOType.OUTPUT;
        if (output ? !(other instanceof IStarlightReceiver || other instanceof IStarlightTransmission)
                : !(other instanceof IStarlightSource || other instanceof IStarlightTransmission)) return false;
        BlockPos from = output ? worldPosition : at, to = output ? at : worldPosition;
        var handler = StarlightNetworkHelper.getHandler(level);
        boolean accepted = handler != null && (handler.getOutgoingTargets(from).contains(to) || handler.addLink(from, to));
        if (accepted && links.add(at.immutable())) markForUpdate();
        if (accepted && other instanceof TileStarlightProvider peer && peer.links.add(worldPosition.immutable())) peer.markForUpdate();
        return accepted;
    }
    @Override public boolean tryUnlink(Player player, BlockPos at) {
        if (!links.remove(at)) return false;
        if (level instanceof ServerLevel) {
            StarlightNetworkHelper.removeLink(level, worldPosition, at);
            StarlightNetworkHelper.removeLink(level, at, worldPosition);
            if (level.hasChunkAt(at) && level.getBlockEntity(at) instanceof LinkableTileEntity peer
                    && peer.getLinkedPositions().contains(worldPosition)) peer.tryUnlink(player, worldPosition);
        }
        markForUpdate(); return true;
    }
    @Override public List<BlockPos> getLinkedPositions() { return List.copyOf(links); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putLongArray("starlightLinks", links.stream().mapToLong(BlockPos::asLong).toArray()); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); links.clear();
        for (long value : tag.getLongArray("starlightLinks")) {
            BlockPos at = BlockPos.of(value);
            if (!at.equals(worldPosition) && at.distSqr(worldPosition) <= 64 * 64 && links.size() < 16) links.add(at);
        }
    }

    public static final class Input extends TileStarlightProvider implements IStarlightReceiver {
        private int stored;
        public Input(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public IOType getIOType() { return IOType.INPUT; }
        @Override protected void registerNode() { StarlightNetworkHelper.registerReceiver(level, worldPosition, this); }
        public int stored() { return stored; }
        @Override public double getMaxStarlightInput() { return 50; }
        @Override public Level getReceiverLevel() { return level; }
        @Override public void receiveStarlight(double amount, ResourceLocation constellation) {
            if (!(level instanceof ServerLevel) || !Double.isFinite(amount) || amount <= 0.001) return;
            int next = (int) Math.min(10000, stored + amount * 200);
            if (next != stored) { stored = next; setChanged(); }
        }
        @Override public void tick() {
            super.tick();
            if (!(level instanceof ServerLevel server)) return;
            int before = stored; stored = Math.round(stored * 0.95F);
            int y = worldPosition.getY();
            if (y > 40 && level.dimensionType().hasSkyLight() && level.canSeeSky(worldPosition.above()) && SkyHandler.getContext(level) != null) {
                float height = y > 120 ? 1F + (y - 120) / 272F : (y - 20) / 100F;
                float distribution = SkyCollectionHelper.getSkyNoiseDistribution(server, worldPosition);
                float collect = 160 * height * (0.6F + 0.4F * distribution) * (0.2F + 0.8F * CelestialHandler.getTimeOfDayFactor(level));
                stored = Math.round(Math.min(10000, stored + collect));
            }
            if (before != stored) setChanged();
        }
        @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putInt("starlight", stored); }
        @Override public void load(CompoundTag tag) { super.load(tag); stored = Math.max(0, Math.min(10000, tag.getInt("starlight"))); }
    }

    public static final class Output extends TileStarlightProvider implements IStarlightSource {
        private double production;
        private long lastOffer = Long.MIN_VALUE;
        private ResourceLocation constellation;
        public Output(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public IOType getIOType() { return IOType.OUTPUT; }
        @Override protected void registerNode() { StarlightNetworkHelper.registerSource(level, worldPosition, this); }
        @Override public Level getSourceLevel() { return level; }
        @Override public boolean hasSkyAccess() { return true; } // Generated by recipes, not collected from the sky.
        @Override public double getStarlightProduction() { return level != null && lastOffer != Long.MIN_VALUE && level.getGameTime() - lastOffer <= 2 ? production : 0; }
        public void offer(double amount) {
            if (!(level instanceof ServerLevel) || !Double.isFinite(amount) || amount < 0) return;
            long now = level.getGameTime();
            production = Math.min(Float.MAX_VALUE, (lastOffer == now ? production : 0) + amount);
            lastOffer = now;
        }
        @Override public ResourceLocation getAttunedConstellationKey() {
            if (constellation == null) constellation = ConstellationRegistry.getWeakConstellations().stream().findFirst().map(c -> c.getRegistryName()).orElse(null);
            return constellation;
        }
        public void cycleConstellation() {
            var all = ConstellationRegistry.getWeakConstellations().stream().map(c -> c.getRegistryName()).toList();
            if (all.isEmpty()) return;
            constellation = all.get((all.indexOf(getAttunedConstellationKey()) + 1) % all.size());
            // Astral re-registration drops outgoing links; restore the known links immediately.
            var handler = StarlightNetworkHelper.getHandler(level);
            var targets = handler == null ? List.<BlockPos>of() : List.copyOf(handler.getOutgoingTargets(worldPosition));
            registerNode();
            targets.forEach(at -> StarlightNetworkHelper.addLink(level, worldPosition, at));
            markForUpdate();
        }
        @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if (getAttunedConstellationKey() != null) tag.putString("constellation", constellation.toString()); }
        @Override public void load(CompoundTag tag) {
            super.load(tag); constellation = ResourceLocation.tryParse(tag.getString("constellation"));
            // Runtime production is intentionally not restored from disk.
            production = 0; lastOffer = Long.MIN_VALUE;
        }
        @Override public void onChunkUnloaded() { production = 0; lastOffer = Long.MIN_VALUE; super.onChunkUnloaded(); }
    }
}
