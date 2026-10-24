/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.fluxnetworks;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockEnergyHatch;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxNetworkData;
import sonar.fluxnetworks.common.device.TileFluxPlug;
import sonar.fluxnetworks.common.device.TileFluxPoint;
import sonar.fluxnetworks.common.integration.energy.FNEnergyConnector;
import sonar.fluxnetworks.common.util.EnergyUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Real optional-mod connector selection, capability boundaries and wireless network transfers. */
public final class FluxNetworksRegressionChecks {
    private FluxNetworksRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) {
        boolean enabled = Config.entryForFluxNetworksIntegration().get();
        long capacity = Config.entryForEnergyCapacity(EnergyHatchData.ULTIMATE).get();
        long rate = Config.entryForEnergyTransfer(EnergyHatchData.ULTIMATE).get();
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            Config.entryForFluxNetworksIntegration().set(true);
            // All tier/direction combinations must expose the same buffer through FE and FN.
            for (EnergyHatchData tier : EnergyHatchData.values()) {
                for (boolean input : new boolean[]{true, false}) {
                    var hatch = placeHatch(level, origin, input, tier, before);
                    var fe = hatch.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
                    for (Direction face : Direction.values()) {
                        var fn = hatch.getCapability(FluxCapabilities.FN_ENERGY_STORAGE, face)
                                .orElseThrow(AssertionError::new);
                        require(fn.canReceive() == input && fn.canExtract() != input, "FN direction");
                        require(EnergyUtils.getConnector(hatch, face) == FNEnergyConnector.INSTANCE,
                                "Flux Networks selects the FN connector before FE");
                        require(fn.getMaxEnergyStoredL() == fe.getMaxEnergyStored(), "shared capacity");
                        require(fn.receiveEnergyL(-1, false) == 0 && fn.extractEnergyL(Long.MIN_VALUE, false) == 0,
                                "negative amounts cannot create or remove energy");
                        int expected = Math.min(hatch.getCapacity(), tier.getMaxTransfer());
                        if (input) {
                            require(fn.receiveEnergyL(Long.MAX_VALUE, true) == expected && hatch.getStoredEnergy() == 0,
                                    "long-max simulation clamps to tier without mutation");
                            require(fn.receiveEnergyL(Long.MAX_VALUE, false) == expected && fe.getEnergyStored() == expected,
                                    "FN fill visible through FE");
                            require(fn.extractEnergyL(Long.MAX_VALUE, false) == 0 && fe.extractEnergy(1, false) == 0,
                                    "external extraction from input forbidden");
                            require(hatch.consumeInternal(expected, false) == expected, "recipe consumes FN energy");
                        } else {
                            require(hatch.produceInternal(expected, false) == expected, "recipe produces energy");
                            require(fn.receiveEnergyL(Long.MAX_VALUE, false) == 0 && fe.receiveEnergy(1, false) == 0,
                                    "external filling of output forbidden");
                            require(fn.extractEnergyL(Long.MAX_VALUE, true) == expected && fe.getEnergyStored() == expected,
                                    "extraction simulation is read only");
                            require(fn.extractEnergyL(Long.MAX_VALUE, false) == expected && fe.getEnergyStored() == 0,
                                    "FN removal visible through FE");
                        }
                    }
                    var cap = hatch.getCapability(FluxCapabilities.FN_ENERGY_STORAGE);
                    IFNEnergyStorage retained = cap.orElseThrow(AssertionError::new);
                    Config.entryForFluxNetworksIntegration().set(false);
                    require(!hatch.getCapability(FluxCapabilities.FN_ENERGY_STORAGE).isPresent()
                                    && !retained.canReceive() && !retained.canExtract(), "config disables FN including cached view");
                    require(hatch.getCapability(ForgeCapabilities.ENERGY).isPresent(), "FE remains available");
                    Config.entryForFluxNetworksIntegration().set(true);
                    require(hatch.getCapability(FluxCapabilities.FN_ENERGY_STORAGE).isPresent(), "FN can be reenabled");
                    hatch.invalidateCaps();
                    require(!cap.isPresent() && retained.receiveEnergyL(1, false) == 0
                            && retained.extractEnergyL(1, false) == 0, "invalidation blocks cached operations");
                    level.removeBlock(origin, false);
                }
            }

            Config.entryForEnergyCapacity(EnergyHatchData.ULTIMATE).set((long) Integer.MAX_VALUE);
            Config.entryForEnergyTransfer(EnergyHatchData.ULTIMATE).set((long) Integer.MAX_VALUE);
            var full = placeHatch(level, origin, true, EnergyHatchData.ULTIMATE, before);
            var fn = full.getCapability(FluxCapabilities.FN_ENERGY_STORAGE).orElseThrow(AssertionError::new);
            require(fn.receiveEnergyL(Long.MAX_VALUE, false) == Integer.MAX_VALUE, "signed-int boundary saturates");
            require(fn.receiveEnergyL(1, false) == 0 && fn.getEnergyStoredL() == Integer.MAX_VALUE, "full buffer cannot overflow");
            var saved = full.saveWithoutMetadata();
            full.consumeInternal(100, false);
            full.load(saved);
            require(fn.getEnergyStoredL() == Integer.MAX_VALUE, "shared FN/FE energy survives NBT reload");
            level.removeBlock(origin, false);

            for (Direction face : Direction.values()) checkNetwork(level, origin, face, before);
            checkFeFallback(level, origin, before);
            Modularmachinery.LOG.info("[MMCE][flux-networks-regression] PASS: all tiers/sides, real FN connector, "
                    + "simulation, directions, long overflow/negative guards, NBT, invalidation/config fallback, "
                    + "six real plug/network/point paths, disconnection and energy conservation");
        } finally {
            Config.entryForFluxNetworksIntegration().set(enabled);
            Config.entryForEnergyCapacity(EnergyHatchData.ULTIMATE).set(capacity);
            Config.entryForEnergyTransfer(EnergyHatchData.ULTIMATE).set(rate);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
        }
    }

    private static void checkNetwork(ServerLevel level, BlockPos at, Direction face, Map<BlockPos, BlockState> before) {
        var output = (TileEnergyOutputHatch) placeHatch(level, at, false, EnergyHatchData.TINY, before);
        BlockPos plugAt = at.relative(face);
        BlockPos pointAt = at.offset(8, 0, 0);
        BlockPos inputAt = pointAt.relative(face);
        place(level, plugAt, sonar.fluxnetworks.register.RegistryBlocks.FLUX_PLUG.get().defaultBlockState(), before);
        place(level, pointAt, sonar.fluxnetworks.register.RegistryBlocks.FLUX_POINT.get().defaultBlockState(), before);
        var input = placeHatch(level, inputAt, true, EnergyHatchData.TINY, before);
        var plug = (TileFluxPlug) level.getBlockEntity(plugAt);
        var point = (TileFluxPoint) level.getBlockEntity(pointAt);
        var data = FluxNetworkData.getInstance();
        var network = data.createNetwork(FakePlayerFactory.getMinecraft(level), "MMC regression " + face,
                0x3498DB, SecurityLevel.PUBLIC, "");
        require(network != null, "test network created");
        try {
            require(plug.connect(network) && point.connect(network), "real devices connected");
            plug.getTransferHandler().setLimit(64);
            point.getTransferHandler().setLimit(64);
            plug.updateSideTransfer(face.getOpposite(), output);
            point.updateSideTransfer(face, input);
            // A first cycle establishes real demand / the network's plug buffer limiter.
            network.onEndServerTick();
            output.produceInternal(100, false);
            int initial = output.getStoredEnergy();
            for (int i = 0; i < 4; i++) {
                int previous = output.getStoredEnergy();
                output.step(level, at);
                require(previous - output.getStoredEnergy() <= 64, "plug transfer limit respected");
                network.onEndServerTick();
                long total = (long) output.getStoredEnergy() + input.getStoredEnergy()
                        + plug.getTransferHandler().getBuffer() + point.getTransferHandler().getBuffer();
                require(total == initial, "real network conserves all energy without double insertion");
            }
            require(input.getStoredEnergy() == initial && output.getStoredEnergy() == 0,
                    "output -> plug -> wireless network -> point -> input, " + face);
            plug.disconnect();
            network.onEndServerTick();
            output.produceInternal(50, false);
            output.step(level, at);
            require(output.getStoredEnergy() == 50, "disconnected plug must not drain output");
        } finally {
            level.removeBlock(plugAt, false);
            level.removeBlock(pointAt, false);
            level.removeBlock(at, false);
            level.removeBlock(inputAt, false);
            data.deleteNetwork(network);
        }
    }

    private static void checkFeFallback(ServerLevel level, BlockPos at, Map<BlockPos, BlockState> before) {
        Config.entryForFluxNetworksIntegration().set(false);
        var output = (TileEnergyOutputHatch) placeHatch(level, at, false, EnergyHatchData.TINY, before);
        var input = placeHatch(level, at.east(), true, EnergyHatchData.TINY, before);
        output.produceInternal(100, false);
        output.step(level, at);
        require(input.getStoredEnergy() == 100 && output.getStoredEnergy() == 0, "ordinary FE fallback with integration disabled");
        Config.entryForFluxNetworksIntegration().set(true);
    }

    private static TileEnergyHatch placeHatch(ServerLevel level, BlockPos at, boolean input, EnergyHatchData tier,
                                            Map<BlockPos, BlockState> before) {
        var block = input ? RegistryBlocks.energyInputHatch.get() : RegistryBlocks.energyOutputHatch.get();
        place(level, at, block.defaultBlockState().setValue(BlockEnergyHatch.SIZE, tier), before);
        return (TileEnergyHatch) level.getBlockEntity(at);
    }

    private static void place(ServerLevel level, BlockPos at, BlockState state, Map<BlockPos, BlockState> before) {
        before.putIfAbsent(at.immutable(), level.getBlockState(at));
        level.setBlock(at, state, Block.UPDATE_ALL);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
