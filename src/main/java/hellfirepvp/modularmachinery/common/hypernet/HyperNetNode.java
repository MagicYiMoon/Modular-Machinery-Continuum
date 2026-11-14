/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO old.ComputationCenter/NetNode/DataProcessor, b37fbb53,
 * KasumiNova and contributors. New loaded-only, server-thread lifetime implementation.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.LongToDoubleFunction;

/** A runtime belongs to one exact loaded BE/definition. It never requests chunks or persists credit. */
public final class HyperNetNode implements AutoCloseable {
    public enum ConnectResult { SUCCESS, RETIRED, NOT_FORMED, UNKNOWN_CENTER, WRONG_DIMENSION,
        NO_PERMISSION, CENTER_STOPPED, CONNECTION_LIMIT, TYPE_LIMIT, INVALID_BINDING }
    private static final ComputationBudget.Allocation NONE = new ComputationBudget.Allocation(0, Map.of());
    private final TileController owner;
    private final ServerLevel world;
    private final DynamicMachine machine;
    private final HyperNetDefinition definition;
    private final long revision;
    private final long createdTick;
    private final UUID providerId;
    private final Map<BlockPos, HyperNetNode> connections = new LinkedHashMap<>();
    private final LinkedHashMap<BlockPos,HyperNetMonitor.Member> monitorMembers=new LinkedHashMap<>();
    private boolean monitorRosterLimited;
    private final ComputationBudget budget;
    private final HyperNetHardware hardware;
    private final HyperNetThermalState thermal;
    private final HyperNetCircuitState circuit;
    private final HyperNetResearchStation researchStation;
    private HyperNetNode center;
    private ComputationBudget.Provider lease;
    private LongToDoubleFunction supply;
    private long sampledTick = Long.MIN_VALUE;
    private long creditedTick = Long.MIN_VALUE;
    private UUID creditedCenter;
    private double sampledSupply;
    private final HyperNetStatistics statistics = new HyperNetStatistics();
    private final HyperNetLoadAverage loadAverage;
    private final HyperNetDemand demand = new HyperNetDemand();
    private final UUID schedulingOwner = UUID.randomUUID();
    private final ComputationFairScheduler scheduler = new ComputationFairScheduler();
    private long statusTick=Long.MIN_VALUE;
    private boolean statusWorking;
    private boolean closed;
    private HyperNetDatabaseState databaseState;
    private long databaseRevision = Long.MIN_VALUE;
    private int databaseEnergyCount=-1;
    private hellfirepvp.modularmachinery.common.modifier.RecipeModifier databaseEnergyModifier;

