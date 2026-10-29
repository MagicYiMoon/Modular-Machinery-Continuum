/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.registry;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyInputHatch;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputHatch;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.tiles.TileFluidInputHatch;
import hellfirepvp.modularmachinery.common.tiles.TileFluidOutputHatch;
import hellfirepvp.modularmachinery.common.tiles.TileItemInputBus;
import hellfirepvp.modularmachinery.common.tiles.TileItemOutputBus;
import hellfirepvp.modularmachinery.common.tiles.TileParallelController;
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block entity type registry.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream registered tile entities imperatively with
 * {@code GameRegistry.registerTileEntity(Class, String)} during pre-init, keying them by class. On
 * 1.20.1 a {@link BlockEntityType} is a registry object binding a set of valid blocks to a factory,
 * and <b>the valid-block set is part of its identity</b> — a type listing no blocks will fail
 * validation, and its block entities will not tick or save. Each block that owns a block entity must
 * appear in exactly one type's {@code of(...)} list.
 *
 * <p><b>Factories take only {@code (pos, state)}.</b> A 1.12.2 tile could read its own block's
 * metadata in its constructor. Here the variant must be recovered from the blockstate, and the type
 * itself is passed as a {@code RegistryObject} supplier so a tile can reference its own registry entry
 * without creating a static-initialisation cycle.
 */
public final class RegistryBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Modularmachinery.MODID);

    public static final RegistryObject<BlockEntityType<TileController>> CONTROLLER =
            BLOCK_ENTITIES.register("blockcontroller", () -> BlockEntityType.Builder
                    .of(TileController::new, DedicatedControllers.validBlocks(false, RegistryBlocks.blockController.get()))
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileItemInputBus>> ITEM_INPUT_BUS =
            BLOCK_ENTITIES.register("blockinputbus", () -> BlockEntityType.Builder
                    .of(TileItemInputBus::new, RegistryBlocks.itemInputBus.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileItemOutputBus>> ITEM_OUTPUT_BUS =
            BLOCK_ENTITIES.register("blockoutputbus", () -> BlockEntityType.Builder
                    .of(TileItemOutputBus::new, RegistryBlocks.itemOutputBus.get())
                    .build(null));

    // THE ME BLOCK ENTITY TYPES ARE NOT REGISTERED HERE, AND THE FIRST ATTEMPT TO DO SO CRASHED THE SERVER.
    //
    // They were written as two more entries in this register, referencing MEItemInputBus/MEItemOutputBus. The build compiled --
    // AE2 is a `compileOnly` dependency -- and the dev server then died during RegisterEvent with:
    //
    //     NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost
    //         at RegistryBlockEntities.lambda$static$4(RegistryBlockEntities.java:93)
    //
    // Because loading THIS class now reaches the ME tiles, and the ME tiles reach AE2. On a server without AE2 that is a hard
    // failure at registry time, not a missing feature.
    //
    // RegistryMEBlocks' own javadoc already said the rule this broke: "Always registered, never gated on AE2. What IS gated is
    // behaviour, not identity" -- and it can register all eight ME BLOCKS unconditionally precisely because BlockMEPlaceholder
    // references no AE2 type at all.
    //
    // So a tile that IS an AE2 machine cannot be reached from an unconditional register. The three ways forward are recorded in
     

    public static final RegistryObject<BlockEntityType<TileFluidInputHatch>> FLUID_INPUT_HATCH =
            BLOCK_ENTITIES.register("blockfluidinputhatch", () -> BlockEntityType.Builder
                    .of(TileFluidInputHatch::new, RegistryBlocks.fluidInputHatch.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileFluidOutputHatch>> FLUID_OUTPUT_HATCH =
            BLOCK_ENTITIES.register("blockfluidoutputhatch", () -> BlockEntityType.Builder
                    .of(TileFluidOutputHatch::new, RegistryBlocks.fluidOutputHatch.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileEnergyInputHatch>> ENERGY_INPUT_HATCH =
            BLOCK_ENTITIES.register("blockenergyinputhatch", () -> BlockEntityType.Builder
                    .of(TileEnergyInputHatch::new, RegistryBlocks.energyInputHatch.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileEnergyOutputHatch>> ENERGY_OUTPUT_HATCH =
            BLOCK_ENTITIES.register("blockenergyoutputhatch", () -> BlockEntityType.Builder
                    .of(TileEnergyOutputHatch::new, RegistryBlocks.energyOutputHatch.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputPort>> ENERGY_OUTPUT_PORT =
            BLOCK_ENTITIES.register("blockenergyoutputport", () -> BlockEntityType.Builder
                    .of(hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputPort::new, RegistryBlocks.energyOutputPort.get()).build(null));

    public static final RegistryObject<BlockEntityType<TileFactoryController>> FACTORY_CONTROLLER =
            BLOCK_ENTITIES.register("blockfactorycontroller", () -> BlockEntityType.Builder
                    .of(TileFactoryController::new, DedicatedControllers.validBlocks(true, RegistryBlocks.blockFactoryController.get()))
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileSmartInterface>> SMART_INTERFACE =
            BLOCK_ENTITIES.register("blocksmartinterface", () -> BlockEntityType.Builder
                    .of(TileSmartInterface::new, RegistryBlocks.smartInterface.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileParallelController>> PARALLEL_CONTROLLER =
            BLOCK_ENTITIES.register("blockparallelcontroller", () -> BlockEntityType.Builder
                    .of(TileParallelController::new, RegistryBlocks.parallelController.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileUpgradeBus>> UPGRADE_BUS =
            BLOCK_ENTITIES.register("blockupgradebus", () -> BlockEntityType.Builder
                    .of(TileUpgradeBus::new, RegistryBlocks.upgradeBus.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<hellfirepvp.modularmachinery.common.tiles.TileEnvironmentDetector>> ENVIRONMENT_DETECTOR =
            BLOCK_ENTITIES.register("blockenvironmentdetector", () -> BlockEntityType.Builder
                    .of(hellfirepvp.modularmachinery.common.tiles.TileEnvironmentDetector::new, RegistryBlocks.environmentDetector.get()).build(null));

    private RegistryBlockEntities() {
    }
}
