/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Read-only render cut: hidden layers cannot occlude exposed block/fluid faces or affect fluid height. */
public final class BlueprintLayerView implements BlockAndTintGetter {
    private final BlockAndTintGetter source;
    private final int layer;

    public BlueprintLayerView(BlockAndTintGetter source, int layer) {
        this.source = source;
        this.layer = layer;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        return pos.getY() == layer ? source.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return pos.getY() == layer ? source.getFluidState(pos) : Blocks.AIR.defaultBlockState().getFluidState();
    }

    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return pos.getY() == layer ? source.getBlockEntity(pos) : null;
    }

    @Override public int getHeight() { return source.getHeight(); }
    @Override public int getMinBuildHeight() { return source.getMinBuildHeight(); }
    @Override public float getShade(Direction direction, boolean shade) { return source.getShade(direction, shade); }
    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return source.getBlockTint(pos, resolver); }
    @Override public LevelLightEngine getLightEngine() { return source.getLightEngine(); }
    @Override public int getBrightness(LightLayer light, BlockPos pos) { return source.getBrightness(light, pos); }
    @Override public int getRawBrightness(BlockPos pos, int darkness) { return source.getRawBrightness(pos, darkness); }
}
