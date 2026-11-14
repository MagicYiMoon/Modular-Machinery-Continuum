/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A REPEATING STRUCTURE SEGMENT: the `dynamic-patterns` entry of a machine definition.
 *
 * <h2>What it is, in the mod's terms</h2>
 *
 * <p>Upstream's shipped {@code assembly_line.json} declares a pattern named {@code line} with {@code minSize 5},
 * {@code maxSize 15}, a {@code parts} template and a {@code parts-end} cap. The machine is a wall PLUS a variable-length
 * line of sections laid out from that template, and upstream refuses to form it when the line is wrong.
 *
 * <p>The port read none of it, so the machine formed from the wall alone. That is the defect this class exists to
 * close, and it is closed in stages (PLAN section 320.4): this class is the DATA, and the matcher integration is a
 * separate step.
 *
 * <h2>The template reuses {@link MachinePattern}</h2>
 *
 * <p>Upstream holds {@code TaggedPositionBlockArray}s here. The port already has {@link MachinePattern} and its
 * {@link MachinePattern.Part}, whose {@code elements} field is the same string the machine {@code parts} array uses —
 * so the template can be parsed with the SAME element reader, the same variable resolution and the same
 * {@code BlockStateResolver} that the static pattern already goes through. A second representation would be a second
 * set of rules for what an element string means.
 *
 * <h2>What this class does NOT do yet</h2>
 *
 * <p><b>It does not match anything.</b> There is no instantiate-for-a-size, no {@code matchesSize} and no
 * {@code addPatternToBlockArray}. Upstream has all three in this class (395 lines), and they are step 2 and step 3 of
 * section 320.4. Writing them before the data is landed and asserted is the mistake this session has recorded four
 * times.
 */
public final class DynamicPattern {

    private final String name;

    /**
     * The directions this pattern may run in.
     *
     * <p>A SET rather than one direction: upstream reads an array, and a pattern may be valid on more than one face.
     * Empty is not a legal state — the parse requires the key — but an empty set here means "not declared", so a
     * caller can tell the difference between a pattern with no faces and one whose faces failed to resolve.
     */
    private final Set<Direction> faces = EnumSet.noneOf(Direction.class);

    /**
     * The smallest and largest number of sections this pattern accepts.
     *
     * <p>Defaults from upstream's second constructor ({@code minSize 0}, {@code maxSize 1}), used when a pattern is
     * built by name alone. A parsed pattern always has them from the JSON.
     */
    private int minSize = 0;
    private int maxSize = 1;

    /** Where the section starts, and how the structure grows per section. */
    private BlockPos structureSizeOffsetStart = BlockPos.ZERO;
    private BlockPos structureSizeOffset = BlockPos.ZERO;

    /** The section template. */
    private MachinePattern pattern = new MachinePattern();

    /** The cap, or null when the JSON declared no {@code parts-end}. */
    private MachinePattern patternEnd = null;

