/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in disposable world. Advances REAL AE networks through server ticks; never shipped. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class EStorageNetworkVerification {
    private static MinecraftServer server;
    private static ServerLevel world;
    private static final BlockPos BASE = new BlockPos(0, 120, 0);
    private static final BlockPos CHANNEL = BASE.south(), POWER = BASE.south(2), CASING = BASE.east(3);
    private static final AEKey ITEM = AEItemKey.of(Items.DIAMOND), FLUID = AEFluidKey.of(Fluids.WATER);
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final long ITEMS = 3_000_000_000L, FLUIDS = 123456789L;
    private static final ResourceLocation ID = new ResourceLocation("modularmachinery", EStorageRegistration.machineName(9));
    private static DynamicMachine previous;
    private static TileEStorageController controller;
    private static TileEStorageDrive itemDrive, fluidDrive;
    private static TileEStorageDrive gasDrive;
    private static AEKey gas;
    private static final long GASES = 9_000_000_000L;
    private static boolean gasesEnabled() { return net.minecraftforge.fml.ModList.get().isLoaded("appmek"); }
    private static void gasPattern(MachinePattern pattern) {
        if (gasesEnabled()) pattern.addPart(new MachinePattern.Part(-1, 0, 0, "modularmachinery:estorage_cell_drive"));
    }
    private static EStorageMEChannelNode channel;
    private static EStorageCellInventory oldBackend;
    private static MEStorage obsoleteView;
    private static int ticks, phase, since;
    private static IManagedGridNode previousNode;
    private static TileEStorageEnergyCell battery;
    private static double batteryBefore, gridCapacity;
    private static long appearanceRevision;
    private static net.minecraft.world.level.block.state.BlockState appearanceState;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("modularmachinery.esNetworkTest")) return;
        server = event.getServer(); world = server.overworld();
        try {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.setChunkForced(x, z, true);
            for (int x = -1; x <= 10; x++) for (int z = 0; z <= 3; z++) world.setBlockAndUpdate(BASE.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            var pattern = new MachinePattern();
            gasPattern(pattern);
            pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_cell_drive"));
            pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:estorage_cell_drive"));
            pattern.addPart(new MachinePattern.Part(3, 0, 0, "modularmachinery:estorage_casing"));
            pattern.addPart(new MachinePattern.Part(0, 0, 1, "modularmachinery:estorage_me_channel"));
            previous = MachineReloadListener.getLoader().getMachines().put(ID, new DynamicMachine(ID.getPath(), "ES network fixture", pattern));
            world.setBlockAndUpdate(BASE, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH));
            world.setBlockAndUpdate(BASE.east(), EStorageRegistration.DRIVE.get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(2), EStorageRegistration.DRIVE.get().defaultBlockState());
            world.setBlockAndUpdate(CASING, EStorageRegistration.CASING.get().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL, EStorageRegistration.CHANNEL.get().defaultBlockState());
            power(true);
            controller = (TileEStorageController) world.getBlockEntity(BASE);
            itemDrive = (TileEStorageDrive) world.getBlockEntity(BASE.east());
            fluidDrive = (TileEStorageDrive) world.getBlockEntity(BASE.east(2));
            channel = (EStorageMEChannelNode) world.getBlockEntity(CHANNEL);
            require(itemDrive.insertMatrix(cell(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.C))
                    && fluidDrive.insertMatrix(cell(EStorageCellSpec.Kind.FLUID, EStorageCellSpec.Tier.A)), "matrix insertion");
            if (gasesEnabled()) {
                world.setBlockAndUpdate(BASE.west(), EStorageRegistration.DRIVE.get().defaultBlockState());
                gasDrive = (TileEStorageDrive) world.getBlockEntity(BASE.west());
                hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.run();
                gas = hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(0);
                require(gasDrive.insertMatrix(cell(EStorageCellSpec.Kind.GAS, EStorageCellSpec.Tier.C)), "gas matrix insertion");
            }
            controller.refreshStructure();
            require(controller.isAssembled(), "network fixture not formed");
            Modularmachinery.LOG.info("[MMC][es-network] Fixture formed; waiting for real ME power/channels");
        } catch (Throwable error) { finish(error); }
    }
    private static ItemStack cell(EStorageCellSpec.Kind kind, EStorageCellSpec.Tier tier) {
        var stack = new ItemStack(EStorageRegistration.CELLS.get(new EStorageCellSpec(kind, tier)).get());
        stack.getOrCreateTag().putString("Signature", "ES-network-test"); return stack;
    }
    private static void power(boolean enabled) { world.setBlockAndUpdate(POWER, enabled ? AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState() : Blocks.AIR.defaultBlockState()); }
    private static MEStorage network() { return channel.managedNode().getGrid().getStorageService().getInventory(); }
    private static boolean ready() { return ticks - since >= 80 && channel.isNetworkActive(); }
    private static void next(int target) {
        phase = target; since = ticks;
        Modularmachinery.LOG.info("[MMC][es-network] Entering phase {} at tick {}", phase, ticks);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        try {
            ticks++;
            if (ticks > 2400 || ticks - since > 500) throw new AssertionError("ES network timeout at phase " + phase);
            switch (phase) {
                case 0 -> {
                    if (!ready()) return;
                    channel.tick();
                    var node = channel.managedNode().getNode();
                    require(node.hasFlag(GridFlags.REQUIRE_CHANNEL) && node.hasFlag(GridFlags.DENSE_CAPACITY), "source channel flags absent");
                    double expectedDrain = gasesEnabled() ? 579.625 : 329.625;
                    require(channel.idleDrain() == expectedDrain && channel.managedNode().getGrid().getEnergyService().getIdlePowerUsage() >= expectedDrain, "64 AE/t plus matrix idle drain not charged to grid");
                    var inventory = network();
                    require(inventory.insert(ITEM, ITEMS, Actionable.MODULATE, SOURCE) == ITEMS, "ME item insert truncated long");
                    require(inventory.insert(FLUID, FLUIDS, Actionable.MODULATE, SOURCE) == FLUIDS, "ME fluid insert");
                    if (gasesEnabled()) {
                        require(inventory.insert(gas, GASES, Actionable.SIMULATE, SOURCE) == GASES && gasDrive.storageAccess().storedAmount() == 0, "ME gas simulation");
                        require(inventory.insert(gas, GASES, Actionable.MODULATE, SOURCE) == GASES, "ME gas long insertion");
                        for (var wrong : hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.otherForms())
                            require(inventory.insert(wrong, 1, Actionable.MODULATE, SOURCE) == 0, "ME gas matrix accepted other chemical forms");
                    }
                    checkContents();
                    CompoundTag before = itemDrive.matrixSnapshot().getTag().copy();
                    require(inventory.extract(ITEM, ITEMS, Actionable.SIMULATE, SOURCE) == ITEMS
                            && inventory.insert(ITEM, 77, Actionable.SIMULATE, SOURCE) == 77
                            && itemDrive.matrixSnapshot().getTag().equals(before), "network simulation changed matrix");
                    var views = new ArrayList<MEStorage>(); channel.mountInventories((view, priority) -> views.add(view));
                    obsoleteView = views.stream().filter(view -> view.getAvailableStacks().get(ITEM) == ITEMS).findFirst().orElseThrow();
                    controller.refreshStructure(); channel.tick();
                    require(obsoleteView.extract(ITEM, 1, Actionable.SIMULATE, SOURCE) == 1, "unchanged structure check unnecessarily remounted ES storage");
                    oldBackend = (EStorageCellInventory) itemDrive.storageAccess();
                    ItemStack removed = itemDrive.takeMatrix();
                    require(inventory.extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0 && !oldBackend.isOpen(), "removed matrix still accessible");
                    require(itemDrive.insertMatrix(cell(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.C)), "replacement insert");
                    controller.refreshStructure(); channel.tick();
                    require(network().insert(ITEM, 7, Actionable.MODULATE, SOURCE) == 7
                            && obsoleteView.extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0, "old mounted view followed replacement matrix");
                    require(network().extract(ITEM, 7, Actionable.MODULATE, SOURCE) == 7, "replacement conservation");
                    itemDrive.takeMatrix(); itemDrive.insertMatrix(removed); controller.refreshStructure(); channel.tick(); checkContents();
                    previousNode = channel.managedNode(); oldBackend = (EStorageCellInventory) itemDrive.storageAccess();
                    world.setBlockAndUpdate(CASING, Blocks.AIR.defaultBlockState()); controller.refreshStructure();
                    require(channel.managedNode() == null && !oldBackend.isOpen() && inventory.extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0, "structure loss did not destroy/unmount channel");
                    world.setBlockAndUpdate(CASING, EStorageRegistration.CASING.get().defaultBlockState()); controller.refreshStructure();
                    next(1);
                }
                case 1 -> {
                    if (!ready()) return;
                    require(channel.managedNode() != previousNode, "destroyed managed node reused"); checkContents();
                    CompoundTag disk = channel.saveWithoutMetadata();
                    require(disk.getCompound("MENode").contains("channel"), "ME identity was not saved");
                    previousNode = channel.managedNode(); oldBackend = (EStorageCellInventory) itemDrive.storageAccess();
                    world.setBlockAndUpdate(CHANNEL, Blocks.AIR.defaultBlockState());
                    world.setBlockAndUpdate(CHANNEL, EStorageRegistration.CHANNEL.get().defaultBlockState());
                    channel = (EStorageMEChannelNode) world.getBlockEntity(CHANNEL); channel.load(disk);
                    require(!channel.isNetworkActive() && channel.controller() == null && !oldBackend.isOpen(), "loaded node trusted persisted structure");
                    controller.refreshStructure(); next(2);
                }
                case 2 -> {
                    if (!ready()) return;
                    checkContents(); oldBackend = (EStorageCellInventory) itemDrive.storageAccess(); power(false); next(3);
                }
                case 3 -> {
                    if (ticks - since < 40 || channel.isNetworkActive()) return;
                    require(network().extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0
                            && network().getAvailableStacks().get(ITEM) == 0
                            && oldBackend.extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0 && !oldBackend.isOpen(), "unpowered ES advertised or modified inventory");
                    power(true); next(4);
                }
                case 4 -> {
                    if (!ready()) return;
                    checkContents();
                    require(obsoleteView.extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0, "obsolete mounted view revived");
                    for (int i = 1; i <= 8; i++) world.setBlockAndUpdate(POWER.east(i), AEBlocks.INTERFACE.block().defaultBlockState());
                    next(5);
                }
                case 5 -> {
                    if (ticks - since < 120 || channel.managedNode().getGrid().getPathingService().isNetworkBooting()) return;
                    int active = channel.isNetworkActive() ? 1 : 0;
                    for (int i = 1; i <= 8; i++) {
                        var host = (IInWorldGridNodeHost) world.getBlockEntity(POWER.east(i));
                        var endpoint = host.getGridNode(Direction.WEST);
                        require(endpoint != null && endpoint.getGrid() == channel.managedNode().getGrid(), "native channel fixture not connected");
                        if (endpoint.isActive()) active++;
                    }
                    // AE2 15's ad-hoc calculator returns zero channels for the entire network
                    // when nine devices exceed the default eight-channel limit.
                    require(active == 0 && !channel.managedNode().getNode().meetsChannelRequirements(), "ad-hoc network channel limit was bypassed: " + active);
                    require(network().getAvailableStacks().get(ITEM) == 0
                            && network().extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0, "channel-limited ES exposed inventory");
                    for (int i = 1; i <= 8; i++) world.setBlockAndUpdate(POWER.east(i), Blocks.AIR.defaultBlockState());
                    next(6);
                }
                case 6 -> {
                    if (!ready()) return;
                    checkContents(); oldBackend = (EStorageCellInventory) itemDrive.storageAccess();
                    world.setBlockAndUpdate(BASE.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    require(network().extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0 && !oldBackend.isOpen(), "redstone stop bypassed");
                    channel.tick(); require(channel.managedNode() == null, "stopped ES retained power drain node");
                    world.setBlockAndUpdate(BASE.above(), Blocks.AIR.defaultBlockState()); next(7);
                }
                case 7 -> {
                    if (!ready()) return;
                    checkContents(); oldBackend = (EStorageCellInventory) itemDrive.storageAccess();
                    previousNode = channel.managedNode(); channel.onChunkUnloaded();
                    require(channel.managedNode() == null && previousNode.getNode() == null
                            && oldBackend.extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0, "unloaded channel remained mounted");
                    controller.refreshStructure(); next(8);
                }
                case 8 -> {
                    if (!ready()) return;
                    checkContents();
                    gridCapacity = channel.managedNode().getGrid().getEnergyService().getMaxStoredPower();
                    var pattern = new MachinePattern();
                    gasPattern(pattern);
                    pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:estorage_cell_drive"));
                    pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:estorage_cell_drive"));
                    pattern.addPart(new MachinePattern.Part(3, 0, 0, "modularmachinery:estorage_casing"));
                    pattern.addPart(new MachinePattern.Part(0, 0, 1, "modularmachinery:estorage_me_channel"));
                    pattern.addPart(new MachinePattern.Part(4, 0, 0, "modularmachinery:estorage_energy_cell_l4"));
                    MachineReloadListener.getLoader().getMachines().put(ID, new DynamicMachine(ID.getPath(), "ES battery fixture", pattern));
                    world.setBlockAndUpdate(BASE.east(4), EStorageRegistration.ENERGY_CELLS.get(4).get().defaultBlockState());
                    battery = (TileEStorageEnergyCell) world.getBlockEntity(BASE.east(4));
                    previousNode = channel.managedNode(); controller.refreshStructure(); channel.tick(); next(9);
                }
                case 9 -> {
                    if (!ready()) return;
                    require(channel.managedNode() != previousNode && previousNode.getNode() == null, "battery addition did not replace capacity registration");
                    require(channel.managedNode().getNode().getService(IAEPowerStorage.class) == channel
                            && channel.isAEPublicPowerStorage() && channel.getAEMaxPower() == 10_000_000, "public AE power service absent");
                    require(channel.managedNode().getGrid().getEnergyService().getMaxStoredPower() == gridCapacity + 10_000_000, "grid capacity did not include battery exactly once");
                    // Remove the unlimited source before testing real charge, drain and recovery.
                    power(false); next(10);
                }
                case 10 -> {
                    if (ticks - since < 80) return;
                    var energy = channel.managedNode().getGrid().getEnergyService();
                    energy.extractAEPower(20_000_000, Actionable.MODULATE, PowerMultiplier.ONE);
                    battery.extract(Double.MAX_VALUE, false); channel.tick();
                    require(energy.injectPower(100_000, Actionable.SIMULATE) == 0 && battery.stored() == 0, "grid charge simulation changed battery");
                    require(energy.injectPower(100_000, Actionable.MODULATE) == 0 && battery.stored() > 90_000, "empty ES cannot recharge through ME grid");
                    batteryBefore = battery.stored(); next(11);
                }
                case 11 -> {
                    if (!ready()) return;
                    checkContents();
                    double used = batteryBefore - battery.stored();
                    require(used > 0 && used < 80 * channel.idleDrain() * 1.1, "internal battery not supplying grid or idle drain charged twice: " + used);
                    double before = battery.stored();
                    require(channel.extractAEPower(12.5, Actionable.SIMULATE, PowerMultiplier.ONE) == 12.5 && battery.stored() == before, "public extraction simulation mutated energy");
                    require(channel.extractAEPower(12.5, Actionable.MODULATE, PowerMultiplier.ONE) == 12.5 && battery.stored() == before - 12.5, "public extraction units wrong");
                    double savedMultiplier = PowerMultiplier.CONFIG.multiplier;
                    try {
                        PowerMultiplier.CONFIG.multiplier = 2;
                        before = battery.stored();
                        require(channel.extractAEPower(10, Actionable.SIMULATE, PowerMultiplier.CONFIG) == 10 && battery.stored() == before,
                                "configured power multiplier simulation changed battery");
                        require(channel.extractAEPower(10, Actionable.MODULATE, PowerMultiplier.CONFIG) == 10 && battery.stored() == before - 20,
                                "configured power multiplier not applied exactly once");
                    } finally { PowerMultiplier.CONFIG.multiplier = savedMultiplier; }
                    var energy = channel.managedNode().getGrid().getEnergyService();
                    energy.extractAEPower(20_000_000, Actionable.MODULATE, PowerMultiplier.ONE); next(12);
                }
                case 12 -> {
                    if (ticks - since < 80 || channel.isNetworkActive()) return;
                    require(battery.stored() == 0 && network().extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 0, "exhausted battery did not stop ME storage");
                    channel.tick();
                    require(channel.managedNode().getGrid().getEnergyService().injectPower(20_000_000, Actionable.MODULATE) > 0
                            && battery.stored() == battery.capacity(), "grid full-charge remainder wrong");
                    // Force AE to remove a full requester, then restore free space. The pending
                    // event must be delivered outside the injection iterator and re-add it.
                    channel.extractAEPower(1000, Actionable.MODULATE, PowerMultiplier.ONE); channel.tick();
                    require(channel.managedNode().getGrid().getEnergyService().injectPower(1000, Actionable.MODULATE) == 0
                            && battery.stored() == battery.capacity(), "full battery requester did not recover after discharge");
                    next(13);
                }
                case 13 -> {
                    if (!ready()) return;
                    checkContents();
                    double before = battery.stored(); var disk = battery.saveWithoutMetadata();
                    battery.onChunkUnloaded(); controller.refreshStructure(); channel.tick();
                    battery.load(disk); controller.refreshStructure(); channel.tick();
                    require(battery.stored() == before && channel.getAEMaxPower() == 10_000_000, "battery reload lost energy or capacity");
                    next(14);
                }
                case 14 -> {
                    if (!ready()) return;
                    checkContents();
                    // Keep a native cable node alive so removal can be measured on the same grid.
                    power(true); next(15);
                }
                case 15 -> {
                    if (!ready()) return;
                    var grid = channel.managedNode().getGrid();
                    double capacity = grid.getEnergyService().getMaxStoredPower();
                    double before = battery.stored(); var drop = battery.takeDrop();
                    world.setBlockAndUpdate(BASE.east(4), Blocks.AIR.defaultBlockState());
                    controller.refreshStructure(); channel.tick();
                    require(channel.managedNode() == null && !controller.isOperational(), "missing battery structure stayed operational");
                    require(channel.injectAEPower(50, Actionable.MODULATE) == 50
                            && channel.extractAEPower(50, Actionable.MODULATE, PowerMultiplier.ONE) == 0, "disconnected power service still transferred energy");
                    require(grid.getEnergyService().getMaxStoredPower() <= capacity - 10_000_000, "removed battery left phantom grid capacity");
                    require(drop.getTag().getDouble("energyStored") == before && battery.stored() == 0, "battery removal duplicated/lost energy");
                    world.setBlockAndUpdate(BASE.east(4), EStorageRegistration.ENERGY_CELLS.get(4).get().defaultBlockState());
                    battery = (TileEStorageEnergyCell) world.getBlockEntity(BASE.east(4)); battery.loadEnergy(drop.getTag());
                    controller.refreshStructure(); channel.tick();
                    require(controller.storedEnergy() == before, "restored battery energy not conserved");
                    next(16);
                }
                case 16 -> {
                    if (!ready()) return;
                    checkContents();
                    var stack = itemDrive.takeMatrix();
                    var item = (appeng.api.storage.cells.ICellWorkbenchItem) stack.getItem();
                    item.getConfigInventory(stack).addFilter(ITEM);
                    require(itemDrive.insertMatrix(stack), "partitioned matrix reinsert");
                    controller.refreshStructure(); channel.tick(); next(17);
                }
                case 17 -> {
                    if (!ready()) return;
                    checkContents();
                    var dirt = AEItemKey.of(Items.DIRT);
                    require(network().insert(dirt, 5, Actionable.SIMULATE, SOURCE) == 0
                            && network().insert(dirt, 5, Actionable.MODULATE, SOURCE) == 0, "ME mounting bypassed partition whitelist");
                    require(network().insert(ITEM, 5, Actionable.MODULATE, SOURCE) == 5
                            && network().extract(ITEM, 5, Actionable.MODULATE, SOURCE) == 5, "whitelisted ME insert/extract failed");
                    var stack = itemDrive.takeMatrix();
                    var item = (appeng.api.storage.cells.ICellWorkbenchItem) stack.getItem();
                    require(item.getUpgrades(stack).insertItem(0, appeng.core.definitions.AEItems.INVERTER_CARD.stack(), false).isEmpty(), "network matrix inverter insertion");
                    require(itemDrive.insertMatrix(stack), "inverted matrix reinsert");
                    controller.refreshStructure(); channel.tick(); next(18);
                }
                case 18 -> {
                    if (!ready()) return;
                    checkContents();
                    var dirt = AEItemKey.of(Items.DIRT);
                    require(network().insert(ITEM, 1, Actionable.MODULATE, SOURCE) == 0
                            && network().insert(dirt, 5, Actionable.MODULATE, SOURCE) == 5
                            && network().extract(dirt, 5, Actionable.MODULATE, SOURCE) == 5, "ME mounting bypassed inverted partition");
                    require(network().extract(ITEM, 5, Actionable.MODULATE, SOURCE) == 5, "changed partition blocked old inventory extraction");
                    var stack = itemDrive.takeMatrix();
                    var item = (appeng.api.storage.cells.ICellWorkbenchItem) stack.getItem();
                    item.getUpgrades(stack).extractItem(0, 1, false); item.getConfigInventory(stack).clear();
                    require(itemDrive.insertMatrix(stack), "restored matrix reinsert");
                    controller.refreshStructure(); channel.tick(); next(19);
                }
                case 19 -> {
                    if (!ready()) return;
                    require(network().insert(ITEM, 5, Actionable.MODULATE, SOURCE) == 5, "cleared filter did not restore ME insertion");
                    checkContents();
                    var slot = itemDrive.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                            .orElseThrow(AssertionError::new);
                    var retained = itemDrive.storageAccess();
                    var before = slot.extractItem(0, 1, true);
                    require(!before.isEmpty() && itemDrive.hasMatrix(), "automated simulation removed mounted matrix");
                    var removed = slot.extractItem(0, 1, false);
                    require(net.minecraft.world.item.ItemStack.matches(before, removed) && !retained.isOpen(), "automated extraction lost NBT or retained backend access");
                    require(network().extract(ITEM, ITEMS, Actionable.SIMULATE, SOURCE) == 0, "network retained removed automated matrix");
                    require(slot.insertItem(0, removed, true).isEmpty() && !itemDrive.hasMatrix(), "automated simulated return mounted matrix");
                    require(slot.insertItem(0, removed, false).isEmpty() && !retained.isOpen(), "automated return resurrected old backend");
                    controller.refreshStructure(); channel.tick(); next(20);
                }
                case 20 -> {
                    if (!ready()) return;
                    checkContents();
                    var views = new ArrayList<MEStorage>(); channel.mountInventories((view, priority) -> views.add(view));
                    obsoleteView = views.stream().filter(view -> view.getAvailableStacks().get(ITEM) == ITEMS).findFirst().orElseThrow();
                    previousNode = channel.managedNode(); appearanceRevision = itemDrive.networkRevision(); appearanceState = itemDrive.getBlockState();
                    require(network().insert(ITEM, 1, Actionable.MODULATE, SOURCE) == 1
                            && network().extract(ITEM, 1, Actionable.MODULATE, SOURCE) == 1, "live write LED fixture failed");
                    require(itemDrive.appearance().status() == EStorageDriveAppearance.Status.RUN, "network write did not activate LED");
                    next(21);
                }
                case 21 -> {
                    if (ticks - since < 50) return;
                    checkContents();
                    require(itemDrive.appearance().status() == EStorageDriveAppearance.Status.IDLE, "live write LED never expired");
                    require(itemDrive.getBlockState() == appearanceState && itemDrive.networkRevision() == appearanceRevision
                            && channel.managedNode() == previousNode && oldViewCanExtract(), "cosmetic tick invalidated live ME mounting");
                    if (gasesEnabled()) {
                        var previous = gasDrive.storageAccess(); var slot = gasDrive.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
                        var removed = slot.extractItem(0, 1, false);
                        require(!previous.isOpen() && network().extract(gas, 1, Actionable.MODULATE, SOURCE) == 0, "removed gas matrix still mounted");
                        require(slot.insertItem(0, removed, false).isEmpty(), "gas matrix automatic return");
                        controller.refreshStructure(); channel.tick(); next(22);
                    } else finish(null);
                }
                case 22 -> {
                    if (!ready()) return;
                    checkContents();
                    var data = (EStorageCellInventory) gasDrive.storageAccess(); var stack = gasDrive.takeMatrix();
                    require(!data.isOpen(), "gas filter remount did not revoke original backend");
                    ((appeng.api.storage.cells.ICellWorkbenchItem) stack.getItem()).getConfigInventory(stack).addFilter(gas);
                    require(gasDrive.insertMatrix(stack), "partitioned gas matrix return"); controller.refreshStructure(); channel.tick(); next(23);
                }
                case 23 -> {
                    if (!ready()) return;
                    checkContents(); var other = hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(1);
                    require(network().insert(other, 1, Actionable.MODULATE, SOURCE) == 0
                            && network().extract(gas, GASES, Actionable.MODULATE, SOURCE) == GASES
                            && network().insert(gas, GASES, Actionable.MODULATE, SOURCE) == GASES, "mounted gas partition/long extraction");
                    checkContents();
                    Modularmachinery.LOG.info("[MMC][es-gas-network] PASS: 9 billion gas units through real ME network, simulations, non-gas refusal, stock survives all network power/channel/structure phases, automation revocation/remount and mounted partition");
                    finish(null);
                }
            }
        } catch (Throwable error) { finish(error); }
    }
    private static void checkContents() {
        channel.tick(); var inventory = network();
        require(inventory.getAvailableStacks().get(ITEM) == ITEMS && inventory.getAvailableStacks().get(FLUID) == FLUIDS, "network stock lost/duplicated");
        require(inventory.extract(ITEM, ITEMS, Actionable.SIMULATE, SOURCE) == ITEMS
                && inventory.extract(FLUID, FLUIDS, Actionable.SIMULATE, SOURCE) == FLUIDS, "resumed network cannot extract stored stock");
        if (gasesEnabled()) require(inventory.getAvailableStacks().get(gas) == GASES
                && inventory.extract(gas, GASES, Actionable.SIMULATE, SOURCE) == GASES, "network gas lost/duplicated/truncated");
    }
    private static boolean oldViewCanExtract() { return obsoleteView.extract(ITEM, ITEMS, Actionable.SIMULATE, SOURCE) == ITEMS; }
    private static void finish(Throwable error) {
        if (error == null) Modularmachinery.LOG.info("[MMC][es-network] OVERALL: PASS: real ME item/fluid long transfers, source idle drain, simulations, matrix swaps and obsolete leases, structure rebuild, node NBT reload, power loss/recovery, 8-channel saturation, redstone stop and channel unload/rebind; internal battery registration, grid charge/discharge, empty/full recovery, single idle drain, battery NBT and removal capacity/energy conservation; mounted partition whitelist/inverter/clear, old content extraction after filter changes; physical matrix automation with immediate revocation and remount; stock conserved");
        else Modularmachinery.LOG.error("[MMC][es-network] OVERALL: FAIL, phase=" + phase, error);
        if (world != null) {
            if (previous == null) MachineReloadListener.getLoader().getMachines().remove(ID);
            else MachineReloadListener.getLoader().getMachines().put(ID, previous);
        }
        MinecraftServer stopping = server; server = null; if (stopping != null) stopping.halt(false);
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
