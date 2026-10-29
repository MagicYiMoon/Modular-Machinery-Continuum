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
package hellfirepvp.modularmachinery.common.crafting.requirement;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;



























 
public final class ItemMetadataShim {

    /**
     * {@code "itemId@meta"} → 1.20.1 item id.
     *
     * <p>The {@code minecraft:dye} values follow the 1.12.2 {@code EnumDyeColor} order:
     * 0 white … 3 cocoa (brown) … 15 black. Note that 1.20.1's brown dye is {@code brown_dye}, but the
     * <b>item</b> 1.12.2 called "cocoa" metadata 3 was the cocoa bean, which is what the recipe meant.
     */
    private static final Map<String, String> METADATA_ITEMS = Map.ofEntries(
            Map.entry("minecraft:dye@0", "minecraft:white_dye"),
            Map.entry("minecraft:dye@1", "minecraft:orange_dye"),
            Map.entry("minecraft:dye@2", "minecraft:magenta_dye"),
            Map.entry("minecraft:dye@3", "minecraft:cocoa_beans"),
            Map.entry("minecraft:dye@4", "minecraft:yellow_dye"),
            Map.entry("minecraft:dye@5", "minecraft:lime_dye"),
            Map.entry("minecraft:dye@6", "minecraft:pink_dye"),
            Map.entry("minecraft:dye@7", "minecraft:gray_dye"),
            Map.entry("minecraft:dye@8", "minecraft:light_gray_dye"),
            Map.entry("minecraft:dye@9", "minecraft:cyan_dye"),
            Map.entry("minecraft:dye@10", "minecraft:purple_dye"),
            Map.entry("minecraft:dye@11", "minecraft:blue_dye"),
            Map.entry("minecraft:dye@12", "minecraft:brown_dye"),
            Map.entry("minecraft:dye@13", "minecraft:green_dye"),
            Map.entry("minecraft:dye@14", "minecraft:red_dye"),
            Map.entry("minecraft:dye@15", "minecraft:black_dye"),

            // coal: 0 coal, 1 charcoal
            Map.entry("minecraft:coal@0", "minecraft:coal"),
            Map.entry("minecraft:coal@1", "minecraft:charcoal"),

            // fish: the numeric order differs from the 1.13 item list, so this is a table
            Map.entry("minecraft:fish@0", "minecraft:cod"),
            Map.entry("minecraft:fish@1", "minecraft:salmon"),
            Map.entry("minecraft:fish@2", "minecraft:tropical_fish"),
            Map.entry("minecraft:fish@3", "minecraft:pufferfish"),

            // stone variants became distinct items
            Map.entry("minecraft:stone@0", "minecraft:stone"),
            Map.entry("minecraft:stone@1", "minecraft:granite"),
            Map.entry("minecraft:stone@3", "minecraft:diorite"),
            Map.entry("minecraft:stone@5", "minecraft:andesite"),

            // sandstone: 0 normal, 1 chiselled, 2 cut
            Map.entry("minecraft:sandstone@1", "minecraft:chiselled_sandstone"),
            Map.entry("minecraft:sandstone@2", "minecraft:cut_sandstone"),

            // quartz block: 0 normal, 1 chiselled, 2 pillar
            Map.entry("minecraft:quartz_block@1", "minecraft:chiselled_quartz_block"),
            Map.entry("minecraft:quartz_block@2", "minecraft:quartz_pillar"),

            // stonebrick: 0 normal, 1 mossy, 2 cracked, 3 chiselled
            Map.entry("minecraft:stonebrick@1", "minecraft:mossy_stone_bricks"),
            Map.entry("minecraft:stonebrick@2", "minecraft:cracked_stone_bricks"),
            Map.entry("minecraft:stonebrick@3", "minecraft:chiselled_stone_bricks"),

            // red_flower: 0 poppy, 1 blue orchid, 2 allium, 3 houstonia, 4 tulip(red), 5 oxeye daisy
            Map.entry("minecraft:red_flower@0", "minecraft:poppy"),
            Map.entry("minecraft:red_flower@1", "minecraft:blue_orchid"),
            Map.entry("minecraft:red_flower@2", "minecraft:allium"),
            Map.entry("minecraft:red_flower@3", "minecraft:azure_bluet"),
            Map.entry("minecraft:red_flower@4", "minecraft:red_tulip"),
            Map.entry("minecraft:red_flower@5", "minecraft:oxeye_daisy"));

    private ItemMetadataShim() {
    }

    /**
     * @param element an id as written in JSON, e.g. {@code minecraft:dye@3}
     * @return the 1.20.1 item id, or {@code null} when this shim has no verified mapping
     */
    public static ResourceLocation itemFor(String element) {
        String mapped = METADATA_ITEMS.get(element);
        return mapped == null ? null : ResourceLocation.tryParse(mapped);
    }

    /** @return the number of mapped ids, for load-time reporting */
    public static int mappedCount() {
        return METADATA_ITEMS.size();
    }
}
