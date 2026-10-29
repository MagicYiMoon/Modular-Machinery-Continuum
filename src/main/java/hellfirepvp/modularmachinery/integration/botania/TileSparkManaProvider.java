/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.botania;

import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.api.*;
import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.mana.*;
import vazkii.botania.api.mana.spark.*;
import java.util.Optional;

/** Dedicated million-mana storage; native sparks own all transfer amounts and network rules. */
public class TileSparkManaProvider extends TileManaProvider implements SparkAttachable, Wandable {
    public static final int CAPACITY = 1_000_000;
    private LazyOptional<SparkAttachable> spark = LazyOptional.of(() -> this);
    private LazyOptional<Wandable> wand = LazyOptional.of(() -> this);
    private int scanDelay;
    public TileSparkManaProvider(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public int capacity() { return CAPACITY; }
    private boolean live() {
        return !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }
    @Override public long take(long amount, boolean simulate) { return live() ? super.take(amount, simulate) : 0; }
    @Override public long add(long amount, boolean simulate) { return live() ? super.add(amount, simulate) : 0; }
    @Override public void receiveMana(int amount) {
        // Output is an externally full source. Internal recipe production uses add, not receiveMana.
        if (live() && (getIOType() == IOType.INPUT || amount < 0)) super.receiveMana(amount);
    }
    @Override public boolean isFull() { return getIOType() == IOType.OUTPUT || super.isFull(); }
    @Override public boolean canAttachSpark(ItemStack stack) { return level != null && !isRemoved(); }
    @Override public int getAvailableSpaceForMana() {
        return getIOType() == IOType.INPUT ? Math.max(0, capacity() - getCurrentMana()) : 0;
    }
    @Override public boolean areIncomingTranfersDone() { return getIOType() == IOType.OUTPUT || getAvailableSpaceForMana() == 0; }
    @Override public ManaSpark getAttachedSpark() {
        if (level == null || isRemoved() || (level instanceof ServerLevel server && !server.getServer().isSameThread())) return null;
        var entities = level.getEntitiesOfClass(Entity.class, new AABB(worldPosition.above()),
                entity -> entity.isAlive() && entity instanceof ManaSpark attached && attached.getAttachPos().equals(worldPosition));
        return entities.size() == 1 ? (ManaSpark) entities.get(0) : null;
    }
    @Override public void attachSpark(ManaSpark entity) { scanDelay = 0; }
    @Override public void tick() {
        if (!live() || getIOType() != IOType.INPUT || isFull()) return;
        if (scanDelay > 0) { scanDelay--; return; }
        scanDelay = 9;
        var own = getAttachedSpark();
        if (own == null || own.getUpgrade() == SparkUpgradeType.ISOLATED) return;
        for (var other : SparkHelper.getSparksAround(level, own.entity().getX(), own.entity().getY(), own.entity().getZ(), own.getNetwork())) {
            if (other != own && other.getUpgrade() != SparkUpgradeType.ISOLATED
                    && other.getAttachedManaReceiver() instanceof ManaPool && other.getAttachedTile() != null)
                other.registerTransfer(own);
        }
    }
    @Override public boolean onUsedByWand(Player player, ItemStack stack, Direction side) {
        if (live()) {
            markForUpdate();
            player.displayClientMessage(Component.translatable("message.modularmachinery.mana.stored", getCurrentMana(), capacity()), true);
        }
        return true;
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == BotaniaForgeCapabilities.SPARK_ATTACHABLE) return spark.cast();
        if (cap == BotaniaForgeCapabilities.WANDABLE) return wand.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); spark.invalidate(); wand.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps(); spark = LazyOptional.of(() -> this); wand = LazyOptional.of(() -> this); scanDelay = 0;
    }
    @Override public void load(net.minecraft.nbt.CompoundTag tag) {
        var checked = tag.copy(); checked.putInt("mana", Math.min(capacity(), Math.max(0, tag.getInt("mana"))));
        super.load(checked); scanDelay = 0;
    }
    /** Native ManaPool discovery allows dominant pools and recessive output sparks to export recipe mana. */
    public static final class Output extends TileSparkManaProvider implements ManaPool {
        public Output(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public IOType getIOType() { return IOType.OUTPUT; }
        @Override public boolean isOutputtingPower() { return true; }
        @Override public int getMaxMana() { return capacity(); }
        @Override public Optional<DyeColor> getColor() { return Optional.of(DyeColor.WHITE); }
        @Override public void setColor(Optional<DyeColor> color) { }
        @Override public void onLoad() { super.onLoad(); network(ManaNetworkAction.ADD); }
        @Override public void setRemoved() { network(ManaNetworkAction.REMOVE); super.setRemoved(); }
        @Override public void onChunkUnloaded() { network(ManaNetworkAction.REMOVE); super.onChunkUnloaded(); }
        private void network(ManaNetworkAction action) {
            if (level != null) BotaniaAPI.instance().getManaNetworkInstance().fireManaNetworkEvent(this, ManaBlockType.POOL, action);
        }
    }
}
