/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.world;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.TileEFabricatorPart;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Chunk BE metadata can precede the queued light-section initialization. Recheck after both have settled. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EFabricatorVisualUpdates {
    private record Pending(int delay, int attempt) { }
    private static final Map<TileEFabricatorPart, Pending> PENDING = new IdentityHashMap<>();
    private EFabricatorVisualUpdates() { }
    public static void enqueue(TileEFabricatorPart part) { PENDING.put(part, new Pending(2, 0)); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (level == null) { PENDING.clear(); return; }
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var part = entry.getKey(); var pending = entry.getValue();
            if (part.isRemoved() || part.getLevel() != level || level.getBlockEntity(part.getBlockPos()) != part) { iterator.remove(); continue; }
            if (pending.delay() > 0) { entry.setValue(new Pending(pending.delay() - 1, pending.attempt())); continue; }
            level.getLightEngine().checkBlock(part.getBlockPos());
            if (pending.attempt() == 2) iterator.remove();
            else entry.setValue(new Pending(pending.attempt() == 0 ? 4 : 20, pending.attempt() + 1));
        }
    }
}
