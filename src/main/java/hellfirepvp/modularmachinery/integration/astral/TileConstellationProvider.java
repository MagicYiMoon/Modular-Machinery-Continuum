/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.astral;

import hellfirepvp.astralsorcery.common.base.MoonPhase;
import hellfirepvp.astralsorcery.common.constellation.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class TileConstellationProvider extends TileMachineComponent {
    public TileConstellationProvider(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(() -> type, pos, state); }
    @Override public IOType getIOType() { return IOType.INPUT; }
    public boolean isActive(ResourceLocation id) {
        if (level == null || level.isClientSide() || !level.dimensionType().hasSkyLight()) return false;
        var context = SkyHandler.getContext(level);
        var constellation = ConstellationRegistry.getConstellation(id);
        return context != null && constellation != null && context.getConstellationHandler().isActiveCurrently(constellation, MoonPhase.fromWorld(level));
    }
    public String activeNames() {
        return ConstellationRegistry.getAllConstellations().stream().map(c -> c.getRegistryName()).filter(this::isActive)
                .map(ResourceLocation::toString).collect(java.util.stream.Collectors.joining(", "));
    }
}
