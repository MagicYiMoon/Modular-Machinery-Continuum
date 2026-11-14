/* SPDX-License-Identifier: GPL-3.0-only; NECO virtual CPUs adapted to AE2 15. */
package hellfirepvp.modularmachinery.mixin.minecraft;

import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Every native CPU keeps a null marker and follows AE's original paths unchanged. */
@Pseudo
@Mixin(targets = "appeng.me.cluster.implementations.CraftingCPUCluster", remap = false)
public abstract class MixinECalculatorCPU implements ECalculatorCpuBridge {
    @Unique private ECalculatorCpuHost mmc$host;
    @Override public ECalculatorCpuHost mmc$ecHost() { return mmc$host; }
    @Override public void mmc$ecHost(ECalculatorCpuHost host) { mmc$host = host; }
    @Inject(method = "getNode", at = @At("HEAD"), cancellable = true)
    private void mmc$node(CallbackInfoReturnable<IGridNode> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.node()); }
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void mmc$active(CallbackInfoReturnable<Boolean> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.active()); }
    @Inject(method = "isBusy", at = @At("HEAD"), cancellable = true)
    private void mmc$busy(CallbackInfoReturnable<Boolean> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.busy()); }
    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true)
    private void mmc$level(CallbackInfoReturnable<Level> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.level()); }
    @Inject(method = "getSrc", at = @At("HEAD"), cancellable = true)
    private void mmc$source(CallbackInfoReturnable<IActionSource> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.source()); }
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void mmc$name(CallbackInfoReturnable<Component> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.name()); }
    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void mmc$storage(CallbackInfoReturnable<Long> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.storage()); }
    @Inject(method = "getCoProcessors", at = @At("HEAD"), cancellable = true)
    private void mmc$parallelism(CallbackInfoReturnable<Integer> ci) { if (mmc$host != null) ci.setReturnValue(mmc$host.parallelism()); }
    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void mmc$dirty(CallbackInfo ci) { if (mmc$host != null) { mmc$host.dirty(); ci.cancel(); } }
    @Redirect(method = "submitJob", at = @At(value = "INVOKE", target = "Lappeng/crafting/execution/CraftingCpuLogic;trySubmitJob(Lappeng/api/networking/IGrid;Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingRequester;)Lappeng/api/networking/crafting/ICraftingSubmitResult;"))
    private ICraftingSubmitResult mmc$submit(CraftingCpuLogic logic, IGrid grid, ICraftingPlan plan, IActionSource source, ICraftingRequester requester) {
        return mmc$host == null ? logic.trySubmitJob(grid, plan, source, requester) : mmc$host.submit(grid, plan, source, requester);
    }
}
