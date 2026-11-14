/* SPDX-License-Identifier: GPL-3.0-only; NECO EF WorkerStatus, KasumiNova and contributors. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.client.model.data.ModelProperty;

/** Cosmetic metadata only; the world palette and structure matcher never receive worker status. */
public enum EFabricatorAppearance implements StringRepresentable {
    OFF, ON, RUN;
    public static final EnumProperty<EFabricatorAppearance> STATUS = EnumProperty.create("status", EFabricatorAppearance.class);
    public static final ModelProperty<EFabricatorAppearance> DATA = new ModelProperty<>();
    // ServerChunkCache.getChunkNow deliberately returns null on the lighting thread.
    // Publish only immutable light values there; never read/instantiate block entities or request chunks.
    // Weak world keys cannot retain a closed server/dimension. Parts remove their entries on unbind/unload.
    private static final java.util.Map<net.minecraft.server.level.ServerLevel, java.util.concurrent.ConcurrentMap<Long, Integer>> LIGHTS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static void publishLight(net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos pos, EFabricatorAppearance appearance) {
        var values = LIGHTS.computeIfAbsent(world, ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        if (appearance == OFF) values.remove(pos.asLong()); else values.put(pos.asLong(), 10);
    }
    public static int serverLight(net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos pos) {
        var values = LIGHTS.get(world); return values == null ? 0 : values.getOrDefault(pos.asLong(), 0);
    }
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static EFabricatorAppearance decode(int value) { return value >= 0 && value < values().length ? values()[value] : OFF; }
}
