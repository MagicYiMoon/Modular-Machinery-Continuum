/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.block.prop;

import net.minecraft.util.StringRepresentable;
import java.util.Locale;

/** Capacity ladder from the gugu-utils extension, not MMCE core. */
public enum EmberHatchSize implements StringRepresentable {
    TINY(400), SMALL(800), MEDIUM(1600), REINFORCED(3200), BIG(6400), HUGE(9600), LUDICROUS(12800), EXTREME(16000);
    private final int capacity;
    EmberHatchSize(int capacity) { this.capacity = capacity; }
    public int capacity() { return capacity; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
