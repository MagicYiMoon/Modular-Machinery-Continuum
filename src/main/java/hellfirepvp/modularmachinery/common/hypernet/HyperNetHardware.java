/* SPDX-License-Identifier: GPL-3.0-only
 * Physical module/load concepts adapted from NECO old.DataProcessor, b37fbb53.
 * New loaded-only snapshots and all-or-nothing native FE payment.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import hellfirepvp.modularmachinery.common.upgrade.ControllerUpgradeQueries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import java.math.*;
import java.util.*;

/** Per-runtime hardware state; transient, server-thread only, never loaded from item-supplied numbers. */
final class HyperNetHardware {
    private record Snapshot(List<HyperNetLoad.Installed> modules, List<TileEnergyHatch> hatches) { }
    private final TileController owner;
    private final HyperNetDefinition definition;
    private final HyperNetThermalState thermal;
    private Snapshot snapshot;
    private HyperNetLoad load;
    private long tick = Long.MIN_VALUE;
    private boolean changed;
    private BigDecimal used = BigDecimal.ZERO;
    private BigDecimal delivered = BigDecimal.ZERO, numerator = BigDecimal.ONE, denominator = BigDecimal.ONE;
    private static final MathContext RAW_ROUNDING=new MathContext(700,RoundingMode.CEILING),OUTPUT_ROUNDING=new MathContext(700,RoundingMode.FLOOR);
    private final Map<TileEnergyHatch, Integer> transfer = new IdentityHashMap<>(), spent = new IdentityHashMap<>();

    HyperNetHardware(TileController owner, HyperNetDefinition definition, HyperNetThermalState thermal) {
        this.owner = owner; this.definition = definition;this.thermal=thermal;
    }
    private Snapshot capture() {
        var installed = ControllerUpgradeQueries.installed(owner, true);
        var modules = new ArrayList<HyperNetLoad.Installed>();
        // Bus/type order is stable and determines which module incurs load first, as in the source.
        for (var upgrade : installed) for (var module : definition.modules())
            if (upgrade.getType().getName().equals(module.upgrade()) && upgrade.getStackSize() > 0)
                modules.add(new HyperNetLoad.Installed(module, upgrade.getStackSize()));
        var hatches = new ArrayList<TileEnergyHatch>();
        var world = (ServerLevel) owner.getLevel();
        for (var pos : new LinkedHashSet<>(owner.getBoundPositions())) {
            var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !world.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(pos))) continue;
            var entity = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
            if (entity instanceof TileEnergyHatch hatch && hatch.getClass() == hellfirepvp.modularmachinery.common.tiles.TileEnergyInputHatch.class
                    && !hatch.isRemoved() && hatch.getIOType() == IOType.INPUT
                    && owner.getBlockPos().equals(hatch.getControllerPos())) hatches.add(hatch);
        }
        return new Snapshot(List.copyOf(modules), List.copyOf(hatches));
    }
    private void refresh() {
        var current = capture(); long now = owner.getLevel().getGameTime();
        if (tick != now) {
            tick = now; snapshot = current; changed = false; used = BigDecimal.ZERO;
            delivered=BigDecimal.ZERO;
            numerator=thermal==null?BigDecimal.ONE:thermal.numerator();
            denominator=thermal==null?BigDecimal.ONE:thermal.denominator();
            load = new HyperNetLoad(snapshot.modules); transfer.clear(); spent.clear();
            for (var hatch : snapshot.hatches) {
                // Query a full buffer's configured rate without changing it or temporarily filling it.
                transfer.put(hatch, hatch.getMachineTransferLimit());
            }
        } else if (!snapshot.equals(current)) changed = true;
    }
    boolean ready() { refresh(); return !changed && load.capacity() > 0 && (thermal==null||!thermal.overheated()); }
    boolean inspectionReady(){return load!=null&&!changed&&load.capacity()>0;}
    double inspectionCapacity(){return load==null?0:load.capacity();}
    /** Installed effective capacity for source-style wear; not remaining or consumed work, no FE debit. */
    double provision() {
        refresh();
        if(changed||thermal!=null&&thermal.overheated())return 0;
        return BigDecimal.valueOf(load.capacity()).multiply(numerator).divide(denominator,OUTPUT_ROUNDING).doubleValue();
    }
    BigDecimal remaining() {
        refresh();
        if(changed||numerator.signum()==0||thermal!=null&&thermal.overheated())return BigDecimal.ZERO;
        var raw=used.add(load.affordableWork(used, availableEnergy()));
        return raw.multiply(numerator).divide(denominator,OUTPUT_ROUNDING).subtract(delivered).max(BigDecimal.ZERO);
    }
    private int allowance(TileEnergyHatch hatch) {
        return Math.min(hatch.consumeInternal(Integer.MAX_VALUE, true), Math.max(0, transfer.get(hatch) - spent.getOrDefault(hatch, 0)));
    }
    private long availableEnergy() {
        long total = 0;
        for (var hatch : snapshot.hatches) total = Math.addExact(total, allowance(hatch));
        return total;
    }
    Payment plan(BigDecimal amount) {
        refresh();
        if (changed || amount.signum() < 0 || numerator.signum()==0 || thermal!=null&&thermal.overheated()) return null;
        var nextRaw=delivered.add(amount).multiply(denominator).divide(numerator,RAW_ROUNDING);
        BigInteger cost;
        try { cost = load.increment(used, nextRaw.subtract(used)); } catch (IllegalArgumentException invalid) { return null; }
        if (cost.compareTo(BigInteger.valueOf(availableEnergy())) > 0) return null;
        long remaining = cost.longValueExact(); var draws = new LinkedHashMap<TileEnergyHatch, Integer>();
        for (var hatch : snapshot.hatches) {
            int take = (int) Math.min(remaining, allowance(hatch));
            if (take > 0) draws.put(hatch, take);
            remaining -= take; if (remaining == 0) break;
        }
        return remaining == 0 ? new Payment(this, used, nextRaw, amount, draws) : null;
    }
    /** Only exact native hatches have a known, reversible machine-side channel. No third-party callbacks. */
    static final class Payment {
        private final HyperNetHardware hardware;
        private final BigDecimal before;
        private final BigDecimal amount;
        private final BigDecimal nextRaw;
        private final Map<TileEnergyHatch, Integer> draws, taken = new LinkedHashMap<>();
        Payment(HyperNetHardware hardware, BigDecimal before, BigDecimal nextRaw, BigDecimal amount, Map<TileEnergyHatch, Integer> draws) {
            this.hardware = hardware; this.before = before;this.nextRaw=nextRaw; this.amount = amount; this.draws = draws;
        }
        boolean take() {
            if (hardware.changed || hardware.used.compareTo(before) != 0) return false;
            for (var draw : draws.entrySet()) {
                int actual = draw.getKey().consumeInternal(draw.getValue(), false);
                if (actual > 0) taken.put(draw.getKey(), actual);
                if (actual != draw.getValue()) { rollback(); return false; }
            }
            return true;
        }
        void rollback() {
            for (var draw : taken.entrySet()) if (draw.getKey().produceInternal(draw.getValue(), false) != draw.getValue())
                throw new IllegalStateException("Native HyperNet energy refund failed");
            taken.clear();
        }
        void commit() {
            hardware.used = nextRaw;hardware.delivered=hardware.delivered.add(amount);
            taken.forEach((hatch, amount) -> hardware.spent.merge(hatch, amount, Math::addExact));
            taken.clear();
            if(hardware.thermal!=null)hardware.thermal.generate(amount);
        }
    }
}
