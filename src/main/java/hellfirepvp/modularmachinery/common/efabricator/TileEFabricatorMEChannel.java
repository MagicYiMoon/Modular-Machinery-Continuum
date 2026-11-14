/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EFabricatorMEChannel. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Common registry identity and opaque AE node data, including when AE2 is absent. */
public class TileEFabricatorMEChannel extends TileEFabricatorPart {
    protected CompoundTag nodeData = new CompoundTag();
    public TileEFabricatorMEChannel(BlockPos pos, BlockState state) { super(pos, state); }
    public void setPlacingPlayer(Player player) { }
    public boolean isNetworkActive() { return false; }
    public double extractWorkEnergy(double requested) { return 0; }
    public long insertOutput(net.minecraft.world.item.ItemStack item, long amount) { return 0; }
    protected void disconnect() { }
    @Override public void unbind() { disconnect(); super.unbind(); }
    @Override public void load(CompoundTag tag) { super.load(tag); nodeData = tag.getCompound("MENode").copy(); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); if (!nodeData.isEmpty()) tag.put("MENode", nodeData.copy()); }
}
