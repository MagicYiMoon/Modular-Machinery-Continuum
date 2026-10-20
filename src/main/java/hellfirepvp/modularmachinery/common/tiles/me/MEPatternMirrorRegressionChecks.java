/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.tiles.me;

import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.machine.MachineSelfTest;
import hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;

/** Integrated-only checks for the source mod's coordinate-bound ME mirror. */
public final class MEPatternMirrorRegressionChecks {
    private MEPatternMirrorRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        BlockPos providerPos = origin;
        BlockPos mirrorPos = origin.offset(1, 0, 0);
        BlockState providerState = hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks
                .all().get("blockmepatternprovider").get().defaultBlockState();
        BlockState mirrorState = hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks
                .all().get("blockmepatternmirrorimage").get().defaultBlockState();
        try {
            level.removeBlock(providerPos, false);
            level.removeBlock(mirrorPos, false);
            level.setBlock(providerPos, providerState, Block.UPDATE_ALL);
            level.setBlock(mirrorPos, mirrorState, Block.UPDATE_ALL);
            require(level.getBlockEntity(providerPos) instanceof TileMEPatternProvider,
                    "provider block did not create its AE2 tile");
            require(level.getBlockEntity(mirrorPos) instanceof TileMEPatternMirrorImage,
                    "mirror block did not create its tile");
            TileMEPatternProvider provider = (TileMEPatternProvider) level.getBlockEntity(providerPos);
            TileMEPatternMirrorImage mirror = (TileMEPatternMirrorImage) level.getBlockEntity(mirrorPos);
            mirror.setProviderPos(providerPos);

            var direct = provider.machineItemHandler();
            direct.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false);
            var mirrored = mirror.machineItemHandler();
            require(mirrored == direct, "mirror did not expose the provider's live item view");
            require(mirrored.getStackInSlot(0).getCount() == 3, "mirror view lost provider contents");

            MachineContext context = new MachineContext(level, origin, net.minecraft.core.Direction.NORTH,
                    MachineReloadListener.getLoader().findByName("alloy_furnace"),
                    MachineReloadListener.getLoader().getVariables(),
                    MachineReloadListener.getLoader().findByName("alloy_furnace").getPattern());
            context.overrideComponentPositions(List.of(providerPos, mirrorPos));
            var handlers = context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT);
            require(handlers.size() == 1 && handlers.get(0) == direct,
                    "provider and mirror counted the same cache more than once");

            provider.setWorkMode(WorkModeSetting.ISOLATION_INPUT);
            require(provider.getIsolatedItemHandlers().size() == TileMEPatternProvider.PATTERNS,
                    "provider did not expose 36 isolation handlers");
            require(mirror.getIsolatedItemHandlers().size() == TileMEPatternProvider.PATTERNS,
                    "mirror did not forward isolation handlers");
            require(mirror.getIsolatedItemHandlers().get(0) == provider.getIsolatedItemHandlers().get(0),
                    "mirror copied rather than forwarded an isolation handler");
            require(context.isolationGroups().size() == TileMEPatternProvider.PATTERNS,
                    "provider plus mirror created duplicate isolation groups");
            context.selectIsolationGroup(context.isolationGroups().get(0).key());
            var isolated = context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT);
            require(isolated.size() == 1, "selected isolation group borrowed another slot");

            var saved = mirror.saveWithoutMetadata();
            require(saved.getLong("providerPos") == providerPos.asLong(), "mirror providerPos was not persisted");
            mirror.setProviderPos(null);
            mirror.load(saved);
            require(providerPos.equals(mirror.getProviderPos()), "mirror providerPos did not round-trip");

            level.removeBlock(providerPos, false);
            require(mirror.machineItemHandler() == null,
                    "mirror still exposed a removed provider");
            provider.setWorkMode(WorkModeSetting.DEFAULT);
            System.out.println("[MMCE][regression] ME pattern mirror PASS");
        } finally {
            level.removeBlock(providerPos, false);
            level.removeBlock(mirrorPos, false);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
