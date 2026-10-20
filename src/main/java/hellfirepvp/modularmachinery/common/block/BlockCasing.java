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

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Machine casing — the structural filler block of every multiblock.
 *
 * <p>Six variants selected by the {@link CasingType} blockstate property. In 1.12.2 these were block
 * metadata values rendered through {@code getStateFromMeta}/{@code getMetaFromState}; on 1.20.1 they
 * are ordinary blockstate properties, so the variants become first-class states and the shipped
 * {@code blockcasing.json} needs no transformation beyond the path fixes applied to all assets.
 *
 * <p>A casing owns no block entity: it is pure structure, identified by the structure matcher for what
 * it is rather than for what it does.
 */
public class BlockCasing extends BlockMachineComponent {

    private static final EnumProperty<CasingType> CASING = CasingType.createProperty();

    public BlockCasing(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CASING, CasingType.PLAIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CASING);
    }
}
