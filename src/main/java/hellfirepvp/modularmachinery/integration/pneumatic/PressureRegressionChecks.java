/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real PNC handlers, pressure tubes and MMC ordinary/factory execution. */
public final class PressureRegressionChecks {
    private PressureRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines(); var oldMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var oldRecipes = new LinkedHashMap<>(recipes);
        try {
            BlockPos pos = origin.atY(220);
            for (String id : List.of("pressure_input", "pressure_output"))
                require(level.getRecipeManager().byKey(new ResourceLocation("modularmachinery", id)).isPresent(), "conditional crafting missing: " + id);
            checkParsing();
            checkTemperature(level, pos.offset(0,0,20), saved);
            place(level, pos, RegistryBlocks.pressureInput.get(), saved);
            place(level, pos.offset(0,0,8), RegistryBlocks.pressureOutput.get(), saved);
            var input = (TilePressureHatch) level.getBlockEntity(pos);
            var output = (TilePressureHatch) level.getBlockEntity(pos.offset(0,0,8));
            var cap = input.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY);
            require(cap.orElseThrow(IllegalStateException::new) == input.air() && input.air().getVolume() == 16000
                    && input.air().getDangerPressure() == 20 && input.air().getCriticalPressure() == 25, "native tier/capability/volume");
            input.air().addAir(32000); input.tick();
            require(input.air().getAir() == 32000 && input.air().getSideLeaking() == null, "input leaked without a pipe");
            require(input.take(100,2,true)==100 && input.air().getAir()==32000 && input.take(100,3,false)==0, "simulate/pressure threshold");
            output.add(32000,false); output.tick();
            require(output.air().getAir()<32000 && output.air().getSideLeaking()==Direction.UP, "disconnected output did not vent natively");
            Block tube = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pneumaticcraft", "advanced_pressure_tube"));
            require(tube != null && tube != Blocks.AIR, "real advanced tube missing");
            place(level,pos.above(),tube,saved); input.neighborsChanged();
            var tubeCap=level.getBlockEntity(pos.above()).getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY,Direction.DOWN)
                    .orElseThrow(IllegalStateException::new);
            int total=input.air().getAir()+tubeCap.getAir();
            input.tick();
            require(tubeCap.getAir()>0 && input.air().getAir()+tubeCap.getAir()==total,"native tube balance lost air or did not connect");
            place(level,pos.above(),Blocks.AIR,saved); input.neighborsChanged();
            input.tick();require(input.air().getConnectedAirHandlers(input).isEmpty(),"removed tube retained a connection");
            input.air().setPressure(-0.5f);
            require(input.meetsPressure(-0.25f) && !input.meetsPressure(-0.75f) && input.take(100,-0.25f,true)==100,"vacuum comparison/demand");
            input.air().setPressure(-1);require(input.take(100,-0.5f,false)==0,"hard vacuum created negative air below -1 bar");
            input.air().setPressure(2);input.setControllerPos(pos.below());
            var disk=input.saveWithoutMetadata();var update=input.getUpdateTag();
            var copy=new TilePressureHatch(PressureRegistration.INPUT.get(),pos,input.getBlockState(),true);copy.load(update);
            require(copy.air().getAir()==32000 && pos.below().equals(copy.getControllerPos()),"sync air/controller binding");
            require(CompletableFuture.supplyAsync(()->input.take(100,0,false)==0 && input.add(100,false)==0).join(),"async mutation allowed");
            input.invalidateCaps();require(!cap.isPresent(),"invalidated cap stayed live");input.reviveCaps();
            require(input.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY).isPresent(),"cap not revived");
            level.removeBlockEntity(pos);var restored=new TilePressureHatch(PressureRegistration.INPUT.get(),pos,input.getBlockState(),true);
            level.setBlockEntity(restored);restored.load(disk);
            require(restored.air().getAir()==32000 && input.take(1,0,false)==0 && input.add(1,false)==0,"disk reload/stale old instance");
            var malformed=new CompoundTag();var air=new CompoundTag();air.putInt("Air",Integer.MIN_VALUE);malformed.put("pneumaticAir",air);restored.load(malformed);
            require(restored.air().getAir()==-16000,"saved vacuum lower bound");
            checkUpgrades(level, pos, restored);
            checkRecipes(level,pos.offset(0,0,32),saved);
            if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) checkScript();
            Modularmachinery.LOG.info("[MMC][pressure] PASS: native tier-two 16000mL, real tube balance/conservation and disconnect, output native leak, pressure/vacuum, simulation/NBT/capability/async/stale guards, strict JSON/snapshots, ordinary+factory 2x one-shot/rate jobs, shared shortage/full-output no partial payment, pressure-only conditions and selectors");
        } finally {
            saved.forEach((pos,state)->level.setBlock(pos,state,Block.UPDATE_ALL));
            machines.clear();machines.putAll(oldMachines);recipes.clear();recipes.putAll(oldRecipes);MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkTemperature(ServerLevel level, BlockPos pos, Map<BlockPos,BlockState> saved) {
        place(level,pos,RegistryBlocks.pressureInput.get(),saved);
        place(level,pos.east(),RegistryBlocks.pressureInput.get(),saved);
        var hot=(TilePressureHatch)level.getBlockEntity(pos);var cold=(TilePressureHatch)level.getBlockEntity(pos.east());
        hot.heat().setTemperature(600);cold.heat().setTemperature(300);hot.tick();
        require(cold.heat().getTemperature()>300 && hot.heat().getTemperature()<600,"native reciprocal heat exchange");
        var heatCap=cold.getCapability(PNCCapabilities.HEAT_EXCHANGER_CAPABILITY);
        cold.invalidateCaps();double removed=cold.heat().getTemperature();
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime((level.getGameTime()/20+1)*20);hot.tick();
        require(!heatCap.isPresent() && cold.heat().getTemperature()==removed && !cold.live(),"stale heat linkage/capability");
        cold.reviveCaps();cold.neighborsChanged();hot.neighborsChanged();
        hot.heat().setTemperature(500.9);
        var machine=new DynamicMachine("temperature_probe","Temperature",new MachinePattern());
        var ctx=new MachineContext(level,pos,Direction.NORTH,machine,new MachineVariables(),new MachinePattern());
        ctx.overrideComponentPositions(List.of(pos));
        var bound=new RequirementTemperature(500,500);
        require(bound.canStart(ctx) && bound.planTick(ctx,List.of(bound),64).execute(),"integer inclusive temperature or batch scaling");
        hot.heat().setTemperature(499.9);hot.air().setPressure(2);int air=hot.air().getAir();
        require(!bound.canStart(ctx) && MachineTicker.processPerTick(null,List.of(new RequirementCompressedAir.Rate(IOType.INPUT,0,10),bound),ctx,1,false)!=null && hot.air().getAir()==air,"temperature failure paid per-tick input");
        hot.heat().setTemperature(700.5);var disk=hot.saveWithoutMetadata();var copy=new TilePressureHatch(PressureRegistration.INPUT.get(),pos,hot.getBlockState(),true);copy.load(disk);
        require(copy.heat().getTemperature()==700.5,"temperature persistence");copy.load(new CompoundTag());require(copy.heat().getTemperature()==300,"old save temperature default");
        disk.putDouble("pneumaticTemperature",Double.NaN);copy.load(disk);require(copy.heat().getTemperature()==300,"non-finite saved temperature");
        var recipe=new MachineRecipe("temperature_probe","temperature",1,List.of(bound),null,false,-1,"");
        require(RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe,Map.of())).requirements().get(0) instanceof RequirementTemperature decoded && decoded.minimum()==500 && decoded.maximum()==500,"temperature snapshot");
        for(String json:List.of("{}","{\"min\":-1}","{\"min\":501,\"max\":500}","{\"min\":1.5}","{\"min\":\"500\"}","{\"max\":2147483648}")) {
            try {new RequirementTemperature.Type().createRequirement(IOType.INPUT,JsonParser.parseString(json).getAsJsonObject());throw new AssertionError("invalid temperature accepted: "+json);}catch(JsonParseException expected){}
        }
        Modularmachinery.LOG.info("[MMC][temperature] PASS: native reciprocal heat, stale unlink/capability, integer inclusive bounds, batch invariance, per-tick no debit, saved/old/malformed NBT, snapshot and strict JSON");
    }
    private static void checkUpgrades(ServerLevel level, BlockPos pos, TilePressureHatch hatch) {
        Item volume = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft", "volume_upgrade"));
        Item security = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pneumaticcraft", "security_upgrade"));
        require(volume != null && volume != Items.AIR && security != null && security != Items.AIR, "native upgrade items");
        var registry = me.desht.pneumaticcraft.api.PneumaticRegistry.getInstance().getUpgradeRegistry();
        require(registry.getMaxUpgrades(hatch, ((me.desht.pneumaticcraft.api.upgrade.IUpgradeItem)volume).getUpgradeType()) == 64,
                "native applicable-upgrade registration");
        var inventory = hatch.upgrades();
        hatch.air().setPressure(2);
        require(inventory.insertItem(0,new ItemStack(volume,4),true).isEmpty() && hatch.air().getVolume()==16000 && hatch.air().getAir()==32000,"simulated upgrade changed air/volume");
        inventory.insertItem(0,new ItemStack(volume,4),false);
        require(hatch.air().getVolume()==64000 && hatch.air().getAir()==32000 && hatch.air().getPressure()==.5f,"native four-volume formula/air preservation");
        require(!inventory.isItemValid(1,new ItemStack(volume)) && !inventory.isItemValid(1,new ItemStack(Items.DIRT)),"duplicate/unsupported upgrade accepted");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                UUID.fromString("ca8cc7de-4d5e-47bc-aeea-2f78292016de"), "mmc_pressure"));
        player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5); player.getInventory().clearContent();
        var menu=MenuPressureHatch.forTile(77,player.getInventory(),hatch);
        require(menu.slots.size()==40 && menu.stillValid(player),"upgrade menu geometry/liveness");
        int before=hatch.air().getAir();
        require(menu.getSlot(0).getMaxStackSize(new ItemStack(volume))==64 && hatch.air().getAir()==before && hatch.air().getVolume()==64000,"slot limit probe vented air");
        player.getInventory().setItem(9,new ItemStack(security,2));
        menu.quickMoveStack(player,4);
        require(hatch.upgradeCount("security")==1 && player.getInventory().getItem(9).getCount()==1,"shift insert security cap");
        player.getInventory().setItem(10,new ItemStack(volume,2));menu.quickMoveStack(player,5);
        require(hatch.upgradeCount("volume")==6 && hatch.air().getAir()==before && hatch.air().getVolume()==me.desht.pneumaticcraft.api.pressure.PressureHelper.getUpgradedVolume(16000,6),"shift merge volume did not recalculate");
        hatch.air().setPressure(-.5f);
        var disk=hatch.saveWithoutMetadata();
        var copy=new TilePressureHatch(PressureRegistration.INPUT.get(),pos,hatch.getBlockState(),true);copy.load(disk);
        require(copy.upgradeCount("volume")==6 && copy.upgradeCount("security")==1 && copy.air().getAir()==hatch.air().getAir() && copy.air().getVolume()==hatch.air().getVolume(),"upgraded vacuum NBT lost air/upgrades");
        copy.load(disk);require(copy.air().getAir()==hatch.air().getAir(),"repeated NBT refresh drained air");
        var wire=MenuPressureHatch.dataFor(hatch);var received=new net.minecraft.world.inventory.SimpleContainerData(7);
        var source=new hellfirepvp.modularmachinery.common.menu.IntContainerData(wire);var target=new hellfirepvp.modularmachinery.common.menu.IntContainerData(received);
        for(int i=0;i<source.getCount();i++)target.set(i,(short)source.get(i));
        require(received.get(0)==hatch.air().getAir() && received.get(1)==hatch.air().getVolume() && received.get(4)==1 && received.get(6)==hatch.heat().getTemperatureAsInt(),"signed-short menu sync truncated upgraded vacuum");
        hatch.air().setPressure(26);hatch.tick();
        require(level.getBlockEntity(pos)==hatch && hatch.air().getPressure()<=25 && hatch.air().getAir()>0,"security upgrade failed native critical clamp");
        hatch.air().setPressure(21);before=hatch.air().getAir();hatch.tick();
        require(hatch.air().getAir()<before,"security upgrade did not vent above danger");
        hatch.air().setPressure(2);
        float pressure=hatch.air().getPressure();menu.quickMoveStack(player,0);
        require(hatch.air().getVolume()==16000 && Math.abs(hatch.air().getPressure()-pressure)<.001,"removing volume did not retain native pressure");
        // No-security hatches retain normal native behaviour, not a permanent clamp after extraction.
        inventory.extractItem(1,1,false);hatch.air().setPressure(2);before=hatch.air().getAir();hatch.tick();
        require(hatch.air().getAir()==before,"security removal left safety vent enabled at safe pressure");
        inventory.insertItem(0,new ItemStack(volume,64),false);inventory.insertItem(1,new ItemStack(security),false);
        require(hatch.air().getVolume()==256000,"64 volume cap");
        hatch.air().setPressure(20);
        for(int i=0;i<source.getCount();i++)target.set(i,(short)source.get(i));
        require(received.get(0)==5120000 && received.get(1)==256000,"menu sync truncated large positive air/volume");
        // A saved inventory cannot resize the hatch or smuggle in unsupported/duplicate upgrades.
        var bad=hatch.saveWithoutMetadata();var upgrades=bad.getCompound("pressureUpgrades");upgrades.putInt("Size",9999);
        var list=upgrades.getList("Items",10);var duplicate=new ItemStack(volume,64).save(new CompoundTag());duplicate.putInt("Slot",2);list.add(duplicate);
        var unsupported=new ItemStack(Items.DIRT).save(new CompoundTag());unsupported.putInt("Slot",3);list.add(unsupported);
        copy.load(bad);require(copy.upgrades().getSlots()==4 && copy.upgradeCount("volume")==64 && copy.upgrades().getStackInSlot(2).isEmpty() && copy.upgrades().getStackInSlot(3).isEmpty(),"untrusted upgrade NBT accepted");
        var entities=new HashSet<>(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)));
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        int droppedVolume=0,droppedSecurity=0;
        var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2));
        for(var entity:drops)if(!entities.contains(entity)){if(entity.getItem().is(volume))droppedVolume+=entity.getItem().getCount();if(entity.getItem().is(security))droppedSecurity+=entity.getItem().getCount();entity.discard();}
        require(droppedVolume==64 && droppedSecurity==1 && !menu.stillValid(player),"break lost/duplicated upgrades or stale menu survived");
        player.getInventory().clearContent();
        Modularmachinery.LOG.info("[MMC][pressure-upgrades] PASS: four slots, native applicability/formula, simulation and pure slot-limit queries, duplicate/type/cap rules, real shift insertion/merge/removal, upgraded vacuum NBT, 32-bit menu sync, native safety vent/critical clamp, fixed-size saved inventory and exact break drops");
    }
    private static void checkRecipes(ServerLevel level,BlockPos origin,Map<BlockPos,BlockState> saved) {
        for(boolean factory:List.of(false,true)) for(boolean rate:List.of(false,true)) {
            BlockPos pos=origin.offset(factory?16:0,0,rate?8:0);
            place(level,pos,factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get(),saved);
            level.setBlock(pos,level.getBlockState(pos).setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING,Direction.NORTH),Block.UPDATE_ALL);
            Block[] blocks={RegistryBlocks.itemInputBus.get(),RegistryBlocks.itemOutputBus.get(),RegistryBlocks.pressureInput.get(),RegistryBlocks.pressureOutput.get()};
            var pattern=new MachinePattern();
            for(int i=0;i<blocks.length;i++){place(level,pos.offset(i+1,0,0),blocks[i],saved);pattern.addPart(new MachinePattern.Part(i+1,0,0,ForgeRegistries.BLOCKS.getKey(blocks[i]).toString(),i<3?"in":"out"));}
            var machine=new DynamicMachine("pressure_job","Pressure job",pattern);machine.setMaxThreads(1);machine.setParallelizable(true);machine.setInternalParallelism(2);machine.setMaxParallelism(2);
            MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery","pressure_job"),machine);
            var controller=(TileController)level.getBlockEntity(pos);controller.setMachineName("pressure_job");
            var input=(TilePressureHatch)level.getBlockEntity(pos.offset(3,0,0));var output=(TilePressureHatch)level.getBlockEntity(pos.offset(4,0,0));input.air().setPressure(2);
            var items=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.offset(1,0,0))).getInventory();
            var products=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.offset(2,0,0))).getInventory();items.setStackInSlot(0,new ItemStack(Items.COAL,2));
            RequirementCompressedAir consume=rate?new RequirementCompressedAir.Rate(IOType.INPUT,1,100):new RequirementCompressedAir(IOType.INPUT,1,100);
            RequirementCompressedAir produce=rate?new RequirementCompressedAir.Rate(IOType.OUTPUT,0,40):new RequirementCompressedAir(IOType.OUTPUT,0,40);
            consume.setSelectorTag("in");produce.setSelectorTag("out");
            var temperature=new RequirementTemperature(400,600);temperature.setSelectorTag("in");input.heat().setTemperature(300);
            var recipe=new MachineRecipe("pressure_job","pressure_batch",3,List.of(new RequirementItem(IOType.INPUT,"minecraft:coal",1),new RequirementItem(IOType.OUTPUT,"minecraft:diamond",1),consume,produce,temperature),null,false,-1,"");
            MachineReloadListener.getRecipeLoader().getByMachine().put("pressure_job",List.of(recipe));
            require(MachineFormation.tryForm(level,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"machine formation");controller.setFormed(true);
            if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);
            require(items.getStackInSlot(0).getCount()==2 && input.air().getAir()==32000,"cold start consumed inputs");
            input.heat().setTemperature(500);
            // A failed start uses the normal search retry delay; advancing game time avoids hiding it.
            for(int i=0;i<120 && items.getStackInSlot(0).getCount()==2;i++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
                if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);
            }
            require(items.getStackInSlot(0).isEmpty() && products.getStackInSlot(0).isEmpty(),"temperature start did not schedule job");
            input.heat().setTemperature(300);int pausedAir=input.air().getAir();
            if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);
            require(input.air().getAir()==pausedAir && products.getStackInSlot(0).isEmpty(),"cold running job paid rate or completed");
            input.heat().setTemperature(500);
            for(int i=0;i<12 && products.getStackInSlot(0).isEmpty();i++){if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);}
            require(products.getStackInSlot(0).is(Items.DIAMOND) && products.getStackInSlot(0).getCount()==2 && items.getStackInSlot(0).isEmpty()
                    && input.air().getAir()==(rate?31400:31800) && output.air().getAir()==(rate?240:80),"actual 2x pressure job: factory="+factory+", rate="+rate+", input="+input.air().getAir()+", output="+output.air().getAir());
            var ctx=new MachineContext(level,pos,Direction.NORTH,machine,new MachineVariables(),pattern);
            require(!CompletableFuture.supplyAsync(() -> new RequirementCompressedAir(IOType.INPUT,1,1).canStart(ctx)).join(), "async requirement world access");
            var decoded=RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe,Map.of()));
            require(decoded.requirements().get(2) instanceof RequirementCompressedAir remote && remote.rate()==rate && remote.pressure()==1 && "in".equals(remote.getSelectorTag()),"snapshot pressure/rate/selector");
            int before=input.air().getAir();output.add(Long.MAX_VALUE,false);
            require(MachineTicker.processPerTick(recipe,List.of(new RequirementCompressedAir.Rate(IOType.INPUT,1,100),new RequirementCompressedAir.Rate(IOType.OUTPUT,0,40)),ctx,1,false)!=null
                    && input.air().getAir()==before,"full output partially paid input");
            require(!RequirementCompressedAir.plan(ctx,List.of(new RequirementCompressedAir(IOType.INPUT,1,20000),new RequirementCompressedAir(IOType.INPUT,1,20000)),1,IOType.INPUT,false).execute()
                    && input.air().getAir()==before,"shared shortage partially paid input");
            require(new RequirementCompressedAir(IOType.INPUT,1,0).canStart(ctx),"zero-air pressure condition failed");
            var selector=new RequirementCompressedAir(IOType.INPUT,0,1);selector.setSelectorTag("out");require(!selector.canStart(ctx),"selector ignored IO direction");
            BlockPos far=new BlockPos(24000000,220,24000000);ctx.overrideComponentPositions(List.of(far));
            require(!level.hasChunkAt(far) && !new RequirementCompressedAir(IOType.INPUT,0,1).canStart(ctx) && !level.hasChunkAt(far), "query forced unloaded chunk");
        }
    }
    private static void checkParsing() {
        for(String value:List.of("{}","{\"pressure\":1,\"air\":-1}","{\"pressure\":1,\"air\":1.5}","{\"pressure\":1,\"air\":2147483648}","{\"pressure\":26}","{\"pressure\":-2}","{\"pressure\":\"2\"}","{\"pressure\":1,\"perTick\":\"true\"}")) {
            try { new RequirementCompressedAir.Type(false).createRequirement(IOType.INPUT,JsonParser.parseString(value).getAsJsonObject());throw new AssertionError("invalid air JSON accepted: "+value); }
            catch(JsonParseException expected) { }
        }
        require(new RequirementCompressedAir.Type(true).createRequirement(IOType.INPUT,JsonParser.parseString("{\"pressure\":2}").getAsJsonObject()) instanceof RequirementCompressedAir.Rate,"legacy per-tick type");
    }
    private static void checkScript() {
        var recipe=MachineReloadListener.getRecipeLoader().getRecipe("mmc_pressure_fixture");
        // The full integration fixture is required, so compileOnly success cannot mask ZenCode errors.
        require(recipe!=null && recipe.requirements().stream().filter(r->r instanceof RequirementCompressedAir).count()==4,"actual ZenCode fixture registration");
        require(recipe.requirements().stream().anyMatch(r->r instanceof RequirementTemperature t && t.minimum()==400 && t.maximum()==600),"actual temperature ZenCode method");
    }
    private static void place(ServerLevel level,BlockPos pos,Block block,Map<BlockPos,BlockState> saved){saved.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError("Pressure: "+message);}
}
