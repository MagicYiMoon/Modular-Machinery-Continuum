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
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The machine controller block entity.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream extended {@code TileEntityBase} (itself a {@code TileEntity})
 * and persisted through {@code readFromNBT}/{@code writeToNBT}. The 1.20.1 hooks are
 * {@code load(CompoundTag)} and {@code saveAdditional(CompoundTag)} — and note that
 * {@code saveAdditional} <b>returns void and mutates the tag it is given</b>, rather than returning a
 * new tag as {@code writeToNBT} did.
 *
 * <p>Common controller state, recipe progress, component bindings and GeckoLib animation registration.
 * Recipe execution and structure matching run through the server tickers on the main thread.
 */
public class TileController extends TileEntityBase implements GeoBlockEntity,
        hellfirepvp.modularmachinery.common.helper.IMachineController {
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    /** The complete declared sequence is evaluated against the selected model on each predicate. */
    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new hellfirepvp.modularmachinery.common.geo.MachineAnimationController(this));
    }

    @Override
    public boolean isWorking() {
        return isFormed() && !isRedstoneStopped() && isCrafting();
    }

    @Override
    public String getFormedMachineName() {
        return isFormed() ? getMachineName() : null;
    }

    @Override
    public hellfirepvp.modularmachinery.common.machine.RecipeThread[] getRecipeThreadList() {
        return new hellfirepvp.modularmachinery.common.machine.RecipeThread[] {
                new hellfirepvp.modularmachinery.common.machine.RecipeThread(this, null)};
    }

    @Override
    public hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe[] getActiveRecipeList() {
        return java.util.Arrays.stream(getRecipeThreadList()).map(thread -> thread.getActiveRecipe())
                .filter(java.util.Objects::nonNull)
                .toArray(hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe[]::new);
    }

    @Override
    public hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe getActiveRecipe() {
        var recipes = getActiveRecipeList();
        return recipes.length == 0 ? null : recipes[0];
    }

    /** Latest actually evaluated run; never serialize capability/context references into a save or packet. */
    private hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext runtimeRecipeContext;
    private long runtimeRecipeContextRevision;

    public void observeRuntimeRecipeContext(hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext run) {
        runtimeRecipeContext = run;
        if (run != null) {
            run.bindRuntimeThread(new hellfirepvp.modularmachinery.common.machine.RecipeThread(this, null));
            if (java.util.Objects.equals(getRecipeName(), run.getRecipeName())) updateRecipeDuration(run.effectiveDuration());
        }
        runtimeRecipeContextRevision = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getServerDefinitionRevision();
    }

    public hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext getRuntimeRecipeContext() {
        return hasActiveRecipe() && runtimeRecipeContext != null
                && java.util.Objects.equals(getRecipeName(), runtimeRecipeContext.getRecipe().registryName())
                && runtimeRecipeContextRevision == hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getServerDefinitionRevision()
                ? runtimeRecipeContext : null;
    }

    @Override
    public double getTick(Object object) {
        return getLevel() == null ? 0 : getLevel().getGameTime();
    }
    /**
     * The blueprint slot's backing inventory: exactly one stack, accepted only from ItemBlueprint.
     *
     * <p>The restriction lives on the handler's isItemValid rather than in a bespoke Slot subclass.
     * Upstream used an inner ContainerController$SlotBlueprint for this; the effect is the same, and it has
     * to hold in BOTH places a stack can arrive -- a player click and a hopper -- which is why it belongs on
     * the inventory rather than on the slot widget. Slot#mayPlace alone would leave automation open.
     */
    private final net.minecraftforge.items.ItemStackHandler blueprintInventory =
            new net.minecraftforge.items.ItemStackHandler(1) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                     
                    //
                    // `setMachineName` had exactly two callers: the /modularmachinery bind command and the
                    // self-test. The slot accepted blueprints and nothing ever read one, so the ONLY way to
                    // bind a controller was to type a command -- which is not the flow the mod describes.
                    //
                    // A blueprint carries one machine key; dropping it in is how a controller learns which
                    // machine it is. This is that link, and it was the last missing piece of the binding
                    // path: the slot, the item, the preview screen and the formation code all existed, and
                    // nothing joined them.
                    if (!loadingBlueprint) syncMachineFromBlueprint();
                }

                @Override
                public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                    if (!(stack.getItem() instanceof hellfirepvp.modularmachinery.common.item.ItemBlueprint)) return false;
                    String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
                    var associated = hellfirepvp.modularmachinery.common.item.ItemBlueprint.getAssociatedMachine(stack);
                    return fixed == null || associated != null && fixed.equals(associated.getRegistryName());
                }
            };

    /** @return the single-slot blueprint inventory, for the controller menu and automation */
    public net.minecraftforge.items.IItemHandler getBlueprintInventory() {
        return blueprintInventory;
    }

    /** True while the controller believes its multiblock is assembled and running. */

    /**
     * Set when a saved controller comes back FORMED, so the first tick can re-bind its components.
     *
     * <p>A saved binding list is only a cleanup record, so loaded formed machines still rebind against
     * the world once. Unloaded structure chunks postpone consumption of this one-shot request.</p>
     */
    private boolean pendingRebind = false;
    private boolean loadingBlueprint;
    private boolean formed = false;
    private long nextAutomaticStructureCheck = Long.MIN_VALUE;
    private int structureSearchRetries;

    /** First tick, then upstream's unformed retry delay: 30 + retries * 5, capped at 100. */
    boolean automaticStructureCheckDue(long tick) {
        if (tick < nextAutomaticStructureCheck) return false;
        nextAutomaticStructureCheck = tick + Math.min(30 + structureSearchRetries * 5, 100);
        structureSearchRetries = Math.min(structureSearchRetries + 1, 14);
        return true;
    }

    /** Identifier of the machine definition currently bound to this controller, or {@code null}. */
    private String machineName = null;

    /** Player that placed this controller; used by the optional controller security system. */
    private UUID owner;

    /** Controller-scoped data exposed to integrations and persisted with the block entity. */
    private CompoundTag customData = new CompoundTag();
    /** Permanent controller modifiers, keyed like upstream's customModifier map. */
    private final Map<String, RecipeModifier> customModifiers = new LinkedHashMap<>();
    private final hellfirepvp.modularmachinery.common.modifier.RuntimeModifierState recipeThreadModifiers =
            new hellfirepvp.modularmachinery.common.modifier.RuntimeModifierState();

    @Override
    public BlockPos getIPos() { return getBlockPos().immutable(); }

    @Override
    public net.minecraft.world.level.block.state.BlockState getIBlockState() { return getBlockState(); }

    @Override
    public net.minecraft.core.Direction getFacing() {
        var state = getIBlockState();
        return state.hasProperty(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                ? state.getValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                : net.minecraft.core.Direction.NORTH;
    }

    @Override
    public BlockPos rotateWithControllerFacing(BlockPos offset) {
        java.util.Objects.requireNonNull(offset, "relative position");
        // Same NORTH -> WEST -> SOUTH -> EAST transform as upstream, formation and projection.
        return hellfirepvp.modularmachinery.common.machine.StructureMatcher.toWorldOffset(
                BlockPos.ZERO, getFacing(), offset.getX(), offset.getY(), offset.getZ());
    }

    @Override
    public hellfirepvp.modularmachinery.common.util.SmartInterfaceData getSmartInterfaceData(String type) {
        if (type == null) return null;
        for (var data : getSmartInterfaceDataList()) if (type.equals(data.getType())) return data;
        return null;
    }

    @Override
    public hellfirepvp.modularmachinery.common.util.SmartInterfaceData[] getSmartInterfaceDataList() {
        var result = new java.util.ArrayList<hellfirepvp.modularmachinery.common.util.SmartInterfaceData>();
        // Resolve only recorded, loaded components; async server queries must not traverse world tiles.
        if (level == null || !formed || isRemoved() || machineName == null
                || level instanceof net.minecraft.server.level.ServerLevel server && !server.getServer().isSameThread()
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this)
            return result.toArray(hellfirepvp.modularmachinery.common.util.SmartInterfaceData[]::new);
        for (var pos : new java.util.LinkedHashSet<>(boundPositions)) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof TileSmartInterface iface)
                    || iface.isRemoved() || !worldPosition.equals(iface.getControllerPos())) continue;
            var data = iface.provideComponent().getMachineData(0);
            if (data != null && (data.getMachineName() == null || machineName.equals(data.getMachineName())))
                result.add(hellfirepvp.modularmachinery.common.util.SmartInterfaceData.view(this, iface, data));
        }
        return result.toArray(hellfirepvp.modularmachinery.common.util.SmartInterfaceData[]::new);
    }

    @Override public String[] getFoundModifierReplacements() {
        return hellfirepvp.modularmachinery.common.upgrade.ControllerUpgradeQueries.foundModifiers(this);
    }
    @Override public boolean hasModifierReplacement(String modifierName) {
        if (modifierName == null) return false;
        for (String name : getFoundModifierReplacements()) if (modifierName.equals(name)) return true;
        return false;
    }
    @Override public boolean hasMachineUpgrade(String upgradeName) { return getMachineUpgrade(upgradeName).length != 0; }
    @Override public hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade[] getMachineUpgrade(String upgradeName) {
        if (upgradeName == null) return new hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade[0];
        return hellfirepvp.modularmachinery.common.upgrade.ControllerUpgradeQueries.installed(this, true).stream()
                .filter(upgrade -> upgradeName.equals(upgrade.getType().getName()))
                .toArray(hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade[]::new);
    }

    public CompoundTag getCustomDataTag() {
        return customData;
    }

    public void setCustomDataTag(CompoundTag data) {
        if (!canChangeRuntimeModifiers()) return;
        CompoundTag next = data == null ? new CompoundTag() : data.copy();
        // Replacing even an equal value detaches previously returned mutable script views.
        boolean changed = !customData.equals(next);
        this.customData = next;
        if (changed) markNoUpdateSync();
    }

    @Override
    public com.blamejared.crafttweaker.api.data.IData getCustomData() {
        return hellfirepvp.modularmachinery.integration.crafttweaker.ControllerCustomDataView.create(this);
    }

    @Override
    public void setCustomData(com.blamejared.crafttweaker.api.data.IData data) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel) || !canChangeRuntimeModifiers()) return;
        if (data == null || !(data.getInternal() instanceof CompoundTag tag))
            throw new IllegalArgumentException("controller customData must be a compound IData value");
        setCustomDataTag(tag);
    }

    public Map<String, RecipeModifier> getCustomModifiers() {
        return java.util.Collections.unmodifiableMap(customModifiers);
    }

    public boolean hasModifier(String key) {
        return key != null && customModifiers.containsKey(key);
    }

    public void addPermanentModifier(String key, RecipeModifier modifier) {
        if (!canChangeRuntimeModifiers() || key == null || key.isBlank() || modifier == null
                || hellfirepvp.modularmachinery.common.modifier.RuntimeModifierState.same(customModifiers.get(key), modifier)) return;
        customModifiers.put(key, modifier);
        refreshRuntimeModifierContexts();
        markForUpdate();
    }

    public void removePermanentModifier(String key) {
        if (canChangeRuntimeModifiers() && key != null && customModifiers.remove(key) != null) {
            refreshRuntimeModifierContexts(); markForUpdate();
        }
    }

    /** Detached Java fixtures are supported; attached worlds only permit their owning server thread. */
    public boolean canChangeRuntimeModifiers() {
        return !isRemoved() && (level == null || level instanceof net.minecraft.server.level.ServerLevel server
                && server.getServer().isSameThread() && level.isLoaded(worldPosition)
                && level.getBlockEntity(worldPosition) == this);
    }
    public void addModifier(String key, RecipeModifier modifier) {
        if (modifier != null) changeRecipeThreadModifier(false, key, modifier);
    }
    public void removeModifier(String key) { changeRecipeThreadModifier(false, key, null); }
    public Map<String, RecipeModifier> getRecipeThreadModifiers(boolean permanent) {
        return permanent ? recipeThreadModifiers.permanent() : recipeThreadModifiers.semi();
    }
    public java.util.List<RecipeModifier> activeRecipeThreadModifiers() { return recipeThreadModifiers.active(); }
    public void changeRecipeThreadModifier(boolean permanent, String key, RecipeModifier modifier) {
        if (!canChangeRuntimeModifiers()) return;
        boolean changed = modifier == null ? recipeThreadModifiers.remove(permanent, key)
                : recipeThreadModifiers.put(permanent, key, modifier);
        if (changed) { refreshRuntimeModifierContexts(); resetNormalRecipeSearch(); markForUpdate(); }
    }
    protected void refreshRuntimeModifierContexts() {
        var run = getRuntimeRecipeContext();
        if (run != null) observeRuntimeRecipeContext(MachineTicker.refreshRuntimeModifiers(this, null, run));
        resetNormalRecipeSearch();
    }
    public void updateRecipeDuration(int value) {
        if (!hasActiveRecipe()) return;
        int next = Math.max(1, value);
        if (recipeDuration != next) { recipeDuration = next; recipeTicks = Math.min(recipeTicks, next); markForUpdate(); }
    }

    public void clearCustomRuntimeState() {
        customData = new CompoundTag();
        customModifiers.clear();
        refreshRuntimeModifierContexts();
        markForUpdate();
    }

    public UUID getOwner() {
        return owner;
    }

    // Source TileEntityRestrictedTick increments after each controller tick, including paused/unformed ticks.
    // Deliberately neither saved nor synchronized: each newly loaded server/client instance starts at zero.
    private int controllerTicksExisted;

    @Override public int getTicksExisted() { return controllerTicksExisted; }
    public void countControllerTick() { controllerTicksExisted++; }

    @Override public int getExtraThreadCount() { return 0; }
    @Override public void setExtraThreadCount(int extraThreadCount) { } // Ordinary controllers have no dynamic pool.

    @Override
    public net.minecraft.world.level.Level getIWorld() {
        if (level == null || isRemoved()) return null;
        // Do not expose a stale tile's world, load a chunk to validate it, or touch the server off-thread.
        if (level instanceof net.minecraft.server.level.ServerLevel server && !server.getServer().isSameThread()) return null;
        return level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this ? level : null;
    }

    @Override
    public String getOwnerUUIDString() { return owner == null ? null : owner.toString(); }

    @Override
    public String getOwnerName() {
        if (owner == null || !(getIWorld() instanceof net.minecraft.server.level.ServerLevel server)) return null;
        var cache = server.getServer().getProfileCache();
        // UUID lookup only consults the local cache. A name lookup could trigger remote authentication.
        return cache == null ? null : cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }

    @Override
    public net.minecraft.world.entity.player.Player getOwnerIPlayer() {
        if (owner == null || !(getIWorld() instanceof net.minecraft.server.level.ServerLevel server)) return null;
        return server.getServer().getPlayerList().getPlayer(owner);
    }

    public void setOwner(UUID owner) {
        if (!java.util.Objects.equals(this.owner, owner)) {
            this.owner = owner;
            markForUpdate();
        }
    }

    // --- Active recipe state (R4: written only on the server thread) ---

    /** Name of the recipe currently running, or {@code null} when idle. */
    private String recipeName;
    private final java.util.Set<Integer> activeCatalystRequirements = new java.util.LinkedHashSet<>();
    /** Ticks elapsed on the current recipe. */
    private int recipeTicks;
     
    // IT IS PERSISTED BECAUSE IT IS PART OF THE RECIPE STATE, NOT OF THE MACHINE CONFIGURATION: a craft that is
    // half done when the world saves must remember how parallel it was, or it would resume producing a different
    // amount than it started with. UPSTREAM STORES BOTH THE VALUE AND ITS CEILING, and this port does too, because
    // the ceiling is what a reloaded craft is allowed to be reduced to.
    private int parallelism = 1;
    private int maxParallelism = 1;
    /** Duration of the current recipe in ticks. */
    private int recipeDuration;
    /** Indices of delayed requirements already consumed during this recipe run. */
    private final java.util.Set<Integer> triggeredRequirements = new java.util.HashSet<>();
    /** Source-style combination-component group used by the active recipe, or null for normal machines. */
    private String isolationGroupKey;

    // Source TimeRecorder keeps the last 100 controller ticks and 20 actual recipe searches.
    private final hellfirepvp.modularmachinery.common.util.RollingTiming performanceTiming =
            new hellfirepvp.modularmachinery.common.util.RollingTiming(100);
    private final hellfirepvp.modularmachinery.common.util.RollingTiming recipeSearchTiming =
            new hellfirepvp.modularmachinery.common.util.RollingTiming(20);
    private int normalSearchRetries;
    private long normalNextSearchTick = Long.MIN_VALUE;
    private long normalDefinitionRevision = -1;

    public void recordPerformanceNanos(long nanos) { performanceTiming.add(nanos); }
    public void recordRecipeSearchNanos(long nanos) { recipeSearchTiming.add(nanos); }
    public long getAveragePerformanceMicros() { return performanceTiming.averageMicros(); }
    public long getAverageRecipeSearchMicros() { return recipeSearchTiming.averageMicros(); }
    public long getPerformanceSamples() { return performanceTiming.size(); }
    public int getRecipeSearchSamples() { return recipeSearchTiming.size(); }

    public boolean normalRecipeSearchDue(long tick, long definitionRevision) {
        if (normalDefinitionRevision != definitionRevision) {
            normalDefinitionRevision = definitionRevision;
            resetNormalRecipeSearch();
        }
        return tick >= normalNextSearchTick;
    }
    public void recordNormalRecipeSearchResult(long tick, boolean started) {
        if (started) resetNormalRecipeSearch();
        else {
            normalSearchRetries = Math.min(16, normalSearchRetries + 1);
            normalNextSearchTick = tick + Math.min(20 + normalSearchRetries * 5, 100);
        }
    }
    public void requestNormalRecipeSearch() { normalNextSearchTick = Long.MIN_VALUE; }
    private void resetNormalRecipeSearch() {
        normalSearchRetries = 0;
        requestNormalRecipeSearch();
    }

    public String getIsolationGroupKey() {
        return isolationGroupKey;
    }

    public void setIsolationGroupKey(String key) {
        if (java.util.Objects.equals(isolationGroupKey, key)) return;
        isolationGroupKey = key;
        markForUpdate();
    }

    public boolean hasTriggeredRequirement(int index) {
        return triggeredRequirements.contains(index);
    }

    public void markTriggeredRequirement(int index) {
        triggeredRequirements.add(index);
    }

    public void clearTriggeredRequirements() {
        triggeredRequirements.clear();
    }

    /**
     * How many components the structure matcher bound to this controller at the last formation.
     *
     * <p>Recorded because it is the difference between "the machine is assembled" and "the machine is
     * assembled <i>and</i> its busses and hatches are actually wired up". A structure can match while
     * binding nothing — for instance when every declared position holds a decorative casing — and such a
     * machine forms, looks correct, and can never run a recipe. The GUI surfaces the number so that case
     * is visible to a player instead of being a silent dead end.
     *
     * <p>Transient: it is re-derived on every formation, so persisting it would only create a way for the
     * saved value to disagree with the world.
     */
    private int boundComponents = 0;

    /**
     * The positions of the components bound at the last successful formation.
     *
     * <p>Retained so the chunk-unloaded state can cover the whole structure rather than only this controller's
     * own chunk, which is what upstream means (§136.3). `MachineFormation.bindComponents` already computed
     * this list for its own unbinding pass, so keeping it costs no extra walking.
     *
     * <p>Persisted as cleanup candidates across reloads. A successful rebind replaces these with the
     * components actually present; the saved list never substitutes for structure matching.
     */
    private java.util.List<BlockPos> boundPositions = java.util.List.of();
    private final java.util.Set<BlockPos> pendingUnbindPositions = new java.util.LinkedHashSet<>();
    private boolean structureCheckRequested;
    /** World positions covered by the formed pattern, synchronized for the client model hider. */
    private java.util.List<BlockPos> hiddenPositions = java.util.List.of();

    /** @param positions the components bound at the last formation; see {@link #boundPositions} */
    public void setBoundPositions(java.util.List<BlockPos> positions) {
        this.boundPositions = java.util.List.copyOf(positions);
    }

    /** Reconcile recorded components in O(old + new), never by searching surrounding world volume. */
    public void reconcileComponentBindings(java.util.List<BlockPos> positions) {
        var next = new java.util.LinkedHashSet<>(positions);
        var stale = new java.util.LinkedHashSet<>(boundPositions);
        stale.removeAll(next);
        boolean changed = pendingUnbindPositions.addAll(stale);
        changed |= pendingUnbindPositions.removeAll(next);
        boolean bindingsChanged = !boundPositions.equals(positions);
        setBoundPositions(java.util.List.copyOf(next));
        if (changed || bindingsChanged) setChanged();
        flushPendingComponentUnbinds();
    }

    /** Retain unloaded stale positions for a later tick; never load a chunk merely to clear its binding. */
    public void flushPendingComponentUnbinds() {
        if (level == null || level.isClientSide() || pendingUnbindPositions.isEmpty()) return;
        var iterator = pendingUnbindPositions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile component
                    && worldPosition.equals(component.getControllerPos())) component.setControllerPos(null);
            iterator.remove();
            setChanged();
        }
    }

    public void requestStructureCheck() { structureCheckRequested = true; }
    public boolean isStructureCheckRequested() { return structureCheckRequested; }
    public void acknowledgeStructureCheck() { structureCheckRequested = false; }

    @Override public void setRemoved() {
        hellfirepvp.modularmachinery.common.machine.MachineStructureChanges.unwatch(this);
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        hellfirepvp.modularmachinery.common.machine.MachineStructureChanges.unwatch(this);
        super.onChunkUnloaded();
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && formed) {
            pendingRebind = true;
            requestStructureCheck();
            if (formedPattern != null)
                hellfirepvp.modularmachinery.common.machine.MachineStructureChanges.watch(this, formedPattern);
        }
    }

    /**
     * @return how many bound component POSITIONS the controller retained at the last formation.
     *
     * <p>Exists so a check can prove the list is populated rather than empty (§139.2). Without it,
     * {@link #isStructureChunkLoaded} iterates nothing and returns {@code true} — and a check asserting
     * "chunk-loaded agrees with the level" cannot tell that apart from a structure whose every block is
     * genuinely loaded. **A loop over an empty collection is not a check.**
     *
     * <p>Callers should compare this against the count the formation itself reported, which is an
     * independent number.
     */
    






 
    public java.util.List<BlockPos> getBoundPositions() {
        return boundPositions;
    }

    public java.util.List<BlockPos> getHiddenPositions() { return hiddenPositions; }

    public void setHiddenPositions(java.util.List<BlockPos> positions) {
        this.hiddenPositions = positions == null ? java.util.List.of() : java.util.List.copyOf(positions);
    }

    













 
    private hellfirepvp.modularmachinery.common.machine.MachinePattern formedPattern;
    private final hellfirepvp.modularmachinery.common.machine.ControllerStructureQueries structureQueries =
            new hellfirepvp.modularmachinery.common.machine.ControllerStructureQueries(this);

    public void recordFormedStructure(hellfirepvp.modularmachinery.common.machine.DynamicMachine machine,
            hellfirepvp.modularmachinery.common.machine.MachinePattern pattern,
            java.util.Map<String, Integer> sizes, net.minecraft.core.Direction facing) {
        structureQueries.record(machine, pattern, sizes, facing);
        setFormedPattern(pattern);
    }

    @Override public hellfirepvp.modularmachinery.common.helper.IDynamicPatternInfo getDynamicPattern(String name) {
        return structureQueries.dynamic(name);
    }
    @Override public BlockPos[] getBlockPosInPattern(String name) { return structureQueries.positions(name); }
    @Override public BlockPos[] getBlockPosInPattern(BlockState state) { return structureQueries.positions(state); }
    @Override public BlockPos[] getBlockPosInPattern(com.blamejared.crafttweaker.api.item.IItemStack stack) {
        return structureQueries.positions(stack == null ? null : stack.getInternal());
    }
    @Override public BlockPos[] getBlockPosInPattern(hellfirepvp.modularmachinery.common.helper.IBlockStatePredicate predicate) {
        return structureQueries.positions(predicate);
    }
    @Override public int getBlocksInPattern(String name) { return getBlockPosInPattern(name).length; }
    @Override public int getBlocksInPattern(BlockState state) { return getBlockPosInPattern(state).length; }
    @Override public int getBlocksInPattern(com.blamejared.crafttweaker.api.item.IItemStack stack) {
        return getBlockPosInPattern(stack).length;
    }
    @Override public int getBlocksInPattern(hellfirepvp.modularmachinery.common.helper.IBlockStatePredicate predicate) {
        return getBlockPosInPattern(predicate).length;
    }

    /** @param pattern the pattern the formation walk bound components from; see {@link #formedPattern} */
    public void setFormedPattern(hellfirepvp.modularmachinery.common.machine.MachinePattern pattern) {
        this.formedPattern = pattern;
        if (pattern != null) {
            hellfirepvp.modularmachinery.common.machine.MachineStructureChanges.watch(this, pattern);
            if (formed && refreshFormedPositions()) markForUpdate();
        }
    }

    /** Dynamic growth/shrink updates both the chunk guard and the client's hidden structure cells. */
    private boolean refreshFormedPositions() {
        if (formedPattern == null) return false;
        var facing = getBlockState().hasProperty(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                ? getBlockState().getValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING)
                : net.minecraft.core.Direction.NORTH;
        var positions = new java.util.ArrayList<BlockPos>();
        for (var part : formedPattern.getParts())
            positions.add(hellfirepvp.modularmachinery.common.machine.StructureMatcher.toWorldOffset(
                    worldPosition, facing, part.x(), part.y(), part.z()));
        if (hiddenPositions.equals(positions)) return false;
        setHiddenPositions(positions);
        return true;
    }

    /**
     * @return the formed pattern, falling back to the definition's own when nothing has been recorded.
     *
     * <p>The fallback matters for the one-shot re-bind after a world load, which runs before any formation in
     * that session: asking for the formed pattern there must not answer {@code null} and take every component
     * lookup down with it. The definition's pattern is the right answer for the 4 of 5 shipped machines that
     * declare no dynamic sections at all.</p>
     */
    public hellfirepvp.modularmachinery.common.machine.MachinePattern getFormedPattern(
            hellfirepvp.modularmachinery.common.machine.DynamicMachine fallback) {
        return formedPattern != null ? formedPattern : fallback.getPattern();
    }

    public int getBoundPositionCount() {
        return boundPositions.size();
    }

    /** @param count the number of components bound by the last formation */
    public void setBoundComponentCount(int count) {
        this.boundComponents = count;
    }

    /** @return the number of components bound at the last formation */
    public int getBoundComponentCount() {
        return boundComponents;
    }

    public boolean hasActiveRecipe() {        return recipeName != null;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public java.util.Set<Integer> getActiveCatalystRequirements() {
        return java.util.Set.copyOf(activeCatalystRequirements);
    }

    public void setActiveCatalystRequirements(java.util.Set<Integer> values) {
        activeCatalystRequirements.clear();
        if (values != null) activeCatalystRequirements.addAll(values);
    }

    public int getRecipeTicks() {
        return recipeTicks;
    }

    public int getRecipeDuration() {
        return recipeDuration;
    }

    /**
     * @return how many times this craft's inputs and outputs are multiplied, as decided when it started
     *
     * <p>IT IS PART OF THE RECIPE STATE RATHER THAN A QUERY AGAINST THE MACHINE (upstream ActiveMachineRecipe U43):
     * a craft in progress keeps the value it began with, so removing a parallel controller mid-craft does not
     * silently change what the craft is producing. THE CEILING IS STORED ALONGSIDE IT because that is what a
     * reloaded or restarted craft is allowed to be reduced to.</p>
     */
    public int getParallelism() {
        return parallelism;
    }

    /** @return the ceiling this craft started under, used when a restart has to reduce its parallelism */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    // ------------------------------------------------------------------
    // The parallelism READOUT (upstream GuiMachineController U145-U154 / GuiFactoryController U277-U311)
    // ------------------------------------------------------------------

    


















































 
    public java.util.List<net.minecraft.network.chat.Component> parallelismReadoutLines() {
        if (getParallelism() <= 1) {
            return java.util.List.of();
        }
        return java.util.List.of(
                net.minecraft.network.chat.Component.translatable(PARALLELISM_KEY, getParallelism()),
                net.minecraft.network.chat.Component.translatable(MAX_PARALLELISM_KEY, getMaxParallelism()));
    }

     
    public static final String PARALLELISM_KEY = "gui.controller.parallelism";
     
    public static final String MAX_PARALLELISM_KEY = "gui.controller.max_parallelism";

    /**
     * How far apart the readout's lines are drawn, and how far the block is offset from the line above it --
     * ONE number for both screens, reusing the line height this port's status text already uses. Upstream
     * advances 10 pixels inside a 0.72 pose (i.e. 7.2 panel pixels); the port's screens draw unscaled 1.20.1
     * text, whose own comfortable line height is this value -- {@code ScreenMachineController} already lays its
     * status, progress and components lines out on it before this readout was added.
     */
    public static final int READOUT_LINE_STRIDE = 18;

    /** Starts a recipe. Inputs must already have been consumed by the caller. */
    public void beginRecipe(String recipeName, int duration, int parallelism) {
        runtimeRecipeContext = null;
        resetNormalRecipeSearch();
        this.recipeName = recipeName;
        this.recipeDuration = Math.max(1, duration);
        this.recipeTicks = 0;
        this.parallelism = Math.max(1, parallelism);
        this.maxParallelism = Math.max(1, parallelism);
        this.triggeredRequirements.clear();
        this.isolationGroupKey = null;
         
        // then started a different craft would keep displaying the previous craft's reason.
        setStatusMessage(null);
        markForUpdate();
    }

    











 
    public void advanceRecipe() {
        if (this.recipeTicks < this.recipeDuration) {
            this.recipeTicks++;
        }
        // Syncing every tick would flood the client with packets for no visual benefit; progress is
        // synced on the transitions and on completion instead.
    }
         
        //
        // Upstream RecipeTickEvent.preventProgressing rewrites the recipe own tick counter: if it is above zero it
        // subtracts one, so the craft does not advance on that tick. THE PORT ADVANCES UNCONDITIONALLY ONE LINE
        // ABOVE, so subtracting one here has EXACTLY the same net effect -- one tick of no progress -- and the
        // floor-at-zero guard is upstream own, carried over rather than invented.
        //
        // WHY THIS IS NOT A SETTER: nothing in this port needs to place the counter at an arbitrary value, and a
        // setter would let a caller leave it above the duration or below zero. A one-step rewind is the whole
        // contract upstream gives its handlers.
        public void rewindRecipeTick() {
            if (this.recipeTicks > 0) {
                this.recipeTicks--;
            }
        }


    /** Completes the current recipe and returns to idle. */
    public void finishRecipe() {
        runtimeRecipeContext = null;
        recipeThreadModifiers.clearSemi();
        resetNormalRecipeSearch();
        this.recipeName = null;
        this.recipeTicks = 0;
        this.recipeDuration = 0;
        this.triggeredRequirements.clear();
        this.activeCatalystRequirements.clear();
        this.isolationGroupKey = null;
        setStatusMessage(null);
        markForUpdate();
    }

    




















 
    private String statusMessage = null;

    /** @param key the unlocalized reason the machine is holding, or {@code null} to clear it */
    public void setStatusMessage(String key) {
        if (java.util.Objects.equals(this.statusMessage, key)) {
            return;     // unchanged: no packet, no chunk-dirty flag
        }
        this.statusMessage = key;
        markForUpdate();
    }

    /** @return the unlocalized reason the machine is holding, or {@code null}; see {@link #statusMessage} */
    public String getStatusMessage() {
        return statusMessage;
    }

    





















 
    public boolean isCrafting() {
        return hasActiveRecipe() && statusMessage == null;
    }

    /**
     * Drops the current recipe without emitting.
     *
     * <p>Used when the machine comes apart mid-craft or its definition disappears on reload. The inputs
     * are already consumed, so this loses them — the alternative is a phantom recipe that can never
     * complete, which is worse because it occupies the machine forever.
     */
    public void abandonRecipe() {
        if (recipeName != null) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.info(
                    "[MMCE] controller at {} abandoned recipe '{}' at {}/{} ticks",
                    worldPosition.toShortString(), recipeName, recipeTicks, recipeDuration);
        }
        finishRecipe();
    }

    /**
     * @param type the block entity type; passed as a supplier so subclasses (e.g. the factory
     *             controller) can bind their own registry entry without a static-initialisation cycle
     */
    protected TileController(java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<?>> type,
                             BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.machineName = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(state.getBlock());
    }

    public TileController(BlockPos pos, BlockState state) {
        this(hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities.CONTROLLER, pos, state);
    }

    /**
     * Takes the re-bind request if one is pending, and clears it.
     *
     * <p>CONSUMED ONCE RATHER THAN TESTED, so this cannot become a per-tick structure walk. The caller is
     * MachineTicker, which has the facing the walk needs; TileController does not carry it.</p>
     */
    public boolean consumeRebindRequest() {
        if (pendingRebind && !isStructureChunkLoaded()) return false;
        boolean requested = this.pendingRebind;
        this.pendingRebind = false;
        return requested;
    }

    public boolean isFormed() {
        return formed;
    }

    /**
     * True when a redstone signal is arriving at this controller, so the machine is held stopped.
     *
     * <p>Added for the controller's status model (§135): upstream's display set has SIX states and this port
     * derived only four of them, because nothing tracked the other two. `gui.controller.status.redstone_stopped`
     * — "Machine stopped by incoming redstone signal" — has been sitting in both language files, unwired,
     * since the asset conversion (§107.1).
     *
     * <p>A stopped machine reported as {@code idle} is the failure this prevents: "not running" and "cannot
     * run until you cut the signal" are the same pixels and different problems.
     *
     * <p>Read straight from the world rather than tracked in a field. A cached flag would need invalidating on
     * every neighbour change, and the one place that consumes it — a status line — is already reading the
     * controller every frame.
     */
    public boolean isRedstoneStopped() {
        net.minecraft.world.level.Level level = getLevel();
        return level != null && level.hasNeighborSignal(getBlockPos());
    }

    /**
     * True when the controller, recorded components and all recorded formed structure cells are loaded.
     * This guard performs no chunk loads and covers decorative cells in dynamic sections as well as hatches.
     */
    public boolean isStructureChunkLoaded() {
        net.minecraft.world.level.Level level = getLevel();
        if (level == null || !level.isLoaded(getBlockPos())) {
            return false;
        }
        // Every bound component, which is what upstream's `chunk_unloaded` means: a block in the multiblock
        // structure going away. The list is what formation bound, so it matches the structure actually
        // standing rather than the definition's declared extent.
        for (BlockPos componentPos : boundPositions) {
            if (!level.isLoaded(componentPos)) {
                return false;
            }
        }
        for (BlockPos structurePos : hiddenPositions) {
            if (!level.isLoaded(structurePos)) return false;
        }
        return true;
    }

    public void setFormed(boolean formed) {
        if (!formed) structureQueries.clear();
        if (!formed) runtimeRecipeContext = null;
        if (this.formed != formed) {
            resetNormalRecipeSearch();
            nextAutomaticStructureCheck = Long.MIN_VALUE;
            structureSearchRetries = 0;
        }
         
        //
        //   upstream fires MachineStructureFormedEvent at TileMultiblockMachineController U609 and
        //   MachineStructureUpdateEvent at U610, right after it -- both inside the CONTROLLER OWN TICK.
        //   THIS PORT DOES NOT FORM IN THE TICK LOOP. Formation is driven by explicit actions, and
        //   setFormed has FIVE callers: CommandModularMachinery U249, ControllerInteraction U148,
        //   ItemConstructTool U119, and MachineSelfTest twice.
        //
        //   SO COPYING UPSTREAM POSITION WOULD MAKE FOUR OF THOSE FIVE FORM SILENTLY, with nothing fired.
        //   Putting the dispatch inside setFormed keeps upstream ORDER AND SEMANTICS and reaches every caller.
        if (this.formed != formed) {
            this.formed = formed;
            if (!formed) {
                acknowledgeStructureCheck();
                hellfirepvp.modularmachinery.common.machine.MachineStructureChanges.unwatch(this);
            } else if (formedPattern != null) {
                hellfirepvp.modularmachinery.common.machine.MachineStructureChanges.watch(this, formedPattern);
            }
        if (formed && formedPattern != null) {
            refreshFormedPositions();
        } else if (!formed) {
            setHiddenPositions(java.util.List.of());
        }
        if (formed) {
            // Upstream U609 then U610: formed FIRST, then update, and the order is upstream own (497.1).
            new hellfirepvp.modularmachinery.common.event.machine.MachineStructureFormedEvent(this)
                    .postEvent();
            new hellfirepvp.modularmachinery.common.event.machine.MachineStructureUpdateEvent(this)
                    .postEvent();
        } else {
            // Upstream U557: the structure changed. Same event, the unformed direction.
            new hellfirepvp.modularmachinery.common.event.machine.MachineStructureUpdateEvent(this)
                    .postEvent();
        }
            syncFormedState();
            markForUpdate();
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this));
        }
    }

    /** Upstream failure-action RESET: keep the active recipe and restart its progress at tick zero. */
    public void resetRecipeProgress() {
        this.recipeTicks = 0;
        markForUpdate();
    }

    public String getMachineName() {
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        return fixed == null ? machineName : fixed;
    }

    /**
     * Resolves the current upgrade set for upgrade-scoped event callbacks.
     *
     * <p>This intentionally mirrors {@code MachineContext.installedUpgrades()} without constructing a synthetic
     * server context. Recorded loaded buses provide capped static copies and per-slot dynamic views;
     * guarded data writes still reach the original bus/item, without merging inventory-owned counts.</p>
     */
    public java.util.List<hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade>
    getInstalledUpgradesForEvents() {
        return hellfirepvp.modularmachinery.common.upgrade.ControllerUpgradeQueries.installed(this, false);
    }

    /** Detached blueprint controllers use the client model snapshot without attaching to a real world. */
    public boolean isBlueprintPreview() {
        return false;
    }

    public void setMachineName(String machineName) {
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        String nextMachine = fixed == null ? machineName : fixed;
        if (!java.util.Objects.equals(this.machineName, nextMachine)) {
            structureQueries.retainForMachine(nextMachine);
            resetNormalRecipeSearch();
            runtimeRecipeContext = null;
        }
        this.machineName = nextMachine;
        markForUpdate();
    }

    /** Blueprint changes request a fresh search; optional blueprints do not unset a formed machine. */
    public void syncMachineFromBlueprint() {
        var blueprint = hellfirepvp.modularmachinery.common.item.ItemBlueprint
                .getAssociatedMachine(blueprintInventory.getStackInSlot(0));
        var found = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                .findByName(machineName);
        // An optional blueprint may be removed without changing the recognised machine. A required
        // blueprint must remain present, as in source checkStructure().
        if (formed && found != null && (!found.isRequiresBlueprint() || found == blueprint)) {
            markForUpdate();
            return;
        }
        if (formed) {
            setFormed(false);
            if (this instanceof TileFactoryController factory) factory.abandonAllRecipes();
            else abandonRecipe();
        }
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        this.machineName = fixed != null ? fixed : blueprint == null ? null : blueprint.getRegistryName();
        nextAutomaticStructureCheck = Long.MIN_VALUE;
        structureSearchRetries = 0;
        markForUpdate();
    }

    /**
     * Mirrors {@link #formed} onto the blockstate so the shipped
     * {@code facing=…,formed=…} model variants select the correct texture.
     */
    private void syncFormedState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(hellfirepvp.modularmachinery.common.block.BlockController.FORMED)
                && state.getValue(hellfirepvp.modularmachinery.common.block.BlockController.FORMED) != formed) {
            level.setBlock(worldPosition,
                    state.setValue(hellfirepvp.modularmachinery.common.block.BlockController.FORMED, formed),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        runtimeRecipeContext = null;
        super.load(tag);
        formedPattern = null;
        structureQueries.load(tag);
        resetNormalRecipeSearch();
        normalDefinitionRevision = -1;
        if (tag.contains("blueprint")) {
            loadingBlueprint = true;
            try { blueprintInventory.deserializeNBT(tag.getCompound("blueprint")); }
            finally { loadingBlueprint = false; }
        }
        this.formed = tag.getBoolean("formed");
         
        this.pendingRebind = this.formed;
        this.hiddenPositions = readHiddenPositions(tag);
        // Legacy saves already carry the formed cells as hiddenPositions; use them as cleanup candidates
        // until the first rebind replaces them with the actual component positions.
        this.boundPositions = tag.contains("boundPositions", net.minecraft.nbt.Tag.TAG_LONG_ARRAY)
                ? readPositions(tag, "boundPositions") : hiddenPositions;
        this.pendingUnbindPositions.clear();
        this.pendingUnbindPositions.addAll(readPositions(tag, "pendingUnbindPositions"));
        this.structureCheckRequested = this.formed;
        String fixed = hellfirepvp.modularmachinery.common.registry.DedicatedControllers.binding(getBlockState().getBlock());
        this.machineName = fixed != null ? fixed : tag.contains("machineName") ? tag.getString("machineName") : null;
        if (tag.hasUUID("owner")) {
            this.owner = tag.getUUID("owner");
        } else {
            this.owner = null;
        }
        this.recipeName = tag.contains("recipeName") ? tag.getString("recipeName") : null;
        this.activeCatalystRequirements.clear();
        if (tag.contains("activeCatalysts", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            for (int index : tag.getIntArray("activeCatalysts")) this.activeCatalystRequirements.add(index);
        }
        this.recipeTicks = tag.getInt("recipeTicks");
        this.recipeDuration = tag.getInt("recipeDuration");
        this.isolationGroupKey = tag.contains("isolationGroupKey") ? tag.getString("isolationGroupKey") : null;
        this.parallelism = tag.contains("parallelism") ? Math.max(1, tag.getInt("parallelism")) : 1;
        this.maxParallelism = tag.contains("maxParallelism") ? Math.max(1, tag.getInt("maxParallelism")) : this.parallelism;
        this.triggeredRequirements.clear();
        if (tag.contains("triggeredRequirements", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            for (int index : tag.getIntArray("triggeredRequirements")) this.triggeredRequirements.add(index);
        }
        // ASSIGNED UNCONDITIONALLY RATHER THAN ONLY WHEN PRESENT, and the difference is the CLIENT: this same
        // method is what onDataPacket runs, so an absent key has to CLEAR the message. Reading it only when
        // present would leave a stale "output full" line on a machine that has since started producing.
        this.statusMessage = tag.contains("statusMessage") ? tag.getString("statusMessage") : null;
        this.customData = tag.contains("customData") ? tag.getCompound("customData").copy() : new CompoundTag();
        recipeThreadModifiers.read(tag.getCompound("recipeThreadModifiers"));
        this.customModifiers.clear();
        if (tag.contains("customModifier", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag list = tag.getList("customModifier", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                RecipeModifier modifier = RecipeModifier.deserialize(entry.getCompound("modifier"));
                if (modifier != null && entry.contains("key")) {
                    this.customModifiers.put(entry.getString("key"), modifier);
                }
            }
        }
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structureQueries.save(tag);
        tag.putLongArray("boundPositions", boundPositions.stream().mapToLong(BlockPos::asLong).toArray());
        if (!pendingUnbindPositions.isEmpty())
            tag.putLongArray("pendingUnbindPositions", pendingUnbindPositions.stream().mapToLong(BlockPos::asLong).toArray());
        tag.put("blueprint", blueprintInventory.serializeNBT());
        tag.putBoolean("formed", formed);
        if (!hiddenPositions.isEmpty()) {
            long[] positions = new long[hiddenPositions.size()];
            for (int i = 0; i < positions.length; i++) positions[i] = hiddenPositions.get(i).asLong();
            tag.putLongArray("hiddenPositions", positions);
        }
        if (machineName != null) {
            tag.putString("machineName", machineName);
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        // Progress is persisted so a recipe survives a chunk unload / server restart rather than
        // silently restarting and consuming a second set of inputs.
        if (recipeName != null) {
            tag.putString("recipeName", recipeName);
            tag.putInt("recipeTicks", recipeTicks);
            tag.putInt("recipeDuration", recipeDuration);
        tag.putInt("parallelism", parallelism);
        tag.putInt("maxParallelism", maxParallelism);
            if (isolationGroupKey != null) tag.putString("isolationGroupKey", isolationGroupKey);
            if (!triggeredRequirements.isEmpty()) {
                tag.putIntArray("triggeredRequirements", triggeredRequirements.stream().mapToInt(Integer::intValue).toArray());
            }
            if (!activeCatalystRequirements.isEmpty()) {
                tag.putIntArray("activeCatalysts", activeCatalystRequirements.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        // THE HOLD REASON RIDES THE SAME TWO HOOKS AS EVERYTHING ELSE: getUpdateTag delegates to
        // saveAdditional, so this one line is the whole client sync (see the field's javadoc). Written only
        // when set, so the tag carries nothing on the ordinary path.
        if (statusMessage != null) {
            tag.putString("statusMessage", statusMessage);
        }
        if (!customData.isEmpty()) {
            tag.put("customData", customData.copy());
        }
        var threadModifierTag = new CompoundTag(); recipeThreadModifiers.write(threadModifierTag);
        tag.put("recipeThreadModifiers", threadModifierTag);
        if (!customModifiers.isEmpty()) {
            ListTag list = new ListTag();
            customModifiers.forEach((key, modifier) -> {
                if (key != null && modifier != null) {
                    CompoundTag entry = new CompoundTag();
                    entry.putString("key", key);
                    entry.put("modifier", modifier.serialize());
                    list.add(entry);
                }
            });
            tag.put("customModifier", list);
        }
    }

    private static java.util.List<BlockPos> readPositions(CompoundTag tag, String key) {
        if (!tag.contains(key, net.minecraft.nbt.Tag.TAG_LONG_ARRAY)) return java.util.List.of();
        return java.util.Arrays.stream(tag.getLongArray(key)).mapToObj(BlockPos::of).distinct().toList();
    }

    private static java.util.List<BlockPos> readHiddenPositions(CompoundTag tag) {
        if (!tag.contains("hiddenPositions", net.minecraft.nbt.Tag.TAG_LONG_ARRAY)) return java.util.List.of();
        long[] positions = tag.getLongArray("hiddenPositions");
        java.util.ArrayList<BlockPos> result = new java.util.ArrayList<>(positions.length);
        for (long value : positions) result.add(BlockPos.of(value));
        return java.util.List.copyOf(result);
    }
}
