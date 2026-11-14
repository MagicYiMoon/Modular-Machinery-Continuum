/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.mixin.minecraft;

import hellfirepvp.modularmachinery.common.network.PktControllerRegistryLogin;
import net.minecraftforge.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Forge gathers channels in map order; our identity cache must precede registry injection. */
@Mixin(value = NetworkRegistry.class, remap = false)
public abstract class MixinControllerRegistryLogin {
    @Inject(method = "gatherLoginPayloads", at = @At("RETURN"), cancellable = true)
    private static void modularmachinery$controllerIdentitiesFirst(NetworkDirection direction, boolean local,
            CallbackInfoReturnable<List<NetworkRegistry.LoginPayload>> callback) {
        if (direction != NetworkDirection.LOGIN_TO_CLIENT || local) return;
        var original = callback.getReturnValue();
        var ordered = new ArrayList<NetworkRegistry.LoginPayload>();
        for (var payload : original) if (PktControllerRegistryLogin.CONTEXT.equals(payload.getMessageContext())) ordered.add(payload);
        for (var payload : original) if (!PktControllerRegistryLogin.CONTEXT.equals(payload.getMessageContext())) ordered.add(payload);
        callback.setReturnValue(ordered);
    }
}
