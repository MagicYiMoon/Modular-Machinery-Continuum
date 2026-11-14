/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/** Exercises Forge's item-before-block dispatch through the real server right-click entry point. */
public final class ConstructToolInteractionRegressionChecks {
    private ConstructToolInteractionRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) {
        var player = new RecordingPlayer(level);
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        BlockPos second = origin.offset(2, 0, 0);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        previous.put(origin, level.getBlockState(origin));
        previous.put(second, level.getBlockState(second));
        List<Block> blocks = new ArrayList<>();
        ForgeRegistries.BLOCKS.getEntries().forEach(entry -> {
            String namespace = entry.getKey().location().getNamespace();
            if (namespace.equals(Modularmachinery.MODID) || namespace.equals("modularmagic"))
                blocks.add(entry.getValue());
        });
        blocks.add(Blocks.CHEST);
        blocks.add(Blocks.FURNACE);
        int marked = 0;
        int controllers = 0;
        try {
            for (Block block : blocks) {
                PlayerStructureSelectionHelper.purgeSelection(player);
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RegistryItems.CONSTRUCT_TOOL.get()));
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                level.setBlock(origin, block.defaultBlockState(), Block.UPDATE_ALL);
                player.menuOpens = 0;
                player.messages.clear();
                require(click(player, level, origin, InteractionHand.MAIN_HAND).consumesAction(),
                        "selection tool did not consume click on " + block);
                require(player.menuOpens == 0 && player.containerMenu == player.inventoryMenu,
                        "block opened a menu before selection on " + block);
                var selection = PlayerStructureSelectionHelper.selectionOf(player);
                if (block instanceof BlockController) {
                    require(selection.isEmpty(), "controller click unexpectedly marked a corner");
                    require(player.messages.size() == 1 && Component.Serializer.toJson(player.messages.get(0))
                                    .contains("message.structurebuild.empty"),
                            "empty selection message was missing or repeated on " + block);
                    controllers++;
                    continue;
                }
                require(origin.equals(selection.getFirstCorner()) && selection.getSecondCorner() == null,
                        "interactive block prevented first corner on " + block);
                level.setBlock(second, block.defaultBlockState(), Block.UPDATE_ALL);
                player.setShiftKeyDown(true);
                require(click(player, level, second, InteractionHand.MAIN_HAND).consumesAction(),
                        "sneak selection was not consumed on " + block);
                require(selection.isComplete() && origin.equals(selection.getFirstCorner())
                                && second.equals(selection.getSecondCorner()) && player.menuOpens == 0,
                        "sneak selection opened a menu or failed to mark second corner on " + block);
                require(player.getMainHandItem().getCount() == 1,
                        "selection consumed the construction tool");
                marked++;
            }

            // Removing the tool must restore the hatch menu; the fix belongs to the item only.
            player.setShiftKeyDown(false);
            for (Block block : List.of(RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(),
                    RegistryBlocks.fluidInputHatch.get(), RegistryBlocks.fluidOutputHatch.get(),
                    RegistryBlocks.energyInputHatch.get(), RegistryBlocks.energyOutputHatch.get(),
                    RegistryBlocks.smartInterface.get(), RegistryBlocks.parallelController.get(),
                    RegistryBlocks.upgradeBus.get(), Blocks.CHEST, Blocks.FURNACE)) {
                level.setBlock(origin, block.defaultBlockState(), Block.UPDATE_ALL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                player.menuOpens = 0;
                click(player, level, origin, InteractionHand.MAIN_HAND);
                require(player.menuOpens == 1, "ordinary block menu stopped opening on " + block);
                if (player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
            }

            PlayerStructureSelectionHelper.purgeSelection(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RegistryItems.CONSTRUCT_TOOL.get()));
            level.setBlock(origin, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            click(player, level, origin, InteractionHand.OFF_HAND);
            require(PlayerStructureSelectionHelper.selectionOf(player).isEmpty(),
                    "offhand tool unexpectedly modified the main-hand selection");
            Modularmachinery.LOG.info("[MMCE][construct-tool-interaction] PASS: {} blocks mark both corners, "
                    + "{} controller variants retain export/error handling; ordinary menus and offhand preserved",
                    marked, controllers);
        } finally {
            player.setShiftKeyDown(false);
            PlayerStructureSelectionHelper.purgeSelection(player);
            previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
        }
    }

    private static InteractionResult click(RecordingPlayer player, ServerLevel level, BlockPos pos,
                                           InteractionHand hand) {
        return player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static final class RecordingPlayer extends FakePlayer {
        private int menuOpens;
        private final List<Component> messages = new ArrayList<>();

        private RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "ConstructToolTest"));
        }

        @Override
        public OptionalInt openMenu(MenuProvider provider) {
            menuOpens++;
            return OptionalInt.empty();
        }

        @Override
        public void nextContainerCounter() {
            menuOpens++;
            super.nextContainerCounter();
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message.copy());
        }
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
