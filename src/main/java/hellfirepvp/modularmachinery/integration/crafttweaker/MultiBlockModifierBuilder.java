/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.core.BlockPos;
import org.openzen.zencode.java.ZenCodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftTweaker 14/ZenCode facade for the source mod's MultiBlockModifierBuilder.
 *
 * <p>The old builder accepted a 1.12 {@code BlockArray}; that type has no 1.20.1 equivalent.
 * This facade keeps the same fluent builder shape while expressing the array as explicit relative
 * cells.  {@link #addToMachine(String)} is the 1.20.1 registration step: script execution starts
 * with the machine reload hook, so the newly loaded definition is still mutable when the script
 * adds its runtime condition.</p>
 */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MultiBlockModifierBuilder")
public final class MultiBlockModifierBuilder {
    private final String name;
    private final List<MultiBlockModifierReplacement.Cell> cells = new ArrayList<>();
    private final List<RecipeModifier> modifiers = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();

    private MultiBlockModifierBuilder(String name) {
        this.name = name;
    }

    @ZenCodeType.Method
    public static MultiBlockModifierBuilder newBuilder() {
        return new MultiBlockModifierBuilder("multiblock_modifier");
    }

    @ZenCodeType.Method
    public static MultiBlockModifierBuilder newBuilder(String name) {
        return new MultiBlockModifierBuilder(name);
    }

    /** Adds a required block at a controller-relative coordinate. */
    @ZenCodeType.Method
    public MultiBlockModifierBuilder addCell(String blockId, int x, int y, int z) {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("blockId cannot be empty");
        }
        cells.add(new MultiBlockModifierReplacement.Cell(new BlockPos(x, y, z), blockId));
        return this;
    }

    /** Adds a recipe scaling rule using the same operation numbering as the source mod (0 add, 1 multiply). */
    @ZenCodeType.Method
    public MultiBlockModifierBuilder addModifier(String target, String io, int operation, float multiplier) {
        return addModifier(target, io, operation, multiplier, false);
    }

    @ZenCodeType.Method
    public MultiBlockModifierBuilder addModifier(String target, String io, int operation,
                                                  float multiplier, boolean affectChance) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("modifier target cannot be empty");
        }
        IOType ioType = switch (io == null ? "" : io.toLowerCase(java.util.Locale.ROOT)) {
            case RecipeModifier.IO_INPUT -> IOType.INPUT;
            case RecipeModifier.IO_OUTPUT -> IOType.OUTPUT;
            default -> throw new IllegalArgumentException("modifier io must be input or output");
        };
        if (operation < RecipeModifier.OPERATION_ADD || operation > RecipeModifier.OPERATION_MULTIPLY) {
            throw new IllegalArgumentException("modifier operation must be 0 (add) or 1 (multiply)");
        }
        modifiers.add(new RecipeModifier(target, ioType, multiplier, operation, affectChance));
        return this;
    }

    @ZenCodeType.Method
    public MultiBlockModifierBuilder addDescription(String description) {
        if (description != null && !description.isBlank()) descriptions.add(description);
        return this;
    }

    /** Builds a detached replacement, useful to Java integrations and tests. */
    @ZenCodeType.Method
    public MultiBlockModifierReplacement build() {
        if (cells.isEmpty()) throw new IllegalStateException("a multiblock modifier needs at least one cell");
        if (modifiers.isEmpty()) throw new IllegalStateException("a multiblock modifier needs at least one modifier");
        return new MultiBlockModifierReplacement(name, cells, modifiers, descriptions);
    }

    /** Builds and attaches this condition to the live machine definition loaded by CraftTweaker. */
    @ZenCodeType.Method
    public boolean addToMachine(String machineName) {
        DynamicMachine machine = MachineReloadListener.getLoader().findByName(machineName);
        if (machine == null) {
            throw new IllegalArgumentException("unknown Modular Machinery machine: " + machineName);
        }
        machine.addMultiBlockModifierReplacement(build());
        return true;
    }
}
