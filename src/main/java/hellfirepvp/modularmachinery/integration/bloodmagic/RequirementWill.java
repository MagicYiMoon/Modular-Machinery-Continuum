/* SPDX-License-Identifier: GPL-3.0-only
 * Port of ModularMagic's chunk-will requirement. Source will costs do not scale with batching.
 */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.*;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;
import java.util.*;

public final class RequirementWill extends SelectorRequirement implements BatchIORequirement {
    private final IOType io;
    private final double amount, min, max;
    private final EnumDemonWillType type;
    public RequirementWill(IOType io, double amount, EnumDemonWillType type, double min, double max) {
        this.io = io; this.amount = amount; this.type = type; this.min = min; this.max = max;
    }
    public double amount() { return amount; }
    public String willName() { return type == EnumDemonWillType.DEFAULT ? "raw" : type.toString(); }
    @Override public String typeId() { return "modularmachinery:will"; }
    @Override public IOType getIOType() { return io; }
    @Override public String describe() { return io.name().toLowerCase(Locale.ROOT) + " " + amount + " " + willName() + " will (" + min + ".." + max + ")"; }
    @Override public String describeFailure(MachineContext context) { return "error.modularmachinery.requirement.will." + (io == IOType.INPUT ? "less" : "more"); }
    @Override public boolean canStart(MachineContext context) { return plan(context, List.of(this), io).valid(); }
    @Override public boolean canFinish(MachineContext context) { return canStart(context); }
    @Override public boolean canEmit(MachineContext context, int copies) { return io != IOType.OUTPUT || canStart(context); }
    @Override public boolean consume(MachineContext context, int copies) { return plan(context, List.of(this), IOType.INPUT).execute(); }
    @Override public int produce(MachineContext context, int copies) {
        var p = plan(context, List.of(this), IOType.OUTPUT);
        return p.execute() ? (int) Math.min(Integer.MAX_VALUE, p.reserved.values().stream().mapToDouble(Draw::amount).sum()) : 0;
    }
    @Override public Plan planInputs(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, IOType.INPUT); }
    @Override public String outputFailure(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, IOType.OUTPUT).failure; }
    @Override public void writeJson(JsonObject json) {
        json.addProperty("amount", amount); json.addProperty("will-type", willName()); json.addProperty("min", min); json.addProperty("max", max);
    }
    private record Key(int chunkX, int chunkZ, EnumDemonWillType type) {}
    private record Draw(TileWillProvider tile, EnumDemonWillType type, double amount, double limit) {}
    private static WillPlan plan(MachineContext context, List<Requirement> all, IOType direction) {
        var reserved = new LinkedHashMap<Key, Draw>();
        var ordered = all.stream().sorted(Comparator.comparing(Requirement::isIgnoreOutputCheck)).toList();
        for (var raw : ordered) {
            if (!(raw instanceof RequirementWill r) || r.io != direction) continue;
            boolean found = false;
            Set<Key> visited = new HashSet<>();
            for (var pos : context.forSelector(r.getSelectorTag()).getComponentPositions()) {
                if (!(context.getLevel().getBlockEntity(pos) instanceof TileWillProvider tile) || tile.getIOType() != direction) continue;
                Key key = new Key(pos.getX() >> 4, pos.getZ() >> 4, r.type);
                if (!visited.add(key)) continue;
                Draw prior = reserved.get(key);
                double paid = prior == null ? 0 : prior.amount;
                double limit = prior == null ? (direction == IOType.INPUT ? r.min : r.max)
                        : direction == IOType.INPUT ? Math.max(prior.limit, r.min) : Math.min(prior.limit, r.max);
                double held = tile.amount(r.type);
                double available = Math.max(0, direction == IOType.INPUT ? held - paid - limit : limit - held - paid);
                double take = Math.min(r.amount, available);
                if (take < r.amount && !(direction == IOType.OUTPUT && r.isIgnoreOutputCheck())) continue;
                if (direction == IOType.OUTPUT && r.isIgnoreOutputCheck() && take == 0) { found = true; break; }
                reserved.put(key, new Draw(tile, r.type, paid + take, limit));
                found = true; break;
            }
            if (!found && !(direction == IOType.OUTPUT && r.isIgnoreOutputCheck()))
                return new WillPlan(r.describeFailure(context), direction, reserved);
        }
        return new WillPlan(null, direction, reserved);
    }
    private record WillPlan(String failure, IOType direction, Map<Key, Draw> reserved) implements Plan {
        @Override public boolean valid() { return failure == null; }
        @Override public boolean execute() {
            if (!valid()) return false;
            for (Draw d : reserved.values()) {
                double held = d.tile.amount(d.type);
                if (direction == IOType.INPUT ? held - d.amount < d.limit : held + d.amount > d.limit) return false;
            }
            for (Draw d : reserved.values()) {
                if (direction == IOType.INPUT) d.tile.take(d.type, d.amount, false);
                else d.tile.add(d.type, d.amount, false);
            }
            return true;
        }
    }
}
