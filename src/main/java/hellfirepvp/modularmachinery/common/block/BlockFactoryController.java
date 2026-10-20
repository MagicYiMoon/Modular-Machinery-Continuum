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
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Factory controller block — {@code modularmachinery:blockfactorycontroller}.
 *
 * <p>State space is identical to {@link BlockController} ({@code facing} × {@code formed}), and the
 * shipped {@code blockfactorycontroller.json} keys on exactly those two properties. Rather than
 * re-declare the properties and re-derive the rotation handling, this extends {@link BlockController}
 * and overrides only the block entity. That matters because {@code BlockController} deliberately
 * routes rotation through the non-deprecated direction-level primitives
 * ({@code Rotation#rotate(Direction)} / {@code Mirror#mirror(Direction)}) instead of the deprecated
 * {@code BlockBehaviour#rotate}/{@code BlockState#rotate} pair — re-deriving it here would risk
 * reintroducing the deprecated path or, worse, diverging in behaviour.
 *
 * <p>The factory controller drives a whole machine <i>family</i> (many controller instances sharing
 * one machine definition) rather than a single multiblock.
 */
public class BlockFactoryController extends BlockController {

    public BlockFactoryController(Properties properties) { this(properties, null); }

    public BlockFactoryController(Properties properties, String machine) {
        super(properties, machine);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFactoryController(pos, state);
    }

    /**
     * Hands the server the FACTORY ticker, not the controller's.
     *
     * <p><b>This override is the wiring, and without it factory mode does nothing.</b> {@link BlockController}
     * answers this with {@code MachineTicker.tick}, which drives a single-craft controller; a factory controller
     * handed to that engine reports "no active recipe" for ever, because its state lives in its runs rather than
     * in the inherited single-craft fields -- so the engine would start a craft and consume its inputs on every
     * tick while the factory itself stayed empty. {@code MachineTicker} also refuses a
     * {@code TileFactoryController} outright as a second line of defence, and the self-test asserts BOTH halves
     * (PLAN 662): the block supplies a ticker that drives a run, and the single-craft engine started by hand on
     * this tile starts nothing.</p>
     */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TileFactoryController factory) {
                hellfirepvp.modularmachinery.common.tiles.FactoryTicker.tick(lvl, pos, factory);
            }
        };
    }

    /**
     * Opens the FACTORY controller screen.
     *
     * <p>Overriding this is the whole point: without it, this block inherits {@code BlockController#use} and
     * opens the machine controller screen instead -- wrong content, and 176 wide where the blueprint slot at
     * x=255 needs 280. See §67.1.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.TileFactoryController factory) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuFactoryController
                                            .forController(windowId, inventory, factory),
                            Component.translatable("gui.modularmachinery.factory_controller")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}