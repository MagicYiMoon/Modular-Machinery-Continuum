/* SPDX-License-Identifier: GPL-3.0-only
 * Opt-in GeckoLib playback regression, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.ControllerModelGetEvent;
import hellfirepvp.modularmachinery.common.geo.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import software.bernie.geckolib.core.animatable.model.*;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.KeyframeStack;
import software.bernie.geckolib.core.keyframe.event.data.*;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.lang.reflect.Field;
import java.util.*;

/** Uses real GL4 queue processing on the server; no renderer, GL context or player world is used. */
public final class GeoAnimationRegressionChecks {
    public static final String TARGET = "mmc_geo_animation_regression";
    public static final String BASE = TARGET + "_base", ALT = TARGET + "_alternate";
    private GeoAnimationRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (BlockPos pos : List.of(origin, origin.east(), origin.below(), origin.east().above()))
            saved.put(pos, level.getBlockState(pos));
        Object remoteBefore = field(GeoMachineModelRegistry.class, "remote").get(null);
        try {
            level.setBlock(origin.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            var base = register(BASE);
            var alternate = register(ALT);
            base.addAnimation("intro", false);
            base.addAnimation("loop", true);
            alternate.addAnimation("intro", false);
            alternate.addAnimation("loop", true);
            GeoMachineModelRegistry.bindMachine(TARGET, BASE);
            checkQueue(base);

            level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState(), Block.UPDATE_ALL);
            TileController tile = (TileController) level.getBlockEntity(origin);
            tile.setMachineName(TARGET);
            tile.setFormed(true);
            tile.beginRecipe("animation_work", 200, 1);
            require(tile.isWorking() && TARGET.equals(tile.getFormedMachineName()), "ordinary working query is disconnected");
            tile.setStatusMessage("regression.blocked");
            require(!tile.isWorking(), "blocked ordinary run claims working");
            tile.setStatusMessage(null);
            level.setBlock(origin.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            require(!tile.isWorking(), "redstone-stopped ordinary run claims working");
            level.setBlock(origin.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            var capture = new ControllerModelAnimationEvent[1];
            boolean[] stop = {false}, empty = {false};
            GeoMachineModelRegistry.addAnimationHandler(TARGET, event -> {
                capture[0] = event;
                if (stop[0]) event.setPlayState(1);
                if (empty[0]) event.setAnimation("");
            });
            var model = new PlaybackModel();
            AnimationController<TileController> controller = tile.getAnimatableInstanceCache()
                    .<TileController>getManagerForId(0).getAnimationControllers().get("mmce_machine");
            require(controller instanceof MachineAnimationController, "registered native controller is not the production predicate");
            var machineController = (MachineAnimationController) controller;
            for (int tick = 0; tick <= 15; tick++) process(model, tile, controller, tick);
            require(name(controller).equals("loop"), "intro did not advance to looping second stage");
            RawAnimation retained = controller.getCurrentRawAnimation();
            int builds = model.builds;
            process(model, tile, controller, 16);
            require(controller.getCurrentRawAnimation() == retained && model.builds == builds,
                    "equal sequence restarted or rebuilt on the next frame");
            var event = capture[0];
            require(event.getController() == tile && event.getIMachineController() == tile
                    && event.getCurrentModelName().equals(BASE) && event.getCurrentAnimationName().equals("loop")
                    && event.getAnimationTick() == 16 && event.getPartialTick() == 0.25F
                    && event.getLimbSwing() == 1.5F && event.getLimbSwingAmount() == 0.75F && event.isMoving()
                    && event.getAnimationState() == 0 && event.getAnimationSpeed() == 1,
                    "animation context did not read the actual state");
            event.setTransitionLengthTicks(2.5);
            require(event.getTransitionLengthTicks() == 2.5 && machineController.getTransitionLengthTicks() == 2.5,
                    "fractional transition was truncated or not applied");
            boolean rejected = false;
            try { event.setTransitionLengthTicks(Double.NaN); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "nonfinite transition accepted");
            machineController.setTransitionLengthTicks(0);

            stop[0] = true;
            process(model, tile, controller, 17);
            require(controller.getAnimationState() == AnimationController.State.STOPPED
                    && controller.getCurrentRawAnimation() == null, "STOP retained queued playback");
            stop[0] = false;
            process(model, tile, controller, 18);
            require(name(controller).equals("intro"), "STOP to CONTINUE did not restart the introduction");
            empty[0] = true;
            process(model, tile, controller, 19);
            require(controller.getAnimationState() == AnimationController.State.STOPPED, "empty queue fell back to default");
            empty[0] = false;
            tile.setFormed(false);
            process(model, tile, controller, 20);
            require(controller.getCurrentRawAnimation() == null && tile.getFormedMachineName() == null,
                    "unformed controller retained playback or a formed name");
            tile.setFormed(true);
            process(model, tile, controller, 21);
            require(name(controller).equals("intro"), "reformation failed to restart playback");

            // Natural completion must stay stopped until an explicit stop/resume or sequence change.
            base.clearAnimations(); base.addAnimation("intro", false);
            for (int tick = 22; tick <= 30; tick++) process(model, tile, controller, tick);
            require(controller.hasAnimationFinished(), "one-shot sequence did not finish");
            retained = controller.getCurrentRawAnimation(); builds = model.builds;
            process(model, tile, controller, 31);
            require(controller.hasAnimationFinished() && retained == controller.getCurrentRawAnimation() && model.builds == builds,
                    "finished one-shot replayed every frame");
            base.addAnimation("loop", true);
            process(model, tile, controller, 32);

            ScriptMachineEventRegistry.register(TARGET, ControllerModelGetEvent.class, selection -> selection.setModelName(ALT));
            builds = model.builds;
            process(model, tile, controller, 33);
            require(capture[0].getCurrentModelName().equals(ALT) && model.builds > builds
                    && controller.getCurrentAnimation().animation().length() == 7,
                    "same-named animations retained the old model's baked queue");
            builds = model.builds;
            process(model, tile, controller, 34);
            require(model.builds == builds, "unchanged selected model reset every frame");
            model.reloaded = true;
            GeoMachineModelRegistry.markClientResourcesReloaded();
            process(model, tile, controller, 35);
            require(model.builds > builds && controller.getCurrentAnimation().animation().length() == 8,
                    "resource reload retained the previous baked animation");
            builds = model.builds;
            process(model, tile, controller, 36);
            require(model.builds == builds, "resource revision reset more than once");

            checkFactory(level, origin.east());
            if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                clearHandlers();
                hellfirepvp.modularmachinery.integration.crafttweaker.GeoAnimationScriptRegressionChecks.run(tile, model);
            }
            clearHandlers();
            GeoMachineModelRegistry.installSnapshot(GeoMachineModelRegistry.snapshot());
            TileController preview = new TileController(origin, RegistryBlocks.blockController.get().defaultBlockState()) {
                @Override public boolean isBlueprintPreview() { return true; }
            };
            preview.setMachineName(TARGET); preview.setFormed(true);
            var previewController = preview.getAnimatableInstanceCache().<TileController>getManagerForId(0)
                    .getAnimationControllers().get("mmce_machine");
            process(model, preview, previewController, 0);
            require(previewController.getCurrentRawAnimation().getStageCount() == 2
                    && GeoMachineModelRegistry.getForController(preview) != base,
                    "client snapshot preview lost full sequence or used server definitions");

            int[] failures = {0};
            GeoMachineModelRegistry.addAnimationHandler(TARGET, broken -> {
                failures[0]++;
                throw new IllegalArgumentException("intentional animation regression failure");
            });
            var failed = new ControllerModelAnimationEvent(base);
            GeoMachineModelRegistry.dispatchAnimation(TARGET, failed);
            var disabled = new ControllerModelAnimationEvent(base);
            GeoMachineModelRegistry.dispatchAnimation(TARGET, disabled);
            require(failures[0] == 1 && failed.getPlayState() == 1 && disabled.getPlayState() == 1,
                    "broken handler flooded retries or silently resumed default playback");
            Modularmachinery.LOG.info("[MMC][geo-animation] PASS: full GL4 queue, repeated stages, live context/fractional transition, "
                    + "stable frames, stop/resume, one-shot finish, reformation, selected model/reload, client snapshot, "
                    + "ordinary/factory queries and failed-handler isolation");
        } finally {
            clearHandlers();
            Object script = field(GeoMachineModelRegistry.class, "SCRIPT").get(null);
            ((Map<?, ?>) field(script.getClass(), "models").get(script)).keySet().removeAll(List.of(BASE, ALT));
            ((Map<?, ?>) field(script.getClass(), "bindings").get(script)).remove(TARGET);
            field(GeoMachineModelRegistry.class, "remote").set(null, remoteBefore);
            for (var entry : saved.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
        }
    }

    private static void checkQueue(MachineGeoModel model) {
        var event = new ControllerModelAnimationEvent(model);
        event.setAnimation("pulse", false);
        event.addAnimation("pulse", false);
        event.addAnimation("tail", true);
        var stages = event.buildAnimation().getAnimationStages();
        require(stages.size() == 3 && stages.get(0).animationName().equals("pulse")
                && stages.get(1).animationName().equals("pulse") && stages.get(2).loopType() == Animation.LoopType.LOOP
                && stages.get(0).loopType() == Animation.LoopType.PLAY_ONCE, "queue lost order, repeated name or loop type");
        require(model.getAnimations().size() == 2, "per-predicate edits mutated model defaults");
    }

    private static void checkFactory(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, RegistryBlocks.blockFactoryController.get().defaultBlockState(), Block.UPDATE_ALL);
        var factory = (TileFactoryController) level.getBlockEntity(pos);
        factory.setMachineName(TARGET); factory.setFormed(true); factory.setFactoryCeiling(2, 1);
        var blocked = factory.claimThread(); blocked.beginRecipe("blocked", 20, 1); blocked.setBlockedReason("test.blocked");
        var working = factory.claimThread(); working.beginRecipe("working", 20, 1);
        require(factory.isWorking(), "factory queried only its first blocked thread");
        working.setBlockedReason("test.blocked");
        require(!factory.isWorking(), "fully blocked factory claims working");
        working.clearBlockedReason();
        level.setBlock(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        try { require(!factory.isWorking(), "redstone-stopped factory claims working"); }
        finally { level.removeBlock(pos.above(), false); }
    }

    private static MachineGeoModel register(String name) {
        return GeoMachineModelRegistry.registerModel(name, "mmc_test:geo/" + name + ".geo.json",
                "mmc_test:textures/" + name + ".png", "mmc_test:animations/" + name + ".animation.json");
    }

    public static void process(PlaybackModel model, TileController tile, AnimationController<TileController> controller, double tick) {
        var state = new AnimationState<>(tile, 1.5F, 0.75F, 0.25F, true).withController(controller);
        state.animationTick = tick;
        controller.process(model, state, model.bones, model.snapshots, tick, false);
    }

    private static String name(AnimationController<?> controller) {
        return controller.getCurrentAnimation() == null ? "" : controller.getCurrentAnimation().animation().name();
    }

    private static void clearHandlers() throws Exception {
        ((Map<?, ?>) field(GeoMachineModelRegistry.class, "HANDLERS").get(null)).remove(TARGET);
        ((Set<?>) field(GeoMachineModelRegistry.class, "FAILED_HANDLERS").get(null)).remove(TARGET);
        ((Map<?, ?>) field(ScriptMachineEventRegistry.class, "HANDLERS").get(null)).remove(TARGET);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void require(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }

    /** In-memory baked clips, processed by GL4 itself rather than a simulated animation scheduler. */
    public static final class PlaybackModel implements CoreGeoModel<TileController> {
        private final AnimationProcessor<TileController> processor = new AnimationProcessor<>(this);
        private final Map<String, CoreGeoBone> bones;
        private final Map<String, BoneSnapshot> snapshots;
        public int builds;
        public boolean reloaded;
        public PlaybackModel() {
            var root = new software.bernie.geckolib.cache.object.GeoBone(null, "root", false, 0D, false, false);
            root.saveInitialSnapshot();
            bones = Map.of("root", root);
            snapshots = Map.of("root", new BoneSnapshot(root));
            processor.registerGeoBone(root);
        }
        @Override public CoreBakedGeoModel getBakedGeoModel(String resource) { return null; }
        @Override public AnimationProcessor<TileController> getAnimationProcessor() { return processor; }
        // mclib is GeckoLib's nested runtime JAR, not a direct compiler dependency. Empty keyframe
        // stacks need no mclib values; keep this fixture erased instead of adding a new dependency.
        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override public Animation getAnimation(TileController tile, String name) {
            builds++;
            boolean alternate = GeoMachineModelRegistry.selectedForController(tile).getMachineName().equals(ALT);
            // Deliberately loop in the file: non-looping script stages must override this to PLAY_ONCE.
            return new Animation(name, alternate ? (reloaded ? 8 : 7) : 2, Animation.LoopType.LOOP,
                    new BoneAnimation[] {new BoneAnimation("root", new KeyframeStack(), new KeyframeStack(), new KeyframeStack())},
                    new Animation.Keyframes(new SoundKeyframeData[0],
                    new ParticleKeyframeData[0], new CustomInstructionKeyframeData[0]));
        }
        @Override public void handleAnimations(TileController tile, long id, AnimationState<TileController> state) { }
    }
}
