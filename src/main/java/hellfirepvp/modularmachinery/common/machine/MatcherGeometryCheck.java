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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-checks for the structure matcher's geometry, run at definition load time.
 *
 * <p><b>Why these live in the mod rather than in a test source set.</b> An incorrect pattern rotation
 * is one of the nastiest possible bugs here: it does not crash, it still produces a "reasonable"
 * structure, and it is simply rotated or mirrored from what the pack author drew. It would present as
 * "this machine never forms" and be debugged by trial and error against a live world.
 *
 * <p>A JUnit suite would be the usual home for this, but the port's toolchain cannot currently resolve
 * a test dependency — {@code repo1.maven.org} is unreachable from the build environment, so adding
 * JUnit fails the build outright. Rather than ship a test that does not run, or silently drop the
 * coverage, the invariants are checked here on every definition load and reported in the log. That
 * gives the same protection with no network requirement, and it also means the checks run in
 * production, not only during a build.
 *
 * <p>If a test dependency becomes resolvable, these should move to a real test suite — they are pure
 * functions of {@link StructureMatcher#toWorldOffset} and need no game state.
 */
final class MatcherGeometryCheck {

    /** Squared-length table used to confirm rotation preserves distances. */
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {0, 0, 1}, {0, 1, 0}, {2, 3, 5}, {-1, 0, 0}, {0, 2, -3}, {-4, 1, 2},
    };

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
    };

    private MatcherGeometryCheck() {
    }

    /** @return the list of failed assertions; empty means every invariant held */
    static List<String> run() {
        List<String> failures = new ArrayList<>();
        BlockPos origin = new BlockPos(0, 64, 0);

        for (Direction facing : HORIZONTAL) {
            // 1. The pattern's forward axis (+Z) must map to the REAR of the controller -- i.e. to -facing.
            //
            //    THIS ASSERTION WAS WRONG AND IT FAILED DETERMINISTICALLY FOR ALL FOUR FACINGS (PLAN section 408).
            //    It expected `facing`, which is a different rotation from the one StructureMatcher implements, so the
            //    check reported "structures will match incorrectly" on every call in every configuration -- while the
            //    rotation itself was already correct.
            //
            //    The expectation is derived from the mapping, not guessed at it. StructureMatcher's rotation makes NORTH
            //    the IDENTITY because upstream's BlockArrayCache starts there unrotated (PLAN section 309), so:
            //
            //        +Z -> NORTH (0,1)  WEST (1,0)  SOUTH (0,-1)  EAST (-1,0)
            //
            //    and -facing is (0,1), (1,0), (0,-1), (-1,0) for those same four. That the pattern extends BEHIND its
            //    controller is what a controller mounted at the front of a machine means.
            //
            //    Assertion 2 below was checked the same way and IS correct, which is why only this one changes.
            BlockPos forward = StructureMatcher.toWorldOffset(origin, facing, 0, 0, 1).subtract(origin);
            int expectedForwardX = -facing.getStepX();
            int expectedForwardZ = -facing.getStepZ();
            if (forward.getX() != expectedForwardX || forward.getZ() != expectedForwardZ) {
                failures.add("forward axis for " + facing + " maps to (" + forward.getX() + ","
                        + forward.getZ() + "), expected (" + expectedForwardX + "," + expectedForwardZ + ")");
            }

            // 2. The pattern's +X must map to the facing's right-hand side.
            //
            //    Getting this expectation right is subtle and was initially wrong here:
            //    Direction#getCounterClockWise() is the LEFT-hand neighbour of a facing, not the right.
            //    Facing north (0,0,-1), whose right-hand side is EAST, getCounterClockWise() gives WEST.
            //    The right-hand side is Direction#getClockWise().
            //
            //    This check failing is what surfaced the confusion -- the matcher's own rotation was
            //    already consistent; see the derivation in StructureMatcher#toWorldOffset.
            BlockPos right = StructureMatcher.toWorldOffset(origin, facing, 1, 0, 0).subtract(origin);
            Direction expectedRight = facing.getClockWise();
            if (right.getX() != expectedRight.getStepX() || right.getZ() != expectedRight.getStepZ()) {
                failures.add("right axis for " + facing + " maps to (" + right.getX() + ","
                        + right.getZ() + "), expected (" + expectedRight.getStepX() + ","
                        + expectedRight.getStepZ() + ")");
            }

            // 3. Rotation must be an isometry: a machine must not stretch when the controller turns.
            for (int[] offset : OFFSETS) {
                BlockPos step = StructureMatcher
                        .toWorldOffset(origin, facing, offset[0], offset[1], offset[2]).subtract(origin);
                int expectedSq = offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2];
                int actualSq = step.getX() * step.getX() + step.getY() * step.getY()
                        + step.getZ() * step.getZ();
                if (expectedSq != actualSq) {
                    failures.add("rotation for " + facing + " changed length of ("
                            + offset[0] + "," + offset[1] + "," + offset[2] + ")");
                }
                // 4. Y is never transformed: machines in this mod are symmetric about the vertical axis.
                if (step.getY() != offset[1]) {
                    failures.add("rotation for " + facing + " moved offset ("
                            + offset[0] + "," + offset[1] + "," + offset[2] + ") along Y");
                }
            }
        }

        // 5. The controller's own offset must be invariant under every facing, including the vertical
        //    ones the block never produces — otherwise a part declared at (0,0,0) would compare the
        //    wrong block.
        for (Direction facing : Direction.values()) {
            if (!StructureMatcher.toWorldOffset(origin, facing, 0, 0, 0).equals(origin)) {
                failures.add("zero offset is not invariant for " + facing);
            }
        }

        return failures;
    }

    /** Runs the checks and reports the outcome once. */
    static void verifyAndReport() {
        List<String> failures = run();
        if (failures.isEmpty()) {
            Modularmachinery.LOG.info("[MMCE] matcher geometry self-check passed "
                    + "(4 facings x 7 offsets: forward/right axes, isometry, Y invariance)");
        } else {
            Modularmachinery.LOG.error("[MMCE] matcher geometry self-check FAILED — structures will "
                    + "match incorrectly:");
            failures.forEach(f -> Modularmachinery.LOG.error("[MMCE]   {}", f));
        }
    }
}