    public HyperNetNode(TileController owner, DynamicMachine machine) {
        this.owner = owner; world = (ServerLevel)owner.getLevel(); requireThread();
        this.machine = machine; definition = Objects.requireNonNull(machine.getHyperNetDefinition());
        loadAverage = definition.role() == HyperNetDefinition.Role.PROCESSOR ? new HyperNetLoadAverage() : null;
        revision = MachineReloadListener.getServerDefinitionRevision();
        createdTick = world.getGameTime();
        providerId = UUID.nameUUIDFromBytes((world.dimension().location()+"/"+owner.getBlockPos()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        budget = definition.role() == HyperNetDefinition.Role.CENTER ? new ComputationBudget() : null;
        thermal=definition.thermal()==null?null:new HyperNetThermalState(owner,definition.thermal(),machine.getRegistryName());
        circuit=definition.circuit()==null?null:new HyperNetCircuitState(owner,definition.circuit(),machine.getRegistryName());
        hardware = definition.modules().isEmpty() ? null : new HyperNetHardware(owner, definition,thermal);
        researchStation=definition.role()==HyperNetDefinition.Role.RESEARCH_STATION
                ? new HyperNetResearchStation(this,owner,machine.getRegistryName(),definition.maxTechLevel()):null;
        HyperNetLifecycle.track(this);
    }
    public static void tickController(TileController owner) {
        if (owner.getLevel() instanceof ServerLevel) {
            var node = owner.getHyperNetNode(); if (node != null) node.tick();
        }
    }
    public boolean matches(DynamicMachine current) {
        return !closed && current == machine && current.getHyperNetDefinition() == definition
                && revision == MachineReloadListener.getServerDefinitionRevision();
    }
    private void requireThread() {
        if (!world.getServer().isSameThread()) throw new IllegalStateException("HyperNet accessed outside server thread");
    }
    private boolean current() {
        if (closed || owner.isRemoved() || !matches(MachineReloadListener.serverDefinitions().machines().findByName(owner.getMachineName()))) return false;
        var chunk = world.getChunkSource().getChunkNow(owner.getBlockPos().getX() >> 4, owner.getBlockPos().getZ() >> 4);
        return chunk != null && world.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(owner.getBlockPos()))
                && chunk.getBlockEntity(owner.getBlockPos(), net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) == owner;
    }
    private boolean formed() {
        return current() && owner.isFormed() && !owner.isStructureCheckRequested()
                && owner.isStructureChunkLoaded() && !owner.isRedstoneStopped();
    }
    public boolean working() {
        requireThread();
        if (!current()) { close(); return false; }
        if (!formed()) return false;
        if(thermal!=null&&thermal.overheated())return false;
        if(circuit!=null&&!circuit.permitsWork())return false;
        if(definition.role()==HyperNetDefinition.Role.DATABASE&&(!database().valid()||!researchNetworkAvailable()))return false;
        if(definition.role()==HyperNetDefinition.Role.TERMINAL)return owner instanceof TileHyperNetTerminal terminal&&terminal.servicePowered();
        if (definition.role() == HyperNetDefinition.Role.NODE) return true;
        if (hardware != null && !hardware.ready()) return false;
        return owner instanceof TileFactoryController factory && factory.getFactoryRecipeThreadList().stream()
                .anyMatch(thread -> thread.isPresetThread() && thread.getThreadName().equals(definition.workingThread()) && thread.isWorking());
    }
    public void tick() {
        requireThread();
        if (!current()) { close(); return; }
        demand.update(world.getGameTime(), definition.role() == HyperNetDefinition.Role.NODE && formed()
                ? ComputationTickIO.activeDemand(owner) : HyperNetDemand.EMPTY);
        if (center != null && center.definition.fairScheduling())
            center.scheduler.retain(schedulingOwner, formed() ? ComputationTickIO.activeTickets(owner) : Set.of());
        if(thermal!=null)thermal.refresh();
        statistics.advance(world.getGameTime());
        statusWorking=working();statusTick=world.getGameTime();
        if (loadAverage != null) loadAverage.sample(statusTick, statusWorking, statistics.completedConsumption(statusTick));
        if (budget != null) {
            if (!statusWorking) { disconnectAll(); budget.stop(); }
            else prepareBudget();
        } else {
            if (!formed() || center != null && !center.working()) detach();
            if (formed() && center == null) reconnect();
            // No implicit supply: a module backend must report actual valid supply.
            if (center != null && definition.role() == HyperNetDefinition.Role.PROCESSOR && !statusWorking)
                revokeSupply();
        }
    }
    /** Server-side connection entry; the later card/GUI layer must supply the real interacting player UUID. */
    public ConnectResult connect(BlockPos position, UUID actor) {
        requireThread();
        if (!current()) return ConnectResult.RETIRED;
        if (!formed() || budget != null) return ConnectResult.NOT_FORMED;
        if (actor == null || owner.getOwner() != null && !actor.equals(owner.getOwner())) return ConnectResult.NO_PERMISSION;
        var target = at(position);
        if (target == null || target.budget == null) return ConnectResult.UNKNOWN_CENTER;
        var data = target.owner.getHyperNetData();
        if (data.hasUUID("networkOwner") && !actor.equals(data.getUUID("networkOwner"))) return ConnectResult.NO_PERMISSION;
        var result = target.admit(this);
        if (result != ConnectResult.SUCCESS) return result;
        if (!data.hasUUID("networkOwner")) { data.putUUID("networkOwner", actor); target.owner.setHyperNetData(data); }
        var saved = owner.getHyperNetData(); var binding = new CompoundTag();
        binding.putString("dimension", world.dimension().location().toString()); binding.putLong("pos", position.asLong());
        binding.putString("machine", target.machine.getRegistryName()); binding.putUUID("networkOwner", actor);
        saved.put("binding", binding); owner.setHyperNetData(saved);
        return result;
    }

    /** Claiming a formed center does not require its forwarding recipe to be running. */
    public ConnectResult claimCenter(UUID actor) {
        requireThread();
        if (!current()) return ConnectResult.RETIRED;
        if (!formed() || budget == null) return ConnectResult.NOT_FORMED;
        if (actor == null || owner.getOwner() != null && !actor.equals(owner.getOwner())) return ConnectResult.NO_PERMISSION;
        var data = owner.getHyperNetData();
        if (data.hasUUID("networkOwner") && !actor.equals(data.getUUID("networkOwner"))) return ConnectResult.NO_PERMISSION;
        if (!data.hasUUID("networkOwner")) { data.putUUID("networkOwner", actor); owner.setHyperNetData(data); }
        return ConnectResult.SUCCESS;
    }

    /** Gate only the designated service core. Other maintenance/cooling threads remain available. */
    public static boolean permitsCoreWork(TileController controller, FactoryRecipeThread thread) {
        return coreWorkFailure(controller,thread)==null;
    }
    public static String coreWorkFailure(TileController controller, FactoryRecipeThread thread) {
        var node = controller.getHyperNetNode();
        if(node==null||thread==null||!thread.isPresetThread()||!thread.getThreadName().equals(node.definition.workingThread()))return null;
        if(node.thermal!=null&&node.thermal.overheated())return "modularmachinery.hypernet.overheated";
        if(node.circuit!=null&&!node.circuit.permitsWork())return "modularmachinery.hypernet.circuit_low";
        if(node.researchStation!=null)return node.researchStation.failure(true);
        if(node.definition.role()==HyperNetDefinition.Role.DATABASE) {
            if(!node.database().valid())return "modularmachinery.hypernet.database.invalid_data";
            if(!node.researchNetworkAvailable())return HyperNetResearchStation.PREFIX+"network";
        }
        return node.hardware==null||node.hardware.ready()?null:"modularmachinery.hypernet.missing_modules";
    }
    private boolean isDatabaseCore(FactoryRecipeThread thread) {
        return definition.role()==HyperNetDefinition.Role.DATABASE&&thread!=null&&thread.isPresetThread()
                &&thread.getThreadName().equals(definition.workingThread());
    }
    /** Read-only transient overlay: no named user modifier, saved multiplier or network creation. */
    public static List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> coreModifiers(
            TileFactoryController factory,FactoryRecipeThread thread,
            List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> authored) {
        if(factory==null||!(factory.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread())return authored;
        var node=factory.peekHyperNetNode();
        if(node==null||!node.isDatabaseCore(thread)||!node.current())return authored;
        int count=node.database().completedCount();if(count==0)return authored;
        if(node.databaseEnergyCount!=count){
            node.databaseEnergyCount=count;
            node.databaseEnergyModifier=new hellfirepvp.modularmachinery.common.modifier.RecipeModifier(
                    "modularmachinery:energy",IOType.INPUT,Math.max(1F,1F+count*0.1F),
                    hellfirepvp.modularmachinery.common.modifier.RecipeModifier.OPERATION_MULTIPLY,false);
        }
        var result=new ArrayList<>(authored);result.add(node.databaseEnergyModifier);return result;
    }
    /** Upstream Database.onWorkingTick resets the service core before each real work attempt. */
    public static void prepareDatabaseCoreTick(TileFactoryController factory,FactoryRecipeThread thread) {
        var node=factory.getHyperNetNode();if(node!=null&&node.isDatabaseCore(thread))thread.setServiceRecipeProgress(0);
    }
    public static boolean holdDatabaseCore(TileFactoryController factory,FactoryRecipeThread thread) {
        var node=factory.getHyperNetNode();if(node==null||!node.isDatabaseCore(thread))return false;
        thread.setServiceRecipeProgress(Math.min(1,thread.getRecipeDuration()-1));return true;
    }
    /** Called only after a real core work tick, including its final tick; never on an output wait. */
    public static void onCoreWorked(TileController controller,FactoryRecipeThread thread) {
        var node=controller.getHyperNetNode();
        if(node==null||node.circuit==null||thread==null||!thread.isPresetThread()
                ||!thread.getThreadName().equals(node.definition.workingThread())||!node.circuit.wearDue()||!node.working())return;
        node.requireThread();node.prune();double generation=0;
        for(var provider:node.connections.values()) if(provider.definition.role()==HyperNetDefinition.Role.PROCESSOR&&provider.working()) {
            double provision=provider.hardware==null?provider.sampledSupply:provider.hardware.provision();
            if(Double.isFinite(provision)&&provision>0)generation=Math.min(node.definition.carrying(),generation+provision);
        }
        node.circuit.wear((double)node.connections.size()/node.definition.maxConnections()+generation/node.definition.carrying());
        if(!node.circuit.permitsWork()){node.disconnectAll();node.budget.stop();}
    }
    public record ResearchTaskSnapshot(String id,java.math.BigDecimal progress,boolean awaitingCoreCompletion,boolean valid) {}
    boolean isResearchCore(FactoryRecipeThread thread) {
        return researchStation!=null&&thread!=null&&thread.isPresetThread()&&thread.getThreadName().equals(definition.workingThread());
    }
    /** Trusted server task entry. A future terminal must provide its authenticated player's UUID. */
    public String provideResearchTask(String id,UUID actor) {
        requireThread();return researchStation==null?HyperNetResearchStation.PREFIX+"not_station":researchStation.provide(id,actor);
    }
    /** Creative-mode and OP permissions are checked against the real server actor, never packet fields. */
    public String provideCreativeResearchTask(String id,net.minecraft.server.level.ServerPlayer actor){
        requireThread();return researchStation==null?HyperNetResearchStation.PREFIX+"not_station":researchStation.provideCreative(id,actor);
    }
    public boolean cancelResearchTask(UUID actor) {requireThread();return researchStation!=null&&researchStation.cancel(actor);}
    public ResearchTaskSnapshot researchTask() {
        requireThread();return researchStation==null||!current()?new ResearchTaskSnapshot("",java.math.BigDecimal.ZERO,false,false):researchStation.snapshot();
    }
    boolean canManageResearch(UUID actor) {
        if(!current()||actor==null||owner.getOwner()!=null&&!actor.equals(owner.getOwner()))return false;
        var binding=owner.getHyperNetBinding();return binding.hasUUID("networkOwner")&&actor.equals(binding.getUUID("networkOwner"));
    }
    boolean researchNetworkAvailable() {
        return formed()&&center!=null&&center.working()&&bindingMatches(center)&&center.connections.get(owner.getBlockPos())==this;
    }
    List<HyperNetNode> researchDatabases() {
        var target=budget!=null?this:center;
        if(!formed()||target==null||!target.working()||target!=this&&!bindingMatches(target))return List.of();
        var result=new ArrayList<HyperNetNode>();
        for(var peer:List.copyOf(target.connections.values()))if(peer.definition.role()==HyperNetDefinition.Role.DATABASE
                &&peer.center==target&&peer.bindingMatches(target)&&peer.working()&&peer.database().valid())result.add(peer);
        return result;
    }
    boolean canResearchCompute(java.math.BigDecimal amount) {return researchNetworkAvailable()&&center.request(amount,true).granted()>0;}
    java.math.BigDecimal availableResearchComputation() {
        if(!researchNetworkAvailable())return java.math.BigDecimal.ZERO;
        center.prepareBudget();return center.budget.availableExact();
    }
    /** Called only after the native core's ordinary per-tick inputs have been paid. */
    public static String workResearchCore(TileController controller,FactoryRecipeThread thread) {
        var node=controller.getHyperNetNode();return node!=null&&node.isResearchCore(thread)?node.researchStation.work():null;
    }
    public static boolean alignResearchCore(TileController controller,FactoryRecipeThread thread) {
        var node=controller.getHyperNetNode();return node!=null&&node.isResearchCore(thread)&&node.researchStation.align(thread);
    }
    public static void finishResearchCore(TileController controller,FactoryRecipeThread thread) {
        var node=controller.getHyperNetNode();if(node!=null&&node.isResearchCore(thread))node.researchStation.finished();
    }
    void finishResearchTick() {requireThread();if(researchStation!=null&&current())researchStation.extra();}
    public double storedHeat(){requireThread();return current()&&thermal!=null?thermal.stored().doubleValue():0;}
    public boolean overheated(){requireThread();return current()&&thermal!=null&&thermal.overheated();}
    public int circuitDurability(){requireThread();return current()&&circuit!=null?circuit.stored():0;}
    String maintenanceFailure(java.math.BigDecimal cooling,java.math.BigDecimal repair,int coolingCopies,boolean start,
                              boolean hasCooling,boolean hasRepair) {
        requireThread();
        if(!formed()||hasCooling&&(thermal==null||definition.role()!=HyperNetDefinition.Role.PROCESSOR)
                ||hasRepair&&(circuit==null||definition.role()!=HyperNetDefinition.Role.CENTER))
            return HyperNetMaintenance.INVALID;
        if(!start)return null;
        if(hasCooling) {
            if(thermal.stored().compareTo(cooling)<0)return HyperNetMaintenance.HEAT;
            if(!definition.thermal().dynamicPattern().isEmpty()) {
                var pattern=owner.getDynamicPattern(definition.thermal().dynamicPattern());
                if(coolingCopies>(pattern==null?1:Math.max(1,pattern.getSize())))return HyperNetMaintenance.SEGMENTS;
            }
        }
        return hasRepair&&repair.compareTo(java.math.BigDecimal.valueOf(circuit.room()))>0?HyperNetMaintenance.ROOM:null;
    }
    void maintain(java.math.BigDecimal cooling,java.math.BigDecimal repair,boolean hasCooling,boolean hasRepair) {
        requireThread();
        if(hasCooling)thermal.remove(cooling);
        if(hasRepair)circuit.repair(repair);
    }
    public void disconnect() {
        requireThread(); detach(); var data = owner.getHyperNetData(); data.remove("binding"); owner.setHyperNetData(data);
    }
    private ConnectResult reconnect() {
        var binding = owner.getHyperNetBinding();
        if (binding.isEmpty()) return ConnectResult.INVALID_BINDING;
        if (!binding.getString("dimension").equals(world.dimension().location().toString())) return ConnectResult.WRONG_DIMENSION;
        if (!binding.contains("pos", net.minecraft.nbt.Tag.TAG_LONG) || !binding.hasUUID("networkOwner")) return ConnectResult.INVALID_BINDING;
        var target = at(BlockPos.of(binding.getLong("pos")));
        if (target == null || target.budget == null || !target.machine.getRegistryName().equals(binding.getString("machine"))) return ConnectResult.UNKNOWN_CENTER;
        var saved = target.owner.getHyperNetData();
        if (!saved.hasUUID("networkOwner") || !saved.getUUID("networkOwner").equals(binding.getUUID("networkOwner"))) return ConnectResult.NO_PERMISSION;
        return target.admit(this);
    }
    private HyperNetNode at(BlockPos position) {
        var chunk = world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !world.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(position))) return null;
        var entity = chunk.getBlockEntity(position, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        return entity instanceof TileController controller ? controller.getHyperNetNode() : null;
    }
    private void prune() {
        for (var node : List.copyOf(connections.values())) if (!node.formed() || node.center != this || !node.bindingMatches(this)) node.detach();
    }
    private ConnectResult admit(HyperNetNode node) {
        if (!working()) return ConnectResult.CENTER_STOPPED;
        prune();
        var existing = connections.get(node.owner.getBlockPos());
        if (existing != null && existing != node) existing.detach();
        if (connections.get(node.owner.getBlockPos()) == node) { remember(node); return ConnectResult.SUCCESS; }
        if((node.definition.role()==HyperNetDefinition.Role.RESEARCH_STATION||node.definition.role()==HyperNetDefinition.Role.TERMINAL)
                &&connections.values().stream().anyMatch(n->n.definition.role()==node.definition.role()))
            return ConnectResult.TYPE_LIMIT;
        if (connections.size() >= definition.maxConnections()) return ConnectResult.CONNECTION_LIMIT;
        if (connections.values().stream().filter(n -> n.definition.role() == node.definition.role()).count() >= definition.maxNodesPerType())
            return ConnectResult.TYPE_LIMIT;
        node.detach(); node.center = this; connections.put(node.owner.getBlockPos(), node);
        remember(node);
        if (node.definition.role() == HyperNetDefinition.Role.PROCESSOR) node.lease = budget.attach(node.providerId);
        return ConnectResult.SUCCESS;
    }
    private void remember(HyperNetNode node) {
        monitorMembers.remove(node.owner.getBlockPos());
        if(monitorMembers.size()>=HyperNetMonitor.MAX_MEMBERS){monitorMembers.remove(monitorMembers.keySet().iterator().next());monitorRosterLimited=true;}
        monitorMembers.put(node.owner.getBlockPos(),HyperNetMonitor.member(node.owner,node.machine));
    }
    private void revokeSupply() {
        if (lease != null) { center.budget.detach(lease); lease = null; }
    }
    private void detach() {
        if (center != null) {
            revokeSupply(); center.scheduler.removeOwner(schedulingOwner); center.connections.remove(owner.getBlockPos(), this); center = null;
        }
    }
    private void disconnectAll() { for (var node : List.copyOf(connections.values())) node.detach(); scheduler.clear(); }
    private void prepareBudget() {
        // Replacing/reloading a centre must not recreate already-spent credit in the same tick.
        if (world.getGameTime() <= createdTick) return;
        statistics.advance(world.getGameTime());
        budget.beginTick(world.getGameTime(), true, definition.carrying()); prune();
        for (var node : List.copyOf(connections.values())) if (node.definition.role() == HyperNetDefinition.Role.PROCESSOR) {
            if (!node.working() || node.supply == null && node.hardware == null) { node.revokeSupply(); continue; }
            // Replacing a processor or moving it between networks cannot duplicate physical supply.
            // A mid-tick network switch resumes generation on the next tick.
            if (world.getGameTime() <= node.createdTick || node.creditedTick == world.getGameTime()
                    && !providerId.equals(node.creditedCenter)) { node.revokeSupply(); continue; }
            if (node.lease == null) node.lease = budget.attach(node.providerId);
            // A backend is internal server code; no JSON constant or client report manufactures supply.
            if (node.sampledTick != world.getGameTime()) {
                node.sampledTick = world.getGameTime();
                node.sampledSupply = node.hardware == null ? node.supply.applyAsDouble(world.getGameTime()) : node.hardware.remaining().doubleValue();
            }
            double amount = node.sampledSupply;
            if (!Double.isFinite(amount) || amount < 0) { node.revokeSupply(); continue; }
            boolean published = node.hardware == null ? budget.publish(node.lease, amount) : budget.publish(node.lease, node.hardware.remaining());
            if (published) {
                node.creditedTick = world.getGameTime(); node.creditedCenter = providerId;
            }
            if (node.hardware != null) budget.restrict(node.lease, node.hardware.remaining());
        }
        statistics.offer(world.getGameTime(), Math.min(definition.carrying(),budget.available()+statistics.current()));
    }
    /** Package-private accounting fixture seam; disabled when physical modules are configured. */
    void processorSupply(LongToDoubleFunction source) {
        requireThread();
        if (closed || definition.role() != HyperNetDefinition.Role.PROCESSOR || hardware != null) throw new IllegalStateException("Not a test-seam processor");
        if (source != supply) { if (center != null) revokeSupply(); supply = source; }
    }
    public double available() {
        requireThread();
        if (!working()) return 0;
        if (budget != null) { prepareBudget(); return budget.available(); }
        var target = connectedCenter(); return target == null ? 0 : target.available();
    }
    public ComputationBudget.Allocation request(double amount, boolean simulate) {
        requireThread();
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid computation request");
        return request(java.math.BigDecimal.valueOf(amount), simulate);
    }
    /** Startup-only union over currently admitted, working databases. Does not reconnect or spend FE/compute. */
    public String researchFailure(List<String> ids) {
        requireThread();
        for (var id : ids) if (HyperNetResearchRegistry.find(id) == null) return "craftcheck.failure.research.unknown";
        if (!working()) return "craftcheck.failure.research.network";
        var target = budget != null ? this : center;
        if (target == null || !target.working() || target != this && !bindingMatches(target)) return "craftcheck.failure.research.network";
        var missing = new HashSet<>(ids); boolean database = false;
        for (var peer : List.copyOf(target.connections.values())) {
            if (peer.definition.role() != HyperNetDefinition.Role.DATABASE || peer.center != target
                    || !peer.bindingMatches(target) || !peer.working()) continue;
            var ledger = peer.database();
            if (!ledger.valid()) continue;
            database = true; missing.removeIf(ledger::has);
        }
        return !database ? "craftcheck.failure.research.database" : missing.isEmpty() ? null : "craftcheck.failure.research.missing";
    }
    HyperNetDatabaseState database() {
        if (databaseState == null || databaseRevision != owner.getHyperNetDataRevision()) {
            databaseState = new HyperNetDatabaseState(machine.getRegistryName(),definition.storageCapacity(),owner.getHyperNetDataPart("database"));
            databaseRevision = owner.getHyperNetDataRevision();
        }
        return databaseState;
    }
    private boolean writableDatabase() {
        return definition.role() == HyperNetDefinition.Role.DATABASE && working() && center != null && center.working() && bindingMatches(center);
    }
    /** Trusted server integration API; callers must perform research work and dependency checks first. */
    public boolean writeResearchProgress(String id, java.math.BigDecimal points) {
        requireThread(); if (!writableDatabase()) return false;
        var ledger = database(); if (!ledger.write(id,points)) return false;
        saveDatabase(ledger); return true;
    }
    /** Converts an existing fully researched slot to a completed record atomically. */
    public boolean completeResearch(String id) {
        requireThread(); if (!writableDatabase()) return false;
        var ledger = database(); if (!ledger.complete(id)) return false;
        saveDatabase(ledger); return true;
    }
    public java.math.BigDecimal researchProgress(String id) {
        requireThread(); return writableDatabase() ? database().progress(id) : null;
    }
    public boolean hasResearchSpace(String id) {
        requireThread(); return writableDatabase() && HyperNetResearchRegistry.find(id) != null && database().room(id);
    }
    void saveDatabase(HyperNetDatabaseState ledger) {
        var data = owner.getHyperNetData(); data.put("database",ledger.save()); owner.setHyperNetData(data);
    }
    public boolean isConsumerConnected() {
        requireThread();
        return definition.role() == HyperNetDefinition.Role.NODE && working() && connectedCenter() != null;
    }
    public ComputationBudget.Allocation request(java.math.BigDecimal amount, boolean simulate) {
        requireThread();
        if (amount == null || amount.signum() < 0 || amount.compareTo(java.math.BigDecimal.valueOf(Double.MAX_VALUE)) > 0
                || amount.signum() > 0 && amount.doubleValue() == 0)
            throw new IllegalArgumentException("Invalid computation request");
        if (!working()) return NONE;
        var target = budget != null ? this : connectedCenter();
        if (target == null || !target.working()) return NONE;
        target.prepareBudget();
        var allocation = target.budget.request(amount, true);
        if (allocation.granted() == 0) return allocation;
        var payments = new ArrayList<HyperNetHardware.Payment>();
        for (var share : allocation.exactShares().entrySet()) {
            var provider = target.connections.values().stream().filter(node -> node.providerId.equals(share.getKey())).findFirst().orElse(null);
            if (provider == null) return NONE;
            if (provider.hardware != null) {
                var payment = provider.hardware.plan(share.getValue());
                if (payment == null) return NONE;
                payments.add(payment);
            }
        }
        if (simulate) return allocation;
        for (int i = 0; i < payments.size(); i++) if (!payments.get(i).take()) {
            for (int j = 0; j < i; j++) payments.get(j).rollback();
            return NONE;
        }
        var result = target.budget.request(amount, false);
        if (result.granted() == 0) { payments.forEach(HyperNetHardware.Payment::rollback); return result; }
        payments.forEach(HyperNetHardware.Payment::commit);
        long now=world.getGameTime();target.statistics.commit(now,amount);
        if(this!=target)statistics.commit(now,amount);
        for(var share:result.exactShares().entrySet()) {
            var provider=target.connections.values().stream().filter(n->n.providerId.equals(share.getKey())).findFirst().orElse(null);
            if(provider!=null && provider!=this)provider.statistics.commit(now,share.getValue());
        }
        return result;
    }
    /** Fair-mode admission uses the whole published tick offer, without enqueueing or spending it. */
    boolean canStartWork(java.math.BigDecimal amount) {
        requireThread();var target = connectedCenter();
        if (target == null || !working()) return false;
        target.prepareBudget();
        return target.definition.fairScheduling()
                ? amount.compareTo(target.budget.availableExact().add(target.statistics.currentExact())) <= 0
                : request(amount, true).granted() > 0;
    }
    /** Native running recipes only. Candidate checks and direct API requests do not enqueue. */
    String workFailure(UUID key, java.math.BigDecimal amount) {
        requireThread();
        var target = connectedCenter();
        if (target == null || !working()) return ComputationTickIO.DISCONNECTED;
        target.prepareBudget();
        if (!target.definition.fairScheduling())
            return request(amount, true).granted() > 0 ? null : ComputationTickIO.SHORTFALL;
        var available = target.budget.availableExact();
        var total = available.add(target.statistics.currentExact());
        if (amount.compareTo(total) > 0) { target.scheduler.abandon(key); return ComputationTickIO.SHORTFALL; }
        if (!target.scheduler.permits(world.getGameTime(), schedulingOwner, key, amount, available))
            return ComputationTickIO.QUEUED;
        if (request(amount, true).granted() == 0) {
            target.scheduler.abandon(key); return ComputationTickIO.SHORTFALL;
        }
        return null;
    }
    boolean executeWork(UUID key, java.math.BigDecimal amount) {
        if (workFailure(key, amount) != null) return false;
        var target = center;
        if (request(amount, false).granted() == 0) { abandonWork(key); return false; }
        if (target.definition.fairScheduling()) target.scheduler.granted(world.getGameTime(), key);
        return true;
    }
    void abandonWork(UUID key) { requireThread(); if (center != null) center.scheduler.abandon(key); }
    private HyperNetNode connectedCenter() {
        if (center != null && (!center.working() || !bindingMatches(center))) detach();
        if (center == null && formed()) reconnect();
        return center;
    }
    private boolean bindingMatches(HyperNetNode target) {
        var binding = owner.getHyperNetBinding();
        var data = target.owner.getHyperNetData();
        return binding.contains("pos", net.minecraft.nbt.Tag.TAG_LONG) && binding.getLong("pos") == target.owner.getBlockPos().asLong()
                && binding.getString("dimension").equals(world.dimension().location().toString())
                && binding.getString("machine").equals(target.machine.getRegistryName()) && binding.hasUUID("networkOwner")
                && data.hasUUID("networkOwner") && data.getUUID("networkOwner").equals(binding.getUUID("networkOwner"));
    }
    public record Status(HyperNetDefinition.Role role,boolean online,int connections,
                         double consumed,double offered,double heat,boolean overheated,int durability,
                         double demanded,int demandConsumers) {}
    /** Source processor TOP uses the 100-tick mean; retain separate exact last-tick statistics. */
    public double averageLoad() { return loadAverage != null && status().online() ? loadAverage.average() : 0; }
    private HyperNetDemand.Sample completedDemand(long now) {
        if (!formed()) return HyperNetDemand.EMPTY;
        if (budget == null) return definition.role() == HyperNetDefinition.Role.NODE ? demand.completed(now) : HyperNetDemand.EMPTY;
        var total = HyperNetDemand.EMPTY;
        // No pruning, reconnects, hardware refresh or chunk loads during inspection.
        for (var node : connections.values())
            if (node.definition.role() == HyperNetDefinition.Role.NODE && node.center == this && node.formed() && node.bindingMatches(this))
                total = total.add(node.demand.completed(now));
        return total;
    }
    /** Read-only: no reconnect, publication, hardware refresh, FE debit or chunk request. */
    public Status status() {
        requireThread();long now=world.getGameTime();boolean valid=current();
        var sample=statistics.completed(now);
        var requested=valid?completedDemand(now):HyperNetDemand.EMPTY;
        boolean online=valid&&formed()&&statusWorking&&now-statusTick>=0&&now-statusTick<=1
                &&(thermal==null||!thermal.peekOverheated())&&(circuit==null||circuit.permitsWork());
        online &= budget!=null || center!=null&&center.current()&&center.statusWorking;
        return new Status(definition.role(),online,online&&budget!=null?connections.size():0,
                valid?sample.consumed():0,valid?sample.offered():0,
                valid&&thermal!=null?thermal.peekStored().doubleValue():0,valid&&thermal!=null&&thermal.peekOverheated(),
                valid&&circuit!=null?circuit.stored():0,requested.displayedAmount(),requested.consumers());
    }
    // Inspection deliberately avoids working(), connectedCenter(), at(), refresh() and prepareBudget().
    TileController inspectionOwner(){requireThread();return owner;}
    HyperNetDefinition definition(){return definition;}
    boolean terminalConnected(){requireThread();return center!=null&&center.current()&&bindingMatches(center);}
    Object inspectionResearchTaskIdentity(){requireThread();return researchStation==null?null:researchStation.identity();}
    List<HyperNetNode> inspectionResearchPeers(){
        requireThread();if(budget==null||!current())return List.of();
        return connections.values().stream().filter(n->n.current()&&n.center==this&&n.bindingMatches(this)).toList();
    }
    List<HyperNetMonitor.Member> inspectionMembers(){requireThread();return List.copyOf(monitorMembers.values());}
    boolean inspectionRosterLimited(){return monitorRosterLimited;}
    int inspectionConnectionCount(){requireThread();return (int)connections.values().stream().filter(n->n.formed()&&n.center==this&&n.bindingMatches(this)).count();}
    boolean inspectionBindingMatches(TileController peer){
        var binding=peer.getHyperNetBinding();var data=owner.getHyperNetData();
        return binding.contains("pos",net.minecraft.nbt.Tag.TAG_LONG)&&binding.getLong("pos")==owner.getBlockPos().asLong()
                &&binding.getString("dimension").equals(world.dimension().location().toString())
                &&binding.getString("machine").equals(machine.getRegistryName())&&binding.hasUUID("networkOwner")
                &&data.hasUUID("networkOwner")&&binding.getUUID("networkOwner").equals(data.getUUID("networkOwner"));
    }
    HyperNetNode inspectionCenter(){
        requireThread();if(!current())return null;if(budget!=null)return this;
        var binding=owner.getHyperNetBinding();
        if(!binding.contains("pos",net.minecraft.nbt.Tag.TAG_LONG)||!binding.getString("dimension").equals(world.dimension().location().toString()))return null;
        var host=HyperNetMonitor.loaded(world,BlockPos.of(binding.getLong("pos")));var target=host==null?null:host.peekHyperNetNode();
        return target!=null&&target.current()&&target.budget!=null&&bindingMatches(target)?target:null;
    }
    double inspectionCapacity(){requireThread();return hardware==null?0:hardware.inspectionCapacity();}
    record Diagnostic(HyperNetMonitor.Fault fault,String detail){}
    Diagnostic inspectionFault(){
        requireThread();var fault=HyperNetMonitor.Fault.ONLINE;String detail="";long now=world.getGameTime();
        if(!current())fault=HyperNetMonitor.Fault.RETIRED;
        else if(thermal!=null&&thermal.peekOverheated())fault=HyperNetMonitor.Fault.OVERHEATED;
        else if(circuit!=null&&!circuit.permitsWork())fault=HyperNetMonitor.Fault.CIRCUIT_LOW;
        else if(statusTick==Long.MIN_VALUE||now-statusTick>1||now<statusTick)fault=HyperNetMonitor.Fault.SAMPLING;
        else if(hardware!=null&&!hardware.inspectionReady())fault=HyperNetMonitor.Fault.MODULES_MISSING;
        else if(definition.role()==HyperNetDefinition.Role.TERMINAL){
            if(!(owner instanceof TileHyperNetTerminal terminal)||!terminal.servicePowered()){
                fault=HyperNetMonitor.Fault.CORE_STOPPED;detail=owner instanceof TileHyperNetTerminal terminal?terminal.serviceFailure():"";
            }
        }
        else if(definition.role()!=HyperNetDefinition.Role.NODE){
            if(!(owner instanceof TileFactoryController factory))fault=HyperNetMonitor.Fault.FACTORY_REQUIRED;
            else {
                var core=factory.getFactoryRecipeThreadList().stream().filter(t->t.isPresetThread()&&t.getThreadName().equals(definition.workingThread())).findFirst().orElse(null);
                if(core==null)fault=HyperNetMonitor.Fault.CORE_MISSING;
                else if(!core.isWorking()){fault=HyperNetMonitor.Fault.CORE_STOPPED;detail=core.getBlockedReason();}
            }
        }
        if(fault!=HyperNetMonitor.Fault.ONLINE)return new Diagnostic(fault,detail==null?"":detail);
        if(budget==null){
            var binding=owner.getHyperNetBinding();
            if(!binding.contains("pos",net.minecraft.nbt.Tag.TAG_LONG))fault=HyperNetMonitor.Fault.UNBOUND;
            else if(!binding.getString("dimension").equals(world.dimension().location().toString()))fault=HyperNetMonitor.Fault.WRONG_DIMENSION;
            else {
                var host=HyperNetMonitor.loaded(world,BlockPos.of(binding.getLong("pos")));
                var target=host==null?null:host.peekHyperNetNode();
                if(target==null||!target.current()||target.budget==null)fault=HyperNetMonitor.Fault.CENTER_UNAVAILABLE;
                else if(!bindingMatches(target))fault=HyperNetMonitor.Fault.BINDING_REJECTED;
                else if(!target.status().online())fault=HyperNetMonitor.Fault.CENTER_STOPPED;
                else if(center!=target)fault=HyperNetMonitor.Fault.CONNECTION_PENDING;
                else if(definition.role()==HyperNetDefinition.Role.PROCESSOR&&(hardware==null&&supply==null||sampledTick>=now-1&&sampledSupply<=0))fault=HyperNetMonitor.Fault.NO_SUPPLY;
            }
        }
        return new Diagnostic(fault,detail);
    }
    int schedulingTickets() { requireThread(); return scheduler.size(); }
    public int connectionCount() { requireThread(); if (!working() || budget == null) return 0; prune(); return connections.size(); }
    public boolean isClosed() { return closed; }
    public ServerLevel level() { return world; }
    @Override public void close() {
        requireThread(); if (closed) return;
        disconnectAll(); detach(); if (budget != null) budget.close(); monitorMembers.clear(); demand.clear(); scheduler.clear(); supply = null; databaseState = null; closed = true;
    }
}
