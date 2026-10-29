/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.component.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import java.util.*;

/** Explicitly registered vanilla barrels stand in for foreign blocks; no optional-mod classes required. */
public final class ComponentProxyRegressionChecks {
    private static final String TARGET = "mmc_proxy_regression", KEY = "mmc:regression_component_proxy";
    private ComponentProxyRegressionChecks() { }
    private static int registrationEvents;
    public static void onRegistration(hellfirepvp.modularmachinery.common.event.RegisterMachineComponentProxiesEvent event) {
        registrationEvents++;
        require(event.getRegistry() == MachineComponentProxyRegistry.INSTANCE, "registration event registry identity");
    }
    public static void run(ServerLevel level, BlockPos origin) {
        var registry = MachineComponentProxyRegistry.INSTANCE;
        var initialRegistry = registry.getRegistry();
        var machines = MachineReloadListener.getLoader().getMachines(); var savedMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var savedRecipes = new LinkedHashMap<>(recipes);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        var views = new IdentityHashMap<BlockEntity, ProxiedMachineComponent>();
        MachineComponentProxy proxy = new MachineComponentProxy() {
            public boolean isSupported(BlockEntity be) { return views.containsKey(be); }
            public ProxiedMachineComponent proxyComponent(BlockEntity be) { return views.get(be); }
        };
        try {
            require(registry.isInitialized(), "common setup did not initialize proxy registration");
            registry.initialize(); require(registry.getRegistry() == initialRegistry, "initialization changed registrations");
            require(registrationEvents == 1, "component registration event was not delivered exactly once");
            registry.register(KEY, proxy);
            try { registry.register(KEY, proxy); throw new AssertionError("duplicate proxy accepted"); }
            catch (IllegalArgumentException expected) { }
            try { registry.getRegistry().clear(); throw new AssertionError("registry snapshot mutable"); }
            catch (UnsupportedOperationException expected) { }
            require(!initialRegistry.containsKey(KEY), "registration mutated old registry snapshot");
            checkSharedBoundaries(level, origin.offset(0, 0, 24), saved, views);
            checkSharedControllers(level, origin.offset(0, 0, 32), saved, views);
            for (boolean factory : List.of(false, true)) {
                BlockPos pos = origin.offset(0, 0, factory ? 12 : 0);
                put(level, pos, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get())
                        .defaultBlockState().setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING, Direction.NORTH), saved);
                var pattern = new MachinePattern();
                for (int i = 1; i <= 4; i++) {
                    put(level, pos.offset(i, 0, 0), Blocks.BARREL.defaultBlockState(), saved);
                    pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:barrel", i == 2 ? "out" : "in"));
                }
                var controller = (TileController) level.getBlockEntity(pos);
                var machine = new DynamicMachine(TARGET, "Proxy fixture", pattern); machine.setMaxThreads(1);
                machine.setParallelizable(true); machine.setInternalParallelism(2); machine.setMaxParallelism(2);
                machines.put(new ResourceLocation("modularmachinery", TARGET), machine); controller.setMachineName(TARGET);
                var inputItems = new ItemStackHandler(2); inputItems.setStackInSlot(0, new ItemStack(Items.COAL, 2));
                var outputItems = new ItemStackHandler(2);
                var inputFluids = new FluidTank(1000); inputFluids.fill(new FluidStack(Fluids.WATER, 200),
                        net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                var outputFluids = new FluidTank(1000);
                var inputEnergy = new EnergyStorage(1000); inputEnergy.receiveEnergy(120, false);
                var outputEnergy = new EnergyStorage(1000);
                var in = ProxiedMachineComponent.of(IOType.INPUT, inputItems, inputFluids, inputEnergy);
                var out = ProxiedMachineComponent.of(IOType.OUTPUT, outputItems, outputFluids, outputEnergy);
                var inBE = level.getBlockEntity(pos.offset(1, 0, 0));
                require(registry.proxy(inBE) == null, "unregistered barrel auto-enabled");
                views.put(inBE, in); views.put(level.getBlockEntity(pos.offset(2, 0, 0)), out);
                views.put(level.getBlockEntity(pos.offset(3, 0, 0)), in); // Same live storage, two structure positions.
                put(level, pos.offset(5, 0, 0), RegistryBlocks.itemInputBus.get().defaultBlockState(), saved);
                checkRegistryPrecedence(registry, inBE, level.getBlockEntity(pos.offset(5, 0, 0)), in, out);
                var context = new MachineContext(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader().getVariables(), pattern);
                require(context.getComponentPositions().size() == 3, "proxy discovery or explicit opt-in boundary");
                require(context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT).equals(List.of(inputItems))
                        && context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.OUTPUT).equals(List.of(outputItems))
                        && context.internalFluidInputs().equals(List.of(inputFluids))
                        && context.internalFluidOutputs().equals(List.of(outputFluids))
                        && context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).equals(List.of(inputEnergy)),
                        "direction or shared-handler deduplication");
                require(context.forSelector("out").capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT).isEmpty()
                        && context.forSelector("in").internalFluidOutputs().isEmpty(), "selector leaked proxy IO");
                var attempt = MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader());
                require(attempt.formed() && controller.getBoundPositions().size() == 3 && controller.getBoundComponentCount() == 3,
                        "formation did not record external parts");
                controller.setFormed(true);
                var energyA = new RequirementEnergy(IOType.INPUT, 10);
                var energyB = new RequirementEnergy(IOType.INPUT, 10);
                var productEnergy = new RequirementEnergy(IOType.OUTPUT, 5);
                var recipe = new MachineRecipe(TARGET, "proxy_paid_batch", 3, List.of(
                        new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                        new RequirementFluid(IOType.INPUT, "minecraft:water", 100), energyA, energyB,
                        new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1),
                        new RequirementFluid(IOType.OUTPUT, "minecraft:lava", 50), productEnergy), null, false, -1, "");
                recipes.put(TARGET, List.of(recipe));
                for (int i = 0; i < 12 && outputItems.getStackInSlot(0).isEmpty(); i++) tick(level, pos, controller);
                require(outputItems.getStackInSlot(0).is(Items.DIAMOND) && outputItems.getStackInSlot(0).getCount() == 2
                        && inputItems.getStackInSlot(0).isEmpty() && inputFluids.isEmpty()
                        && outputFluids.getFluidAmount() == 100 && outputFluids.getFluid().getFluid() == Fluids.LAVA
                        && inputEnergy.getEnergyStored() == 0 && outputEnergy.getEnergyStored() == 30,
                        "ordinary/factory actual paid 2x batch item/fluid/FE IO mismatch");
                // Combined requirements must reserve once against shared identity, including before any execution.
                inputEnergy.receiveEnergy(15, false);
                int oldOutput = outputEnergy.getEnergyStored();
                require(MachineTicker.processPerTick(recipe, List.of(energyA, energyB), context, 1, false) != null
                        && inputEnergy.getEnergyStored() == 15 && outputEnergy.getEnergyStored() == oldOutput,
                        "insufficient combined energy partially consumed or aliases counted twice");
                inputEnergy.receiveEnergy(100, false); outputEnergy.receiveEnergy(1000, false);
                require(MachineTicker.processPerTick(recipe, List.of(energyA, productEnergy), context, 1, false) != null
                        && inputEnergy.getEnergyStored() == 115 && outputEnergy.getEnergyStored() == 1000,
                        "full external energy output consumed input before rejecting tick");
                // Equal-valued wrappers can still own separate buffers. Reserve each identity independently.
                var equalA = new EqualEnergyStorage(20); equalA.receiveEnergy(20, false);
                var equalB = new EqualEnergyStorage(20); equalB.receiveEnergy(20, false);
                var otherBE = level.getBlockEntity(pos.offset(3, 0, 0));
                views.put(inBE, ProxiedMachineComponent.of(IOType.INPUT, null, null, equalA));
                views.put(otherBE, ProxiedMachineComponent.of(IOType.INPUT, null, null, equalB));
                require(MachineTicker.processPerTick(recipe, List.of(energyA, energyB), context, 2, false) == null
                        && equalA.getEnergyStored() == 0 && equalB.getEnergyStored() == 0,
                        "distinct equal-valued energy inputs shared a reservation");
                views.put(inBE, ProxiedMachineComponent.of(IOType.OUTPUT, null, null, equalA));
                views.put(otherBE, ProxiedMachineComponent.of(IOType.OUTPUT, null, null, equalB));
                var equalProduct = new RequirementEnergy(IOType.OUTPUT, 10);
                require(MachineTicker.processPerTick(recipe, List.of(equalProduct, equalProduct), context, 2, false) == null
                        && equalA.getEnergyStored() == 20 && equalB.getEnergyStored() == 20,
                        "distinct equal-valued energy outputs shared a reservation");
                var replacement = new EnergyStorage(1000);
                views.put(inBE, ProxiedMachineComponent.of(IOType.INPUT, null, null, replacement));
                views.remove(level.getBlockEntity(pos.offset(3, 0, 0)));
                require(context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).equals(List.of(replacement)),
                        "context retained stale handler after capability replacement");
                registry.unregister(KEY);
                require(context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).isEmpty()
                        && context.internalFluidOutputs().isEmpty(), "unregister left cached proxy capability");
                registry.register(KEY, proxy);
                put(level, pos.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), saved);
                require(context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).isEmpty(), "removed tile still exposed proxy");
                MachineFormation.rebind(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader());
                require(controller.getBoundPositions().equals(List.of(pos.offset(2, 0, 0))), "rebind retained obsolete proxy positions");
                controller.requestStructureCheck(); tick(level, pos, controller);
                require(!controller.isFormed() && outputEnergy.getEnergyStored() == 1000
                        && outputItems.getStackInSlot(0).getCount() == 2, "broken structure kept processing external IO");
                // Native adapters must retain owner-only extraction and must not survive replacement at the same position.
                BlockPos nativePos = pos.offset(5, 0, 0);
                put(level, nativePos, RegistryBlocks.energyInputHatch.get().defaultBlockState(), saved);
                context.overrideComponentPositions(List.of(nativePos));
                var nativeHatch = (hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch) level.getBlockEntity(nativePos);
                nativeHatch.getEnergyStorage().receiveEnergy(100, false);
                var nativeView = context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).get(0);
                require(nativeHatch.getCapability(ForgeCapabilities.ENERGY).orElse(null).extractEnergy(50, false) == 0
                        && nativeView.extractEnergy(50, false) == 50, "native owner channel lost external direction gate");
                put(level, nativePos, Blocks.AIR.defaultBlockState(), saved);
                put(level, nativePos, RegistryBlocks.energyInputHatch.get().defaultBlockState(), saved);
                var newView = context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).get(0);
                require(newView != nativeView && newView.getEnergyStored() == 0, "native replacement reused stale energy view");
                BlockPos far = new BlockPos(2_000_000, pos.getY(), 2_000_000);
                require(!level.hasChunkAt(far), "unloaded fixture chunk already present");
                context.overrideComponentPositions(List.of(far));
                require(context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.ANY).isEmpty()
                        && !level.hasChunkAt(far), "component query loaded a remote chunk");
                views.clear();
            }
            Modularmachinery.LOG.info("[MMC][component-proxy] PASS: explicit registry/order/native priority, discovery/formation, selectors/direction, alias dedup, ordinary+factory paid 2x item/fluid/FE jobs, joint FE reservation, live replacement/unregister/rebind/unform and unloaded-chunk guard");
        } finally {
            registry.unregister(KEY); registry.unregister(KEY + "_later"); registry.unregister(KEY + "_null");
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(savedMachines); recipes.clear(); recipes.putAll(savedRecipes);
            require(registry.getRegistry().equals(initialRegistry), "regression leaked proxy registration");
        }
    }
    private static void checkSharedBoundaries(ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> saved,
                                               Map<BlockEntity, ProxiedMachineComponent> views) {
        var failures = new ArrayList<String>();
        var pattern = new MachinePattern();
        for (int i = 1; i <= 2; i++) {
            put(level, pos.offset(i, 0, 0), Blocks.BARREL.defaultBlockState(), saved);
            pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:barrel", i == 1 ? "a" : "b"));
        }
        var a = new EqualItemHandler(); var b = new EqualItemHandler();
        a.setStackInSlot(0, new ItemStack(Items.COAL)); b.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        views.put(level.getBlockEntity(pos.offset(1, 0, 0)), ProxiedMachineComponent.of(IOType.INPUT, a, null, null));
        views.put(level.getBlockEntity(pos.offset(2, 0, 0)), ProxiedMachineComponent.of(IOType.INPUT, b, null, null));
        var machine = new DynamicMachine(TARGET, "Shared boundaries", pattern);
        var context = new MachineContext(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader().getVariables(), pattern);
        var coal = new RequirementItem(IOType.INPUT, "minecraft:coal", 1); coal.setSelectorTag("a");
        var iron = new RequirementItem(IOType.INPUT, "minecraft:iron_ingot", 1); iron.setSelectorTag("b");
        var plan = hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.plan(context, List.of(coal, iron), 1, false);
        if (!(plan.valid() && plan.execute() && a.getStackInSlot(0).isEmpty() && b.getStackInSlot(0).isEmpty()))
            failures.add("independent equal-valued item handlers collapsed");
        // Two overlapping snapshots may inspect the same inventory, but only one may commit it.
        a.setStackInSlot(0, new ItemStack(Items.COAL)); b.setStackInSlot(0, ItemStack.EMPTY);
        var first = hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.plan(context, List.of(coal), 1, false);
        var second = hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan.plan(context, List.of(coal), 1, false);
        if (!(first.valid() && second.valid() && first.execute() && !second.execute() && a.getStackInSlot(0).isEmpty()))
            failures.add("stale shared inventory snapshot committed twice");
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:barrel", "alias"));
        var duplicate = new MachineContext(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader().getVariables(), pattern);
        if (!(duplicate.getComponentPositions().equals(List.of(pos.offset(1, 0, 0), pos.offset(2, 0, 0)))
                && duplicate.forSelector("alias").getComponentPositions().equals(List.of(pos.offset(1, 0, 0)))))
            failures.add("duplicate pattern coordinates counted twice or selector alias lost");
        BlockPos nativePos = pos.offset(3, 0, 0);
        put(level, nativePos, RegistryBlocks.fluidInputHatch.get().defaultBlockState(), saved);
        var mutable = nativePos.mutable();
        context.overrideComponentPositions(List.of(mutable, nativePos)); mutable.move(Direction.EAST);
        if (!(context.getComponentPositions().equals(List.of(nativePos)) && context.internalFluidTanks().size() == 1))
            failures.add("override retained duplicate or mutable coordinates");
        context.overrideComponentPositions(List.of(nativePos));
        var oldTank = (hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank) level.getBlockEntity(nativePos);
        oldTank.produceInternal(new FluidStack(Fluids.WATER, 100), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var oldView = context.internalFluidInputs().get(0);
        put(level, nativePos, Blocks.AIR.defaultBlockState(), saved);
        put(level, nativePos, RegistryBlocks.fluidInputHatch.get().defaultBlockState(), saved);
        var newTank = (hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank) level.getBlockEntity(nativePos);
        newTank.produceInternal(new FluidStack(Fluids.LAVA, 200), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var newView = context.internalFluidInputs().get(0);
        if (!(newView != oldView && newView.getFluidInTank(0).getFluid() == Fluids.LAVA
                && newView.drain(50, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount() == 50
                && newTank.getTank().getFluidAmount() == 150 && oldTank.getTank().getFluidAmount() == 100))
            failures.add("replacement fluid hatch reused stale view");
        require(failures.isEmpty(), String.join("; ", failures));
        views.clear();
        Modularmachinery.LOG.info("[MMC][shared-component-boundaries] PASS: independent equal item stores, stale snapshot rejection, duplicate/mutable coordinates, selector aliases and live native fluid replacement");
    }

    private static void checkSharedControllers(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> saved,
                                                Map<BlockEntity, ProxiedMachineComponent> views) {
        var machines = MachineReloadListener.getLoader().getMachines();
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine();
        var pattern = new MachinePattern();
        for (int i = 1; i <= 3; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:barrel", i == 2 ? "out" : "in"));
        var machine = new DynamicMachine(TARGET, "Shared controllers", pattern); machine.setMaxThreads(1);
        machines.put(new ResourceLocation("modularmachinery", TARGET), machine);
        var recipe = new MachineRecipe(TARGET, "shared_paid", 3, List.of(
                new RequirementItem(IOType.INPUT, "minecraft:coal", 1), new RequirementFluid(IOType.INPUT, "minecraft:water", 100),
                new RequirementEnergy(IOType.INPUT, 10), new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1),
                new RequirementFluid(IOType.OUTPUT, "minecraft:lava", 50), new RequirementEnergy(IOType.OUTPUT, 5)), null, false, -1, "");
        recipes.put(TARGET, List.of(recipe));
        var items = new ItemStackHandler(1); items.setStackInSlot(0, new ItemStack(Items.COAL));
        var products = new ItemStackHandler(1); var water = new FluidTank(1000); var lava = new FluidTank(1000);
        var energy = new EnergyStorage(1000); var outputEnergy = new EnergyStorage(1000);
        water.fill(new FluidStack(Fluids.WATER, 100), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        energy.receiveEnergy(30, false);
        var input = ProxiedMachineComponent.of(IOType.INPUT, items, water, energy);
        var output = ProxiedMachineComponent.of(IOType.OUTPUT, products, lava, outputEnergy);
        var controllers = new ArrayList<TileController>(); var positions = new ArrayList<BlockPos>();
        for (int n = 0; n < 2; n++) {
            BlockPos pos = origin.offset(0, 0, n * 8); positions.add(pos);
            put(level, pos, (n == 0 ? RegistryBlocks.blockController.get() : RegistryBlocks.blockFactoryController.get())
                    .defaultBlockState().setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING, Direction.NORTH), saved);
            for (int i = 1; i <= 3; i++) {
                put(level, pos.offset(i, 0, 0), Blocks.BARREL.defaultBlockState(), saved);
                views.put(level.getBlockEntity(pos.offset(i, 0, 0)), i == 2 ? output : input);
            }
            var controller = (TileController) level.getBlockEntity(pos); controllers.add(controller); controller.setMachineName(TARGET);
            require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "shared fixture formation");
            controller.setFormed(true);
        }
        for (int t = 0; t < 20; t++) for (int n = 0; n < 2; n++) tick(level, positions.get(n), controllers.get(n));
        require(products.getStackInSlot(0).getCount() == 1 && items.getStackInSlot(0).isEmpty() && water.isEmpty()
                && energy.getEnergyStored() == 0 && lava.getFluidAmount() == 50 && outputEnergy.getEnergyStored() == 15,
                "shared ordinary/factory controllers duplicated finite resources or products");
        // Refill and drive only the waiting factory; it must see the current shared storage.
        items.setStackInSlot(0, new ItemStack(Items.COAL));
        water.fill(new FluidStack(Fluids.WATER, 100), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        energy.receiveEnergy(30, false);
        for (int t = 0; t < 40; t++) tick(level, positions.get(1), controllers.get(1));
        require(products.getStackInSlot(0).getCount() == 2 && items.getStackInSlot(0).isEmpty() && water.isEmpty()
                && energy.getEnergyStored() == 0 && lava.getFluidAmount() == 100 && outputEnergy.getEnergyStored() == 30,
                "waiting factory did not process exactly one paid refill");
        views.clear();
        Modularmachinery.LOG.info("[MMC][shared-controllers] PASS: ordinary+factory alias the same item/fluid/FE stores; finite inventory pays once; refill resumes waiting factory without duplicate output");
    }

    private static final class EqualItemHandler extends ItemStackHandler {
        private EqualItemHandler() { super(1); }
        @Override public boolean equals(Object other) { return other instanceof EqualItemHandler; }
        @Override public int hashCode() { return 1; }
    }

    private static void checkRegistryPrecedence(MachineComponentProxyRegistry registry, BlockEntity be, BlockEntity nativeBE,
                                                ProxiedMachineComponent first, ProxiedMachineComponent second) {
        registry.register(KEY + "_later", new MachineComponentProxy() {
            public boolean isSupported(BlockEntity candidate) { return true; }
            public ProxiedMachineComponent proxyComponent(BlockEntity candidate) { return second; }
        });
        require(registry.proxy(be) == first, "overlapping proxies lost registration ordering");
        require(registry.proxy(nativeBE) == null, "wildcard proxy replaced a native bus");
        registry.unregister(KEY + "_later");
    }
    private static void tick(ServerLevel level, BlockPos pos, TileController controller) {
        if (controller instanceof TileFactoryController factory) FactoryTicker.tick(level, pos, factory);
        else MachineTicker.tick(level, pos, controller);
    }
    private static void put(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, BlockState> saved) {
        saved.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, Block.UPDATE_ALL);
    }
    private static final class EqualEnergyStorage extends EnergyStorage {
        private EqualEnergyStorage(int capacity) { super(capacity); }
        @Override public boolean equals(Object other) { return other instanceof EqualEnergyStorage; }
        @Override public int hashCode() { return 1; }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
