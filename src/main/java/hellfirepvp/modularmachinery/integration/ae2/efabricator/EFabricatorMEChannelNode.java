/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EFabricatorMEChannel/PatternBus. */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import appeng.api.crafting.*;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.*;
import appeng.api.util.AECableType;
import appeng.crafting.pattern.AECraftingPattern;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** A real crafting provider. Queue admission is atomic; work execution is a separate EF stage. */
public final class EFabricatorMEChannelNode extends TileEFabricatorMEChannel implements IInWorldGridNodeHost, IActionHost, ICraftingProvider {
    private IManagedGridNode node;
    private Integer placingPlayer;
    private boolean dirty = true, wasActive, disconnecting;
    private long revision = -1, definitionRevision = -1;
    private Object recipes;
    private Map<AEItemKey, AECraftingPattern> patterns = Map.of();
    private List<IPatternDetails> available = List.of();
    private int validPatternCount;
    private double configuredDrain = -1;
    private final IGridNodeListener<EFabricatorMEChannelNode> listener = new IGridNodeListener<>() {
        @Override public void onSaveChanges(EFabricatorMEChannelNode host, IGridNode gridNode) { host.setChanged(); }
        @Override public void onStateChanged(EFabricatorMEChannelNode host, IGridNode gridNode, State reason) { host.dirty = true; }
        @Override public void onGridChanged(EFabricatorMEChannelNode host, IGridNode gridNode) { host.dirty = true; }
    };
    public EFabricatorMEChannelNode(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public void setPlacingPlayer(Player player) {
        placingPlayer = appeng.api.features.IPlayerRegistry.getMapping(player.level()).getPlayerId(player.getUUID());
        nodeData.putInt("PlacingPlayer", placingPlayer);
        if (node != null) node.setOwningPlayer(player);
        setChanged();
    }
    @Override public boolean isNetworkActive() { return !disconnecting && isActivePart() && node != null && node.isActive(); }
    @Override public double extractWorkEnergy(double requested) {
        if (!(level instanceof ServerLevel world) || !world.getServer().isSameThread() || !isNetworkActive()
                || !Double.isFinite(requested) || requested <= 0) return 0;
        return node.getGrid().getEnergyService().extractAEPower(requested, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }
    @Override public long insertOutput(net.minecraft.world.item.ItemStack item, long amount) {
        if (!(level instanceof ServerLevel world) || !world.getServer().isSameThread() || !isNetworkActive() || item.isEmpty() || amount <= 0) return 0;
        var grid = node.getGrid();
        return appeng.api.storage.StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                AEItemKey.of(item), amount, new appeng.me.helpers.MachineSource(this));
    }
    private boolean current() { return owner() != null && revision == owner().patternRevision()
            && recipes == level.getRecipeManager() && definitionRevision == MachineReloadListener.getServerDefinitionRevision(); }
    public int validPatternCount() { return current() ? validPatternCount : 0; }
    public double idleDrain() { return 64D + validPatternCount(); }
    @Override public List<IPatternDetails> getAvailablePatterns() { return isNetworkActive() && current() ? available : List.of(); }
    @Override public boolean isBusy() { return !isNetworkActive() || !current() || !owner().hasQueueSpace(); }
    @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] input) {
        if (!(level instanceof ServerLevel world) || !world.getServer().isSameThread() || details == null || isBusy()) return false;
        var canonical = patterns.get(details.getDefinition());
        if (canonical == null || !canonical.equals(details)) return false;
        var host = owner();
        EFabricatorPatterns.Prepared prepared;
        try { prepared = EFabricatorPatterns.prepare(canonical, input, world); }
        catch (RuntimeException failure) { return false; } // Bad/missing recipe data cannot consume the real inputs.
        if (prepared == null || owner() != host || isBusy() || !prepared.unchanged(input)) return false;
        if (!host.offerSingleWork(prepared.work())) return false;
        prepared.consume(input);
        return true;
    }
    private void rebuildPatterns() {
        var next = new LinkedHashMap<AEItemKey, AECraftingPattern>(); int count = 0;
        for (var part : owner().parts()) if (part instanceof TileEFabricatorPatternBus bus && bus.isActivePart()) {
            for (var stack : bus.patternStacks()) try {
                var decoded = PatternDetailsHelper.decodePattern(stack, level, false);
                // Ordinary processing, smithing and stonecutting are not source EF crafting recipes.
                if (decoded instanceof AECraftingPattern crafting) { next.putIfAbsent(crafting.getDefinition(), crafting); count++; }
            } catch (RuntimeException failure) {
                Modularmachinery.LOG.warn("[MMC][EF] Could not decode a stored pattern at {}; keeping its inventory item", bus.getBlockPos());
            }
        }
        patterns = Collections.unmodifiableMap(next); available = List.copyOf(next.values()); validPatternCount = count;
        revision = owner().patternRevision(); recipes = level.getRecipeManager();
        definitionRevision = MachineReloadListener.getServerDefinitionRevision(); dirty = true;
    }
    @Override public void tick() {
        if (!(level instanceof ServerLevel)) return;
        if (owner() != null && owner().isStructureCheckRequested()) owner().refreshStructure();
        if (!isActivePart()) { disconnect(); return; }
        if (!current()) rebuildPatterns();
        if (node == null) {
            node = GridHelper.createManagedNode(this, listener).setTagName("channel")
                    .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                    .setVisualRepresentation(owner().getBlockState().getBlock()).setIdlePowerUsage(idleDrain())
                    .addService(ICraftingProvider.class, this);
            node.loadFromNBT(nodeData);
            if (placingPlayer != null) node.setOwningPlayerId(placingPlayer);
            configuredDrain = idleDrain(); node.create(level, worldPosition); dirty = true;
        }
        double drain = idleDrain();
        if (configuredDrain != drain) { node.setIdlePowerUsage(drain); configuredDrain = drain; }
        boolean active = isNetworkActive();
        if (active != wasActive) { wasActive = active; dirty = true; }
        // Refresh outside storage/crafting callbacks to avoid modifying AE's provider iterator in place.
        if (dirty) { dirty = false; ICraftingProvider.requestUpdate(node); }
    }
    @Override protected void disconnect() {
        if (disconnecting) return;
        disconnecting = true;
        try {
            if (node != null) { node.saveToNBT(nodeData); node.destroy(); node = null; }
            patterns = Map.of(); available = List.of(); validPatternCount = 0;
            revision = -1; definitionRevision = -1; recipes = null; configuredDrain = -1; dirty = true; wasActive = false;
        } finally { disconnecting = false; }
    }
    @Override public IGridNode getGridNode(Direction side) { return node == null ? null : node.getNode(); }
    @Override public IGridNode getActionableNode() { return getGridNode(null); }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.DENSE_SMART; }
    @Override public void load(CompoundTag tag) { super.load(tag); placingPlayer = nodeData.contains("PlacingPlayer") ? nodeData.getInt("PlacingPlayer") : null; }
    @Override protected void saveAdditional(CompoundTag tag) { if (node != null) node.saveToNBT(nodeData); super.saveAdditional(tag); }
}
