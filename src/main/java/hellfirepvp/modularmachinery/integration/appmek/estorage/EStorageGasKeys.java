/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek.estorage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;

/** The upstream matrix stores gases, not all four forms sharing AppMek's key type. */
public final class EStorageGasKeys {
    private EStorageGasKeys() { }
    public static AEKeyType type() { return MekanismKeyType.TYPE; }
    public static boolean isGas(AEKey key) { return key instanceof MekanismKey chemical && chemical.getForm() == MekanismKey.GAS; }
}
