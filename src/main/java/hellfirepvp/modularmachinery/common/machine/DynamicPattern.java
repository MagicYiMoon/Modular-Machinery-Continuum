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
