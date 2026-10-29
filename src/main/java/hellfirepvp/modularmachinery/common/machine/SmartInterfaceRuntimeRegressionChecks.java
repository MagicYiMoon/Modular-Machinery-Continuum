/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementInterfaceNumInput;
import hellfirepvp.modularmachinery.common.event.machine.SmartInterfaceUpdateEvent;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceData;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Opt-in world fixtures for the existing smart-interface provider and its source-shaped runtime view. */
public final class SmartInterfaceRuntimeRegressionChecks {
    public static final String TARGET = "mmc_smart_runtime_regression";
    private SmartInterfaceRuntimeRegressionChecks() { }
    public static final class RecordingInterface extends TileSmartInterface {
        public int syncs, updates;
        public RecordingInterface(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void markNoUpdateSync() { syncs++; super.markNoUpdateSync(); }
        @Override public void markForUpdate() { updates++; super.markForUpdate(); }
        public void reset() { syncs = updates = 0; }
    }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines(); var before = new LinkedHashMap<>(machines);
        try {
            for (boolean factory : List.of(false, true)) {
                BlockPos pos = origin.offset(factory ? 32 : 0, 0, 0);
                var pattern = new MachinePattern();
                for (int x = 1; x <= 3; x++) pattern.addPart(new MachinePattern.Part(x, 0, 0, "modularmachinery:blocksmartinterface"));
                var machine = new DynamicMachine(TARGET, TARGET, pattern);
                machine.addSmartInterfaceType(new hellfirepvp.modularmachinery.common.util.SmartInterfaceType("mode", 1).setPriority(100));
                machine.addSmartInterfaceType(new hellfirepvp.modularmachinery.common.util.SmartInterfaceType("voltage", 10).setPriority(50));
                machines.put(new net.minecraft.resources.ResourceLocation("modularmachinery", TARGET), machine);
                saved.put(pos, level.getBlockState(pos));
                level.setBlock(pos, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get()).defaultBlockState(), Block.UPDATE_ALL);
                var tile = (TileController) level.getBlockEntity(pos);
                var interfaces = new ArrayList<RecordingInterface>();
                for (int x = 1; x <= 3; x++) {
                    var at = pos.offset(x, 0, 0); saved.put(at, level.getBlockState(at));
                    var state = RegistryBlocks.smartInterface.get().defaultBlockState(); level.setBlock(at, state, Block.UPDATE_ALL);
                    level.removeBlockEntity(at); var iface = new RecordingInterface(at, state); level.setBlockEntity(iface); interfaces.add(iface);
                }
                var events = new ArrayList<SmartInterfaceUpdateEvent>();
                ScriptMachineEventRegistry.register(TARGET, SmartInterfaceUpdateEvent.class, event -> {
                    events.add(event); var data = event.getScriptNewData();
                    require(data != null && data.getIPos().equals(pos) && TARGET.equals(data.getParentMachineName()), "publication lost controller position or parent registry name");
                    data.setValue(data.getValue() + 1);
                });
                require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "smart fixture did not form");
                require(events.size() == 3 && interfaces.get(0).provideComponent().getMachineData(0).getNumber() == 2
                        && interfaces.get(1).provideComponent().getMachineData(0).getNumber() == 11,
                        "publication edit failed before formation completed or recursively posted events: count=" + events.size()
                                + ", first=" + interfaces.get(0).provideComponent().getMachineData(0).getNumber()
                                + ", second=" + interfaces.get(1).provideComponent().getMachineData(0).getNumber());
                var publication = events.get(0).getScriptNewData(); publication.setValue(99);
                require(publication.getValue() == 2, "retained publication bypassed unformed write guard");
                clearEvents();
                tile.setMachineName(TARGET); tile.setFormed(true);
                checkViews(level, tile, machine, interfaces);
                if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
                    hellfirepvp.modularmachinery.integration.crafttweaker.SmartInterfaceRuntimeScriptRegressionChecks.run(tile, interfaces.get(0));
            }
            Modularmachinery.LOG.info("[MMC][smart-runtime] PASS: ordinary/factory publication, provider-backed queries/edits, duplicate types, requirements, save/sync, lifecycle and thread guards");
        } finally {
            clearEvents(); saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(before); MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkViews(ServerLevel level, TileController tile, DynamicMachine machine, List<RecordingInterface> interfaces) throws Exception {
        var iface = interfaces.get(0); iface.reset();
        SmartInterfaceData[] all = tile.getSmartInterfaceDataList();
        require(all.length == 3 && all[0].getType().equals("mode") && all[1].getType().equals("voltage")
                && all[2].getType().equals("mode") && tile.getSmartInterfaceData("missing") == null
                && tile.getSmartInterfaceData(null) == null, "query omitted a provider, invented missing data or changed binding order");
        var data = tile.getSmartInterfaceData("mode");
        require(data.getValue() == 2 && data.getIPos().equals(tile.getBlockPos()) && TARGET.equals(data.getParentMachineName()), "source getters differ");
        all[0] = null; require(tile.getSmartInterfaceDataList()[0] != null && iface.syncs == 0 && iface.updates == 0, "query array leaked storage or reads synchronized");
        var condition = new RequirementInterfaceNumInput("mode", 3.25F);
        var context = new MachineContext(level, tile.getBlockPos(), Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
        require(!condition.canStart(context), "old mode unexpectedly met range");
        data.setValue(3.25F);
        require(iface.provideComponent().getMachineData(0).getNumber() == 3.25 && condition.canStart(context)
                && MachineTicker.consumeInputs(List.of(condition), context, 64) && iface.syncs == 1 && iface.updates == 0,
                "runtime write did not reach real requirement/provider/save+sync");
        data.setValue(3.25F); require(iface.syncs == 1, "equal value synchronized again");
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            try { data.setValue(bad); throw new AssertionError("nonfinite runtime value accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var retained = data;
        Thread worker = new Thread(() -> { try {
            retained.setValue(90); require(tile.getSmartInterfaceDataList().length == 0 && tile.getSmartInterfaceData("mode") == null, "async query traversed world");
        } catch (Throwable problem) { error.set(problem); } }); worker.start(); worker.join();
        require(error.get() == null && data.getValue() == 3.25F && iface.syncs == 1, "async write/read guard failed");
        var disk = iface.saveWithoutMetadata(); var update = iface.getUpdateTag();
        var restored = new TileSmartInterface(iface.getBlockPos(), iface.getBlockState()); restored.load(disk);
        var observedController = tile instanceof TileFactoryController ? new TileFactoryController(tile.getBlockPos(), tile.getBlockState())
                : new TileController(tile.getBlockPos(), tile.getBlockState());
        observedController.load(tile.getUpdateTag());
        var observed = new SmartInterfaceUpdateEvent(observedController, iface.getBlockPos(), restored.provideComponent().getMachineData(0)).getScriptNewData();
        observed.setValue(90); restored.provideComponent().getMachineData(0).setNumber(77);
        require(observed.getValue() == 3.25F && data.getValue() == 3.25F, "offline observation mutated storage or retained live numeric alias");
        restored.load(update); require(restored.provideComponent().getMachineData(0).getNumber() == 3.25, "update NBT lost edited value");
        iface.load(disk); data.setValue(90);
        require(iface.provideComponent().getMachineData(0).getNumber() == 3.25, "pre-load data view mutated reloaded storage");
        data = tile.getSmartInterfaceData("mode");
        tile.setFormed(false); data.setValue(90);
        require(tile.getSmartInterfaceDataList().length == 0 && data.getValue() == 3.25F, "unformed controller retained writable query");
        tile.setFormed(true);
        iface.setControllerPos(tile.getBlockPos().east(20)); data.setValue(90);
        require(tile.getSmartInterfaceDataList().length == 2 && data.getValue() == 3.25F, "foreign component binding remained reachable");
        iface.setControllerPos(tile.getBlockPos());
        var bound = tile.getBoundPositions(); tile.setBoundPositions(bound.subList(1, bound.size())); data.setValue(90);
        require(data.getValue() == 3.25F && tile.getSmartInterfaceDataList().length == 2, "removed component remained writable");
        tile.setBoundPositions(bound);
        tile.setMachineName(TARGET + "_other"); data.setValue(90);
        require(tile.getSmartInterfaceDataList().length == 0 && data.getValue() == 3.25F, "old machine binding remained writable");
        tile.setMachineName(TARGET);
        iface.setRemoved(); data.setValue(90); require(data.getValue() == 3.25F, "removed interface remained writable"); iface.clearRemoved();
        tile.setRemoved(); data.setValue(90); require(data.getValue() == 3.25F, "removed controller remained writable"); tile.clearRemoved();
        var unloaded = new BlockPos(2_000_000, tile.getBlockPos().getY(), 2_000_000);
        require(!level.hasChunkAt(unloaded), "remote unloaded fixture already loaded");
        var remoteBound = new ArrayList<>(bound); remoteBound.add(unloaded); tile.setBoundPositions(remoteBound);
        require(tile.getSmartInterfaceDataList().length == 3 && !level.hasChunkAt(unloaded), "query force-loaded a chunk"); tile.setBoundPositions(bound);
        MachineReloadListener.refreshRecipes(); require(tile.getSmartInterfaceData("mode").getValue() == 3.25F, "definition refresh reset provider value");
        iface.provideComponent().removeMachineData("mode"); data.setValue(90);
        iface.provideComponent().addMachineData(TARGET, TARGET, machine.getSmartInterfaceType("mode"), true);
        require(data.getValue() == 3.25F && tile.getSmartInterfaceData("mode").getValue() == 1, "removed datum changed replacement or default not restored");
    }
    private static void clearEvents() throws Exception {
        var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).remove(TARGET);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
