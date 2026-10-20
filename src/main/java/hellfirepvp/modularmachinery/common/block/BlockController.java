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

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * The machine controller — the block a player right-clicks to form and run a multiblock.
 *
 * <p>State layout is taken verbatim from the shipped {@code blockcontroller.json}, which already
 * keys its variants on {@code facing} (four horizontal values) and {@code formed} (boolean). On
 * 1.12.2 the same two properties existed but the block was rotated with
 * {@code BlockHorizontal#withRotation}/{@code withMirror}; on 1.20.1 those live on the block itself
 * as {@link #rotate} / {@link #mirror} overrides.
 *
 * <p>{@code @SuppressWarnings("deprecation")} is on this class because overriding
 * {@code rotate(BlockState, Rotation)} / {@code mirror(BlockState, Mirror)} — deprecated in 1.20.1
 * ahead of the octahedral-group migration — is nevertheless the only way to make a directional block
 * rotate, and is exactly what vanilla's own {@code HorizontalDirectionalBlock} does.
 */
@SuppressWarnings("deprecation")
public class BlockController extends BlockMachineComponent {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public BlockController(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(FORMED, false));
    }

    /** Capture the placer when a fresh controller is placed; a tagged drop keeps its original owner. */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                            BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            java.util.UUID owner = null;
            if (stack.hasTag() && stack.getTag().hasUUID("owner")) {
                owner = stack.getTag().getUUID("owner");
            } else if (placer instanceof net.minecraft.world.entity.player.Player player) {
                owner = player.getGameProfile().getId();
            }
            controller.setOwner(owner);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    /**
     * Places the controller facing the player, matching the 1.12.2 behaviour of
     * {@code BlockHorizontal} placement.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(FORMED, false);
    }

    /**
     * Rotation-aware state mapping.
     *
     * <p>Note the route taken here deliberately avoids the two deprecated 1.20.1 methods
     * ({@code BlockBehaviour#rotate(BlockState, Rotation)} and {@code BlockStateBase#rotate(Rotation)}).
     * The direction-level primitives {@link Rotation#rotate(net.minecraft.core.Direction)} and
     * {@link Mirror#mirror(net.minecraft.core.Direction)} are the non-deprecated ones, so the property
     * is mapped directly and no deprecation suppression is needed.
     */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos, BlockState state) {
        return new hellfirepvp.modularmachinery.common.tiles.TileController(pos, state);
    }

    /**
     * The 1.12 controller's {@code breakBlock} ejects every stack held by its internal inventory before
     * the tile is removed.  The 1.20 loot table only creates the controller item, so without this hook a
     * blueprint placed in a controller would vanish when the block was mined.  This also applies to the
     * factory controller because it inherits this block implementation and overrides only its block entity.
     */
    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level,
                         net.minecraft.core.BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
                && !level.isClientSide
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            net.minecraftforge.items.IItemHandler inventory = controller.getBlueprintInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                net.minecraft.world.item.ItemStack stack;
                while (!(stack = inventory.extractItem(slot, inventory.getStackInSlot(slot).getCount(), false))
                        .isEmpty()) {
                    net.minecraft.world.level.block.Block.popResource(level, pos, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** Preserve the source controller's owner tag when the block is copied with the middle mouse button. */
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(
            BlockState state, net.minecraft.world.phys.HitResult target,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller
                && controller.getOwner() != null) {
            stack.getOrCreateTag().putUUID("owner", controller.getOwner());
        }
        return stack;
    }

    /**
     * Runs the machine's recipe execution every server tick.
     *
     * <p>1.12.2 delivered ticking through {@code ITickable} on the tile, so every tile ticked whether it
     * needed to or not, and machine work was pushed onto a worker pool. On 1.20.1 the block opts in per
     * block-entity type, and the work is <b>server-thread only</b>: see {@code MachineTicker} for why
     * the async model did not survive the port, and what shape a future optimisation must take.
     *
     * <p>The ticker is returned only for the server side. Returning it for the client would have the
     * client attempt to advance recipes against a world it does not own.
     */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
                hellfirepvp.modularmachinery.common.tiles.MachineTicker.tick(lvl, pos, controller);
            }
        };
    }

    /**
     * Convenience for the structure matcher and GUI code.
     *
     * @return the property set for this block, used when comparing states structurally
     */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state,
                                                     net.minecraft.world.level.Level level,
                                                     net.minecraft.core.BlockPos pos,
                                                     net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
        // Shift-right-click is left to the formation handler (ControllerInteraction), so the two do not
        // both act on one click and form the machine while also opening a window over it.
        if (player.isShiftKeyDown()) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        // AND THE SAME FOR THE CONSTRUCT TOOL (PLAN section 262).
        //
        // Vanilla tries the BLOCK first and the ITEM only if the block passes -- unless the player is
        // sneaking, which skips the block. So with the forming tool in hand and no sneak, this method ran,
        // opened the menu, and the tool's useOn was never reached. The client reported exactly that:
        // "右键是打开gui，shift右键才会报出缺东西" -- plain click opened the GUI, shift-click reported the
        // missing blocks, and nothing formed automatically.
        //
        // Returning PASS hands the click to the item, which is what "holding the tool" should mean.
        if (player.getMainHandItem().getItem()
                instanceof hellfirepvp.modularmachinery.common.item.ItemConstructTool) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        // AND FOR THE PROJECTOR, which is the same rule one item later (PLAN section 294).
        //
        // Section 262 added this for the construct tool and did not generalise it, so the projector hit the
        // identical wall: vanilla tries the BLOCK first, this method opened the menu, and
        // ItemMachineProjector.useOn -- the only caller of renderMachinePreview -- was never reached. The
        // client reported it exactly: "拿投影仪右键是打开机器控制器界面".
        //
        // The lesson is not "add another instanceof" but "this method must decline for every item whose
        // right-click means something on a controller". There are two such items and both are named here.
        if (player.getMainHandItem().getItem()
                instanceof hellfirepvp.modularmachinery.common.item.ItemMachineProjector) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        // The menu is opened on the SERVER only. Opening it on both sides is the classic 1.20.1 mistake:
        // the client would build its own menu from locally-guessed data, and the two would immediately
        // disagree about the machine's state.
        if (!level.isClientSide
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            // NetworkHooks.openScreen rather than Player#openMenu: the extra BlockPos has to be written
            // into the open packet, and the one-argument openMenu cannot carry it. Without the position
            // the client-side factory would have no way to find the controller it is showing.
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider(
                            (windowId, inventory, ignored) -> hellfirepvp.modularmachinery.common.menu
                                    .MenuMachineController.forController(windowId, inventory, controller),
                            net.minecraft.network.chat.Component.translatable(
                                    "gui.modularmachinery.controller")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        // SUCCESS on both sides, not CONSUME: the client must also act on the click so it knows not to
        // swing the arm, and the server must not reject it. Returning PASS here would make the click fall
        // through to item use and the GUI would never open.
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    public static Property<?>[] stateProperties() {
        return new Property<?>[]{FACING, FORMED};
    }
}
