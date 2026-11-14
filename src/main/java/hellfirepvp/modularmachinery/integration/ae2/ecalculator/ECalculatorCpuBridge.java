/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

/** Implemented only by the AE CPU mixin. Native CPUs retain a null host. */
public interface ECalculatorCpuBridge {
    ECalculatorCpuHost mmc$ecHost();
    void mmc$ecHost(ECalculatorCpuHost host);
}
