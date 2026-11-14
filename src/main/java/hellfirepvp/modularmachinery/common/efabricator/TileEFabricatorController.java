/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EPartController/EFabricatorController. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** EF physical host. Never run the ordinary MMC recipe engine on these dedicated controllers. */
public final class TileEFabricatorController extends TileController {
    private final Map<BlockPos, TileEFabricatorPart> parts = new LinkedHashMap<>();
    private boolean assembled, redstoneStopped, overclocked, activeCooling;
    private long nextCheck, revision = -1;
    private long patternRevision;
    private EFabricatorOutputBuffer outputs = new EFabricatorOutputBuffer();
    private EFabricatorCooling cooling = new EFabricatorCooling();
    private boolean speedupApplied;
    private CompoundTag opaqueProduction;
    private long totalCrafted;
    private int untilWork = EFabricatorSpecs.WORK_DELAY;
    private int outputCursor;
    public TileEFabricatorController(BlockPos pos, BlockState state) { super(EFabricatorRegistration.CONTROLLER_TILE, pos, state); }
    public EFabricatorSpecs.Tier tier() { return ((BlockEFabricatorController)getBlockState().getBlock()).tier(); }
    public boolean isAssembled() { return assembled; }
    public boolean owns(TileEFabricatorPart part) { return parts.get(part.getBlockPos()) == part; }
    public List<TileEFabricatorPart> parts() { return List.copyOf(parts.values()); }
    public long patternRevision() { return patternRevision; }
    public void patternsChanged() { patternRevision++; }
    public boolean hasQueueSpace() {
        return isOperational() && productionReadable() && parts.values().stream().anyMatch(p -> p instanceof TileEFabricatorWorker w && w.freeCrafts() > 0);
    }
    /** AE2 15 pushes one craft at a time. No partial acceptance is possible at this boundary. */
    public boolean offerSingleWork(EFabricatorQueue.Work work) {
        if (work.crafts() != 1 || !isOperational() || !productionReadable()) return false;
        for (var part : parts.values()) if (part instanceof TileEFabricatorWorker worker && worker.offerWork(work) == 1) {
            applyCoolingSpeedup(parallelism()); return true;
        }
        return false;
    }
    public boolean isOverclocked() { return overclocked; }
    public boolean isActiveCooling() { return activeCooling; }
    public boolean productionReadable() { return opaqueProduction == null; }
    public long totalCrafted() { return productionReadable() ? totalCrafted : -1; }
    public List<EFabricatorOutputBuffer.Entry> pendingOutputs() { return productionReadable() ? outputs.entries() : List.of(); }
    private TileEFabricatorMEChannel channel() {
        for (var part : parts.values()) if (part instanceof TileEFabricatorMEChannel channel && channel.isActivePart()) return channel;
        return null;
    }
    public boolean canManufacture() {
        var channel = channel();
        return isOperational() && productionReadable() && (!activeCooling || cooling.units() >= EFabricatorSpecs.COOLANT_PER_CRAFT)
                && channel != null && channel.isNetworkActive();
    }
    public int coolingUnits() { return productionReadable() ? cooling.units() : -1; }
    public int cooledCraftLimit() { return activeCooling ? cooling.units() / EFabricatorSpecs.COOLANT_PER_CRAFT : Integer.MAX_VALUE; }
    public int ticksUntilWork() { return untilWork; }
    boolean storeProducts(EFabricatorQueue.Work work) {
        if (!canManufacture() || work.crafts() > cooledCraftLimit() || !outputs.add(work)) return false;
        if (activeCooling) cooling.consume(work.crafts() * EFabricatorSpecs.COOLANT_PER_CRAFT);
        totalCrafted = totalCrafted > Long.MAX_VALUE - work.crafts() ? Long.MAX_VALUE : totalCrafted + work.crafts();
        setChanged(); return true;
    }
    public CompoundTag productionData() {
        if (opaqueProduction != null) return opaqueProduction.copy();
        var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putLong("TotalCrafted", totalCrafted); tag.put("Outputs", outputs.save());
        if (!cooling.isEmpty()) tag.put("Cooling", cooling.save()); return tag;
    }
    public void restoreProductionData(CompoundTag tag) {
        outputs = new EFabricatorOutputBuffer(); cooling = new EFabricatorCooling(); opaqueProduction = null;
        totalCrafted = 0; untilWork = workDelay(); speedupApplied = false;
        if (!tag.isEmpty()) try {
            if (tag.getInt("Version") != 1 || !tag.contains("TotalCrafted", net.minecraft.nbt.Tag.TAG_LONG) || tag.getLong("TotalCrafted") < 0)
                throw new IllegalArgumentException("Unknown EF production data");
            outputs = EFabricatorOutputBuffer.load(tag.getCompound("Outputs")); totalCrafted = tag.getLong("TotalCrafted");
            if (tag.contains("Cooling") && !tag.contains("Cooling", net.minecraft.nbt.Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid EF cooling data");
            cooling = EFabricatorCooling.load(tag.getCompound("Cooling"));
        } catch (RuntimeException failure) { opaqueProduction = tag.copy(); }
        setChanged();
    }
    public void setModes(boolean overclock, boolean cooling) {
        if (level == null || level.isClientSide) return;
        if (overclocked != overclock || activeCooling != cooling) {
            overclocked = overclock; activeCooling = cooling; untilWork = workDelay(); speedupApplied = false; setChanged();
        }
    }
    public int parallelism() { return EFabricatorSpecs.parallelism(parts.values().stream().map(TileEFabricatorPart::specification)
            .filter(s -> s.kind() == EFabricatorSpecs.Kind.PARALLEL).map(EFabricatorSpecs.Part::tier).toList(), overclocked, activeCooling); }
    public int workDelay() { return EFabricatorSpecs.workDelay((int)parts.values().stream().filter(p -> p instanceof TileEFabricatorWorker).count(), activeCooling); }
    public boolean isOperational() {
        return assembled && isFormed() && !isRemoved() && !redstoneStopped && !isStructureCheckRequested()
                && isStructureChunkLoaded() && revision == MachineReloadListener.getServerDefinitionRevision();
    }
    @Override public void tick() {
        if (!(level instanceof ServerLevel)) return;
        redstoneStopped = super.isRedstoneStopped();
        if (assembled && !isStructureChunkLoaded()) setFormed(false);
        if (level.getGameTime() >= nextCheck || isStructureCheckRequested() || revision != MachineReloadListener.getServerDefinitionRevision()) refreshStructure();
        var channel = channel();
        if (!isOperational() || !productionReadable() || channel == null || !channel.isNetworkActive()) return;
        if (--untilWork > 0) return;
        untilWork = workDelay(); speedupApplied = false;
        // Source updates LEDs before consuming a real queue, once per fabrication cycle.
        // Our output backpressure/coolant checks must not freeze a completed worker's RUN lamp.
        for (var part : parts.values()) if (part instanceof TileEFabricatorWorker) part.refreshAppearance();
        // Retry a bounded number of types per cycle, retaining everything ME cannot actually accept.
        var waiting = outputs.entries(); int visited = 0;
        int start = waiting.isEmpty() ? 0 : Math.floorMod(outputCursor, waiting.size());
        while (visited < Math.min(64, waiting.size()) && channel.isNetworkActive()) {
            var entry = waiting.get((start + visited++) % waiting.size());
            long inserted = channel.insertOutput(entry.item(), entry.amount());
            if (inserted > 0) { outputs.remove(entry, inserted); setChanged(); }
        }
        outputCursor = waiting.isEmpty() ? 0 : (start + visited) % waiting.size();
        // Backpressure also prevents unbounded production while storage is full.
        if (!outputs.isEmpty()) return;
        var loader = MachineReloadListener.getLoader(); var machine = loader.findByName(EFabricatorRegistration.machineName(tier()));
        if (machine == null) return;
        var context = new MachineContext((ServerLevel)level, worldPosition, getBlockState().getValue(BlockEFabricatorController.FACING),
                machine, loader.getVariables(), getFormedPattern(machine));
        context.overrideComponentPositions(getBoundPositions());
        // The source precharges this cache in normal mode too. Re-resolve handlers each cycle, never after unload.
        cooling.refill(context.internalFluidInputs(), context.internalFluidOutputs(), this::setChanged);
        if (!canManufacture()) return;
        for (var part : List.copyOf(parts.values())) if (part instanceof TileEFabricatorWorker worker && worker.isActivePart() && worker.readable()) {
            double needed = worker.energyNeeded();
            if (needed > 0) worker.supplyEnergy(channel.extractWorkEnergy(needed));
        }
        int available = parallelism();
        for (var part : List.copyOf(parts.values())) if (part instanceof TileEFabricatorWorker worker) {
            // The source grants a base one-craft operation even when the shared parallel allowance is exhausted.
            int completed = worker.doWork(Math.max(available, 1));
            if (completed > 1) available = Math.max(0, available - completed);
        }
        if (activeCooling && parts.values().stream().anyMatch(p -> p instanceof TileEFabricatorWorker w && w.queuedCrafts() > 0))
            applyCoolingSpeedup(available);
    }
    private void applyCoolingSpeedup(int unused) {
        if (!activeCooling || speedupApplied || !isOperational() || !productionReadable()) return;
        var acceleration = EFabricatorSpecs.coolingAcceleration(parallelism(), unused, workDelay(), cooling.units());
        cooling.consume(acceleration.units()); untilWork = workDelay() - acceleration.ticks(); speedupApplied = true;
        if (acceleration.units() > 0) setChanged();
    }
    public void refreshStructure() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        redstoneStopped = super.isRedstoneStopped(); nextCheck = level.getGameTime() + 40;
        revision = MachineReloadListener.getServerDefinitionRevision(); acknowledgeStructureCheck(); flushPendingComponentUnbinds();
        var loader = MachineReloadListener.getLoader(); var machine = loader.findByName(EFabricatorRegistration.machineName(tier()));
        if (machine == null || sharedController(server)) { setFormed(false); return; }
        var facing = getBlockState().getValue(BlockEFabricatorController.FACING);
        var attempt = MachineFormation.tryForm(server, worldPosition, facing, machine, loader);
        if (!attempt.formed()) { setFormed(false); return; }
        var found = new LinkedHashMap<BlockPos, TileEFabricatorPart>(); int channels = 0;
        for (var item : getFormedPattern(machine).getParts()) {
            var pos = StructureMatcher.toWorldOffset(worldPosition, facing, item.x(), item.y(), item.z());
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) { setFormed(false); return; }
            if (chunk.getBlockEntity(pos) instanceof TileEFabricatorPart part) {
                if (!part.canBind(this) || part.specification().kind() == EFabricatorSpecs.Kind.CHANNEL && ++channels > 1) { setFormed(false); return; }
                found.put(pos, part);
            }
        }
        if (!assembled || !parts.equals(found)) patternsChanged();
        for (var entry : parts.entrySet()) if (found.get(entry.getKey()) != entry.getValue()) entry.getValue().unbind(this);
        parts.clear(); parts.putAll(found);
        for (var part : parts.values()) if (!part.bind(this)) { setFormed(false); return; }
        assembled = true; setBoundComponentCount(attempt.boundCount() + parts.size()); setFormed(true); acknowledgeStructureCheck();
        for (var part : parts.values()) part.refreshAppearance();
    }
    private boolean sharedController(ServerLevel server) {
        for (var pos : List.of(worldPosition.above(2), worldPosition.below(2))) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof TileEFabricatorController) return true;
        }
        return false;
    }
    private void disassemble() { assembled = false; patternsChanged(); for (var part : parts.values()) part.unbind(this); parts.clear(); }
    /** Source HIDE_POS_LIST: the host model occupies these seventeen cells; keep authoritative blocks and inventories intact. */
    public List<BlockPos> modelHiddenPositions() {
        var result = new ArrayList<BlockPos>(17);
        var facing = getBlockState().getValue(BlockEFabricatorController.FACING);
        for (int x = -1; x <= 1; x++) for (int z = 0; z <= 1; z++) for (int y = -1; y <= 1; y++)
            if (x != 0 || y != 0 || z != 0) result.add(StructureMatcher.toWorldOffset(worldPosition, facing, x, y, z));
        return List.copyOf(result);
    }
    private void refreshClientHiding(boolean remove) {
        if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> { if (remove) hellfirepvp.modularmachinery.client.world.BlockModelHider.remove(this);
                    else hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this); });
    }
    @Override public void setFormed(boolean formed) { if (!formed) disassemble(); super.setFormed(formed); }
    @Override public void load(CompoundTag tag) {
        disassemble(); super.load(tag); assembled = false; nextCheck = 0; revision = -1; redstoneStopped = false;
        overclocked = tag.getBoolean("EfOverclock"); activeCooling = tag.getBoolean("EfActiveCooling");
        restoreProductionData(tag.getCompound("EfProduction"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putBoolean("EfOverclock", overclocked); tag.putBoolean("EfActiveCooling", activeCooling);
        tag.put("EfProduction", productionData());
    }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag(); tag.putBoolean("formed", isFormed());
        if (getMachineName() != null) tag.putString("machineName", getMachineName());
        return tag;
    }
    /** Chunk/update packets contain cosmetics only. Never feed them into the durable production-data loader. */
    @Override public void handleUpdateTag(CompoundTag tag) {
        if (level != null && !level.isClientSide) return;
        setFormed(tag.getBoolean("formed")); refreshClientHiding(false);
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    @Override public void onLoad() { super.onLoad(); refreshClientHiding(false); }
    @Override public void setRemoved() { disassemble(); super.setRemoved(); refreshClientHiding(true); }
    @Override public void onChunkUnloaded() { disassemble(); super.onChunkUnloaded(); refreshClientHiding(true); }
}
