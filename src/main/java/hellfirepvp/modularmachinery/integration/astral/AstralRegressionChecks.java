/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.astral;

import com.google.gson.*;
import hellfirepvp.astralsorcery.common.base.MoonPhase;
import hellfirepvp.astralsorcery.common.constellation.*;
import hellfirepvp.astralsorcery.common.starlight.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import java.util.*;

public final class AstralRegressionChecks {
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(),
                RegistryModularMagicBlocks.blockStarlightProviderInput.get(), RegistryModularMagicBlocks.blockStarlightProviderOutput.get(),
                RegistryModularMagicBlocks.blockConstellationProvider.get(), RegistryBlocks.energyInputHatch.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var roofBefore = level.getBlockState(origin.offset(2, 1, 0));
        var passivePos = origin.offset(8, 120, 0);
        var passiveBefore = level.getBlockState(passivePos);
        long time = level.getDayTime();
        try {
            // The suite runs at ServerStartedEvent, before the first natural world tick.
            // Post the real Forge event so Astral's registered sky/network handlers initialize normally.
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.LevelTickEvent(
                    net.minecraftforge.fml.LogicalSide.SERVER, net.minecraftforge.event.TickEvent.Phase.END, level, () -> true));
            for (int i = 0; i < blocks.length; i++) { var at = origin.offset(i, 0, 0); before[i] = level.getBlockState(at); level.setBlock(at, blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            level.setBlock(origin.offset(2, 1, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            var input = (TileStarlightProvider.Input) level.getBlockEntity(origin.offset(2, 0, 0));
            var output = (TileStarlightProvider.Output) level.getBlockEntity(origin.offset(3, 0, 0));
            var detector = (TileConstellationProvider) level.getBlockEntity(origin.offset(4, 0, 0));
            var energy = (TileEnergyHatch) level.getBlockEntity(origin.offset(5, 0, 0));
            require(input != null && output != null && detector != null, "native tiles registered");
            input.tick(); output.tick();
            var handler = StarlightNetworkHelper.getHandler(level);
            require(handler != null && handler.hasNode(input.getBlockPos()) && handler.hasNode(output.getBlockPos()), "both providers join actual Astral graph");
            require(output.tryLinkBlock(null, input.getBlockPos()) && input.tryLinkBlock(null, output.getBlockPos()), "native directed link and reciprocal metadata");
            int beforeNetwork = input.stored(); output.offer(1); handler.tick(level);
            require(input.stored() > beforeNetwork && input.stored() - beforeNetwork <= 200,
                    "actual network delivers output with transmission loss: before=" + beforeNetwork + ", after=" + input.stored());
            input.receiveStarlight(Double.NaN, null); input.receiveStarlight(Double.POSITIVE_INFINITY, null);
            input.receiveStarlight(Double.MAX_VALUE, null); require(input.stored() == 10000, "finite input clamps to source capacity");
            var saved = input.saveWithoutMetadata(); input.tick(); require(input.stored() == 9500, "covered input decays by source 5 percent");
            input.load(saved); require(input.stored() == 10000, "input NBT restores intensity");
            var outputSaved = output.saveWithoutMetadata(); output.load(outputSaved);
            require(output.getStarlightProduction() == 0, "NBT reload cannot restore stale production");
            output.onChunkUnloaded(); require(!handler.hasNode(output.getBlockPos()), "unload removes source and cannot generate forever");
            output.onLoad(); output.tick(); require(handler.hasNode(output.getBlockPos()), "reload registers source again");
            require(output.tryLinkBlock(null, input.getBlockPos()), "saved link can be restored");
            var previousConstellation = output.getAttunedConstellationKey(); output.cycleConstellation();
            require(!Objects.equals(previousConstellation, output.getAttunedConstellationKey()), "output attunement can change");
            output.tick();

            var pattern = new MachinePattern();
            for (int i = 2; i < blocks.length; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:stone", i == 2 ? "input" : "other"));
            var machine = new DynamicMachine("astral_regression", "Astral regression", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos(), detector.getBlockPos(), energy.getBlockPos()));
            var in = new RequirementStarlight(IOType.INPUT, 8000); var out = new RequirementStarlight(IOType.OUTPUT, 4000);
            require(in.canStart(context), "intensity threshold met");
            var absent = new RequirementStarlight(IOType.INPUT, 1); absent.setSelectorTag("missing"); require(!absent.canStart(context), "missing selector cannot use unselected input");
            var changed = in.scaled(0.5F, Requirement.OPERATION_MULTIPLY, IOType.INPUT, false);
            require(changed instanceof RequirementStarlight r && r.amount() == 4000, "starlight modifier preserves float intensity");
            var recipe = new MachineRecipe("astral_regression", "astral_regression", 2, List.of(in, out), null, false, -1, "");
            require(MachineTicker.processPerTick(recipe, recipe.requirements(), context, 64, false) == null && input.stored() == 10000,
                    "batch does not multiply or consume source starlight intensity");
            require(output.getStarlightProduction() == 1, "source output conversion amount / 4000");
            var lastOffer = TileStarlightProvider.Output.class.getDeclaredField("lastOffer"); lastOffer.setAccessible(true);
            lastOffer.setLong(output, level.getGameTime() - 3);
            require(output.getStarlightProduction() == 0, "production expires without an active recipe after two ticks");
            int held = energy.getStoredEnergy(); energy.produceInternal(100, false); held = energy.getStoredEnergy();
            var impossible = new RequirementStarlight(IOType.INPUT, 10001);
            require(MachineTicker.processPerTick(recipe, List.of(impossible, new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy(IOType.INPUT, 10)), context, 1, false) != null
                    && energy.getStoredEnergy() == held, "missing intensity fails before FE payment");
            output.load(outputSaved);
            require(MachineTicker.processPerTick(recipe, List.of(out, new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy(IOType.INPUT, Integer.MAX_VALUE)), context, 1, false) != null
                    && output.getStarlightProduction() == 0, "failed resource preflight never publishes starlight");

            var sky = SkyHandler.getContext(level); require(sky != null, "Astral sky handler runs on dedicated server");
            level.setBlock(passivePos, RegistryModularMagicBlocks.blockStarlightProviderInput.get().defaultBlockState(), Block.UPDATE_ALL);
            var passive = (TileStarlightProvider.Input) level.getBlockEntity(passivePos);
            passive.tick(); require(passive.stored() > 0, "open-sky passive input calls actual Astral sky noise/time APIs");
            boolean activeSeen = false, inactiveSeen = false;
            var cst = ConstellationRegistry.getWeakConstellations().stream().findFirst().orElseThrow();
            for (int phase = 0; phase < 8; phase++) {
                level.setDayTime(phase * 24000L + 18000); sky.tick(level);
                boolean expected = sky.getConstellationHandler().isActiveCurrently(cst, MoonPhase.fromWorld(level));
                require(detector.isActive(cst.getRegistryName()) == expected, "detector agrees with real Astral phase query");
                activeSeen |= expected; inactiveSeen |= !expected;
            }
            require(activeSeen && inactiveSeen, "star condition changes across actual moon phases");
            var type = new RequirementConstellation.Type();
            var legacy = type.createRequirement(IOType.INPUT, JsonParser.parseString("{\"constellation\":\"astralsorcery.constellation." + cst.getRegistryName().getPath() + "\"}").getAsJsonObject());
            var snapshotRecipe = new MachineRecipe("astral_regression", "snapshot", 2, List.of(in, out, legacy), null, false, -1, "");
            var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(snapshotRecipe, Map.of()));
            require(decoded.requirements().get(0) instanceof RequirementStarlight r && r.amount() == 8000
                    && decoded.requirements().get(2) instanceof RequirementConstellation, "source IDs, legacy constellation name and network snapshot");
            for (String bad : List.of("{\"amount\":-1}", "{\"amount\":1e1000}", "{\"amount\":\"5\"}")) {
                try { new RequirementStarlight.Type().createRequirement(IOType.INPUT, JsonParser.parseString(bad).getAsJsonObject()); throw new AssertionError("bad starlight JSON accepted"); }
                catch (JsonParseException expected) { }
            }
            try { type.createRequirement(IOType.OUTPUT, JsonParser.parseString("{\"constellation\":\"discidia\"}").getAsJsonObject()); throw new AssertionError("output constellation accepted"); }
            catch (JsonParseException expected) { }
            checkControllers(level, origin, machine, context, recipe);
            require(output.tryUnlink(null, input.getBlockPos()) && input.getLinkedPositions().isEmpty()
                    && !handler.getOutgoingTargets(output.getBlockPos()).contains(input.getBlockPos()), "unlink clears graph and both saved endpoint records");
            Modularmachinery.LOG.info("[MMCE][astral-regression] PASS: native network/link/unload, intensity decay/cap/NBT, no batch consumption, output conversion, preflight protection, moon phases, JSON/snapshots and ordinary/factory lifecycle");
        } finally {
            level.setDayTime(time); var sky = SkyHandler.getContext(level); if (sky != null) sky.tick(level);
            level.setBlock(passivePos, passiveBefore, Block.UPDATE_ALL);
            level.setBlock(origin.offset(2, 1, 0), roofBefore, Block.UPDATE_ALL);
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static void checkControllers(ServerLevel level, BlockPos origin, DynamicMachine machine, MachineContext context, MachineRecipe recipe) throws Exception {
        var input = (TileStarlightProvider.Input) level.getBlockEntity(origin.offset(2, 0, 0)); input.receiveStarlight(50, null);
        var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
        var tick = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
        var fstart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
        var ftick = FactoryTicker.class.getDeclaredMethod("tickThread", hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
        for (var m : List.of(start, tick, fstart, ftick)) m.setAccessible(true);
        var controller = (TileController) level.getBlockEntity(origin); controller.setMachineName(machine.getRegistryName());
        start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe)); require(controller.hasActiveRecipe(), "ordinary controller starts starlight recipe");
        for (int i = 0; i < 3 && controller.hasActiveRecipe(); i++) tick.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
        require(!controller.hasActiveRecipe(), "ordinary controller completes starlight recipe");
        var factory = (TileFactoryController) level.getBlockEntity(origin.offset(1, 0, 0)); factory.setMachineName(machine.getRegistryName()); factory.setFactoryCeiling(2, 2);
        fstart.invoke(null, factory, machine, context, List.of(recipe));
        var active = factory.getFactoryRecipeThreadList().stream().filter(hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
        fstart.invoke(null, factory, machine, context, List.of(recipe)); require(factory.getActiveThreadCount() == 1, "factory does not duplicate same recipe into another thread");
        for (int i = 0; i < 3 && active.hasActiveRecipe(); i++) ftick.invoke(null, active, factory, machine, context, List.of(recipe));
        require(!active.hasActiveRecipe() && input.stored() == 10000, "factory finishes without consuming intensity");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
