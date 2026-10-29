/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-09.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/package hellfirepvp.modularmachinery.common.modifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of a machine's {@code modifiers} array: a STRUCTURE CONDITION plus the rules it activates.
 *
 * <pre>
 * "modifiers": [ { "elements": "modularmachinery:blockcasing@1", "x": 0, "y": 1, "z": 0,
 *                  "description": "Replace block above controller with machine vent for double item output!",
 *                  "modifier": { "target": "modularmachinery:item", "io": "output",
 *                                "operation": 1, "multiplier": 2.0 } } ]
 * </pre>
 *
 * <h2>Why this is separate from {@link RecipeModifier}</h2>
 *
 * <p>The {@code modifier} object says WHAT to scale. <b>The {@code elements}/{@code x}/{@code y}/{@code z} say
 * WHEN it applies</b> -- place that block at that offset from the controller, and the rules become active. That
 * is upstream's {@code AbstractModifierReplacement} / {@code SingleBlockModifierReplacement}, and it is why the
 * feature is a STRUCTURE-CONDITIONAL behaviour rather than a tuning knob.
 *
 * <p><b>It exists before the test that consumes it, on purpose.</b> A {@link RecipeModifier} cannot be applied
 * without knowing whether it is ACTIVE, and applying every declared modifier unconditionally would make the vent
 * bonus apply whether or not the vent is there -- a new wrong behaviour, which is worse than the current absent
 * one. So the condition is parsed and held, and the test is the next step.
 */
public final class ModifierReplacement {

    private static final java.util.concurrent.atomic.AtomicInteger DEFAULT_NAME_COUNTER = new java.util.concurrent.atomic.AtomicInteger();
    private final String name = nextDefaultName();

    public static String nextDefaultName() { return "ReplacementModifier - " + DEFAULT_NAME_COUNTER.getAndIncrement(); }
    public String getName() { return name; }

    private final String blockId;
    private final int x;
    private final int y;
    private final int z;
    private final String description;
    private final List<RecipeModifier> modifiers;

    private ModifierReplacement(String blockId, int x, int y, int z, String description,
                                List<RecipeModifier> modifiers) {
        this.blockId = blockId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.description = description;
        this.modifiers = List.copyOf(modifiers);
    }

    /**
     * Reads one entry of the array.
     *
     * @return {@code null} when the entry has no structure condition or no rules -- either makes it
     *         unapplicable, and a half-understood modifier is worse than none
     */
    public static ModifierReplacement deserialize(JsonObject entry) {
        JsonElement elements = entry.get("elements");
        if (elements == null || elements.isJsonNull()) {
            return null;
        }
        JsonElement rule = entry.get("modifier");
        if (rule == null || !rule.isJsonObject()) {
            return null;
        }
        RecipeModifier parsed = RecipeModifier.deserialize(rule.getAsJsonObject());
        if (parsed == null) {
            return null;
        }
        List<RecipeModifier> rules = new ArrayList<>();
        rules.add(parsed);
        if (entry.has("modifiers") && entry.get("modifiers").isJsonArray()) {
            for (JsonElement extra : entry.getAsJsonArray("modifiers")) {
                if (extra.isJsonObject()) {
                    RecipeModifier more = RecipeModifier.deserialize(extra.getAsJsonObject());
                    if (more != null) {
                        rules.add(more);
                    }
                }
            }
        }
        JsonElement description = entry.get("description");
        return new ModifierReplacement(
                elements.getAsString(),
                hellfirepvp.modularmachinery.common.machine.PatternCoordinates.scalar(entry, "x"),
                hellfirepvp.modularmachinery.common.machine.PatternCoordinates.scalar(entry, "y"),
                hellfirepvp.modularmachinery.common.machine.PatternCoordinates.scalar(entry, "z"),
                (description == null || description.isJsonNull()) ? null : description.getAsString(),
                rules);
    }

    /** Expands independent single-block conditions; the controller position is excluded as upstream does. */
    public static List<ModifierReplacement> deserializeAll(JsonObject entry) {
        List<ModifierReplacement> replacements = new ArrayList<>();
        // Reuse one scalar view: copying the original coordinate arrays per position is quadratic.
        JsonObject located = entry.deepCopy();
        for (net.minecraft.core.BlockPos pos : hellfirepvp.modularmachinery.common.machine.PatternCoordinates.expand(entry)) {
            if (pos.equals(net.minecraft.core.BlockPos.ZERO)) continue;
            located.addProperty("x", pos.getX());
            located.addProperty("y", pos.getY());
            located.addProperty("z", pos.getZ());
            ModifierReplacement replacement = deserialize(located);
            if (replacement != null) replacements.add(replacement);
        }
        return replacements;
    }

    public String getBlockId() {
        return blockId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /** @return the line the JSON shows the player, e.g. "Replace block above controller with machine vent" */
    public String getDescription() {
        return description;
    }

    /** @return the rules this condition activates */
    public List<RecipeModifier> getModifiers() {
        return modifiers;
    }

    /**
     * Whether this condition currently HOLDS on a formed machine.
     *
     * <p>Upstream's {@code AbstractModifierReplacement} subclasses do this: MultiBlockModifierReplacement has
     * {@code matches(controller)} over a BlockArray, and SingleBlockModifierReplacement holds a BlockInformation
     * plus a BlockPos. This is the single-block case, which is what the shipped machinery JSON uses.
     *
     * <p>The offset is rotated by the machine's facing with the SAME helper the structure matcher uses
     * ({@code StructureMatcher.toWorldOffset}), so a modifier cannot disagree with the pattern machinery about
     * where "one block above the controller" is.
     *
     * <p>A bare id accepts every state of the block; explicit metadata pins a state, rotated with
     * the coordinates as in upstream's updateModifiers / BlockInformation.copyRotateYCCW.
     *
     * @return false when the block cannot be resolved -- an unresolvable condition must not grant its bonus
     */
    public boolean matches(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos controllerPos,
                           net.minecraft.core.Direction facing) {
        if (level == null || controllerPos == null) {
            return false;
        }
        net.minecraft.world.level.block.state.BlockState expected =
                hellfirepvp.modularmachinery.common.machine.BlockStateShim.resolve(blockId);
        if (expected == null) {
            return false;
        }
        net.minecraft.core.BlockPos world =
                hellfirepvp.modularmachinery.common.machine.StructureMatcher.toWorldOffset(
                        controllerPos, facing, x, y, z);
        if (!level.isLoaded(world)) return false;
        // EXACT when the element names metadata, BLOCK-ONLY when it does not -- which is exactly what
         
        //
        //   modularmachinery:blockcasing@4   "explicit metadata, so exactly casing=reinforced.
        //                                     A plain casing must NOT satisfy it."
        //   modularmachinery:blockcasing     "no metadata, so any casing variant will do."
        //
        // The first version of this method compared getBlock() in BOTH cases, which is wrong for the first: the
        // alloy furnace condition is `blockcasing@1`, and a formed machine already has a casing above its
        // controller -- so a block-only test would read as TRUE almost always and re-create precisely the
        // "applies whether or not the vent is there" defect the ordering in sections 234-236 existed to avoid.
        net.minecraft.world.level.block.state.BlockState actual = level.getBlockState(world);
        if (blockId.indexOf('@') >= 0) {
            return actual.equals(hellfirepvp.modularmachinery.common.machine.StructureMatcher.rotateState(expected, facing));
        }
        return actual.getBlock() == expected.getBlock();
    }
}
