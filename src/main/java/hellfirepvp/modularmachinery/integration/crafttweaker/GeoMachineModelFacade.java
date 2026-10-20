/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import org.openzen.zencode.java.ZenCodeType;

/** CraftTweaker namespace matching the source mod's GeoMachineModel helper. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.GeoMachineModel")
public final class GeoMachineModelFacade {
    private GeoMachineModelFacade() { }

    @ZenCodeType.Method
    public static boolean registerGeoMachineModel(String modelName, String model, String texture, String animation) {
        if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName cannot be empty");
        GeoMachineModelRegistry.registerModel(modelName, model, texture, animation);
        return true;
    }
}
