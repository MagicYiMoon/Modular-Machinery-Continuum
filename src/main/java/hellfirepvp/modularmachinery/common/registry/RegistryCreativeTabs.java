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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's creative tab.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream used a {@code CreativeTabs} subclass and put every block and
 * item into it from {@code getSubItems}. On 1.20.1 {@code CreativeModeTab} is a registered object
 * built through {@link CreativeModeTab#builder()} and populated by a
 * {@code displayItems} generator, and <b>it is a vanilla registry, not a Forge one</b> — so it is
 * created with {@link Registries#CREATIVE_MODE_TAB} (singular) rather than a
 * {@code ForgeRegistries} constant. Forge has no {@code ForgeRegistries.CREATIVE_MODE_TABS};
 * assuming it does is a compile error that reads like a missing Forge version.
 */
public final class RegistryCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Modularmachinery.MODID);

    /**
     * The tab's plain item entries, declared in one place.
     *
     * <p><b>Why a list and not thirteen calls.</b> §182 added an item to this tab and §184/§185 tried three
     * times to check that every registered item is listed -- building the tab contents (seven compile errors),
     * and reading {@code getDisplayItems()} (41 false failures, because a creative tab's contents are built on
     * the CLIENT and a dedicated server never populates them).
     *
     * <p><b>§185.3's conclusion was that the item-side question has to be asked about the port's own intent
     * rather than the game's client-side state.</b> This list is that intent, made declarative: the tab
     * iterates it and the self-test checks against it. <b>Its weakness is statable and worth stating — it
     * cannot prove the tab builder used the list.</b> What it can prove is that every registered item is
     * either declared here, or comes from one of the three generated sets (the {@code modularmagic} providers,
     * the ME item forms, the per-machine blueprints), and that is a claim about the port rather than about a
     * client that is not running.
     *
     * <p>{@code itemMachineProjector} is §182's, and upstream shipped no crafting recipe for it — checked
     * against the jar's seventeen recipes, none of which is this item — so the tab is where it belongs.
     */
    public static final java.util.List<? extends java.util.function.Supplier<? extends net.minecraft.world.item.Item>>
            // The wildcard is required and the compiler established it twice: `RegistryObject<BlockItem>` is
            // not a `Supplier<Item>`, because Java generics are invariant. `Supplier<? extends Item>` accepts
            // both the block items and the plain ones, which is what this list holds.
            EXPLICIT_ITEMS = java.util.List.of(
                    RegistryItems.itemModularium,
                    RegistryItems.itemMachineProjector,
                    RegistryItems.blockController,
                    RegistryItems.blockCasing,
                    RegistryItems.itemInputBus,
                    RegistryItems.itemOutputBus,
                    RegistryItems.fluidInputHatch,
                    RegistryItems.fluidOutputHatch,
                    RegistryItems.energyInputHatch,
                    RegistryItems.energyOutputHatch,
                    RegistryItems.blockFactoryController,
                    RegistryItems.smartInterface,
                    RegistryItems.parallelController,
                    RegistryItems.upgradeBus,
                    RegistryItems.CONSTRUCT_TOOL,
                    // THE FOUR UPGRADE ITEMS (audit row 37). They are in this list because the suite asks a real
                    // question about it -- "every registered item is declared reachable" (MachineSelfTest) -- and an
                    // item a player cannot obtain is an item that cannot be put in an upgrade bus slot, which would
                    // leave the whole upgrade chain unreachable in survival. A tab entry is not a crafting recipe,
                    // and upstream shipped none for its upgrades either: its upgrades were granted by scripts.
                    hellfirepvp.modularmachinery.common.upgrade.UpgradeItems.speedItem(),
                    hellfirepvp.modularmachinery.common.upgrade.UpgradeItems.energyItem(),
                    hellfirepvp.modularmachinery.common.upgrade.UpgradeItems.outputItem(),
                    hellfirepvp.modularmachinery.common.upgrade.UpgradeItems.timeItem());

    /**
     * THE ONE LIST OF FAMILIES THE TAB SWEEPS -- read by the CREATIVE TAB and by the SELF-TEST.
     *
     * <p><b>Why the families are a list and not nine arguments.</b> §719 extracted the SWEEP into
     * {@link #variantStacks} so the tab and the check could not disagree about how a tiered stack is built.
     * WHICH FAMILIES GET SWEPT was still written at the call site, and that is where the defect of §731 lived:
     * the parallel controller and the upgrade bus were never handed to the sweep at all, so the tab offered one
     * bare entry each and every placed block was the default tier -- while their five tier names sat in both
     * language files and their five blockstates sat in the jar. A check that named its own families would have
     * passed with that defect present, which is why the list is here and both callers read THIS one.</p>
     *
     * <p>The wildcard is required and the compiler established it twice: {@code RegistryObject<BlockItem>} is
     * not a {@code Supplier<Item>}, because Java generics are invariant. {@code Supplier<? extends Item>} accepts
     * both the block items and the plain ones.</p>
     */
    public static final java.util.List<? extends java.util.function.Supplier<? extends net.minecraft.world.item.Item>>
            VARIANT_FAMILIES = java.util.List.of(
                    RegistryItems.itemInputBus,
                    RegistryItems.itemOutputBus,
                    RegistryItems.fluidInputHatch,
                    RegistryItems.fluidOutputHatch,
                    RegistryItems.energyInputHatch,
                    RegistryItems.energyOutputHatch,
                    RegistryItems.blockCasing,
                    // §731: the two `type=` families were missing from this list, and that omission WAS the report
                    // ("we do not have different tiers of the upgrade bus and the parallel controller").
                    RegistryItems.parallelController,
                    RegistryItems.upgradeBus);

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.modularmachinery"))
                    .icon(() -> new ItemStack(RegistryItems.itemModularium.get()))
                    .displayItems((parameters, output) -> {
                        EXPLICIT_ITEMS.forEach(entry -> output.accept(entry.get()));
                        DedicatedControllers.entries().forEach(entry -> output.accept(entry.item().get()));
                        // modularmagic providers: registered under another namespace but shown in
                        // this mod's tab, since they ship in this jar.
                        RegistryModularMagicItems.PROVIDERS.forEach(item -> output.accept(item.get()));
        // The eight ME blocks. They were registered with no BlockItem until §169, so they could not be placed
        // and no structure naming one could ever be assembled. The blocks still do nothing -- that is the
        // intended stub -- but they can now be obtained and put in the world, which is what makes a stub
        // useful rather than merely present.
        RegistryMEBlocks.itemForms().values().forEach(item -> output.accept(item.get()));
                // One blueprint per loaded machine, matching upstream's fillItemCategory. These are
                // generated rather than registered because every blueprint is the same ITEM differing only
                // in the machine key it carries in NBT.
                for (ItemStack blueprint : RegistryItems.blueprintVariants()) {
                    output.accept(blueprint);
                }
                        // ONE STACK PER TIER (PLAN section 270), FOR EVERY FAMILY IN THE ONE LIST ABOVE.
                        //
                        // The hatch and bus sizes are BLOCKSTATE VARIANTS of a single block each, and one
                        // BlockItem per block means the creative tab can only ever offer the DEFAULT tier.
                        // The client reported exactly that: "没有看到更高级的舱室，只有基础舱室".
                        //
                        // No new item class is needed. Vanilla's BlockItem reads a `BlockStateTag` compound
                        // off the stack and places THAT state -- confirmed by reading its bytecode, which
                        // carries the literal string "BlockStateTag". So a stack carrying the tag IS the
                        // higher tier, and placing it yields the right block.
                        acceptVariants(output, VARIANT_FAMILIES);

                    })
                    .build());
    /**
     * Writes one stack per variant of each family in {@code items} into {@code output}, each carrying the
     * {@code BlockStateTag} that makes it place as that variant.
     */
    private static void acceptVariants(net.minecraft.world.item.CreativeModeTab.Output output,
                                       java.util.List<? extends java.util.function.Supplier<? extends net.minecraft.world.item.Item>> items) {
        for (ItemStack stack : sweep(items)) {
            output.accept(stack);
        }
    }

    /**
     * THE STACKS THE TAB SHOWS: the sweep applied to {@link #VARIANT_FAMILIES}, the tab's own family list.
     *
     * <p><b>Why this exists next to {@link #variantStacks}.</b> That method shares the SWEEP and takes the
     * families as arguments, so a check calling it names its own families -- and §731's defect was precisely a
     * family that no caller named. This method has no argument to get wrong: it is the tab's own list and
     * nothing else, which is what makes a check against it able to FAIL when a family is dropped from the tab.
     * Both methods run the same {@code sweep}, so they cannot disagree about how a tagged stack is built.</p>
     */
    public static java.util.List<ItemStack> tabVariantStacks() {
        return sweep(VARIANT_FAMILIES);
    }

    /**
     * THE ONE RULE THAT SAYS WHICH STACKS CARRY WHICH TIER -- read by the CREATIVE TAB and by the SELF-TEST.
     *
     * <p><b>Why this is a list and not a write.</b> It was written straight into {@code output} until the fluid
     * hatch report ("all fluid input and output hatches are 1000 mB", PLAN 719), which asked whether the eight
     * tiers are REACHABLE. A check answering that has to compare its expectation against the very stacks the tab
     * shows. Asserting against a SEPARATE re-implementation would have passed even if the tab's own sweep
     * regressed -- the shape PLAN 629 measured, where the item and the TOP provider each re-derived the variant
     * and drifted apart. TWO CALLERS READING ONE METHOD CANNOT DISAGREE; that is the whole reason this exists.
     *
     * <p><b>Why the block is reached through the ITEM.</b> {@link net.minecraft.world.item.BlockItem#getBlock()}
     * means this needs no reference to {@code RegistryBlocks}' field names, so the sweep can be added or trimmed
     * by editing one list. A name that does not match would be a compile error either way, but this way there is
     * only one name per entry instead of two.
     *
     * <p><b>Why the property values are strings.</b> A blockstate property serialises by NAME, not by ordinal or
     * by the enum constant's Java name -- {@code size=big}, not {@code size=2}. Writing the ordinal would place
     * the wrong tier or fail to parse, and it would look like a data problem rather than a code one.
     */
    public static java.util.List<ItemStack> variantStacks(
            java.util.function.Supplier<? extends net.minecraft.world.item.Item>... items) {
        return sweep(java.util.Arrays.asList(items));
    }

    /** The sweep itself, over an already-built list of families. See {@link #variantStacks}. */
    private static java.util.List<ItemStack> sweep(
            java.util.List<? extends java.util.function.Supplier<? extends net.minecraft.world.item.Item>> items) {
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        for (java.util.function.Supplier<? extends net.minecraft.world.item.Item> supplier : items) {
            net.minecraft.world.item.Item item = supplier.get();
            if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) {
                continue;   // not a block; nothing to sweep
            }
            net.minecraft.world.level.block.Block block = blockItem.getBlock();
            for (net.minecraft.world.level.block.state.BlockState state :
                    block.getStateDefinition().getPossibleStates()) {
                ItemStack stack = new ItemStack(item);
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                for (net.minecraft.world.level.block.state.properties.Property<?> property :
                        state.getProperties()) {
                    tag.putString(property.getName(), tierNameOf(state, property));
                }
                if (!tag.isEmpty()) {
                    stack.getOrCreateTag().put("BlockStateTag", tag);
                }
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /** @return the serialised name of {@code property} in {@code state}, as the blockstate codec writes it */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String tierNameOf(net.minecraft.world.level.block.state.BlockState state,
                                     net.minecraft.world.level.block.state.properties.Property property) {
        return property.getName(state.getValue(property));
    }

    private RegistryCreativeTabs() {
    }
}