    public DynamicPattern(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** @return the faces this pattern may run in, unmodifiable */
    public Set<Direction> getFaces() {
        return Collections.unmodifiableSet(faces);
    }

    public void addFace(Direction face) {
        if (face != null) {
            faces.add(face);
        }
    }

    public int getMinSize() {
        return minSize;
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public BlockPos getStructureSizeOffsetStart() {
        return structureSizeOffsetStart;
    }

    public void setStructureSizeOffsetStart(BlockPos structureSizeOffsetStart) {
        if (structureSizeOffsetStart != null) {
            this.structureSizeOffsetStart = structureSizeOffsetStart;
        }
    }

    public BlockPos getStructureSizeOffset() {
        return structureSizeOffset;
    }

    public void setStructureSizeOffset(BlockPos structureSizeOffset) {
        if (structureSizeOffset != null) {
            this.structureSizeOffset = structureSizeOffset;
        }
    }

    /** @return the section template, never null */
    public MachinePattern getPattern() {
        return pattern;
    }

    public void setPattern(MachinePattern pattern) {
        if (pattern != null) {
            this.pattern = pattern;
        }
    }

    /** @return the cap, or {@code null} when the JSON declared no {@code parts-end} */
    public MachinePattern getPatternEnd() {
        return patternEnd;
    }

    public void setPatternEnd(MachinePattern patternEnd) {
        this.patternEnd = patternEnd;
    }

    /**
     * Whether this pattern accepts a given number of sections.
     *
     * <p>Upstream checks {@code size >= minSize} and {@code size <= maxSize} in two different methods, so this is a
     * single predicate over both bounds rather than a copy of either. It is the one piece of BEHAVIOUR that belongs
     * with the data, because it is the definition of the bounds rather than a use of them.
     */
    public boolean accepts(int size) {
        return size >= minSize && size <= maxSize;
    }

    /**
     * Builds the concrete structure for a given number of sections (PLAN section 326).
     *
     * <p>Upstream's {@code addPatternToBlockArray} (DynamicPattern:165-211) is the source of the layout, and its loop
     * is exactly this:
     *
     * <pre>
     *   offset = structureSizeOffsetStart;
     *   for (i = 0; i &lt; size; i++) {
     *       if (i &gt; 0) offset += structureSizeOffset;
     *       place the `parts` template at offset;
     *   }
     *   if (parts-end) place it at offset + structureSizeOffset;      // ONE step past the last section
     * </pre>
     *
     * <p>Two details of it are easy to get wrong and are therefore stated: the FIRST section sits at
     * {@code structureSizeOffsetStart} with no step applied, and the CAP is placed one step beyond the last section
     * rather than on top of it.
     *
     * <p><b>This does not check anything.</b> It builds the shape a size implies; whether the world matches that
     * shape is the matcher's business, and that is step 3 of section 320.4.
     *
     * @param size the number of sections, which the caller is expected to have checked with {@link #accepts(int)}
     * @return a pattern containing every section and the cap, with the same element strings as the templates
     */
    public MachinePattern instantiate(int size) {
        MachinePattern built = new MachinePattern();
        BlockPos offset = structureSizeOffsetStart;

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                offset = offset.offset(structureSizeOffset.getX(), structureSizeOffset.getY(),
                        structureSizeOffset.getZ());
            }
            copyInto(built, pattern, offset);
        }

        if (patternEnd != null) {
            BlockPos endOffset = offset.offset(structureSizeOffset.getX(), structureSizeOffset.getY(),
                    structureSizeOffset.getZ());
            copyInto(built, patternEnd, endOffset);
        }

        return built;
    }

