/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.base;

import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;































 
public abstract class MEMachineComponent extends BlockEntity implements IInWorldGridNodeHost,
        appeng.api.networking.security.IActionHost, MachineComponentTile {

    /** Controller binding required when an AE2 bus is used as a multiblock component. */
    private BlockPos controllerPos;

    /**
     * The node AE2 manages for this block.
     *
     * <p>The listener is an instance created once, and only {@code onSaveChanges} is overridden: it is the sole abstract
     * method on {@link IGridNodeListener} on 1.20.1, and it is where a change the node decides to persist is written back
     * through {@link #setChanged()}.
     */
    private final IManagedGridNode aeProxy = GridHelper.createManagedNode(this, new IGridNodeListener<MEMachineComponent>() {
        @Override
        public void onSaveChanges(MEMachineComponent owner, IGridNode node) {
            owner.setChanged();
        }
    });

    




 
    protected MEMachineComponent(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos,
                                 BlockState state) {
        super(type.get(), pos, state);
        // Upstream's two settings, unchanged: a channel is required, and the node costs one unit of idle power.
        aeProxy.setIdlePowerUsage(1.0D);
        aeProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        aeProxy.setInWorldNode(true);
    }

    /**
     * @return a stack standing for this block, used as the node's visual representation in AE2's own debug views
     *
     * <p>Abstract rather than a field because the answer differs per subclass and comes from that subclass's own block, which
     * is why upstream declares it abstract too.
     */
    public ItemStack getVisualItemStack() {
        // A DEFAULT, added after the THIRD subclass in a row failed to compile without it: MEItemInputBus (section 400), both
        // fluid buses (section 442's round), and the pattern provider tile (section 445).
        //
        // Abstract was described as right because "the answer differs per subclass and comes from that subclass's own block" --
        // and the second half of that is exactly why a default works: getBlockState() IS the subclass's own block, and every
        // implementation actually written in this project is this same line. A subclass that wants something else still
        // overrides it, so nothing is lost and three compile rounds are not repeated.
        return new ItemStack(getBlockState().getBlock());
    }

    /** @return the node AE2 manages; subclasses use it to open a storage channel or read the grid */
    public IManagedGridNode getMainNode() {
        return aeProxy;
    }

    @Override
    public void setControllerPos(@Nullable BlockPos controllerPos) {
        if (java.util.Objects.equals(this.controllerPos, controllerPos)) {
            return;
        }
        this.controllerPos = controllerPos;
        setChanged();
    }

    @Override
    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Rule 1: the level and position both exist by now, and neither did in the constructor.
        if (level != null && !level.isClientSide()) {
            aeProxy.create(level, worldPosition);
            // The visual representation needs the block, which is also only available now.
            ItemStack visual = getVisualItemStack();
            if (!visual.isEmpty()) {
                aeProxy.setVisualRepresentation(visual);
            }
        }
    }

    @Override
    public void setRemoved() {
        // Rule 2: a node that outlives its block stays on the grid and keeps drawing idle power for a block that is gone.
        aeProxy.destroy();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Rule 3: AE2 persists the node's identity; skipping this makes the bus reconnect as a NEW node on every load.
        aeProxy.saveToNBT(tag);
        if (controllerPos != null) {
            tag.putLong("controllerPos", controllerPos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        aeProxy.loadFromNBT(tag);
        controllerPos = tag.contains("controllerPos", CompoundTag.TAG_LONG)
                ? BlockPos.of(tag.getLong("controllerPos")) : null;
    }


    @Override
    public void clearRemoved() {
         
        //
        // It was, and a user-reported crash found it:
        //
        //     StackOverflowError: Ticking block entity
        //       -> GridHelper.getNodeHost -> Level.getBlockEntity(neighbour)
        //       -> the neighbour is CONSTRUCTED -> its clearRemoved -> its node -> ITS neighbours -> repeat
        //
        // clearRemoved RUNS WHILE THE CHUNK IS STILL ASSEMBLING BLOCK ENTITIES, and creating a node there
        // makes AE2 SCAN NEIGHBOURS SYNCHRONOUSLY -- which constructs them, which creates THEIR nodes,
        // recursively with no yield. onLoad ABOVE already does this correctly and runs once the chunk own
        // block entities are registered.
        //
        // THE TWO CALLS WERE REDUNDANT AS WELL AS HARMFUL: one node, one creation site. A block entity
        // moved by a piston or restored with its chunk comes back through onLoad as well, so nothing is lost.
        super.clearRemoved();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return aeProxy.getNode();
    }

    /**
     * The node this machine acts THROUGH, for AE2's action-source bookkeeping.
     *
     * <p><b>This is the method §394 deleted, and deleting it was the wrong diagnosis.</b> That section recorded
     * "getActionableNode() does not exist on 15.x -- it is a 1.12.2 method", based on the first compile rejecting the override.
     * It does exist; it belongs to {@link appeng.api.networking.security.IActionHost}, not to
     * {@link IInWorldGridNodeHost}.
     *
     * <p>Checked with {@code javap} rather than recalled:</p>
     *
     * <pre>
     * IActionHost           { IGridNode getActionableNode(); }
     * IInWorldGridNodeHost  { IGridNode getGridNode(Direction); default AECableType getCableConnectionType(Direction); }
     * </pre>
     *
     * <p><b>A machine block entity implements BOTH, and they answer different questions:</b> the first is how AE2 FINDS this
     * node in the world, the second is which node an action taken BY this machine originates from --
     * {@code IActionSource.ofMachine(IActionHost)} needs exactly this. A class that implements only the first is discoverable and
     * cannot act.
     */
    @Override
    public IGridNode getActionableNode() {
        return aeProxy.getNode();
    }
}
