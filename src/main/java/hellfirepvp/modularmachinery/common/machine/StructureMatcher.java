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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether the blocks around a controller form a given machine.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream kept the pattern as a dense array of metadata ints
 * ({@code BlockArray}) and compared {@code block + meta} at each offset, with a
 * {@code BlockArrayCache} memoising results. On 1.20.1 three things change:
 *
 * <ol>
 *   <li><b>There is no metadata</b> — a part is matched against a {@link BlockState}. The
 *       {@link BlockStateShim} supplied the translation from the 1.12.2 spelling.</li>
 *   <li><b>The pattern is sparse</b>, so matching touches only the declared parts rather than the
 *       whole bounding volume. A 3×5×3 shell is 32 comparisons, not 45.</li>
 *   <li><b>Rotation is applied at match time</b> rather than by pre-rotating the pattern. The
 *       controller's {@code facing} decides the mapping from pattern offsets to world offsets, so a
 *       pattern that matches facing north also matches facing south without a second copy.</li>
 * </ol>
 *
 * <p><b>Design rule: report, do not substitute.</b> A part whose element cannot be resolved is
 * recorded as such and the machine does not form. Treating an unresolvable part as "matches anything"
 * would let a machine assemble out of the wrong blocks — the worst possible failure mode, because it
 * is silent and only shows up as mysteriously wrong recipes later.
 */
public final class StructureMatcher {

    /**
     * Outcome of one match attempt.
     *
     * @param matched      true when every declared part matched
     * @param partsMatched how many parts matched (equal to the pattern size on success)
     * @param mismatches   the parts that did not match, at most {@link #MAX_REPORTED_MISMATCHES}
     */
    public record Result(boolean matched, int partsMatched, List<String> mismatches) {

        public static Result success(int parts) {
            return new Result(true, parts, List.of());
        }
    }

    /** Cap on reported mismatches, so a wrong machine does not produce a wall of text. */
    private static final int MAX_REPORTED_MISMATCHES = 8;

