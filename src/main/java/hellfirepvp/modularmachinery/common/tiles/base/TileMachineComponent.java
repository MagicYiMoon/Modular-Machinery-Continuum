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
package hellfirepvp.modularmachinery.common.tiles.base;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base block entity for a functional machine component (a hatch, bus or controller part).
 *
 * <p>Adds the controller binding that {@link MachineComponentTile} requires and persists it. The
 * binding is deliberately stored as a {@link BlockPos} rather than a direct reference: a component
 * and its controller routinely live in different chunks, and on 1.20.1 holding a strong reference to
 * a {@code BlockEntity} across chunk boundaries is how you leak or crash on unload. The position is a
 * stable handle that survives save/reload, which is also what makes the structure matcher's
 * invalidation logic tractable.
 */
public abstract class TileMachineComponent extends TileEntityBase
        implements MachineComponentTile, ColorableMachineTile, MachineGroupInput {

    private BlockPos controllerPos;

    protected TileMachineComponent(java.util.function.Supplier<? extends BlockEntityType<?>> type,
                                   BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void setControllerPos(BlockPos controllerPos) {
        // MARK ONLY ON A CHANGE, AND THAT IS LOAD-BEARING RATHER THAN TIDY (PLAN 683).
        //
        // The periodic structure re-check re-binds every component of every formed machine (MachineFormation.
        // bindComponents, once per 50 ticks -- upstream's own updateComponents-on-every-check, U556), and this
        // method is what that walk calls. Marking unconditionally would make an IDLE machine that changed
        // nothing push a block-entity update and dirty a chunk for every hatch it owns, every 2.5 seconds --
        // which is why the factory sync-cadence check (MachineSelfTest, PLAN 664) caught it: `an idle factory
        // tick must leave the chunk clean`. Upstream's own idiom is the same guard one level up: its
        // distributeCasingColor only writes when `colorable.getMachineColor() != color`, and TileController.
        // setFormed only fires its events when the flag actually moves.
        if (java.util.Objects.equals(this.controllerPos, controllerPos)) {
            return;
        }
        this.controllerPos = controllerPos;
        markForUpdate();
    }

    @Override
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    // --- Colour and group input (§154) ---
    //
    // Upstream keeps these on `TileColorableMachineComponent`, a class this port never ported. They live here
    // instead, because every hatch and bus already extends this class -- so the two interfaces are implemented
    // by something real rather than added as a layer with no consumers (§154).
    //
    // `canGroupInput` is a separate question from `isGroupInput` (see the interface's javadoc): capability
    // versus configuration. This base is capable; subclasses that cannot be a group input override it.

    /** Player-chosen colour as ARGB, or 0 when unset. */
    private int definedColor;

    /** The group this component feeds, or 0 when unset. */
    private int groupId;

    /** Whether this component is configured to act as a group input. */
    private boolean groupInput;

    @Override
    public int getMachineColor() {
        return definedColor;
    }

    @Override
    public void setMachineColor(int color) {
        this.definedColor = color;
        markForUpdate();
    }

    /**
     * @return the group this component feeds while it is configured as a group input, or -1 when it is not
     *
     * <p><b>UPSTREAM'S OWN RULE, READ AT THE BODY (rule A):</b> {@code TileColorableMachineComponent.java:104-108}
     * is, verbatim,
     *
     * <pre>
     *   public int getGroupId() {
     *       if (isGroupInput()) return groupId;
     *       return -1;
     *   }
     * </pre>
     *
     * <p>Upstream's {@code MachineGroupInput} states the same rule in words ("isGroupInput 返回 false 时，
     * getGroupId() 应当返回 -1"), and upstream's controller reads it back at
     * {@code TileMultiblockMachineController.java:206}, where {@code groupId < 0} is what means "belongs to no
     * group".
     *
     * <p>THE PORT RETURNED THE STORED FIELD UNCONDITIONALLY, and the difference is visible in the port's own GUI
     * rather than only in theory: {@code MenuGroupInputConfig.wireGroupSlots} sends this value through a data
     * slot and {@code ScreenGroupInputConfig} prints it, so switching a hatch OUT of group input left the old
     * group number on screen where upstream shows -1. Same shape as §629 -- two readers of one rule
     * disagreeing -- except the disagreement was between this getter and the flag that gates it.
     */
    @Override
    public int getGroupId() {
        return groupInput ? groupId : -1;
    }

    @Override
    public void setGroupId(int groupId) {
        this.groupId = groupId;
        markForUpdate();
    }

    @Override
    public boolean canGroupInput() {
        return true;
    }

    @Override
    public boolean isGroupInput() {
        return groupInput;
    }

    @Override
    public void setGroupInput(boolean groupInput) {
        this.groupInput = groupInput;
        markForUpdate();
    }

    @Override
    public void load(CompoundTag tag) {

        super.load(tag);
        if (tag.contains("controllerPos")) {
            this.controllerPos = BlockPos.of(tag.getLong("controllerPos"));
        } else {
            this.controllerPos = null;
        }
        this.definedColor = tag.getInt("definedColor");
        this.groupId = tag.getInt("groupId");
        this.groupInput = tag.getBoolean("groupInput");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controllerPos != null) {
            tag.putLong("controllerPos", controllerPos.asLong());
        }
        tag.putInt("definedColor", definedColor);
        tag.putInt("groupId", groupId);
        tag.putBoolean("groupInput", groupInput);
    }
}
