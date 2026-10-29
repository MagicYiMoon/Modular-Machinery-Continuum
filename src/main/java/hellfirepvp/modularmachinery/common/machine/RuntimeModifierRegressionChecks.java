/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.modifier.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import hellfirepvp.modularmachinery.common.upgrade.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.lang.reflect.Method;
import java.util.*;

/** Real IO, thread scopes, preset polling and save/load checks; only used by the opt-in server suite. */
public final class RuntimeModifierRegressionChecks {
    private static final String TARGET = "mmc_runtime_modifier_regression";
    private RuntimeModifierRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var before = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines();
        var saved = new LinkedHashMap<>(machines);
        try {
            checkState();
            checkOrdinary(level, origin, before);
            checkFactory(level, origin.offset(0, 0, 8), before);
            Modularmachinery.LOG.info("[MMC][runtime-modifier] PASS: real IO, PRE edits, replacement, three scopes, existing-thread broadcast, defaults, save/load, completion and stale/off-thread writes");
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(TARGET);
            machines.clear(); machines.putAll(saved); MachineReloadListener.refreshRecipes();
        }
    }

    private static RecipeModifier modifier(String type, IOType io, float multiplier) {
        return new RecipeModifier("modularmachinery:" + type, io, multiplier, RecipeModifier.OPERATION_MULTIPLY, false);
    }
    private static void checkState() {
        var state = new RuntimeModifierState();
        var two = modifier("item", IOType.OUTPUT, 2);
        var three = modifier("item", IOType.OUTPUT, 3);
        state.configure(Map.of("default", two), Map.of("once", two));
        state.put(true, "default", three); state.clearSemi();
        for (int i = 0; i < 3; i++) state.configure(Map.of("default", two), Map.of("once", two));
        require(RuntimeModifierState.same(state.permanent().get("default"), three) && state.semi().isEmpty(),
                "preset polling erased override or resurrected completed semi modifier");
        var tag = new CompoundTag(); state.write(tag);
        var loaded = new RuntimeModifierState(); loaded.read(tag);
        loaded.configure(Map.of("default", two), Map.of("once", two));
        require(RuntimeModifierState.same(loaded.permanent().get("default"), three) && loaded.semi().isEmpty(),
                "save/load lost override/default history");
        loaded.remove(true, "default"); loaded.configure(Map.of("default", two), Map.of("once", two));
        require(loaded.permanent().isEmpty(), "polling restored explicitly removed default");
        loaded.configure(Map.of("default", three), Map.of("once", two));
        require(RuntimeModifierState.same(loaded.permanent().get("default"), three), "changed default never applied");
        loaded.configure(Map.of(), Map.of());
        require(loaded.active().isEmpty(), "deleted unmodified default remained");
        var idle = new FactoryRecipeThread(null); idle.addPermanentModifier("saved", two);
        var restored = FactoryRecipeThread.deserialize(idle.serialize(), null);
        require(restored != null && restored.getPermanentModifiers().containsKey("saved"), "idle dynamic modifier lost on load");
        require(!state.put(true, "default", modifier("item", IOType.OUTPUT, 3)), "equal value was treated as a new modifier");
    }

    private static void checkOrdinary(ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> before) throws Exception {
        var tile = place(level, pos, false, before);
        var machine = install(); var context = context(level, pos, machine);
        var recipe = recipe("modifier_ordinary", "");
        var energy = ((TileEnergyHatch) level.getBlockEntity(pos.offset(3, 0, 0))).getEnergyStorage();
        inventory(level, pos.offset(1, 0, 0)).setStackInSlot(0, new ItemStack(Items.COAL, 16));
        energy.receiveEnergy(10000, false);
        method(MachineTicker.class, "tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class)
                .invoke(null, tile, context, List.of(recipe), MachineTicker.resolvedRun(machine, recipe, context));
        var thread = tile.getRecipeThreadList()[0];
        var retained = thread.getContext();
        thread.addModifier("yield", modifier("item", IOType.OUTPUT, 2));
        thread.addModifier("yield", modifier("item", IOType.OUTPUT, 3));
        thread.addPermanentModifier("speed", modifier("duration", IOType.INPUT, .5F));
        tile.addPermanentModifier("global_energy", modifier("energy", IOType.INPUT, .5F));
        require(retained.getEffectiveDuration() == 4 && thread.getContext().getEffectiveDuration() == 2
                && tile.getRecipeDuration() == 2 && ((RequirementItem) retained.getRequirements().get(1)).getAmount() == 1
                && ((RequirementItem) thread.getContext().getRequirements().get(1)).getAmount() == 3,
                "live edits stacked, failed to refresh duration, or mutated retained observation");
        require(!tile.hasModifier("yield") && tile.hasModifier("global_energy") && thread.hasModifier("yield")
                && thread.hasPermanentModifier("speed"), "controller/thread scopes were conflated");
        Thread invalidWriter = new Thread(() -> thread.addModifier("off_thread", modifier("item", IOType.OUTPUT, 8)));
        invalidWriter.start(); invalidWriter.join();
        require(!thread.hasModifier("off_thread"), "off-thread script write reached world state");
        ScriptMachineEventRegistry.register(TARGET, RecipeTickEvent.class, event -> {
            if (event.phase == Phase.START) event.getRecipeThread().addModifier("tick_energy", modifier("energy", IOType.INPUT, .5F));
        });
        int energyBefore = energy.getEnergyStored();
        Method advance = method(MachineTicker.class, "advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
        advance.invoke(null, tile, context, List.of(recipe), MachineTicker.resolvedRun(machine, recipe, context));
        require(energyBefore - energy.getEnergyStored() == 25, "PRE edit did not affect same-tick continuous IO");
        advance.invoke(null, tile, context, List.of(recipe), MachineTicker.resolvedRun(machine, recipe, context));
        require(!tile.hasActiveRecipe() && inventory(level, pos.offset(2, 0, 0)).getStackInSlot(0).getCount() == 3
                && !thread.hasModifier("yield") && !thread.hasModifier("tick_energy") && thread.hasPermanentModifier("speed")
                && tile.hasModifier("global_energy"), "completion IO or modifier lifetime differs");
        energy.extractEnergy(Integer.MAX_VALUE, false); energy.receiveEnergy(30, false);
        require(MachineTicker.processPerTick(recipe, List.of(new RequirementEnergy(IOType.INPUT, 20),
                        new RequirementEnergy(IOType.INPUT, 20)), context, 1, false) != null && energy.getEnergyStored() == 30,
                "unselected overlapping energy requirements partially consumed a failed tick");
        require(MachineTicker.processPerTick(recipe, List.of(new RequirementEnergy(IOType.INPUT, Integer.MAX_VALUE)),
                        context, 2, false) != null && energy.getEnergyStored() == 30,
                "large batch energy overflowed or consumed a failed tick");
        tile.load(tile.getUpdateTag());
        require(thread.hasPermanentModifier("speed") && tile.hasModifier("global_energy"), "ordinary permanent scopes lost on load");
        energy.receiveEnergy(10000, false);
        thread.addPermanentModifier("input", modifier("item", IOType.INPUT, 2));
        int coalBefore = inventory(level, pos.offset(1, 0, 0)).getStackInSlot(0).getCount();
        method(MachineTicker.class, "tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class)
                .invoke(null, tile, context, List.of(recipe), null);
        require(tile.hasActiveRecipe() && tile.getRecipeDuration() == 2
                && inventory(level, pos.offset(1, 0, 0)).getStackInSlot(0).getCount() == coalBefore - 2,
                "idle ordinary search omitted saved thread modifiers from startup payment/duration");
        tile.abandonRecipe(); thread.removePermanentModifier("input");
        if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
            tile.beginRecipe(recipe.registryName(), 4, 1);
            tile.observeRuntimeRecipeContext(MachineTicker.resolvedRun(machine, recipe, context));
            hellfirepvp.modularmachinery.integration.crafttweaker.RuntimeModifierScriptRegressionChecks.run(tile, null, recipe);
            tile.abandonRecipe();
        }
        thread.removePermanentModifier("speed"); tile.removePermanentModifier("global_energy");
        require(!thread.hasPermanentModifier("speed") && !tile.hasModifier("global_energy"), "permanent removal failed");
        tile.setMachineName(TARGET + "_rebound"); thread.addModifier("stale", modifier("item", IOType.OUTPUT, 2));
        require(tile.getRecipeThreadModifiers(false).isEmpty(), "rebound handle changed new machine");
    }

    private static void checkFactory(ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> before) throws Exception {
        var tile = (TileFactoryController) place(level, pos, true, before);
        var machine = install(); var context = context(level, pos, machine);
        var recipe = recipe("modifier_core", "ore"); var dynamic = recipe("modifier_dynamic", "");
        var preset = modifier("item", IOType.OUTPUT, 2);
        machine.addCoreThreadPreset("ore", List.of(recipe.registryName()), Map.of(), Map.of("yield", preset));
        tile.setFactoryCeiling(3, 1); sync(tile, machine);
        inventory(level, pos.offset(1, 0, 0)).setStackInSlot(0, new ItemStack(Items.COAL, 16));
        ((TileEnergyHatch) level.getBlockEntity(pos.offset(3, 0, 0))).getEnergyStorage().receiveEnergy(10000, false);
        Method start = method(FactoryTicker.class, "startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
        start.invoke(null, tile, machine, context, List.of(recipe)); start.invoke(null, tile, machine, context, List.of(dynamic));
        var core = tile.getFactoryRecipeThreadList().get(0);
        var threads = tile.getRecipeThreadList();
        require(tile.getActiveRecipeList().length == 2, "factory fixture did not start distinct recipes");
        threads[0].addModifier("yield", modifier("item", IOType.OUTPUT, 3));
        threads[0].addPermanentModifier("speed", modifier("duration", IOType.INPUT, .5F));
        tile.addModifier("broadcast", modifier("energy", IOType.INPUT, .5F));
        for (int i = 0; i < 3; i++) sync(tile, machine);
        require(threads[0].hasModifier("broadcast") && threads[1].hasModifier("broadcast")
                && !threads[1].hasModifier("yield") && !threads[1].hasPermanentModifier("speed")
                && core.getRecipeDuration() == 2 && ((RequirementItem) threads[0].getContext().getRequirements().get(1)).getAmount() == 3,
                "broadcast, targeting or preset polling crossed scopes");
        var future = tile.claimThread();
        require(future != null && !future.getSemiPermanentModifiers().containsKey("broadcast"), "future dynamic thread inherited old broadcast");
        tile.removeModifier("broadcast");
        require(!threads[0].hasModifier("broadcast") && !threads[1].hasModifier("broadcast"), "broadcast removal missed a thread");
        var restored = FactoryRecipeThread.deserialize(core.serialize(), tile);
        restored.configurePreset("ore", List.of(recipe.registryName()), Map.of(), Map.of("yield", preset));
        require(RuntimeModifierState.same(restored.getSemiPermanentModifiers().get("yield"), modifier("item", IOType.OUTPUT, 3))
                && restored.getPermanentModifiers().containsKey("speed"), "active core load/preset reset overrides");
        Method tick = method(FactoryTicker.class, "tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
        for (int i = 0; i < 2; i++) tick.invoke(null, core, tile, machine, context, List.of(recipe, dynamic));
        sync(tile, machine);
        require(!core.hasActiveRecipe() && core.getSemiPermanentModifiers().isEmpty() && core.getPermanentModifiers().containsKey("speed")
                && inventory(level, pos.offset(2, 0, 0)).getStackInSlot(0).getCount() == 3
                && tile.getActiveRecipeList().length == 1, "factory completion resurrected defaults or damaged another run");
        start.invoke(null, tile, machine, context, List.of(recipe));
        require(core.getRecipeDuration() == 2 && ((RequirementItem) core.getRuntimeRecipeContext().getRequirements().get(1)).getAmount() == 1,
                "next craft lost permanent speed or recovered cleared semi modifier");
        if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
            hellfirepvp.modularmachinery.integration.crafttweaker.RuntimeModifierScriptRegressionChecks.run(tile, core, recipe);
        checkUpgrade(level, pos, tile, core, machine, context, recipe, dynamic, tick);
        tile.load(tile.getUpdateTag());
        threads[0].addModifier("stale", modifier("item", IOType.OUTPUT, 9));
        require(!tile.getRecipeThreadList()[0].hasModifier("stale"), "pre-load factory handle changed restored thread");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void checkUpgrade(ServerLevel level, BlockPos pos, TileFactoryController tile, FactoryRecipeThread core,
                                     DynamicMachine machine, MachineContext context, MachineRecipe recipe, MachineRecipe dynamic,
                                     Method tick) throws Exception {
        var itemsField = RegistryMachineUpgrade.class.getDeclaredField("ITEMS"); itemsField.setAccessible(true);
        var prototypesField = RegistryMachineUpgrade.class.getDeclaredField("PROTOTYPES"); prototypesField.setAccessible(true);
        Map bindings = (Map) itemsField.get(null); Map prototypes = (Map) prototypesField.get(null);
        var previous = bindings.containsKey(Items.AMETHYST_SHARD) ? new ArrayList((List) bindings.get(Items.AMETHYST_SHARD)) : null;
        var inventory = (IItemHandlerModifiable) ((TileUpgradeBus) level.getBlockEntity(pos.offset(4, 0, 0))).getUpgradeInventory();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        String name = TARGET + "_upgrade";
        try {
            var upgrade = SimpleMachineUpgrade.of(new UpgradeType(name, "Thread regression", 1, 1)).duration(2F);
            upgrade.addEventHandler(FactoryRecipeTickEvent.class, (event, installed) -> {
                var recipeEvent = (FactoryRecipeTickEvent) event;
                if (recipeEvent.phase != Phase.START || !recipeEvent.getRecipeThread().getThreadName().equals("ore")) return;
                calls.incrementAndGet();
                recipeEvent.getRecipeThread().addModifier("upgrade_yield", modifier("item", IOType.OUTPUT, 2));
                recipeEvent.getController().addModifier("upgrade_broadcast", modifier("energy", IOType.INPUT, .5F));
            });
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.AMETHYST_SHARD), upgrade);
            inventory.setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            tick.invoke(null, core, tile, machine, context, List.of(recipe, dynamic));
            require(calls.get() == 1 && core.getRecipeDuration() == 4 && core.getRuntimeRecipeContext().getEffectiveDuration() == 4
                    && core.getSemiPermanentModifiers().containsKey("upgrade_yield")
                    && tile.getRecipeThreadList()[1].hasModifier("upgrade_broadcast"), "installed upgrade failed targeted/broadcast event mutation");
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            require(core.getSemiPermanentModifiers().containsKey("upgrade_yield"), "item removal revoked an already granted semi effect");
            tick.invoke(null, core, tile, machine, context, List.of(recipe, dynamic));
            require(calls.get() == 1 && !core.hasActiveRecipe() && core.getSemiPermanentModifiers().isEmpty()
                    && inventory(level, pos.offset(2, 0, 0)).getStackInSlot(0).getCount() == 5,
                    "upgrade removal kept callbacks, lost granted output, or skipped completion cleanup");
            inventory.setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            method(FactoryTicker.class, "startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class)
                    .invoke(null, tile, machine, context(level, pos, machine), List.of(recipe));
            require(core.hasActiveRecipe() && core.getRecipeDuration() == 4 && core.getRuntimeRecipeContext().getEffectiveDuration() == 4,
                    "startup/context dropped declarative upgrade duration while applying thread speed");
            core.abandonRecipe();
        } finally {
            inventory.setStackInSlot(0, ItemStack.EMPTY); prototypes.remove(name);
            if (previous == null) bindings.remove(Items.AMETHYST_SHARD); else bindings.put(Items.AMETHYST_SHARD, previous);
        }
    }
    private static void sync(TileFactoryController tile, DynamicMachine machine) {
        tile.syncCoreThreadPresets(machine.getCoreThreadPresets(), machine.getCoreThreadPermanentModifiers(), machine.getCoreThreadSemiPermanentModifiers());
    }
    private static DynamicMachine install() {
        var machine = new DynamicMachine(TARGET, TARGET, new MachinePattern());
        MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery", TARGET), machine); return machine;
    }
    private static TileController place(ServerLevel level, BlockPos pos, boolean factory, Map<BlockPos, BlockState> before) {
        Block[] blocks = {factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get(),
                RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.energyInputHatch.get(), RegistryBlocks.upgradeBus.get()};
        for (int i = 0; i < blocks.length; i++) {
            var at = pos.offset(i, 0, 0); before.putIfAbsent(at, level.getBlockState(at));
            level.setBlock(at, blocks[i].defaultBlockState(), Block.UPDATE_ALL);
        }
        var tile = (TileController) level.getBlockEntity(pos); tile.setMachineName(TARGET); tile.setFormed(true);
        tile.setBoundPositions(List.of(pos.offset(1, 0, 0), pos.offset(2, 0, 0), pos.offset(3, 0, 0), pos.offset(4, 0, 0)));
        ((TileUpgradeBus) level.getBlockEntity(pos.offset(4, 0, 0))).setControllerPos(pos);
        return tile;
    }
    private static MachineContext context(ServerLevel level, BlockPos pos, DynamicMachine machine) {
        var context = new MachineContext(level, pos, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
        context.overrideComponentPositions(((TileController) level.getBlockEntity(pos)).getBoundPositions()); return context;
    }
    private static IItemHandlerModifiable inventory(ServerLevel level, BlockPos pos) {
        return (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
    }
    private static MachineRecipe recipe(String name, String thread) {
        return new MachineRecipe(TARGET, name, 4, List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1), new RequirementEnergy(IOType.INPUT, 100)), null, false, -1, thread);
    }
    private static Method method(Class<?> owner, String name, Class<?>... args) throws Exception {
        var method = owner.getDeclaredMethod(name, args); method.setAccessible(true); return method;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
