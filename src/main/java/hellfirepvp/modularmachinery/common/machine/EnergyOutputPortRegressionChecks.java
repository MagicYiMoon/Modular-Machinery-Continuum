/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.*;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Runs against real world components and tickers, including both recipe controllers. */
public final class EnergyOutputPortRegressionChecks {
    private EnergyOutputPortRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) {
        var saved = new LinkedHashMap<BlockPos,BlockState>();
        long time = level.getGameTime();
        var rangeSetting=hellfirepvp.modularmachinery.common.data.Config.energyOutputPortRangeSetting();int oldRange=rangeSetting.get();rangeSetting.set(3);
        var machines=MachineReloadListener.getLoader().getMachines(); var oldMachines=new LinkedHashMap<>(machines);
        var recipes=MachineReloadListener.getRecipeLoader().getByMachine(); var oldRecipes=new LinkedHashMap<>(recipes);
        try {
            BlockPos pos=origin.atY(230);
            for (Direction facing:Direction.values()) {
                clearRay(level,pos,facing,saved);
                var port=port(level,pos,facing,saved);
                var target=receiver(level,pos.relative(facing,3),facing.getOpposite(),saved);
                require(port.produceInternal(1000,true)==1000 && port.getStoredEnergy()==0,"simulation changed buffer");
                require(port.produceInternal(1000,false)==1000,"recipe deposit");
                for(var side:Direction.values()) require(port.getCapability(ForgeCapabilities.ENERGY,side).isPresent()==(side==facing),"external front face");
                port.tick(); require(target.energy.getEnergyStored()==1000 && port.getStoredEnergy()==0,"distance 3 facing "+facing);
                require(pos.relative(facing,3).equals(port.targetPosition()),"target world position");
                require(port.visualDistance()==3 && port.getRenderBoundingBox().contains(net.minecraft.world.phys.Vec3.atCenterOf(pos.relative(facing,3))),"beam bounds/distance "+facing);
                advance(level,9); port.tick();
                require(port.visualRate()==100,"actual averaged beam rate "+facing+": "+port.visualRate());
                var clientCopy=new TileEnergyOutputPort(pos,port.getBlockState());clientCopy.load(port.getUpdateTag());
                require(clientCopy.visualDistance()==3 && clientCopy.visualRate()==100,"visual update packet");
                require(!port.saveWithoutMetadata().contains("portVisualDistance"),"ephemeral visuals leaked into disk save");
                advance(level,10);port.tick();require(port.visualRate()==0,"idle beam rate did not clear");
                place(level,pos.relative(facing,3),Blocks.AIR.defaultBlockState(),saved);advance(level,10);port.tick();
                require(port.visualDistance()==0 && port.visualRate()==0,"disconnected beam remained visible");
                require(port.getBlockState().getValue(BlockEnergyOutputPort.STATUS)==0,"disconnected blockstate");
                var read=port.getCapability(ForgeCapabilities.ENERGY,facing).orElseThrow(IllegalStateException::new);
                require(!read.canReceive() && read.receiveEnergy(10,false)==0 && read.extractEnergy(-1,false)==0,"external insertion/negative extraction");
                place(level,pos,Blocks.AIR.defaultBlockState(),saved);
            }
            clearRay(level,pos,Direction.EAST,saved);
            var nativePort=port(level,pos,Direction.EAST,saved);
            place(level,pos.east(3),RegistryBlocks.energyInputHatch.get().defaultBlockState(),saved);
            var nativeInput=(TileEnergyInputHatch)level.getBlockEntity(pos.east(3));
            int nativeAccepted=nativeInput.getEnergyStorage().receiveEnergy(500,true);
            nativePort.produceInternal(500,false);nativePort.tick();
            require(nativeAccepted>0 && nativeInput.getStoredEnergy()==nativeAccepted && nativePort.getStoredEnergy()==500-nativeAccepted,"real MMC FE receiver and native transfer limit");
            place(level,pos,Blocks.AIR.defaultBlockState(),saved);
            clearRay(level,pos,Direction.EAST,saved);
            var port=port(level,pos,Direction.EAST,saved);var target=receiver(level,pos.east(3),Direction.WEST,saved);
            port.produceInternal(1000,false);place(level,pos.east(),Blocks.GLASS.defaultBlockState(),saved);port.tick();
            require(target.energy.getEnergyStored()==1000,"transparent block blocked ray");
            port.produceInternal(1000,false);place(level,pos.east(2),Blocks.STONE.defaultBlockState(),saved);advance(level,10);port.tick();
            require(target.energy.getEnergyStored()==1000 && port.getStoredEnergy()==1000 && port.targetPosition()==null,"opaque blocker");
            place(level,pos.east(2),Blocks.CHEST.defaultBlockState(),saved);advance(level,10);port.tick();require(port.getStoredEnergy()==1000,"non-energy tile skipped");
            place(level,pos.east(2),Blocks.AIR.defaultBlockState(),saved);advance(level,10);port.tick();require(port.getStoredEnergy()==0,"unblocked target not recovered");
            // Cached receiver invalidation must not retain a writable old capability.
            port.produceInternal(500,false);target.invalidateCaps();port.tick();require(port.getStoredEnergy()==500,"invalid receiver still writable");
            place(level,pos.east(3),Blocks.AIR.defaultBlockState(),saved);target=receiver(level,pos.east(3),Direction.WEST,saved);advance(level,10);port.tick();require(target.energy.getEnergyStored()==500,"replacement target not found");
            clearRay(level,pos,Direction.EAST,saved);target=receiver(level,pos.east(4),Direction.WEST,saved);advance(level,10);port.tick();
            port.produceInternal(25,false);port.tick();require(target.energy.getEnergyStored()==0 && port.getStoredEnergy()==25,"default range exceeded");
            rangeSetting.set(4);port.tick();require(target.energy.getEnergyStored()==25 && port.getStoredEnergy()==0,"range change did not refresh cached target");rangeSetting.set(3);
            place(level,pos,Blocks.AIR.defaultBlockState(),saved);port=port(level,pos,Direction.EAST,saved);
            clearRay(level,pos,Direction.EAST,saved);target=receiver(level,pos.east(3),null,saved);port.produceInternal(25,false);port.tick();require(target.energy.getEnergyStored()==25,"unsided receiver fallback");
            // Partial acceptance, full target, save/load, and a shared push/pull tick budget.
            place(level,pos.east(3),Blocks.AIR.defaultBlockState(),saved);target=receiver(level,pos.east(3),Direction.WEST,saved);target.energy.receiveEnergy(Integer.MAX_VALUE-123,false);
            advance(level,10);port.produceInternal(Integer.MAX_VALUE,false);port.tick();
            require(port.getStoredEnergy()==Integer.MAX_VALUE-123 && target.energy.getEnergyStored()==Integer.MAX_VALUE,"partial acceptance/conservation");
            int stored=port.getStoredEnergy();int actualCalls=target.actualReceives;advance(level,1);port.tick();require(port.getStoredEnergy()==stored && target.actualReceives==actualCalls,"full receiver discarded output or received a committing call");
            var nbt=port.saveWithoutMetadata();var detached=new TileEnergyOutputPort(pos,port.getBlockState());detached.load(nbt);require(detached.getStoredEnergy()==stored,"persistent buffer");
            nbt.putInt("energy",-1);detached.load(nbt);require(detached.getStoredEnergy()==0,"negative saved buffer");
            var cap=port.getCapability(ForgeCapabilities.ENERGY,Direction.EAST).orElseThrow(IllegalStateException::new);
            if(net.minecraftforge.fml.ModList.get().isLoaded("fluxnetworks"))
                require(!port.getCapability(sonar.fluxnetworks.api.FluxCapabilities.FN_ENERGY_STORAGE).isPresent(),"Flux all-face budget bypass");
            int simulated=cap.extractEnergy(Integer.MAX_VALUE,true);require(simulated==TileEnergyOutputPort.TRANSFER && port.getStoredEnergy()==stored,"external simulation");
            int pulled=cap.extractEnergy(100,false);target.energy.extractEnergy(Integer.MAX_VALUE,false);
            var reentryPort=port;
            target.onReceive=()->require(cap.extractEnergy(1,false)==0 && reentryPort.produceInternal(1,false)==0,"receiving callback reentered port transfer");
            port.tick();target.onReceive=null;
            require(pulled==100 && target.energy.getEnergyStored()==TileEnergyOutputPort.TRANSFER-100 && cap.extractEnergy(1,false)==0,"push/pull bypassed shared tick limit");
            var selected=port;require(CompletableFuture.supplyAsync(()->selected.produceInternal(1,false)==0 && cap.extractEnergy(1,false)==0).join(),"async access");
            var optional=port.getCapability(ForgeCapabilities.ENERGY);port.invalidateCaps();require(!optional.isPresent() && cap.extractEnergy(1,false)==0,"invalidated port");
            port.reviveCaps();advance(level,1);require(cap.extractEnergy(1,false)==0,"revived stale capability");
            var front=port.getCapability(ForgeCapabilities.ENERGY,Direction.EAST).orElseThrow(IllegalStateException::new);
            level.setBlock(pos,port.getBlockState().setValue(BlockEnergyOutputPort.FACING,Direction.WEST),2);
            require(front.extractEnergy(1,false)==0,"rotated cached side capability");
            // A real unloaded chunk immediately in front must remain unloaded.
            BlockPos boundary=null;
            for(int chunk=pos.getX()>>4;chunk<(pos.getX()>>4)+64;chunk++) {
                BlockPos edge=new BlockPos(chunk*16+15,230,pos.getZ());
                if(level.hasChunkAt(edge) && !level.hasChunkAt(edge.east())) {boundary=edge;break;}
            }
            require(boundary!=null,"loaded/unloaded chunk boundary missing");
            saved.putIfAbsent(boundary,level.getBlockState(boundary));
            // Suppress shape propagation as well as neighbour events: shape updates query the next chunk.
            level.setBlock(boundary,RegistryBlocks.energyOutputPort.get().defaultBlockState().setValue(BlockEnergyOutputPort.FACING,Direction.EAST),18);
            require(!level.hasChunkAt(boundary.east()),"test expected unloaded chunk");var border=(TileEnergyOutputPort)level.getBlockEntity(boundary);border.produceInternal(10,false);border.tick();
            require(!level.hasChunkAt(boundary.east()) && border.getStoredEnergy()==10,"scan forced unloaded chunk");
            require(level.getRecipeManager().byKey(new ResourceLocation("modularmachinery","energy_output_port")).isPresent(),"crafting recipe missing");
            checkRecipe(level,pos.offset(0,0,16),false,saved);checkRecipe(level,pos.offset(16,0,16),true,saved);
            Modularmachinery.LOG.info("[MMC][energy-port] PASS: six directions/range/blockers/cache/lifecycle, native FE and fallback, persistent bounded buffer, partial/full conservation, shared push/pull tick limit, six beam bounds/averaged actual rate/update-only NBT/idle/disconnect, async/unloaded guards, actual ordinary+factory 2x output recipes");
        } finally {
            saved.forEach((p,state)->level.setBlock(p,state,Block.UPDATE_ALL));((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time);rangeSetting.set(oldRange);
            machines.clear();machines.putAll(oldMachines);recipes.clear();recipes.putAll(oldRecipes);MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkRecipe(ServerLevel level,BlockPos pos,boolean factory,Map<BlockPos,BlockState> saved) {
        place(level,pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH),saved);
        place(level,pos.east(),RegistryBlocks.itemInputBus.get().defaultBlockState(),saved);place(level,pos.west(),RegistryBlocks.itemOutputBus.get().defaultBlockState(),saved);
        var port=port(level,pos.above(),Direction.EAST,saved);clearRay(level,pos.above(),Direction.EAST,saved);var target=receiver(level,pos.above().east(3),Direction.WEST,saved);
        var pattern=new MachinePattern();pattern.addPart(new MachinePattern.Part(1,0,0,"modularmachinery:blockinputbus"));pattern.addPart(new MachinePattern.Part(-1,0,0,"modularmachinery:blockoutputbus"));pattern.addPart(new MachinePattern.Part(0,1,0,"modularmachinery:blockenergyoutputport","power_out"));
        var machine=new DynamicMachine("port_job","Port job",pattern);machine.setMaxThreads(1);machine.setParallelizable(true);machine.setInternalParallelism(2);machine.setMaxParallelism(2);
        MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery","port_job"),machine);
        var controller=(TileController)level.getBlockEntity(pos);controller.setMachineName("port_job");
        var items=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.east())).getInventory();var products=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.west())).getInventory();items.setStackInSlot(0,new ItemStack(Items.COAL,2));
        var output=new RequirementEnergy(IOType.OUTPUT,250);output.setSelectorTag("power_out");
        var recipe=new MachineRecipe("port_job","port_batch",3,List.of(new RequirementItem(IOType.INPUT,"minecraft:coal",1),new RequirementItem(IOType.OUTPUT,"minecraft:diamond",1),output),null,false,-1,"");
        MachineReloadListener.getRecipeLoader().getByMachine().put("port_job",List.of(recipe));
        require(MachineFormation.tryForm(level,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"machine formation");controller.setFormed(true);
        port.produceInternal(Integer.MAX_VALUE,false);target.energy.receiveEnergy(Integer.MAX_VALUE,false);
        if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);
        require(items.getStackInSlot(0).getCount()==2 && products.getStackInSlot(0).isEmpty() && port.getStoredEnergy()==Integer.MAX_VALUE,"full port consumed recipe inputs");
        port.getEnergyStorage().extractEnergy(Integer.MAX_VALUE,false);target.energy.extractEnergy(Integer.MAX_VALUE,false);
        // A failed full-buffer start uses the controller's normal recipe-search retry delay.
        for(int i=0;i<120 && products.getStackInSlot(0).isEmpty();i++){advance(level,1);if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);port.tick();}
        require(products.getStackInSlot(0).is(Items.DIAMOND) && products.getStackInSlot(0).getCount()==2 && items.getStackInSlot(0).isEmpty() && target.energy.getEnergyStored()==1500 && port.getStoredEnergy()==0,
                "actual ordinary/factory 2x FE recipe factory="+factory+" input="+items.getStackInSlot(0)+" output="+products.getStackInSlot(0)+" received="+target.energy.getEnergyStored()+" buffer="+port.getStoredEnergy());
    }
    private static void advance(ServerLevel level,int ticks){((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime()+ticks);}
    private static TileEnergyOutputPort port(ServerLevel level,BlockPos pos,Direction facing,Map<BlockPos,BlockState> saved){place(level,pos,RegistryBlocks.energyOutputPort.get().defaultBlockState().setValue(BlockEnergyOutputPort.FACING,facing),saved);return (TileEnergyOutputPort)level.getBlockEntity(pos);}
    private static void clearRay(ServerLevel level,BlockPos pos,Direction facing,Map<BlockPos,BlockState> saved){for(int i=1;i<=4;i++)place(level,pos.relative(facing,i),Blocks.AIR.defaultBlockState(),saved);}
    private static Receiver receiver(ServerLevel level,BlockPos pos,Direction side,Map<BlockPos,BlockState> saved){place(level,pos,Blocks.CHEST.defaultBlockState(),saved);level.removeBlockEntity(pos);var tile=new Receiver(pos,side);level.setBlockEntity(tile);return tile;}
    private static void place(ServerLevel level,BlockPos pos,BlockState state,Map<BlockPos,BlockState> saved){saved.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,Block.UPDATE_ALL);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError("Energy port: "+message);}
    private static final class Receiver extends BlockEntity {
        int actualReceives;Runnable onReceive;
        final EnergyStorage energy=new EnergyStorage(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE) {
            @Override public int receiveEnergy(int amount,boolean simulate) {
                if(!simulate) {actualReceives++;if(onReceive!=null)onReceive.run();}
                return super.receiveEnergy(amount,simulate);
            }
        };
        final LazyOptional<IEnergyStorage> cap=LazyOptional.of(()->energy);final Direction side;
        Receiver(BlockPos pos,Direction side){super(BlockEntityType.CHEST,pos,Blocks.CHEST.defaultBlockState());this.side=side;}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction query){return capability==ForgeCapabilities.ENERGY && query==side ? cap.cast():super.getCapability(capability,query);}
        @Override public void invalidateCaps(){super.invalidateCaps();cap.invalidate();}
    }
}
