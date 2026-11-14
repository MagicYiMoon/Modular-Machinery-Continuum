/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;

/** Real placed ES tiles and vanilla/Forge useItemOn. Also runs when AE2 is entirely absent. */
public final class EStorageHostRegressionChecks {
    private EStorageHostRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos origin) {
        var machines = MachineReloadListener.getLoader().getMachines();
        var saved = new LinkedHashMap<>(machines);
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "MmcEsFixture"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        List<BlockPos> touched = new ArrayList<>();
        try {
            for (int tier : new int[]{4, 6, 9}) {
                var pattern = new MachinePattern();
                pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_cell_drive"));
                pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:estorage_casing"));
                var machine = new DynamicMachine(EStorageRegistration.machineName(tier), "ES test", pattern);
                machines.put(new net.minecraft.resources.ResourceLocation("modularmachinery", machine.getRegistryName()), machine);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockPos drivePos = StructureMatcher.toWorldOffset(origin, facing, 1, 0, 0);
                    BlockPos casing = StructureMatcher.toWorldOffset(origin, facing, 2, 0, 0);
                    for (BlockPos pos : List.of(origin, drivePos, casing)) {
                        require(world.getBlockState(pos).isAir(), "ES fixture must start in empty space"); touched.add(pos);
                    }
                    world.setBlockAndUpdate(origin, EStorageRegistration.CONTROLLERS.get(tier).get().defaultBlockState()
                            .setValue(BlockEStorageController.FACING, facing));
                    world.setBlockAndUpdate(drivePos, EStorageRegistration.DRIVE.get().defaultBlockState());
                    world.setBlockAndUpdate(casing, EStorageRegistration.CASING.get().defaultBlockState());
                    var controller = (TileEStorageController) world.getBlockEntity(origin);
                    var drive = (TileEStorageDrive) world.getBlockEntity(drivePos);
                    require(controller != null && drive != null, "ES block entity registration");
                    controller.refreshStructure();
                    require(controller.isAssembled() && controller.driveCount() == 1 && controller.isOperational(), "ES formation/rotation");
                    for (var entry : EStorageRegistration.CELLS.entrySet()) {
                        var spec = entry.getKey();
                        ItemStack cell = new ItemStack(entry.getValue().get());
                        cell.getOrCreateTag().putString("Signature", "preserved");
                        if (spec.kind() == EStorageCellSpec.Kind.GAS && !spec.backendAvailable()) {
                            var opaque = new CompoundTag(); opaque.putString("UnavailableChemical", "appmek:opaque-preserved-key");
                            cell.getOrCreateTag().put("MMCEStorage", opaque);
                        }
                        player.setItemInHand(InteractionHand.MAIN_HAND, cell);
                        click(world, player, drivePos);
                        require(player.getMainHandItem().isEmpty() && drive.hasMatrix(), "sneak insertion with occupied offhand");
                        require(!drive.insertMatrix(new ItemStack(entry.getValue().get())), "occupied slot overwrote matrix");
                        controller.refreshStructure();
                        boolean supported = spec.tier().supportedBy(tier);
                        require(drive.isTierSupported() == supported, "host level bypass");
                        boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2");
                        EStorageMatrixAccess oldAccess = drive.storageAccess();
                        require((oldAccess != null) == (spec.backendAvailable() && supported), "optional backend/host-tier access gate");
                        if (!spec.backendAvailable()) require(drive.summary() == null && drive.matrixIdleDrain() == 0,
                                "unavailable backend parsed unknown inventory or charged idle power");
                        if (oldAccess != null) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.seed(oldAccess, spec);
                        CompoundTag disk = drive.saveWithoutMetadata();
                        CompoundTag controllerDisk = controller.saveWithoutMetadata();
                        require(!drive.getUpdateTag().contains("Matrix"), "private matrix inventory leaked in chunk update");
                        // Recreate the runtime binding from saved tags; persisted formed flags grant no access.
                        controller.load(controllerDisk);
                        require(!controller.isAssembled() && drive.storageAccess() == null, "saved formation trusted before recheck");
                        drive.load(disk);
                        controller.tick();
                        if (oldAccess != null) {
                            hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(oldAccess, spec);
                            hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.checkCount(drive.storageAccess(), spec);
                        }
                        click(world, player, drivePos);
                        require(!drive.hasMatrix() && player.getMainHandItem().getCount() == 1
                                && player.getMainHandItem().getTag().getString("Signature").equals("preserved"), "matrix extraction/NBT identity");
                        require(drive.takeMatrix().isEmpty(), "second removal duplicated matrix");
                        if (!spec.backendAvailable()) require(player.getMainHandItem().getTag().equals(cell.getTag()), "absent backend access rewrote matrix NBT");
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }
                    if (tier == 9 && facing == Direction.NORTH) {
                        checkLifecycle(world, origin, drivePos, casing, controller, drive, machines, machine);
                        drive = (TileEStorageDrive) world.getBlockEntity(drivePos);
                    }
                    // Controller removal cannot delete matrix data from its separate drive.
                    var cell = new ItemStack(EStorageRegistration.CELLS.values().iterator().next().get());
                    require(drive.insertMatrix(cell), "insert before controller removal");
                    world.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
                    require(drive.hasMatrix() && drive.storageAccess() == null, "controller removal lost/mounted matrix");
                    require(drive.takeMatrix().getItem() == cell.getItem(), "unformed drive cannot return matrix");
                    world.setBlockAndUpdate(drivePos, Blocks.AIR.defaultBlockState());
                    world.setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState());
                }
            }
            checkExclusiveBinding(world, origin, machines, touched);
            checkChannelBinding(world, origin.above(8), machines, touched);
            if (net.minecraftforge.fml.ModList.get().isLoaded("ae2"))
                hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.checkNativeCells(world, origin.above(4));
            Modularmachinery.LOG.info("[MMC][es-host] PASS: S4/S6/S9, four facings, nine matrices, real sneak/offhand insertion/removal, no overwrite, host tier checks, save/rebind/revocation, private update tags, structure/definition loss, unload, filled-drive drop, controller removal, exclusive ownership, ME channel binding/opaque NBT/duplicate-channel checks; native ME slot/nesting checks only with AE2 present={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
        } finally {
            for (BlockPos pos : touched) {
                if (world.getBlockEntity(pos) instanceof TileEStorageDrive drive) drive.takeMatrix();
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            machines.clear(); machines.putAll(saved);
        }
    }
    private static void checkChannelBinding(ServerLevel world, BlockPos pos,
            Map<net.minecraft.resources.ResourceLocation, DynamicMachine> machines, List<BlockPos> touched) {
        String name = EStorageRegistration.machineName(9);
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_me_channel"));
        machines.put(new net.minecraft.resources.ResourceLocation("modularmachinery", name), new DynamicMachine(name, "ES channel fixture", pattern));
        touched.addAll(List.of(pos, pos.east(), pos.east(2)));
        world.setBlockAndUpdate(pos, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH));
        world.setBlockAndUpdate(pos.east(), EStorageRegistration.CHANNEL.get().defaultBlockState());
        var controller = (TileEStorageController) world.getBlockEntity(pos);
        var channel = (TileEStorageMEChannel) world.getBlockEntity(pos.east());
        var disk = new CompoundTag(); var opaque = new CompoundTag(); opaque.putString("FutureData", "preserved"); disk.put("MENode", opaque);
        channel.load(disk); controller.refreshStructure();
        require(controller.isAssembled() && controller.channel() == channel && channel.controller() == controller, "ME channel binding");
        require(channel.saveWithoutMetadata().getCompound("MENode").equals(opaque), "opaque ME identity not preserved");
        require(!channel.getUpdateTag().contains("MENode"), "private ME node data broadcast");
        pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:estorage_me_channel"));
        world.setBlockAndUpdate(pos.east(2), EStorageRegistration.CHANNEL.get().defaultBlockState());
        controller.refreshStructure();
        require(!controller.isAssembled() && channel.controller() == null, "multiple ME channels exposed shared matrices");
        require(!channel.isNetworkActive(), "unbound ME channel remained active");
    }
    private static void checkLifecycle(ServerLevel world, BlockPos origin, BlockPos drivePos, BlockPos casing,
                                       TileEStorageController controller, TileEStorageDrive drive,
                                       Map<net.minecraft.resources.ResourceLocation, DynamicMachine> machines, DynamicMachine machine) {
        var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.A);
        require(drive.insertMatrix(new ItemStack(EStorageRegistration.CELLS.get(spec).get())), "lifecycle insert");
        controller.refreshStructure();
        boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2");
        EStorageMatrixAccess old = drive.storageAccess();
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.seed(old, spec);
        // A retained backend may discover an invalidation before the drive's next access.
        controller.requestStructureCheck();
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(old, spec);
        controller.refreshStructure();
        if (ae) {
            require(drive.storageAccess() != old, "closed cached backend not replaced after reformation");
            hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.checkCount(drive.storageAccess(), spec);
        }
        old = drive.storageAccess(); drive.onChunkUnloaded();
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(old, spec);
        controller.refreshStructure();
        old = drive.storageAccess(); controller.onChunkUnloaded();
        require(!controller.isOperational() && drive.storageAccess() == null && drive.hasMatrix(), "unloaded host still mounted or lost matrix");
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(old, spec);
        controller.refreshStructure();
        old = drive.storageAccess();
        world.setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState()); controller.refreshStructure();
        require(!controller.isAssembled() && drive.storageAccess() == null && drive.hasMatrix(), "broken structure still mounted/lost contents");
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(old, spec);
        world.setBlockAndUpdate(casing, EStorageRegistration.CASING.get().defaultBlockState()); controller.refreshStructure();
        var id = new net.minecraft.resources.ResourceLocation("modularmachinery", machine.getRegistryName());
        old = drive.storageAccess(); machines.remove(id); controller.refreshStructure();
        require(!controller.isAssembled() && drive.storageAccess() == null && drive.hasMatrix(), "missing definition still mounted/lost contents");
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(old, spec);
        machines.put(id, machine); controller.refreshStructure();
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.checkCount(drive.storageAccess(), spec);
        ItemStack expected = drive.matrixSnapshot(); old = drive.storageAccess();
        var bounds = new net.minecraft.world.phys.AABB(drivePos).inflate(2);
        var previous = new HashSet<>(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds));
        world.setBlockAndUpdate(drivePos, Blocks.AIR.defaultBlockState());
        require(world.getBlockState(drivePos).isAir() && world.getBlockEntity(drivePos) == null, "occupied ES drive resurrected after removal");
        var dropped = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds,
                entity -> !previous.contains(entity) && entity.getItem().getItem() == expected.getItem());
        require(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 1
                && ItemStack.isSameItemSameTags(dropped.get(0).getItem(), expected), "broken drive did not drop exactly one intact matrix");
        if (ae) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.assertRevoked(old, spec);
        dropped.forEach(net.minecraft.world.entity.Entity::discard);
        world.setBlockAndUpdate(drivePos, EStorageRegistration.DRIVE.get().defaultBlockState());
        var replacementDrive = (TileEStorageDrive)world.getBlockEntity(drivePos);
        require(replacementDrive.insertMatrix(expected), "replacement matrix insert");
        var beforeReplacement = new HashSet<>(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds));
        world.setBlockAndUpdate(drivePos, Blocks.STONE.defaultBlockState());
        require(world.getBlockState(drivePos).is(Blocks.STONE) && world.getBlockEntity(drivePos) == null, "occupied ES drive overwrote stone replacement");
        dropped = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds,
                entity -> !beforeReplacement.contains(entity) && entity.getItem().getItem() == expected.getItem());
        require(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 1
                && ItemStack.isSameItemSameTags(dropped.get(0).getItem(), expected), "ES replacement did not preserve exactly one matrix");
        dropped.forEach(net.minecraft.world.entity.Entity::discard);
        // Restore the drive expected by the surrounding test.
        Modularmachinery.LOG.info("[MMC][es-drive-removal] PASS: occupied drive leaves air/no BE, stone replacement survives, exactly one intact matrix and backend revoked");
        world.setBlockAndUpdate(drivePos, EStorageRegistration.DRIVE.get().defaultBlockState());
        controller.refreshStructure();
    }
    private static void click(ServerLevel world, FakePlayer player, BlockPos pos) {
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2);
        var result = player.gameMode.useItemOn(player, world, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(result.consumesAction(), "real ES useItemOn was not consumed");
    }
    private static void checkExclusiveBinding(ServerLevel world, BlockPos origin, Map<net.minecraft.resources.ResourceLocation, DynamicMachine> machines, List<BlockPos> touched) {
        var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_cell_drive"));
        String name = EStorageRegistration.machineName(4);
        machines.put(new net.minecraft.resources.ResourceLocation("modularmachinery", name), new DynamicMachine(name, "ES shared fixture", pattern));
        BlockPos drivePos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        BlockPos second = drivePos.offset(drivePos.subtract(origin));
        touched.addAll(List.of(origin, drivePos, second));
        world.setBlockAndUpdate(origin, EStorageRegistration.CONTROLLERS.get(4).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH));
        world.setBlockAndUpdate(drivePos, EStorageRegistration.DRIVE.get().defaultBlockState());
        world.setBlockAndUpdate(second, EStorageRegistration.CONTROLLERS.get(4).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.SOUTH));
        var a = (TileEStorageController) world.getBlockEntity(origin); var b = (TileEStorageController) world.getBlockEntity(second);
        a.refreshStructure(); b.refreshStructure();
        require(a.isAssembled() && !b.isAssembled(), "two ES hosts claimed the same drive");
        world.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); b.refreshStructure();
        require(b.isAssembled(), "removed host retained drive ownership");
        // Original NECO additionally prohibits same-tier controllers two blocks above/below.
        BlockPos stacked = second.above(2); touched.add(stacked);
        world.setBlockAndUpdate(stacked, EStorageRegistration.CONTROLLERS.get(4).get().defaultBlockState()); b.refreshStructure();
        require(!b.isAssembled(), "vertically shared controller accepted");
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
