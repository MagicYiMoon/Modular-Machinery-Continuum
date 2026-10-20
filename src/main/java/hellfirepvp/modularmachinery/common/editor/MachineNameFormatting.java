/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

/** Converts typeable name codes to Minecraft's legacy formatting without changing other ampersands. */
public final class MachineNameFormatting {
    private MachineNameFormatting() {}

    public static String display(String name) {
        return name.replaceAll("(?i)&([0-9a-fk-or])", "\u00a7$1");
    }

    /** EditBox cannot type section signs, so expose existing formatting as editable ampersand codes. */
    public static String editable(String name) {
        return name.replaceAll("(?i)\u00a7([0-9a-fk-or])", "&$1");
    }
}
