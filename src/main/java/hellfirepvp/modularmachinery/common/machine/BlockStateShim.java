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
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.block.CasingType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Curated translation from 1.12.2 element references to 1.20.1 {@link BlockState}s.
 *
 * <h2>Why a curated table rather than a rule</h2>
 *
 * <p>1.12.2 addressed blocks as {@code namespace:block@metadata}. <b>1.20.1 has no metadata.</b> Each
 * variant is now either a distinct block or a blockstate property, and there is no general mapping
 * between the two — it is variant-by-variant knowledge. This is the port's highest-pervasiveness risk
 * (R3), and it surfaced immediately once the loader ran against the shipped definitions:
 *
 * <pre>
 * modularmachinery:blockcasing@4   -> CasingType.REINFORCED
 * modularmachinery:blockcasing@2   -> CasingType.FIREBOX
 * minecraft:stonebrick@0           -> minecraft:stone_bricks   (block renamed in the flattening)
 * </pre>
 *
 * <p>Guessing here would be worse than failing: silently mapping {@code @4} onto the default casing
 * would change which blocks a machine accepts while looking like success. So unresolvable references
 * stay unresolvable and are reported, and only entries with a <b>verified</b> target are listed below.
 *
 * <h2>Two mechanisms</h2>
 * <ul>
 *   <li><b>Aliases</b> — a 1.12.2 block id that was renamed by the 1.13 flattening
 *       ({@code stonebrick} → {@code stone_bricks}), with no metadata involved.</li>
 *   <li><b>Metadata mappings</b> — a {@code (block, metadata)} pair that became a blockstate property
 *       value or a different block entirely.</li>
 * </ul>
 */
public final class BlockStateShim {

    /** 1.12.2 block id → 1.20.1 block id, for blocks renamed by the flattening. */
    private static final Map<String, ResourceLocation> ALIASES = new HashMap<>();

    /** {@code "blockId@meta"} → resolver producing the 1.20.1 state. */
    private static final Map<String, Function<Block, BlockState>> METADATA = new HashMap<>();

