/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import hellfirepvp.modularmachinery.common.estorage.TileEStorageMEChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loaded only after the AE dependency check. Returning the common base type prevents
 * JVM verification of the always-registered block from resolving an AE host hierarchy.
 */
public final class EStorageChannelFactory {
    private EStorageChannelFactory() { }
    public static TileEStorageMEChannel create(BlockPos pos, BlockState state) {
        return new EStorageMEChannelNode(pos, state);
    }
}
