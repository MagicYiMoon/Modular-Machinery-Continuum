/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

/** CraftTweaker wrapper for a machine-declared smart-interface type. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.SmartInterfaceType")
public final class SmartInterfaceTypeBuilder {
    private final hellfirepvp.modularmachinery.common.util.SmartInterfaceType value;

    private SmartInterfaceTypeBuilder(String type, float defaultValue) {
        this.value = new hellfirepvp.modularmachinery.common.util.SmartInterfaceType(type, defaultValue);
    }

    @ZenCodeType.Method
    public static SmartInterfaceTypeBuilder create(String type, float defaultValue) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("smart interface type cannot be blank");
        return new SmartInterfaceTypeBuilder(type, defaultValue);
    }

    @ZenCodeType.Method public SmartInterfaceTypeBuilder setHeaderInfo(String text) {
        value.setHeaderInfo(text); return this;
    }
    @ZenCodeType.Method public SmartInterfaceTypeBuilder setValueInfo(String text) {
        value.setValueInfo(text); return this;
    }
    @ZenCodeType.Method public SmartInterfaceTypeBuilder setFooterInfo(String text) {
        value.setFooterInfo(text); return this;
    }
    @ZenCodeType.Method public SmartInterfaceTypeBuilder setNotEqualMessage(String text) {
        value.setNotEqualMessage(text); return this;
    }
    @ZenCodeType.Method public SmartInterfaceTypeBuilder setJeiTooltip(String text, int argsCount) {
        if (argsCount < 0) throw new IllegalArgumentException("JEI tooltip argument count cannot be negative");
        value.setJeiTooltip(text, argsCount); return this;
    }
    @ZenCodeType.Method public SmartInterfaceTypeBuilder setPriority(int priority) {
        value.setPriority(priority); return this;
    }

    hellfirepvp.modularmachinery.common.util.SmartInterfaceType value() {
        return value;
    }
}