    /**
     * Matches {@code pattern} against the world with the controller at {@code controllerPos}.
     *
     * @param level         the level to read; must be server-side for the result to be authoritative
     * @param controllerPos world position of the controller block
     * @param facing        the controller's facing, used to map pattern offsets to world offsets
     * @param pattern       the definition's pattern
     * @param variables     variable table for expanding names such as {@code casings_decorative}
     * @param resolver      reports elements it cannot translate
     * @return the result, never {@code null}
     */
    public Result match(Level level, BlockPos controllerPos, Direction facing, MachinePattern pattern,
                        MachineVariables variables, BlockStateResolver resolver) {
        return match(level, controllerPos, facing, pattern, variables, resolver, java.util.List.of());
    }

    










 
    public Result match(Level level, BlockPos controllerPos, Direction facing, MachinePattern pattern,
                        MachineVariables variables, BlockStateResolver resolver,
                        java.util.List<hellfirepvp.modularmachinery.common.modifier.ModifierReplacement> replacements) {
        if (pattern.isEmpty()) {
            // An empty pattern is not a machine. Reporting success here would let every controller
            // form instantly with no structure at all.
            return new Result(false, 0, List.of("machine defines no parts"));
        }

        // The controller's own block is part of the structure in some definitions; the declared parts
        // are offsets, so the controller block itself is not compared unless declared at (0,0,0).
        List<String> mismatches = new ArrayList<>();
        int matched = 0;

        for (MachinePattern.Part part : pattern.getParts()) {
            BlockPos worldPos = toWorldOffset(controllerPos, facing, part.x(), part.y(), part.z());

            // 1. Resolve the part's accepted states. A part may name a variable, in which case ANY of
            //    the bound elements may satisfy it — that is how one definition accepts every energy
            //    hatch tier. A bare block id accepts any state of that block; explicit metadata pins
            //    one variant. See BlockStateResolver. 1b (a modifier replacement at this position) is
             
            //    in-world preview asks the identical question.
            List<BlockState> accepted = acceptedStates(part, variables, resolver, replacements);

            if (accepted.isEmpty()) {
                if (mismatches.size() < MAX_REPORTED_MISMATCHES) {
                    mismatches.add(worldPos.toShortString() + " "
                            + part.elements() + " — unresolvable element");
                }
                continue;
            }

            // 2. Compare. A chunk that is not loaded must count as a mismatch rather than being read
            //    as air: loading chunks to test a structure would be a performance and correctness
            //    trap, and an unloaded part genuinely is not verifiable.
            if (!level.isLoaded(worldPos)) {
                if (mismatches.size() < MAX_REPORTED_MISMATCHES) {
                    mismatches.add(worldPos.toShortString() + " chunk not loaded");
                }
                continue;
            }

            BlockState actual = level.getBlockState(worldPos);
            if (matchesAny(actual, accepted, facing)) {
                if (matchesPartNbt(level, worldPos, part)) {
                    matched++;
                } else if (mismatches.size() < MAX_REPORTED_MISMATCHES) {
                    mismatches.add(worldPos.toShortString()
                            + " block matches but block entity NBT does not match");
                }
            } else if (mismatches.size() < MAX_REPORTED_MISMATCHES) {
                // Report the expected block ids, not the raw states: a pack author reads ids.
                //
                // But when the BLOCK matches and only the STATE differs, ids alone produce the useless
                // message "expected minecraft:iron_bars but found minecraft:iron_bars" -- which reads
                // like a matcher bug and sent this port looking in the wrong place twice. Blocks with
                // state properties (iron bars and their connections, for one) genuinely mismatch this
                // way, so the states are included whenever the ids agree.
                String expected = describe(accepted);
                var actualId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(actual.getBlock());
                boolean sameBlock = accepted.stream()
                        .anyMatch(state -> state.getBlock() == actual.getBlock());
                mismatches.add(worldPos.toShortString() + " expected "
                        + expected + " but found "
                        + (sameBlock ? actual.toString() + " — SAME BLOCK, DIFFERENT STATE" : actualId));
            }
        }

        if (!mismatches.isEmpty()) {
            return new Result(false, matched, mismatches);
        }
        return Result.success(matched);
    }

    



















 
    public static List<BlockState> acceptedStates(MachinePattern.Part part, MachineVariables variables,
                                                  BlockStateResolver resolver,
                                                  List<hellfirepvp.modularmachinery.common.modifier.ModifierReplacement>
                                                          replacements) {
        List<BlockState> accepted = new ArrayList<>();
        if (part == null || variables == null || resolver == null) {
            return accepted;
        }
        // A part may name a variable, in which case ANY of the bound elements may satisfy it -- that is
        // how one definition accepts every energy hatch tier. A bare block id accepts any state of that
        // block; explicit metadata pins one variant. See BlockStateResolver.
        for (String bound : variables.resolve(part.elements())) {
            accepted.addAll(resolver.resolveAccepted(bound));
        }

        // A modifier replacement at THIS position is an ALTERNATIVE, not an override (section 239). The
        // offset is compared in PATTERN SPACE, which is where both sides are authored.
        if (replacements != null) {
            for (hellfirepvp.modularmachinery.common.modifier.ModifierReplacement replacement : replacements) {
                if (replacement.getX() == part.x() && replacement.getY() == part.y()
                        && replacement.getZ() == part.z()) {
                    accepted.addAll(resolver.resolveAccepted(replacement.getBlockId()));
                }
            }
        }
        return accepted;
    }

    /**
     * @return true when {@code actual} equals any accepted state
     *
     * <p>Equality rather than {@code is()} on purpose: a definition that pins
     * {@code blockcasing@2} (firebox) must not accept a plain casing, and
     * {@code BlockState#is} would accept any state of the same block.
     */
    public static boolean matchesAny(BlockState actual, List<BlockState> accepted) {
        return matchesAny(actual, accepted, Direction.NORTH);
    }

