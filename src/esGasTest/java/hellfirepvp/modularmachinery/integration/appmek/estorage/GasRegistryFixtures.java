/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Disposable chemical registry entries to exercise the 25-type boundary. Never shipped. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GasRegistryFixtures {
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(MekanismAPI.GAS_REGISTRY_NAME, helper -> {
            for (int i = 0; i < 26; i++) helper.register(new ResourceLocation(Modularmachinery.MODID, "es_test_gas_" + i), new Gas(GasBuilder.builder()));
        });
    }
}
