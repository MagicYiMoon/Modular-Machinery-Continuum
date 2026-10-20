/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEFluidOutputBus;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;

import java.util.UUID;

/** Exercises the AE2-only right-click and live output tank menu on a disposable self-test plot. */
public final class MEFluidOutputMenuRegressionChecks {
    private MEFluidOutputMenuRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("eb8809de-b462-4e43-ab27-0b966c53cc2f"), "MMCEFluidMenu"));
        var previous = level.getBlockState(pos);
        try {
            level.removeBlock(pos, false);
            var block = RegistryMEBlocks.all().get("blockmefluidoutputbus").get();
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(pos) instanceof MEFluidOutputBus, "output bus tile missing");
            var bus = (MEFluidOutputBus) level.getBlockEntity(pos);
            player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            var result = block.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            require(result == InteractionResult.SUCCESS && player.containerMenu instanceof MenuMEFluidOutputBus,
                    "right-click did not open output menu");
            var menu = (MenuMEFluidOutputBus) player.containerMenu;
            require(menu.slots.size() == 50 && menu.stillValid(player), "menu slots or validity wrong");
            for (int i = 0; i < 5; i++) {
                require(menu.slots.get(45 + i).x == 187 && menu.slots.get(45 + i).y == 8 + 18 * i,
                        "output upgrade slot geometry is wrong");
            }
            require(menu.slots.get(9).y == 112 && menu.slots.get(36).y == 170,
                    "inventory slots do not match source output texture");
            require(menu.fluidFor(0).isEmpty(), "empty tank displayed a fluid");
            bus.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 1250));
            bus.tankAt(8).setFluid(new FluidStack(Fluids.LAVA, 8000));
            require(menu.fluidFor(0).getFluid() == Fluids.WATER && menu.fluidFor(0).getAmount() == 1250
                    && menu.fluidFor(8).getFluid() == Fluids.LAVA && menu.fluidFor(8).getAmount() == 8000,
                    "open menu did not follow first/last output tank");
            for (int slot = 0; slot < 9; slot++) {
                require(!menu.slots.get(slot).mayPickup(player)
                        && !menu.slots.get(slot).mayPlace(new ItemStack(Items.WATER_BUCKET)),
                        "fluid display accepts items");
            }
            bus.tankAt(0).setFluid(FluidStack.EMPTY);
            require(menu.fluidFor(0).isEmpty(), "drained tank stayed visible in menu");
            level.removeBlock(pos, false);
            require(!menu.stillValid(player), "removed output bus kept menu valid");
            Modularmachinery.LOG.info("[MMCE][regression] ME fluid output menu PASS: right-click, 9 read-only tanks, live changes, texture geometry, removal");
        } finally {
            player.closeContainer();
            level.setBlock(pos, previous, Block.UPDATE_ALL);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