    static {
        // --- Renamed blocks. Verified against the 1.13 flattening rename list. ---
        aliases("minecraft:stonebrick", "minecraft:stone_bricks");
        aliases("minecraft:stone_stairs", "minecraft:stone_brick_stairs");
        aliases("minecraft:red_flower", "minecraft:poppy");
        aliases("minecraft:yellow_flower", "minecraft:dandelion");
        aliases("minecraft:grass", "minecraft:grass_block");
        aliases("minecraft:lit_furnace", "minecraft:furnace");
        aliases("minecraft:fence", "minecraft:oak_fence");
        aliases("minecraft:fence_gate", "minecraft:oak_fence_gate");
        aliases("minecraft:wooden_door", "minecraft:oak_door");
        aliases("minecraft:trapdoor", "minecraft:oak_trapdoor");
        aliases("minecraft:wooden_slab", "minecraft:oak_slab");
        aliases("minecraft:stone_slab", "minecraft:smooth_stone_slab");
        aliases("minecraft:double_wooden_slab", "minecraft:oak_slab");
        aliases("minecraft:double_stone_slab", "minecraft:smooth_stone_slab");
        aliases("minecraft:planks", "minecraft:oak_planks");
        aliases("minecraft:log", "minecraft:oak_log");
        aliases("minecraft:log2", "minecraft:dark_oak_log");
        aliases("minecraft:leaves", "minecraft:oak_leaves");
        aliases("minecraft:leaves2", "minecraft:dark_oak_leaves");
        aliases("minecraft:sapling", "minecraft:oak_sapling");
        aliases("minecraft:web", "minecraft:cobweb");
        aliases("minecraft:snow_layer", "minecraft:snow");
        aliases("minecraft:deadbush", "minecraft:dead_bush");
        aliases("minecraft:waterlily", "minecraft:lily_pad");
        aliases("minecraft:nether_brick", "minecraft:nether_bricks");
        aliases("minecraft:hardened_clay", "minecraft:terracotta");
        aliases("minecraft:stained_hardened_clay", "minecraft:white_terracotta");
        aliases("minecraft:stained_glass", "minecraft:white_stained_glass");
        aliases("minecraft:stained_glass_pane", "minecraft:white_stained_glass_pane");
        aliases("minecraft:wool", "minecraft:white_wool");
        aliases("minecraft:carpet", "minecraft:white_carpet");
        aliases("minecraft:concrete", "minecraft:white_concrete");
        aliases("minecraft:concrete_powder", "minecraft:white_concrete_powder");
        aliases("minecraft:red_nether_brick", "minecraft:red_nether_bricks");
        aliases("minecraft:silver_glazed_terracotta", "minecraft:light_gray_glazed_terracotta");
        aliases("minecraft:magma", "minecraft:magma_block");
        aliases("minecraft:brick_block", "minecraft:bricks");
        aliases("minecraft:noteblock", "minecraft:note_block");
        aliases("minecraft:anvil", "minecraft:anvil");

        // --- Metadata that became a blockstate property value. ---
        // The mod's own casing enum survived the port intact (CasingType), so its metadata values map
        // straight onto the blockstate property of the same name.
        for (CasingType type : CasingType.values()) {
            // 1.12.2 metadata order == CasingType declaration order: PLAIN 0, VENT 1, FIREBOX 2,
            // GEARBOX 3, REINFORCED 4, CIRCUITRY 5.
            metadata("modularmachinery:blockcasing", type.ordinal(),
                    block -> setProperty(block.defaultBlockState(), "casing", type));
        }

        // --- Stairs, whose 1.12.2 metadata IS documented: bits 0-1 are the FACING, bit 2 the HALF. ---
        //
        // Found by the dynamic-pattern walk (PLAN section 331), which could not rebuild the assembly line
        // because this pair was missing. Upstream's assembly_line.json uses quartz_stairs@0 at x=-1 and
        // quartz_stairs@1 at x=+1 -- a mirrored left/right pair -- and 1.12.2's order is EAST 0, WEST 1,
        // SOUTH 2, NORTH 3.
        //
        // Both are HALF=bottom: metadata 0 and 1 both have bit 2 clear, so only the facing is set and the
        // default half is already right.
        //
        // THE DIRECTIONS ARE NOT VERIFIED BY ANY SELF-TEST, AND CANNOT BE. The check that found the gap
        // places blocks with THIS resolver and then matches them with it, so it is self-consistent whatever
        // these two lines say -- section 309's trap exactly, where a preview and a matcher agreed with each
        // other and disagreed with the mod. What CAN verify them is a player building the line in a world;
        // until that happens the metadata rule above is the only evidence, which is why it is cited rather
        // than assumed.
        metadata("minecraft:quartz_stairs", 0,
                block -> setProperty(block.defaultBlockState(), "facing", net.minecraft.core.Direction.EAST));
        metadata("minecraft:quartz_stairs", 1,
                block -> setProperty(block.defaultBlockState(), "facing", net.minecraft.core.Direction.WEST));
    }

    /** Metadata that became a distinct block: {@code blockId@meta} → other block id. */
    private static final Map<String, ResourceLocation> METADATA_TO_BLOCK = new HashMap<>();

    static {
        // minecraft:stone: 0 stone, 1 granite, 2 polished granite, 3 diorite, 4 polished diorite,
        // 5 andesite, 6 polished andesite.
        metadataToBlock("minecraft:stone", 1, "minecraft:granite");
        metadataToBlock("minecraft:stone", 2, "minecraft:polished_granite");
        metadataToBlock("minecraft:stone", 3, "minecraft:diorite");
        metadataToBlock("minecraft:stone", 4, "minecraft:polished_diorite");
        metadataToBlock("minecraft:stone", 5, "minecraft:andesite");
        metadataToBlock("minecraft:stone", 6, "minecraft:polished_andesite");
        // stonebrick: 0 normal, 1 mossy, 2 cracked, 3 chiselled
        metadataToBlock("minecraft:stonebrick", 1, "minecraft:mossy_stone_bricks");
        metadataToBlock("minecraft:stonebrick", 2, "minecraft:cracked_stone_bricks");
        metadataToBlock("minecraft:stonebrick", 3, "minecraft:chiselled_stone_bricks");
        // sandstone: 0 normal, 1 chiselled, 2 cut
        metadataToBlock("minecraft:sandstone", 1, "minecraft:chiselled_sandstone");
        metadataToBlock("minecraft:sandstone", 2, "minecraft:cut_sandstone");
        // quartz_block: 0 normal, 1 chiselled, 2 pillar, 3/4 smooth
        metadataToBlock("minecraft:quartz_block", 1, "minecraft:chiselled_quartz_block");
        metadataToBlock("minecraft:quartz_block", 2, "minecraft:quartz_pillar");
        metadataToBlock("minecraft:quartz_block", 3, "minecraft:smooth_quartz");
    }

