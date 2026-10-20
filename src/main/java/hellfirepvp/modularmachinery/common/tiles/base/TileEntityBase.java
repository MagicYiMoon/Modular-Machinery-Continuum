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
package hellfirepvp.modularmachinery.common.tiles.base;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Root block entity for every Modular Machinery component.
 *
 * <p><b>What changed from 1.12.2.</b> The upstream {@code TileEntityBase} was a thin subclass of
 * {@code net.minecraft.tileentity.TileEntity}. The renames and signature changes that matter here:
 *
 * <ul>
 *   <li>{@code TileEntity} → {@link BlockEntity}</li>
 *   <li>{@code readFromNBT(NBTTagCompound)} → {@code load(CompoundTag)}; {@code writeToNBT} →
 *       {@code saveAdditional(CompoundTag)}. <b>The 1.20.1 save hook does not return a tag</b> — it is
 *       handed one to fill in, which is the single most common silent-migration mistake.</li>
 *   <li>{@code NBTTagCompound} → {@link CompoundTag}, {@code NBTTagList} → {@code ListTag}</li>
 *   <li>{@code markDirty()} → {@code setChanged()} (persistence) and {@code sendBlockUpdated} for
 *       client sync — they are not the same call</li>
 *   <li>{@code isInvalid()} → {@code isRemoved()}; {@code invalidate()} → {@code setRemoved()}</li>
 *   <li>{@code ITickable} on the tile is gone; see {@link #tick()}</li>
 * </ul>
 *
 * <p><b>Why the type is a {@link RegistryObject}.</b> A {@link BlockEntityType} cannot hand itself to
 * its own factory while it is still being constructed, so the usual
 * {@code new TileX(type, pos, state)} constructor is unbuildable for a DeferredRegister-owned type.
 * Passing the {@link RegistryObject} and resolving it inside the super constructor sidesteps that
 * without reflection: by the time any factory runs, the registry has already resolved.
 */
public abstract class TileEntityBase extends BlockEntity {

    /**
     * Resolves the type lazily, on first construction.
     *
     * <p>Taking a {@code Supplier} rather than a resolved {@link BlockEntityType} avoids two distinct
     * problems. First, a {@code BlockEntityType} cannot hand itself to its own factory while it is
     * still being constructed. Second and subtler: a tile class that references its own registry
     * holder inside a constructor would create a static-initialisation cycle, because the constructor
     * runs while that registry class is still initialising. Resolving at construction time (not class
     * load time) sidesteps both, and costs one virtual call per block entity.
     */
    protected TileEntityBase(java.util.function.Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type.get(), pos, state);
    }

    /**
     * Called once per server tick by the owning block's ticker, if that block supplies one.
     *
     * <p>1.12.2 implemented {@code net.minecraft.util.ITickable}; the interface is gone and a block
     * now opts into ticking per block-entity-type via
     * {@code Block#getTicker(Level, BlockState, BlockEntityType)}. Server-only by construction: the
     * block decides. Subclasses should treat this as the server tick and keep client rendering in a
     * {@code BlockEntityRenderer}.
     */
    public void tick() {
    }

    // ------------------------------------------------------------------
    // Synchronisation
    // ------------------------------------------------------------------

    /**
     * Full state, sent when a client begins tracking this block entity (chunk load / player join).
     * Delegates to {@link #saveAdditional} so there is exactly one serialisation path.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    /**
     * Marks the block entity dirty for persistence and pushes a sync packet to tracking clients.
     *
     * <p>1.12.2 code calls {@code markDirty()} for both purposes. On 1.20.1 they are separate:
     * {@code setChanged()} persists, and {@code sendBlockUpdated(..., Block.UPDATE_CLIENTS)} is what
     * actually reaches the client. Getting only the first one silently loses client state.
     */
    public void markForUpdate() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state,
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Pushes this block entity's data to the clients tracking it, WITHOUT a block update.
     *
     * <p><b>1.12.2 to 1.20.1, and why the port needed a second sync verb at all.</b> Upstream's
     * {@code TileEntitySynchronized} has two: {@code markForUpdate()} marks the chunk dirty and notifies the
     * block, and {@code markNoUpdateSync()} (its U117-U123) schedules the SAME data sync without the block
     * update -- which is what lets upstream call it every tick from a recipe tick
     * ({@code TileFactoryController} U145-U148) without flooding clients with block-change packets.</p>
     *
     * <p>The equivalence here is exact and was verified against the mapped jar rather than assumed:
     * {@code markForUpdate}'s {@code ServerLevel#sendBlockUpdated} calls
     * {@code ServerChunkCache.blockChanged(pos)}, and it is {@code ChunkHolder.broadcastChanges} that turns
     * that into a {@code ClientboundBlockUpdatePacket} <i>and</i> a {@code blockEntity.getUpdatePacket()}
     * send ({@code ChunkHolder.broadcastBlockEntityIfNeeded} then {@code broadcastBlockEntity} calling
     * {@code BlockEntity.getUpdatePacket()}). So the block update is the only part worth dropping, and the
     * data packet is the part worth keeping at a per-tick cadence.</p>
     *
     * <p>Forge's {@code PacketDistributor.TRACKING_CHUNK} is the 1.20.1 replacement for 1.12.2's "send this
     * packet to everyone who can see the block": it resolves the players tracking the chunk the block entity
     * stands in and sends each of them the packet this class's own {@link #getUpdatePacket} builds -- i.e.
     * {@link #getUpdateTag}, i.e. the same serialisation {@link #saveAdditional} writes and {@link #load}
     * reads back.</p>
     */
    public void markNoUpdateSync() {
        if (level == null || level.isClientSide()) {
            return;
        }
        setChanged();
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkAt(worldPosition);
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
        net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk).send(packet);
    }

    /**
     * True when the block entity is still usable. 1.12.2's {@code isInvalid()} has no direct 1.20.1
     * equivalent; {@code isRemoved()} reflects that the entity has been unloaded or destroyed.
     */
    public boolean isUsable() {
        return !isRemoved() && level != null;
    }
}
