/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementKeys;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import java.util.*;

/** A total furnace burn-time budget paid at recipe start; excess time is discarded. */
public final class RequirementFuel extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement.Parallelizable {
    // Source any:fuel is a RequirementItem, even though this port gives it a separate implementation.
    @Override public int getSortingWeight() { return PRIORITY_WEIGHT_ITEM; }
    private final int time;
    private final float chance;
    private final RecipeNbt nbt;
    private boolean unaffected;
    private int parallelism = 1;

    public RequirementFuel(int time, float chance, RecipeNbt nbt) {
        if (time < 0 || !Float.isFinite(chance) || chance < 0 || chance > 1)
            throw new IllegalArgumentException("Invalid fuel time/chance");
        this.time = time;
        this.chance = chance;
        this.nbt = Objects.requireNonNull(nbt);
    }
    public static RequirementFuel parse(IOType io, JsonObject json, boolean snapshot) {
        if (io != IOType.INPUT) throw new JsonParseException("any:fuel is input-only");
        if (json.has("consumeDurability")) throw new JsonParseException("Fuel cannot consume tool durability");
        int time;
        try {
            if (!json.has("time") || !json.get("time").isJsonPrimitive() || !json.getAsJsonPrimitive("time").isNumber())
                throw new IllegalArgumentException();
            time = json.get("time").getAsBigDecimal().intValueExact();
            if (time < (snapshot ? 0 : 1)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new JsonParseException("any:fuel requires a positive integer time", invalid); }
        var fuel = new RequirementFuel(time, RequirementKeys.chanceOf(json), RecipeNbt.read(json));
        if (snapshot && json.has("parallelizeUnaffected")) fuel.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
        return fuel;
    }
    public int getTime() { return time; }
    public float getChance() { return chance; }
    public RecipeNbt getNbt() { return nbt; }
    public static int burnTime(ItemStack stack) { return stack.isEmpty() ? 0 : Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING)); }
    public boolean matches(ItemStack stack) { return burnTime(stack) > 0 && nbt.matches(stack.getTag()); }
    @Override public IOType getIOType() { return IOType.INPUT; }
    @Override public String typeId() { return "modularmachinery:item"; }
    @Override public boolean canStart(MachineContext context) { return ItemInputPlan.plan(context, List.of(this), 1, false).valid(); }
    @Override public int getMaxParallelism(MachineContext context, int maximum, List<Requirement> siblings) {
        var inputs = new ArrayList<Requirement>(siblings == null ? List.of() : siblings);
        if (!inputs.contains(this)) inputs.add(this);
        int low = 0, high = Math.max(0, maximum);
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (ItemInputPlan.plan(context, inputs, mid, false).valid()) low = mid;
            else high = mid - 1;
        }
        return low;
    }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int value) { if (!unaffected) parallelism = value; }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean value) { unaffected = value; if (value) parallelism = 1; }
    @Override public Requirement scaled(float value, int operation, IOType io, boolean affectChance) {
        if (io != null && io != IOType.INPUT) return this;
        var copy = new RequirementFuel(affectChance ? time : Math.max(0, Math.round(Requirement.apply(time, value, operation))),
                affectChance ? Math.max(0, Math.min(1, Requirement.apply(chance, value, operation))) : chance, nbt);
        copy.setParallelizeUnaffected(unaffected);
        copy.setParallelism(parallelism);
        return copySelectorTo(copy);
    }
    @Override public String describe() { return time + " furnace fuel ticks (INPUT)"; }
}
