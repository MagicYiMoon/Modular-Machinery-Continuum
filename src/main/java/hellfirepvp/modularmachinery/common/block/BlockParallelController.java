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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

import hellfirepvp.modularmachinery.common.block.prop.ParallelControllerData;
import hellfirepvp.modularmachinery.common.tiles.TileParallelController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Parallel controller block — {@code modularmachinery:blockparallelcontroller}.
 *
 * <p>Keyed on {@code type=} over {@link ParallelControllerData} ({@code normal} … {@code ultimate}),
 * matching the shipped blockstate JSON. Upstream stored the tier in block metadata and passed the
 * resulting parallelism value into the tile constructor ({@code TileParallelController(int)}); on
 * 1.20.1 the tile reads the tier from the blockstate instead, consistent with every other component.
 */
public class BlockParallelController extends BlockVariantComponent<ParallelControllerData> {

    /** Read by {@code TileParallelController} to size the controller; see {@link BlockVariantComponent}. */
    public static final EnumProperty<ParallelControllerData> TYPE = ParallelControllerData.createProperty();

    public BlockParallelController(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, ParallelControllerData.NORMAL));
    }

    @Override
    protected EnumProperty<ParallelControllerData> variantProperty() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Upstream {@code BlockParallelController.addInformation} at its L47-L51 emits exactly this line, with
     * exactly this key and {@code data.getMaxParallelism()} as its argument. The argument now comes from
     * {@link ParallelControllerData#getTipValue()}, i.e. from the tier itself.</p>
     */
    @Override
    protected String tipKey() {
        return "block.modularmachinery.blockparallelcontroller.tip";
    }

    @Override
    protected BlockEntity createBlockEntity(ParallelControllerData variant, BlockPos pos, BlockState state) {
        return new TileParallelController(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // The screen adjusts the value through the channel (PktSetParallelism); this javadoc used to say the
        // display was read-only "because this port has no network channel", which stopped being true when the
        // channel was added -- corrected here rather than left as a plausible-looking stale note.
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof TileParallelController controller) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuParallelController
                                            .forController(windowId, inventory, controller),
                            Component.translatable("gui.modularmachinery.parallel_controller")),
                    // THE SETTING TRAVELS WITH THE POSITION (defect A): the client's data slots are seeded
                    // from this payload, so the screen shows the block's value as soon as it opens rather
                    // than reading 0 until the ContainerData sync catches up. See MenuParallelController.
                    buffer -> hellfirepvp.modularmachinery.common.menu.MenuParallelController
                            .writeOpenData(buffer, controller));
        }
        return InteractionResult.SUCCESS;
    }
}