    /**
     * Copies THIS PATTERN'S SECTIONS AND CAP INTO AN EXISTING PATTERN, OVERWRITING ITS OWN CELLS (PLAN §750).
     *
     * <p><b>THE SAME LOOP AS {@link #instantiate(int)}, WRITING INTO A CALLER'S PATTERN INSTEAD OF A FRESH ONE.</b>
     * The offsets are the identical expressions, so a change to the layout cannot move one and not the other. This
     * is the merge half of upstream's {@code addPatternToBlockArray}: upstream receives the machine's already-matched
     * pattern and writes each section into it with {@code BlockArray.addBlock}, which replaces whatever the machine
     * declared at that offset (see {@link MachinePattern#addPartReplacing}).
     *
     * <p><b>WHY A MERGE IS NEEDED AT ALL rather than using {@code instantiate} alone.</b> {@code instantiate}
     * returns the SECTIONS ONLY, and the forming machine also has its own declared parts — for the shipped
     * {@code assembly_line}, the 8-part wall the controller stands in. The pattern the engine binds components from
     * and resolves the machine's component positions out of must contain BOTH, which is exactly the pair upstream's
     * {@code foundPattern} holds after formation ({@code TileMultiblockMachineController} U658-U665).
     *
     * @param target the pattern to merge into, typically the machine's declared pattern copied
     * @param size   the number of sections; the caller has checked it with {@link #accepts(int)}
     */
    public void mergeSectionsInto(MachinePattern target, int size) {
        BlockPos offset = structureSizeOffsetStart;

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                offset = offset.offset(structureSizeOffset.getX(), structureSizeOffset.getY(),
                        structureSizeOffset.getZ());
            }
            copyIntoReplacing(target, pattern, offset);
        }

        if (patternEnd != null) {
            BlockPos endOffset = offset.offset(structureSizeOffset.getX(), structureSizeOffset.getY(),
                    structureSizeOffset.getZ());
            copyIntoReplacing(target, patternEnd, endOffset);
        }
    }

    /** Copies a template's parts into a pattern, each shifted by an offset. */
    private static void copyInto(MachinePattern target, MachinePattern template, BlockPos offset) {
        for (MachinePattern.Part part : template.getParts()) {
            target.addPart(new MachinePattern.Part(
                    part.x() + offset.getX(),
                    part.y() + offset.getY(),
                    part.z() + offset.getZ(),
                    part.elements(), part.selectorTag(), part.structureNbt()));
        }
    }

    /** {@link #copyInto}, except a part displaces the target's own part at the same offset (upstream's addBlock). */
    private static void copyIntoReplacing(MachinePattern target, MachinePattern template, BlockPos offset) {
        for (MachinePattern.Part part : template.getParts()) {
            target.addPartReplacing(new MachinePattern.Part(
                    part.x() + offset.getX(),
                    part.y() + offset.getY(),
                    part.z() + offset.getZ(),
                    part.elements(), part.selectorTag(), part.structureNbt()));
        }
    }

    /**
     * Walks the line in the WORLD and returns how many sections it found (PLAN sections 328-330).
     *
     * <p>This is upstream's {@code matchesSize} (DynamicPattern:125-163), whose four rules are:
     *
     * <ol>
     *   <li>Walk outward from the start offset, one step per round, counting sections that MATCH the template.</li>
     *   <li>The FIRST non-matching section STOPS the walk. It is not an error and not a zero.</li>
     *   <li>The cap must then match either AT the stopping offset, or one step BACK -- and in the second case the
     *       count is DECREMENTED. Upstream calls this "prevent overlapping structures": a line whose cap sits on top
     *       of its last section is one section shorter than it looked.</li>
     *   <li>More sections than {@code maxSize} returns 0: the whole pattern fails rather than truncating.</li>
     * </ol>
     *
     * <p>The caller compares the result to {@link #getMinSize()}, which is what makes rule 2 meaningful -- stopping
     * early is a smaller line, not a failure, until it falls under the minimum.
     *
     * <p><b>The offsets are applied in PATTERN SPACE and the rotation happens once, inside
     * {@code StructureMatcher.toWorldOffset}.</b> That method is the rotation PLAN section 309 corrected to match
     * upstream, and this walk deliberately goes through it rather than rotating anything itself. A pre-rotated offset
     * handed to an already-rotating matcher would be section 309's defect with its two halves swapped.
     *
     * @return the number of matching sections, or 0 when there is none or the count exceeds {@code maxSize}
     */
    public int matchSize(net.minecraft.world.level.Level level, BlockPos controllerPos, Direction facing,
                         MachineVariables variables, BlockStateResolver resolver) {
        BlockPos offset = structureSizeOffsetStart;
        int size = 0;

        for (int round = 0; ; round++) {
            if (round > 0) {
                offset = offset.offset(structureSizeOffset.getX(), structureSizeOffset.getY(),
                        structureSizeOffset.getZ());
            }

            if (matchesAt(level, controllerPos, facing, offset, pattern, variables, resolver)) {
                size++;
                if (size > maxSize) {
                    return 0;       // rule 4: over the bound, the whole pattern fails
                }
                continue;
            }

            // Rule 2 stops here. Rule 3 asks about the cap, at this offset and then one step back.
            if (patternEnd != null) {
                if (!matchesAt(level, controllerPos, facing, offset, patternEnd, variables, resolver)) {
                    BlockPos back = offset.offset(-structureSizeOffset.getX(), -structureSizeOffset.getY(),
                            -structureSizeOffset.getZ());
                    if (!matchesAt(level, controllerPos, facing, back, patternEnd, variables, resolver)) {
                        return 0;
                    }
                    size--;
                }
            }
            break;
        }

        return Math.max(size, 0);
    }

    /** Tests one template against the world at one pattern-space offset, through the port's existing matcher. */
    private static boolean matchesAt(net.minecraft.world.level.Level level, BlockPos controllerPos, Direction facing,
                                     BlockPos offset, MachinePattern template,
                                     MachineVariables variables, BlockStateResolver resolver) {
        if (template.isEmpty()) {
            return false;
        }
        MachinePattern shifted = new MachinePattern();
        copyInto(shifted, template, offset);
        return new StructureMatcher().match(level, controllerPos, facing, shifted, variables, resolver).matched();
    }

    @Override
    public String toString() {
        return "DynamicPattern[" + name
                + " faces=" + faces
                + " size=" + minSize + ".." + maxSize
                + " parts=" + pattern.size()
                + " end=" + (patternEnd == null ? "none" : patternEnd.size())
                + "]";
    }
}
