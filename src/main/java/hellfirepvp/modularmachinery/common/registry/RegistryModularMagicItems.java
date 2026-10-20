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

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Item forms for the ModularMagic providers, including blocks without an installed optional integration.
 *
 * <p>Kept in a register of its own with the {@code modularmagic} namespace, for the same reason the
 * blocks are (see {@link RegistryModularMagicBlocks}). Without item forms the blocks would be
 * unobtainable in-game and, more importantly, absent from the creative tab and from any pack that
 * grants them by id.
 *
 * <p>The registrations are built from an explicit list rather than restated per entry so the block and
 * item ids cannot drift apart — a mismatch there produces a block with no item, which is silent until
 * someone tries to place it.
 */
public final class RegistryModularMagicItems {

    private static final String NAMESPACE = "modularmagic";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NAMESPACE);

    /** Block/item id suffixes, matching the upstream {@code modularmagic} asset names exactly. */
    public static final List<String> PROVIDER_IDS = List.of(
            "blockaspectproviderinput",
            "blockaspectprovideroutput",
            "blockstarlightproviderinput",
            "blockstarlightprovideroutput",
            "blockconstellationprovider",
            "blockauraproviderinput",
            "blockauraprovideroutput",
            "blockwillproviderinput",
            "blockwillprovideroutput",
            "blocklifeessenceproviderinput",
            "blocklifeessenceprovideroutput",
            "blockmanaproviderinput",
            "blockmanaprovideroutput",
            "blockgridproviderinput",
            "blockgridprovideroutput",
            "blockimpetusproviderinput",
            "blockimpetusprovideroutput",
            "blockrainbowprovider"
    );

    /** Item forms, in the same order as {@link #PROVIDER_IDS}. */
    public static final List<RegistryObject<BlockItem>> PROVIDERS = new ArrayList<>();

    static {
        for (String id : PROVIDER_IDS) {
            PROVIDERS.add(ITEMS.register(id, () -> {
                // Resolve the block by registry id rather than through a field, so the id list above is
                // the single source of truth and a rename cannot desynchronise the two registries.
                net.minecraft.world.level.block.Block block =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                .getValue(new net.minecraft.resources.ResourceLocation(NAMESPACE, id));
                if (block == null) {
                    throw new IllegalStateException("Missing modularmagic block for item '" + id
                            + "'; block and item ids must stay in sync");
                }
                return new BlockItem(block, new Item.Properties());
            }));
        }
    }

    private RegistryModularMagicItems() {
    }
}
