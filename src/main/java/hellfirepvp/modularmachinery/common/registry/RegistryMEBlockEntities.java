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
package hellfirepvp.modularmachinery.common.registry;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;










































 
public final class RegistryMEBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Modularmachinery.MODID);

    /**
     * The ME item input bus.
     *
     * <p><b>The slot count is NOT stated here any more, and both of the numbers it used to state were wrong.</b> This
     * register wrote {@code 27} for both directions, while upstream's input bus is <b>16 buffer + 16 configuration</b>
     * slots ({@code MEItemInputBus} U36/U57) and its output bus is <b>36</b> ({@code MEItemOutputBus} U28). The counts
     * now live in the classes they describe, as constants the constructors read -- so there is no argument here for a
     * caller to get wrong, and the two directions cannot be given one shared number again.</p>
     */
    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.me.MEItemInputBus>>
            ME_ITEM_INPUT_BUS = TYPES.register("blockmeiteminputbus", () -> BlockEntityType.Builder
            .of((pos, state) -> new hellfirepvp.modularmachinery.common.tiles.me.MEItemInputBus(
                            RegistryMEBlockEntities.ME_ITEM_INPUT_BUS, pos, state),
                    RegistryMEBlocks.all().get("blockmeiteminputbus").get())
            .build(null));

    /** @see #ME_ITEM_INPUT_BUS */
    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.me.MEItemOutputBus>>
            ME_ITEM_OUTPUT_BUS = TYPES.register("blockmeitemoutputbus", () -> BlockEntityType.Builder
            .of((pos, state) -> new hellfirepvp.modularmachinery.common.tiles.me.MEItemOutputBus(
                            RegistryMEBlockEntities.ME_ITEM_OUTPUT_BUS, pos, state),
                    RegistryMEBlocks.all().get("blockmeitemoutputbus").get())
            .build(null));

    /** @see #ME_ITEM_INPUT_BUS */
    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.me.MEFluidInputBus>>
            ME_FLUID_INPUT_BUS = TYPES.register("blockmefluidinputbus", () -> BlockEntityType.Builder
            .of((pos, state) -> new hellfirepvp.modularmachinery.common.tiles.me.MEFluidInputBus(
                            RegistryMEBlockEntities.ME_FLUID_INPUT_BUS, pos, state),
                    RegistryMEBlocks.all().get("blockmefluidinputbus").get())
            .build(null));

    /** @see #ME_ITEM_INPUT_BUS */
    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.me.MEFluidOutputBus>>
            ME_FLUID_OUTPUT_BUS = TYPES.register("blockmefluidoutputbus", () -> BlockEntityType.Builder
            .of((pos, state) -> new hellfirepvp.modularmachinery.common.tiles.me.MEFluidOutputBus(
                            RegistryMEBlockEntities.ME_FLUID_OUTPUT_BUS, pos, state),
                    RegistryMEBlocks.all().get("blockmefluidoutputbus").get())
            .build(null));

    

    private RegistryMEBlockEntities() {
    }

    /**
     * Subscribes this register, and does nothing when AE2 is absent.
     *
     * <p><b>Correct, but not sufficient on its own</b> -- see the class javadoc: a call to this method loads the class before the
     * guard inside it can run, so the caller must guard too.</p>
     */
    public static void register(IEventBus modBus) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            // Nothing to subscribe: the two types reference AE2 classes and cannot be created without it.
            return;
        }
        TYPES.register(modBus);
    }


    




 
    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternProvider>>
            ME_PATTERN_PROVIDER = TYPES.register("blockmepatternprovider", () -> BlockEntityType.Builder
            .of((pos, state) -> new hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternProvider(
                            RegistryMEBlockEntities.ME_PATTERN_PROVIDER, pos, state),
                    RegistryMEBlocks.all().get("blockmepatternprovider").get())
            .build(null));

    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternMirrorImage>>
            ME_PATTERN_MIRROR = TYPES.register("blockmepatternmirrorimage", () -> BlockEntityType.Builder
            .of((pos, state) -> new hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternMirrorImage(
                            RegistryMEBlockEntities.ME_PATTERN_MIRROR, pos, state),
                    RegistryMEBlocks.all().get("blockmepatternmirrorimage").get())
            .build(null));
}

