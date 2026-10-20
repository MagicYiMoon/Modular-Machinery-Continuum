/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyInputHatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Energy input hatch block — {@code modularmachinery:blockenergyinputhatch}. */
public class BlockEnergyInputHatch extends BlockEnergyHatch {

    public BlockEnergyInputHatch(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntity createBlockEntity(EnergyHatchData variant, BlockPos pos, BlockState state) {
        return new TileEnergyInputHatch(pos, state);
    }

    /**
     * @return upstream's {@code tooltip.energyhatch.in.accept} ("Accepts %s FE per tick")
     *
     * <p>Read out of {@code BlockEnergyInputHatch.addInformation}'s body (U56), which is the only thing that tells
     * the two directions apart: the two hatch blocks are identical in every other respect.</p>
     */
    @Override
    protected String transferTipKey() {
        return "tooltip.energyhatch.in.accept";
    }
}
