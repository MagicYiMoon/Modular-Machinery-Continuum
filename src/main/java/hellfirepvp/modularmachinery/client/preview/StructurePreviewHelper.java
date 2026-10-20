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
package hellfirepvp.modularmachinery.client.preview;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.BlueprintPreviewState;
import hellfirepvp.modularmachinery.common.machine.MachinePattern;
import hellfirepvp.modularmachinery.common.machine.WorldProjectionDefinition;
import hellfirepvp.modularmachinery.common.machine.WorldProjectionScene;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Holds the structure preview the player asked for, for the renderer to draw.
 *
 * <p><b>Client-only, and deliberately so.</b> This mirrors {@code youyihj.mmce.common.preview.
 * StructurePreviewHelper} from the jar, which is a pair of statics -- a {@code BlockPos renderPos} and a
 * {@code renderMachinePreview(DynamicMachine, BlockPos)} entry point. The class is {@link OnlyIn} the client
 * because a dedicated server has no renderer to feed, and this port has already had one crash from a
 * client-only type being touched on the server (the {@code Screen} load in the {@code ClientSetup} split).
 *
 * <p><b>What is here is the data model, not the drawing.</b> §181 split the preview into five units; this is
 * the end of unit 1 -- the item can ask for a preview and the request can be stored. The renderer that consumes
 * it is unit 2, and it is the part this port cannot verify by itself.
 */
@OnlyIn(Dist.CLIENT)
public final class StructurePreviewHelper {

    private static DynamicMachine machine;
    /**
     * The concrete pattern shown by the projector.  This is deliberately kept separate from
     * {@link DynamicMachine#getPattern()}: the source mod expands every dynamic pattern to its
     * minimum size for a preview, while the machine definition itself remains the static template.
     */
    private static MachinePattern pattern;
    private static WorldProjectionDefinition definition;
    private static WorldProjectionScene scene;
    private static BlockPos renderPos;
    private static net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
    /** Null means render the complete preview; a value means render only that controller-relative Y layer. */
    private static Integer renderLayer;
    private static boolean pendingPlacement;
    private static boolean automaticLayers;
    private static BlueprintPreviewState.ProjectionSnapshot snapshot;

    /** Source JEI/blueprint button: follow the crosshair until a world use-click fixes the anchor. */
    public static void startPreview(DynamicMachine previewed) {
        startPreview(previewed, 0);
    }

    /** Starts the world preview with the same dynamic-pattern size selected in the blueprint screen. */
    public static void startPreview(DynamicMachine previewed, int requestedSize) {
        startPreview(previewed, new BlueprintPreviewState.ProjectionSnapshot(requestedSize, currentTick()));
    }

