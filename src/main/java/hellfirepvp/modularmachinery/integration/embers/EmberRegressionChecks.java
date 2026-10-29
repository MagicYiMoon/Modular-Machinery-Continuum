/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.embers;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.blockentity.EmberReceiverBlockEntity;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockEmberHatch;
import hellfirepvp.modularmachinery.common.block.prop.EmberHatchSize;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class EmberRegressionChecks {
    private EmberRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos,BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines(); var oldMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var oldRecipes = new LinkedHashMap<>(recipes);
        try {
            BlockPos pos = origin.atY(220);
            var miner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "EmberMiner"));
            miner.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            miner.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            for (var size : EmberHatchSize.values()) {
                String name=size.getSerializedName();
                var recipe=level.getRecipeManager().byKey(new ResourceLocation("modularmachinery","ember_input_"+name)).orElseThrow();
                require(recipe.getResultItem(level.registryAccess()).getTagElement("BlockStateTag").getString("size").equals(name),"crafting result tier "+name);
                var state=RegistryBlocks.emberInput.get().defaultBlockState().setValue(BlockEmberHatch.SIZE,size);
                require(new BlockStateResolver().resolveAccepted("modularmachinery:blockemberinputhatch@"+size.ordinal()).equals(List.of(state)),"explicit structure tier "+name);
                require(new BlockStateResolver().resolveAccepted("modularmachinery:blockemberinputhatch").contains(state),"unconstrained structure tier "+name);
                place(level,pos,state,saved);var tile=(TileEmberHatch)level.getBlockEntity(pos);
                var cap=tile.getCapability(EmbersCapabilities.EMBER_CAPABILITY).orElseThrow(IllegalStateException::new);
                require(tile.capacity()==size.capacity() && cap.acceptsVolatile()==(size.capacity()>=3200),"tier/volatile rule "+name);
                for(var direction:Direction.values())require(tile.getCapability(EmbersCapabilities.EMBER_CAPABILITY,direction).orElseThrow(IllegalStateException::new)==cap,"six-side native cap");
                require(cap.addAmount(20.25,false)==20.25 && tile.amount()==0,"simulate added Ember");
                require(cap.addAmount(20.25,true)==20.25 && cap.removeAmount(.25,false)==.25 && tile.amount()==20.25,"fractional capability/simulated removal");
                cap.setEmberCapacity(1e30);require(cap.getEmberCapacity()==size.capacity(),"external capacity rewrite");
                double before=tile.amount();
                for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})require(cap.addAmount(bad,true)==0 && cap.removeAmount(bad,true)==0 && tile.amount()==before,"invalid native request");
                require(CompletableFuture.supplyAsync(()->cap.addAmount(1,true)==0 && cap.removeAmount(1,true)==0).join(),"async native mutation");
                tile.setControllerPos(pos.below());var tag=tile.getUpdateTag();var copy=new TileEmberHatch(EmberRegistration.INPUT.get(),pos,state);copy.load(tag);
                require(copy.amount()==20.25 && copy.capacity()==size.capacity() && pos.below().equals(copy.getControllerPos()),"saved amount/capacity/binding");
                tag.putDouble("ember",Double.NaN);copy.load(tag);require(copy.amount()==0,"NaN stored amount");
                tag.putDouble("ember",Double.MAX_VALUE);copy.load(tag);require(copy.amount()==size.capacity(),"oversized stored amount");
                var optional=tile.getCapability(EmbersCapabilities.EMBER_CAPABILITY);tile.invalidateCaps();require(!optional.isPresent() && cap.addAmount(1,true)==0,"invalid capability still writable");tile.reviveCaps();
                require(tile.getCapability(EmbersCapabilities.EMBER_CAPABILITY).isPresent(),"revive capability");
                checkHarvest(level,pos,tile,miner,size);
                // Ensure each tier creates a fresh tile; vanilla retains entities for same-block state changes.
                place(level,pos,Blocks.AIR.defaultBlockState(),saved);
            }
            place(level,pos,RegistryBlocks.emberInput.get().defaultBlockState(),saved);
            var tile=(TileEmberHatch)level.getBlockEntity(pos);
            Block receiver=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("embers","ember_receiver"));
            require(receiver!=null && receiver!=Blocks.AIR,"real receiver missing");
            var receiverState=receiver.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP);
            place(level,pos.above(),receiverState,saved);
            var nativeReceiver=(EmberReceiverBlockEntity)level.getBlockEntity(pos.above());nativeReceiver.capability.addAmount(100,true);
            EmberReceiverBlockEntity.serverTick(level,pos.above(),receiverState,nativeReceiver);EmberReceiverBlockEntity.serverTick(level,pos.above(),receiverState,nativeReceiver);
            require(tile.amount()==10 && nativeReceiver.capability.getEmber()==90,"native receiver transfer/conservation");
            var old=tile;var savedTile=tile.saveWithoutMetadata();level.removeBlockEntity(pos);
            tile=new TileEmberHatch(EmberRegistration.INPUT.get(),pos,level.getBlockState(pos));level.setBlockEntity(tile);tile.load(savedTile);
            require(old.add(1,false)==0 && old.take(1,false)==0 && tile.amount()==10,"stale tile/disk restore");
            checkParsing();checkRecipes(level,pos.offset(0,0,16),saved);
            if(net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                var script=MachineReloadListener.getRecipeLoader().getRecipe("mmc_ember_fixture");
                require(script!=null && script.requirements().stream().anyMatch(r->r instanceof RequirementEmber e && e.amount()==12.25),"real ZenCode fixture");
            }
            var example=machines.get(new ResourceLocation("modularmachinery","ember_lab"));
            if(example!=null) {
                require(example.getPattern()!=null,"example pattern missing");
                require(example.getPattern().getParts().stream().anyMatch(p->"ember_in".equals(p.selectorTag())
                        && new BlockStateResolver().resolveAccepted(p.elements()).equals(List.of(RegistryBlocks.emberInput.get().defaultBlockState()))),"example hatch/selector could not resolve");
                var exampleRecipes=recipes.get("ember_lab");
                require(exampleRecipes!=null && exampleRecipes.stream().filter(r->r.requirements().stream().anyMatch(q->q instanceof RequirementEmber e && e.amount()==12.25)).count()==2,"installed JSON and ZenCode tutorial recipes missing");
                Modularmachinery.LOG.info("[MMC][embers] tutorial PASS: actual machine JSON tier/selector and JSON+ZenCode recipes loaded");
            }
            Modularmachinery.LOG.info("[MMC][embers] PASS: eight tiers and native receiver/conservation, volatile thresholds, fractional simulation/guards/lifecycle/NBT, survival harvest and placement retaining Ember, ordinary+factory batch execution, shared reservation, selector/modifier/exemption/snapshot/JSON and actual CT script");
        } finally {
            saved.forEach((pos,state)->level.setBlock(pos,state,Block.UPDATE_ALL));
            machines.clear();machines.putAll(oldMachines);recipes.clear();recipes.putAll(oldRecipes);MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkHarvest(ServerLevel level,BlockPos pos,TileEmberHatch tile,FakePlayer miner,EmberHatchSize size) {
        miner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));
        var box=new AABB(pos).inflate(3);var previous=new HashSet<UUID>();level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).forEach(e->previous.add(e.getUUID()));
        require(miner.getMainHandItem().isCorrectToolForDrops(tile.getBlockState()) && miner.gameMode.destroyBlock(pos),"survival stone harvest");
        ItemStack drop=ItemStack.EMPTY;int count=0;
        for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box))if(!previous.contains(entity.getUUID())) {
            if(entity.getItem().is(RegistryItems.emberInput.get())) {drop=entity.getItem().copy();count+=drop.getCount();}entity.discard();
        }
        require(count==1 && drop.getTag().getDouble("storedEmber")==20.25 && size.getSerializedName().equals(drop.getTagElement("BlockStateTag").getString("size")),"drop lost tier/Ember or duplicated");
        miner.setItemInHand(InteractionHand.MAIN_HAND,drop);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var result=((BlockItem)drop.getItem()).place(new BlockPlaceContext(new UseOnContext(miner,InteractionHand.MAIN_HAND,hit)));
        require(result.consumesAction() && level.getBlockEntity(pos) instanceof TileEmberHatch restored && restored.capacity()==size.capacity() && restored.amount()==20.25,"native placement lost tier/Ember");
    }
    private static void checkRecipes(ServerLevel level,BlockPos origin,Map<BlockPos,BlockState> saved) {
        for(boolean factory:List.of(false,true)) {
            BlockPos pos=origin.offset(factory?16:0,0,0);
            place(level,pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState()
                    .setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING,Direction.NORTH),saved);
            Block[] parts={RegistryBlocks.itemInputBus.get(),RegistryBlocks.itemOutputBus.get(),RegistryBlocks.emberInput.get(),RegistryBlocks.emberInput.get()};var pattern=new MachinePattern();
            for(int i=0;i<parts.length;i++){place(level,pos.offset(i+1,0,0),parts[i].defaultBlockState(),saved);pattern.addPart(new MachinePattern.Part(i+1,0,0,ForgeRegistries.BLOCKS.getKey(parts[i]).toString(),i==3?"other":"ember_in"));}
            var machine=new DynamicMachine("ember_job","Ember job",pattern);machine.setMaxThreads(1);machine.setParallelizable(true);machine.setInternalParallelism(2);machine.setMaxParallelism(2);
            MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery","ember_job"),machine);
            var controller=(TileController)level.getBlockEntity(pos);controller.setMachineName("ember_job");
            var input=(TileEmberHatch)level.getBlockEntity(pos.offset(3,0,0));var other=(TileEmberHatch)level.getBlockEntity(pos.offset(4,0,0));input.add(24.5,false);other.add(100,false);
            var items=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.offset(1,0,0))).getInventory();var products=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.offset(2,0,0))).getInventory();items.setStackInSlot(0,new ItemStack(Items.COAL,2));
            var consume=new RequirementEmber(12.25);consume.setSelectorTag("ember_in");
            var recipe=new MachineRecipe("ember_job","ember_batch",3,List.of(new RequirementItem(IOType.INPUT,"minecraft:coal",1),new RequirementItem(IOType.OUTPUT,"minecraft:diamond",1),consume),null,false,-1,"");
            MachineReloadListener.getRecipeLoader().getByMachine().put("ember_job",List.of(recipe));
            require(MachineFormation.tryForm(level,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"machine formation");controller.setFormed(true);
            for(int i=0;i<12 && products.getStackInSlot(0).isEmpty();i++){if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);}
            require(products.getStackInSlot(0).is(Items.DIAMOND) && products.getStackInSlot(0).getCount()==2 && items.getStackInSlot(0).isEmpty() && input.amount()==0 && other.amount()==100,"actual ordinary/factory exact-balance 2x fractional job");
            var ctx=new MachineContext(level,pos,Direction.NORTH,machine,new MachineVariables(),pattern);
            var decoded=RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe,Map.of()));require(decoded.requirements().get(2) instanceof RequirementEmber e && e.amount()==12.25 && "ember_in".equals(e.getSelectorTag()),"Ember snapshot");
            input.add(75.5,false);
            var first=new RequirementEmber(50);first.setSelectorTag("ember_in");var second=new RequirementEmber(50);second.setSelectorTag("ember_in");
            double before=input.amount();require(!RequirementEmber.plan(ctx,List.of(first,second),1).execute() && input.amount()==before,"shared shortage partially consumed");
            require(first.getMaxParallelism(ctx,8,List.of(first,second))==0,"shared parallelism overestimate");
            var exempt=new RequirementEmber(10);exempt.setParallelizeUnaffected(true);exempt.setParallelism(8);exempt.setSelectorTag("other");require(exempt.getParallelism()==1 && exempt.consume(ctx,8) && other.amount()==90,"batch exemption");
            var scaled=(RequirementEmber)consume.scaled(2,Requirement.OPERATION_MULTIPLY,IOType.INPUT,false);require(scaled.amount()==24.5 && "ember_in".equals(scaled.getSelectorTag()),"double modifier/selector");
            var split=RequirementEmber.plan(ctx,List.of(new RequirementEmber(100)),1);require(split.valid(),"multi-hatch reservation");other.take(1,false);require(!split.execute() && input.amount()==before,"stale plan partially paid");
            require(CompletableFuture.supplyAsync(()->!consume.canStart(ctx)).join(),"async recipe query");
            BlockPos far=new BlockPos(24000000,220,24000000);ctx.overrideComponentPositions(List.of(far));require(!new RequirementEmber(1).canStart(ctx) && !level.hasChunkAt(far),"query forced unloaded chunk");
        }
    }
    private static void checkParsing() {
        for(String json:List.of("{}","{\"ember\":-1}","{\"ember\":1e400}","{\"ember\":\"1\"}","{\"ember\":1,\"perTick\":true}","{\"ember\":1,\"parallelizeUnaffected\":\"true\"}")) {
            try {new RequirementEmber.Type().createRequirement(IOType.INPUT,JsonParser.parseString(json).getAsJsonObject());throw new AssertionError("invalid Ember JSON accepted: "+json);}catch(JsonParseException expected) { }
        }
        try {new RequirementEmber.Type().createRequirement(IOType.OUTPUT,JsonParser.parseString("{\"ember\":1}").getAsJsonObject());throw new AssertionError("unsupported Ember output accepted");}catch(JsonParseException expected) { }
    }
    private static void place(ServerLevel level,BlockPos pos,BlockState state,Map<BlockPos,BlockState> saved){saved.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,Block.UPDATE_ALL);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError("Embers: "+message);}
}
