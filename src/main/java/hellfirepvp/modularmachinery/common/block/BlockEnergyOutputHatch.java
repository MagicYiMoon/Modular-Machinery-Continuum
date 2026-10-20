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
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputHatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Energy output hatch block — {@code modularmachinery:blockenergyoutputhatch}. */
public class BlockEnergyOutputHatch extends BlockEnergyHatch {

    public BlockEnergyOutputHatch(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntity createBlockEntity(EnergyHatchData variant, BlockPos pos, BlockState state) {
        return new TileEnergyOutputHatch(pos, state);
    }

    /**
     * @return upstream's {@code tooltip.energyhatch.out.transfer} ("Transfers %s FE per tick")
     *
     * <p>Read out of {@code BlockEnergyOutputHatch.addInformation}'s body (U56). The input hatch's sentence is a
     * different key, so this is the one line that distinguishes the two blocks' tooltips.</p>
     */
    @Override
    protected String transferTipKey() {
        return "tooltip.energyhatch.out.transfer";
    }
}
