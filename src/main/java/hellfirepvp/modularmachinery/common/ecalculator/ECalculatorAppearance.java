/* SPDX-License-Identifier: GPL-3.0-only
 * NECO EC actual-state and light rules, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.client.model.data.ModelProperty;

/** Bounded cosmetic metadata. Contains no inventory, task NBT, ownership or AE node data. */
public record ECalculatorAppearance(Status status, Link link, Tier tier) {
    public enum Status implements StringRepresentable { OFF, ON, RUN; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public enum Link implements StringRepresentable { NONE, UP, DOWN, ALL; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public enum Tier implements StringRepresentable { NONE, L4, L6, L9; public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final EnumProperty<Link> LINK = EnumProperty.create("link", Link.class);
    public static final EnumProperty<Tier> TIER = EnumProperty.create("link_level", Tier.class);
    public static final ModelProperty<ECalculatorAppearance> DATA = new ModelProperty<>();
    public static final ECalculatorAppearance OFF = new ECalculatorAppearance(Status.OFF, Link.NONE, Tier.NONE);
    public static Tier tier(int tier) { return switch (tier) { case 4 -> Tier.L4; case 6 -> Tier.L6; case 9 -> Tier.L9; default -> Tier.NONE; }; }
    public int encode() { return status.ordinal() | link.ordinal() << 2 | tier.ordinal() << 4; }
    public static ECalculatorAppearance decode(int data) {
        return data < 0 || data > 63 || (data & 3) == 3 ? OFF
                : new ECalculatorAppearance(Status.values()[data & 3], Link.values()[data >> 2 & 3], Tier.values()[data >> 4 & 3]);
    }
    /** Selects a baked variant only; never writes cosmetic states into the world or structure matcher. */
    public BlockState renderState(BlockState state) {
        if (state.hasProperty(STATUS)) state = state.setValue(STATUS, status);
        if (state.hasProperty(ONLINE)) state = state.setValue(ONLINE, status != Status.OFF);
        if (state.hasProperty(FORMED)) state = state.setValue(FORMED, status != Status.OFF);
        if (state.hasProperty(LINK)) state = state.setValue(LINK, link);
        if (state.hasProperty(TIER)) state = state.setValue(TIER, tier);
        return state;
    }
    public int light(ECalculatorSpecs.Kind kind, int memory) {
        return switch (kind) {
            case THREAD, HYPER_THREAD -> status == Status.RUN ? 15 : status == Status.ON ? 10 : 5;
            case PARALLEL -> status == Status.OFF ? 6 : 12;
            case TAIL -> status == Status.OFF ? 0 : 10;
            case DRIVE -> memory == 0 ? 4 : status == Status.OFF ? 8 : 12;
            case TRANSMITTER -> link == Link.ALL ? 12 : link == Link.NONE ? 4 : 8;
            case CHANNEL -> 0;
        };
    }
}
