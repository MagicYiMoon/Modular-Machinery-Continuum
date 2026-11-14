/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Weak tracking is only for final cleanup, never a position/network lookup cache. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class HyperNetLifecycle {
    private static final Map<HyperNetNode, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<HyperNetNode,Boolean> RESEARCH_END=Collections.synchronizedMap(new WeakHashMap<>());
    private HyperNetLifecycle() { }
    static void researchWorked(HyperNetNode node){RESEARCH_END.put(node,Boolean.TRUE);}
    static void track(HyperNetNode node) { LIVE.put(node, Boolean.TRUE); }
    private static List<HyperNetNode> snapshot() { synchronized (LIVE) { return List.copyOf(LIVE.keySet()); } }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        for (var node : snapshot()) if (node.level() == event.getLevel()) { node.close(); LIVE.remove(node); RESEARCH_END.remove(node); }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void endTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        List<HyperNetNode> pending;
        synchronized(RESEARCH_END){pending=List.copyOf(RESEARCH_END.keySet());RESEARCH_END.clear();}
        for(var node:pending)if(!node.isClosed())node.finishResearchTick();
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (var node : snapshot()) if (node.level().getServer() == event.getServer()) { node.close(); LIVE.remove(node); RESEARCH_END.remove(node); }
    }
}
