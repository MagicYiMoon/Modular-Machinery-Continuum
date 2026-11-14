/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ECalculatorMEChannel. */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Same registry identity and opaque node tag with or without AE2. */
public class TileECalculatorMEChannel extends TileECalculatorPart {
    protected CompoundTag nodeData = new CompoundTag();
    public TileECalculatorMEChannel(BlockPos pos, BlockState state) { super(pos, state); }
    public void setPlacingPlayer(Player player) { }
    public boolean isNetworkActive() { return false; }
    protected void disconnect() { }
    @Override public void unbind() { disconnect(); super.unbind(); }
    @Override public void load(CompoundTag tag) { super.load(tag); nodeData = tag.getCompound("MENode").copy(); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if (!nodeData.isEmpty()) tag.put("MENode", nodeData.copy()); }
}
