/* SPDX-License-Identifier: GPL-3.0-only
 * Port of ModularMagic's orb-backed life essence buffer to Blood Magic 3.
 */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.*;
import wayoftime.bloodmagic.common.item.IBloodOrb;
import wayoftime.bloodmagic.core.data.*;
import wayoftime.bloodmagic.util.helper.NetworkHelper;

public final class TileLifeEssenceProvider extends TileMachineComponent implements BreakInventoryProvider {
    private final boolean input;
    private int essence;
    private final ItemStackHandler orb = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof IBloodOrb; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { markForUpdate(); }
    };
    public TileLifeEssenceProvider(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean input) {
        super(() -> type, pos, state); this.input = input;
    }
    @Override public IOType getIOType() { return input ? IOType.INPUT : IOType.OUTPUT; }
    public ItemStackHandler orbInventory() { return orb; }
    @Override public IItemHandler getBreakInventory() { return orb; }
    public int amount() { return essence; }
    public int orbCapacity() {
        ItemStack stack = orb.getStackInSlot(0);
        if (!(stack.getItem() instanceof IBloodOrb item)) return 0;
        var bloodOrb = item.getOrb(stack);
        return bloodOrb == null ? 0 : Math.max(0, bloodOrb.getCapacity());
    }
    // Source recipe reservations and the input network refill both use one tenth of orb capacity.
    public int capacity() { return orbCapacity() / 10; }
    public SoulNetwork network() {
        ItemStack stack = orb.getStackInSlot(0);
        if (!(stack.getItem() instanceof IBloodOrb)) return null;
        Binding binding = Binding.fromStack(stack);
        return binding == null ? null : NetworkHelper.getSoulNetwork(binding);
    }
    public long take(long amount, boolean simulate) {
        int taken = (int) Math.min(Math.max(0L, amount), essence);
        if (!simulate && taken > 0) { essence -= taken; setChanged(); }
        return taken;
    }
    public long add(long amount, boolean simulate) {
        int added = (int) Math.min(Math.max(0L, amount), Math.max(0L, (long) capacity() - essence));
        if (!simulate && added > 0) { essence += added; setChanged(); }
        return added;
    }
    @Override public void tick() {
        if (level == null || level.isClientSide()) return;
        var network = network();
        if (network == null || orbCapacity() <= 0) return;
        if (input) {
            int needed = Math.min(Math.max(0, capacity() - essence), Math.max(0, network.getCurrentEssence()));
            if (needed > 0) {
                int paid = network.syphon(SoulTicket.block(level, worldPosition, needed));
                if (paid > 0) { essence += paid; setChanged(); }
            }
        } else if (essence > 0) {
            // Bound the ticket before SoulNetwork's int addition, including old over-capacity saves.
            int room = (int) Math.max(0L, (long) orbCapacity() - network.getCurrentEssence());
            int offered = Math.min(essence, room);
            if (offered > 0) {
                int accepted = network.add(SoulTicket.block(level, worldPosition, offered), orbCapacity());
                if (accepted > 0) { essence -= accepted; setChanged(); }
            }
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.put("orb", orb.serializeNBT()); tag.putInt("lifeEssenceCache", essence);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        var inventory = tag.getCompound("orb").copy();
        inventory.putInt("Size", 1); // An old/empty tag must not erase the only orb slot.
        orb.deserializeNBT(inventory); essence = Math.max(0, tag.getInt("lifeEssenceCache"));
    }
}
