/* SPDX-License-Identifier: GPL-3.0-only
 * ES block identities adapted from NovaEngineering Core, KasumiNova and contributors.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry holders and tile hierarchies intentionally contain no AE2 types. */
public final class EStorageRegistration {
    private EStorageRegistration() { }
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Modularmachinery.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Modularmachinery.MODID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Modularmachinery.MODID);
    public static final Map<Integer, RegistryObject<BlockEStorageController>> CONTROLLERS = new LinkedHashMap<>();
    public static final Map<EStorageCellSpec, RegistryObject<ItemEStorageCell>> CELLS = new LinkedHashMap<>();
    public static final Map<Integer, RegistryObject<BlockEStorageEnergyCell>> ENERGY_CELLS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<? extends Item>> ITEM_FORMS = new LinkedHashMap<>();
    public static final RegistryObject<Block> CASING = BLOCKS.register("estorage_casing", () -> new Block(RegistryBlocks.componentProperties().strength(20.0F, 2000.0F)));
    public static final RegistryObject<BlockEStorageDrive> DRIVE = BLOCKS.register("estorage_cell_drive", BlockEStorageDrive::new);
    public static final RegistryObject<BlockEStorageMEChannel> CHANNEL = BLOCKS.register("estorage_me_channel", BlockEStorageMEChannel::new);
    static {
        ITEM_FORMS.put("estorage_casing", ITEMS.register("estorage_casing", () -> new BlockItem(CASING.get(), new Item.Properties())));
        ITEM_FORMS.put("estorage_cell_drive", ITEMS.register("estorage_cell_drive", () -> new BlockItem(DRIVE.get(), new Item.Properties())));
        ITEM_FORMS.put("estorage_me_channel", ITEMS.register("estorage_me_channel", () -> new BlockItem(CHANNEL.get(), new Item.Properties())));
        for (int tier : new int[]{4, 6, 9}) {
            String energyId = "estorage_energy_cell_l" + tier;
            double capacity = tier == 4 ? 10_000_000 : tier == 6 ? 100_000_000 : 1_000_000_000;
            var energyBlock = BLOCKS.register(energyId, () -> new BlockEStorageEnergyCell(capacity));
            ENERGY_CELLS.put(tier, energyBlock);
            ITEM_FORMS.put(energyId, ITEMS.register(energyId, () -> new BlockItem(energyBlock.get(), new Item.Properties().stacksTo(1))));
            String id = machineName(tier);
            var block = BLOCKS.register(id, () -> new BlockEStorageController(tier));
            CONTROLLERS.put(tier, block);
            ITEM_FORMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
        for (var kind : EStorageCellSpec.Kind.values()) for (var tier : EStorageCellSpec.Tier.values()) {
            var spec = new EStorageCellSpec(kind, tier);
            String id = "estorage_cell_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + tier.millionBytes() + "m";
            var cell = ITEMS.register(id, () -> spec.backendAvailable()
                    ? hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellFactory.create(spec)
                    : new ItemEStorageCell(spec));
            CELLS.put(spec, cell); ITEM_FORMS.put(id, cell);
        }
    }
    public static final RegistryObject<BlockEntityType<TileEStorageController>> CONTROLLER_TILE = TILES.register("estorage_controller", () ->
            BlockEntityType.Builder.of(TileEStorageController::new, CONTROLLERS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<TileEStorageDrive>> DRIVE_TILE = TILES.register("estorage_cell_drive", () ->
            BlockEntityType.Builder.of(TileEStorageDrive::new, DRIVE.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileEStorageMEChannel>> CHANNEL_TILE = TILES.register("estorage_me_channel", () ->
            BlockEntityType.Builder.<TileEStorageMEChannel>of((pos, state) ->
                    (TileEStorageMEChannel) CHANNEL.get().newBlockEntity(pos, state), CHANNEL.get()).build(null));
    public static final RegistryObject<BlockEntityType<TileEStorageEnergyCell>> ENERGY_TILE = TILES.register("estorage_energy_cell", () ->
            BlockEntityType.Builder.of(TileEStorageEnergyCell::new, ENERGY_CELLS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static String machineName(int tier) { return "extendable_digital_storage_subsystem_l" + tier; }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); }
}
