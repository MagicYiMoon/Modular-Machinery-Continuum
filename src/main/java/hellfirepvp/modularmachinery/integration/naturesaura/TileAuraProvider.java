/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A world aura access point, with the source's radius of one block and no private buffer. */
public final class TileAuraProvider extends TileMachineComponent {
    private final boolean input;
    public TileAuraProvider(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean input) {
        super(() -> type, pos, state); this.input = input;
    }
    @Override public IOType getIOType() { return input ? IOType.INPUT : IOType.OUTPUT; }
    public IAuraType auraType() { return IAuraChunk.getAuraChunk(level, worldPosition).getType(); }
    public long amount() {
        long[] result = {IAuraChunk.DEFAULT_AURA};
        // Sum in long: Nature's Aura's convenience int query can wrap for extreme drain spots.
        IAuraChunk.getSpotsInArea(level, worldPosition, 1, (pos, value) -> result[0] += value);
        return result[0];
    }
    public int spotAmount() { return IAuraChunk.getAuraChunk(level, worldPosition).getDrainSpot(worldPosition); }
    public int change(int amount, IOType direction, boolean simulate) {
        if (level == null || level.isClientSide() || amount <= 0) return 0;
        var chunk = IAuraChunk.getAuraChunk(level, worldPosition);
        // Disable aimForZero: a recipe's exact output must also work when crossing depleted aura.
        return direction == IOType.INPUT ? chunk.drainAura(worldPosition, amount, false, simulate)
                : chunk.storeAura(worldPosition, amount, false, simulate);
    }
}
