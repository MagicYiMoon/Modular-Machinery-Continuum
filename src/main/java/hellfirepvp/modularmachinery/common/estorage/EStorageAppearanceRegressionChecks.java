/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class EStorageAppearanceRegressionChecks {
    private EStorageAppearanceRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos pos) {
        var definitions = MachineReloadListener.getLoader().getMachines();
        var id = new ResourceLocation("modularmachinery", EStorageRegistration.machineName(9)); var saved = definitions.get(id);
        try {
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_cell_drive"));
            definitions.put(id, new DynamicMachine(id.getPath(), "ES visual rules", pattern));
            require(world.getBlockState(pos).isAir() && world.getBlockState(pos.east()).isAir(), "appearance fixture not empty");
            world.setBlockAndUpdate(pos, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState());
            world.setBlockAndUpdate(pos.east(), EStorageRegistration.DRIVE.get().defaultBlockState());
            var owner = (TileEStorageController) world.getBlockEntity(pos);
            var drive = (TileEStorageDrive) world.getBlockEntity(pos.east()); owner.refreshStructure();
            require(drive.appearance().equals(EStorageDriveAppearance.EMPTY) && drive.getBlockState().getLightEmission() == 2, "empty drive visual/light");
            for (var entry : EStorageRegistration.CELLS.entrySet()) {
                var spec = entry.getKey(); drive.insertMatrix(new ItemStack(entry.getValue().get())); owner.refreshStructure();
                var expected = EStorageDriveAppearance.of(spec, null, false);
                require(drive.appearance().equals(expected) && drive.getBlockState().getLightEmission() == 6, "physical matrix level/kind/light");
                require(drive.getModelData().get(EStorageDriveAppearance.DATA).equals(expected), "model data lost metadata");
                var tag = drive.getUpdateTag();
                require(tag.getAllKeys().equals(Set.of("Appearance")) && EStorageDriveAppearance.decode(tag.getInt("Appearance")).equals(expected), "private inventory in visual sync");
                var state = drive.getBlockState(); long revision = drive.networkRevision(); drive.tick();
                require(!owner.isStructureCheckRequested() && drive.getBlockState() == state && revision == drive.networkRevision(), "cosmetic update invalidated structure/network");
                for (var capacity : EStorageDriveAppearance.Capacity.values()) for (var status : EStorageDriveAppearance.Status.values()) {
                    var value = new EStorageDriveAppearance(expected.level(), expected.kind(), capacity, status);
                    require(EStorageDriveAppearance.decode(value.encode()).equals(value), "appearance encoding");
                }
                drive.takeMatrix(); owner.refreshStructure();
            }
            require(EStorageDriveAppearance.decode(-1).equals(EStorageDriveAppearance.EMPTY)
                    && EStorageDriveAppearance.decode(255).equals(EStorageDriveAppearance.EMPTY)
                    && EStorageDriveAppearance.decode(12).equals(EStorageDriveAppearance.EMPTY), "invalid metadata accepted");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                world.setBlockAndUpdate(pos, owner.getBlockState().setValue(BlockEStorageController.FACING, facing));
                var actual = new HashSet<>(owner.modelHiddenPositions());
                Direction right = facing.getClockWise(), back = facing.getOpposite();
                var expected = new HashSet<BlockPos>();
                for (int y = -1; y <= 1; y++) {
                    expected.add(pos.relative(right).offset(0, y, 0)); expected.add(pos.relative(back).offset(0, y, 0));
                    expected.add(pos.relative(right).relative(back).offset(0, y, 0));
                }
                expected.add(pos.above()); expected.add(pos.below());
                require(actual.equals(expected) && actual.size() == 11 && !actual.contains(pos), "source hidden neighbours/facing");
            }
            world.setBlockAndUpdate(pos, owner.getBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH)); owner.refreshStructure();
            if (net.minecraftforge.fml.ModList.get().isLoaded("ae2"))
                hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageAppearanceNativeChecks.run(drive, owner);
            Modularmachinery.LOG.info("[MMC][es-appearance] PASS: nine matrix level/type models, source 2/6 light, bounded metadata encoding, private update tag, cosmetic updates preserve block state/network/structure, eleven hidden neighbours in four facings; native full/type-full/read-write checks with AE2={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
        } finally {
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); world.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
            if (saved == null) definitions.remove(id); else definitions.put(id, saved);
        }
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
