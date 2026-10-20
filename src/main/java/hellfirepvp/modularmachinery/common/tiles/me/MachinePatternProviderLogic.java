/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import hellfirepvp.modularmachinery.common.util.InfItemFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * AE2 15.x changed the pattern-provider push contract.  Its stock logic sends
 * the inputs to an adjacent {@code PatternProviderTarget}; the 1.12 MMCE
 * provider instead accepted those inputs into its own unbounded handler so a
 * multiblock could consume them.  Keeping the AE node and pattern inventory
 * in {@link PatternProviderLogic}, but overriding this one entry point,
 * preserves both the modern AE registration and the original MMCE behaviour.
 */
public final class MachinePatternProviderLogic extends PatternProviderLogic {
    private final InfItemFluidHandler inputHandler;

    private final TileMEPatternProvider owner;

    public MachinePatternProviderLogic(IManagedGridNode node, TileMEPatternProvider owner,
                                       int patterns, InfItemFluidHandler inputHandler) {
        super(node, owner, patterns);
        this.owner = owner;
        this.inputHandler = inputHandler;
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
        if (details == null || inputHolder == null || !getAvailablePatterns().contains(details)
                || !owner.getMainNode().isActive() || !owner.acceptsPattern(false, details)) {
            return false;
        }

        // KeyCounter is the exact set of stacks extracted by the AE crafting
        // CPU.  Store those stacks in the same handler that MachineContext
        // exposes as the provider's INPUT component.  A long is split into
        // legal Forge stack amounts/tank amounts where necessary.
        InfItemFluidHandler targetHandler = owner.handlerForPattern(details);
        if (targetHandler == null) return false;
        owner.beginHandlerSyncBatch();
        try {
            for (KeyCounter counter : inputHolder) {
                if (counter == null) continue;
                for (var entry : counter) {
                    AEKey key = entry.getKey();
                    long amount = entry.getLongValue();
                    if (amount <= 0) continue;
                    if (key instanceof AEItemKey item) {
                        while (amount > 0) {
                            int n = (int) Math.min(Integer.MAX_VALUE, amount);
                            targetHandler.appendItem(item.toStack(n));
                            amount -= n;
                        }
                    } else if (key instanceof AEFluidKey fluid) {
                        while (amount > 0) {
                            int n = (int) Math.min(Integer.MAX_VALUE, amount);
                            FluidStack stack = fluid.toStack(n);
                            targetHandler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                            amount -= n;
                        }
                    }
                }
            }
            owner.onPatternPushed(details);
        } finally {
            owner.endHandlerSyncBatch();
        }
        return true;
    }

    @Override
    public boolean isBusy() {
        return owner.busy();
    }

}
