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
package hellfirepvp.modularmachinery.common.util;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;


























































 
public class InfItemFluidHandler implements IItemHandlerModifiable, IFluidHandler {

    /** The items this container holds. Grows on demand; never bounded. */
    protected final List<ItemStack> itemStackList = new ArrayList<>();

    /** The fluids this container holds. Grows on demand; never bounded. */
    protected final List<FluidStack> fluidStackList = new ArrayList<>();

    /**
     * The fixed, GUI-facing item half; {@link #machineVisibleItems()} is the view that joins it to the list above.
     *
     * <p><b>NOT consulted by the methods of this class, which is still the property §441 recorded.</b> It is consulted
     * by the <i>view</i>, and only the view is handed to a menu or a capability -- so "the container ignores its
     * sub-handler" and "the sub-handler is reachable" are both true, and the second one is what makes a slot over it a
     * live slot rather than storage nothing reads.</p>
     */
    protected volatile IItemHandlerModifiable subItemHandler = null;

    /** The fixed, GUI-facing fluid half; joined by {@link #machineVisibleFluids()}. Same rule as the item side. */
    protected volatile IFluidHandler subFluidHandler = null;

    /** Built once per sub-handler, because a view is a live object and a new one per call would be a new slot target. */
    private volatile IItemHandlerModifiable itemView = null;
    private volatile IFluidHandler fluidView = null;

    protected volatile IntConsumer onItemChanged = null;
    protected volatile IntConsumer onFluidChanged = null;

    public InfItemFluidHandler() {
    }

    public InfItemFluidHandler(IItemHandlerModifiable subItemHandler) {
        this.subItemHandler = subItemHandler;
    }

    public InfItemFluidHandler(IFluidHandler subFluidHandler) {
        this.subFluidHandler = subFluidHandler;
    }

    public InfItemFluidHandler(IItemHandlerModifiable subItemHandler, IFluidHandler subFluidHandler) {
        this.subItemHandler = subItemHandler;
        this.subFluidHandler = subFluidHandler;
    }

    // ------------------------------------------------------------------ fluid

