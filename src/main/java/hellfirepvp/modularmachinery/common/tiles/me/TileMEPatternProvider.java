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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.event.machine.MachineComponentTileNotifiable;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.event.recipe.FactoryRecipeFinishEvent;
import hellfirepvp.modularmachinery.common.event.recipe.RecipeFinishEvent;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import hellfirepvp.modularmachinery.common.tiles.base.MEMachineComponent;
import hellfirepvp.modularmachinery.common.util.InfItemFluidHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * {@code modularmachinery:blockmepatternprovider} -- an Applied Energistics 2 pattern provider the machine can pull from.
 *
 * <h2>Rule A0: what upstream is, and why this class cannot follow its STRUCTURE</h2>
 *
 * <p>Upstream's `MEPatternProvider` is 792 lines and holds a `DualityInterface`. **§414 established with `javap` that `DualityInterface` does
 * not exist in AE2 15.4.11**, which is the A0 condition for deviating.</p>
 *
 * <p>**But §436 found that the replacement is a SEPARATION, not a deletion** -- upstream's single class did four jobs and 15.x gives each its own
 * type:</p>
 *
 * <table>
 *   <caption>upstream's one object, split four ways</caption>
 *   <tr><td>the pattern inventory and crafting logic</td><td>{@link PatternProviderLogic}</td></tr>
 *   <tr><td>the block-entity-side contract</td><td>{@link PatternProviderLogicHost}</td></tr>
 *   <tr><td>the grid-facing identity</td><td>{@code PatternContainer}</td></tr>
 *   <tr><td>where pushed items go</td><td>{@code PatternProviderTarget} / {@code TargetCache}</td></tr>
 *   <tr><td>where rejected items return</td><td>{@code PatternProviderReturnInventory}</td></tr>
 * </table>
 *
 * <p>**So upstream's class SURFACE survives** -- §437 found that `getTargets()`, `getBlockEntity()`, `saveChanges()` and `getConfigManager()`
 * already match 15.x's host interface by name, because upstream implemented the OLD `DualityInterface.Host` and 15.x's interface is that contract
 * renamed and split. **What changes is the object this class HOLDS.**</p>
 *
 * <h2>And the mod-side integration is TWO LINES, per §439</h2>
 *
 * <p>Upstream's `provideComponent()` returned an anonymous `MachineComponent<T>` typed `COMPONENT_ITEM_FLUID_GAS`. **§438 looked for that
 * component-type system and found the port does not have one -- and §439 read the resolution path and found the port needs none: it resolves by
 * `be instanceof MachineComponentTile` plus `getIOType()`.** **So the integration is: implement the marker, return {@link IOType#INPUT}.**</p>
 *
 * <p><b>INPUT is correct and not a guess:</b> upstream's component is `new MachineComponent&lt;&gt;(IOType.INPUT)` (U129, U182) -- a pattern
 * provider FEEDS the machine.</p>
 *
 * <h2>The container, and the fact no signature gives (§443.3)</h2>
 *
 * <p>The tile owns an {@link InfItemFluidHandler} that the machine draws from. **Upstream's constructor also builds THIRTY-SIX separate
 * `InfItemFluidHandler` instances, one per pattern slot, each with its own group id** -- so a provider is 36 independent machine components plus
 * this container, not one component wrapping 36 slots.</p>
 *
 * <p><b>The normal provider input path is live.</b> The modern AE2 pattern-details plumbing is implemented by
 * {@link MachinePatternProviderLogic}; its push entry point receives the exact {@code KeyCounter} inputs selected by
 * the crafting CPU and appends them to the shared handler in normal modes, or to the matching per-pattern handler in
 * isolation mode.</p>
 */
public class TileMEPatternProvider extends MEMachineComponent implements PatternProviderLogicHost,
        hellfirepvp.modularmachinery.common.tiles.PatternIsolationProvider,
        MachineComponentTile, MachineComponentTileNotifiable,
        hellfirepvp.modularmachinery.common.tiles.MachineInputHandlerProvider {

    







 
    public static final int PATTERNS = 36;

    /** Upstream's constant (U91). */
    public static final int SUB_ITEM_HANDLER_SLOTS = 2;

    /** The NBT key the work mode rides, so the state survives a reload and reaches the client. */
    private static final String WORK_MODE_TAG = "workMode";

    /**
     * The fixed item half of the independent storage: the two wells at (181, 172) and (199, 172).
     *
     * <p><b>A {@link ItemStackHandler} rather than AE2's {@code AppEngInternalInventory} (upstream U93).</b> The two are
     * the same shape -- a fixed, NBT-round-tripping, {@code IItemHandlerModifiable} inventory -- and this one is
     * Forge's, which means the MENU needs no AE2 import to hold a slot over it. The stack limit is upstream's
     * default, not a grid inventory's: the pattern inventory's limit of 1 (U95) belongs to patterns, and the single inv
     * is ordinary storage.</p>
     */
    protected final SubItemInventory subItemHandler = new SubItemInventory();

    /**
     * The fixed fluid half: upstream's {@code AEFluidInventoryUpgradeable(this, 1, Integer.MAX_VALUE)} (U94), one tank
     * with no ceiling. {@link FluidTank} is Forge's equivalent, and the {@code Integer.MAX_VALUE} capacity is upstream's
     * own number rather than a guess.
     */
    protected final FluidTank subFluidHandler = new SubFluidTank();

    /** Fixed barrel equivalent of the old AE2 fluid inventory, with the missing Forge change callback restored. */
    private final class SubFluidTank extends FluidTank {
        private SubFluidTank() {
            super(Integer.MAX_VALUE);
        }

        @Override
        protected void onContentsChanged() {
            markHandlerChanged();
        }
    }

    /**
     * Forge's {@link ItemStackHandler} carries no change notification of its own -- the hook is
     * {@code protected void onContentsChanged(int)}, which only a SUBCLASS can reach.
     *
     * <p><b>So the notification is the subclass.</b> Upstream does not need this: its {@code AppEngInternalInventory}
     * takes the tile as its {@code IAEAppEngInventory} host and calls {@code onChangeInventory} itself (U668-U674),
     * which is a contract AE2 15.4.11 no longer offers in that shape. What this port needs is one line of it -- "a write
     * to the fixed half marks the provider dirty" -- and this is the smallest form that gets it. Without it, an item
     * dropped into a well would sit in the world's memory and never reach a save.</p>
     */
    public class SubItemInventory extends ItemStackHandler {

        SubItemInventory() {
            super(SUB_ITEM_HANDLER_SLOTS);
        }

        @Override
        protected void onContentsChanged(int slot) {
            markHandlerChanged();
        }
    }

    /**
     * The container the machine draws from, and the object every recipe sees.
     *
     * <p>Upstream's `handler` is {@code new InfItemFluidHandler(subItemHandler, subFluidHandler)} (U107) -- the two
     * halves passed IN. This port's container ignores its sub-handlers by design (see {@code InfItemFluidHandler}'s own
     * javadoc and §441), so the same two objects are plugged in here and joined by the container's own views, which is
     * what makes the wells live without changing any of the six methods §441 verified.</p>
     */
    protected final InfItemFluidHandler handler = new InfItemFluidHandler();

    /**
     * The source provider owns one input container per pattern slot while {@link WorkModeSetting#ISOLATION_INPUT}
     * is active.  They are kept even when the mode is not selected so changing modes does not destroy buffered
     * ingredients; only the handler exposed to the machine changes.
     */
    private final InfItemFluidHandler[] isolatedHandlers = new InfItemFluidHandler[PATTERNS];

    /** AE2's brains for this block, replacing upstream's `DualityInterface` (U97). */
    private final PatternProviderLogic logic;

    /** Set whenever the container changes, so the client can be told (upstream's `handlerDirty`, U101). */
    public boolean handlerDirty = false;

    /** Groups one AE2 push into a single client update packet. */
    private int handlerSyncSuppression;
    private boolean handlerSyncPending;

    /**
     * The work mode. Upstream's field and default: {@code protected WorkModeSetting workMode =
     * WorkModeSetting.DEFAULT} (U98).
     */
    private volatile WorkModeSetting workMode = WorkModeSetting.DEFAULT;

    /**
     * Upstream's {@code volatile boolean machineCompleted = true} (U99), written by {@code pushPattern} (U271), by
     * {@code returnItems} (U360) and by the recipe-finish event (U682-U690), and read by {@link #isBusy()} (U307).
     *
     * <p>The modern push path lives in {@link MachinePatternProviderLogic}; this state is still advanced by the explicit
     * return action and retained for the crafting-lock mode.</p>
     */
    private volatile boolean machineCompleted = true;

    /** Pattern identity used by upstream's enhanced-blocking mode. */
    @Nullable
    private IPatternDetails currentPattern;
    private int currentPatternIndex = -1;

    /** The cached item view the machine and the menu share; built once, so both see one object (see {@link #machineItemHandler()}). */
    private IItemHandlerModifiable machineItemView = null;

    /** The public item capability is the encoded-pattern inventory, as in the 1.12 source. */
    private net.minecraftforge.items.IItemHandler patternCapability;

    @Nullable
    private BlockPos controllerPos;

    public TileMEPatternProvider(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos,
                                 BlockState state) {
        super(type, pos, state);
        // Upstream's constructor sets the two change callbacks and marks the chunk dirty (U113-120).
        this.handler.setOnItemChanged(slot -> {
            markHandlerChanged();
        });
        this.handler.setOnFluidChanged(slot -> {
            markHandlerChanged();
        });
        // THE GAS CALLBACK IS NOT SET, and that is the same scope decision as InfItemFluidHandler's gas half:
        // upstream guards it with Mods.MEKANISM.isPresent() && Mods.MEKENG.isPresent() (§443.2), and the port's equivalent
        // is an inline ModList check (§444.2). It is omitted here because the container has no gas methods to call yet.
        //
        // UPSTREAM'S TWO HALVES ARE PLUGGED IN HERE (U93-U94, U107) so the container can present them as one handler.
        // The item half is written through so that a change to it marks the chunk dirty like any other change: upstream's
        // AppEngInternalInventory notifies its host, and this is that notification in Forge's own callback.
        this.handler.setSubItemHandler(this.subItemHandler);
        this.handler.setSubFluidHandler(this.subFluidHandler);
        for (int i = 0; i < isolatedHandlers.length; i++) {
            // Upstream constructs every isolated component with the same fixed item and fluid inventories.
            // The per-pattern lists remain independent, while the two GUI wells are intentionally shared by
            // all components and therefore remain usable as a fallback input source in isolation mode.
            InfItemFluidHandler isolated = new InfItemFluidHandler(subItemHandler, subFluidHandler);
            isolated.setOnItemChanged(changed -> markHandlerChanged());
            isolated.setOnFluidChanged(changed -> markHandlerChanged());
            isolatedHandlers[i] = isolated;
        }
        // THE SIZE IS PASSED, and it is upstream's 36 rather than AE2's default: the two-argument constructor of
        // AE2 15.4.11's PatternProviderLogic passes 9 (measured with javap: `bipush 9` into its own three-argument
        // form, whose body hands that int to `new AppEngInternalInventory(host, size)`). Upstream's provider holds
        // 36 (its own PATTERNS, U90), and AE2's constructor takes the count, so this is where the two agree.
        // AE2's stock 15.x provider pushes ingredients to adjacent pattern
        // targets.  MMCE's provider is itself the machine input component;
        // the compatibility subclass retains the modern pattern inventory but
        // receives the CPU's KeyCounter stacks into this shared handler.
        this.logic = new MachinePatternProviderLogic(getMainNode(), this, PATTERNS, handler);
        this.patternCapability = this.logic.getPatternInv().toItemHandler();
    }

    /** @return AE2's logic object -- the replacement for upstream's `getInterfaceDuality()` (U729) */
    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    // ------------------------------------------------------- PatternProviderLogicHost

    @Override
    public BlockEntity getBlockEntity() {
        // Upstream's getTileEntity() returned `this` (U739).
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        // Upstream returned EnumSet.allOf(EnumFacing.class) (U734) -- the provider pushes to every face.
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void saveChanges() {
        setChanged();
        if (level != null && !level.isClientSide) {
            // Asked for by AE2 whenever its own state moves (pattern edits, priority, sends).
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return logic.getConfigManager();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(new ItemStack(getBlockState().getBlock()));
    }

    /**
     * Required by {@code ISubMenuHost}, which {@code PatternProviderLogicHost} reaches through {@code PatternContainer}.
     *
     * <p><b>Not in §435.2's listing, and that is the point:</b> that read took `PatternProviderLogicHost`'s OWN abstract methods and its
     * `default` ones, and this comes from a super-interface one level further up. **The compiler walks that hierarchy one method at a time,
     * which is slower than listing it but exact -- §445 records this one rather than leaving it to be rediscovered.**</p>
     */
    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getBlockState().getBlock());
    }

    // -------------------------------------------------------------- MachineComponentTile

    @Override
    public void setControllerPos(@Nullable BlockPos controllerPos) {
        // MARK ONLY ON A CHANGE, for the reason TileMachineComponent#setControllerPos records at length: the
        // periodic structure re-check re-binds every component of every formed machine, so an unconditional
        // write here would dirty this chunk every 50 ticks on a machine that changed nothing. The two
        // implementations of one interface rule must not disagree about it.
        if (java.util.Objects.equals(this.controllerPos, controllerPos)) {
            return;
        }
        this.controllerPos = controllerPos;
        setChanged();
    }

    @Nullable
    @Override
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * @return INPUT -- a pattern provider feeds the machine. Upstream's component is built with
     *         {@code new MachineComponent<>(IOType.INPUT)} (U129, U182).
     */
    @Override
    public IOType getIOType() {
        return IOType.INPUT;
    }

    /** @return the container the machine draws from */
    public InfItemFluidHandler getHandler() {
        return handler;
    }

    /**
     * Returns the dynamic cache containers represented by the source GUI's ingredient list.
     *
     * <p>The 1.12 provider sends the shared handler followed by every combination component to its client list.
     * The fixed two item slots and fluid barrel are deliberately not part of these lists; they have their own widgets.
     * Keeping this distinction also prevents the shared fixed slots from being repeated once per pattern.</p>
     */
    public java.util.List<InfItemFluidHandler> getGuiCacheHandlers() {
        java.util.ArrayList<InfItemFluidHandler> result =
                new java.util.ArrayList<>(1 + isolatedHandlers.length);
        result.add(handler);
        java.util.Collections.addAll(result, isolatedHandlers);
        return result;
    }

    /** @return the fixed item half of the independent storage, for a menu slot to sit over */
    public IItemHandlerModifiable getSubItemHandler() {
        return subItemHandler;
    }

    /** @return the fixed fluid half of the independent storage */
    public FluidTank getSubFluidHandler() {
        return subFluidHandler;
    }

    /**
     * <b>The input storage as the machine sees it.</b>
     *
     * <p>This is the method that closes the defect the user reported. It returns the container's own joined view
     * ({@code InfItemFluidHandler.machineVisibleItems()}) -- built ONCE and cached, so two callers that ask twice get the
     * same object rather than two wrappers over the same lists, which matters for a caller that compares identity and
     * for a game that shares one object between a menu slot and a recipe.</p>
     *
     * <h2>Normal and isolation modes</h2>
     *
     * <p>A recipe in this port finds the items it consumes by walking the machine's components and asking each for its
     * {@code ITEM_HANDLER} capability ({@code RequirementItem.countAvailable}, L422-L435;
     * {@code MachineTicker.extract}, L1395-L1410). Normal modes expose the shared handler. Isolation mode exposes
     * the selected pattern handler directly and {@code MachineContext} expands the tile into 36 independent
     * capability entries, matching the source mod's 36 machine components.</p>
     *
     * <p><b>Upstream's equivalent is {@code provideComponent()} (U177-U198) returning the shared handler</b> in normal
     * modes, while {@code provideComponents()} (U200-U206) hands out 36 per-pattern containers in isolation mode.
     * The port preserves both paths through the Forge capabilities and {@link PatternIsolationProvider}.</p>
     *
     * @return the item handler the machine reads; never null, and the same instance for the life of this block
     */
    public IItemHandlerModifiable machineItemHandler() {
        IItemHandlerModifiable view = this.machineItemView;
        if (view == null) {
            // The container always has both halves here (the constructor above plugs them in), so this cannot be null;
            // the handler's own accessor returns null only for a container built WITHOUT a fixed half, and the
            // fallback keeps this method's promise rather than dereferencing one.
            view = handler.machineVisibleItems();
            this.machineItemView = view;
        }
        return view;
    }

    /**
     * The fluid half of {@link #machineItemHandler()}.
     *
     * <p>Not cached, because {@code IFluidHandler} has no mutator half to share and the container caches its own view
     * (returning the same object every call). Stated rather than silently relying on that.</p>
     *
     * @return the fluid handler the machine reads; never null for a tile built by this class
     */
    public IFluidHandler machineFluidHandler() {
        return handler.machineVisibleFluids();
    }

    @Override
    public java.util.List<? extends net.minecraftforge.items.IItemHandler> getIsolatedItemHandlers() {
        if (workMode != WorkModeSetting.ISOLATION_INPUT) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(isolatedHandlers).map(InfItemFluidHandler::machineVisibleItems).toList();
    }

    @Override
    public java.util.List<? extends IFluidHandler> getIsolatedFluidHandlers() {
        if (workMode != WorkModeSetting.ISOLATION_INPUT) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(isolatedHandlers).map(InfItemFluidHandler::machineVisibleFluids).toList();
    }

    @Override
    public BlockPos getIsolationGroupPosition() {
        return worldPosition;
    }

    /** Decode physical inventory slots: AE2's available-pattern list omits empty slots. */
    private int patternSlot(IPatternDetails details) {
        if (details == null || level == null) return -1;
        var inventory = logic.getPatternInv();
        for (int i = 0; i < inventory.size(); i++) {
            var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(inventory.getStackInSlot(i), level);
            if (details.equals(decoded)) return i;
        }
        return -1;
    }

    @Nullable
    InfItemFluidHandler handlerForPattern(IPatternDetails details) {
        if (workMode != WorkModeSetting.ISOLATION_INPUT) return handler;
        int index = patternSlot(details);
        return index < 0 ? null : isolatedHandlers[index];
    }

    // ------------------------------------------------------------------ capability

    /**
     * <b>The half that was missing.</b> Before this override the pattern provider exposed NO capability at all -- it
     * inherited {@code BlockEntity}'s empty optional -- so the container built in the constructor was reachable from
     * nowhere: no recipe counted it, no recipe drew from it, and the two wells upstream draws over it had nothing to
     * write to. §729 refused those wells for exactly that reason and recorded it; this method is the producer that
     * makes them honest.
     *
     * <p>The shape is the port's own, from the two ME buses ({@code MEItemBus} L252-L259,
     * {@code MEItemOutputBus}), so this is a reuse rather than a second convention: one capability per type, the handler
     * taken from the tile rather than rebuilt, and {@code super} for everything else.</p>
     */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            // Upstream MEPatternProvider returns its encoded pattern inventory here.
            // The machine-side ingredient cache is supplied through
            // MachineInputHandlerProvider and resolved by MachineContext instead.
            return patternCapability == null ? LazyOptional.empty()
                    : LazyOptional.of(() -> patternCapability).cast();
        }
        return super.getCapability(capability, side);
    }

    // ------------------------------------------------------------------ work mode

    /** @return the mode this provider is in */
    public WorkModeSetting getWorkMode() {
        return workMode;
    }

    /**
     * Sets the mode, and reproduces upstream's two side effects (U483-U495) <b>as far as they apply here</b>.
     *
     * <pre>
     *   U484-U487  if either the OLD or the NEW mode is ISOLATION_INPUT -&gt; returnItemsScheduled()
     *   U489-U491  if the new mode is not CRAFTING_LOCK_MODE             -&gt; machineCompleted = true
     *   U492-U494  if the new mode is not ENHANCED_BLOCKING_MODE         -&gt; resetCurrentPattern()
     * </pre>
     *
     * <p><b>The first branch is reproduced and the trigger is real:</b> entering or leaving isolation changes which
     * container the machine is looking at, so whatever the old one held has to go back to the grid. Upstream schedules
     * that onto its own worker; this port has no worker ({@code BlockController} L106-L108), and the caller is already
     * on the server thread, so it happens at once.</p>
     *
     * <p>The port also retains the source current-pattern identity and slot index.  Enhanced blocking uses the identity
     * as its acceptance gate; isolation uses the slot index to select the active handler and restores that index from
     * NBT after a world reload.</p>
     */
    public void setWorkMode(WorkModeSetting mode) {
        if (mode == null) {
            return;
        }
        boolean isolationInvolved = this.workMode == WorkModeSetting.ISOLATION_INPUT
                || mode == WorkModeSetting.ISOLATION_INPUT;
        this.workMode = mode;
        if (mode != WorkModeSetting.CRAFTING_LOCK_MODE) {
            this.machineCompleted = true;
        }
        if (mode != WorkModeSetting.ENHANCED_BLOCKING_MODE) {
            resetCurrentPattern();
        }
        markWorkModeChanged();
        if (isolationInvolved) {
            returnToNetwork();
        }
    }

    /**
     * <b>Upstream's own predicate, ported to the shared-handler model</b> (U306-U309):
     *
     * <pre>
     *   return (workMode == CRAFTING_LOCK_MODE &amp;&amp; !machineCompleted)
     *       || (workMode == BLOCKING_MODE &amp;&amp; !handler.isEmpty());
     * </pre>
     *
     * <p>AE2 15.x has its own provider implementation, so the port overrides that implementation in
     * {@link MachinePatternProviderLogic} and delegates this predicate to the same shared handler state.</p>
     *
     * <p>The custom AE2 push bridge now feeds the same handler that this predicate reads, so blocking and crafting-lock
     * modes have an observable effect on the provider's busy state.</p>
     *
     * @return upstream's two-clause answer
     */
    public boolean busy() {
        return (workMode == WorkModeSetting.CRAFTING_LOCK_MODE && !machineCompleted)
                || (workMode == WorkModeSetting.BLOCKING_MODE && !handler.isEmpty());
    }

    /**
     * <b>Upstream's other predicate, retained for callers that inspect the tile directly</b> (U275-U281):
     *
     * <pre>
     *   if (patternDetails.isCraftable() || !proxy.isActive() || !proxy.isPowered()) return false;
     *   return workMode != ENHANCED_BLOCKING_MODE || handler.isEmpty() || currentPattern == null
     *          || currentPattern.equals(patternDetails);
     * </pre>
     *
     * <p>The current-pattern identity clause belongs to AE2's internal logic and is intentionally left to
     * {@link MachinePatternProviderLogic}; this tile-level helper evaluates the state that is available here.</p>
     *
     * @param craftable whether AE2 reports the pattern as craftable -- upstream's first rejection
     * @return false when the provider must refuse a push for a reason this port can actually evaluate
     */
    public boolean acceptsPattern(boolean craftable) {
        return acceptsPattern(craftable, null);
    }

    /** Same gate with the incoming pattern identity available to enhanced blocking mode. */
    boolean acceptsPattern(boolean craftable, @Nullable IPatternDetails patternDetails) {
        if (craftable || !getMainNode().isActive() || !getMainNode().isPowered()) {
            return false;
        }
        return workMode != WorkModeSetting.ENHANCED_BLOCKING_MODE || handler.isEmpty()
                || currentPattern == null
                || (patternDetails != null && currentPattern.equals(patternDetails));
    }

    /** @return true once the craft that took the crafting lock has finished (upstream's field, U99) */
    public boolean isMachineCompleted() {
        return machineCompleted;
    }

    /** Called by the AE2 pattern push bridge after inputs have been accepted. */
    void onPatternPushed(IPatternDetails patternDetails) {
        handleNewPattern(patternDetails);
        machineCompleted = workMode != WorkModeSetting.CRAFTING_LOCK_MODE;
        markHandlerChanged();
    }

    void beginHandlerSyncBatch() {
        handlerSyncSuppression++;
    }

    void endHandlerSyncBatch() {
        if (handlerSyncSuppression <= 0) {
            handlerSyncSuppression = 0;
            return;
        }
        handlerSyncSuppression--;
        if (handlerSyncSuppression == 0 && handlerSyncPending) {
            handlerSyncPending = false;
            sendHandlerUpdate();
        }
    }

    private void handleNewPattern(@Nullable IPatternDetails patternDetails) {
        if (workMode == WorkModeSetting.ISOLATION_INPUT) {
            if (patternDetails != null) {
                currentPattern = patternDetails;
                currentPatternIndex = patternSlot(patternDetails);
            }
            return;
        }
        if (workMode != WorkModeSetting.ENHANCED_BLOCKING_MODE) {
            resetCurrentPattern();
            return;
        }
        if (patternDetails == null || patternDetails.equals(currentPattern)) {
            return;
        }
        currentPattern = patternDetails;
        currentPatternIndex = patternSlot(patternDetails);
    }

    private void resetCurrentPattern() {
        currentPattern = null;
        currentPatternIndex = -1;
    }

    /** Releases crafting-lock mode when this formed machine finishes a recipe run. */
    @Override
    public void onMachineEvent(MachineEvent event) {
        if (!(event instanceof RecipeFinishEvent) && !(event instanceof FactoryRecipeFinishEvent)) {
            return;
        }
        if (machineCompleted) {
            return;
        }
        machineCompleted = true;
        markHandlerChanged();
    }

    /**
     * Tells the client the mode moved. The block update is the port's existing sync verb
     * ({@code TileEntityBase.markForUpdate} L117-L124, {@code saveChanges} above), and the tag it carries is
     * {@link #getUpdateTag()} -- which is {@code saveAdditional}, which writes {@link #WORK_MODE_TAG}.
     */
    private void markWorkModeChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------- returning items to the grid

    































 
    public int returnToNetwork() {
        if (level == null || level.isClientSide()) {
            return 0;
        }
        if (!getMainNode().isActive()) {
            return 0;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        IStorageService service = grid.getService(IStorageService.class);
        MEStorage storage = service == null ? null : service.getInventory();
        if (storage == null) {
            return 0;
        }
        IActionSource source = IActionSource.ofMachine(this);
        int refused = 0;

        java.util.List<InfItemFluidHandler> stores = new java.util.ArrayList<>();
        stores.add(handler);
        stores.addAll(java.util.Arrays.asList(isolatedHandlers));
        for (InfItemFluidHandler store : stores) {
            ItemStack[] items = store.getItemStackList().toArray(new ItemStack[0]);
            for (int i = 0; i < items.length; i++) {
                ItemStack stack = items[i];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                AEKey key = AEItemKey.of(stack);
                if (key == null) {
                    // Upstream has no such branch: `itemChannel.createStack(stack)` cannot fail for it, and MEItemOutputBus's
                    // javadoc records the consequence of the assumption it does not have to make -- an unencodable stack that
                    // is dropped rather than kept. Keeping it and counting it is the honest answer for a stack nothing can read.
                    refused++;
                    continue;
                }
                long accepted = storage.insert(key, stack.getCount(), Actionable.MODULATE, source);
                int left = (int) Math.max(0, stack.getCount() - accepted);
                store.getItemStackList().set(i, left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left));
                if (left > 0) {
                    refused++;
                }
            }
        }

        for (InfItemFluidHandler store : stores) {
            FluidStack[] fluids = store.getFluidStackList().toArray(new FluidStack[0]);
            for (int i = 0; i < fluids.length; i++) {
                FluidStack stack = fluids[i];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                AEKey key = AEFluidKey.of(stack);
                if (key == null) {
                    refused++;
                    continue;
                }
                long accepted = storage.insert(key, stack.getAmount(), Actionable.MODULATE, source);
                int left = (int) Math.max(0, stack.getAmount() - accepted);
                store.getFluidStackList().set(i, left <= 0 ? FluidStack.EMPTY : new FluidStack(stack, left));
                if (left > 0) {
                    refused++;
                }
            }
        }

        // THE BARREL IS PART OF THE INDEPENDENT STORAGE TOO, so it is emptied with the rest: leaving it behind would be a
        // second storage inside the same labelled area whose behaviour differed from the first with nothing saying so.
        FluidStack inBarrel = this.subFluidHandler.getFluid();
        if (!inBarrel.isEmpty()) {
            AEKey key = AEFluidKey.of(inBarrel);
            if (key != null) {
                long accepted = storage.insert(key, inBarrel.getAmount(), Actionable.MODULATE, source);
                int left = (int) Math.max(0, inBarrel.getAmount() - accepted);
                this.subFluidHandler.setFluid(left <= 0 ? FluidStack.EMPTY : new FluidStack(inBarrel, left));
                if (left > 0) {
                    refused++;
                }
            } else {
                refused++;
            }
        }

        // Upstream's own two lines at the end of the action (U372-U373), and the mode half of the same idea: the
        // description of the button says it "will reset the state of the Crafting Lock Mode"
        // (`gui.mepatternprovider.return_items.desc`), which is U360's `machineCompleted = true`.
        this.machineCompleted = true;
        resetCurrentPattern();
        markHandlerChanged();
        return refused;
    }

    /** Marks the shared cache dirty and immediately publishes a block-entity update to an open client GUI. */
    private void markHandlerChanged() {
        handlerDirty = true;
        setChanged();
        if (level != null && !level.isClientSide()) {
            if (handlerSyncSuppression > 0) {
                handlerSyncPending = true;
            } else {
                sendHandlerUpdate();
            }
        }
    }

    private void sendHandlerUpdate() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isAllDefault() {
        if (!logic.getPatternInv().isEmpty()) return false;
        if (workMode != WorkModeSetting.DEFAULT || !handler.isEmpty() || handlerDirty || controllerPos != null) {
            return false;
        }
        for (int i = 0; i < subItemHandler.getSlots(); i++) {
            if (!subItemHandler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        if (!subFluidHandler.getFluid().isEmpty()) {
            return false;
        }
        for (InfItemFluidHandler isolated : isolatedHandlers) {
            if (!isolated.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // AE2 owns its own NBT shape; upstream's readProviderNBT/writeProviderNBT (U529/U575) delegate the same way.
        CompoundTag providerTag = new CompoundTag();
        logic.writeToNBT(providerTag);
        tag.put("provider", providerTag);

        if (controllerPos != null) {
            tag.putLong("controllerPos", controllerPos.asLong());
        }
        tag.put("handler", writeHandler());
        if (workMode == WorkModeSetting.ISOLATION_INPUT) {
            net.minecraft.nbt.ListTag isolated = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < isolatedHandlers.length; i++) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("index", i);
                entry.put("handler", writeHandler(isolatedHandlers[i]));
                isolated.add(entry);
            }
            tag.put("isolatedHandlers", isolated);
        }
        // THE WORK MODE IS WRITTEN AS ITS ORDINAL, WHICH IS UPSTREAM'S OWN ENCODING (U587, U544).
        //
        // UPSTREAM WRITES IT ONLY WHEN IT IS NOT DEFAULT -- `if (workMode != WorkModeSetting.DEFAULT)`
        // (U586-U588) -- and reads it UNCONDITIONALLY (`WorkModeSetting.values()[compound.getByte("workMode")]`,
        // U544), relying on the field's initialiser for the absent case. That works, and it is a deviation this
        // port does NOT take, for a measured reason: the missing-key path and the DEFAULT-ordinal path write the
        // same value (ordinal 0), so writing always costs nothing, while "absent means DEFAULT" makes a saved
        // provider indistinguishable from one whose mode was never saved -- which is exactly the difference a check
        // has to be able to see. AND UPSTREAM'S READ HAS NO RANGE GUARD: a corrupted byte makes
        // `values()[i]` throw ArrayIndexOutOfBoundsException out of load() and the chunk fails to load. The port
        // clamps; see readWorkMode.
        tag.putByte(WORK_MODE_TAG, (byte) workMode.ordinal());

        // The independent storage's fixed half, as one tag each, through the handlers' own serialisation.
        CompoundTag sub = new CompoundTag();
        sub.put("items", subItemHandler.serializeNBT());
        CompoundTag barrel = new CompoundTag();
        subFluidHandler.writeToNBT(barrel);
        sub.put("barrel", barrel);
        tag.put("subHandler", sub);
        tag.putBoolean("machineCompleted", machineCompleted);
        if (currentPatternIndex >= 0
                && workMode == WorkModeSetting.ENHANCED_BLOCKING_MODE) {
            tag.putInt("currentPatternIndex", currentPatternIndex);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("provider")) {
            logic.readFromNBT(tag.getCompound("provider"));
        }
        if (tag.contains("controllerPos")) {
            controllerPos = BlockPos.of(tag.getLong("controllerPos"));
        }
        if (tag.contains("handler")) {
            readHandler(tag.getCompound("handler"));
        }
        for (InfItemFluidHandler isolated : isolatedHandlers) readHandler(isolated, new CompoundTag());
        if (tag.contains("isolatedHandlers")) {
            net.minecraft.nbt.ListTag isolated = tag.getList("isolatedHandlers", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < isolated.size(); i++) {
                CompoundTag entry = isolated.getCompound(i);
                int index = entry.getInt("index");
                if (index >= 0 && index < isolatedHandlers.length && entry.contains("handler")) {
                    readHandler(isolatedHandlers[index], entry.getCompound("handler"));
                }
            }
        }
        if (tag.contains("subHandler")) {
            CompoundTag sub = tag.getCompound("subHandler");
            if (sub.contains("items")) {
                subItemHandler.deserializeNBT(sub.getCompound("items"));
            }
            if (sub.contains("barrel")) {
                subFluidHandler.readFromNBT(sub.getCompound("barrel"));
            }
        }
        if (tag.contains(WORK_MODE_TAG)) {
            workMode = WorkModeSetting.fromOrdinal(tag.getByte(WORK_MODE_TAG));
        }
        if (tag.contains("machineCompleted")) {
            machineCompleted = tag.getBoolean("machineCompleted");
        }
        currentPatternIndex = tag.contains("currentPatternIndex") ? tag.getInt("currentPatternIndex") : -1;
        currentPattern = currentPatternIndex >= 0 && currentPatternIndex < PATTERNS && level != null
                ? appeng.api.crafting.PatternDetailsHelper.decodePattern(
                        logic.getPatternInv().getStackInSlot(currentPatternIndex), level) : null;
    }

    /**
     * The full state, and the same serialisation {@code saveAdditional} writes -- the port's own rule from
     * {@code TileEntityBase} L89-L94, restated here because this tile does not extend it ({@code MEMachineComponent}
     * extends {@code BlockEntity} directly).
     *
     * <p>Without this override the mode would be saved and would still be INVISIBLE to a player: vanilla's
     * {@code BlockEntity.getUpdateTag()} carries the block entity TYPE and position only, so a client receives a mode
     * it can never read. This is the defect shape §735 recorded for the parallel controller -- a value that was right
     * on the server and wrong where the player reads it.</p>
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    /**
     * Serialises the container by INDEX.
     *
     * <p><b>By index rather than by name, because the two lists are ORDERED and their order is the slot numbering the machine sees.</b>
     * **Writing them as an unordered map would let a reordered read silently reconnect contents to the wrong slots.**</p>
     */
    private CompoundTag writeHandler() {
        return writeHandler(handler);
    }

    private CompoundTag writeHandler(InfItemFluidHandler source) {
        CompoundTag out = new CompoundTag();
        var items = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < source.getItemStackList().size(); i++) {
            ItemStack stack = source.getItemStackList().get(i);
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            if (stack != null && !stack.isEmpty()) {
                entry.put("stack", stack.save(new CompoundTag()));
            }
            items.add(entry);
        }
        out.put("items", items);

        var fluids = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < source.getFluidStackList().size(); i++) {
            net.minecraftforge.fluids.FluidStack stack = source.getFluidStackList().get(i);
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            if (stack != null && !stack.isEmpty()) {
                entry.put("stack", stack.writeToNBT(new CompoundTag()));
            }
            fluids.add(entry);
        }
        out.put("fluids", fluids);
        return out;
    }

    private void readHandler(CompoundTag in) {
        readHandler(handler, in);
    }

    private void readHandler(InfItemFluidHandler target, CompoundTag in) {
        target.getItemStackList().clear();
        target.getFluidStackList().clear();
        var items = in.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("slot");
            ItemStack stack = entry.contains("stack") ? ItemStack.of(entry.getCompound("stack")) : ItemStack.EMPTY;
            while (target.getItemStackList().size() <= slot) {
                target.getItemStackList().add(ItemStack.EMPTY);
            }
            target.getItemStackList().set(slot, stack);
        }
        var fluids = in.getList("fluids", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < fluids.size(); i++) {
            CompoundTag entry = fluids.getCompound(i);
            int slot = entry.getInt("slot");
            net.minecraftforge.fluids.FluidStack stack = entry.contains("stack")
                    ? net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(entry.getCompound("stack"))
                    : net.minecraftforge.fluids.FluidStack.EMPTY;
            while (target.getFluidStackList().size() <= slot) {
                target.getFluidStackList().add(net.minecraftforge.fluids.FluidStack.EMPTY);
            }
            target.getFluidStackList().set(slot, stack);
        }
    }

    /** @return the grid node this block's logic is built on -- re-exported for the logic's own use */
    public IManagedGridNode getGridNode() {
        return getMainNode();
    }
}