    /** Source setShiftSnap(renderer.getTickSnap()): candidates stay fixed before and after placement. */
    public static void startPreview(DynamicMachine previewed, BlueprintPreviewState.ProjectionSnapshot captured) {
        reset();
        machine = previewed;
        snapshot = captured;
        pattern = buildPreviewPattern(previewed, captured.dynamicSize());
        buildDefinition();
        pendingPlacement = pattern != null;
        automaticLayers = true;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("gui.blueprint.popout.place"), true);
        updatePendingAnchor();
    }

    private static boolean updatePendingAnchor() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        var hit = mc.player.pick(20, 1F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            renderPos = null;
            return false;
        }
        var state = mc.level.getBlockState(blockHit.getBlockPos());
        if (state.getBlock() instanceof hellfirepvp.modularmachinery.common.block.BlockController) {
            renderPos = blockHit.getBlockPos().immutable();
            facing = state.getValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING);
        } else {
            renderPos = blockHit.getBlockPos().relative(blockHit.getDirection());
            facing = net.minecraft.core.Direction.NORTH;
        }
        return true;
    }

    public static boolean isPendingPlacement() { return pendingPlacement; }

    public static void placePreview() {
        if (pendingPlacement && updatePendingAnchor()) {
            pendingPlacement = false;
            tickPreview();
        }
    }

    /** Fixed projections show the lowest incomplete layer, including a lower layer broken later. */
    public static void tickPreview() {
        if (pendingPlacement) { updatePendingAnchor(); return; }
        if (!automaticLayers || !isPreviewing()) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { reset(); return; }
        if (hellfirepvp.modularmachinery.common.machine.PreviewCellDecision.isOutOfRange(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), renderPos)) { reset(); return; }
        renderLayer = hellfirepvp.modularmachinery.common.machine.PreviewLayerSelection.lowestIncomplete(
                mc.level, renderPos, facing, definition);
        if (renderLayer == null) reset();
    }

    private StructurePreviewHelper() {
    }

    /**
     * Starts a preview of {@code machine} anchored at {@code pos}, in the orientation {@code orientation}.
     *
     * <p><b>The facing is the controller's own.</b> §188 recorded a hard-coded {@code NORTH} as a known
     * limitation, because a preview drawn on the wrong axis is a preview that lies -- the machine a player
     * builds is rotated by the controller's {@code FACING} property, and the matcher transforms every part
     * through it. Passing it here means the ghost and the matcher agree by construction rather than by both
     * happening to be unrotated.
     */
    public static void renderMachinePreview(DynamicMachine previewed, BlockPos pos,
                                            net.minecraft.core.Direction orientation) {
        renderMachinePreview(previewed, pos, orientation, null);
    }

    /** Starts a preview, optionally restricted to one controller-relative Y layer. */
    public static void renderMachinePreview(DynamicMachine previewed, BlockPos pos,
                                            net.minecraft.core.Direction orientation, Integer layer) {
        reset();
        machine = previewed;
        // The source projector calls context.snapSamples() when used on the controller.
        snapshot = new BlueprintPreviewState.ProjectionSnapshot(0, currentTick());
        pattern = buildPreviewPattern(previewed);
        buildDefinition();
        renderPos = pos == null ? null : pos.immutable();
        facing = orientation == null ? net.minecraft.core.Direction.NORTH : orientation;
        renderLayer = layer;
        pendingPlacement = false;
        automaticLayers = layer == null;
        if (automaticLayers) tickPreview();
    }

    private static void buildDefinition() {
        var loader = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader();
        definition = pattern == null ? null : new WorldProjectionDefinition(machine, pattern,
                loader.getVariables(), loader.getResolver());
    }

    public static WorldProjectionDefinition getDefinition() { return definition; }

    public static WorldProjectionScene getScene() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (definition == null || renderPos == null || mc.level == null) return null;
        if (scene == null) scene = new WorldProjectionScene(mc.level, renderPos, definition.cells());
        scene.update(renderPos, facing, getSampleTick(), mc.level.getGameTime());
        return scene;
    }

    /** Resource reload preserves placement/candidate choices while recreating detached entities and render caches. */
    public static void clearScene() {
        if (scene != null) scene.close();
        scene = null;
        StructurePreviewRenderer.clearSceneRenderCache();
    }

    private static long currentTick() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    /** The world renderer must never resample from the live world clock. */
    public static long getSampleTick() { return snapshot == null ? 0 : snapshot.sampleTick(); }

    /** Size selected in the blueprint projection, for source-compatible auto-assembly requests. */
    public static int getDynamicPatternSize() { return snapshot == null ? 0 : snapshot.dynamicSize(); }

    /** @return the orientation the preview was requested in, never {@code null} */
    public static net.minecraft.core.Direction getFacing() {
        return facing;
    }

    /** Clears any preview in progress. */
    public static void reset() {
        clearScene();
        machine = null;
        pattern = null;
        definition = null;
        renderPos = null;
        facing = net.minecraft.core.Direction.NORTH;
        renderLayer = null;
        pendingPlacement = false;
        automaticLayers = false;
        snapshot = null;
    }

    /** @return the machine being previewed, or {@code null} */
    public static DynamicMachine getMachine() {
        return machine;
    }

    /** @return the static or minimum-size-expanded pattern currently being previewed */
    public static MachinePattern getPattern() {
        return pattern != null ? pattern : machine == null ? null : machine.getPattern();
    }

    /** @return the anchor of the preview, or {@code null} */
    public static BlockPos getRenderPos() {
        return renderPos;
    }

    /** @return whether a preview is active */
    public static boolean isPreviewing() {
        return machine != null && definition != null && renderPos != null;
    }

    /** @return the selected controller-relative layer, or {@code null} for the complete structure */
    public static Integer getRenderLayer() {
        return renderLayer;
    }

    /**
     * Builds the same minimum-size dynamic preview that upstream's DynamicMachineRenderContext uses.
     * A client cannot know which variable-length structure the player intends to build yet, so the
     * definition's minimum is the stable, useful preview.  Sections replace static cells at the same
     * offset, matching formation's effective-pattern merge semantics.
     */
    /**
     * Returns the pattern the client should show for a blueprint preview.  Dynamic sections are expanded to
     * their minimum size, exactly like the in-world projector.  Keeping this in one client helper prevents the
     * blueprint screen and the world renderer from disagreeing about which Y layers exist.
     */
    public static MachinePattern buildPreviewPattern(DynamicMachine source) {
        return buildPreviewPattern(source, 0);
    }

    /**
     * Builds a preview with the source GUI's requested dynamic-pattern size. A value of zero keeps the minimum
     * section size; each declared pattern clamps the request to its own minimum and maximum independently.
     */
    public static MachinePattern buildPreviewPattern(DynamicMachine source, int requestedSize) {
        return BlueprintPreviewState.buildPattern(source, requestedSize);
    }
}
