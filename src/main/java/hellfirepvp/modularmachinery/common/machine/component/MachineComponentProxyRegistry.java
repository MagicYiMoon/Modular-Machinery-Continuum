/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine.component;

import hellfirepvp.modularmachinery.common.event.RegisterMachineComponentProxiesEvent;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModLoader;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Source-style named registry. Registration order decides overlapping adapters; native components win. */
public final class MachineComponentProxyRegistry {
    public static final MachineComponentProxyRegistry INSTANCE = new MachineComponentProxyRegistry();
    private volatile Map<String, MachineComponentProxy> proxies = Map.of();
    private boolean initialized;
    private MachineComponentProxyRegistry() { }

    public synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        // The Forge game bus is still stopped during common setup; use the mod lifecycle buses.
        ModLoader.get().postEvent(new RegisterMachineComponentProxiesEvent(this));
    }
    public synchronized boolean isInitialized() { return initialized; }
    /** Immutable registration snapshot, never a cache of block entities or capabilities. */
    public Map<String, MachineComponentProxy> getRegistry() { return proxies; }
    public synchronized MachineComponentProxy register(String name, MachineComponentProxy proxy) {
        Objects.requireNonNull(name); Objects.requireNonNull(proxy);
        if (name.isBlank()) throw new IllegalArgumentException("Empty component proxy key");
        if (proxies.containsKey(name)) throw new IllegalArgumentException("Duplicate MachineComponentProxy proxy key: " + name);
        var next = new LinkedHashMap<>(proxies); next.put(name, proxy);
        proxies = Collections.unmodifiableMap(next);
        return proxy;
    }
    public synchronized void unregister(String name) {
        if (!proxies.containsKey(name)) return;
        var next = new LinkedHashMap<>(proxies); next.remove(name);
        proxies = Collections.unmodifiableMap(next);
    }
    public @Nullable ProxiedMachineComponent proxy(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity instanceof MachineComponentTile) return null;
        for (var proxy : proxies.values()) {
            if (proxy.isSupported(blockEntity)) {
                var component = proxy.proxyComponent(blockEntity);
                // Unlike passive native parts, an external IO view must declare its direction explicitly.
                return component == null || component.getIOType() == null ? null : component;
            }
        }
        return null;
    }
}
