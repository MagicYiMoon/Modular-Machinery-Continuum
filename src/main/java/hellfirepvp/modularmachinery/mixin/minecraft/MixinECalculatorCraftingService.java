/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO crafting-cache CPU publication. */
package hellfirepvp.modularmachinery.mixin.minecraft;

import appeng.api.networking.*;
import appeng.crafting.CraftingLink;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorMEChannelNode;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorLogicBridge;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorCpuBridge;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

@Pseudo
@Mixin(targets = "appeng.me.service.CraftingService", remap = false)
public abstract class MixinECalculatorCraftingService {
    @Shadow @Final private Set<CraftingCPUCluster> craftingCPUClusters;
    @Shadow @Final private IGrid grid;
    @Shadow private boolean updateList;
    @Shadow public abstract void addLink(CraftingLink link);
    @Redirect(method = "onServerEndTick", at = @At(value = "INVOKE", target = "Lappeng/crafting/execution/CraftingCpuLogic;tickCraftingLogic(Lappeng/api/networking/energy/IEnergyService;Lappeng/me/service/CraftingService;)V"))
    private void mmc$measureCpuTick(CraftingCpuLogic logic, appeng.api.networking.energy.IEnergyService energy, appeng.me.service.CraftingService service) {
        var host = ((ECalculatorLogicBridge)(Object)logic).mmc$ecLogicHost();
        if (host == null || !host.busy() || !host.belongsTo(grid)) { logic.tickCraftingLogic(energy, service); return; }
        host.beginMeasurement();
        long start = System.nanoTime();
        try { logic.tickCraftingLogic(energy, service); }
        finally { host.endMeasurement(System.nanoTime() - start); }
    }
    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void mmc$removeUnavailableCpus(CallbackInfo ci) {
        // A border chunk may keep the channel loaded without ticking it after a neighbouring core unloads.
        // Do not wait for that channel's block tick to remove a retired CPU from AE's link-liveness roster.
        mmc$pruneUnavailableCpus();
    }
    @Unique
    private void mmc$pruneUnavailableCpus() {
        if (craftingCPUClusters.removeIf(cpu -> {
            var host = ((ECalculatorCpuBridge)(Object)cpu).mmc$ecHost();
            return host != null && !host.belongsTo(grid);
        })) updateList = true;
    }
    @Inject(method = "insertIntoCpus", at = @At("HEAD"))
    private void mmc$rejectOtherGridOutput(AEKey key, long amount, Actionable mode, CallbackInfoReturnable<Long> ci) {
        // AdvancedAE overwrites this method to visit its own CPUs as well. A HEAD injection can
        // coexist with that overwrite; redirecting calls inside it cannot at the same priority.
        // Remove only foreign/retired EC hosts before either implementation iterates AE's roster.
        mmc$pruneUnavailableCpus();
    }
    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void mmc$publishCpus(CallbackInfo ci) {
        for (var channel : grid.getMachines(ECalculatorMEChannelNode.class)) for (var cpu : channel.trackedCpus()) {
            craftingCPUClusters.add(cpu.cluster);
            if (cpu.cluster.craftingLogic.getLastLink() instanceof CraftingLink link) addLink(link);
        }
    }
    @Inject(method = "addNode", at = @At("TAIL"))
    private void mmc$add(IGridNode node, CompoundTag data, CallbackInfo ci) { if (node.getOwner() instanceof ECalculatorMEChannelNode) updateList = true; }
    @Inject(method = "removeNode", at = @At("TAIL"))
    private void mmc$remove(IGridNode node, CallbackInfo ci) { if (node.getOwner() instanceof ECalculatorMEChannelNode) updateList = true; }
}
