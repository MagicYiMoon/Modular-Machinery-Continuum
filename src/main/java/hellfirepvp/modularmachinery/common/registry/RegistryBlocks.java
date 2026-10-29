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
import hellfirepvp.modularmachinery.common.block.BlockCasing;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.block.BlockEnergyInputHatch;
import hellfirepvp.modularmachinery.common.block.BlockEnergyOutputHatch;
import hellfirepvp.modularmachinery.common.block.BlockFactoryController;
import hellfirepvp.modularmachinery.common.block.BlockFluidInputHatch;
import hellfirepvp.modularmachinery.common.block.BlockFluidOutputHatch;
import hellfirepvp.modularmachinery.common.block.BlockInputBus;
import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import hellfirepvp.modularmachinery.common.block.BlockOutputBus;
import hellfirepvp.modularmachinery.common.block.BlockParallelController;
import hellfirepvp.modularmachinery.common.block.BlockSmartInterface;
import hellfirepvp.modularmachinery.common.block.BlockUpgradeBus;
import hellfirepvp.modularmachinery.common.block.CasingType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

















 
public final class RegistryBlocks {

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Modularmachinery.MODID);

    /**
     * Machine-component defaults: iron-like, requires a correct tool, blast resistant.
     *
     * <p>1.12.2 used {@code Material.IRON} plus an explicit {@code setHardness}/{@code setResistance}
     * pair; on 1.20.1 material is expressed through {@link BlockBehaviour.Properties}.
     */
    public static BlockBehaviour.Properties componentProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    // ------------------------------------------------------------------
    // Controllers
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockController> blockController =
            BLOCKS.register("blockcontroller", () -> new BlockController(componentProperties()));

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockCasing> blockCasing =
            BLOCKS.register("blockcasing", () -> new BlockCasing(componentProperties()));

    // ------------------------------------------------------------------
    // Item buses
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockInputBus> itemInputBus =
            BLOCKS.register("blockinputbus", () -> new BlockInputBus(componentProperties()));

    public static final RegistryObject<BlockOutputBus> itemOutputBus =
            BLOCKS.register("blockoutputbus", () -> new BlockOutputBus(componentProperties()));

    // ------------------------------------------------------------------
    // Fluid hatches
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockFluidInputHatch> fluidInputHatch =
            BLOCKS.register("blockfluidinputhatch", () -> new BlockFluidInputHatch(componentProperties()));

    public static final RegistryObject<BlockFluidOutputHatch> fluidOutputHatch =
            BLOCKS.register("blockfluidoutputhatch", () -> new BlockFluidOutputHatch(componentProperties()));

    // ------------------------------------------------------------------
    // Energy hatches
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockEnergyInputHatch> energyInputHatch =
            BLOCKS.register("blockenergyinputhatch", () -> new BlockEnergyInputHatch(componentProperties()));

    public static final RegistryObject<BlockEnergyOutputHatch> energyOutputHatch =
            BLOCKS.register("blockenergyoutputhatch", () -> new BlockEnergyOutputHatch(componentProperties()));

    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockEnergyOutputPort> energyOutputPort =
            BLOCKS.register("blockenergyoutputport", () -> new hellfirepvp.modularmachinery.common.block.BlockEnergyOutputPort(componentProperties()));

    // ------------------------------------------------------------------
    // Controllers and machine logic
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockFactoryController> blockFactoryController =
            BLOCKS.register("blockfactorycontroller", () -> new BlockFactoryController(componentProperties()));

    public static final RegistryObject<BlockSmartInterface> smartInterface =
            BLOCKS.register("blocksmartinterface", () -> new BlockSmartInterface(componentProperties()));

    public static final RegistryObject<BlockParallelController> parallelController =
            BLOCKS.register("blockparallelcontroller", () -> new BlockParallelController(componentProperties()));

    public static final RegistryObject<BlockUpgradeBus> upgradeBus =
            BLOCKS.register("blockupgradebus", () -> new BlockUpgradeBus(componentProperties()));

    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockEnvironmentDetector> environmentDetector =
            BLOCKS.register("blockenvironmentdetector", () -> new hellfirepvp.modularmachinery.common.block.BlockEnvironmentDetector(componentProperties()));

    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockSparkManaProvider> sparkManaInput =
            BLOCKS.register("blocksparkmanainput", () -> new hellfirepvp.modularmachinery.common.block.BlockSparkManaProvider(componentProperties(), true));
    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockSparkManaProvider> sparkManaOutput =
            BLOCKS.register("blocksparkmanaoutput", () -> new hellfirepvp.modularmachinery.common.block.BlockSparkManaProvider(componentProperties(), false));

    private RegistryBlocks() {
    }

    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockEmberHatch> emberInput =
            BLOCKS.register("blockemberinputhatch", () -> new hellfirepvp.modularmachinery.common.block.BlockEmberHatch(componentProperties()));

    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockPressureHatch> pressureInput =
            BLOCKS.register("blockpressureinputhatch", () -> new hellfirepvp.modularmachinery.common.block.BlockPressureHatch(componentProperties(), true));
    public static final RegistryObject<hellfirepvp.modularmachinery.common.block.BlockPressureHatch> pressureOutput =
            BLOCKS.register("blockpressureoutputhatch", () -> new hellfirepvp.modularmachinery.common.block.BlockPressureHatch(componentProperties(), false));

    /**
     * Registers an extra machine component.
     *
     * <p>Upstream exposed {@code registerStatedMachineComponent} so addons could contribute components
     * during pre-init. Deferred registration cannot be driven by arbitrary external calls on 1.20.1 —
     * every name must be known before {@code RegisterEvent} fires — so extra components are declared
     * through this helper from mod-side code instead.
     */
    public static <T extends BlockMachineComponent> RegistryObject<T> registerComponent(
            String name, java.util.function.Supplier<T> factory) {
        return BLOCKS.register(name, factory);
    }

    /** @return every casing variant, for structure JSON authoring and the creative tab */
    public static CasingType[] casingTypes() {
        return CasingType.values();
    }
}
