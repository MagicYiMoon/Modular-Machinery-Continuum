/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Placed tile capability boundary: physical matrices, never their stored resources. */
public final class EStorageAutomationRegressionChecks {
    private EStorageAutomationRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos pos) {
        require(world.getBlockState(pos).isAir(), "automation fixture not empty");
        try {
            world.setBlockAndUpdate(pos, EStorageRegistration.DRIVE.get().defaultBlockState());
            var drive = (TileEStorageDrive) world.getBlockEntity(pos);
            var cap = drive.getCapability(ForgeCapabilities.ITEM_HANDLER);
            var slot = cap.orElseThrow(AssertionError::new);
            for (var direction : Direction.values()) require(drive.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElseThrow(AssertionError::new) == slot, "missing matrix side");
            require(slot.getSlots() == 1 && slot.getSlotLimit(0) == 1, "matrix slot size");
            var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.C);
            var matrix = new ItemStack(EStorageRegistration.CELLS.get(spec).get());
            matrix.getOrCreateTag().putString("author-marker", "keep-me");
            if (net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
                try (var contents = EStorageMatrixAccess.open(matrix, () -> { }, () -> true)) {
                    hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.seed(contents, spec);
                }
            }
            var original = matrix.copy();
            var many = matrix.copyWithCount(3);
            require(slot.insertItem(0, many, true).getCount() == 2 && !drive.hasMatrix() && many.getCount() == 3, "simulated insert changed matrix/source");
            require(slot.insertItem(-1, matrix, false) == matrix && slot.insertItem(1, matrix, true) == matrix
                    && !slot.isItemValid(0, new ItemStack(Items.DIAMOND)), "invalid slot/item accepted");
            require(slot.insertItem(0, new ItemStack(Items.DIAMOND), false).getItem() == Items.DIAMOND, "stored item entered physical slot");
            require(slot.insertItem(0, many, false).getCount() == 2 && many.getCount() == 3 && drive.hasMatrix(), "actual insertion not exactly one");
            require(ItemStack.matches(original, slot.getStackInSlot(0)), "inserted matrix contents/configuration changed");
            var exposed = slot.getStackInSlot(0); exposed.getOrCreateTag().putString("author-marker", "tampered"); exposed.setCount(0);
            require(ItemStack.matches(original, slot.getStackInSlot(0)), "returned stack leaked mutable matrix");
            require(slot.insertItem(0, matrix, false) == matrix, "occupied matrix overwritten");
            var saved = drive.saveWithoutMetadata();
            require(ItemStack.matches(original, slot.extractItem(0, Integer.MAX_VALUE, true)) && saved.equals(drive.saveWithoutMetadata()), "simulated extract mutated inventory");
            require(slot.extractItem(-1, 1, false).isEmpty() && slot.extractItem(0, 0, false).isEmpty(), "invalid extract");
            require(ItemStack.matches(original, slot.extractItem(0, Integer.MAX_VALUE, false)) && !drive.hasMatrix(), "actual extraction lost content or duplicated");
            require(slot.extractItem(0, 1, false).isEmpty(), "matrix extracted twice");
            slot.insertItem(0, matrix, false);
            drive.onChunkUnloaded();
            require(!cap.isPresent() && slot.getStackInSlot(0).isEmpty() && slot.extractItem(0, 1, false).isEmpty(), "unloaded capability retained access");
            drive.onLoad();
            var revived = drive.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
            require(revived != slot && ItemStack.matches(original, revived.getStackInSlot(0)) && slot.extractItem(0, 1, false).isEmpty(), "old capability resurrected");
            var persist = drive.saveWithoutMetadata(); drive.load(persist);
            require(ItemStack.matches(original, revived.extractItem(0, 1, false)), "NBT reload lost physical matrix");
            require(world.getBlockState(pos.west()).isAir() && world.getBlockState(pos.below()).isAir(), "hopper fixture not empty");
            world.setBlockAndUpdate(pos.west(), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.EAST));
            world.setBlockAndUpdate(pos.below(), Blocks.HOPPER.defaultBlockState());
            var input = (net.minecraft.world.level.block.entity.HopperBlockEntity) world.getBlockEntity(pos.west());
            var output = (net.minecraft.world.level.block.entity.HopperBlockEntity) world.getBlockEntity(pos.below());
            input.setItem(0, matrix.copy());
            require(net.minecraftforge.items.VanillaInventoryCodeHooks.insertHook(input) && input.isEmpty()
                    && ItemStack.matches(original, revived.getStackInSlot(0)), "native hopper did not insert matrix");
            require(Boolean.TRUE.equals(net.minecraftforge.items.VanillaInventoryCodeHooks.extractHook(world, output))
                    && !drive.hasMatrix() && ItemStack.matches(original, output.getItem(0)), "native hopper did not extract intact matrix");
            output.clearContent();
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            require(revived.insertItem(0, matrix, false) == matrix && revived.extractItem(0, 1, false).isEmpty(), "removed drive accepted old automation reference");
            Modularmachinery.LOG.info("[MMC][es-automation] PASS: all sides/null, single matrix limit, simulated/real insertion and extraction, NBT/content/config preservation, detached snapshots, invalid requests, unload/revive/removal, stale references and native hopper insertion/extraction hooks");
        } finally {
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(pos.west(), Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        }
    }
    private static void require(boolean result, String reason) { if (!result) throw new AssertionError(reason); }
}
