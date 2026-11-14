/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

/** Constant-time EC identity lookup for AE's storage insertion loop. Null for native CPUs. */
public interface ECalculatorLogicBridge {
    ECalculatorCpuHost mmc$ecLogicHost();
}
