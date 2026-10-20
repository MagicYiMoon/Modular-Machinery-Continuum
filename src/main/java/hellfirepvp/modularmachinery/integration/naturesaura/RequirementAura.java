/* SPDX-License-Identifier: GPL-3.0-only
 * Port of ModularMagic's aura requirement; exact native units replace its inconsistent x100000 IO.
 */
package hellfirepvp.modularmachinery.integration.naturesaura;

import com.google.gson.JsonObject;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.BlockPos;
import java.util.*;

public final class RequirementAura extends SelectorRequirement implements Requirement.Parallelizable, BatchIORequirement {
    private final IOType io;
    private final int amount, min, max;
    private final IAuraType type;
    private int parallelism = 1;
    private boolean unaffected;
    public RequirementAura(IOType io, int amount, IAuraType type, int min, int max) {
        this.io = io; this.amount = amount; this.type = type; this.min = min; this.max = max;
    }
    public int amount() { return amount; }
    public String auraName() { return type.getName().toString(); }
    @Override public IOType getIOType() { return io; }
    @Override public String typeId() { return "modularmachinery:aura"; }
    @Override public String describe() { return io.name().toLowerCase(Locale.ROOT) + " " + amount + " " + auraName() + " aura"; }
    @Override public String describeFailure(MachineContext context) { return "error.modularmachinery.requirement.aura." + (io == IOType.INPUT ? "less" : "more"); }
    @Override public boolean canStart(MachineContext context) { return plan(context, List.of(this), 1, io).valid(); }
    @Override public boolean canFinish(MachineContext context) { return canStart(context); }
    @Override public boolean canEmit(MachineContext context, int copies) { return io != IOType.OUTPUT || plan(context, List.of(this), copies, io).valid(); }
    @Override public boolean consume(MachineContext context, int copies) { return plan(context, List.of(this), copies, IOType.INPUT).execute(); }
    @Override public int produce(MachineContext context, int copies) {
        var p = plan(context, List.of(this), copies, IOType.OUTPUT);
        return p.execute() ? (int) Math.min(Integer.MAX_VALUE, p.draws.values().stream().mapToLong(Draw::amount).sum()) : 0;
    }
    @Override public Plan planInputs(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, copies, IOType.INPUT); }
    @Override public Plan planOutputs(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, copies, IOType.OUTPUT); }
    @Override public String outputFailure(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, copies, IOType.OUTPUT).failure; }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int n) { if (!unaffected) parallelism = Math.max(1, n); }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean flag) { unaffected = flag; if (flag) parallelism = 1; }
    @Override public int getMaxParallelism(MachineContext context, int ceiling, List<Requirement> siblings) {
        var all = new ArrayList<>(siblings == null ? List.<Requirement>of() : siblings); if (!all.contains(this)) all.add(this);
        int low = 0, high = Math.max(0, ceiling);
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (plan(context, all, mid, io).valid()) low = mid; else high = mid - 1;
        }
        return low;
    }
    @Override public void writeJson(JsonObject json) {
        json.addProperty("amount", amount); json.addProperty("aura-type", auraName());
        json.addProperty("min-aura", min); json.addProperty("max-aura", max); json.addProperty("parallelizeUnaffected", unaffected);
    }
    @Override public Requirement scaled(float value, int operation, IOType direction, boolean chance) {
        if (chance || direction != null && direction != io) return this;
        var copy = new RequirementAura(io, Math.max(0, Math.round(Requirement.apply(amount, value, operation))), type, min, max);
        copy.setParallelizeUnaffected(unaffected); copy.setParallelism(parallelism); return copySelectorTo(copy);
    }
    private record Draw(TileAuraProvider tile, long amount, long initialArea, int initialSpot) {}
    private record Limit(TileAuraProvider tile, int limit) {}
    private static boolean overlaps(BlockPos a, BlockPos b) { return a.distSqr(b) <= 1; }
    private static long projected(TileAuraProvider tile, Map<BlockPos, Draw> draws, IOType direction) {
        long result = tile.amount();
        for (var e : draws.entrySet()) if (overlaps(tile.getBlockPos(), e.getKey())) result += direction == IOType.INPUT ? -e.getValue().amount : e.getValue().amount;
        return result;
    }
    private static AuraPlan plan(MachineContext context, List<Requirement> all, int copies, IOType direction) {
        var draws = new LinkedHashMap<BlockPos, Draw>(); var limits = new ArrayList<Limit>();
        var ordered = all.stream().sorted(Comparator.comparing(Requirement::isIgnoreOutputCheck)).toList();
        for (var raw : ordered) {
            if (!(raw instanceof RequirementAura r) || r.io != direction) continue;
            long remaining = (long) r.amount * (r.unaffected ? 1 : Math.max(1, copies));
            Set<Long> chunks = new HashSet<>();
            for (var pos : context.forSelector(r.getSelectorTag()).getComponentPositions()) {
                if (remaining == 0) break;
                if (!(context.getLevel().getBlockEntity(pos) instanceof TileAuraProvider tile) || tile.getIOType() != direction || !tile.auraType().equals(r.type)) continue;
                long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                if (!chunks.add(chunk)) continue; // Source: one access point per chunk, including duplicate positions.
                long held = projected(tile, draws, direction);
                long available = direction == IOType.INPUT ? held - r.min : (long) r.max - held;
                Draw prior = draws.get(pos); long paid = prior == null ? 0 : prior.amount;
                long spot = tile.spotAmount() + (direction == IOType.INPUT ? -paid : paid);
                available = Math.min(available, direction == IOType.INPUT ? spot - Integer.MIN_VALUE : Integer.MAX_VALUE - spot);
                available = Math.min(available, Integer.MAX_VALUE - paid); // One exact, non-overflowing API operation per spot.
                // A write can affect a provider in the next chunk. Preserve all earlier reservations' limits.
                for (var bound : limits) if (overlaps(pos, bound.tile.getBlockPos())) {
                    long projected = projected(bound.tile, draws, direction);
                    available = Math.min(available, direction == IOType.INPUT ? projected - bound.limit : (long) bound.limit - projected);
                }
                long take = Math.min(remaining, Math.max(0, available));
                if (take > 0) {
                    draws.put(pos.immutable(), new Draw(tile, paid + take, tile.amount(), tile.spotAmount()));
                    limits.add(new Limit(tile, direction == IOType.INPUT ? r.min : r.max));
                    remaining -= take;
                }
            }
            if (remaining > 0 && !(direction == IOType.OUTPUT && r.isIgnoreOutputCheck())) return new AuraPlan(r.describeFailure(context), direction, draws, limits);
        }
        return new AuraPlan(null, direction, draws, limits);
    }
    private record AuraPlan(String failure, IOType direction, Map<BlockPos, Draw> draws, List<Limit> limits) implements Plan {
        @Override public boolean valid() { return failure == null; }
        @Override public boolean execute() {
            if (!valid()) return false;
            // Plans execute synchronously on the server; reject stale plans before any mutation.
            for (var d : draws.values()) if (d.tile.isRemoved() || d.tile.amount() != d.initialArea || d.tile.spotAmount() != d.initialSpot
                    || d.tile.change((int) d.amount, direction, true) != d.amount) return false;
            for (var bound : limits) {
                long after = projected(bound.tile, draws, direction);
                if (direction == IOType.INPUT ? after < bound.limit : after > bound.limit) return false;
            }
            for (var d : draws.values()) d.tile.change((int) d.amount, direction, false);
            return true;
        }
    }
}