    @Override
    public int getTanks() {
        // One virtual tank per list entry. Upstream's getTankProperties returned the same shape.
        return fluidStackList.size();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= fluidStackList.size()) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = fluidStackList.get(tank);
        return stack == null ? FluidStack.EMPTY : stack;
    }

    @Override
    public int getTankCapacity(int tank) {
        // UNBOUNDED on purpose: the container appends rather than refusing, so there is no per-tank ceiling to report.
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return !stack.isEmpty();
    }

    /**
     * Upstream's {@code fill}, branch for branch (U78-120).
     *
     * <p><b>The return value is unconditional</b> -- this is an unbounded container, and reporting a partial acceptance would be a different
     * behaviour from upstream's, not a modernised one.</p>
     */
    @Override
    public synchronized int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) {
            return 0;
        }
        if (action.simulate()) {
            return resource.getAmount();
        }

        int toFill = resource.getAmount();

        for (int i = 0; i < fluidStackList.size(); i++) {
            final FluidStack stackInSlot = fluidStackList.get(i);
            if (stackInSlot == null || stackInSlot.isEmpty()) {
                // BRANCH 1: an empty slot takes the incoming stack wholesale.
                fluidStackList.set(i, resource.copy());
                if (onFluidChanged != null) {
                    onFluidChanged.accept(i);
                }
                toFill = 0;
                break;
            }
            if (stackInSlot.isFluidEqual(resource)) {
                // BRANCH 2: absorb, bounded so `amount` cannot overflow into a negative.
                int maxCanFill = Math.min(toFill, Integer.MAX_VALUE - stackInSlot.getAmount());
                stackInSlot.setAmount(stackInSlot.getAmount() + maxCanFill);
                if (onFluidChanged != null) {
                    onFluidChanged.accept(i);
                }
                toFill -= maxCanFill;

                if (toFill <= 0) {
                    break;
                }
            }
        }

        if (toFill > 0) {
            // BRANCH 3: APPEND. This is what makes the container unbounded.
            fluidStackList.add(new FluidStack(resource, toFill));
            if (onFluidChanged != null) {
                onFluidChanged.accept(fluidStackList.size() - 1);
            }
        }

        return resource.getAmount();
    }

    @NotNull
    @Override
    public synchronized FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        // `resource` is non-null here (checked above), and `match` means "only this fluid qualifies".
        return drainInternal(resource, resource.getAmount(), action);
    }

    @NotNull
    @Override
    public synchronized FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        return drainInternal(null, maxDrain, action);
    }

    /**
     * The shared skeleton for both {@code drain} overloads.
     *
     * @param match  when non-null, only slots with this fluid qualify; when null, any slot may be drained
     * @param amount how much to take in total
     */
    private FluidStack drainInternal(@Nullable FluidStack match, int amount, FluidAction action) {
        int remaining = amount;
        FluidStack result = FluidStack.EMPTY;

        for (int i = 0; i < fluidStackList.size() && remaining > 0; i++) {
            FluidStack stackInSlot = fluidStackList.get(i);
            if (stackInSlot == null || stackInSlot.isEmpty()) {
                continue;
            }
            if (match != null && !stackInSlot.isFluidEqual(match)) {
                continue;
            }

            int taking = Math.min(remaining, stackInSlot.getAmount());
            if (result.isEmpty()) {
                // Keep the fluid identity of the FIRST slot that actually gives something, so the caller knows what it got.
                result = new FluidStack(stackInSlot, taking);
            } else {
                result.setAmount(result.getAmount() + taking);
            }
            remaining -= taking;

            if (!action.simulate()) {
                stackInSlot.setAmount(stackInSlot.getAmount() - taking);
                if (stackInSlot.getAmount() <= 0) {
                    // Emptied slots are REMOVED rather than left as empty entries, so getTanks() keeps reporting what is really there.
                    fluidStackList.remove(i);
                    i--;
                }
                if (onFluidChanged != null) {
                    onFluidChanged.accept(i);
                }
            }
        }

        return result;
    }

    // ------------------------------------------------------------------- item

    @Override
    public int getSlots() {
        return itemStackList.size();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= itemStackList.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = itemStackList.get(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public synchronized void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot < 0 || slot >= itemStackList.size()) {
            return;
        }
        itemStackList.set(slot, stack);
        if (onItemChanged != null) {
            onItemChanged.accept(slot);
        }
    }

    /** Upstream's {@code insertItem}: same three branches as {@code fill}, in item form. */
    @NotNull
    @Override
    public synchronized ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // The slot argument is honoured only when it exists; otherwise this container appends, like upstream.
        for (int i = 0; i < itemStackList.size(); i++) {
            if (slot >= 0 && i != slot) {
                continue;
            }
            ItemStack stackInSlot = itemStackList.get(i);
            if (stackInSlot == null || stackInSlot.isEmpty()) {
                if (simulate) {
                    return ItemStack.EMPTY;
                }
                itemStackList.set(i, stack.copy());
                if (onItemChanged != null) {
                    onItemChanged.accept(i);
                }
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(stackInSlot, stack)) {
                int maxCanInsert = Math.min(stack.getCount(), Integer.MAX_VALUE - stackInSlot.getCount());
                if (simulate) {
                    return maxCanInsert >= stack.getCount() ? ItemStack.EMPTY
                            : stack.copyWithCount(stack.getCount() - maxCanInsert);
                }
                stackInSlot.setCount(stackInSlot.getCount() + maxCanInsert);
                if (onItemChanged != null) {
                    onItemChanged.accept(i);
                }
                return maxCanInsert >= stack.getCount() ? ItemStack.EMPTY
                        : stack.copyWithCount(stack.getCount() - maxCanInsert);
            }
            if (slot >= 0) {
                // An explicitly named slot was asked for and it does not accept this stack.
                return stack;
            }
        }

        if (simulate) {
            return ItemStack.EMPTY;
        }
        // Nothing absorbed it: APPEND, which is what makes the item side unbounded too.
        itemStackList.add(stack.copy());
        if (onItemChanged != null) {
            onItemChanged.accept(itemStackList.size() - 1);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Upstream's {@code appendItem} -- the container's own "put this in", bypassing the slot protocol.
     */
    public synchronized void appendItem(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < itemStackList.size(); i++) {
            ItemStack stackInSlot = itemStackList.get(i);
            if (stackInSlot != null && !stackInSlot.isEmpty() && ItemStack.isSameItemSameTags(stackInSlot, stack)) {
                int maxCanInsert = Math.min(stack.getCount(), Integer.MAX_VALUE - stackInSlot.getCount());
                if (maxCanInsert > 0) {
                    stackInSlot.setCount(stackInSlot.getCount() + maxCanInsert);
                    if (onItemChanged != null) {
                        onItemChanged.accept(i);
                    }
                    stack = stack.copyWithCount(stack.getCount() - maxCanInsert);
                    if (stack.isEmpty()) {
                        return;
                    }
                }
            }
        }
        itemStackList.add(stack.copy());
        if (onItemChanged != null) {
            onItemChanged.accept(itemStackList.size() - 1);
        }
    }

    @NotNull
    @Override
    public synchronized ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= itemStackList.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = itemStackList.get(slot);
        if (stackInSlot == null || stackInSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int taking = Math.min(amount, stackInSlot.getCount());
        ItemStack result = stackInSlot.copyWithCount(taking);

        if (!simulate) {
            stackInSlot.setCount(stackInSlot.getCount() - taking);
            if (stackInSlot.getCount() <= 0) {
                itemStackList.remove(slot);
            }
            if (onItemChanged != null) {
                onItemChanged.accept(slot);
            }
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        // UNBOUNDED, for the same reason getTankCapacity is.
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return !stack.isEmpty();
    }

    // ------------------------------------------------------------------ state

    public boolean isEmpty() {
        for (ItemStack stack : itemStackList) {
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        for (FluidStack stack : fluidStackList) {
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public List<ItemStack> getItemStackList() {
        return itemStackList;
    }

    public List<FluidStack> getFluidStackList() {
        return fluidStackList;
    }

    public void setOnItemChanged(IntConsumer onItemChanged) {
        this.onItemChanged = onItemChanged;
    }

    public void setOnFluidChanged(IntConsumer onFluidChanged) {
        this.onFluidChanged = onFluidChanged;
    }

    public IItemHandlerModifiable getSubItemHandler() {
        return subItemHandler;
    }

    public void setSubItemHandler(IItemHandlerModifiable subItemHandler) {
        this.subItemHandler = subItemHandler;
        this.itemView = null;
    }

    public IFluidHandler getSubFluidHandler() {
        return subFluidHandler;
    }

    public void setSubFluidHandler(IFluidHandler subFluidHandler) {
        this.subFluidHandler = subFluidHandler;
        this.fluidView = null;
    }

    // --------------------------------------------------- the two machine-visible views

    /**
     * <b>The independent storage, as one handler that everything sees.</b> Slots {@code [0, sub.getSlots())} are the
     * fixed GUI-facing half and everything after them is this container's own growing list, so a caller that walks the
     * whole handler walks the single inv AND the cache in one pass.
     *
     * <h2>Rule A0: what upstream is, and why the port wraps where upstream concatenates</h2>
     *
     * <p>UPSTREAM: {@code subItemHandler} is an {@code AppEngInternalInventory} (U93, 2 slots) that is passed
     * <i>into</i> the container ({@code new InfItemFluidHandler(subItemHandler, subFluidHandler)}, U107), and
     * {@code InfItemFluidHandler}'s item methods <b>try the sub-handler first and then their own list</b> -- the
     * concatenation is inside the container's own methods, which is why upstream's single {@code handler} object is
     * already the whole answer and the two GUI slots need no adapter.</p>
     *
     * <p>EVIDENCE THAT THE SAME BODY CANNOT BE FOLLOWED HERE: this class's own javadoc records the opposite property of
     * itself -- "the sub-handler is not consulted by the methods below" -- and <b>that property is load-bearing in this
     * port</b>: {@code pushPattern}'s half is not written, so the only writer these two lists have is the machine's own
     * consumption. Routing the container's methods through a sub-handler that nothing fills would make every item
     * {@code insertItem} places land in a slot the machine reads through a <i>different</i> index. Rather than change
     * six methods whose behaviour §441 verified branch by branch, the concatenation lives in ONE place: this view.</p>
     *
     * <p>WHAT THE MACHINE SEES, and this is the point of the whole file: the view is built over <b>this</b>
     * container's two lists, so two components resolved from two different patterns -- or from the same pattern twice --
     * get handlers backed by <b>the same objects</b>. Item counted once per list, and the two lists are disjoint by
     * construction (the sub-handler is an {@code ItemStackHandler} the container never touches), so nothing is
     * double-counted.</p>
     *
     * @return the joined item handler, or {@code null} when this container has no fixed item half -- which is the case
     *         for every {@code InfItemFluidHandler} built without one, and returning null rather than a second view of
     *         the list is what stops a caller exposing the cache twice
     */
    @Nullable
    public IItemHandlerModifiable machineVisibleItems() {
        IItemHandlerModifiable sub = this.subItemHandler;
        if (sub == null) {
            return null;
        }
        IItemHandlerModifiable view = this.itemView;
        if (view == null) {
            view = new ItemAndSubItemHandler(this, sub);
            this.itemView = view;
        }
        return view;
    }

    /**
     * The fluid half of {@link #machineVisibleItems()}: tank 0 is the fixed barrel under the wells, every tank after it
     * is this container's own list. Same A0 record; the difference is only that a tank carries an amount.
     *
     * @return the joined fluid handler, or {@code null} when this container has no fixed fluid half
     */
    @Nullable
    public IFluidHandler machineVisibleFluids() {
        IFluidHandler sub = this.subFluidHandler;
        if (sub == null) {
            return null;
        }
        IFluidHandler view = this.fluidView;
        if (view == null) {
            view = new FluidAndSubTankHandler(this, sub);
            this.fluidView = view;
        }
        return view;
    }

    /** @return how many slots the machine sees, fixed half first */
    public int machineVisibleSlotCount() {
        IItemHandlerModifiable sub = this.subItemHandler;
        return (sub == null ? 0 : sub.getSlots()) + itemStackList.size();
    }

    /**
     * The item view: {@code sub} first, then {@code owner}'s growing list.
     *
     * <p>Read-only in the sense that matters -- it owns no storage of its own, so every method is a delegation and its
     * index arithmetic is the only rule it adds. An index below {@code sub.getSlots()} is the sub-handler; at or above
     * it, {@code index - sub.getSlots()} is the list entry.</p>
     */
    private static final class ItemAndSubItemHandler implements IItemHandlerModifiable {

        private final InfItemFluidHandler owner;
        private final IItemHandlerModifiable sub;

        ItemAndSubItemHandler(InfItemFluidHandler owner, IItemHandlerModifiable sub) {
            this.owner = owner;
            this.sub = sub;
        }

        @Override
        public int getSlots() {
            return sub.getSlots() + owner.itemStackList.size();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0) {
                return ItemStack.EMPTY;
            }
            if (slot < sub.getSlots()) {
                return sub.getStackInSlot(slot);
            }
            return owner.getStackInSlot(slot - sub.getSlots());
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (slot < 0) {
                return;
            }
            if (slot < sub.getSlots()) {
                sub.setStackInSlot(slot, stack);
                return;
            }
            owner.setStackInSlot(slot - sub.getSlots(), stack);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot < 0) {
                return ItemStack.EMPTY;
            }
            if (slot < sub.getSlots()) {
                return sub.insertItem(slot, stack, simulate);
            }
            return owner.insertItem(slot - sub.getSlots(), stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0) {
                return ItemStack.EMPTY;
            }
            if (slot < sub.getSlots()) {
                return sub.extractItem(slot, amount, simulate);
            }
            return owner.extractItem(slot - sub.getSlots(), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0) {
                return 0;
            }
            return slot < sub.getSlots() ? sub.getSlotLimit(slot) : owner.getSlotLimit(slot - sub.getSlots());
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < 0) {
                return false;
            }
            return slot < sub.getSlots() ? sub.isItemValid(slot, stack)
                    : owner.isItemValid(slot - sub.getSlots(), stack);
        }
    }

    /**
     * The fluid view: the sub-tank first, then {@code owner}'s list.
     *
     * <p><b>The one place it is NOT a plain delegation is the tank COUNT.</b> {@code owner.getTanks()} reports the list
     * only, so a caller that asked the owner directly would never see the sub-tank -- which is why the view exists, and
     * why its {@code getTanks} is the sum. {@code fill} and {@code drain} address {@code owner}'s own overloads for the
     * list half, and those already append/remove entries the way §441 measured.</p>
     */
    private static final class FluidAndSubTankHandler implements IFluidHandler {

        private final InfItemFluidHandler owner;
        private final IFluidHandler sub;

        FluidAndSubTankHandler(InfItemFluidHandler owner, IFluidHandler sub) {
            this.owner = owner;
            this.sub = sub;
        }

        @Override
        public int getTanks() {
            return sub.getTanks() + owner.getTanks();
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < 0) {
                return FluidStack.EMPTY;
            }
            return tank < sub.getTanks() ? sub.getFluidInTank(tank) : owner.getFluidInTank(tank - sub.getTanks());
        }

        @Override
        public int getTankCapacity(int tank) {
            if (tank < 0) {
                return 0;
            }
            return tank < sub.getTanks() ? sub.getTankCapacity(tank) : owner.getTankCapacity(tank - sub.getTanks());
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            if (tank < 0) {
                return false;
            }
            return tank < sub.getTanks() ? sub.isFluidValid(tank, stack)
                    : owner.isFluidValid(tank - sub.getTanks(), stack);
        }

        /** Fills the sub-tank first, then the list. The remainder is what neither took. */
        @Override
        public synchronized int fill(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty()) {
                return 0;
            }
            int first = sub.fill(resource, action);
            if (first >= resource.getAmount()) {
                return first;
            }
            FluidStack rest = new FluidStack(resource, resource.getAmount() - first);
            return first + owner.fill(rest, action);
        }

        /** Drains the sub-tank first, then the list -- the reverse of {@link #fill} and for the same reason. */
        @NotNull
        @Override
        public synchronized FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack fromSub = sub.drain(resource, action);
            if (!fromSub.isEmpty() && fromSub.getAmount() >= resource.getAmount()) {
                return fromSub;
            }
            int stillWanted = resource.getAmount() - (fromSub.isEmpty() ? 0 : fromSub.getAmount());
            FluidStack fromList = owner.drain(new FluidStack(resource, stillWanted), action);
            if (fromSub.isEmpty()) {
                return fromList;
            }
            if (fromList.isEmpty()) {
                return fromSub;
            }
            FluidStack total = fromSub.copy();
            total.setAmount(fromSub.getAmount() + fromList.getAmount());
            return total;
        }

        /** The same two-stage walk as {@link #drain(FluidStack, FluidAction)}, with no fluid to match. */
        @NotNull
        @Override
        public synchronized FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack fromSub = sub.drain(maxDrain, action);
            int stillWanted = maxDrain - (fromSub.isEmpty() ? 0 : fromSub.getAmount());
            FluidStack fromList = stillWanted <= 0 ? FluidStack.EMPTY : owner.drain(stillWanted, action);
            if (fromSub.isEmpty()) {
                return fromList;
            }
            if (fromList.isEmpty()) {
                return fromSub;
            }
            FluidStack total = fromSub.copy();
            total.setAmount(fromSub.getAmount() + fromList.getAmount());
            return total;
        }
    }

    /** @return how many entries this container holds, across both kinds */
    public int getStored() {
        return itemStackList.size() + fluidStackList.size();
    }
}
