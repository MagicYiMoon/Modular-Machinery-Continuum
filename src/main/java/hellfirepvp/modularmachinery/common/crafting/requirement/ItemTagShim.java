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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Maps legacy 1.12.2 ore-dictionary names to 1.20.1 item tags.
 *
 * <h2>The problem</h2>
 *
 * <p>1.12.2 recipes addressed "any iron ingot, from any mod" as an <b>ore-dictionary</b> entry:
 * {@code ore:ingotIron}. The ore dictionary does not exist on 1.20.1 — its replacement is
 * <b>item tags</b>, and Forge populates a conventional set for exactly this purpose. The shipped
 * content uses four:
 *
 * <pre>
 * ore:ingotIron      -> forge:ingots/iron
 * ore:ingotGold      -> forge:ingots/gold
 * ore:dustRedstone   -> forge:dusts/redstone
 * ore:dustGlowstone  -> forge:dusts/glowstone
 * </pre>
 *
 * <p>Note the spelling conversion is not purely mechanical: the ore dictionary used a
 * lowerCamelCase tail ({@code ingotIron}, {@code dustRedstone}) while tags use lowercase with a
 * category directory ({@code ingots/iron}, {@code dusts/redstone}), including <b>pluralising the
 * category</b> for some entries and not others ({@code gemDiamond} → {@code gems/diamond} but
 * {@code dustRedstone} → {@code dusts/redstone}). A generated name would be wrong in ways that are
 * hard to notice, which is why this is a table rather than a transform.
 *
 * <h2>Why a tag, not a list of items</h2>
 *
 * <p>Resolving {@code ore:ingotIron} to {@code minecraft:iron_ingot} would work for vanilla and break
 * every pack that adds another iron ingot — exactly the case the ore dictionary existed to handle.
 * The tag preserves that intent and is resolved <b>at match time</b>, so mods added later are picked
 * up without a code change.
 */
public final class ItemTagShim {

    private static final Map<String, String> ORE_DICT_TO_TAG = Map.ofEntries(
            // --- the four the shipped recipes use ---
            Map.entry("ore:ingotIron", "forge:ingots/iron"),
            Map.entry("ore:ingotGold", "forge:ingots/gold"),
            Map.entry("ore:dustRedstone", "forge:dusts/redstone"),
            Map.entry("ore:dustGlowstone", "forge:dusts/glowstone"),

            // --- the common remainder, for pack content beyond the shipped set ---
            Map.entry("ore:ingotCopper", "forge:ingots/copper"),
            Map.entry("ore:ingotTin", "forge:ingots/tin"),
            Map.entry("ore:ingotBronze", "forge:ingots/bronze"),
            Map.entry("ore:ingotLead", "forge:ingots/lead"),
            Map.entry("ore:ingotSilver", "forge:ingots/silver"),
            Map.entry("ore:ingotNickel", "forge:ingots/nickel"),
            Map.entry("ore:ingotSteel", "forge:ingots/steel"),
            Map.entry("ore:ingotAluminum", "forge:ingots/aluminum"),
            Map.entry("ore:ingotUranium", "forge:ingots/uranium"),
            Map.entry("ore:ingotPlatinum", "forge:ingots/platinum"),

            Map.entry("ore:dustIron", "forge:dusts/iron"),
            Map.entry("ore:dustGold", "forge:dusts/gold"),
            Map.entry("ore:dustCopper", "forge:dusts/copper"),
            Map.entry("ore:dustTin", "forge:dusts/tin"),
            Map.entry("ore:dustBronze", "forge:dusts/bronze"),
            Map.entry("ore:dustLead", "forge:dusts/lead"),
            Map.entry("ore:dustSilver", "forge:dusts/silver"),
            Map.entry("ore:dustNickel", "forge:dusts/nickel"),
            Map.entry("ore:dustCoal", "forge:dusts/coal"),
            Map.entry("ore:dustDiamond", "forge:dusts/diamond"),
            Map.entry("ore:dustEmerald", "forge:dusts/emerald"),
            Map.entry("ore:dustQuartz", "forge:dusts/quartz"),
            Map.entry("ore:dustSulfur", "forge:dusts/sulfur"),
            Map.entry("ore:dustSaltpeter", "forge:dusts/saltpeter"),

            Map.entry("ore:gemDiamond", "forge:gems/diamond"),
            Map.entry("ore:gemEmerald", "forge:gems/emerald"),
            Map.entry("ore:gemQuartz", "forge:gems/quartz"),
            Map.entry("ore:gemLapis", "forge:gems/lapis"),
            Map.entry("ore:gemAmethyst", "forge:gems/amethyst"),
            Map.entry("ore:gemPrismarine", "forge:gems/prismarine"),

            Map.entry("ore:oreIron", "forge:ores/iron"),
            Map.entry("ore:oreGold", "forge:ores/gold"),
            Map.entry("ore:oreCopper", "forge:ores/copper"),
            Map.entry("ore:oreTin", "forge:ores/tin"),
            Map.entry("ore:oreLead", "forge:ores/lead"),
            Map.entry("ore:oreSilver", "forge:ores/silver"),
            Map.entry("ore:oreNickel", "forge:ores/nickel"),
            Map.entry("ore:oreCoal", "forge:ores/coal"),
            Map.entry("ore:oreDiamond", "forge:ores/diamond"),
            Map.entry("ore:oreEmerald", "forge:ores/emerald"),
            Map.entry("ore:oreLapis", "forge:ores/lapis"),
            Map.entry("ore:oreRedstone", "forge:ores/redstone"),
            Map.entry("ore:oreQuartz", "forge:ores/quartz"),

            Map.entry("ore:blockIron", "forge:storage_blocks/iron"),
            Map.entry("ore:blockGold", "forge:storage_blocks/gold"),
            Map.entry("ore:blockCopper", "forge:storage_blocks/copper"),
            Map.entry("ore:blockCoal", "forge:storage_blocks/coal"),
            Map.entry("ore:blockDiamond", "forge:storage_blocks/diamond"),
            Map.entry("ore:blockEmerald", "forge:storage_blocks/emerald"),
            Map.entry("ore:blockLapis", "forge:storage_blocks/lapis"),
            Map.entry("ore:blockRedstone", "forge:storage_blocks/redstone"),
            Map.entry("ore:blockQuartz", "forge:storage_blocks/quartz"));

    private ItemTagShim() {
    }

    /**
     * @param legacyName an ore-dictionary style name such as {@code ore:ingotIron}
     * @return the equivalent {@link TagKey}, or {@code null} when this shim has no mapping — in which
     *         case the caller must report it rather than substitute an item, because a wrong
     *         substitution silently changes what a machine accepts
     */
    public static TagKey<Item> tagFor(String legacyName) {
        String mapped = ORE_DICT_TO_TAG.get(legacyName);
        if (mapped == null) {
            return null;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(mapped);
        if (tagId == null) {
            return null;
        }
        return ItemTags.create(tagId);
    }

    /** @return true when {@code name} looks like an ore-dictionary entry rather than a resource location */
    public static boolean isOreDictName(String name) {
        return name != null && name.startsWith("ore:");
    }

    /** @return the number of mapped names, for load-time reporting */
    public static int mappedCount() {
        return ORE_DICT_TO_TAG.size();
    }
}
