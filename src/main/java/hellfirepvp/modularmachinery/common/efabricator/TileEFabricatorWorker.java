/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EFabricatorWorker. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;

/** Durable queue and charged work. All execution is serialized on the server thread by its host. */
public final class TileEFabricatorWorker extends TileEFabricatorPart {
    private EFabricatorQueue queue = new EFabricatorQueue();
    private double energy;
    private CompoundTag opaque;
    public TileEFabricatorWorker(BlockPos pos, BlockState state) { super(pos, state); }
    public boolean readable() { return opaque == null; }
    public int queuedCrafts() { return readable() ? queue.size() : -1; }
    public double energyStored() { return readable() ? energy : -1; }
    /** At most 33 entry icons; callers cannot access queue contents, quantities or private NBT. */
    public java.util.List<net.minecraft.world.item.ItemStack> productIcons() {
        return readable() ? queue.productIcons() : java.util.List.of();
    }
    /** Illustration of the FIFO head, never the real stack or its private NBT. */
    public net.minecraft.world.item.ItemStack currentProductIcon() {
        var first = readable() ? queue.peek(1) : null;
        return first == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(first.output().getItem());
    }
    public double energyNeeded() { return readable() && isActivePart() ? EFabricatorSpecs.ENERGY_CACHE - energy : 0; }
    public void supplyEnergy(double amount) {
        if (!Double.isFinite(amount) || amount < 0 || amount > energyNeeded()) throw new IllegalArgumentException("Invalid EF energy supply");
        if (amount > 0) { energy += amount; setChanged(); }
    }
    public int doWork(int maximumCrafts) {
        var host = owner();
        if (!readable() || !isActivePart() || !host.canManufacture()) return 0;
        int cost = host.tier().energyPerCraft(host.isOverclocked(), host.isActiveCooling());
        int remaining = Math.min(Math.min(Math.max(0, maximumCrafts), (int)(energy / cost)), host.cooledCraftLimit()), completed = 0;
        while (remaining > 0) {
            var work = queue.peek(remaining);
            if (work == null || !host.storeProducts(work)) break;
            queue.poll(work.crafts()); energy -= (long)cost * work.crafts();
            remaining -= work.crafts(); completed += work.crafts();
        }
        if (completed > 0) setChanged();
        return completed;
    }
    public int queueDepth() { return owner() == null ? EFabricatorSpecs.BASE_QUEUE : owner().tier().queueDepth(owner().isOverclocked()); }
    public int freeCrafts() { return readable() && isActivePart() ? Math.max(0, queueDepth() - queue.size()) : 0; }
    public int offerWork(EFabricatorQueue.Work work) {
        if (!readable() || !isActivePart()) return 0;
        int accepted = queue.offer(work, queueDepth()); if (accepted > 0) setChanged(); return accepted;
    }
    public CompoundTag workData() {
        if (opaque != null) return opaque.copy();
        var tag = new CompoundTag(); tag.putInt("Version", 1);
        // Preserve old integral records while also retaining fractional energy returned by AE.
        if (energy == Math.rint(energy)) tag.putInt("Energy", (int)energy); else tag.putDouble("Energy", energy);
        tag.put("Queue", queue.save()); return tag;
    }
    public void restoreWorkData(CompoundTag tag) {
        queue = new EFabricatorQueue(); energy = 0; opaque = null;
        if (!tag.isEmpty()) try {
            if (tag.getInt("Version") != 1 || !(tag.contains("Energy", Tag.TAG_INT) || tag.contains("Energy", Tag.TAG_DOUBLE))
                    || !Double.isFinite(tag.getDouble("Energy")) || tag.getDouble("Energy") < 0
                    || tag.getDouble("Energy") > EFabricatorSpecs.ENERGY_CACHE) throw new IllegalArgumentException("Invalid EF worker data");
            var loaded = EFabricatorQueue.load(tag.getCompound("Queue")); queue = loaded; energy = tag.getDouble("Energy");
        } catch (RuntimeException failure) { opaque = tag.copy(); }
        setChanged();
    }
    @Override public void load(CompoundTag tag) { super.load(tag); restoreWorkData(tag.getCompound("WorkData")); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("WorkData", workData()); }
}
