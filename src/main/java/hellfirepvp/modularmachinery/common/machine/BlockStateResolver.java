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

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a machine JSON element reference into the set of {@link BlockState}s that satisfy it.
 *
 * <h2>The two-tier rule</h2>
 *
 * <p>Whether a part is <b>pinned to one variant</b> or <b>satisfied by any state of a block</b> depends
 * on whether the JSON constrained it:
 *
 * <ul>
 *   <li>{@code modularmachinery:blockcasing@4} — explicit metadata, so exactly
 *       {@code casing=reinforced}. A plain casing must <b>not</b> satisfy it.</li>
 *   <li>{@code modularmachinery:blockcasing} — no metadata, so any casing variant will do. This is the
 *       form the variables use, and it is what makes one definition accept every tier of a hatch.</li>
 *   <li>{@code modularmachinery:blockenergyinputhatch@0} — metadata 0 is the <i>default</i> variant, so
 *       it means the default state specifically rather than "any".</li>
 * </ul>
 *
 * <p>Collapsing the unconstrained case to {@code defaultBlockState()} would be a real bug: a machine
 * declaring a bare block would only accept its default variant, so a player using any other tier would
 * see an unobtainable structure. Conversely, expanding the constrained case to "any state" would let a
 * machine assemble from the wrong blocks.
 *
 * <h2>R3 reporting</h2>
 *
 * <p>Anything the {@link BlockStateShim} cannot translate is recorded, deduplicated and given a
 * reason, and the affected part is reported as unresolvable by the matcher rather than silently
 * accepted. This is the port's highest-pervasiveness risk; see {@link BlockStateShim}.
 */
public final class BlockStateResolver {

    private final Set<String> unresolved = new LinkedHashSet<>();
    private final Map<String, String> unresolvedReasons = new LinkedHashMap<>();

    /**
     * @param element an element name as written in a machine JSON, e.g. {@code minecraft:stonebrick@0}
     *                or {@code modularmachinery:blockcasing}
     * @return the accepted states in priority order (a shim-resolved state first), or an empty list
     *         when the reference cannot be resolved — in which case the reason is recorded, see
     *         {@link #getUnresolved()}
     */
    public List<BlockState> resolveAccepted(String element) {
        if (element == null || element.isEmpty()) {
            return List.of();
        }

        // 1. A BARE BLOCK ID MEANS "ANY STATE OF THAT BLOCK" — and this must run BEFORE the shim.
        //
        // The defect this fixes (§126): the shim branch used to run first and `return List.of(exact)` -- a
        // SINGLE state. So for every block the shim knows, which includes `minecraft:iron_bars`, the "any
        // state" expansion below was UNREACHABLE and a bare id silently became "this exact state".
        //
        // The consequence was player-visible and severe: upstream's own shipped definitions write
        // `"elements": "minecraft:iron_bars"` with no state, and iron bars take east/west connections from
        // their neighbours. A freshly placed structure therefore never matched -- the resolver demanded the
        // default state and found a connected one:
        //
        //   expected minecraft:iron_bars but found
        //   Block{minecraft:iron_bars}[east=false,...,west=true] — SAME BLOCK, DIFFERENT STATE
        //
        // The same applied to fences, walls, glass panes, stairs, redstone dust and chests -- every block
        // whose state depends on its neighbours. StructureMatcher's own javadoc already claimed "a bare
        // block id accepts any state of that block"; that was true only for blocks the shim did NOT know,
        // which reading either branch in isolation would never reveal.
        //
        // Ordering is the whole fix: no metadata -> accept all states of the block; metadata (`@n`) -> let
        // the shim pin it; a block the shim renamed (and `lookupBlock` cannot find) -> shim.
        boolean hadMetadata = element.indexOf('@') >= 0;
        if (!hadMetadata) {
            Block block = lookupBlock(element);
            if (block != null) {
                List<BlockState> states = new ArrayList<>(block.getStateDefinition().getPossibleStates());
                if (!states.isEmpty()) {
                    return states;
                }
            }
        }

        // 2. The curated shim owns all knowledge of renamed blocks and metadata translation.
        BlockState exact = BlockStateShim.resolve(element);
        if (exact != null) {
            return List.of(exact);
        }

        // 3. Genuinely unresolvable: record once, with a reason.
        if (unresolved.add(element)) {
            unresolvedReasons.put(element, diagnose(element, hadMetadata));
        }
        return List.of();
    }

    /**
     * Convenience for callers that only need a single state, such as a preview or a diagnostic listing.
     *
     * @return the first accepted state, or {@code null} when nothing is accepted
     */
    public BlockState resolve(String element) {
        List<BlockState> accepted = resolveAccepted(element);
        return accepted.isEmpty() ? null : accepted.get(0);
    }

    private static Block lookupBlock(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(key);
        return block == Blocks.AIR ? null : block;
    }

    /**
     * @return a human-readable reason, separating the three failure modes so the worklist is
     *         actionable rather than a wall of identical text
     */
    private String diagnose(String element, boolean hadMetadata) {
        int at = element.indexOf('@');
        String id = at >= 0 ? element.substring(0, at) : element;
        int meta = 0;
        if (at >= 0) {
            try {
                meta = Integer.parseInt(element.substring(at + 1).trim());
            } catch (NumberFormatException e) {
                return "unparseable metadata in '" + element + "'";
            }
        }

        ResourceLocation key = ResourceLocation.tryParse(id);
        boolean blockExists = key != null && ForgeRegistries.BLOCKS.containsKey(key);

        if (!blockExists) {
            // Either renamed by the flattening with no shim entry, or from a mod that is absent.
            return "no such block '" + id + "' (renamed by the flattening, or from an absent mod)";
        }
        if (hadMetadata && meta != 0) {
            return "non-zero metadata " + meta + " on '" + id + "' has no curated mapping";
        }
        return "unresolved for an unknown reason";
    }

    /** @return one entry per distinct unresolvable reference, mapped to its reason */
    public Map<String, String> getUnresolved() {
        return unresolvedReasons;
    }

    public boolean hasUnresolved() {
        return !unresolvedReasons.isEmpty();
    }

    /** Logs the unresolved worklist once, deduplicated, at the end of a load. */
    public void reportUnresolved() {
        if (!hasUnresolved()) {
            return;
        }
        Modularmachinery.LOG.warn("[MMCE] {} distinct element reference(s) could not be resolved on "
                + "1.20.1 (metadata flattening, R3):", unresolvedReasons.size());
        Modularmachinery.LOG.warn("[MMCE] shim coverage: {}", BlockStateShim.describeCoverage());
        unresolvedReasons.forEach((element, reason) ->
                Modularmachinery.LOG.warn("[MMCE]   {} — {}", element, reason));
    }
}
