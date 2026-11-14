/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;
import java.util.*;

/** Optional native-recipe fairness. Transient scalar tickets only; never credit or world references. */
public final class ComputationFairScheduler {
    public static final int MAX_TICKETS = 4096;
    private static final class Ticket {
        final UUID owner;
        final long order;
        BigDecimal amount;
        long seen, granted = Long.MIN_VALUE;
        Ticket(UUID owner, long order, BigDecimal amount, long now) {
            this.owner = owner; this.order = order; this.amount = amount; seen = now; granted = now - 1;
        }
    }
    private final Map<UUID, Ticket> tickets = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> owners = new HashMap<>();
    private final Map<UUID, BigDecimal> reserved = new HashMap<>();
    private BigDecimal reservedTotal = BigDecimal.ZERO;
    private long tick = Long.MIN_VALUE, sequence;

    private void begin(long now, BigDecimal available) {
        if (now < tick) throw new IllegalArgumentException("Scheduling time moved backwards");
        if (now == tick) return;
        tick = now;
        for (var entry : List.copyOf(tickets.entrySet()))
            if (now - entry.getValue().seen > 1) abandon(entry.getKey());
        reserved.clear(); reservedTotal = BigDecimal.ZERO;
        var ordered = new ArrayList<>(tickets.entrySet());
        ordered.sort(Comparator.<Map.Entry<UUID, Ticket>>comparingLong(e -> e.getValue().granted)
                .thenComparingLong(e -> e.getValue().order));
        // Oversized jobs cannot block jobs that fit; no partial payment or accumulated credit.
        for (var entry : ordered) {
            var amount = entry.getValue().amount;
            if (amount.compareTo(available.subtract(reservedTotal)) <= 0) {
                reserved.put(entry.getKey(), amount); reservedTotal = reservedTotal.add(amount);
            }
        }
    }
    /** Only actual advancing-work checks call this. Repeated checks are idempotent. */
    public boolean permits(long now, UUID owner, UUID key, BigDecimal amount, BigDecimal available) {
        Objects.requireNonNull(owner); Objects.requireNonNull(key);
        if (amount.signum() <= 0 || available.signum() < 0) throw new IllegalArgumentException("Invalid scheduling amount");
        begin(now, available);
        var ticket = tickets.get(key);
        if (ticket == null) {
            if (tickets.size() >= MAX_TICKETS) return false;
            ticket = new Ticket(owner, sequence++, amount, now); tickets.put(key, ticket);
            owners.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
        }
        if (!ticket.owner.equals(owner)) throw new IllegalArgumentException("Scheduling owner changed");
        ticket.seen = now;
        if (ticket.granted == now) return false;
        if (ticket.amount.compareTo(amount) != 0) { release(key); ticket.amount = amount; }
        if (reserved.containsKey(key)) return amount.compareTo(available) <= 0;
        if (amount.compareTo(available.subtract(reservedTotal)) > 0) return false;
        reserved.put(key, amount); reservedTotal = reservedTotal.add(amount); return true;
    }
    /** Called after real budget and hardware payment succeed, never by simulation. */
    public void granted(long now, UUID key) {
        var ticket = tickets.get(key);
        if (now != tick || ticket == null || ticket.granted == now || !reserved.containsKey(key))
            throw new IllegalStateException("Work has no scheduling reservation");
        ticket.granted = now; release(key);
    }
    private void release(UUID key) {
        var old = reserved.remove(key); if (old != null) reservedTotal = reservedTotal.subtract(old);
    }
    /** Missing non-computation inputs give up priority and the current tick's reservation. */
    public void abandon(UUID key) {
        release(key); var ticket = tickets.remove(key);
        if (ticket != null) {
            var keys = owners.get(ticket.owner); keys.remove(key);
            if (keys.isEmpty()) owners.remove(ticket.owner);
        }
    }
    public void retain(UUID owner, Set<UUID> active) {
        var keys = owners.get(owner);
        if (keys != null) for (var key : List.copyOf(keys)) if (!active.contains(key)) abandon(key);
    }
    public void removeOwner(UUID owner) { retain(owner, Set.of()); }
    public int size() { return tickets.size(); }
    public void clear() { tickets.clear(); owners.clear(); reserved.clear(); reservedTotal = BigDecimal.ZERO; tick = Long.MIN_VALUE; sequence = 0; }
}
