/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import appeng.api.networking.crafting.ICraftingCPU;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorCpuBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Requester cancellation can arrive while an EC is unpowered and cannot tick its crafting logic. */
@Pseudo
@Mixin(targets="appeng.crafting.CraftingLink",remap=false)
public abstract class MixinECalculatorCraftingLink {
    @Shadow @Final private ICraftingCPU cpu;
    @Inject(method={"setCanceled","setDone"},at=@At("RETURN"))
    private void mmc$saveLinkState(boolean value,CallbackInfo ci) {
        // Native link constructors set these flags before assigning cpu, so null is expected there.
        if(cpu instanceof ECalculatorCpuBridge bridge && bridge.mmc$ecHost()!=null) bridge.mmc$ecHost().dirty();
    }
}
