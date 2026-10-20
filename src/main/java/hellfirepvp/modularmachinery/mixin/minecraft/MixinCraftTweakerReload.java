/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CT has no pre-execution listener in 14.0.60; this hook also runs for an empty scripts directory. */
@Pseudo
@Mixin(targets = "com.blamejared.crafttweaker.impl.script.ScriptReloadListener", remap = false)
public abstract class MixinCraftTweakerReload {
    @Inject(method = "prepareRun", at = @At("HEAD"), remap = false)
    private void mmce$prepareServerScripts(CallbackInfoReturnable<Object> callback) {
        MachineReloadListener.prepareScriptReload();
    }
}
