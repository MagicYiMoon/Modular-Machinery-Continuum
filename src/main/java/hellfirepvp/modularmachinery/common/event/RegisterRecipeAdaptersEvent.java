/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapter;
import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired once before recipe definitions are expanded so optional compatibility modules can register adapters.
 *
 * <p>The original CE used a Forge recipe-adapter registry populated conditionally by IC2, NuclearCraft,
 * TConstruct and Thermal Expansion integrations. Those 1.12 APIs cannot be linked from a Forge 1.20.1 base
 * jar. This event keeps the same extension point without loading any optional class: a compatibility module
 * declares its own adapter and registers it when the event is posted.</p>
 */
public final class RegisterRecipeAdaptersEvent extends Event {

    /** Registers or replaces an adapter under its JSON-facing resource location. */
    public void register(ResourceLocation name, RecipeAdapter adapter) {
        RecipeAdapterRegistry.register(Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(adapter, "adapter"));
    }

    /** Convenience overload for a namespaced string such as {@code thermal:insolator}. */
    public void register(String name, RecipeAdapter adapter) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) throw new IllegalArgumentException("Invalid recipe adapter id: " + name);
        register(id, adapter);
    }

    /** Returns the adapter currently installed under the given id, if any. */
    @Nullable
    public RecipeAdapter get(ResourceLocation name) {
        return RecipeAdapterRegistry.get(name);
    }
}
