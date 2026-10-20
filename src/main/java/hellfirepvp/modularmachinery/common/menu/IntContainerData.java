/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import net.minecraft.world.inventory.ContainerData;

/** Transports each logical int in two words through vanilla's signed-short menu data packets. */
public final class IntContainerData implements ContainerData {
    private final ContainerData values;

    public IntContainerData(ContainerData values) {
        this.values = values;
    }

    @Override
    public int get(int index) {
        return (values.get(index / 2) >>> ((index & 1) * 16)) & 0xFFFF;
    }

    @Override
    public void set(int index, int word) {
        int slot = index / 2;
        int old = values.get(slot);
        values.set(slot, (index & 1) == 0
                ? (old & 0xFFFF0000) | (word & 0xFFFF)
                : (old & 0xFFFF) | ((word & 0xFFFF) << 16));
    }

    @Override
    public int getCount() {
        return values.getCount() * 2;
    }
}
