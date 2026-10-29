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

import hellfirepvp.modularmachinery.common.modifier.ModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;



















































 
public final class PreviewCellDecision {

    /**
     * Upstream's own distance test (U133), kept as the squared value so no square root is taken per
     * frame: farther than this from the anchor and the projection is discarded.
     */
    public static final double OUT_OF_RANGE_SQ = 1024.0D;

    /**
     * The distance {@link #OUT_OF_RANGE_SQ} means, in blocks.
     *
     * <p>Derived rather than written twice: 1024 is 32², and a second literal would be free to drift
     * away from the first. The user remembered 40; upstream's code says 32 and this is that 32.</p>
     */
    public static final int MAX_PREVIEW_DISTANCE = (int) Math.sqrt(OUT_OF_RANGE_SQ);

    private PreviewCellDecision() {
    }

    /**
     * What the renderer must do with one declared cell.
     *
     * <ul>
     *   <li>{@link #MATCHED} — the world already holds a block the pattern accepts. <b>DRAW NOTHING</b>
     *       (rule ②, upstream U257-U260).</li>
     *   <li>{@link #HIGHLIGHTED} — something is there that the pattern does NOT accept, and that
     *       something is a block the draw call can render. The ghost is tinted RED (rule ③).</li>
     *   <li>{@link #GHOST} — the cell needs a neutral preview. Empty/undrawable loaded cells use the
     *       expected pattern state when one resolves; an unresolved element or unavailable sample falls
     *       back to the {@code blockcasing} placeholder. Unloaded chunks also use the expected state when
     *       available, because the source renderer has that state cached independently of world chunks.
     *       <b>None of these paths is an error highlight</b>.</li>
     * </ul>
     */
    public enum PreviewCell {
        /** The world already satisfies this cell: no ghost at all. */
        MATCHED,
        /** The cell needs a ghost; the world here is empty or undrawable, so nothing is wrong yet. */
        GHOST,
        /** The cell needs a ghost; what is here now is a block the pattern does not accept. */
        HIGHLIGHTED
    }

    /**
     * One decision, with the state to draw and the reason it was reached.
     *
     * @param cell       what to do
     * @param state      the state to draw, or {@code null} for {@link PreviewCell#MATCHED}
     * @param highlight  whether to tint that state red
     * @param reason     a one-line explanation, for the self-test's message and for a future log
     */
    public record Decision(PreviewCell cell, BlockState state, boolean highlight, String reason) {
    }

