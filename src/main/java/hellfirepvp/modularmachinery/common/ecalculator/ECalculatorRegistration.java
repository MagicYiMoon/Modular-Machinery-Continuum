/* SPDX-License-Identifier: GPL-3.0-only
 * EC identities adapted from NovaEngineering Core, KasumiNova and contributors.
 */
package hellfirepvp.modularmachinery.common.ecalculator;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.*;

/** All common registry holders remain loadable without AE2. */
public final class ECalculatorRegistration {
    private ECalculatorRegistration() { }
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Modularmachinery.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Modularmachinery.MODID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Modularmachinery.MODID);
    public static final Map<Integer, RegistryObject<BlockECalculatorController>> CONTROLLERS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<BlockECalculatorPart>> PARTS = new LinkedHashMap<>();
    public static final Map<ECalculatorSpecs.Memory, RegistryObject<ItemECalculatorMemory>> MEMORIES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<? extends Item>> ITEM_FORMS = new LinkedHashMap<>();
    public static final RegistryObject<Block> CASING = BLOCKS.register("ecalculator_casing", () -> new Block(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion()));
    public static final RegistryObject<BlockECalculatorPart> DRIVE = part("ecalculator_cell_drive", ECalculatorSpecs.Kind.DRIVE, 0);
    public static final RegistryObject<BlockECalculatorPart> CHANNEL = part("ecalculator_me_channel", ECalculatorSpecs.Kind.CHANNEL, 0);
    public static final RegistryObject<BlockECalculatorPart> TRANSMITTER = part("ecalculator_transmitter_bus", ECalculatorSpecs.Kind.TRANSMITTER, 0);
    static {
        ITEM_FORMS.put("ecalculator_casing", ITEMS.register("ecalculator_casing", () -> new BlockItem(CASING.get(), new Item.Properties())));
        for (int tier : new int[]{4, 6, 9}) {
            String id = machineName(tier);
            var controller = BLOCKS.register(id, () -> new BlockECalculatorController(tier));
            CONTROLLERS.put(tier, controller);
            ITEM_FORMS.put(id, ITEMS.register(id, () -> new BlockItem(controller.get(), new Item.Properties())));
            part("ecalculator_thread_core_l" + tier, ECalculatorSpecs.Kind.THREAD, tier);
            part("ecalculator_thread_core_hyper_l" + tier, ECalculatorSpecs.Kind.HYPER_THREAD, tier);
            part("ecalculator_parallel_proc_l" + tier, ECalculatorSpecs.Kind.PARALLEL, tier);
            part("ecalculator_tail_l" + tier, ECalculatorSpecs.Kind.TAIL, tier);
        }
        for (var spec : ECalculatorSpecs.Memory.values()) {
            String id = "ecalculator_cell_" + spec.millionBytes() + "m";
            var memory = ITEMS.register(id, () -> new ItemECalculatorMemory(spec));
            MEMORIES.put(spec, memory); ITEM_FORMS.put(id, memory);
        }
    }
    private static RegistryObject<BlockECalculatorPart> part(String id, ECalculatorSpecs.Kind kind, int tier) {
        var block = BLOCKS.register(id, () -> BlockECalculatorPart.create(new ECalculatorSpecs.Part(kind, tier)));
        PARTS.put(id, block);
        boolean core = kind == ECalculatorSpecs.Kind.THREAD || kind == ECalculatorSpecs.Kind.HYPER_THREAD;
        ITEM_FORMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), core ? new Item.Properties().stacksTo(1) : new Item.Properties())));
        return block;
    }
    public static final RegistryObject<BlockEntityType<TileECalculatorController>> CONTROLLER_TILE = TILES.register("ecalculator_controller", () ->
            BlockEntityType.Builder.of(TileECalculatorController::new, CONTROLLERS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<TileECalculatorPart>> PART_TILE = TILES.register("ecalculator_part", () ->
            BlockEntityType.Builder.of(ECalculatorRegistration::createPart, PARTS.values().stream().filter(p -> p != DRIVE).map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<TileECalculatorDrive>> DRIVE_TILE = TILES.register("ecalculator_cell_drive", () ->
            BlockEntityType.Builder.of(TileECalculatorDrive::new, DRIVE.get()).build(null));
    public static String machineName(int tier) { return "extendable_calculator_subsystem_l" + tier; }
    public static TileECalculatorPart createPart(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (((BlockECalculatorPart)state.getBlock()).specification().kind() == ECalculatorSpecs.Kind.CHANNEL)
            return net.minecraftforge.fml.ModList.get().isLoaded("ae2")
                    ? hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorFactory.channel(pos, state)
                    : new TileECalculatorMEChannel(pos, state);
        return new TileECalculatorPart(pos, state);
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); }
}
