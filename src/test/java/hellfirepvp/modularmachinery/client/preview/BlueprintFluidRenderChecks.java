/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import hellfirepvp.modularmachinery.common.machine.BlueprintMaterialState;
import hellfirepvp.modularmachinery.common.machine.BlueprintLayerView;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3f;
import java.nio.ByteOrder;
import java.util.*;

/** Writes the real GUI entity vertex format through the same adapter as the liquid renderer. */
final class BlueprintFluidRenderChecks {
    private static int assertions;

    static void run() {
        for (var fluid : List.of(Fluids.LAVA.defaultFluidState(), Fluids.WATER.defaultFluidState())) {
            var renderType = PreviewFluidVertexConsumer.renderType(fluid);
            require(renderType != ItemBlockRenderTypes.getRenderLayer(fluid)
                            && renderType.format() == DefaultVertexFormat.NEW_ENTITY,
                    "GUI fluid still uses the terrain shader/vertex format");
            for (int yaw : new int[]{0, 45, 90, 135, 270}) for (int pitch : new int[]{-70, 0, 25, 80}) {
                PoseStack pose = new PoseStack();
                pose.translate(90, 52, 100);
                pose.scale(8, -8, -8);
                pose.mulPose(Axis.XP.rotationDegrees(pitch));
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.translate(-.5, -.5, -1.5);
                var expectedMatrix = new org.joml.Matrix4f(pose.last().pose());
                var expectedNormal = new Vector3f(0, 1, 0).mul(pose.last().normal()).normalize();
                var builder = new BufferBuilder(1024);
                builder.begin(VertexFormat.Mode.QUADS, renderType.format());
                var consumer = new PreviewFluidVertexConsumer(builder, pose.last());
                pose.translate(700, 800, 900); // A BER or later cell must not change this camera snapshot.
                float[][] vertices = {{0, .9F, 1}, {0, .9F, 2}, {1, .9F, 2}, {1, .9F, 1}};
                for (float[] v : vertices) {
                    // Same chained calls as LiquidBlockRenderer.vertex, including packed light.
                    consumer.vertex(v[0], v[1], v[2]).color(1F, .4F, .1F, 1F).uv(.25F, .75F)
                            .uv2(0).normal(0, 1, 0).endVertex();
                }
                var rendered = builder.end();
                try {
                    var bytes = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
                    for (int i = 0; i < vertices.length; i++) {
                        int base = i * renderType.format().getVertexSize();
                        var expected = expectedMatrix.transformPosition(new Vector3f(vertices[i]));
                        require(new Vector3f(bytes.getFloat(base), bytes.getFloat(base + 4), bytes.getFloat(base + 8))
                                        .distance(expected) < .0001F,
                                "fluid vertex did not follow the preview camera");
                        require(bytes.getFloat(base + 16) == .25F && bytes.getFloat(base + 20) == .75F
                                        && bytes.getInt(base + 24) == OverlayTexture.NO_OVERLAY
                                        && bytes.getInt(base + 28) == LightTexture.FULL_BRIGHT,
                                "fluid texture/overlay/light fields shifted in the GUI vertex format");
                        require(new Vector3f(bytes.get(base + 32) / 127F, bytes.get(base + 33) / 127F,
                                        bytes.get(base + 34) / 127F).distance(expectedNormal) < .02F,
                                "fluid normal did not rotate with the structure");
                    }
                } finally { rendered.release(); }
            }
        }
        checkLayerCuts();
        checkCompactMaterials();
        System.out.println("[MMC][blueprint-fluid] PASS: " + assertions
                + " assertions; lava/water GUI format, UV/overlay/light, 20 camera orientations, vanilla fluid face occlusion/layer cuts and compact material scrolling");
    }