    /**
     * @return true when a player at {@code playerX/Y/Z} is at or beyond {@link #OUT_OF_RANGE_SQ} from
     *         the centre of {@code anchor} — the condition upstream uses to DISCARD the projection
     *
     * <p><b>THE COMPARISON IS UPSTREAM'S, CHARACTER FOR CHARACTER</b> ({@code >=}, not {@code >};
     * U133). The distance is measured to the BLOCK CENTRE, which is what
     * {@code getDistanceSqToCenter} did in 1.12.2 -- a plain {@code distanceToSqr(BlockPos)} on
     * 1.20.1 measures to the corner and is off by up to 0.75 blocks of squared distance. Both halves
     * were checked rather than assumed, and the self-test walks the boundary from both sides.</p>
     */
    public static boolean isOutOfRange(double playerX, double playerY, double playerZ, BlockPos anchor) {
        if (anchor == null) {
            return false;
        }
        double dx = playerX - (anchor.getX() + 0.5D);
        double dy = playerY - (anchor.getY() + 0.5D);
        double dz = playerZ - (anchor.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz >= OUT_OF_RANGE_SQ;
    }

    /**
     * Decides one cell of the preview.
     *
     * <p>The four branches, in the order they are taken, and each one's upstream source is on the enum
     * above. The state returned for {@link PreviewCell#HIGHLIGHTED} is the state <b>the pattern
     * asks for</b> rather than the misplaced block that is standing there -- the player is being shown
     * what belongs in the cell, in red, instead of a red copy of their own mistake. The deterministic
     * overload uses the first accepted state; the client sampling overload below rotates through the
     * accepted states like the source renderer. When the part resolves to nothing at all
     * (an element the shim cannot translate) the world's own state is drawn red instead, so the cell is
     * still visible and still marked.</p>
     *
     * @param level        the level to read (client or server; this is a pure read)
     * @param world        the cell's world position
     * @param part         the declared part
     * @param variables    the load's variable table
     * @param resolver     the load's element resolver
     * @param replacements the machine's modifier replacements (alternatives, §239)
     * @param placeholder  what to draw where nothing drawable exists (the {@code blockcasing} state)
     * @return the decision, never {@code null}
     */
    public static Decision decide(Level level, BlockPos world, MachinePattern.Part part,
                                  MachineVariables variables, BlockStateResolver resolver,
                                  List<ModifierReplacement> replacements, BlockState placeholder) {
        return decide(level, world, part, variables, resolver, replacements, placeholder, 0L);
    }

    /**
     * Convenience overload for a machine's own replacements, which is what the preview has in hand.
     *
     * @return the decision, never {@code null}
     */
    public static Decision decide(Level level, BlockPos world, MachinePattern.Part part,
                                  MachineVariables variables, BlockStateResolver resolver,
                                  DynamicMachine machine, BlockState placeholder) {
        return decide(level, world, part, variables, resolver,
                machine == null ? List.of() : machine.getModifierReplacements(), placeholder, 0L);
    }

    /**
     * Preview overload which samples alternatives the same way the 1.12 renderer did.  The old
     * renderer did not always choose the first matching state: it cycled through every applicable
     * state (and every variable alternative), which is why a variable such as {@code casings_all}
     * did not render as a wall of the first casing.  Keeping the default overload deterministic is
     * useful for server-side checks; the client supplies its captured candidate tick here.
     */
    public static Decision decide(Level level, BlockPos world, MachinePattern.Part part,
                                  MachineVariables variables, BlockStateResolver resolver,
                                  DynamicMachine machine, BlockState placeholder, long sampleTick) {
        return decide(level, world, part, variables, resolver,
                machine == null ? List.of() : machine.getModifierReplacements(), placeholder, sampleTick);
    }

    private static Decision decide(Level level, BlockPos world, MachinePattern.Part part,
                                   MachineVariables variables, BlockStateResolver resolver,
                                   List<ModifierReplacement> replacements, BlockState placeholder,
                                   long sampleTick) {
        if (level == null || world == null) {
            return new Decision(PreviewCell.GHOST, placeholder, false, "no level or no position to read");
        }
        return decide(level, world, new WorldProjectionDefinition.Cell(part,
                StructureMatcher.acceptedStates(part, variables, resolver, replacements), false),
                net.minecraft.core.Direction.NORTH, placeholder, sampleTick);
    }

    /** Uses the same cached candidates and NBT match as the projection's layer completion check. */
    public static Decision decide(Level level, BlockPos world, WorldProjectionDefinition.Cell cell,
                                  net.minecraft.core.Direction facing, BlockState placeholder, long sampleTick) {
        if (level == null || world == null) {
            return new Decision(PreviewCell.GHOST, placeholder, false, "no level or no position to read");
        }
        BlockState sample = cell.sample(sampleTick, facing);
        if (!level.isLoaded(world)) {
            return new Decision(PreviewCell.GHOST, sample == null ? placeholder : sample, false,
                    "chunk not loaded, so the cell is neither satisfied nor wrong; expected state is previewed");
        }
        if (cell.matches(level, world, facing)) {
            return new Decision(PreviewCell.MATCHED, null, false,
                    "the world already accepts this cell, so no ghost is drawn there");
        }
        BlockState actual = level.getBlockState(world);
        if (sample != null) {
            if (actual.getRenderShape() != RenderShape.INVISIBLE && !actual.isAir()) {
                return new Decision(PreviewCell.HIGHLIGHTED, sample, true,
                        "the pattern wants a different block here, so the cell is highlighted");
            }
            return new Decision(PreviewCell.GHOST, sample, false,
                    "the cell is empty or not drawable, so the expected block is drawn");
        }

        if (!actual.isAir() && actual.getRenderShape() != RenderShape.INVISIBLE) {
            return new Decision(PreviewCell.HIGHLIGHTED, actual, true,
                    "the part resolves to no block at all and the world holds one, so it is highlighted");
        }
        BlockState placeholderState = placeholder == null ? actual : placeholder;
        return new Decision(PreviewCell.GHOST, placeholderState, false,
                actual.isAir() ? "the cell is empty, so the placeholder is drawn"
                        : "the world here cannot be drawn by the block renderer, so the placeholder is drawn");
    }

    /**
     * Selects one applicable state for a preview wheel.  Forty game ticks gives each alternative two seconds
     * on screen; the old 6/10 tick cadence made a multi-state cell visibly strobe.
     */
    public static BlockState sampleStateForPreview(List<BlockState> accepted, long sampleTick) {
        if (accepted == null || accepted.isEmpty()) {
            return null;
        }
        long step = Math.floorDiv(sampleTick, 40L);
        return accepted.get((int) Math.floorMod(step, accepted.size()));
    }
}
