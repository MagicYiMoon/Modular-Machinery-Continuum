/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry a recipe's {@code "type": "modularmachinery:item"} is resolved against.
 *
 * <h2>What this replaces, and why a map rather than a Forge registry</h2>
 *
 * <p>PLAN section 374: {@code RecipeLoader} had a three-case {@code switch} on the type string. The switch could not be
 * extended from outside the class, which is why the five upstream types this port does not implement were collected into a
 * list and reported instead of loaded, and why §374 called the missing extension point the shared root of three separate
 * gaps -- the five types, the twenty {@code modularmagic} placeholders, and any addon at all.
 *
 * <p>Upstream registers these through {@code RegistriesMM.REQUIREMENT_TYPE_REGISTRY}, a
 * {@code ForgeRegistry<RequirementType<?, ?>>} looked up with {@code getValue} at {@code MachineRecipe:452}. <b>1.20.1 has no
 * {@code IForgeRegistryEntry}</b>, so the entries could not carry a registry name that Forge assigns; a
 * {@link ResourceLocation}-keyed map is what that registry becomes on this version. That is the era's mapping, and it is
 * also why this class is small.
 *
 * <h2>Ordering, and why it is not incidental</h2>
 *
 * <p>The map is a {@link LinkedHashMap} so {@link #names()} reports types in registration order. The error message a pack
 * author reads when they typo a type name lists what IS available, and a stable order means the message does not change
 * between runs for reasons that have nothing to do with their recipe.
 */
public final class RequirementTypeRegistry {

    private static final Map<ResourceLocation, RequirementType> TYPES = new LinkedHashMap<>();

    private RequirementTypeRegistry() {
    }

    /**
     * Registers a requirement type.
     *
     * <p>Registering the same name twice REPLACES the entry rather than throwing. That is deliberate: the three built-ins
     * are registered on first touch, and a pack or addon that wants to override one is doing something the mod should
     * allow rather than crash on. The previous type is returned so a caller can detect the replacement if it cares.
     *
     * @param name the type string as it appears in JSON, e.g. {@code modularmachinery:item}
     * @return the type that was registered under this name before, or {@code null}
     */
    public static synchronized RequirementType register(ResourceLocation name, RequirementType type) {
        if (name == null || type == null) {
            return null;
        }
        RequirementType previous = TYPES.put(name, type);
        if (previous != null) {
            Modularmachinery.LOG.info("[MMCE] requirement type {} re-registered; the previous one is replaced", name);
        }
        return previous;
    }

    /**
     * Looks a type up by name.
     *
     * @return the registered type, or {@code null} when nothing is registered under that name
     */
    @Nullable
    public static synchronized RequirementType get(@Nullable ResourceLocation name) {
        return name == null ? null : TYPES.get(name);
    }

    /** @return every registered name, in registration order; the loader lists these when a type is unknown */
    public static synchronized List<ResourceLocation> names() {
        return List.copyOf(TYPES.keySet());
    }

    /** @return the registry, unmodifiable; for iterating types without snapshotting on every call */
    public static synchronized Map<ResourceLocation, RequirementType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
