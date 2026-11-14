/* SPDX-License-Identifier: GPL-3.0-only
 * Recipe demand/pause concepts adapt NECO old.NetNodeImpl, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementComputation;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import java.math.BigDecimal;
import java.util.List;

/** Only successful work spends budget; read-only active demand is a separate scalar snapshot. */
public final class ComputationTickIO {
    public static final String SHORTFALL = "craftcheck.failure.computation.input", DISCONNECTED = "craftcheck.failure.computation.network";
    public static final String QUEUED = "craftcheck.failure.computation.queued";
    private ComputationTickIO() { }
    public record Plan(HyperNetNode node, BigDecimal amount, String failure, boolean triggerFailure, java.util.UUID ticket) {
        public Plan(HyperNetNode node, BigDecimal amount, String failure, boolean triggerFailure) { this(node,amount,failure,triggerFailure,null); }
        public void abandon() { if (node != null && ticket != null) node.abandonWork(ticket); }
        public boolean valid() { return failure == null; }
        public boolean execute() { return valid() && (node == null || amount.signum() == 0 || (ticket == null ? node.request(amount, false).granted() > 0 : node.executeWork(ticket, amount))); }
    }
    public static boolean pauses(List<Requirement> requirements, String failure) {
        return QUEUED.equals(failure) || (SHORTFALL.equals(failure) || DISCONNECTED.equals(failure)) && requirements.stream()
                .filter(RequirementComputation.class::isInstance).map(RequirementComputation.class::cast).noneMatch(RequirementComputation::triggerFailure);
    }
    public static BigDecimal amount(List<Requirement> requirements, int parallelism) {
        BigDecimal amount = BigDecimal.ZERO;
        for (var requirement : requirements) if (requirement instanceof RequirementComputation computation)
            amount = amount.add(BigDecimal.valueOf(computation.amount()).multiply(BigDecimal.valueOf(
                    computation.isParallelizeUnaffected() ? 1 : Math.max(1, parallelism))));
        return amount;
    }
    private static HyperNetDemand.Sample demand(hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext run,
                                               int tick, int duration, int parallelism) {
        if (run == null || tick >= duration) return HyperNetDemand.EMPTY;
        var amount = amount(hellfirepvp.modularmachinery.common.tiles.MachineTicker.activeRequirementsAtTick(
                run, run.getRequirements(), tick), parallelism);
        return amount.signum() == 0 ? HyperNetDemand.EMPTY : new HyperNetDemand.Sample(amount, 1);
    }
    /** Called after controller work, never by simulation, start checks, TOP or monitor queries. */
    static HyperNetDemand.Sample activeDemand(TileController controller) {
        if (controller instanceof hellfirepvp.modularmachinery.common.tiles.TileFactoryController factory) {
            var total = HyperNetDemand.EMPTY;
            for (var thread : factory.getFactoryRecipeThreadList())
                total = total.add(demand(thread.getRuntimeRecipeContext(), thread.getRecipeTicks(), thread.getRecipeDuration(), thread.getParallelism()));
            return total;
        }
        return demand(controller.getRuntimeRecipeContext(), controller.getRecipeTicks(), controller.getRecipeDuration(), controller.getParallelism());
    }
    static java.util.Set<java.util.UUID> activeTickets(TileController controller) {
        var active = new java.util.HashSet<java.util.UUID>();
        if (controller instanceof hellfirepvp.modularmachinery.common.tiles.TileFactoryController factory) {
            for (var thread : factory.getFactoryRecipeThreadList())
                if (demand(thread.getRuntimeRecipeContext(), thread.getRecipeTicks(), thread.getRecipeDuration(), thread.getParallelism()).consumers() > 0)
                    active.add(thread.computationTicket());
        } else if (demand(controller.getRuntimeRecipeContext(), controller.getRecipeTicks(), controller.getRecipeDuration(), controller.getParallelism()).consumers() > 0)
            active.add(controller.computationTicket());
        return active;
    }
    public static Plan workPlan(MachineContext context, List<Requirement> requirements, int parallelism,
                                java.util.UUID ticket) {
        return plan(context, requirements, parallelism, java.util.Objects.requireNonNull(ticket), false);
    }
    public static Plan plan(MachineContext context, List<Requirement> requirements, int parallelism) {
        return plan(context, requirements, parallelism, null, false);
    }
    public static Plan startPlan(MachineContext context, List<Requirement> requirements, int parallelism) {
        return plan(context, requirements, parallelism, null, true);
    }
    private static Plan plan(MachineContext context, List<Requirement> requirements, int parallelism, java.util.UUID ticket, boolean starting) {
        BigDecimal amount = amount(requirements, parallelism); boolean present = false, triggerFailure = false;
        for (var requirement : requirements) if (requirement instanceof RequirementComputation computation) {
            present = true; triggerFailure |= computation.triggerFailure();
        }
        if (!present) return new Plan(null, BigDecimal.ZERO, null, false);
        var world = context.getLevel(); var pos = context.getControllerPos();
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var entity = chunk == null ? null : chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        var node = entity instanceof TileController controller && context.getMachine() != null
                && context.getMachine().getRegistryName().equals(controller.getMachineName()) ? controller.getHyperNetNode() : null;
        if (node == null || !node.isConsumerConnected()) return new Plan(node, amount, DISCONNECTED, triggerFailure);
        if (ticket != null && amount.signum() > 0 && amount.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) <= 0)
            return new Plan(node, amount, node.workFailure(ticket, amount), triggerFailure, ticket);
        if (amount.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0 || amount.signum() > 0 && (starting ? !node.canStartWork(amount) : node.request(amount, true).granted() == 0))
            return new Plan(node, amount, SHORTFALL, triggerFailure);
        return new Plan(node, amount, null, triggerFailure);
    }
}
