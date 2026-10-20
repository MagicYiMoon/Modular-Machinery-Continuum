/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.ArrayList;
import java.util.List;

/**
 * The set of machine components currently wired to a controller, and their capabilities.
 *
 * <h2>Why this exists</h2>
 *
 * <p>1.12.2 resolved capabilities by looking up the tile at a world position each time a requirement
 * asked a question. That is wrong on 1.20.1 for a concrete reason: a capability is handed out as a
 * {@link LazyOptional} which may have been <b>invalidated</b> since it was obtained, and a machine whose
 * contents are queried every tick must not re-resolve — and must not hold a stale handler either.
 *
 * <p>So this class resolves each component's capability <b>once per inspection</b>, keeps the
 * {@link LazyOptional} for the duration, and returns it. Callers use the value and drop it; nothing
 * caches across ticks. A component that is removed invalidates its optional, and the next inspection
 * simply does not find it — which is the behaviour the 1.12.2 code got wrong by holding raw handlers.
 *
 * <h2>Ordering</h2>
 *
 * <p>Components come back in the order the machine declares them, not in world position order. Recipe
 * consumption is <b>visible</b> to players: a machine that pulls from its input buses in a different
 * order after a reload would look like it was behaving randomly. Declaration order is stable, matches
 * what the structure JSON says, and makes consumption reproducible.
 */
public final class MachineContext {

    private final ServerLevel level;
    private final BlockPos controllerPos;
    private final Direction facing;
    private final MachinePattern pattern;
    private final MachineVariables variables;
    private final DynamicMachine machine;
    private MachineContext root;
    private String selectorTag;
    private java.util.Set<BlockPos> selectedPositions;
    private final java.util.Map<String, MachineContext> selectorViews = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, net.minecraftforge.fluids.capability.IFluidHandler> fluidInputViews = new java.util.HashMap<>();
    /** The source mod's combination-component group currently selected for this inspection. */
    private String isolationGroupKey;

    /**
     * Resolved component positions, in declaration order.
     *
     * <p>Only positions are stored, not capabilities: a position stays valid while the world does, and
     * the capability is re-resolved on each call so an invalidated optional is never handed out.
     */
    private List<BlockPos> componentPositions;

    /**
     * @param pattern <b>THE PATTERN THIS CONTEXT RESOLVES COMPONENTS FROM — THE ONE THE MACHINE WAS FORMED WITH,
     *                NOT THE DEFINITION'S.</b> The two differ whenever the machine declares
     *                {@code dynamic-patterns}: the formed pattern is the definition's parts with its sections
     *                merged in (PLAN §750), and the shipped {@code assembly_line} keeps its input bus, its fluid
     *                and ME hatches and its output bus <b>only</b> in the sections. Passing
     *                {@code machine.getPattern()} here compiles, reads as correct, and silently hands the recipe
     *                layer a machine with no busses — which is why the parameter is explicit rather than
     *                defaulted.
     */
    public MachineContext(ServerLevel level, BlockPos controllerPos, Direction facing,
                          DynamicMachine machine, MachineVariables variables, MachinePattern pattern) {
        this.level = level;
        this.controllerPos = controllerPos;
        this.facing = facing;
        this.pattern = pattern != null ? pattern : machine.getPattern();
        this.variables = variables;
        this.machine = machine;
        this.root = this;
    }

    /** Independent views share a root; sibling requirements never inherit each other's selector. */
    public MachineContext forSelector(String tag) {
        if (tag == null || tag.isEmpty()) return root;
        String cacheKey = (root.isolationGroupKey == null ? "<all>" : root.isolationGroupKey) + "|" + tag;
        return root.selectorViews.computeIfAbsent(cacheKey, key -> {
            MachineContext view = new MachineContext(level, controllerPos, facing, machine, variables, pattern);
            view.root = root;
            view.selectorTag = tag;
            return view;
        });
    }

    /** A stable identity for one source-style combination component. */
    public record IsolationGroup(String key, BlockPos providerPos, int slot) {}

