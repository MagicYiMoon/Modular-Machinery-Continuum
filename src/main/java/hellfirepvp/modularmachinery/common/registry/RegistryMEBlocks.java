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
import hellfirepvp.modularmachinery.common.block.BlockMEPlaceholder;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;


































 
public final class RegistryMEBlocks {

    private RegistryMEBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Modularmachinery.MODID);

    /**
     * The eight concrete blocks, as named upstream.
     *
     * <p>A {@link LinkedHashMap} rather than a {@code List} so the id is paired with its block at the
     * point of declaration — the two cannot fall out of step, and the order stays stable for logging.
     */
    private static final Map<String, RegistryObject<Block>> REGISTERED = new LinkedHashMap<>();

    /**
     * The item forms of the eight blocks.
     *
     * <p><b>Added in §169, and it is not a detail.</b> These blocks were registered with no {@link BlockItem},
     * so they could not be held, could not appear in the creative tab and <b>could not be placed by a
     * player</b> — only {@code /setblock} reached them. That defeated the purpose this class's own javadoc
     * states: a structure JSON naming {@code modularmachinery:blockmeiteminputbus} would <i>load</i> and the
     * machine could <i>never be assembled</i>, because the block that the structure requires cannot be put in
     * the world.
     *
     * <p><b>"Empty" and "absent" are different</b>, which is the distinction the original scope decision left
     * implicit. An empty hatch can be placed and does nothing — that is the intended stub. An itemless block
     * cannot be placed at all, which is not a quieter stub but a missing one.
     */
    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Modularmachinery.MODID);

    /** The item forms, by the same names, so the tab can add them. */
    private static final Map<String, RegistryObject<net.minecraft.world.item.Item>> REGISTERED_ITEMS =
            new LinkedHashMap<>();

    private static void register(String name) {
        RegistryObject<Block> block = BLOCKS.register(name,
                () -> new BlockMEPlaceholder(RegistryBlocks.componentProperties(), name));
        REGISTERED.put(name, block);
        // The item is what makes the block obtainable and placeable. See ITEMS above.
        REGISTERED_ITEMS.put(name, ITEMS.register(name,
                () -> new net.minecraft.world.item.BlockItem(block.get(),
                        new net.minecraft.world.item.Item.Properties())));
    }

    









 
    private static void registerReal(String name, java.util.function.Supplier<Block> blockFactory) {
        RegistryObject<Block> block = BLOCKS.register(name, blockFactory);
        REGISTERED.put(name, block);
        REGISTERED_ITEMS.put(name, ITEMS.register(name,
                () -> new net.minecraft.world.item.BlockItem(block.get(),
                        new net.minecraft.world.item.Item.Properties())));
    }

    /** @return the item forms by upstream name, in declaration order */
    public static Map<String, RegistryObject<net.minecraft.world.item.Item>> itemForms() {
        return java.util.Collections.unmodifiableMap(REGISTERED_ITEMS);
    }



     
    //
    // A first attempt at this made BlockMEItemBus construct the tile INLINE, and the standalone configuration rejected it:
    //
    //     NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost
    //     Caused by: NullPointerException: Registry Object not present: modularmachinery:blockmeiteminputbus
    //
    // The cause is not that "the method body runs later" -- a class file's method bodies NAME the types they use, and the JVM's
    // verifier resolves those names when the class is LINKED. So BlockMEItemBus must not name an AE2-dependent type anywhere,
    // and it now reaches them only through MEBusTiles, a separate class nothing loads until newBlockEntity runs.
    static {
        registerReal("blockmeiteminputbus",
                () -> new hellfirepvp.modularmachinery.common.block.BlockMEItemBus(
                        RegistryBlocks.componentProperties(),
                        hellfirepvp.modularmachinery.common.block.BlockMEItemBus.Direction.INPUT));
        registerReal("blockmeitemoutputbus",
                () -> new hellfirepvp.modularmachinery.common.block.BlockMEItemBus(
                        RegistryBlocks.componentProperties(),
                        hellfirepvp.modularmachinery.common.block.BlockMEItemBus.Direction.OUTPUT));

         
        //
        // An earlier version registered these two names against BlockMEItemBus with a Kind.FLUID flag, justified by rule B's
        // "reuse". Rule A0 was added afterwards and subordinates B: upstream has BlockMEFluidBus + two concrete subclasses, and no
        // evidence was produced that they cannot work on 1.20.1. So they are written out, and BlockMEItemBus goes back to being
        // the item-only class upstream has.
        registerReal("blockmefluidinputbus",
                () -> new hellfirepvp.modularmachinery.common.block.BlockMEFluidInputBus(
                        RegistryBlocks.componentProperties()));
        registerReal("blockmefluidoutputbus",
                () -> new hellfirepvp.modularmachinery.common.block.BlockMEFluidOutputBus(
                        RegistryBlocks.componentProperties()));

        // Keep optional tile creation behind MEBusTiles; the block identity is unconditional.
        registerReal("blockmepatternprovider",
                () -> new hellfirepvp.modularmachinery.common.block.BlockMEPatternProvider(
                        RegistryBlocks.componentProperties()));

        // AE2 chemical storage is supplied by Applied Mekanistics, guarded by BlockMEGasBus.
        registerReal("blockmegasinputbus", () -> new hellfirepvp.modularmachinery.common.block.BlockMEGasBus(RegistryBlocks.componentProperties(), true));
        registerReal("blockmegasoutputbus", () -> new hellfirepvp.modularmachinery.common.block.BlockMEGasBus(RegistryBlocks.componentProperties(), false));

        // The mirror has no grid node of its own; it forwards the machine-visible
        // storage to a provider bound through an AE2 memory card.
        registerReal("blockmepatternmirrorimage",
                () -> new hellfirepvp.modularmachinery.common.block.BlockMEPatternMirrorImage(
                        RegistryBlocks.componentProperties()));
    }

    /**
     * @return the registered ME blocks by upstream name, in declaration order
     *
     * <p>Wrapped in an unmodifiable {@link LinkedHashMap} rather than {@code Map.copyOf}, which returns an
     * <b>unordered</b> immutable map. The first version of this method used {@code Map.copyOf} while the
     * comment claimed declaration order, and the self-test's log line printed the names shuffled — which
     * is how the mismatch was caught. Order is worth keeping: a log listing the blocks in a stable order
     * is comparable between runs, and a shuffled one is not.
     */
    public static Map<String, RegistryObject<Block>> all() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(REGISTERED));
    }

    /** @return how many ME blocks were registered */
    public static int count() {
        return REGISTERED.size();
    }
}
