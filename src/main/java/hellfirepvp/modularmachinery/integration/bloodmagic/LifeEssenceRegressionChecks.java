/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import wayoftime.bloodmagic.core.data.*;
import wayoftime.bloodmagic.common.item.BloodMagicItems;
import java.nio.file.*;
import java.util.*;

/** Real Blood Magic soul network and ordinary/factory life essence recipe lifecycles. */
public final class LifeEssenceRegressionChecks {
    private LifeEssenceRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(),
                RegistryModularMagicBlocks.blockLifeEssenceProviderInput.get(), RegistryModularMagicBlocks.blockLifeEssenceProviderOutput.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        String id = "lifeessence_regression_" + UUID.randomUUID().toString().replace("-", "");
        Path machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        Path recipeFile = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        try {
            for (int i = 0; i < blocks.length; i++) { before[i] = level.getBlockState(origin.offset(i, 0, 0)); level.setBlock(origin.offset(i, 0, 0), blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            var input = (TileLifeEssenceProvider) level.getBlockEntity(origin.offset(2, 0, 0));
            var output = (TileLifeEssenceProvider) level.getBlockEntity(origin.offset(3, 0, 0));
            require(input != null && output != null && input.capacity() == 0, "real tiles and no capacity without an orb");
            input.load(new net.minecraft.nbt.CompoundTag());
            require(input.orbInventory().getSlots() == 1 && input.capacity() == 0, "empty legacy NBT keeps the orb slot");
            var orb = new net.minecraft.world.item.ItemStack(BloodMagicItems.MASTER_BLOOD_ORB.get());
            var owner = UUID.randomUUID();
            orb.getOrCreateTag().put("binding", new Binding(owner, "MMCE regression").serializeNBT());
            input.orbInventory().setStackInSlot(0, orb.copy()); output.orbInventory().setStackInSlot(0, orb.copy());
            var network = input.network();
            require(network != null && input.capacity() == 100000 && input.orbCapacity() == 1000000, "real bound orb resolves network and source one-tenth capacity");
            network.setCurrentEssence(200000); input.tick();
            require(input.amount() == 100000 && network.getCurrentEssence() == 100000, "network refill pays LP exactly once without damaging owner");
            input.tick(); require(network.getCurrentEssence() == 100000, "full buffer never syphons twice");
            output.add(1000, false); network.setCurrentEssence(1000000); output.tick();
            require(output.amount() == 1000, "full soul network preserves rejected LP");
            network.setCurrentEssence(999600); output.tick();
            require(output.amount() == 600 && network.getCurrentEssence() == 1000000, "partial network return keeps remainder");
            network.setCurrentEssence(0); output.tick(); require(output.amount() == 0 && network.getCurrentEssence() == 600, "remaining LP returns exactly");
            var saved = input.saveWithoutMetadata(); input.take(Long.MAX_VALUE, false); input.load(saved);
            require(input.amount() == 100000 && input.network() == network, "orb binding and cached LP persist");
            input.orbInventory().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY); input.tick();
            require(input.amount() == 100000 && input.capacity() == 0, "removing orb preserves paid cache");
            input.orbInventory().setStackInSlot(0, orb.copy());
            require(!input.orbInventory().isItemValid(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE))
                    && !input.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent(), "orb slot rejects ordinary items and never supplies recipe ingredients");
            var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LPMenuTest"));
            player.setPos(origin.getX(), origin.getY(), origin.getZ());
            var menu = MenuLifeEssence.forTile(0, player.getInventory(), input);
            require(menu.slots.size() == 37 && menu.stillValid(player) && menu.bound() && menu.amount() == 100000,
                    "orb menu binds real tile, slots, owner status and full integer data");
            require(!menu.quickMoveStack(player, 0).isEmpty() && input.orbInventory().getStackInSlot(0).isEmpty(), "orb shift-click returns to player");
            int orbSlot = -1;
            for (int i = 1; i < menu.slots.size(); i++) if (menu.getSlot(i).hasItem()) { orbSlot = i; break; }
            require(orbSlot > 0 && !menu.quickMoveStack(player, orbSlot).isEmpty()
                    && input.orbInventory().getStackInSlot(0).getCount() == 1, "orb shift-click returns to provider with limit one");
            require(input.getBreakInventory().getStackInSlot(0).hasTag(), "break inventory preserves bound orb NBT");
            Files.writeString(machineFile, "{\"parallelizable\":true,\"parallelism\":2,\"parts\":[{\"x\":1,\"elements\":\"minecraft:stone\"}]}");
            var source = JsonParser.parseString("""
                    {"recipeTime":2,"parallelized":true,"requirements":[
                      {"type":"modularmachinery:lifeessence","io-type":"input","amount":100},
                      {"type":"modularmachinery:lifeessence","io-type":"output","amount":40}]}
                    """).getAsJsonObject();
            source.addProperty("machine", id); source.addProperty("registryName", id);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var tick = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var fstart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            var ftick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            for (var m : List.of(start, tick, fstart, ftick)) m.setAccessible(true);
            var controller = (TileController) level.getBlockEntity(origin); controller.setMachineName(id);
            var factory = (TileFactoryController) level.getBlockEntity(origin.offset(1, 0, 0)); factory.setMachineName(id);
            for (boolean rate : List.of(false, true)) for (boolean useFactory : List.of(false, true)) {
                source.getAsJsonArray("requirements").forEach(r -> r.getAsJsonObject().addProperty("perTick", rate));
                Files.writeString(recipeFile, source.toString()); MachineReloadListener.reloadNow();
                var machine = MachineReloadListener.getLoader().findByName(id); var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
                require(recipe != null && recipe.requirements().size() == 2, "source LP JSON loads");
                var remote = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
                require(remote.requirements().get(0) instanceof RequirementLifeEssence r && r.rate() == rate && r.amount() == 100, "LP snapshot retains amount and perTick");
                input.take(Long.MAX_VALUE, false); input.add(2000, false); output.take(Long.MAX_VALUE, false);
                var context = new MachineContext(level, useFactory ? origin.offset(1, 0, 0) : origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
                context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos()));
                FactoryRecipeThread active = null;
                if (useFactory) {
                    factory.setFactoryCeiling(2, 2); fstart.invoke(null, factory, machine, context, List.of(recipe));
                    active = factory.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                    fstart.invoke(null, factory, machine, context, List.of(recipe));
                    require(factory.getActiveThreadCount() == 1 && active.getParallelism() == 2, "factory keeps distinct recipe and batch policy");
                } else {
                    start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                    require(controller.hasActiveRecipe() && controller.getParallelism() == 2, "ordinary LP recipe starts with batch");
                }
                require(input.amount() == (rate ? 2000 : 1800), "rate is not prepaid and one-time input is paid once");
                for (int n = 0; n < 3; n++) {
                    if (useFactory) { if (active.hasActiveRecipe()) ftick.invoke(null, active, factory, machine, context, List.of(recipe)); }
                    else if (controller.hasActiveRecipe()) tick.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                }
                require(input.amount() == (rate ? 1600 : 1800) && output.amount() == (rate ? 160 : 80), "ordinary/factory one-time and per-tick output counts");
                var demands = List.<Requirement>of(new RequirementLifeEssence.Rate(IOType.INPUT, 1000), new RequirementLifeEssence.Rate(IOType.INPUT, 1000));
                require(MachineTicker.processPerTick(recipe, demands, context, 1, false) != null && input.amount() == (rate ? 1600 : 1800), "shared rate shortage never partly consumes LP");
                output.add(Long.MAX_VALUE, false); int held = input.amount();
                require(MachineTicker.processPerTick(recipe, List.of(new RequirementLifeEssence.Rate(IOType.INPUT, 10), new RequirementLifeEssence.Rate(IOType.OUTPUT, 10)), context, 1, false) != null
                        && input.amount() == held, "output-full rate preflight preserves input");
            }
            var machine = MachineReloadListener.getLoader().findByName(id);
            var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of(input.getBlockPos(), output.getBlockPos()));
            class CountedOutput extends RequirementLifeEssence {
                int placed;
                CountedOutput(int amount) { super(IOType.OUTPUT, amount); }
                @Override public int produce(MachineContext ctx, int copies) { return placed = super.produce(ctx, copies); }
            }
            var discardable = new CountedOutput(20); discardable.setIgnoreOutputCheck(true);
            var required = new CountedOutput(10);
            var outputs = List.<Requirement>of(discardable, required);
            output.take(Long.MAX_VALUE, false); output.add(output.capacity() - 10, false);
            require(required.outputFailure(context, outputs, 1) == null && required.getMaxParallelism(context, 5, outputs) == 1,
                    "discardable LP never steals mandatory output reservation or reduces its batch");
            MachineTicker.emitOutputs(recipe, outputs, context, 1);
            require(required.placed == 10 && discardable.placed == 0 && output.amount() == output.capacity(),
                    "mandatory LP emits before a leading discardable output");
            var discardedRate = new RequirementLifeEssence.Rate(IOType.OUTPUT, 20); discardedRate.setIgnoreOutputCheck(true);
            output.take(10, false);
            require(MachineTicker.processPerTick(recipe, List.of(discardedRate, new RequirementLifeEssence.Rate(IOType.OUTPUT, 10)), context, 1, false) == null
                            && output.amount() == output.capacity(), "rate outputs reserve mandatory LP before discardable LP");
            var parser = new LifeEssenceRequirementType();
            for (String invalid : List.of("{\"amount\":-1}", "{\"amount\":1.5}", "{\"amount\":2147483648}", "{\"amount\":1,\"perTick\":\"yes\"}")) {
                try { parser.createRequirement(IOType.INPUT, JsonParser.parseString(invalid).getAsJsonObject()); throw new AssertionError("invalid LP JSON accepted"); }
                catch (JsonParseException expected) {}
            }
            Modularmachinery.LOG.info("[MMCE][lifeessence-regression] PASS: native Blood Magic orb/soul network refill and partial returns, capacity/NBT, source JSON and snapshots, ordinary+factory batch counts, one-shot and per-tick IO, shared shortage/output-full no partial LP payment, mandatory output priority over discardable output");
        } finally {
            Files.deleteIfExists(machineFile); Files.deleteIfExists(recipeFile); MachineReloadListener.reloadNow();
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
