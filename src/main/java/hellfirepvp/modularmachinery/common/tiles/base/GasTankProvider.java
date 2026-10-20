/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.base;

import java.util.List;

/** Stable tank identities for machine-internal gas reservations; no optional API in this contract. */
public interface GasTankProvider {
    List<? extends GasTankAccess> gasTanks();
}
