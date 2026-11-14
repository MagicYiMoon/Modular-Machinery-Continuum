/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO EPartController/ECalculatorController, KasumiNova and contributors.
 */
package hellfirepvp.modularmachinery.common.ecalculator;

import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Physical EC host; optional AE integration supplies crafting tasks, never ordinary MMC recipes. */
public final class TileECalculatorController extends TileController {
    private final Map<BlockPos, TileECalculatorPart> parts = new LinkedHashMap<>();
    private boolean assembled, redstoneStopped;
    private long nextCheck, definitionRevision = -1, componentRevision;
    private long nextVisualUpdate;
    private final Map<BlockPos, ECalculatorAppearance.Link> driveVisualLinks = new HashMap<>();
    public TileECalculatorController(BlockPos pos, BlockState state) { super(ECalculatorRegistration.CONTROLLER_TILE, pos, state); }
    public int tier() { return ((BlockECalculatorController)getBlockState().getBlock()).tier(); }
    public boolean isAssembled() { return assembled; }
    public boolean owns(TileECalculatorPart part) { return parts.get(part.getBlockPos()) == part; }
    public List<TileECalculatorPart> parts() { return List.copyOf(parts.values()); }
    public long componentRevision() { return componentRevision; }
    public void componentsChanged() { componentRevision++; }
    public long totalBytes() { return isOperational() ? parts.values().stream().filter(p -> p instanceof TileECalculatorDrive)
            .mapToLong(p -> ((TileECalculatorDrive)p).suppliedBytes()).sum() : 0; }
    public long normalSlots() { return isOperational() ? parts.values().stream().mapToLong(p -> p.specification().normalSlots()).sum() : 0; }
    public long hyperSlots() { return isOperational() ? parts.values().stream().mapToLong(p -> p.specification().hyperSlots()).sum() : 0; }
    public long parallelism() { return isOperational() ? parts.values().stream().mapToLong(p -> p.specification().parallelism()).sum() : 0; }
    public boolean isOperational() {
        return assembled && isFormed() && !isRemoved() && !redstoneStopped && !isStructureCheckRequested()
                && isStructureChunkLoaded() && definitionRevision == MachineReloadListener.getServerDefinitionRevision();
    }
    @Override public void tick() {
        if (!(level instanceof ServerLevel)) return;
        // Sample neighbours only from the host's block tick. AE CPU liveness must not refresh chunk tickets.
        redstoneStopped = super.isRedstoneStopped();
        if (assembled && !isStructureChunkLoaded()) setFormed(false);
        if (level.getGameTime() >= nextCheck || isStructureCheckRequested()
                || definitionRevision != MachineReloadListener.getServerDefinitionRevision()) refreshStructure();
        if (level.getGameTime() >= nextVisualUpdate) {
            nextVisualUpdate = level.getGameTime() + 5;
            refreshPartAppearances();
        }
        // Deliberately does not tick TileController's ordinary MMC recipe engine.
    }
    public void refreshStructure() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        redstoneStopped = super.isRedstoneStopped();
        nextCheck = level.getGameTime() + 40;
        definitionRevision = MachineReloadListener.getServerDefinitionRevision();
        acknowledgeStructureCheck(); flushPendingComponentUnbinds();
        var loader = MachineReloadListener.getLoader();
        var machine = loader.findByName(ECalculatorRegistration.machineName(tier()));
        if (machine == null || sharedController()) { setFormed(false); return; }
        Direction facing = getBlockState().getValue(BlockECalculatorController.FACING);
        var attempt = MachineFormation.tryForm(server, worldPosition, facing, machine, loader);
        if (!attempt.formed()) { setFormed(false); return; }
        Map<BlockPos, TileECalculatorPart> found = new LinkedHashMap<>();
        int channels = 0;
        for (var item : getFormedPattern(machine).getParts()) {
            var pos = StructureMatcher.toWorldOffset(worldPosition, facing, item.x(), item.y(), item.z());
            if (!server.hasChunkAt(pos)) { setFormed(false); return; }
            if (server.getBlockEntity(pos) instanceof TileECalculatorPart part) {
                if (!part.canBind(this) || part.specification().kind() == ECalculatorSpecs.Kind.CHANNEL && ++channels > 1) {
                    setFormed(false); return;
                }
                found.put(pos, part);
            }
        }
        if (!parts.equals(found)) {
            for (var entry : parts.entrySet()) if (found.get(entry.getKey()) != entry.getValue()) entry.getValue().unbind(this);
            parts.clear(); parts.putAll(found); componentsChanged();
        }
        for (var part : parts.values()) if (!part.bind(this)) { setFormed(false); return; }
        assembled = true;
        setBoundComponentCount(attempt.boundCount() + parts.size());
        setFormed(true); acknowledgeStructureCheck();
        refreshPartAppearances();
    }
    ECalculatorAppearance.Link driveVisualLink(BlockPos pos) { return driveVisualLinks.getOrDefault(pos, ECalculatorAppearance.Link.NONE); }
    public void refreshPartAppearances() {
        // One linear pass, preserving source last-transmitter-wins order. Drives never scan all parts.
        driveVisualLinks.clear();
        for (var part : parts.values()) if (part.specification().kind() == ECalculatorSpecs.Kind.TRANSMITTER) {
            if (part.visualDriveLink(Direction.UP)) driveVisualLinks.put(part.getBlockPos().above(), ECalculatorAppearance.Link.DOWN);
            if (part.visualDriveLink(Direction.DOWN)) driveVisualLinks.put(part.getBlockPos().below(), ECalculatorAppearance.Link.UP);
        }
        for (var part : parts.values()) part.refreshAppearance();
    }
    /** Source controller replaces 17 housing cells; the measured workers cap replaces five more. */
    public List<BlockPos> modelHiddenPositions() {
        var result = new LinkedHashSet<BlockPos>();
        var facing = getBlockState().getValue(BlockECalculatorController.FACING);
        for (int x = -1; x <= 1; x++) for (int z = 0; z <= 1; z++) for (int y = -1; y <= 1; y++) {
            if (x != 0 || y != 0 || z != 0) result.add(StructureMatcher.toWorldOffset(worldPosition, facing, x, y, z));
        }
        var machine = MachineReloadListener.getLoader().findByName(ECalculatorRegistration.machineName(tier()));
        var workers = getDynamicPattern("workers");
        var pattern = machine == null ? null : machine.getDynamicPatterns().get("workers");
        if (workers != null && pattern != null) {
            var offset = pattern.getStructureSizeOffset().multiply(workers.getSize()).offset(pattern.getStructureSizeOffsetStart());
            for (var pos : List.of(new BlockPos(0,-1,1), new BlockPos(0,-1,0), new BlockPos(0,0,1), new BlockPos(0,1,1), new BlockPos(0,1,0))) {
                pos = pos.offset(offset);
                result.add(StructureMatcher.toWorldOffset(worldPosition, facing, pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return List.copyOf(result);
    }
    private void refreshClientHiding(boolean remove) {
        if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> { if (remove) hellfirepvp.modularmachinery.client.world.BlockModelHider.remove(this);
                    else hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this); });
    }
    private boolean sharedController() {
        // NECO tests the controller CLASS, so unlike a same-block check this includes different tiers.
        for (var pos : List.of(worldPosition.above(2), worldPosition.below(2)))
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof TileECalculatorController) return true;
        return false;
    }
    private void disassemble() {
        assembled = false;
        driveVisualLinks.clear();
        for (var part : parts.values()) part.unbind(this);
        if (!parts.isEmpty()) componentsChanged();
        parts.clear();
    }
    @Override public void setFormed(boolean formed) { if (!formed) disassemble(); super.setFormed(formed); }
    @Override public void load(CompoundTag tag) { disassemble(); super.load(tag); assembled = false; redstoneStopped = false; nextCheck = 0; definitionRevision = -1; }
    @Override public void onLoad() { super.onLoad(); refreshClientHiding(false); }
    @Override public void setRemoved() { disassemble(); super.setRemoved(); refreshClientHiding(true); }
    @Override public void onChunkUnloaded() { disassemble(); super.onChunkUnloaded(); refreshClientHiding(true); }
}
