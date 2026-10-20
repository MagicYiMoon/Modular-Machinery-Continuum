/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.IngredientArrayBuilder")
public final class IngredientArrayBuilder {
    private IngredientArrayBuilder() {}
    @ZenCodeType.Method public static IngredientArrayPrimer newBuilder() { return new IngredientArrayPrimer(); }
}
