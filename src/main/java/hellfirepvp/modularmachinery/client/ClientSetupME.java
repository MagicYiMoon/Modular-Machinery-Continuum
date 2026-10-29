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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client;

import java.util.List;




















 
public final class ClientSetupME {

    private ClientSetupME() {
    }

    /**
     * Binds the ME screens. Called ONLY from inside the AE2 guard in ClientSetup.
     *
     * @param bound the caller own list of bound menu names, so the same self-check covers these entries
     */
    public static void register(java.util.List<String> bound) {
        net.minecraft.client.gui.screens.MenuScreens.register(
                hellfirepvp.modularmachinery.common.registry.RegistryMEMenus.PATTERN_PROVIDER.get(),
                 
                // `hellfirepvp.modularmachinery.client.gui` while living in `client/screen/`, and this line named it by
                // the DECLARED name -- so the two agreed and javac never had to complain. A file whose package
                // disagrees with its path compiles only while every build places it on one source root; the moment
                // anything generates sources, opens it in an IDE or enforces the relation, it breaks for a reason
                 
                hellfirepvp.modularmachinery.client.screen.ScreenMEPatternProvider::new);
        bound.add("me_pattern_provider");
         
        // pattern provider is: ClientSetup's body must name nothing that reaches an AE2 type, or the guard around this call
        // would be decorating a link that had already failed.
        net.minecraft.client.gui.screens.MenuScreens.register(
                hellfirepvp.modularmachinery.common.registry.RegistryMEMenus.ME_FLUID_INPUT_BUS.get(),
                hellfirepvp.modularmachinery.client.screen.ScreenMEFluidInputBus::new);
        bound.add("me_fluid_input_bus");
         
        // could not be opened at all -- a menu type without a screen binding throws on the CLIENT the first time
        // one is opened, which is why the binding and the registration are recorded together in the same log line.
        net.minecraft.client.gui.screens.MenuScreens.register(
                hellfirepvp.modularmachinery.common.registry.RegistryMEMenus.ME_ITEM_INPUT_BUS.get(),
                hellfirepvp.modularmachinery.client.screen.ScreenMEItemInputBus::new);
        bound.add("me_item_input_bus");
        // AUDIT_COMPONENTS row 11: the ME item OUTPUT bus, the direction that had no menu and no screen at all.
        // Same three-part entry as the line above, and the same two facts are recorded together for the same reason:
        // the menu type is registered in RegistryMEMenus (AE2-gated) and the binding is here (client-only), so neither
        // alone makes the block openable.
        net.minecraft.client.gui.screens.MenuScreens.register(
                hellfirepvp.modularmachinery.common.registry.RegistryMEMenus.ME_ITEM_OUTPUT_BUS.get(),
                hellfirepvp.modularmachinery.client.screen.ScreenMEItemOutputBus::new);
        bound.add("me_item_output_bus");
        net.minecraft.client.gui.screens.MenuScreens.register(
                hellfirepvp.modularmachinery.common.registry.RegistryMEMenus.ME_FLUID_OUTPUT_BUS.get(),
                hellfirepvp.modularmachinery.client.screen.ScreenMEFluidOutputBus::new);
        bound.add("me_fluid_output_bus");
        if (hellfirepvp.modularmachinery.common.block.BlockMEGasBus.available()) {
            hellfirepvp.modularmachinery.integration.appmek.ScreenMEGasBus.register();
            bound.add("me_gas_bus");
        }
    }
}
