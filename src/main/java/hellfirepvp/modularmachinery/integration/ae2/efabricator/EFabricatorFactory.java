/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import hellfirepvp.modularmachinery.common.efabricator.TileEFabricatorMEChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Keep AE's node subclass out of the common registration method's verifier frames. */
public final class EFabricatorFactory {
    private EFabricatorFactory() { }
    public static TileEFabricatorMEChannel channel(BlockPos pos, BlockState state) {
        return new EFabricatorMEChannelNode(pos, state);
    }
}
