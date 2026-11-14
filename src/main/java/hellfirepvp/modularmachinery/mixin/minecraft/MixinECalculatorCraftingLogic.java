/* SPDX-License-Identifier: GPL-3.0-only; EC lifecycle guards for AE2 15 execution. */
package hellfirepvp.modularmachinery.mixin.minecraft;

import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.api.networking.energy.IEnergyService;
import net.minecraft.world.level.Level;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets = "appeng.crafting.execution.CraftingCpuLogic", remap = false)
public abstract class MixinECalculatorCraftingLogic implements ECalculatorLogicBridge {
    @Shadow @Final private CraftingCPUCluster cluster;
    @Unique private ECalculatorCpuHost mmc$host() { return ((ECalculatorCpuBridge)(Object)cluster).mmc$ecHost(); }
    @Override public ECalculatorCpuHost mmc$ecLogicHost() { return mmc$host(); }
    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void mmc$requireReservation(IGrid grid, ICraftingPlan plan, IActionSource source, ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> ci) {
        var host = mmc$host();
        if (host != null && !host.submitting()) ci.setReturnValue(CraftingSubmitResult.CPU_OFFLINE);
    }
    @Redirect(method = "trySubmitJob", at = @At(value = "INVOKE", target = "Lappeng/crafting/execution/CraftingCpuHelper;tryExtractInitialItems(Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/IGrid;Lappeng/crafting/inv/ListCraftingInventory;Lappeng/api/networking/security/IActionSource;)Lappeng/api/stacks/GenericStack;"))
    private GenericStack mmc$retainRejectedRollback(ICraftingPlan plan, IGrid grid, ListCraftingInventory inventory, IActionSource source) {
        var host = mmc$host();
        return host == null ? CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, source) : host.extractInitialItems(plan, grid, inventory, source);
    }
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void mmc$rejectStaleInput(AEKey key, long amount, Actionable mode, CallbackInfoReturnable<Long> ci) {
        var host = mmc$host(); if (host != null && !host.active()) ci.setReturnValue(0L);
    }
    @ModifyVariable(method = "insert", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private long mmc$limitFinalOutputToRequester(long amount, AEKey key, long originalAmount, Actionable mode) {
        if (mmc$host() == null || amount <= 0) return amount;
        var logic = (appeng.crafting.execution.CraftingCpuLogic)(Object)this;
        var output = logic.getFinalJobOutput();
        var link = logic.getLastLink();
        // AE counts final outputs even when a detached requester accepts zero. Keep EC's work in flight instead.
        if (output != null && output.what().equals(key) && link instanceof appeng.crafting.CraftingLink nativeLink && !link.isStandalone())
            return Math.min(amount, nativeLink.insert(key, amount, Actionable.SIMULATE));
        return amount;
    }
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void mmc$rejectStaleExecution(int maxPatterns, CraftingService service, IEnergyService energy, Level level, CallbackInfoReturnable<Integer> ci) {
        var host = mmc$host(); if (host != null && (!host.active() || !host.belongsTo(service))) ci.setReturnValue(0);
    }
    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void mmc$countPushedPatterns(int maxPatterns, CraftingService service, IEnergyService energy, Level level, CallbackInfoReturnable<Integer> ci) {
        var host = mmc$host(); if (host != null) host.recordOperations(ci.getReturnValueI());
    }
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), cancellable = true)
    private void mmc$rejectOtherGridTick(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        var host = mmc$host(); if (host != null && !host.belongsTo(service)) ci.cancel();
    }
    @Inject(method = "storeItems", at = @At("HEAD"), cancellable = true)
    private void mmc$rejectStaleReturn(CallbackInfo ci) {
        var host = mmc$host(); if (host != null && !host.active()) ci.cancel();
    }
    @Inject(method = "cancel", at = @At("RETURN"))
    private void mmc$saveCancellation(CallbackInfo ci) { var host = mmc$host(); if (host != null) host.dirty(); }
    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void mmc$releaseFinished(CallbackInfo ci) { var host = mmc$host(); if (host != null) host.afterTick(); }
}
