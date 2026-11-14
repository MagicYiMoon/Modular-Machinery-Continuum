/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator.trace;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorLifecycleVerification;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disposable-only diagnostic: reports callers refreshing the tested core chunk's UNKNOWN ticket. */
@Mixin(ServerChunkCache.class)
public abstract class ECalculatorChunkTicketTrace {
    @Inject(method="getChunkFutureMainThread",at=@At("HEAD"))
    private void mmc$trace(int x,int z,ChunkStatus status,boolean create,CallbackInfoReturnable<?> ci){
        if(create&&x==32&&z==32&&ECalculatorLifecycleVerification.traceUnloadTicket())
            Modularmachinery.LOG.warn("[MMC][ec-lifecycle] core chunk ticket refresh",new Exception("test-only chunk request trace"));
    }
}
