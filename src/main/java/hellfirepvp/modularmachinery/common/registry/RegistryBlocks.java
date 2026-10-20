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

/**
 * Block registry.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream held bare {@code public static Block} fields populated by
 * {@code RegistryBlocks.initialize()} from the {@code FMLPreInitializationEvent} handler. Both the
 * lifecycle event and static-field registration are gone: blocks are lazily-resolved
 * {@link RegistryObject}s, and the whole {@link DeferredRegister} is attached to the mod event bus in
 * the {@code @Mod} constructor. Forgetting that attachment registers nothing, <b>silently</b>.
 *
 * <p>Upstream's field names are preserved verbatim ({@code blockController}, {@code itemInputBus}, …)
 * so the remaining port work proceeds against familiar names, even though 1.20.1 convention would use
 * SCREAMING_SNAKE constants.
 *
 * <p>Registry ids (<i>not</i> field names) are what matter for compatibility: they are the ids the
 * shipped blockstates, models and lang entries are keyed on, and what addon structure JSONs reference.
 * They are taken from the upstream asset filenames.
 */
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

    private RegistryBlocks() {
    }

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
