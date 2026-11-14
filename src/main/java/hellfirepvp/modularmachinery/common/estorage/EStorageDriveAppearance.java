/* SPDX-License-Identifier: GPL-3.0-only
 * NECO drive state/LED rules, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.estorage;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.client.model.data.ModelProperty;

/** Render metadata only. Never contains resource keys, quantities or matrix NBT. */
public record EStorageDriveAppearance(Level level, Kind kind, Capacity capacity, Status status) {
    public enum Level implements StringRepresentable { EMPTY, A, B, C; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public enum Kind implements StringRepresentable { EMPTY, ITEM, FLUID, GAS; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public enum Capacity implements StringRepresentable { EMPTY, TYPE_MAX, FULL; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public enum Status implements StringRepresentable { IDLE, RUN; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public static final EnumProperty<Level> LEVEL = EnumProperty.create("storage_level", Level.class);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("storage_type", Kind.class);
    public static final EnumProperty<Capacity> CAPACITY = EnumProperty.create("storage_capacity", Capacity.class);
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    public static final ModelProperty<EStorageDriveAppearance> DATA = new ModelProperty<>();
    public static final EStorageDriveAppearance EMPTY = new EStorageDriveAppearance(Level.EMPTY, Kind.EMPTY, Capacity.EMPTY, Status.IDLE);
    public static EStorageDriveAppearance of(EStorageCellSpec spec, EStorageMatrixSummary summary, boolean running) {
        Capacity fullness = summary == null || summary.usedTypes() == 0 ? Capacity.EMPTY
                : summary.usedBytes() >= spec.totalBytes() ? Capacity.FULL
                : summary.usedTypes() >= spec.maxTypes() ? Capacity.TYPE_MAX : Capacity.EMPTY;
        return new EStorageDriveAppearance(Level.values()[spec.tier().ordinal() + 1],
                switch (spec.kind()) { case ITEM -> Kind.ITEM; case FLUID -> Kind.FLUID; case GAS -> Kind.GAS; }, fullness, running ? Status.RUN : Status.IDLE);
    }
    public int encode() { return level.ordinal() | kind.ordinal() << 2 | capacity.ordinal() << 4 | status.ordinal() << 6; }
    public static EStorageDriveAppearance decode(int data) {
        int level = data & 3, kind = data >> 2 & 3, capacity = data >> 4 & 3;
        if (data < 0 || data > 127 || capacity > 2 || level == 0 || kind == 0) return EMPTY;
        return new EStorageDriveAppearance(Level.values()[level], Kind.values()[kind], Capacity.values()[capacity], Status.values()[data >> 6 & 1]);
    }
    /** Virtual state for the baked model; the server world never writes these cosmetic properties. */
    public BlockState renderState(BlockState base) {
        return base.setValue(LEVEL, level).setValue(KIND, kind).setValue(CAPACITY, capacity).setValue(STATUS, status);
    }
}
