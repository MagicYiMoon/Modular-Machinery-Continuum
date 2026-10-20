/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.base;

import net.minecraft.nbt.CompoundTag;

/** Small optional-gas contract kept free of Mekanism classes. */
public interface GasTankAccess {
    boolean hasGas();
    long getGasAmount();
    long getGasCapacity();
    CompoundTag getGasTag();
    void setGasTag(CompoundTag tag);
    void clearGas();
    boolean canAcceptGas();
    boolean canExtractGas();
}
