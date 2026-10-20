/* SPDX-License-Identifier: GPL-3.0-only
 * Port of ModularMagic's mana input and output providers using Botania's 1.20.1 API.
 */
package hellfirepvp.modularmachinery.integration.botania;

import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.*;
import java.util.Optional;

public class TileManaProvider extends TileMachineComponent implements ManaReceiver {
    private int mana;
    private LazyOptional<ManaReceiver> receiver = LazyOptional.of(() -> this);
    public TileManaProvider(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(() -> type, pos, state); }
    @Override public IOType getIOType() { return IOType.INPUT; }
    public int capacity() { return Config.manaProviderCapacity(); }
    @Override public int getCurrentMana() { return mana; }
    @Override public Level getManaReceiverLevel() { return level; }
    @Override public BlockPos getManaReceiverPos() { return worldPosition; }
    @Override public boolean isFull() { return mana >= capacity(); }
    @Override public boolean canReceiveManaFromBursts() { return getIOType() == IOType.INPUT; }
    @Override public void receiveMana(int amount) {
        int next = (int) Math.max(0L, Math.min(capacity(), (long) mana + amount));
        if (next != mana) { mana = next; markForUpdate(); }
    }
    public long take(long amount, boolean simulate) {
        int taken = (int) Math.min(Math.max(0, amount), mana);
        if (!simulate && taken > 0) { mana -= taken; markForUpdate(); }
        return taken;
    }
    public long add(long amount, boolean simulate) {
        int added = (int) Math.min(Math.max(0, amount), Math.max(0, capacity() - mana));
        if (!simulate && added > 0) { mana += added; markForUpdate(); }
        return added;
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == BotaniaForgeCapabilities.MANA_RECEIVER ? receiver.cast() : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); receiver.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); receiver = LazyOptional.of(() -> this); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putInt("mana", mana); }
    @Override public void load(CompoundTag tag) { super.load(tag); mana = Math.max(0, tag.getInt("mana")); }

    /** Like the source output provider, it is discoverable as an outputting mana pool. */
    public static final class Output extends TileManaProvider implements ManaPool {
        public Output(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public IOType getIOType() { return IOType.OUTPUT; }
        @Override public boolean isOutputtingPower() { return true; }
        @Override public int getMaxMana() { return capacity(); }
        @Override public Optional<DyeColor> getColor() { return Optional.of(DyeColor.WHITE); }
        @Override public void setColor(Optional<DyeColor> color) {}
        @Override public void onLoad() { super.onLoad(); network(ManaNetworkAction.ADD); }
        @Override public void setRemoved() { network(ManaNetworkAction.REMOVE); super.setRemoved(); }
        @Override public void onChunkUnloaded() { network(ManaNetworkAction.REMOVE); super.onChunkUnloaded(); }
        private void network(ManaNetworkAction action) {
            if (level != null) BotaniaAPI.instance().getManaNetworkInstance().fireManaNetworkEvent(this, ManaBlockType.POOL, action);
        }
    }
}
