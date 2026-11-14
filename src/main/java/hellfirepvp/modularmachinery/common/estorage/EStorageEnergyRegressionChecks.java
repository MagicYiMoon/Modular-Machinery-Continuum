/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import com.mojang.authlib.GameProfile;
import java.util.*;

/** Real tiles, survival break/place, mixed-capacity aggregation. Also runs without AE. */
public final class EStorageEnergyRegressionChecks {
    private EStorageEnergyRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos origin) {
        var definitions = MachineReloadListener.getLoader().getMachines(); var saved = new LinkedHashMap<>(definitions);
        var touched = new ArrayList<BlockPos>();
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EsEnergyCheck"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        try {
            var pattern = new MachinePattern();
            int index = 1;
            for (int tier : new int[]{4, 6, 9}) pattern.addPart(new MachinePattern.Part(index++, 0, 0, "modularmachinery:estorage_energy_cell_l" + tier));
            String id = EStorageRegistration.machineName(9);
            definitions.put(new ResourceLocation("modularmachinery", id), new DynamicMachine(id, "ES energy test", pattern));
            for (int i = 0; i <= 4; i++) {
                var pos = origin.east(i); touched.add(pos); touched.add(pos.below());
                require(world.getBlockState(pos).isAir() && world.getBlockState(pos.below()).isAir(), "energy fixture not empty");
            }
            world.setBlockAndUpdate(origin, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH));
            TileEStorageEnergyCell[] cells = new TileEStorageEnergyCell[3]; index = 0;
            for (int tier : new int[]{4, 6, 9}) {
                var pos = origin.east(index + 1); var block = EStorageRegistration.ENERGY_CELLS.get(tier).get();
                world.setBlockAndUpdate(pos, block.defaultBlockState());
                var cell = (TileEStorageEnergyCell) world.getBlockEntity(pos); cells[index++] = cell;
                double expected = tier == 4 ? 10_000_000 : tier == 6 ? 100_000_000 : 1_000_000_000;
                require(cell.capacity() == expected, "source energy capacity");
                var tag = new CompoundTag(); tag.putDouble("energyStored", 123456.75); tag.putDouble("maxEnergyStore", Double.MAX_VALUE);
                cell.load(tag); require(cell.stored() == 123456.75 && cell.capacity() == expected, "saved capacity spoof");
                var disk = cell.saveWithoutMetadata(); cell.extract(Double.MAX_VALUE, false); cell.load(disk);
                require(cell.stored() == 123456.75, "fractional save/reload");
                for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
                    tag.putDouble("energyStored", bad); cell.loadEnergy(tag); require(cell.stored() == 0, "invalid saved energy accepted");
                }
                tag.putDouble("energyStored", expected * 2); cell.loadEnergy(tag); require(cell.stored() == expected, "over-capacity saved energy");
                for (double fraction : new double[]{0, .049, .05, .5, .7, .9, 1}) {
                    tag.putDouble("energyStored", expected * fraction); cell.loadEnergy(tag);
                    require(cell.getBlockState().getValue(BlockEStorageEnergyCell.STATUS) == BlockEStorageEnergyCell.Status.fromFraction(fraction), "source charge status threshold");
                }
                cell.extract(Double.MAX_VALUE, false);
            }
            var host = (TileEStorageController) world.getBlockEntity(origin); host.refreshStructure();
            require(host.isOperational() && host.energyCapacity() == 1_110_000_000, "mixed battery aggregation");
            require(host.injectPower(1_200_000_000, true) == 90_000_000 && host.storedEnergy() == 0, "simulation did not span empty batteries");
            require(host.injectPower(1_200_000_000, false) == 90_000_000 && host.storedEnergy() == 1_110_000_000, "overflow not conserved");
            require(host.extractPower(1_200_000_000, true) == 1_110_000_000 && host.storedEnergy() == 1_110_000_000, "discharge simulation changed energy");
            require(host.extractPower(1_200_000_000, false) == 1_110_000_000 && host.storedEnergy() == 0, "multi-battery extraction stopped at empty cell");
            cells[0].inject(100, false); cells[1].inject(50, false); cells[2].inject(200, false);
            host.injectPower(25, false); require(cells[1].stored() == 75, "charge not sent to least-stored cell");
            host.extractPower(25, false); require(cells[2].stored() == 175, "discharge not sent to most-stored cell");
            double before = host.storedEnergy();
            for (double amount : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
                host.injectPower(amount, false); require(host.extractPower(amount, false) == 0 && host.storedEnergy() == before, "invalid transfer changed energy");
            }
            // Real survival mining and BlockItem placement must move exactly one charged item.
            var pos = cells[0].getBlockPos(); var bounds = new AABB(pos).inflate(3);
            double charge = cells[0].stored(); var previous = new HashSet<>(world.getEntitiesOfClass(ItemEntity.class, bounds));
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            require(player.gameMode.destroyBlock(pos), "survival mining failed");
            var drops = world.getEntitiesOfClass(ItemEntity.class, bounds,
                    e -> !previous.contains(e) && e.getItem().is(EStorageRegistration.ENERGY_CELLS.get(4).get().asItem()));
            require(drops.size() == 1 && drops.get(0).getItem().getCount() == 1
                    && drops.get(0).getItem().getTag().getDouble("energyStored") == charge && cells[0].stored() == 0, "charged drop missing or duplicated");
            ItemStack item = drops.get(0).getItem().copy(); drops.get(0).discard();
            host.refreshStructure(); require(!host.isAssembled() && host.energyCapacity() == 0 && cells[1].stored() == 75, "removed battery still counted/lost other battery");
            world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            var result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false)));
            require(result.consumesAction() && item.isEmpty(), "survival charged item placement failed");
            var restored = (TileEStorageEnergyCell) world.getBlockEntity(pos);
            require(restored != null && restored.stored() == charge && restored.capacity() == 10_000_000, "placement lost stored energy");
            host.refreshStructure(); require(host.storedEnergy() == before && host.energyCapacity() == 1_110_000_000, "break/place energy not conserved");
            cells[1].onChunkUnloaded(); host.requestStructureCheck();
            require(host.extractPower(1, false) == 0 && host.storedEnergy() == 0, "unloaded component still accessible before recheck");
            host.refreshStructure(); require(host.storedEnergy() == before, "rebind lost energy");
            // Two opposite hosts cannot claim the same battery, just as for matrix drives.
            var shared = new MachinePattern(); shared.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_energy_cell_l4"));
            definitions.put(new ResourceLocation("modularmachinery", id), new DynamicMachine(id, "shared energy", shared));
            world.setBlockAndUpdate(origin.east(2), Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(origin.east(2), EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.SOUTH));
            host.refreshStructure(); var second = (TileEStorageController) world.getBlockEntity(origin.east(2)); second.refreshStructure();
            require(host.isAssembled() && !second.isAssembled(), "battery claimed by two hosts");
            Modularmachinery.LOG.info("[MMC][es-energy] PASS: source three capacities, fractional NBT, fixed capacity/invalid input checks, mixed battery overflow and simulation, least/most-stored order, survival charged drop/place conservation, five charge states, unload/rebind and exclusive ownership; AE2 optional");
        } finally {
            for (var pos : touched) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            definitions.clear(); definitions.putAll(saved);
            for (var drop : world.getEntitiesOfClass(ItemEntity.class, new AABB(origin, origin.east(5)).inflate(3)))
                if (EStorageRegistration.ENERGY_CELLS.values().stream().anyMatch(b -> drop.getItem().is(b.get().asItem()))) drop.discard();
        }
    }
    private static void require(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
