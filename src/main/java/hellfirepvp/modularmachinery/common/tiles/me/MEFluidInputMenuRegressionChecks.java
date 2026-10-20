/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;

import java.util.UUID;

/** Exercises the input bus menu's config and stored-fluid data channels. */
public final class MEFluidInputMenuRegressionChecks {
    private MEFluidInputMenuRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("d7a663e4-a2b1-4ae8-92d8-2f2f4f1f4d91"), "MMCEFluidInputMenu"));
        var previous = level.getBlockState(pos);
        try {
            level.removeBlock(pos, false);
            var block = RegistryMEBlocks.all().get("blockmefluidinputbus").get();
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(pos) instanceof MEFluidInputBus, "input bus tile missing");
            var bus = (MEFluidInputBus) level.getBlockEntity(pos);
            player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            var result = block.use(level.getBlockState(pos), level, pos, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            require(result == InteractionResult.SUCCESS && player.containerMenu instanceof MenuMEFluidInputBus,
                    "right-click did not open input menu");
            var menu = (MenuMEFluidInputBus) player.containerMenu;
            require(menu.slots.size() == 50 && menu.stillValid(player), "input menu slots or validity wrong");
            for (int i = 0; i < 5; i++) {
                require(menu.slots.get(45 + i).x == 187 && menu.slots.get(45 + i).y == 8 + 18 * i,
                        "input upgrade slot geometry is wrong");
            }
            require(menu.slots.get(0).x == 8 && menu.slots.get(0).y == 35
                            && menu.slots.get(9).y == MenuMEFluidInputBus.PLAYER_INV_Y
                            && menu.slots.get(36).y == MenuMEFluidInputBus.PLAYER_HOTBAR_Y,
                    "input menu geometry does not match source texture");
            require(menu.configuredFluidFor(0).isEmpty() && menu.storedFluidFor(0).isEmpty(),
                    "empty input bus exposed fluid data");

            bus.setConfiguredFluid(0, new FluidStack(Fluids.WATER, 1));
            bus.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 1250));
            bus.tankAt(8).setFluid(new FluidStack(Fluids.LAVA, 8000));
            require(menu.configuredFluidFor(0).getFluid() == Fluids.WATER
                            && menu.configuredFluidFor(0).getAmount() == 8000,
                    "configured fluid data did not normalize to tank capacity");
            require(menu.storedFluidFor(0).getFluid() == Fluids.WATER
                            && menu.storedFluidFor(0).getAmount() == 1250
                            && menu.storedFluidFor(8).getFluid() == Fluids.LAVA
                            && menu.storedFluidFor(8).getAmount() == 8000,
                    "stored fluid data did not follow first/last tank");
            require(MenuMEFluidInputBus.DATA_SIZE == 36, "input menu did not expose config and stored channels");

            bus.tankAt(0).setFluid(FluidStack.EMPTY);
            bus.tankAt(8).setFluid(FluidStack.EMPTY);
            require(menu.storedFluidFor(0).isEmpty() && menu.storedFluidFor(8).isEmpty(),
                    "drained tanks stayed visible in menu data");
            level.removeBlock(pos, false);
            require(!menu.stillValid(player), "removed input bus kept menu valid");
            Modularmachinery.LOG.info("[MMCE][regression] ME fluid input menu PASS: config and stored tank sync, geometry, removal");
        } finally {
            player.closeContainer();
            level.setBlock(pos, previous, Block.UPDATE_ALL);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