    /**
     * Lists the groups exposed by all isolation-input providers in declaration order.  Ordinary components are
     * implicitly present in every returned group, matching the upstream controller's generalComponents merge.
     */
    public List<IsolationGroup> isolationGroups() {
        java.util.Map<String, IsolationGroup> groups = new java.util.LinkedHashMap<>();
        for (BlockPos pos : getComponentPositions()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof hellfirepvp.modularmachinery.common.tiles.PatternIsolationProvider isolated)) continue;
            int size = Math.max(isolated.getIsolatedItemHandlers().size(), isolated.getIsolatedFluidHandlers().size());
            BlockPos groupPos = isolated.getIsolationGroupPosition() != null
                    ? isolated.getIsolationGroupPosition() : pos;
            for (int slot = 0; slot < size; slot++) {
                String key = isolationGroupKey(groupPos, slot);
                groups.putIfAbsent(key, new IsolationGroup(key, groupPos, slot));
            }
        }
        return new ArrayList<>(groups.values());
    }

    private static String isolationGroupKey(BlockPos providerPos, int slot) {
        return providerPos.asLong() + ":" + slot;
    }

    /** Selects one combination-component group for all subsequent capability lookups on this context. */
    public void selectIsolationGroup(String key) {
        root.isolationGroupKey = key;
    }

    /** @return the selected source-style group, or {@code null} when no group is pinned */
    public String selectedIsolationGroupKey() {
        return root.isolationGroupKey;
    }

    /**
     * @return the definition this machine was formed from.
     *
     * <p><b>KEPT BECAUSE A REQUIREMENT NEEDS IT AND HAD NO OTHER WAY TO ASK (PLAN 663, B4).</b> Upstream's
     * {@code RequirementInterfaceNumInput} resolves its {@code SmartInterfaceType} descriptor out of the
     * machine at CONSTRUCTION time and reads the descriptor's {@code notEqualMessage} when a value is out of
     * range (upstream U86-U89). This port's requirement type factory receives only the requirement's own JSON
     * (see {@code RequirementType#createRequirement}), so the machine is not available where upstream had it —
     * but it IS available on every evaluation, because the context is built from it. Exposing it here keeps
     * the descriptor lookup on the same object upstream read it from instead of inventing a second registry
     * of declared types.</p>
     */
    public DynamicMachine getMachine() {
        return machine;
    }

    public ServerLevel getLevel() {
        return level;
    }

    /** @return the controller facing, needed to rotate a modifier offset the way the pattern is rotated */
    public Direction getFacing() {
        return facing;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * @return the positions of the machine's components, resolved lazily and cached for the lifetime of
     *         this context only
     */
    public List<BlockPos> getComponentPositions() {
        if (selectorTag != null) {
            if (selectedPositions == null) {
                selectedPositions = new java.util.HashSet<>();
                for (MachinePattern.Part part : pattern.getParts()) {
                    if (selectorTag.equals(part.selectorTag())) selectedPositions.add(StructureMatcher.toWorldOffset(
                            controllerPos, facing, part.x(), part.y(), part.z()));
                }
            }
            return root.getComponentPositions().stream().filter(selectedPositions::contains).toList();
        }
        if (componentPositions == null) {
            componentPositions = resolveComponents();
        }
        return componentPositions;
    }

    private List<BlockPos> resolveComponents() {
        List<BlockPos> found = new ArrayList<>();
        for (MachinePattern.Part part : pattern.getParts()) {
            BlockPos worldPos = StructureMatcher.toWorldOffset(
                    controllerPos, facing, part.x(), part.y(), part.z());
            if (!level.isLoaded(worldPos)) {
                // An unloaded chunk's component genuinely cannot be inspected. Skipping it means a
                // requirement may report "not satisfied" rather than reading false emptiness, which is
                // the safe direction: the machine stalls instead of voiding items into a buffer it
                // could not see.
                continue;
            }
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof MachineComponentTile) {
                found.add(worldPos);
            }
        }
        return found;
    }

    /**
     * Collects this machine's <b>upgrade buses</b> themselves (audit row 37).
     *
     * <h2>Rule A0 — upstream resolves the provider through a component; this port resolves the tile through the
     * pattern, and the two say the same thing</h2>
     *
     * <p>Upstream: {@code TileMultiblockMachineController.checkAndAddComponents} calls
     * {@code checkAndAddUpgradeBus(component)} for every component the structure walk found (U842), and that method
     * (U847-U871) recognises a {@code TileUpgradeBus.UpgradeBusProvider}, records it in {@code foundUpgradeBuses} and
     * asks it for {@code getUpgrades(this)} — the controller hands ITSELF in, which is how the bus filters by
     * {@code type.isCompatible(foundMachine)} against the controller's found machine (TileUpgradeBus U251-U256).
     *
     * <p><b>The port has no {@code MachineComponent} type at all, and this class is its substitute — the same one
     * {@link #internalEnergyHatches()} and {@link #smartInterfaces()} already are.</b> Upstream's provider is a
     * <i>component</i>, so the controller's structure walk could hand it to the recipe machinery; here the
     * reachability comes from the other half of upstream's shape: the bus block entity implements
     * {@link MachineComponentTile} and this method resolves exactly those tiles out of the pattern — which the same
     * formation walk populated {@code setControllerPos} on. The compatibility filter is applied by the BUS
     * ({@code TileUpgradeBus#getUpgradesFor}), which is where upstream applies it too.
     *
     * <p><b>Cost:</b> a scan of the machine's components per call. {@code MachineTicker} caches its answer for the
     * duration of one tick rather than calling this per requirement — see {@code MachineTicker#upgradeSnapshotOf}.
     *
     * @return the upgrade buses among this machine's components, in declaration order
     */
    public List<hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus> upgradeBuses() {
        List<hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus> found = new ArrayList<>();
        for (BlockPos pos : getComponentPositions()) {
            if (level.getBlockEntity(pos)
                    instanceof hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus bus) {
                found.add(bus);
            }
        }
        return found;
    }

    /**
     * @return every upgrade installed in this machine's buses that is compatible with this machine
     *
     * <p><b>THE ONE PRODUCER of the installed-upgrade list for a machine.</b> Both the modifier half
     * ({@code MachineTicker#activeModifiers}) and the duration half ({@code MachineTicker#recipeDuration}) read
     * upgrades through here, so an upgrade cannot influence a requirement and a duration by two different rules.
     *
     * <p>Deduplication by type is the caller's job, exactly as it is upstream's
     * {@code checkAndAddUpgradeBus} merge across buses (U855-U869): this returns one object per (bus, type) pair and
     * the caller owns the machine-scoped view.
     */
    public List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade> installedUpgrades() {
        List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade> found = new ArrayList<>();
        String machineName = machine == null ? null : machine.getRegistryName();
        for (hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus bus : upgradeBuses()) {
            found.addAll(bus.getUpgradesFor(machineName));
        }
        return found;
    }

    /**
     * Collects this machine's energy hatches themselves, for the machine's <b>internal</b> use.
     *
     * <h2>Why the capability list is not enough</h2>
     *
     * <p>The capability path hands back whatever object was constructed to implement
     * {@code IEnergyStorage}. For our hatches that is the <b>anonymous {@code EnergyStorage} subclass</b>
     * created in {@code TileEnergyHatch#createStorage} — its runtime class is
     * {@code TileEnergyHatch$1}, which is <b>not</b> an instance of {@code TileEnergyHatch}. A caller
     * that wanted the owning tile and wrote {@code instanceof TileEnergyHatch} therefore matched
     * nothing and silently did nothing.
     *
     * <p>That distinction matters because the two roles genuinely differ. The capability expresses the
     * <b>external</b> contract — an input hatch refuses extraction so a neighbouring machine cannot
     * drain it — while the owning machine must be able to spend that same buffer. Reaching the tile
     * directly is what separates the two.
     *
     * @return the energy hatches among this machine's components
     */
    public List<hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch> internalEnergyHatches() {
        List<hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch> found = new ArrayList<>();
        for (BlockPos pos : getComponentPositions()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch hatch) {
                found.add(hatch);
            }
        }
        return found;
    }

    /**
     * Collects this machine's SMART INTERFACES, for the requirement that reads a published value (PLAN 663, B4).
     *
     * <p>The same "internal" channel {@link #internalEnergyHatches()} and {@link #internalFluidTanks()} use,
     * and for the same reason: a capability is the <b>external</b> contract, and a smart interface's value is
     * not a capability at all — it is state on the tile that the machine's own recipe requirements read. There
     * is nothing to resolve through {@code getCapability}, so the tile is returned.</p>
     *
     * <p><b>THIS IS THE PORT'S EQUIVALENT OF "THE PROVIDER IS A MACHINE COMPONENT".</b> Upstream's
     * {@code SmartInterfaceProvider} is a {@code MachineComponent}, so the recipe machinery hands it to a
     * requirement and {@code RequirementInterfaceNumInput} reads
     * {@code provider.getMachineData(type)} (upstream U76-U77). The port has no {@code MachineComponent} type,
     * so the reachability comes from the other half of upstream's shape: the interface block entity implements
     * {@code MachineComponentTile} and this class resolves exactly those tiles out of the pattern — which the
     * same formation walk populated {@code setControllerPos} on. From the tile the requirement asks for
     * {@code provideComponent()}, which is upstream's own call site in the packet handler and the screen.</p>
     *
     * @return the smart interfaces among this machine's components, in declaration order
     */
    public List<hellfirepvp.modularmachinery.common.tiles.TileSmartInterface> smartInterfaces() {
        List<hellfirepvp.modularmachinery.common.tiles.TileSmartInterface> found = new ArrayList<>();
        for (BlockPos pos : getComponentPositions()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.TileSmartInterface iface) {
                found.add(iface);
            }
        }
        return found;
    }

    /**
     * Collects this machine's fluid tanks themselves, for the machine's <b>internal</b> use.
     *
     * <p>The same distinction {@link #internalEnergyHatches()} makes, for the same reason: an input
     * hatch's {@code IFluidHandler} refuses {@code drain} so a neighbouring machine cannot steal the
     * fluid its owner is collecting, which also stops the owner from spending it. The capability is the
     * <b>external</b> contract; this returns the tiles so the internal channel can be used.
     *
     * @return the fluid tanks among this machine's components
     */
    public List<hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank> internalFluidTanks() {
        List<hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank> found = new ArrayList<>();
        for (BlockPos pos : getComponentPositions()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank tank) {
                found.add(tank);
            }
        }
        return found;
    }

    /** Recipe input handlers, including ME caches, with ordinary hatch direction gates bypassed internally. */
    public List<net.minecraftforge.fluids.capability.IFluidHandler> internalFluidInputs() {
        return capabilities(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, ComponentKind.INPUT, true);
    }

    /** Recipe output handlers, including ME buses and the owning machine's hatch fill access. */
    public List<net.minecraftforge.fluids.capability.IFluidHandler> internalFluidOutputs() {
        return capabilities(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, ComponentKind.OUTPUT, true);
    }

    public net.minecraftforge.fluids.capability.IFluidHandler internalFluidView(BlockPos pos,
            hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank tank) {
        return root.fluidInputViews.computeIfAbsent(pos, ignored -> new net.minecraftforge.fluids.capability.IFluidHandler() {
                    public int getTanks() { return tank.getTank().getTanks(); }
                    public net.minecraftforge.fluids.FluidStack getFluidInTank(int index) { return tank.getTank().getFluidInTank(index); }
                    public int getTankCapacity(int index) { return tank.getTank().getTankCapacity(index); }
                    public boolean isFluidValid(int index, net.minecraftforge.fluids.FluidStack stack) {
                        return index == 0 && !stack.isEmpty();
                    }
                    public int fill(net.minecraftforge.fluids.FluidStack stack, FluidAction action) {
                        return tank.getIOType() == IOType.OUTPUT ? tank.produceInternal(stack, action) : 0;
                    }
                    public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.fluids.FluidStack stack, FluidAction action) { return tank.consumeInternal(stack, action); }
                    public net.minecraftforge.fluids.FluidStack drain(int amount, FluidAction action) {
                        return tank.consumeInternal(new net.minecraftforge.fluids.FluidStack(tank.getTank().getFluid(), amount), action);
                    }
        });
    }

    /**
     * Replaces this context's component list with an explicit one.
     *
     * <p>Exists for the self-test, which needs to drive a requirement against <b>one specific</b> block
     * entity it just placed. The alternative — building a whole formed machine — would test the
     * requirement only indirectly, and would fail for a reason (structure assembly) unrelated to what is
     * being checked.
     *
     * <p>Not used by the engine: {@link #getComponentPositions()} is otherwise derived from the
     * controller's bound components during structure assembly.
     */
    public void overrideComponentPositions(List<BlockPos> positions) {
        this.componentPositions = new ArrayList<>(positions);
    }

    /**
     * Collects every capability of the given type exposed by this machine's components.
     *
     * @param capability the capability to gather, e.g. {@code ForgeCapabilities.ITEM_HANDLER}
     * @param filter     which components to include
     * @return the live handlers in declaration order; empty when the machine has no such component
     */
    public <T> List<T> capabilities(Capability<T> capability, ComponentKind filter) {
        return capabilities(capability, filter, false);
    }

    private <T> List<T> capabilities(Capability<T> capability, ComponentKind filter, boolean internalFluidInput) {
        List<T> handlers = new ArrayList<>();
        for (BlockPos pos : getComponentPositions()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MachineComponentTile component)) {
                continue;
            }
            if (!filter.accepts(component)) {
                continue;
            }
            if (internalFluidInput && be instanceof hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank tank) {
                @SuppressWarnings("unchecked") T typed = (T) internalFluidView(pos, tank);
                handlers.add(typed);
                continue;
            }
            // Source MMCE exposes one machine component per pattern slot while its provider is in
            // ISOLATION_INPUT mode.  Keep this bridge AE2-free: the provider decides when the list is active,
            // and the context simply adds each live capability to the same requirement scan.
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.PatternIsolationProvider isolated) {
                BlockPos groupPos = isolated.getIsolationGroupPosition() != null
                        ? isolated.getIsolationGroupPosition() : pos;
                if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                    var isolatedHandlers = isolated.getIsolatedItemHandlers();
                    for (int i = 0; i < isolatedHandlers.size(); i++) {
                        if (root.isolationGroupKey != null
                                && !isolationGroupKey(groupPos, i).equals(root.isolationGroupKey)) continue;
                        @SuppressWarnings("unchecked") T typed = (T) isolatedHandlers.get(i);
                        handlers.add(typed);
                    }
                    if (!isolatedHandlers.isEmpty()) {
                        continue;
                    }
                    // A pinned provider leaving isolation mode must not expose its normal cache to that run.
                    if (root.isolationGroupKey != null && root.isolationGroupKey.startsWith(groupPos.asLong() + ":")) continue;
                }
                if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER) {
                    var isolatedHandlers = isolated.getIsolatedFluidHandlers();
                    for (int i = 0; i < isolatedHandlers.size(); i++) {
                        if (root.isolationGroupKey != null
                                && !isolationGroupKey(groupPos, i).equals(root.isolationGroupKey)) continue;
                        @SuppressWarnings("unchecked") T typed = (T) isolatedHandlers.get(i);
                        handlers.add(typed);
                    }
                    if (!isolatedHandlers.isEmpty()) {
                        continue;
                    }
                    if (root.isolationGroupKey != null && root.isolationGroupKey.startsWith(groupPos.asLong() + ":")) continue;
                }
            }
            // Source MMCE separates a component's machine provider from its
            // public Forge capability.  In particular, MEPatternProvider's
            // public ITEM_HANDLER is its encoded-pattern inventory, while the
            // machine must consume the cached ingredients from provideComponent().
            // This bridge is deliberately after PatternIsolationProvider: in
            // isolation mode the 36 selected handlers must win over the shared
            // normal-mode cache.
            if (be instanceof hellfirepvp.modularmachinery.common.tiles.MachineInputHandlerProvider machineInput) {
                if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                    @SuppressWarnings("unchecked") T typed = (T) machineInput.machineItemHandler();
                    if (typed != null) handlers.add(typed);
                    continue;
                }
                if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER) {
                    @SuppressWarnings("unchecked") T typed = (T) machineInput.machineFluidHandler();
                    if (typed != null) handlers.add(typed);
                    continue;
                }
            }
            LazyOptional<T> optional = be.getCapability(capability);
            // orElse(null) rather than isPresent() then get(): the optional may be invalidated between
            // the two calls, and resolving once removes that window.
            T handler = optional.orElse(null);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        // Source addComponent deduplicates InfItemFluidHandler instances. Mirrors
        // expose the very same live views, so count each store once per inspection.
        // Do this after selector filtering: a selected mirror still reaches its store.
        java.util.Set<T> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        handlers.removeIf(handler -> !seen.add(handler));
        return handlers;
    }

    /**
     * Which components a requirement should draw from.
     *
     * <p>The direction matters and is not cosmetic: on 1.20.1 {@code IFluidHandler} answers per side and
     * {@code IItemHandler} implements its own {@code extractItem}, so a machine that drew from its
     * output bus would <b>void the product it just made</b>. Filtering by the component's own IOType is
     * what prevents that.
     */
    public enum ComponentKind {
        /** Input busses, input hatches: anything a recipe legitimately consumes from. */
        INPUT,
        /** Output busses, output hatches: anything a recipe legitimately produces into. */
        OUTPUT,
        /** Every component, for diagnostics. */
        ANY;

        boolean accepts(MachineComponentTile component) {
            if (this == ANY) {
                return true;
            }
            // Read through the interface rather than reflection. The earlier reflective version called a
            // public method on a subclass of a package-private class, which threw
            // IllegalAccessException; the catch treated that as "no direction", so every component was
            // directionless, INPUT matched nothing, and requirements could never find their hatches.
            IOType type = component.getIOType();
            if (type == null) {
                return true;
            }
            return (this == INPUT && type == IOType.INPUT) || (this == OUTPUT && type == IOType.OUTPUT);
        }
    }
}
