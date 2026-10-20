/* SPDX-License-Identifier: GPL-3.0-only
 * Port of upstream RecipeBuilder (HellFirePvP / Modular Machinery 2019), modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;
import net.minecraft.resources.ResourceLocation;

@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.RecipeBuilder")
public final class RecipeBuilder {
    private RecipeBuilder() { }
    @ZenCodeType.Method
    public static RecipePrimer newBuilder(String name, String machine, int ticks) {
        return newBuilder(name, machine, ticks, 0, false);
    }
    @ZenCodeType.Method
    public static RecipePrimer newBuilder(String name, String machine, int ticks, int priority) {
        return newBuilder(name, machine, ticks, priority, false);
    }
    @ZenCodeType.Method
    public static RecipePrimer newBuilder(String name, String machine, int ticks, int priority, boolean cancel) {
        return new RecipePrimer(normalize(name), normalize(machine), Math.max(1, ticks), priority, cancel);
    }
    private static String normalize(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) throw new IllegalArgumentException("Invalid recipe/machine name: " + name);
        return id.getNamespace().equals("minecraft") || id.getNamespace().equals("modularmachinery")
                ? id.getPath() : id.toString();
    }
}
