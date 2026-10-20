/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Exercises the item-input menu's JEI ghost and count-edit packet paths. */
public final class MEItemInputMenuRegressionChecks {
    private MEItemInputMenuRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos pos) {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("8b6b95f6-1fc5-4ff5-8ab3-0d441a0e9d7e"), "MMCEItemInputMenu"));
        var previous = level.getBlockState(pos);
        try {
            level.removeBlock(pos, false);
            var block = RegistryMEBlocks.all().get("blockmeiteminputbus").get();
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(pos) instanceof MEItemInputBus, "item input bus tile missing");
            var bus = (MEItemInputBus) level.getBlockEntity(pos);
            player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            var result = block.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            require(result == InteractionResult.SUCCESS && player.containerMenu instanceof MenuMEItemInputBus,
                    "right-click did not open item input menu");
            var menu = (MenuMEItemInputBus) player.containerMenu;
            require(menu.slots.size() == 68 && menu.stillValid(player), "item input menu slots or validity wrong");

            ItemStack ghost = new ItemStack(Items.DIAMOND, 1);
            require(ModularmachineryNetwork.PktMEItemInputBusGhost.apply(player, pos, 0, ghost),
                    "JEI ghost write was rejected");
            require(bus.getConfigInventory().getStackInSlot(0).is(Items.DIAMOND)
                            && bus.getConfigInventory().getStackInSlot(0).getCount() == 1,
                    "JEI ghost item did not reach config inventory");
            require(ModularmachineryNetwork.PktMEItemInputBusCount.apply(player, pos, 0, 128),
                    "item input count write was rejected");
            require(bus.getConfigInventory().getStackInSlot(0).getCount() == 128,
                    "item input count write did not update marked stack");
            require(!ModularmachineryNetwork.PktMEItemInputBusCount.apply(player, pos, 16, 2),
                    "out-of-range item input slot was accepted");
            level.removeBlock(pos, false);
            require(!menu.stillValid(player), "removed item input bus kept menu valid");
            Modularmachinery.LOG.info("[MMCE][regression] ME item input menu PASS: JEI ghost write, count edit, slot validation, removal");
        } finally {
            player.closeContainer();
            level.setBlock(pos, previous, Block.UPDATE_ALL);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