    private static void checkLayerCuts() {
        // This standalone harness bypasses Forge's registry lifecycle, which initializes state caches.
        for (var block : List.of(Blocks.AIR, Blocks.STONE, Blocks.LAVA, Blocks.WATER)) {
            block.defaultBlockState().initCache();
        }
        require(!Blocks.LAVA.defaultBlockState().getFluidState().isEmpty()
                        && !Blocks.WATER.defaultBlockState().getFluidState().isEmpty(),
                "fluid fixture state caches were not initialized");
        // Use vanilla's actual culling predicate: checking only transformed vertices missed this bug.
        final java.lang.reflect.Method occludes;
        try {
            occludes = LiquidBlockRenderer.class.getDeclaredMethod("isFaceOccludedByNeighbor",
                    BlockGetter.class, BlockPos.class, Direction.class, float.class, BlockState.class);
            occludes.setAccessible(true);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        for (BlockState liquid : List.of(Blocks.LAVA.defaultBlockState(), Blocks.WATER.defaultBlockState())) {
            for (int y : new int[]{-5, 0, 7}) {
                var source = new FluidViewFixture();
                BlockPos center = new BlockPos(-17, y, 33);
                source.states.put(center, liquid);
                for (Direction side : Direction.values()) source.states.put(center.relative(side), Blocks.STONE.defaultBlockState());
                BlockAndTintGetter cut = new BlueprintLayerView(source, y);
                for (Direction side : List.of(Direction.UP, Direction.DOWN)) {
                    BlockPos neighbor = center.relative(side);
                    // A source fluid has a small air gap below a solid ceiling: vanilla keeps that
                    // top face, while the touching bottom face is culled against the lower casing.
                    require(occludes(occludes, source, center, side) == (side == Direction.DOWN),
                            "vanilla ceiling gap or lower-casing occlusion changed");
                    require(!occludes(occludes, cut, center, side), "hidden layer still removes the fluid cut face");
                    require(cut.getBlockState(neighbor).isAir() && cut.getFluidState(neighbor).isEmpty()
                                    && source.getBlockState(neighbor).is(Blocks.STONE),
                            "render cut changed the underlying pattern or retained hidden blocks/fluids");
                    require(LiquidBlockRenderer.shouldRenderFace(cut, center, liquid.getFluidState(), liquid, side,
                                    cut.getFluidState(neighbor)), "exposed fluid cut face was rejected before tessellation");
                }
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    require(occludes(occludes, cut, center, side)
                                    && cut.getBlockState(center.relative(side)).is(Blocks.STONE),
                            "layer cut exposed fluid through same-layer opaque neighbors");
                }
                source.states.put(center.above(), liquid);
                source.states.put(center.below(), liquid);
                require(liquid.getFluidState().getHeight(source, center) == 1
                                && liquid.getFluidState().getHeight(cut, center) < 1,
                        "hidden upper fluid still controls the cut surface height");
                require(!LiquidBlockRenderer.shouldRenderFace(source, center, liquid.getFluidState(), liquid,
                                Direction.UP, source.getFluidState(center.above()))
                                && LiquidBlockRenderer.shouldRenderFace(cut, center, liquid.getFluidState(), liquid,
                                Direction.UP, cut.getFluidState(center.above())),
                        "hidden matching fluid still suppresses the cut surface");
                source.states.put(center.west(), liquid);
                require(!LiquidBlockRenderer.shouldRenderFace(cut, center, liquid.getFluidState(), liquid,
                                Direction.WEST, cut.getFluidState(center.west())),
                        "adjacent same-layer fluid acquired an internal face");
                require(cut.getBlockState(center).equals(liquid) && cut.getFluidState(center).equals(liquid.getFluidState())
                                && cut.getBlockTint(center, (biome, x, z) -> 0) == 0x44BBCC
                                && cut.getRawBrightness(center, 0) == 15 && cut.getBrightness(LightLayer.SKY, center) == 15
                                && cut.getMinBuildHeight() == source.getMinBuildHeight() && cut.getHeight() == source.getHeight(),
                        "render cut lost same-layer state, biome tint, full light or scene limits");
                var otherLayer = new BlueprintLayerView(source, y + 1);
                require(otherLayer.getBlockState(center).isAir() && !otherLayer.getFluidState(center.above()).isEmpty()
                                && !cut.getFluidState(center).isEmpty(), "switching layers shared a mutable render filter");
            }
        }
    }

    private static boolean occludes(java.lang.reflect.Method method, BlockAndTintGetter view, BlockPos center, Direction side) {
        try {
            return (boolean) method.invoke(null, view, center, side, .8888889F, view.getBlockState(center.relative(side)));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class FluidViewFixture implements BlockAndTintGetter {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 4096; }
        @Override public int getMinBuildHeight() { return -2048; }
        @Override public float getShade(Direction side, boolean shade) { return 1; }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0x44BBCC; }
        @Override public LevelLightEngine getLightEngine() { return null; }
        @Override public int getBrightness(LightLayer light, BlockPos pos) { return 15; }
        @Override public int getRawBrightness(BlockPos pos, int darkness) { return 15; }
    }

    private static void checkCompactMaterials() {
        var materials = new BlueprintMaterialState(1, 2);
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        for (int i = 0; i < 28; i++) cells.put(new BlockPos(i, 0, 0), Blocks.STONE.defaultBlockState());
        materials.updateSummary(cells, (pos, state) -> {
            var item = new ItemStack(Items.STONE);
            item.getOrCreateTag().putInt("variant", pos.getX());
            return item;
        });
        Set<Integer> seen = new HashSet<>();
        for (int row = 0; row < 5; row++) {
            for (int i = 0; i < 9; i++) {
                var item = materials.material(i);
                if (!item.isEmpty()) seen.add(item.getTag().getInt("variant"));
            }
            require(materials.material(9).isEmpty(), "hidden second row still has a hit target");
            materials.scrollMaterials(1);
        }
        require(seen.size() == 28 && materials.maxMaterialScroll() == 3,
                "single-row summary lost the final material row");
        materials.scrollMaterialTo(0);
        require(materials.material(0).getTag().getInt("variant") == 0, "summary cannot return to the beginning");
        materials.select(BlockPos.ZERO, new ItemStack(Items.STONE), List.of(new ItemStack(Items.STONE),
                new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.DIAMOND_BLOCK), new ItemStack(Items.GLASS)));
        require(materials.maxAlternativeScroll() == 1 && materials.alternative(2).isEmpty(),
                "compact alternative viewport has an inaccessible final candidate");
        materials.scrollAlternatives(1);
        require(materials.alternative(1).is(Items.GLASS), "last alternative cannot be reached");
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
