/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import org.openzen.zencode.java.ZenCodeType;

/** CraftTweaker 14 port of the source RecipeModifierBuilder. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.RecipeModifierBuilder")
public final class RecipeModifierBuilder {
    private String target = "";
    private String io = "";
    private float value;
    private int operation;
    private boolean affectChance;

    @ZenCodeType.Method
    public static RecipeModifierBuilder newBuilder() { return new RecipeModifierBuilder(); }

    @ZenCodeType.Method
    public static RecipeModifierBuilder create(String target, String io, float value, int operation, boolean chance) {
        return newBuilder().setRequirementType(target).setIOType(io).setValue(value)
                .setOperation(operation).isAffectChance(chance);
    }

    @ZenCodeType.Method public RecipeModifierBuilder setRequirementType(String value) { target = value; return this; }
    @ZenCodeType.Method public RecipeModifierBuilder setIOType(String value) { io = value; return this; }
    @ZenCodeType.Method public RecipeModifierBuilder setValue(float value) { this.value = value; return this; }
    @ZenCodeType.Method public RecipeModifierBuilder setOperation(int value) { operation = value; return this; }
    @ZenCodeType.Method public RecipeModifierBuilder isAffectChance(boolean value) { affectChance = value; return this; }

    @ZenCodeType.Method
    public RecipeModifier build() {
        if (target == null || target.isBlank()) throw new IllegalArgumentException("modifier target cannot be empty");
        IOType direction = switch (io == null ? "" : io.toLowerCase(java.util.Locale.ROOT)) {
            case RecipeModifier.IO_INPUT -> IOType.INPUT;
            case RecipeModifier.IO_OUTPUT -> IOType.OUTPUT;
            default -> throw new IllegalArgumentException("modifier io must be input or output");
        };
        if (operation < RecipeModifier.OPERATION_ADD || operation > RecipeModifier.OPERATION_MULTIPLY)
            throw new IllegalArgumentException("modifier operation must be 0 or 1");
        if (!Float.isFinite(value)) throw new IllegalArgumentException("modifier value must be finite");
        return new RecipeModifier(target, direction, value, operation, affectChance);
    }
}
