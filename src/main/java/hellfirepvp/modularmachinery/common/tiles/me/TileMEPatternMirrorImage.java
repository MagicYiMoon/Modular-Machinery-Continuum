/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.PatternIsolationProvider;
import hellfirepvp.modularmachinery.common.tiles.MachineInputHandlerProvider;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Source-compatible ME pattern mirror.  The block stores only the bound provider
 * position; all machine-visible storage and isolation handlers remain owned by the
 * real provider.  This is what lets several machines consume from one AE2 provider
 * without copying or desynchronising its cache.
 */
public class TileMEPatternMirrorImage extends TileMachineComponent
        implements PatternIsolationProvider, MachineInputHandlerProvider {

    @Nullable
    private BlockPos providerPos;

    public TileMEPatternMirrorImage(java.util.function.Supplier<? extends BlockEntityType<?>> type,
                                    BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos getProviderPos() {
        return providerPos;
    }

    public void setProviderPos(@Nullable BlockPos providerPos) {
        if (java.util.Objects.equals(this.providerPos, providerPos)) return;
        this.providerPos = providerPos;
        markForUpdate();
    }

    @Nullable
    private TileMEPatternProvider provider() {
        if (isRemoved() || providerPos == null || level == null || level.isClientSide()
                || !level.isLoaded(providerPos)) return null;
        BlockEntity be = level.getBlockEntity(providerPos);
        return be instanceof TileMEPatternProvider provider && !provider.isRemoved() ? provider : null;
    }

    @Override
    public IOType getIOType() {
        return IOType.INPUT;
    }

    @Override
    public List<? extends IItemHandler> getIsolatedItemHandlers() {
        TileMEPatternProvider provider = provider();
        return provider == null ? List.of() : provider.getIsolatedItemHandlers();
    }

    @Override
    public List<? extends IFluidHandler> getIsolatedFluidHandlers() {
        TileMEPatternProvider provider = provider();
        return provider == null ? List.of() : provider.getIsolatedFluidHandlers();
    }

    @Override
    public BlockPos getIsolationGroupPosition() {
        return providerPos;
    }

    @Override
    public IItemHandler machineItemHandler() {
        TileMEPatternProvider provider = provider();
        return provider == null ? null : provider.machineItemHandler();
    }

    @Override
    public IFluidHandler machineFluidHandler() {
        TileMEPatternProvider provider = provider();
        return provider == null ? null : provider.machineFluidHandler();
    }

    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        // The source mirror has no public item/fluid capability.  Its only
        // storage path is the machine component forwarded through the bridge
        // above; exposing it publicly makes adjacent automation bypass the
        // provider's bound-machine semantics.
        return LazyOptional.empty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (providerPos != null) tag.putLong("providerPos", providerPos.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        providerPos = tag.contains("providerPos") ? BlockPos.of(tag.getLong("providerPos")) : null;
    }
}
