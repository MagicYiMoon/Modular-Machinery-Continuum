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

import hellfirepvp.modularmachinery.common.block.prop.ItemBusSize;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base for an item bus (input or output).
 *
 * <p><b>1.12.2 → 1.20.1 capability model.</b> Upstream kept an {@code IItemHandler} field and handed
 * it straight back from {@code TileEntity#getCapability}. On 1.20.1 a capability is exposed as a
 * {@link LazyOptional}: consumers subscribe to it and expect it to be invalidated when the block
 * entity goes away. Returning a bare handler still compiles but leaves consumers holding a reference
 * into an unloaded chunk, which is the standard source of late-game item duplication and crashes.
 *
 * <p>The 1.12.2 input/output distinction was a per-block flag consulted by the machine's transfer
 * logic. It is modelled here as {@link IOType}, and it also governs {@code isItemValid}: an output bus
 * must not accept external insertion, which is precisely the rule that prevents output slots from
 * being used as free storage.
 *
 * <p>Slot count comes from the blockstate's
 * {@link hellfirepvp.modularmachinery.common.block.prop.ItemBusSize} rather than from metadata (which
 * no longer exists), so it is supplied by the block's factory.
 */
public abstract class TileItemBus extends TileMachineComponent {

    private final IOType ioType;
    private int slots;

    /**
     * {@code protected} since PLAN section 369 so the two transfer subclasses can reach it.
     *
     * <p>It was {@code private} while the subclasses were constructor-only shells, which was consistent. Adding the
     * item buses' transfer logic made it the wrong choice, and it is recorded here because a reader who finds the
     * modifier has no other way to know it was deliberate.
     */
    protected ItemStackHandler inventory;

    /**
     * The tick at which this bus may next move something.
     *
     * <p>Upstream's {@code canWork(minWorkDelay, maxWorkDelay)} is a RANDOM delay of 5 to 60 ticks rather than a fixed
     * period, and that is the point: it keeps a wall of adjacent buses from all transferring on the same tick. The
     * port reproduces the random interval rather than a fixed one because the fixed version would reintroduce exactly
     * the behaviour upstream's randomness exists to avoid.
     */
    private long nextWorkTick = 0L;

    /** @return whether this bus may move something on this tick, arming the next random delay when it does */
    protected boolean canWork() {
        if (level == null) {
            return false;
        }
        long now = level.getGameTime();
        if (now < nextWorkTick) {
            return false;
        }
        // 5..60 inclusive, matching upstream's minWorkDelay/maxWorkDelay.
        nextWorkTick = now + 5 + (level.random.nextInt(56));
        return true;
    }

    /**
     * Visits every neighbouring {@code ITEM_HANDLER} this bus is allowed to talk to (PLAN section 369).
     *
     * <p><b>This walk is SHARED between the input and output buses, and that is a design decision worth stating.</b>
     * Upstream duplicates it in two classes, and its six rules are exactly the ones that produce a bug when guessed:
     *
     * <ol>
     *   <li>the client is skipped</li>
     *   <li>the random 5..60 throttle applies</li>
     *   <li>all six faces are tried</li>
     *   <li><b>a neighbour that is itself a {@code TileItemBus} is SKIPPED</b> -- buses do not chain, so two side by
     *       side do not move items between each other</li>
     *   <li>the capability is queried with {@code facing.getOpposite()} -- the neighbour must be asked for the face
     *       that points BACK at us</li>
     *   <li>a neighbour with no handler is skipped</li>
     * </ol>
     *
     * <p>Writing them once is not tidiness: it is the only way a later reader can change the chaining rule without
     * having to remember that there are two copies of it.
     *
     * @param body what to do with a neighbour's handler; called at most once per accepted face
     */
    protected void forEachNeighbourHandler(java.util.function.Consumer<net.minecraftforge.items.IItemHandler> body) {
        if (level == null || level.isClientSide() || !canWork()) {
            return;
        }
        for (net.minecraft.core.Direction facing : net.minecraft.core.Direction.values()) {
            BlockPos offset = worldPosition.relative(facing);
            net.minecraft.world.level.block.entity.BlockEntity neighbour = level.getBlockEntity(offset);
            if (neighbour == null || neighbour instanceof TileItemBus) {
                continue;
            }
            var handler = neighbour.getCapability(
                    net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                    facing.getOpposite());
            if (handler.isPresent()) {
                body.accept(handler.resolve().orElse(null));
            }
        }
    }

    protected TileItemBus(java.util.function.Supplier<? extends BlockEntityType<?>> type,
                          BlockPos pos, BlockState state, IOType ioType, int slots) {
        super(type, pos, state);
        this.ioType = ioType;
        this.slots = slots;
        this.inventory = createInventory(slots);
    }

    /**
     * Resolves the bus variant from a blockstate without referencing a concrete block class.
     *
     * <p>The {@code BlockEntityType} factory only receives {@code (pos, state)}, so slot count has to
     * be recovered from the state. Reading the property generically keeps the tiles from having to
     * import their own block class just to ask it a question the state already answers.
     */
    /**
     * @return this bus's tier, derived from its own blockstate
     *
     * <p>Convenience over {@link #variantOf(BlockState)} so callers with a tile in hand -- the GUI open
     * path and the menu -- do not each have to reach for {@code getBlockState()} and remember which
     * static to call. The tier is where the slot count and the GUI geometry both live.
     */
    public ItemBusSize getSize() {
        return variantOf(getBlockState());
    }

    public static ItemBusSize variantOf(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            // Compare values rather than casting the property: the type parameter is erased, so a
            // direct cast is unchecked and only avoidable by letting the state hand back an Object.
            if ("size".equals(property.getName())
                    && property.getPossibleValues().stream()
                    .anyMatch(value -> value instanceof ItemBusSize)) {
                return (ItemBusSize) state.getValue(property);
            }
        }
        return ItemBusSize.NORMAL;
    }

    /** @return slot count for the given state, falling back to NORMAL if the state is unexpected */
    public static int slotsOf(BlockState state) {
        return variantOf(state).getSlots();
    }

    /**
     * The per-slot ceiling this bus's inventory enforces.
     *
     * <p><b>64 by default, which is exactly Forge's own default, so an ordinary bus behaves as it always did.</b> It exists as a
     * hook for ONE caller: the Applied Energistics 2 buses, whose upstream form calls
     * <code>inventory.setStackLimit(Integer.MAX_VALUE, slotIDs)</code> because an ME bus holds network QUANTITIES rather than
     * 64-capped stacks.
     *
     * <p>Recorded here rather than left implicit because a subclass returning {@code Integer.MAX_VALUE} from this method looks
     * like a typo, and the reason it is not is upstream's own behaviour rather than a porting choice (PLAN section 395.2,
     * direction B).
     */
    protected int slotLimit() {
        return 64;
    }

    protected ItemStackHandler createInventory(int size) {
        return new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public int getSlotLimit(int slot) {
                // Through the hook rather than the constant, so a subclass can raise it without replacing the whole handler --
                // and so the DIRECTION rules below stay in one place for both kinds of bus.
                return slotLimit();
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                // Accept, for BOTH bus directions.
                //
                // This override was written twice and got the direction wrong both times, because the
                // requirement is contradictory once the handler is shared:
                //
                //   - TileItemBus#createInventory is the INVENTORY used by the machine AND by external
                //     automation through the same capability, so `isItemValid` participates on both
                //     paths. Returning `ioType == INPUT` made an OUTPUT bus reject the product the
                //     machine had just made (`offered 1 accepted 0` in every slot, recipe completed,
                //     nothing emitted). Returning `ioType == OUTPUT` then made the INPUT bus reject the
                //     machine's own input loading (`inputs inserted — 0/40`).
                //
                // The lesson is that `isItemValid` cannot express a caller-dependent rule here either —
                // the same trap as the energy hatch's rate limit. Direction is enforced where it is
                // actually meaningful: MachineContext.ComponentKind filters which components a recipe
                // may consume from and produce into, which is the machine's own logic and is unaffected
                // by what automation does.
                //
                // The residual behaviour change is that external automation may now insert into an
                // output bus. That is a mild gameplay nicety rather than a correctness problem, and it
                // is strictly better than either alternative above, both of which broke the machine.
                return super.isItemValid(slot, stack);
            }
        };
    }

    public IOType getIOType() {
        return ioType;
    }

    public int getSlots() {
        return slots;
    }
    public IItemHandler getInventory() {
        // SELF-HEALING AGAINST THE STATE THE TILE WAS BUILT FROM (PLAN section 279).
        //
        // The client put the F3 line on this and it settled the report:
        //
        //   Targeted Block: modularmachinery:blockoutputbus
        //   size: ludicrous
        //
        // with a SIX-slot GUI. Six is `normal`, the DEFAULT state -- so this tile's inventory was built from
        // a state that is not the state the block ended up in. Section 274's candidate 2, confirmed by
        // measurement rather than by reasoning.
        //
        // WHERE that happens is still open: the blockstate is right, so something between placement and
        // `newBlockEntity` handed the tile the default. Rather than guess which of several paths it is, this
        // checks the ONE fact that matters -- does the inventory match the block the tile is actually on --
        // and rebuilds when it does not.
        //
        // Doing it here rather than in a load hook means it also repairs blocks already placed in saved
        // worlds, which is the population that actually has the bug.
        // THE LIVE STATE, NOT `getBlockState()` (PLAN section 280).
        //
        // `BlockEntity.getBlockState()` CACHES: it returns its `blockState` field, filling it from the
        // level on FIRST call and never again. If anything reads it before the final state lands, the cache
        // holds the DEFAULT forever.
        //
        // That single fact explains both symptoms this report produced: the inventory was built from
        // `normal` (the cached default) while F3, which reads the level directly, showed `ludicrous`. And it
        // explains why the first version of this self-heal did nothing -- it asked the SAME stale cache and
        // computed the same six.
        //
        // So the level is asked, not the tile. The null guard covers a tile that has been created but not
        // yet placed, where there is no level to ask.
        net.minecraft.world.level.block.state.BlockState live =
                level != null ? level.getBlockState(worldPosition) : getBlockState();
        int expected = slotsOf(live);
        if (inventory == null || inventory.getSlots() != expected) {
            slots = expected;
            inventory = createInventory(expected);
            setChanged();
        }
        return inventory;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.of(() -> inventory).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // ItemStackHandler is INBTSerializable<CompoundTag>, so this round-trips without the 1.12.2
        // NBTTagList-walking that the old ItemStack list serialisation needed.
        if (tag.contains("inventory")) {
            inventory.deserializeNBT(tag.getCompound("inventory"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
    }
}
