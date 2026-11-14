/* SPDX-License-Identifier: GPL-3.0-only; NECO EF identities, KasumiNova and contributors. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.*;

public final class EFabricatorRegistration {
    private EFabricatorRegistration() { }
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Modularmachinery.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Modularmachinery.MODID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Modularmachinery.MODID);
    public static final Map<EFabricatorSpecs.Tier, RegistryObject<BlockEFabricatorController>> CONTROLLERS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<BlockEFabricatorPart>> PARTS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<? extends Item>> ITEM_FORMS = new LinkedHashMap<>();
    public static final RegistryObject<Block> CASING = BLOCKS.register("efabricator_casing", () -> new Block(RegistryBlocks.componentProperties().strength(20, 2000).noOcclusion()));
    public static final RegistryObject<BlockEFabricatorVent> VENT = BLOCKS.register("efabricator_vent", BlockEFabricatorVent::new);
    public static final RegistryObject<BlockEFabricatorPart> WORKER = part("efabricator_worker", EFabricatorSpecs.Kind.WORKER, null);
    public static final RegistryObject<BlockEFabricatorPart> PATTERN_BUS = part("efabricator_pattern_bus", EFabricatorSpecs.Kind.PATTERN_BUS, null);
    public static final RegistryObject<BlockEFabricatorPart> CHANNEL = part("efabricator_me_channel", EFabricatorSpecs.Kind.CHANNEL, null);
    static {
        ITEM_FORMS.put("efabricator_casing", ITEMS.register("efabricator_casing", () -> new BlockItem(CASING.get(), new Item.Properties())));
        ITEM_FORMS.put("efabricator_vent", ITEMS.register("efabricator_vent", () -> new BlockItem(VENT.get(), new Item.Properties())));
        for (var tier : EFabricatorSpecs.Tier.values()) {
            String id = machineName(tier); var controller = BLOCKS.register(id, () -> new BlockEFabricatorController(tier));
            CONTROLLERS.put(tier, controller); ITEM_FORMS.put(id, ITEMS.register(id, () -> new BlockItem(controller.get(), new Item.Properties().stacksTo(1))));
            part("efabricator_parallel_proc_l" + tier.level, EFabricatorSpecs.Kind.PARALLEL, tier);
        }
    }
    private static RegistryObject<BlockEFabricatorPart> part(String id, EFabricatorSpecs.Kind kind, EFabricatorSpecs.Tier tier) {
        var block = BLOCKS.register(id, () -> BlockEFabricatorPart.create(new EFabricatorSpecs.Part(kind, tier))); PARTS.put(id, block);
        ITEM_FORMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), kind == EFabricatorSpecs.Kind.WORKER || kind == EFabricatorSpecs.Kind.PATTERN_BUS
                ? new Item.Properties().stacksTo(1) : new Item.Properties()))); return block;
    }
    public static final RegistryObject<BlockEntityType<TileEFabricatorController>> CONTROLLER_TILE = TILES.register("efabricator_controller", () ->
            BlockEntityType.Builder.of(TileEFabricatorController::new, CONTROLLERS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<TileEFabricatorPart>> PART_TILE = TILES.register("efabricator_part", () ->
            BlockEntityType.Builder.of(EFabricatorRegistration::createPart, PARTS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static TileEFabricatorPart createPart(BlockPos pos, BlockState state) {
        return switch (((BlockEFabricatorPart)state.getBlock()).specification().kind()) {
            case WORKER -> new TileEFabricatorWorker(pos, state);
            case PATTERN_BUS -> new TileEFabricatorPatternBus(pos, state);
            case CHANNEL -> net.minecraftforge.fml.ModList.get().isLoaded("ae2")
                    ? hellfirepvp.modularmachinery.integration.ae2.efabricator.EFabricatorFactory.channel(pos, state)
                    : new TileEFabricatorMEChannel(pos, state);
            default -> new TileEFabricatorPart(pos, state);
        };
    }
    public static String machineName(EFabricatorSpecs.Tier tier) { return "extendable_fabricator_subsystem_l" + tier.level; }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); }
}
