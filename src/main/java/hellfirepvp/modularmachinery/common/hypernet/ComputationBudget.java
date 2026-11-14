/* SPDX-License-Identifier: GPL-3.0-only
 * Tick-budget concept adapted from NECO ComputationCenter/DataProcessor,
 * KasumiNova and contributors, revision b37fbb53. New server-thread ledger.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import java.util.*;
import java.math.BigDecimal;

/**
 * One computation centre's transient budget. No world references, static cache or saved credit.
 * Each loaded provider must publish its valid supply again every tick. Call only on the owning
 * server thread. World/recipe/energy integration is deliberately a separate layer.
 */
public final class ComputationBudget implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final Map<UUID, Provider> providers = new LinkedHashMap<>();
    private final Set<UUID> published = new HashSet<>();
    private long tick = -1;
    // Decimal balances prevent small debits from disappearing beside a very large balance.
    private BigDecimal limit = BigDecimal.ZERO, remaining = BigDecimal.ZERO;
    private boolean operational, closed;

    /** A lease belongs to this exact centre instance, not merely to a matching position or UUID. */
    public static final class Provider {
        private final UUID id;
        private BigDecimal remaining = BigDecimal.ZERO;
        private Provider(UUID id) { this.id = id; }
    }

    /** Shares are a receipt for later actual load/energy accounting, never a persistent balance. */
    public record Allocation(double granted, Map<UUID, Double> shares, Map<UUID, BigDecimal> exactShares) {
        public Allocation(double granted, Map<UUID, Double> shares) {
            this(granted, shares, decimals(shares));
        }
        public Allocation {
            shares = Collections.unmodifiableMap(new LinkedHashMap<>(shares));
            exactShares = Collections.unmodifiableMap(new LinkedHashMap<>(exactShares));
        }
        private static Map<UUID, BigDecimal> decimals(Map<UUID, Double> shares) {
            var out = new LinkedHashMap<UUID, BigDecimal>(); shares.forEach((id, amount) -> out.put(id, BigDecimal.valueOf(amount))); return out;
        }
        private static final Allocation NONE = new Allocation(0, Map.of());
    }

    public Provider attach(UUID id) {
        checkOpen();
        var provider = new Provider(Objects.requireNonNull(id));
        // Replacing a node revokes its previous lease, even if its position/UUID is reused.
        providers.put(id, provider);
        return provider;
    }

    public boolean detach(Provider provider) {
        checkOpen();
        return provider != null && providers.remove(provider.id, provider);
    }

    /** Duplicate tick callbacks cannot refill either the centre or its providers. */
    public boolean beginTick(long gameTick, boolean working, double carryingLimit) {
        checkOpen(); validate(carryingLimit);
        if (gameTick < 0 || gameTick < tick) throw new IllegalArgumentException("Computation tick moved backwards");
        if (gameTick == tick) return false;
        tick = gameTick; operational = working; limit = BigDecimal.valueOf(carryingLimit);
        remaining = working ? limit : BigDecimal.ZERO;
        published.clear();
        providers.values().forEach(provider -> provider.remaining = BigDecimal.ZERO);
        return true;
    }

    /** Publish only after checking structure, connection, modules, power and heat for this tick. */
    public boolean publish(Provider provider, double supply) {
        checkOpen(); validate(supply); return publish(provider, BigDecimal.valueOf(supply));
    }
    public boolean publish(Provider provider, BigDecimal supply) {
        checkOpen(); validate(supply);
        // A same-tick unload/replacement must not republish already-spent physical supply.
        if (!valid(provider) || tick < 0 || !operational || !published.add(provider.id)) return false;
        provider.remaining = supply;
        return true;
    }

    /** Power/module changes can only reduce this tick's unspent offer, never refill it. */
    public void restrict(Provider provider, double remainingSupply) {
        checkOpen(); validate(remainingSupply); restrict(provider, BigDecimal.valueOf(remainingSupply));
    }
    public void restrict(Provider provider, BigDecimal remainingSupply) {
        checkOpen(); validate(remainingSupply);
        if (valid(provider)) provider.remaining = provider.remaining.min(remainingSupply);
    }

    /** Immediate disconnect/stop; resuming requires the next tick and fresh provider publications. */
    public void stop() {
        checkOpen(); operational = false; remaining = BigDecimal.ZERO;
        providers.values().forEach(provider -> provider.remaining = BigDecimal.ZERO);
    }

    public double available() { return availableExact().doubleValue(); }
    public BigDecimal availableExact() {
        checkOpen();
        BigDecimal total = BigDecimal.ZERO;
        if (operational) for (var provider : providers.values()) {
            // Saturate at the finite centre budget, avoiding overflow with very large providers.
            total = total.add(provider.remaining.min(remaining.subtract(total)));
            if (total.compareTo(remaining) >= 0) break;
        }
        return total;
    }

    /** All-or-nothing; failed and simulated requests change neither centre nor provider budgets. */
    public Allocation request(double required, boolean simulate) {
        checkOpen(); validate(required); return request(BigDecimal.valueOf(required), simulate);
    }
    public Allocation request(BigDecimal requested, boolean simulate) {
        checkOpen(); validate(requested);
        if (requested.signum() > 0 && requested.doubleValue() == 0)
            throw new IllegalArgumentException("Computation request is below the representable receipt range");
        if (requested.signum() == 0 || !operational || requested.compareTo(remaining) > 0) return Allocation.NONE;
        var plan = new LinkedHashMap<UUID, Double>();
        var exact = new LinkedHashMap<UUID, BigDecimal>();
        var debits = new LinkedHashMap<Provider, BigDecimal>();
        BigDecimal needed = requested;
        for (var provider : providers.values()) {
            var take = needed.min(provider.remaining);
            if (take.signum() > 0) { plan.put(provider.id, take.doubleValue()); exact.put(provider.id, take); debits.put(provider, take); needed = needed.subtract(take); }
            if (needed.signum() == 0) break;
        }
        if (needed.signum() > 0) return Allocation.NONE;
        if (!simulate) {
            remaining = remaining.subtract(requested);
            debits.forEach((provider, amount) -> provider.remaining = provider.remaining.subtract(amount));
        }
        return new Allocation(requested.doubleValue(), plan, exact);
    }

    public double consumed() { checkOpen(); return operational ? limit.subtract(remaining).doubleValue() : 0; }
    private boolean valid(Provider provider) { return provider != null && providers.get(provider.id) == provider; }
    private static void validate(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid computation amount");
    }
    private static void validate(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0)
            throw new IllegalArgumentException("Invalid computation amount");
    }
    private void checkOpen() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Computation budget used outside its server thread");
        if (closed) throw new IllegalStateException("Computation centre retired");
    }
    @Override public void close() {
        checkOpen(); stop(); providers.clear(); published.clear(); closed = true;
    }
}
