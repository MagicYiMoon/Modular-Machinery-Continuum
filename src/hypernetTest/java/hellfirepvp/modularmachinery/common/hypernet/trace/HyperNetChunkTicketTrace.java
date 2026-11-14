/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet.trace;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetLifecycleVerification;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Disposable diagnostic only: exposes code which would accidentally refresh the unload fixture. */
@Mixin(ServerChunkCache.class)
public abstract class HyperNetChunkTicketTrace {
    @Inject(method="getChunkFutureMainThread",at=@At("HEAD"))
    private void mmc$trace(int x,int z,ChunkStatus status,boolean create,CallbackInfoReturnable<?> ci){
        if(create&&x==69&&z==64&&HyperNetLifecycleVerification.traceUnloadTicket())
            Modularmachinery.LOG.warn("[MMC][hypernet-lifecycle] processor chunk ticket refresh",new Exception("test-only chunk request trace"));
    }
}
