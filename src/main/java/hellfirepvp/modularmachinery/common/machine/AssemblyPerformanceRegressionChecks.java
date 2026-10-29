/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.assembly.MachineAssembly;
import hellfirepvp.modularmachinery.common.assembly.MachineAssemblyManager;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.event.AutoAssemblyEvents;
import hellfirepvp.modularmachinery.common.registry.DedicatedControllers;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import hellfirepvp.modularmachinery.common.util.RollingTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;

/** Runs only in isolated self-tests. Covers actual assembly requests, inventory, world writes and ticker searches. */
public final class AssemblyPerformanceRegressionChecks {
    private AssemblyPerformanceRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        checkTimings();
        checkAssembly(level, origin);
        checkFluidPayments(level, origin.offset(0, 0, 6));
        checkIdleSearch(level, origin.offset(0, 0, 12));
    }

    private static void checkAssembly(ServerLevel level, BlockPos origin) throws Exception {
        var loader = MachineReloadListener.getLoader();
        var definitions = new LinkedHashMap<>(loader.getMachines());
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (int x = 0; x <= 5; x++) previous.put(origin.offset(x, 0, 0), level.getBlockState(origin.offset(x, 0, 0)));
        var field = Config.class.getDeclaredField("AUTO_ASSEMBLY_NEED_ALL");
        field.setAccessible(true);
        var needAll = (ForgeConfigSpec.BooleanValue) field.get(null);
        boolean oldNeedAll = needAll.get();
        var variable = "assembly_regression_choices";
        List<String> oldVariable = loader.getVariables().get(variable);
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        pattern.addPart(new MachinePattern.Part(2, 0, 0, "minecraft:stone"));
        pattern.addPart(new MachinePattern.Part(3, 0, 0, "modularmachinery:blockinputbus"));
        pattern.addPart(new MachinePattern.Part(4, 0, 0, "minecraft:water"));
        pattern.addPart(new MachinePattern.Part(5, 0, 0, variable));
        List<RecordingPlayer> players = new ArrayList<>();
        try {
            needAll.set(true);
            loader.getVariables().put(variable, List.of("minecraft:iron_block", "minecraft:gold_block"));
            var blocks = new ArrayList<Block>(List.of(RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get()));
            for (var entry : DedicatedControllers.entries()) {
                if (blocks.stream().noneMatch(b -> b instanceof hellfirepvp.modularmachinery.common.block.BlockController c
                        && Objects.equals(c.getDedicatedMachine(), entry.definition().name())
                        && (b instanceof hellfirepvp.modularmachinery.common.block.BlockFactoryController) == entry.factory()))
                    blocks.add(entry.block().get());
                if (blocks.size() >= 4) break;
            }
            int tested = 0;
            for (Block block : blocks) {
                String binding = DedicatedControllers.binding(block);
                var machine = new DynamicMachine(binding == null ? "assembly_regression" : binding, "Assembly test", pattern);
                loader.getMachines().put(new ResourceLocation("modularmachinery", machine.getRegistryName()), machine);
                previous.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                level.setBlock(origin, block.defaultBlockState(), Block.UPDATE_ALL);
                var controller = (TileController) level.getBlockEntity(origin);
                controller.setMachineName(machine.getRegistryName());
                var author = player(level, origin, true); players.add(author);
                var selectionTool = hellfirepvp.modularmachinery.common.registry.RegistryItems.CONSTRUCT_TOOL.get();
                author.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(selectionTool));
                selectionTool.useOn(new net.minecraft.world.item.context.UseOnContext(author, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(origin),
                                Direction.UP, origin, false)));
                require(!controller.isFormed() && !MachineAssemblyManager.contains(level, origin)
                        && level.getBlockState(origin.offset(1, 0, 0)).isAir()
                        && author.messages.stream().anyMatch(m -> json(m).contains("message.structurebuild.empty")),
                        "selection tool with no selection still places/forms a machine instead of reporting its empty selection");
                var creative = player(level, origin, true); players.add(creative);
                AutoAssemblyEvents.processAssemblyRequest(level, creative, origin, 0);
                require(level.getBlockState(origin.offset(1, 0, 0)).is(Blocks.STONE)
                        && level.getBlockState(origin.offset(3, 0, 0)).is(RegistryBlocks.itemInputBus.get())
                        && level.getBlockState(origin.offset(4, 0, 0)).is(Blocks.WATER)
                        && level.getBlockState(origin.offset(5, 0, 0)).is(Blocks.IRON_BLOCK),
                        "creative stick request still requires materials: " + block);
                require(!MachineAssemblyManager.contains(level, origin) && creative.getMainHandItem().getCount() == 1
                        && creative.messages.stream().noneMatch(m -> json(m).contains("assembly.tip.required")),
                        "creative request consumed inventory, queued work, or reported missing materials");

                for (int x = 1; x <= 5; x++) level.setBlock(origin.offset(x, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                var survival = player(level, origin, false); players.add(survival);
                survival.getInventory().items.set(1, new ItemStack(Items.STONE));
                AutoAssemblyEvents.processAssemblyRequest(level, survival, origin, 0);
                String messages = survival.messages.stream().map(AssemblyPerformanceRegressionChecks::json)
                        .collect(java.util.stream.Collectors.joining("\n"));
                require(messages.contains("assembly.tip.required") && messages.contains("block.minecraft.stone")
                        && messages.contains("1000 mB ") && messages.contains("assembly.tip.or")
                        && messages.contains("block.minecraft.iron_block") && messages.contains("block.minecraft.gold_block"),
                        "missing-material report omitted counts, translated names, fluids or alternatives: " + messages);
                require(survival.getInventory().items.get(1).getCount() == 1 && !MachineAssemblyManager.contains(level, origin)
                        && level.getBlockState(origin.offset(1, 0, 0)).isAir(), "failed preflight consumed inventory or placed a block");

                survival.getInventory().items.set(2, new ItemStack(Items.STONE));
                survival.getInventory().items.set(3, new ItemStack(RegistryBlocks.itemInputBus.get())); // No BlockStateTag.
                survival.getInventory().items.set(4, new ItemStack(Items.WATER_BUCKET));
                survival.getInventory().items.set(5, new ItemStack(Items.GOLD_BLOCK));
                var assembly = MachineAssembly.start(level, origin, survival, machine, Direction.NORTH, loader);
                require(assembly != null, "complete survival inventory (untagged base component/alternate block) rejected");
                for (int tick = 0; !assembly.isFinished() && tick < Config.autoAssemblyInterval() * 10; tick++) assembly.tick();
                require(assembly.isFinished() && level.getBlockState(origin.offset(3, 0, 0)).is(RegistryBlocks.itemInputBus.get())
                        && level.getBlockState(origin.offset(5, 0, 0)).is(Blocks.GOLD_BLOCK), "survival job did not place paid alternatives");
                require(count(survival, Items.STONE) == 0 && count(survival, Items.WATER_BUCKET) == 0
                        && count(survival, Items.BUCKET) == 1 && count(survival, Items.GOLD_BLOCK) == 0,
                        "survival assembly payment/container return was not conserved");
                tested++;
            }
            // Partial mode still sends the report before building the affordable cells.
            for (int x = 1; x <= 5; x++) level.setBlock(origin.offset(x, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            needAll.set(false);
            var partial = player(level, origin, false); players.add(partial);
            partial.getInventory().items.set(1, new ItemStack(Items.STONE));
            var machine = loader.findByName(((TileController) level.getBlockEntity(origin)).getMachineName());
            var job = MachineAssembly.start(level, origin, partial, machine, Direction.NORTH, loader);
            require(job != null && partial.messages.stream().anyMatch(m -> json(m).contains("assembly.tip.required")), "partial mode omitted material report");
            for (int tick = 0; !job.isFinished() && tick < Config.autoAssemblyInterval() * 10; tick++) job.tick();
            require(level.getBlockState(origin.offset(1, 0, 0)).is(Blocks.STONE) && count(partial, Items.STONE) == 0,
                    "partial mode did not place exactly the affordable block");
            Modularmachinery.LOG.info("[MMC][assembly-regression] PASS: {} controller types, selection tool never auto-builds, actual creative stick requests, survival reports/alternatives, base-tier items, split stacks, fluid buckets, inventory conservation, partial mode", tested);
        } finally {
            players.forEach(MachineAssemblyManager::removePlayer);
            needAll.set(oldNeedAll);
            previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            loader.getMachines().clear(); loader.getMachines().putAll(definitions);
            if (oldVariable == null) loader.getVariables().asMap().remove(variable);
            else loader.getVariables().put(variable, oldVariable);
        }
    }

    private static void checkFluidPayments(ServerLevel level, BlockPos origin) {
        var loader = MachineReloadListener.getLoader();
        BlockPos target = origin.offset(1, 0, 0);
        BlockState before = level.getBlockState(target);
        var player = player(level, origin, false);
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:water"));
        var machine = new DynamicMachine("assembly_fluid_regression", "Fluid payment test", pattern);
        // GTCEu attaches its own fluid handler to glass bottles. Use an otherwise inert item for
        // this synthetic 2000 mB capability so the fixture does not compete with real mod handlers.
        java.util.function.Consumer<net.minecraftforge.event.AttachCapabilitiesEvent<ItemStack>> attach = event -> {
            if (!event.getObject().is(Items.PAPER)) return;
            var handler = new net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack(event.getObject(), 2000);
            var capability = net.minecraftforge.common.util.LazyOptional.of(() -> handler);
            event.addCapability(new ResourceLocation("modularmachinery", "assembly_test_tank"),
                    new net.minecraftforge.common.capabilities.ICapabilityProvider() {
                        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                                net.minecraftforge.common.capabilities.Capability<T> type, Direction side) {
                            return type == net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM
                                    ? capability.cast() : net.minecraftforge.common.util.LazyOptional.empty();
                        }
                    });
        };
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == player && event.getPos().equals(target)) event.setCanceled(true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, attach);
        try {
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack partial = new ItemStack(Items.PAPER);
            ItemStack full = new ItemStack(Items.PAPER);
            var water = net.minecraft.world.level.material.Fluids.WATER;
            var execute = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
            net.minecraftforge.fluids.FluidUtil.getFluidHandler(partial).orElseThrow(IllegalStateException::new)
                    .fill(new net.minecraftforge.fluids.FluidStack(water, 500), execute);
            net.minecraftforge.fluids.FluidUtil.getFluidHandler(full).orElseThrow(IllegalStateException::new)
                    .fill(new net.minecraftforge.fluids.FluidStack(water, 1500), execute);
            require(fluidAmount(partial) == 500 && fluidAmount(full) == 1500, "synthetic fluid fixtures did not fill");
            player.getInventory().items.set(1, partial);
            player.getInventory().items.set(2, full);
            var assembly = MachineAssembly.start(level, origin, player, machine, Direction.NORTH, loader);
            require(assembly != null && fluidAmount(partial) == 500 && fluidAmount(full) == 1500,
                    "fluid preflight mutated real inventory");
            finishAssembly(assembly);
            require(level.getBlockState(target).is(Blocks.WATER) && fluidAmount(partial) == 500
                    && fluidAmount(full) == 500 && count(player, Items.BUCKET) == 0,
                    "fluid assembly drained an insufficient container or created an extra bucket");
            // A protected placement must return the paid block item, including a filled bucket.
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(cancel);
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            player.getInventory().clearContent();
            player.getInventory().items.set(1, new ItemStack(Items.WATER_BUCKET));
            finishAssembly(MachineAssembly.start(level, origin, player, machine, Direction.NORTH, loader));
            require(level.getBlockState(target).isAir() && count(player, Items.WATER_BUCKET) == 1
                    && count(player, Items.BUCKET) == 0, "protected bucket placement lost or duplicated its payment");
            player.getInventory().clearContent();
            player.getInventory().items.set(1, partial);
            player.getInventory().items.set(2, full);
            net.minecraftforge.fluids.FluidUtil.getFluidHandler(full).orElseThrow(IllegalStateException::new)
                    .fill(new net.minecraftforge.fluids.FluidStack(water, 1000), execute);
            finishAssembly(MachineAssembly.start(level, origin, player, machine, Direction.NORTH, loader));
            require(level.getBlockState(target).isAir() && fluidAmount(partial) + fluidAmount(full) == 2000
                    && count(player, Items.BUCKET) == 0, "protected fluid placement lost or duplicated its payment");
            Modularmachinery.LOG.info("[MMC][assembly-fluid-regression] PASS: simulated preflight, insufficient-container conservation, fluid-handler payment, no extra bucket, protected item/fluid refunds");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(attach);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(cancel);
            level.setBlock(target, before, Block.UPDATE_ALL);
        }
    }

    private static int fluidAmount(ItemStack stack) {
        return net.minecraftforge.fluids.FluidUtil.getFluidHandler(stack).orElseThrow(IllegalStateException::new)
                .getFluidInTank(0).getAmount();
    }

    private static void finishAssembly(MachineAssembly assembly) {
        require(assembly != null, "funded assembly was rejected");
        for (int tick = 0; !assembly.isFinished() && tick < Config.autoAssemblyInterval() * 10; tick++) assembly.tick();
        require(assembly.isFinished(), "assembly did not finish within its placement interval");
    }

    private static void checkIdleSearch(ServerLevel level, BlockPos origin) {
        var loader = MachineReloadListener.getLoader();
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine();
        var definitions = new LinkedHashMap<>(loader.getMachines());
        var recipeBefore = new LinkedHashMap<>(recipes);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (int x = 0; x <= 2; x++) previous.put(origin.offset(x, 0, 0), level.getBlockState(origin.offset(x, 0, 0)));
        long timeBefore = level.getGameTime();
        try {
            level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.offset(1, 0, 0), RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.offset(2, 0, 0), RegistryBlocks.itemOutputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var pattern = new MachinePattern();
            pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockinputbus"));
            pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:blockoutputbus"));
            var machine = new DynamicMachine("normal_search_regression", "Normal search test", pattern);
            loader.getMachines().put(new ResourceLocation("modularmachinery", machine.getRegistryName()), machine);
            var controller = (TileController) level.getBlockEntity(origin);
            controller.setMachineName(machine.getRegistryName());
            require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, loader).formed(), "search fixture did not form");
            controller.setFormed(true);
            var probe = new CountingRequirement();
            var candidates = new ArrayList<MachineRecipe>();
            for (int i = 0; i < 80; i++) candidates.add(new MachineRecipe(machine.getRegistryName(), "idle_probe_" + i, 2,
                    List.of(probe), null, false, 1, ""));
            recipes.put(machine.getRegistryName(), candidates);
            for (int tick = 0; tick < 200; tick++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(timeBefore + tick);
                MachineTicker.tick(level, origin, controller);
            }
            require(probe.checks >= 160 && probe.checks <= 800, "idle ticker still scans 80 recipes every tick, or never searches: " + probe.checks);
            int searches = controller.getRecipeSearchSamples();
            require(searches >= 2 && searches <= 10, "search timing contains non-search ticks");
            var input = ((TileItemBus) level.getBlockEntity(origin.offset(1, 0, 0))).getInventory();
            int beforeWake = probe.checks;
            input.insertItem(0, new ItemStack(Items.COAL), false);
            MachineTicker.tick(level, origin, controller);
            require(probe.checks > beforeWake, "local item input did not wake backoff search");
            var real = new MachineRecipe(machine.getRegistryName(), "wake_real", 2, List.of(
                    new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                    new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, 1, "");
            recipes.put(machine.getRegistryName(), List.of(real));
            controller.requestNormalRecipeSearch();
            MachineTicker.tick(level, origin, controller);
            require(controller.hasActiveRecipe(), "search backoff blocked a new runnable recipe");
            int searchSamples = controller.getRecipeSearchSamples();
            MachineTicker.tick(level, origin, controller);
            MachineTicker.tick(level, origin, controller);
            require(!controller.hasActiveRecipe() && controller.getRecipeSearchSamples() == searchSamples,
                    "active processing entered search timing, or no longer completed");
            require(((TileItemBus) level.getBlockEntity(origin.offset(2, 0, 0))).getInventory().getStackInSlot(0).is(Items.DIAMOND),
                    "search optimization lost actual recipe output");
            controller.recordNormalRecipeSearchResult(timeBefore + 199, false);
            require(controller.normalRecipeSearchDue(timeBefore + 199, MachineReloadListener.getServerDefinitionRevision() + 1),
                    "definition revision did not reset normal search backoff");
            Modularmachinery.LOG.info("[MMC][normal-search-regression] PASS: {} requirement checks / 200 idle ticks / 80 recipes (old path 16000), {} actual searches, input wakeup, real crafting, reload wakeup, active/search timing separation", beforeWake, searches);
        } finally {
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(timeBefore);
            previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            loader.getMachines().clear(); loader.getMachines().putAll(definitions);
            recipes.clear(); recipes.putAll(recipeBefore);
        }
    }

    private static void checkTimings() {
        var cpu = new RollingTiming(100);
        var search = new RollingTiming(20);
        cpu.add(200_000_000L); search.add(200_000_000L);
        for (int i = 0; i < 100; i++) cpu.add(1_000);
        for (int i = 0; i < 20; i++) search.add(2_000);
        require(cpu.averageMicros() == 1 && cpu.size() == 100 && search.averageMicros() == 2 && search.size() == 20,
                "startup timing spikes never leave the rolling windows");
        for (int i = 0; i < 1000; i++) cpu.add(Long.MAX_VALUE);
        require(cpu.averageMicros() > 0, "timing accumulation overflowed");
    }

    private static RecordingPlayer player(ServerLevel level, BlockPos origin, boolean creative) {
        var player = new RecordingPlayer(level);
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        player.gameMode.changeGameModeForPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Config.autoAssemblyItem()));
        return player;
    }
    private static int count(RecordingPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static String json(Component message) { return Component.Serializer.toJson(message); }
    private static final class RecordingPlayer extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();
        private RecordingPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "AssemblyProbe")); }
        @Override public void displayClientMessage(Component message, boolean actionBar) { messages.add(message.copy()); }
    }
    private static final class CountingRequirement implements Requirement {
        private int checks;
        @Override public IOType getIOType() { return IOType.INPUT; }
        @Override public boolean canStart(MachineContext context) { checks++; return false; }
        @Override public String typeId() { return "modularmachinery:regression_search_probe"; }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