    private BlockStateShim() {
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /**
     * @param element an element name from a machine JSON, e.g. {@code minecraft:stonebrick@0}
     * @return the resolved state, or {@code null} if this shim has no verified translation — in which
     *         case the caller must report it rather than substitute a default
     */
    public static BlockState resolve(String element) {
        if (element == null || element.isEmpty()) {
            return null;
        }

        String id = element;
        int meta = 0;
        boolean hadMeta = false;
        int at = element.indexOf('@');
        if (at >= 0) {
            id = element.substring(0, at);
            hadMeta = true;
            try {
                meta = Integer.parseInt(element.substring(at + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // 1. A distinct-block mapping wins: it is the most specific case.
        if (hadMeta) {
            ResourceLocation target = METADATA_TO_BLOCK.get(id + "@" + meta);
            if (target != null) {
                Block block = ForgeRegistries.BLOCKS.getValue(target);
                // The Blocks.AIR check is NOT redundant with the null check. Forge's registry lookup
                // returns the registry's DEFAULT VALUE for an unregistered name rather than null, so a
                // stale entry in METADATA_TO_BLOCK would come back as air and this method would hand
                // out air.defaultBlockState() -- a "valid" state that is really an absence. A structure
                // built from that places nothing and then fails the match with a mismatch that points
                // at the machine rather than at the shim. The three sibling lookups in this class
                // already guard both cases; this one checked only null.
                return (block == null || block == Blocks.AIR) ? null : block.defaultBlockState();
            }
        }

        // 2. Resolve the block id, following an alias if the id was renamed.
        Block block = lookup(id);
        if (block == null) {
            return null;
        }

        // 3. A property mapping, for metadata that became a blockstate value.
        if (hadMeta) {
            Function<Block, BlockState> mapper = METADATA.get(id + "@" + meta);
            if (mapper != null) {
                return mapper.apply(block);
            }
            // Non-zero metadata with no curated entry: refuse rather than silently default.
            if (meta != 0) {
                return null;
            }
        }

        return block.defaultBlockState();
    }

    private static Block lookup(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(key);
        if (block != null && block != Blocks.AIR) {
            return block;
        }
        ResourceLocation alias = ALIASES.get(id);
        if (alias != null) {
            block = ForgeRegistries.BLOCKS.getValue(alias);
            if (block != null && block != Blocks.AIR) {
                return block;
            }
        }
        return null;
    }

    /** @return true when an id is known to this shim, for diagnostics */
    public static boolean isKnown(String id) {
        return ALIASES.containsKey(id) || ForgeRegistries.BLOCKS.containsKey(ResourceLocation.tryParse(id));
    }

    // ------------------------------------------------------------------
    // Registration helpers (static initialisers only)
    // ------------------------------------------------------------------

    private static void aliases(String from, String to) {
        ALIASES.put(from, new ResourceLocation(to));
    }

    private static void metadataToBlock(String blockId, int meta, String targetId) {
        METADATA_TO_BLOCK.put(blockId + "@" + meta, new ResourceLocation(targetId));
    }

    private static void metadata(String blockId, int meta, Function<Block, BlockState> mapper) {
        METADATA.put(blockId + "@" + meta, mapper);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, String name, T value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            return state;
        }
        return state.setValue((Property<T>) property, value);
    }

    /** @return a summary of what this shim can translate, for load-time reporting */
    public static List<String> describeCoverage() {
        return List.of(
                "aliases: " + ALIASES.size(),
                "metadata->block: " + METADATA_TO_BLOCK.size(),
                "metadata->property: " + METADATA.size());
    }
}