    /** Tests pattern-space candidates after applying the controller's horizontal rotation. */
    public static boolean matchesAny(BlockState actual, List<BlockState> accepted, Direction facing) {
        for (BlockState state : accepted) {
            if (rotateState(state, facing).equals(actual)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Source IBlockStateDescriptor.copyRotateYCCW rotates states along with BlockArray coordinates.
     * Use the block's rotation API so axes, connections, rail shapes and mod-specific properties work
     * too; changing a property named "facing" alone would not reproduce that behavior.
     * NORTH is the authored orientation, as in {@link #toWorldOffset}.
     */
    public static BlockState rotateState(BlockState state, Direction facing) {
        if (state == null) return null;
        Rotation rotation = switch (facing) {
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return rotation == Rotation.NONE ? state : state.rotate(rotation);
    }

    /** Matches the optional block-entity NBT constraint attached to a structure part. */
    public static boolean matchesPartNbt(Level level, BlockPos worldPos, MachinePattern.Part part) {
        if (part == null || part.nbtPattern() == null) return true;
        if (level == null || worldPos == null) return false;
        net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(worldPos);
        // A declared NBT constraint always requires a real block entity, including for an empty object.
        if (entity == null) return false;
        return hellfirepvp.modularmachinery.common.util.nbt.NbtMatchingHelper.matchNbtCompound(
                part.nbtPattern(), entity.saveWithoutMetadata());
    }

    private static String describe(List<BlockState> accepted) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accepted.size() && i < 4; i++) {
            if (i > 0) {
                sb.append(" or ");
            }
            sb.append(net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getKey(accepted.get(i).getBlock()));
        }
        if (accepted.size() > 4) {
            sb.append(" (… ").append(accepted.size() - 4).append(" more)");
        }
        return sb.toString();
    }

    /**
     * Transforms a pattern-space offset into a world position.
     *
     * <p><b>Public since §187</b>, because the in-world structure preview has to place its ghosts at exactly the
     * positions the matcher would test. It was package-private while the matcher was the only caller; making it
     * public is the alternative to a second implementation of the same rotation, which is the kind of duplication
     * that produces a preview which does not line up with the machine it previews.
     */
    public static BlockPos toWorldOffset(BlockPos controllerPos, Direction facing, int x, int y, int z) {
         
        //
        // Upstream (MiscUtils:73-75):
        //
        //     public static BlockPos rotateYCCW(BlockPos pos) {
        //         return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        //     }
        //
        // and BlockArrayCache:78-80 builds its cache starting at NORTH with NO rotation, so NORTH is the
        // IDENTITY and each further facing is one more rotateYCCW. EnumFacing.rotateYCCW walks
        // NORTH -> WEST -> SOUTH -> EAST, which gives:
        //
        //     NORTH (x, y, z)   WEST (z, y, -x)   SOUTH (-x, y, -z)   EAST (-z, y, x)
        //
        // The port had NORTH -> (x, y, -z) and three more derived from it. That is a REFLECTION, not a
        // rotation: every shipped machine was matched on the MIRROR side of its controller, and no controller
        // rotation could compensate because all four facings were reflected.
        //
        // Found by an audit against the recovered upstream source, after this session had already used the
        // wrong mapping in the matcher, in the preview renderer, and in the self-test -- all four consistent
        // with each other, which is why the self-test's geometry check could not see it.
        return switch (facing) {
            case NORTH -> controllerPos.offset(x, y, z);
            case WEST -> controllerPos.offset(z, y, -x);
            case SOUTH -> controllerPos.offset(-x, y, -z);
            case EAST -> controllerPos.offset(-z, y, x);
            // A vertically-facing controller is not produced by BlockController, but falling back to
            // the unrotated mapping is the safe reading rather than throwing mid-match.
            default -> controllerPos.offset(x, y, z);
        };
    }
}
