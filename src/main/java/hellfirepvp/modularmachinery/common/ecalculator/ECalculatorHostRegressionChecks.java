/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.ecalculator;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.estorage.EStorageRegistration;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Real world, real useItemOn and Forge capability checks. Runs only in disposable self-test worlds. */
public final class ECalculatorHostRegressionChecks {
    private ECalculatorHostRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos origin) {
        var machines = MachineReloadListener.getLoader().getMachines();
        var saved = new LinkedHashMap<>(machines);
        var touched = new LinkedHashSet<BlockPos>();
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "MmcEcFixture"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        try {
            require(ECalculatorSpecs.Memory.A.bytes() == 65_536_000L && ECalculatorSpecs.Memory.B.bytes() == 1_048_576_000L
                    && ECalculatorSpecs.Memory.C.bytes() == 16_777_216_000L, "source memory capacity/long overflow");
            for (int tier : new int[]{4, 6, 9}) for (var facing : Direction.Plane.HORIZONTAL) {
                var pattern = new MachinePattern();
                var ids = new ArrayList<>(ECalculatorRegistration.PARTS.keySet());
                for (int i = 0; i < ids.size(); i++) pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, "modularmachinery:" + ids.get(i)));
                var machine = new DynamicMachine(ECalculatorRegistration.machineName(tier), "EC fixture", pattern);
                var id = new ResourceLocation("modularmachinery", machine.getRegistryName());
                machines.put(id, machine);
                var positions = new ArrayList<BlockPos>();
                place(world, origin, ECalculatorRegistration.CONTROLLERS.get(tier).get().defaultBlockState().setValue(BlockECalculatorController.FACING, facing), touched);
                for (int i = 0; i < ids.size(); i++) {
                    var pos = StructureMatcher.toWorldOffset(origin, facing, i + 1, 0, 0); positions.add(pos);
                    place(world, pos, ECalculatorRegistration.PARTS.get(ids.get(i)).get().defaultBlockState().setValue(BlockECalculatorPart.FACING, facing), touched);
                }
                var host = (TileECalculatorController)world.getBlockEntity(origin);
                var drive = (TileECalculatorDrive)world.getBlockEntity(positions.get(ids.indexOf("ecalculator_cell_drive")));
                host.refreshStructure();
                require(host.isOperational() && host.parts().size() == ids.size(), "EC formation/facing/part registration");
                appearance(world, host, tier, facing);
                require(host.normalSlots() == 8 && host.hyperSlots() == 14 && host.parallelism() == 18_688,
                        "source normal/hyper/parallel sums, or invented core-tier restriction");
                require(host.totalBytes() == 0, "empty drive supplied bytes");
                for (var memory : ECalculatorRegistration.MEMORIES.entrySet()) {
                    var input = new ItemStack(memory.getValue().get());
                    input.getOrCreateTag().putString("Signature", "opaque-memory");
                    input.getOrCreateTag().put("UnknownFutureData", payload());
                    var expected = input.copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, input);
                    click(world, player, drive.getBlockPos());
                    require(player.getMainHandItem().isEmpty() && drive.hasMemory(), "real sneak insert with occupied offhand");
                    host.refreshStructure();
                    boolean supported = memory.getKey().supportedBy(tier);
                    require(drive.isTierSupported() == supported && host.totalBytes() == (supported ? memory.getKey().bytes() : 0), "memory host tier");
                    require(!drive.insertMemory(input) && !drive.insertMemory(new ItemStack(Items.STONE)), "occupied drive overwritten");
                    var disk = drive.saveWithoutMetadata(); var hostDisk = host.saveWithoutMetadata();
                    require(!drive.getUpdateTag().contains("Memory"), "private memory NBT leaked");
                    host.load(hostDisk); drive.load(disk);
                    require(!host.isAssembled() && drive.owner() == null && drive.suppliedBytes() == 0, "trusted persisted owner/formation");
                    host.tick();
                    require(host.isOperational() && host.totalBytes() == (supported ? memory.getKey().bytes() : 0), "save/rebind capacity");
                    click(world, player, drive.getBlockPos());
                    require(!drive.hasMemory() && ItemStack.isSameItemSameTags(expected, player.getMainHandItem()), "memory removal changed opaque NBT");
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
                if (tier == 9 && facing == Direction.NORTH) lifecycle(world, host, drive, machine, machines, positions, player);
                // Unbinding leaves physical contents intact and drops exactly one memory.
                var input = new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get()); input.getOrCreateTag().put("UnknownFutureData", payload());
                require(drive.insertMemory(input), "final memory insert");
                world.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
                require(drive.hasMemory() && drive.owner() == null && drive.suppliedBytes() == 0, "host break lost memory or left active owner");
                var drops = removeAndCollect(world, drive.getBlockPos(), input.getItem());
                require(world.getBlockState(drive.getBlockPos()).isAir() && world.getBlockEntity(drive.getBlockPos()) == null,
                        "removing occupied drive resurrected empty block/entity");
                require(drops.size() == 1 && drops.get(0).getItem().getCount() == 1 && ItemStack.isSameItemSameTags(input, drops.get(0).getItem()), "drive break duplicated/deleted memory NBT");
                drops.forEach(ItemEntity::discard);
                world.setBlockAndUpdate(drive.getBlockPos(), ECalculatorRegistration.DRIVE.get().defaultBlockState());
                var replacementDrive = (TileECalculatorDrive)world.getBlockEntity(drive.getBlockPos());
                require(replacementDrive.insertMemory(input), "replacement fixture insert");
                drops = removeAndCollect(world, drive.getBlockPos(), input.getItem(), Blocks.STONE.defaultBlockState());
                require(world.getBlockState(drive.getBlockPos()).is(Blocks.STONE) && world.getBlockEntity(drive.getBlockPos()) == null
                        && drops.size() == 1 && ItemStack.isSameItemSameTags(input, drops.get(0).getItem()), "occupied drive overwrote replacement/lost memory");
                drops.forEach(ItemEntity::discard);
                for (var pos : positions) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            exclusive(world, origin, machines, touched);
            transmitter(world, origin.above(8), machines, touched);
            Modularmachinery.LOG.info("[MMC][ec-drive-removal] PASS: occupied drives leave air/no BE, stone replacement survives, one intact memory drop, all three tiers/four facings");
            Modularmachinery.LOG.info("[MMC][ec-host] PASS: source long capacities, 3 tiers/4 facings, normal/hyper/shared parallel, real sneak insertion, physical automation/simulation, NBT/opaque data, exclusive ownership, mixed-tier vertical exclusion, unload/rebind, definition loss, transmitter alignment, single drops; CPU execution requires separate ec-network fixture, AE2 present={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
            Modularmachinery.LOG.info("[MMC][ec-appearance] PASS: bounded metadata, 3 tiers/4 facings, source bound/idle lights, unchanged world palette, private data excluded, late snapshot, fixed 17 and dynamic 5-cell hiding, transmitter all/up/down/none, alignment and unsupported memory");
        } finally {
            for (var pos : touched) if (world.hasChunkAt(pos)) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            // Direct source-style core drops are cleaned only inside this disposable fixture region.
            world.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(40)).forEach(ItemEntity::discard);
            machines.clear(); machines.putAll(saved);
        }
    }
    private static void lifecycle(ServerLevel world, TileECalculatorController host, TileECalculatorDrive drive, DynamicMachine machine,
                                  Map<ResourceLocation, DynamicMachine> machines, List<BlockPos> positions, FakePlayer player) {
        ItemStack input = new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get()); input.getOrCreateTag().put("Opaque", payload());
        IItemHandler cap = drive.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(() -> new AssertionError("missing EC slot"));
        for (var side : Direction.values()) require(drive.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(), "missing automation side");
        require(drive.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(), "missing unsided automation");
        require(cap.getSlotLimit(0) == 1 && cap.getSlotLimit(1) == 0, "physical memory slot limit");
        require(cap.insertItem(0, input, true).isEmpty() && !drive.hasMemory(), "simulate insert mutated");
        require(!cap.isItemValid(0, new ItemStack(EStorageRegistration.CELLS.values().iterator().next().get())), "ES matrix accepted as EC memory");
        require(cap.insertItem(0, input, false).isEmpty() && drive.hasMemory(), "automation insert");
        var copy = cap.getStackInSlot(0); copy.getOrCreateTag().putBoolean("Tampered", true);
        require(!drive.memorySnapshot().getTag().contains("Tampered"), "slot view mutates original");
        require(ItemStack.isSameItemSameTags(cap.extractItem(0, 1, true), input) && drive.hasMemory(), "simulate extraction mutated");
        drive.onChunkUnloaded();
        require(cap.extractItem(0, 1, false).isEmpty() && cap.insertItem(0, input, false) == input && drive.hasMemory(), "retained unloaded capability mutated memory");
        require(!host.isOperational(), "unloaded part left live host");
        host.refreshStructure(); require(!host.isAssembled(), "unloaded part rebound");
        drive.onLoad(); host.refreshStructure();
        require(host.isOperational() && host.totalBytes() == inputMemoryBytes(), "part reload didn't restore memory");
        require(cap.extractItem(0, 1, false).isEmpty(), "old capability revived");
        var fresh = drive.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new AssertionError("missing revived EC slot"));
        require(ItemStack.isSameItemSameTags(fresh.extractItem(0, 1, false), input), "fresh capability cannot extract");
        // Actual creative gesture consumes the physical memory too.
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE); player.setItemInHand(InteractionHand.MAIN_HAND, input.copy());
        click(world, player, drive.getBlockPos()); require(player.getMainHandItem().isEmpty(), "creative duplicated memory");
        click(world, player, drive.getBlockPos()); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        var id = new ResourceLocation("modularmachinery", machine.getRegistryName());
        machines.remove(id); host.refreshStructure(); require(!host.isAssembled() && drive.owner() == null, "missing definition retained ownership");
        machines.put(id, machine); host.refreshStructure();
        var corePos = positions.stream().filter(p -> world.getBlockEntity(p) instanceof TileECalculatorPart part && part.specification().kind() == ECalculatorSpecs.Kind.THREAD).findFirst().orElseThrow();
        var core = (TileECalculatorPart)world.getBlockEntity(corePos); core.restoreBackend(payload());
        require(core.getUpdateTag().getAllKeys().equals(Set.of("Appearance")), "private future CPU data leaked");
        var coreDisk = core.saveWithoutMetadata(); core.load(coreDisk);
        require(core.backendSnapshot().equals(payload()) && core.owner() == null, "opaque CPU persistence or owner restored"); host.refreshStructure();
        Item coreItem = core.getBlockState().getBlock().asItem(); var coreState = core.getBlockState();
        var dropped = removeAndCollect(world, corePos, coreItem);
        require(dropped.size() == 1 && dropped.get(0).getItem().getTag().getCompound("EcBackendData").equals(payload()), "core drop lost future payload");
        var stack = dropped.get(0).getItem().copy(); dropped.forEach(ItemEntity::discard);
        world.setBlockAndUpdate(corePos, coreState); ((BlockECalculatorPart)coreState.getBlock()).setPlacedBy(world, corePos, coreState, player, stack);
        require(((TileECalculatorPart)world.getBlockEntity(corePos)).backendSnapshot().equals(payload()), "core placement lost payload");
        host.refreshStructure();
        host.onChunkUnloaded(); require(!host.isOperational() && drive.owner() == null, "host unload retained ownership"); host.onLoad(); host.refreshStructure();
    }
    private static long inputMemoryBytes() { return 16_777_216_000L; }
    private static void appearance(ServerLevel world, TileECalculatorController host, int tier, Direction facing) {
        for (int encoded = -1; encoded <= 64; encoded++) {
            var decoded = ECalculatorAppearance.decode(encoded);
            require(ECalculatorAppearance.decode(decoded.encode()).equals(decoded), "appearance invalid ordinal/roundtrip");
        }
        for (var part : host.parts()) {
            var state = part.getBlockState();
            var data = part.appearance();
            require(data.status() == ECalculatorAppearance.Status.ON, "bound part not online");
            require(!state.hasProperty(ECalculatorAppearance.STATUS) || state.getValue(ECalculatorAppearance.STATUS) == ECalculatorAppearance.Status.OFF, "render status entered palette");
            require(!state.hasProperty(ECalculatorAppearance.ONLINE) || !state.getValue(ECalculatorAppearance.ONLINE), "render online entered palette");
            require(!state.hasProperty(ECalculatorAppearance.FORMED) || !state.getValue(ECalculatorAppearance.FORMED), "render formed entered palette");
            require(ECalculatorAppearance.decode(part.getUpdateTag().getInt("Appearance")).equals(data)
                    && part.getUpdateTag().getAllKeys().equals(Set.of("Appearance")), "late snapshot or private payload leak");
            int expected = switch (part.specification().kind()) { case THREAD, HYPER_THREAD, TAIL -> 10; case PARALLEL -> 12; case DRIVE, TRANSMITTER -> 4; case CHANNEL -> 0; };
            require(state.getLightEmission(world, part.getBlockPos()) == expected, "source idle light");
        }
        var hidden = host.modelHiddenPositions();
        require(hidden.size() == 17 && !hidden.contains(host.getBlockPos()), "source fixed hidden cells/controller hidden");
        require(hidden.contains(StructureMatcher.toWorldOffset(host.getBlockPos(), facing, -1, -1, 1)), "hidden cells rotation");
        var machine = MachineReloadListener.getLoader().findByName(ECalculatorRegistration.machineName(tier));
        var workers = new DynamicPattern("workers"); workers.setMinSize(1); workers.setMaxSize(3);
        workers.addFace(Direction.SOUTH); workers.setStructureSizeOffsetStart(new BlockPos(0, 0, 2)); workers.setStructureSizeOffset(new BlockPos(0, 0, 2));
        machine.getDynamicPatterns().put("workers", workers);
        try {
            host.recordFormedStructure(machine, machine.getPattern(), Map.of("workers", 2), facing);
            hidden = host.modelHiddenPositions();
            require(hidden.size() == 22 && hidden.contains(StructureMatcher.toWorldOffset(host.getBlockPos(), facing, 0, 1, 6))
                    && !hidden.contains(StructureMatcher.toWorldOffset(host.getBlockPos(), facing, 0, 0, 6)), "dynamic cap offset/rotation or tail anchor hidden");
        } finally { machine.getDynamicPatterns().remove("workers"); host.recordFormedStructure(machine, machine.getPattern(), Map.of(), facing); }
    }
    private static void exclusive(ServerLevel world, BlockPos origin, Map<ResourceLocation, DynamicMachine> machines, Set<BlockPos> touched) {
        var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:ecalculator_cell_drive"));
        var name = ECalculatorRegistration.machineName(4); var id = new ResourceLocation("modularmachinery", name);
        machines.put(id, new DynamicMachine(name, "shared EC", pattern));
        var drive = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0); var second = drive.offset(drive.subtract(origin));
        place(world, origin, ECalculatorRegistration.CONTROLLERS.get(4).get().defaultBlockState().setValue(BlockECalculatorController.FACING, Direction.NORTH), touched);
        place(world, drive, ECalculatorRegistration.DRIVE.get().defaultBlockState(), touched);
        place(world, second, ECalculatorRegistration.CONTROLLERS.get(4).get().defaultBlockState().setValue(BlockECalculatorController.FACING, Direction.SOUTH), touched);
        var a = (TileECalculatorController)world.getBlockEntity(origin); var b = (TileECalculatorController)world.getBlockEntity(second);
        a.refreshStructure(); b.refreshStructure(); require(a.isOperational() && !b.isAssembled(), "two hosts claimed same EC part");
        world.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState()); b.refreshStructure(); require(b.isOperational(), "removed EC host retained owner");
        place(world, second.above(2), ECalculatorRegistration.CONTROLLERS.get(9).get().defaultBlockState(), touched);
        b.refreshStructure(); require(!b.isAssembled(), "different-tier vertical sharing bypassed source class rule");
        for (var pos : touched) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
    private static void transmitter(ServerLevel world, BlockPos origin, Map<ResourceLocation, DynamicMachine> machines, Set<BlockPos> touched) {
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:ecalculator_transmitter_bus"));
        pattern.addPart(new MachinePattern.Part(1, 1, 0, "modularmachinery:ecalculator_cell_drive"));
        pattern.addPart(new MachinePattern.Part(1, -1, 0, "modularmachinery:ecalculator_cell_drive"));
        var name = ECalculatorRegistration.machineName(4); machines.put(new ResourceLocation("modularmachinery", name), new DynamicMachine(name, "EC transmitter", pattern));
        place(world, origin, ECalculatorRegistration.CONTROLLERS.get(4).get().defaultBlockState(), touched);
        var busPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        place(world, busPos, ECalculatorRegistration.TRANSMITTER.get().defaultBlockState(), touched);
        for (var side : List.of(Direction.UP, Direction.DOWN)) place(world, busPos.relative(side), ECalculatorRegistration.DRIVE.get().defaultBlockState(), touched);
        var host = (TileECalculatorController)world.getBlockEntity(origin); host.refreshStructure();
        var bus = (TileECalculatorPart)world.getBlockEntity(busPos);
        for (var side : List.of(Direction.UP, Direction.DOWN)) ((TileECalculatorDrive)world.getBlockEntity(busPos.relative(side))).insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.A).get()));
        host.refreshStructure(); require(bus.linkedDrive(Direction.UP) && bus.linkedDrive(Direction.DOWN) && !bus.linkedDrive(Direction.NORTH), "source transmitter adjacency");
        require(bus.appearance().link() == ECalculatorAppearance.Link.ALL && bus.appearance().tier() == ECalculatorAppearance.Tier.L4
                && bus.getBlockState().getLightEmission(world, busPos) == 12, "source all-link visual/light");
        require(((TileECalculatorDrive)world.getBlockEntity(busPos.above())).appearance().link() == ECalculatorAppearance.Link.DOWN
                && ((TileECalculatorDrive)world.getBlockEntity(busPos.below())).appearance().link() == ECalculatorAppearance.Link.UP, "drive connection direction");
        var upper = busPos.above(); world.setBlockAndUpdate(upper, world.getBlockState(upper).setValue(BlockECalculatorPart.FACING, Direction.SOUTH)); host.refreshStructure();
        require(!bus.linkedDrive(Direction.UP) && bus.linkedDrive(Direction.DOWN), "misaligned transmitter linked");
        require(bus.appearance().link() == ECalculatorAppearance.Link.DOWN && bus.getBlockState().getLightEmission(world, busPos) == 8, "misaligned visual link");
        var lower = (TileECalculatorDrive)world.getBlockEntity(busPos.below()); lower.takeMemory(); lower.insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.B).get())); host.refreshStructure();
        require(!bus.linkedDrive(Direction.DOWN), "unsupported memory linked transmitter");
        require(bus.appearance().link() == ECalculatorAppearance.Link.NONE && lower.appearance().status() == ECalculatorAppearance.Status.ON
                && lower.getBlockState().getLightEmission(world, lower.getBlockPos()) == 12, "source unsupported memory stays bound/on but not linked");
        host.setFormed(false);
        require(bus.appearance().equals(ECalculatorAppearance.OFF) && lower.appearance().equals(ECalculatorAppearance.OFF)
                && lower.hasMemory() && lower.getBlockState().getLightEmission(world, lower.getBlockPos()) == 8, "unbinding appearance/inventory/light");
        // A drive may be touched by two transmitters; preserve source last-bound direction.
        world.setBlockAndUpdate(upper, world.getBlockState(upper).setValue(BlockECalculatorPart.FACING, Direction.NORTH));
        pattern.addPart(new MachinePattern.Part(1, 2, 0, "modularmachinery:ecalculator_transmitter_bus"));
        var secondBus = busPos.above(2);
        place(world, secondBus, ECalculatorRegistration.TRANSMITTER.get().defaultBlockState(), touched);
        host.refreshStructure();
        require(((TileECalculatorDrive)world.getBlockEntity(upper)).appearance().link() == ECalculatorAppearance.Link.UP,
                "sandwiched drive did not use last-bound transmitter");
        var backendBefore = ((TileECalculatorDrive)world.getBlockEntity(upper)).memorySnapshot();
        host.refreshPartAppearances(); host.refreshPartAppearances();
        require(ItemStack.isSameItemSameTags(backendBefore, ((TileECalculatorDrive)world.getBlockEntity(upper)).memorySnapshot()), "cosmetic refresh altered memory");
    }
    private static CompoundTag payload() { var tag = new CompoundTag(); tag.putString("UnknownVersion", "preserve-without-AE"); tag.putLong("LargeNumber", 16_777_216_000L); return tag; }
    private static void place(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, Set<BlockPos> touched) {
        require(world.getBlockState(pos).isAir(), "EC fixture needs empty space at " + pos); touched.add(pos); world.setBlockAndUpdate(pos, state);
    }
    private static void click(ServerLevel world, FakePlayer player, BlockPos pos) {
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        require(player.gameMode.useItemOn(player, world, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(), "EC useItemOn not consumed");
    }
    private static List<ItemEntity> removeAndCollect(ServerLevel world, BlockPos pos, Item item) {
        return removeAndCollect(world, pos, item, Blocks.AIR.defaultBlockState());
    }
    private static List<ItemEntity> removeAndCollect(ServerLevel world, BlockPos pos, Item item, net.minecraft.world.level.block.state.BlockState replacement) {
        var bounds = new AABB(pos).inflate(2); var previous = new HashSet<>(world.getEntitiesOfClass(ItemEntity.class, bounds));
        world.setBlockAndUpdate(pos, replacement);
        return world.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !previous.contains(entity) && entity.getItem().getItem() == item);
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
