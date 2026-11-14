/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.efabricator;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;

/** Actual block placement, ownership, Forge inventory lifetime and survival loot in disposable worlds. */
public final class EFabricatorRegressionChecks {
    private EFabricatorRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos pos) {
        queues(); outputs(); EFabricatorCoolingChecks.run();
        var definitions = MachineReloadListener.getLoader().getMachines();
        var saved = new HashMap<ResourceLocation, DynamicMachine>();
        var touched = new HashSet<BlockPos>();
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EfBaseCheck"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        try {
            for (var tier : EFabricatorSpecs.Tier.values()) {
                var id = new ResourceLocation("modularmachinery", EFabricatorRegistration.machineName(tier)); saved.put(id, definitions.get(id));
                var pattern = new MachinePattern();
                var ids = List.of("efabricator_worker", "efabricator_pattern_bus", "efabricator_me_channel",
                        "efabricator_parallel_proc_l4", "efabricator_parallel_proc_l6", "efabricator_parallel_proc_l9");
                for (int i = 0; i < ids.size(); i++) pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, "modularmachinery:" + ids.get(i)));
                var machine = new DynamicMachine(id.getPath(), "EF base fixture", pattern); definitions.put(id, machine);
                for (var facing : Direction.Plane.HORIZONTAL) {
                    place(world, pos, EFabricatorRegistration.CONTROLLERS.get(tier).get().defaultBlockState().setValue(BlockEFabricatorController.FACING, facing), touched);
                    var positions = new ArrayList<BlockPos>();
                    for (int i = 0; i < ids.size(); i++) {
                        var next = StructureMatcher.toWorldOffset(pos, facing, i + 1, 0, 0); positions.add(next);
                        place(world, next, EFabricatorRegistration.PARTS.get(ids.get(i)).get().defaultBlockState().setValue(BlockEFabricatorPart.FACING, facing), touched);
                    }
                    var host = (TileEFabricatorController)world.getBlockEntity(pos); host.refreshStructure();
                    var worker = (TileEFabricatorWorker)world.getBlockEntity(positions.get(0));
                    var bus = (TileEFabricatorPatternBus)world.getBlockEntity(positions.get(1));
                    require(host.isOperational() && host.parts().size() == 6 && worker.isActivePart() && host.parallelism() == 352, "EF formation/rotation/part types");
                    require(worker.appearance() == EFabricatorAppearance.ON && worker.getBlockState().getLightEmission(world, worker.getBlockPos()) == 10
                            && host.getBlockState().getLightEmission(world, pos) == 10, "source assembled LED/light");
                    var workerLightState = worker.getBlockState(); var workerLightPos = worker.getBlockPos();
                    require(java.util.concurrent.CompletableFuture.supplyAsync(() -> workerLightState.getLightEmission(world, workerLightPos)).join() == 10,
                            "lighting-thread lookup lost emission or required a main-thread chunk lookup");
                    require(host.modelHiddenPositions().size() == 17 && new HashSet<>(host.modelHiddenPositions()).size() == 17
                            && !host.modelHiddenPositions().contains(pos)
                            && host.modelHiddenPositions().contains(StructureMatcher.toWorldOffset(pos, facing, -1, -1, 1)), "source rotated seventeen-cell host hiding");
                    host.setModes(true, false); require(host.parallelism() == 813 && worker.queueDepth() == 32 * tier.overclockMultiplier, "source overclock quotas");
                    host.setModes(true, true); require(host.parallelism() == 864 && host.workDelay() == 19, "source cooling removes parallel debuffs");
                    var output = new ItemStack(Items.OAK_PLANKS, 4); output.getOrCreateTag().putString("Signature", "private-output");
                    require(worker.offerWork(new EFabricatorQueue.Work(1000, output, List.of(new ItemStack(Items.BUCKET)))) == worker.queueDepth(), "bounded worker acceptance");
                    int retained = worker.queuedCrafts(); host.setModes(false, false);
                    require(worker.queuedCrafts() == retained && worker.offerWork(new EFabricatorQueue.Work(1, output, List.of())) == 0, "downclock deleted or overfilled queued work");
                    var disk = worker.workData(); var hostDisk = host.saveWithoutMetadata(); var workerDisk = worker.saveWithoutMetadata();
                    worker.refreshAppearance();
                    require(worker.appearance() == EFabricatorAppearance.RUN && worker.energyStored() == 0, "source queued work LED without fabricated power");
                    require(worker.getUpdateTag().getAllKeys().equals(Set.of("Appearance")) && bus.getUpdateTag().isEmpty()
                            && worker.getBlockState().getValue(EFabricatorAppearance.STATUS) == EFabricatorAppearance.OFF, "queue/pattern leak or cosmetic world-palette mutation");
                    worker.handleUpdateTag(new CompoundTag()); host.handleUpdateTag(new CompoundTag());
                    require(worker.workData().equals(disk) && host.isAssembled(), "client metadata hook changed authoritative server data");
                    if (tier == EFabricatorSpecs.Tier.L9 && facing == Direction.NORTH && net.minecraftforge.fml.ModList.get().isLoaded("theoneprobe"))
                        hellfirepvp.modularmachinery.integration.top.EFabricatorProbeRegressionChecks.run(world, worker, player);
                    host.load(hostDisk); worker.load(workerDisk);
                    require(!host.isAssembled() && worker.owner() == null && worker.workData().equals(disk), "trusted saved ownership or altered work");
                    host.tick(); require(host.isOperational() && worker.queuedCrafts() == retained, "reload lost queue or binding");
                    var otherPos = pos.below(2); place(world, otherPos, EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L4).get().defaultBlockState(), touched);
                    require(!worker.canBind((TileEFabricatorController)world.getBlockEntity(otherPos)), "shared worker owner accepted");
                    host.refreshStructure(); require(!host.isAssembled() && worker.owner() == null, "cross-tier shared controller rule");
                    world.setBlockAndUpdate(otherPos, Blocks.AIR.defaultBlockState()); host.refreshStructure();
                    worker.onChunkUnloaded(); require(!worker.isActivePart() && worker.owner() == null && worker.workData().equals(disk), "worker unload lost work or remained active");
                    require(worker.appearance() == EFabricatorAppearance.OFF, "unloaded worker kept RUN light");
                    require(EFabricatorAppearance.serverLight(world, worker.getBlockPos()) == 0, "unloaded worker retained server light metadata");
                    worker.onLoad(); host.refreshStructure();
                    var handler = bus.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
                    require(handler.getSlots() == 72 && handler.getSlotLimit(0) == 1 && !handler.isItemValid(0, new ItemStack(Items.STONE))
                            && !handler.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "pattern inventory bounds/filter");
                    if (tier == EFabricatorSpecs.Tier.L9 && facing == Direction.NORTH) {
                        if (net.minecraftforge.fml.ModList.get().isLoaded("ae2"))
                            hellfirepvp.modularmachinery.integration.ae2.efabricator.EFabricatorPatternChecks.run(bus);
                        else {
                            var channel = (TileEFabricatorMEChannel)world.getBlockEntity(positions.get(2));
                            var channelTag = channel.saveWithoutMetadata(); var futureNode = new CompoundTag();
                            futureNode.putString("UnknownAEVersion", "preserve-without-AE"); channelTag.put("MENode", futureNode);
                            channel.load(channelTag); host.refreshStructure();
                            require(channel.saveWithoutMetadata().getCompound("MENode").equals(futureNode) && channel.getUpdateTag().isEmpty()
                                    && !channel.isNetworkActive(), "optional AE node data lost or exposed without AE");
                        }
                        var data = bus.patternData();
                        bus.onChunkUnloaded(); require(handler.getStackInSlot(0).isEmpty() && handler.extractItem(0, 1, false).isEmpty(), "stale inventory survived unload");
                        bus.onLoad(); host.refreshStructure(); require(bus.patternData().equals(data), "unload changed physical patterns");
                        survival(world, player, worker, "EfWorkData", worker.workData()); host.refreshStructure();
                        worker = (TileEFabricatorWorker)world.getBlockEntity(positions.get(0));
                        survival(world, player, bus, "EfPatternData", bus.patternData()); host.refreshStructure();
                        bus = (TileEFabricatorPatternBus)world.getBlockEntity(positions.get(1));
                        unknown(worker, bus);
                        survival(world, player, worker, "EfWorkData", worker.workData()); host.refreshStructure();
                        bus = (TileEFabricatorPatternBus)world.getBlockEntity(positions.get(1));
                        survival(world, player, bus, "EfPatternData", bus.patternData()); host.refreshStructure();
                        var twice = new MachinePattern();
                        for (var part : pattern.getParts()) twice.addPart(part);
                        twice.addPart(new MachinePattern.Part(7, 0, 0, "modularmachinery:efabricator_me_channel"));
                        var channel2 = StructureMatcher.toWorldOffset(pos, facing, 7, 0, 0);
                        place(world, channel2, EFabricatorRegistration.CHANNEL.get().defaultBlockState(), touched);
                        definitions.put(id, new DynamicMachine(id.getPath(), "two channels", twice)); host.refreshStructure();
                        require(!host.isAssembled(), "multiple EF channels accepted");
                        definitions.put(id, machine); world.setBlockAndUpdate(channel2, Blocks.AIR.defaultBlockState()); host.refreshStructure();
                        var production = new CompoundTag(); production.putInt("Version", 1); production.putLong("TotalCrafted", 3);
                        var pending = new EFabricatorOutputBuffer();
                        pending.add(new EFabricatorQueue.Work(3, output, List.of(new ItemStack(Items.BUCKET)))); production.put("Outputs", pending.save());
                        var cooling = new CompoundTag(); cooling.putInt("Version", 1); cooling.putInt("Cache", 271); production.put("Cooling", cooling);
                        host.restoreProductionData(production);
                        require(!host.getUpdateTag().contains("EfProduction") && host.productionData().equals(production), "private output state leaked or changed");
                        survivalController(world, player, host, production); host = (TileEFabricatorController)world.getBlockEntity(pos); host.refreshStructure();
                        require(host.coolingUnits() == 271, "survival placement lost private cooling cache");
                        var future = new CompoundTag(); future.putInt("Version", 999); future.putString("Output", "keep-future-format");
                        host.restoreProductionData(future); require(!host.productionReadable() && !host.hasQueueSpace() && host.totalCrafted() == -1, "unknown output state remained writable");
                        host.load(host.saveWithoutMetadata()); require(host.productionData().equals(future), "unknown output state lost on reload"); host.refreshStructure();
                        survivalController(world, player, host, future); host = (TileEFabricatorController)world.getBlockEntity(pos); host.refreshStructure();
                        host.restoreProductionData(new CompoundTag());
                    }
                    definitions.remove(id); host.refreshStructure(); require(!host.isAssembled() && host.parts().isEmpty(), "missing definition retained parts");
                    definitions.put(id, machine); host.refreshStructure(); require(host.isOperational(), "definition recovery failed");
                    host.onChunkUnloaded(); require(!host.isOperational() && host.parts().isEmpty(), "host unload retained active parts");
                    host.onLoad(); host.refreshStructure();
                    for (var next : positions) world.setBlockAndUpdate(next, Blocks.AIR.defaultBlockState());
                    world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
            Modularmachinery.LOG.info("[MMC][ef-appearance] PASS: three tiers/four facings, assembled/queued/unloaded worker LEDs, source host/worker light, seventeen rotated hidden cells, immutable cosmetic world palette, bounded metadata and server packet-hook rejection");
            Modularmachinery.LOG.info("[MMC][ef-base] PASS: three host tiers/four facings, source parallel/overclock/cooling quotas, exclusive ownership, two-channel rejection, runtime-only binding, downclock/unload/reload retention, private-data exclusion, 72-slot capability lifetime and survival tagged block loot/replacement; AE2 present={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
        } finally {
            for (var next : touched) world.setBlockAndUpdate(next, Blocks.AIR.defaultBlockState());
            saved.forEach((id, old) -> { if (old == null) definitions.remove(id); else definitions.put(id, old); });
            world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(16)).forEach(ItemEntity::discard);
        }
    }
    private static void queues() {
        var unit = new ItemStack(Items.DIAMOND, Integer.MAX_VALUE); unit.getOrCreateTag().putString("Marker", "keep");
        var work = new EFabricatorQueue.Work(1000, unit, List.of(new ItemStack(Items.BUCKET, 300), ItemStack.EMPTY));
        unit.setCount(1); var copy = work.output(); copy.setCount(2);
        var queue = new EFabricatorQueue(); require(queue.offer(work, 32) == 32 && queue.size() == 32, "queue counted batches instead of crafts");
        var decoded = EFabricatorQueue.load(queue.save()); require(decoded.size() == 32 && decoded.offer(work, 32) == 0, "read restored zero queue size");
        var first = decoded.poll(7); var second = decoded.poll(100);
        require(first.crafts() == 7 && second.crafts() == 25 && first.outputAmount() + second.outputAmount() == 32L * Integer.MAX_VALUE
                && first.remaining().get(0).getCount() == 300 && second.remaining().get(0).getCount() == 300
                && first.output().getTag().getString("Marker").equals("keep") && decoded.isEmpty() && decoded.poll(1) == null, "split lost or duplicated output/remainder/NBT");
        require(EFabricatorQueue.load(decoded.save()).isEmpty() && decoded.poll(0) == null, "empty/zero poll");
        var bad = queue.save(); bad.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putInt("Crafts", 513);
        try { EFabricatorQueue.load(bad); throw new AssertionError("oversized queue accepted"); } catch (IllegalArgumentException expected) { }
        bad = queue.save(); bad.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).getCompound("Output").getCompound("Item").putString("id", "absent_mod:item");
        try { EFabricatorQueue.load(bad); throw new AssertionError("missing item silently discarded"); } catch (IllegalArgumentException expected) { }
        var order = new EFabricatorQueue(); order.offer(new EFabricatorQueue.Work(2, new ItemStack(Items.APPLE), List.of()), 32);
        order.offer(new EFabricatorQueue.Work(2, new ItemStack(Items.DIAMOND), List.of()), 32);
        require(order.poll(32).output().is(Items.APPLE) && order.poll(32).output().is(Items.DIAMOND), "FIFO order changed");
        for (var tier : EFabricatorSpecs.Tier.values()) require(tier.energyPerCraft(true, false) == 100 * tier.overclockMultiplier
                && tier.energyPerCraft(true, true) == 100 && tier.queueDepth(true) <= 512, "source energy/queue specification");
        require(EFabricatorSpecs.workDelay(200, true) == 1, "negative cooled work interval");
        Modularmachinery.LOG.info("[MMC][ef-queue] PASS: FIFO counts crafts, bounded partial admission, split conservation, int-sized unit counts/long totals, NBT/deep copies, reload/recount and invalid/missing-item rejection; source specifications");
    }
    private static void outputs() {
        var stack = new ItemStack(Items.DIAMOND, Integer.MAX_VALUE); stack.getOrCreateTag().putString("Private", "buffer-NBT");
        var buffer = new EFabricatorOutputBuffer();
        require(buffer.add(new EFabricatorQueue.Work(512, stack, List.of(new ItemStack(Items.BUCKET, 300)))), "long output admission");
        var copy = EFabricatorOutputBuffer.load(buffer.save()); var entry = copy.entries().get(0);
        require(entry.amount() == 512L * Integer.MAX_VALUE && entry.item().getTag().getString("Private").equals("buffer-NBT"), "long output/NBT codec");
        var exposed = entry.item(); exposed.setCount(2); exposed.getTag().putString("Private", "changed");
        require(entry.item().getCount() == 1 && entry.item().getTag().getString("Private").equals("buffer-NBT"), "mutable output template escaped");
        copy.remove(entry, 3); require(copy.entries().get(0).amount() == entry.amount() - 3, "partial delivery lost remainder");
        var bad = buffer.save(); bad.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putLong("Amount", -1);
        try { EFabricatorOutputBuffer.load(bad); throw new AssertionError("negative output accepted"); } catch (IllegalArgumentException expected) { }
        bad = buffer.save(); var list = bad.getList("Entries", Tag.TAG_COMPOUND); list.add(list.getCompound(0).copy());
        try { EFabricatorOutputBuffer.load(bad); throw new AssertionError("duplicate output accepted"); } catch (IllegalArgumentException expected) { }
        bad = buffer.save(); bad.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).getCompound("Item").putString("id", "missing_mod:product");
        try { EFabricatorOutputBuffer.load(bad); throw new AssertionError("missing product silently discarded"); } catch (IllegalArgumentException expected) { }
        var limit = new EFabricatorOutputBuffer();
        for (int i=0;i<EFabricatorOutputBuffer.MAX_TYPES;i++) {
            var item = new ItemStack(Items.STONE); item.getOrCreateTag().putInt("Type", i);
            require(limit.add(new EFabricatorQueue.Work(1,item,List.of())), "buffer type limit too small");
        }
        var before = limit.save(); require(!limit.add(new EFabricatorQueue.Work(1,new ItemStack(Items.APPLE),List.of())) && limit.save().equals(before), "full buffer changed on rejection");
        var overflow = buffer.save(); overflow.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putLong("Amount", Long.MAX_VALUE);
        var full = EFabricatorOutputBuffer.load(overflow); before = full.save();
        require(!full.add(new EFabricatorQueue.Work(1,stack,List.of())) && full.save().equals(before), "output count overflow changed buffer");
        Modularmachinery.LOG.info("[MMC][ef-output-codec] PASS: bounded atomic product/remainder buffer, long counts, NBT/deep copies, partial removal, duplicate/negative/missing-item rejection and overflow retention");
    }
    private static void survivalController(ServerLevel world, FakePlayer player, TileEFabricatorController host, CompoundTag expected) {
        var pos = host.getBlockPos(); var block = host.getBlockState().getBlock();
        world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
        require(player.gameMode.destroyBlock(pos), "controller survival break failed");
        var drops = world.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
        require(world.getBlockState(pos).isAir() && drops.size()==1 && drops.get(0).getItem().is(block.asItem())
                && drops.get(0).getItem().getTag()!=null && drops.get(0).getItem().getTag().getCompound("EfProduction").equals(expected), "controller pending outputs lost/duplicated in loot");
        var stack=drops.get(0).getItem().copy(); drops.forEach(ItemEntity::discard); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
        require(result.consumesAction() && stack.isEmpty() && world.getBlockEntity(pos) instanceof TileEFabricatorController placed
                && placed.productionData().equals(expected), "placed controller lost output data");
    }
    private static void unknown(TileEFabricatorWorker worker, TileEFabricatorPatternBus bus) {
        var opaque = new CompoundTag(); opaque.putInt("Version", 999); opaque.putString("Future", "preserve-opaque");
        worker.restoreWorkData(opaque); bus.restorePatternData(opaque);
        require(!worker.readable() && !bus.readable() && worker.queuedCrafts() == -1 && worker.workData().equals(opaque) && bus.patternData().equals(opaque), "unknown format treated as empty");
        worker.load(worker.saveWithoutMetadata()); bus.load(bus.saveWithoutMetadata());
        require(worker.workData().equals(opaque) && bus.patternData().equals(opaque), "unknown data changed while saving");
        var missing = new CompoundTag(); missing.putInt("Version", 1); var patterns = new ListTag(); var entry = new CompoundTag();
        entry.putInt("Slot", 0); var item = new CompoundTag(); item.putString("id", "missing_mod:pattern"); item.putByte("Count", (byte)1); entry.put("Item", item); patterns.add(entry); missing.put("Patterns", patterns);
        bus.restorePatternData(missing); require(!bus.readable() && bus.patternData().equals(missing), "missing pattern dependency cleared data");
    }
    private static void survival(ServerLevel world, FakePlayer player, TileEFabricatorPart part, String key, CompoundTag expected) {
        var pos = part.getBlockPos(); var block = part.getBlockState().getBlock();
        world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        require(player.gameMode.destroyBlock(pos), "survival block break failed");
        var drops = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        require(world.getBlockState(pos).isAir() && drops.size() == 1 && drops.get(0).getItem().is(block.asItem())
                && drops.get(0).getItem().getTag() != null && drops.get(0).getItem().getTag().getCompound(key).equals(expected), "survival loot lost/duplicated private component data");
        var stack = drops.get(0).getItem().copy(); drops.forEach(ItemEntity::discard);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        require(result.consumesAction() && stack.isEmpty() && world.getBlockEntity(pos) instanceof TileEFabricatorPart, "real block-item placement failed");
        var replacement = world.getBlockEntity(pos);
        require((replacement instanceof TileEFabricatorWorker w ? w.workData() : ((TileEFabricatorPatternBus)replacement).patternData()).equals(expected), "placed component lost private data");
    }
    private static void place(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, Set<BlockPos> touched) { touched.add(pos); level.setBlockAndUpdate(pos, state); }
    private static void require(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
}
