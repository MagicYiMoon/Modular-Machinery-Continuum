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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Item registry.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream registered items in {@code RegistryItems.initialize()} and built
 * {@code ItemBlock}s by hand, calling {@code setRegistryName} and {@code setUnlocalizedName} on each.
 * On 1.20.1 the registry name comes from the {@link DeferredRegister} key, the display name comes from
 * the language file, and a block's item form is an ordinary {@link BlockItem} — there is no separate
 * {@code ItemBlock} class.
 *
 * <p>1.12.2 also inferred block→item models, so most {@code ItemBlock}s needed no model of their own.
 * 1.20.1 does not: every block item needs a {@code models/item/<block>.json} whose {@code parent}
 * points at the block model.
 */
public final class RegistryItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Modularmachinery.MODID);

    /** The mod's signature crafting material. */
    public static final RegistryObject<Item> itemModularium =
            ITEMS.register("itemmodularium", () -> new Item(new Item.Properties()));


    /**
     * §181 unit 1: the machine projector.
     *
     * <p>Declared as a field rather than registered inside the static block like the rest of this class --
     * putting a field declaration inside `static {}` is what the first attempt did, and the compiler rejected
     * it. Registering unconditionally is deliberate: §169's lesson is that nothing asserts an item's existence
     * unless a check does, and the item is harmless on a server because its client half goes through
     * {@code DistExecutor}.
     */
    public static final RegistryObject<Item> itemMachineProjector =
            ITEMS.register("machine_projector",
                    hellfirepvp.modularmachinery.common.item.ItemMachineProjector::new);
    /** The blueprint item. One per machine is offered in the creative tab by {@link #blueprintVariants()}. */
    public static final RegistryObject<Item> BLUEPRINT =
            // "itemblueprint", not "blueprint". The asset conversion produced models/item/itemblueprint.json
            // from upstream's own model name, which is derived from ITS registry name -- so registering as
            // "blueprint" would leave the item with no model and render it as the missing-model cube.
            // Matching upstream also keeps the item's identity stable across the port.
            ITEMS.register("itemblueprint", hellfirepvp.modularmachinery.common.item.ItemBlueprint::new);

    /**
     * The forming tool: right-click a controller to assemble the machine around it.
     *
     * <p>Its model and texture already shipped -- {@code models/item/itemconstructtool.json} pointing at
     * {@code modularmachinery:item/wrench} -- so only the item class and this declaration were missing.
     *
     * <p>A FIELD, not a statement in an initialiser. The first attempt inserted a bare
     * {@code ITEMS.register(...)} call after {@code BLUEPRINT}'s declaration, which is outside any method body,
     * and the compiler rejected it with "expected identifier". The class registers by declaring fields; this
     * follows that.
     */
    public static final RegistryObject<Item> CONSTRUCT_TOOL =
            ITEMS.register("itemconstructtool",
                    hellfirepvp.modularmachinery.common.item.ItemConstructTool::new);

    /**
     * Builds one blueprint stack per loaded machine, for the creative tab.
     *
     * <p>Upstream fills these from the creative tab's {@code fillItemCategory}, which is why a fresh
     * install shows a blueprint per machine rather than a single blank one. Reading the loader at call time
     * rather than caching matters: machine definitions are reloaded on every datapack and CraftTweaker run,
     * so a cached list would offer machines that no longer exist.
     */
    public static java.util.List<ItemStack> blueprintVariants() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (var entry : hellfirepvp.modularmachinery.common.machine.MachineReloadListener
                .getLoader().getMachines().entrySet()) {
            ItemStack stack = new ItemStack(BLUEPRINT.get());
            hellfirepvp.modularmachinery.common.item.ItemBlueprint.setAssociatedMachine(stack, entry.getKey());
            out.add(stack);
        }
        return out;
    }

    // --- Block items: one per registered block, in registration order. ---

    public static final RegistryObject<BlockItem> blockController =
            ITEMS.register("blockcontroller", () -> new BlockItem(
                    RegistryBlocks.blockController.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> blockCasing =
            ITEMS.register("blockcasing", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.blockCasing.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> itemInputBus =
            ITEMS.register("blockinputbus", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.itemInputBus.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> itemOutputBus =
            ITEMS.register("blockoutputbus", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.itemOutputBus.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> fluidInputHatch =
            ITEMS.register("blockfluidinputhatch", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.fluidInputHatch.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> fluidOutputHatch =
            ITEMS.register("blockfluidoutputhatch", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.fluidOutputHatch.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> energyInputHatch =
            ITEMS.register("blockenergyinputhatch", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.energyInputHatch.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> energyOutputHatch =
            ITEMS.register("blockenergyoutputhatch", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.energyOutputHatch.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> blockFactoryController =
            ITEMS.register("blockfactorycontroller", () -> new BlockItem(
                    RegistryBlocks.blockFactoryController.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> smartInterface =
            ITEMS.register("blocksmartinterface", () -> new BlockItem(
                    RegistryBlocks.smartInterface.get(), new Item.Properties()));

    /**
     * The parallel controller and the upgrade bus: the last two TIERED block items, and they were bare
     * {@link BlockItem}s (PLAN 731).
     *
     * <p>Both blocks carry a five-constant {@code type=} variant ({@code ParallelControllerData} and
     * {@code UpgradeBusData}), both have five language keys each, five blockstates, five block models and five
     * item models -- and neither item could NAME its tier, because the naming reads a stack's
     * {@code BlockStateTag} and only {@link hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent}
     * does that. Every sibling family ({@link #blockCasing}, the four hatches, the two buses) already used it;
     * these two did not, and a bare {@code BlockItem} cannot be a tier at all -- which is why the creative tab
     * could only ever offer the default one and the user reported the tiers as missing.
     *
     * <p>Registered through the same {@code () -> new …} supplier as the rest of this class: the Item constructor
     * touches {@code BuiltInRegistries.ITEM}, so constructing one after the registry is frozen throws, and the
     * deferred supplier is what keeps construction at the registry event rather than at class initialisation.
     */
    public static final RegistryObject<BlockItem> parallelController =
            ITEMS.register("blockparallelcontroller", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.parallelController.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> upgradeBus =
            ITEMS.register("blockupgradebus", () -> new hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent(
                    RegistryBlocks.upgradeBus.get(), new Item.Properties()));

    // ---------------------------------------------------------------------------------------------
    // THE FOUR UPGRADE ITEMS (audit row 37).
    //
    // Registered by `UpgradeItems.register(ITEMS)`, which is a single call rather than four fields here,
    // because each item and its `RegistryMachineUpgrade` entry have to be created TOGETHER: an item whose
    // upgrade was never registered would be a stack a player can install and that does nothing, which is
    // the defect this unit exists to close. `UpgradeItems` owns both halves so they cannot drift.
    //
    // ORDER IS NOT LOAD-BEARING HERE, unlike the field declarations above: `UpgradeItems.register` is called
    // from this static initialiser, i.e. after `ITEMS` exists and before Forge fires the registry events, so
    // the `RegistryObject`s are valid immediately and the Item objects are built later by the supplied
    // lambdas -- which is the same reason every other item in this class is registered through a supplier.
    // ---------------------------------------------------------------------------------------------
    static {
        hellfirepvp.modularmachinery.common.upgrade.UpgradeItems.register(ITEMS);
    }

    private RegistryItems() {
    }
}
