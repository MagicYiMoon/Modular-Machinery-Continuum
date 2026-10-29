/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.botania;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandlerModifiable;
import vazkii.botania.api.*;
import vazkii.botania.api.mana.spark.*;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.entity.ManaSparkEntity;
import vazkii.botania.common.item.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Uses native Botania spark items/entities, not a mock transfer implementation. */
public final class SparkManaRegressionChecks {
    private SparkManaRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var saved = new LinkedHashMap<BlockPos, BlockState>(); var entities = new ArrayList<ManaSparkEntity>();
        var machines = MachineReloadListener.getLoader().getMachines(); var oldMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var oldRecipes = new LinkedHashMap<>(recipes);
        var player = FakePlayerFactory.getMinecraft(level); var oldHand = player.getMainHandItem(); var offHand = player.getOffhandItem();
        try {
            for (String id : List.of("spark_mana_input", "spark_mana_output"))
                require(level.getRecipeManager().byKey(new ResourceLocation("modularmachinery", id)).isPresent(), "conditional crafting missing: " + id);
            BlockPos at = origin.atY(210);
            for (int x : new int[]{0,4,8,24}) place(level, at.offset(x,0,0), x == 4 || x == 24 ? RegistryBlocks.sparkManaInput.get().defaultBlockState()
                    : BotaniaBlocks.manaPool.defaultBlockState(), saved);
            var source = (ManaPoolBlockEntity) level.getBlockEntity(at);
            var input = (TileSparkManaProvider) level.getBlockEntity(at.offset(4,0,0));
            var wrongPool = (ManaPoolBlockEntity) level.getBlockEntity(at.offset(8,0,0));
            var farInput = (TileSparkManaProvider) level.getBlockEntity(at.offset(24,0,0));
            // Botania initializes a new pool's capacity on its first real server tick.
            initializePool(level, source); initializePool(level, wrongPool);
            require(input.capacity() == 1000000 && input.canReceiveManaFromBursts(), "spark capacity / input bursts");
            var sparkCap = input.getCapability(BotaniaForgeCapabilities.SPARK_ATTACHABLE); var manaCap = input.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER);
            require(sparkCap.orElseThrow(IllegalStateException::new) == input && manaCap.orElseThrow(IllegalStateException::new) == input
                    && input.getCapability(BotaniaForgeCapabilities.WANDABLE).orElseThrow(IllegalStateException::new) == input, "native capabilities missing");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.spark,2)); player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            var hit = new BlockHitResult(Vec3.atCenterOf(input.getBlockPos()), Direction.UP, input.getBlockPos(), false);
            require(input.getBlockState().use(level, player, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS, "block swallowed spark useOn");
            require(BotaniaItems.spark.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
                    && player.getMainHandItem().getCount() == 1, "real spark item did not attach / consumed wrong count");
            var attached = (ManaSparkEntity) input.getAttachedSpark(); require(attached != null, "attached spark not found above input"); entities.add(attached);
            require(!ManaSparkItem.attachSpark(level, input.getBlockPos(), player.getMainHandItem(), ItemStack.EMPTY)
                    && player.getMainHandItem().getCount() == 1, "duplicate spark attached");
            var donor = spark(level, at, entities); var wrong = spark(level, wrongPool.getBlockPos(), entities); var far = spark(level, farInput.getBlockPos(), entities);
            wrong.setNetwork(DyeColor.RED); source.receiveMana(20000); wrongPool.receiveMana(20000);
            for (int i=0;i<30;i++) { farInput.tick(); cycle(input, List.of(donor,attached,wrong,far), 1); }
            require(input.getCurrentMana() > 0 && source.getCurrentMana() + input.getCurrentMana() == 20000
                    && wrongPool.getCurrentMana() == 20000 && farInput.getCurrentMana() == 0,
                    "native transfer lost mana or ignored color/range: input=" + input.getCurrentMana()
                    + ", source=" + source.getCurrentMana() + ", wrong=" + wrongPool.getCurrentMana()
                    + ", far=" + farInput.getCurrentMana() + ", networks=" + donor.getNetwork() + "/" + attached.getNetwork()
                    + ", removed=" + donor.isRemoved() + "/" + attached.isRemoved()
                    + ", live=" + (input.getAttachedSpark() != null) + ", transfers=" + donor.getOutgoingTransfers().size());
            source.receiveMana(20000); int before = input.getCurrentMana(); donor.setNetwork(DyeColor.BLUE);
            cycle(input, List.of(donor,attached,wrong,far), 15); require(input.getCurrentMana() == before, "changed color still transferred");
            donor.setNetwork(DyeColor.WHITE); donor.setUpgrade(SparkUpgradeType.ISOLATED);
            cycle(input, List.of(donor,attached,wrong,far), 15); require(input.getCurrentMana() == before, "isolated donor transferred");
            donor.setUpgrade(SparkUpgradeType.NONE); attached.setUpgrade(SparkUpgradeType.ISOLATED);
            cycle(input, List.of(donor,attached,wrong,far), 15); require(input.getCurrentMana() == before, "isolated input transferred");
            attached.setUpgrade(SparkUpgradeType.NONE);
            input.add(Long.MAX_VALUE,false); before = source.getCurrentMana();
            cycle(input, List.of(donor,attached,wrong,far), 15);
            require(input.getCurrentMana() == 1000000 && source.getCurrentMana() == before, "full input swallowed mana");
            input.take(1234,false); cycle(input, List.of(donor,attached,wrong,far), 25);
            require(input.getCurrentMana() == 1000000 && source.getCurrentMana() == before - 1234, "full->partial did not reconnect or overfilled");
            attached.discard(); source.receiveMana(10000); input.take(5000,false); before=input.getCurrentMana();
            cycle(input,List.of(donor,wrong,far),15);require(input.getCurrentMana()==before,"missing spark still transferred");
            var replacement=spark(level,input.getBlockPos(),entities);cycle(input,List.of(donor,replacement,wrong,far),25);
            require(input.getCurrentMana()==1000000,"replaced spark did not reconnect");
            // Duplicate entities must not be chosen arbitrarily. Cleanup leaves normal operation intact.
            var duplicate=spark(level,input.getBlockPos(),entities);require(input.getAttachedSpark()==null,"ambiguous attached spark selected");duplicate.discard();
            checkSaveAndGuards(level,input,sparkCap);
            checkOutput(level,at.offset(0,0,8),saved,entities);
            checkRecipes(level,at.offset(0,0,32),saved);
            Modularmachinery.LOG.info("[MMC][spark-mana] PASS: native item attachment/duplicate prevention, real spark transfers, color/range/isolation/full/refill/replacement, output source direction and dominant/recessive export, capability lifecycle/NBT/async guards, ordinary+factory 2x one-shot/per-tick mana recipes");
        } finally {
            entities.forEach(entity -> { if (!entity.isRemoved()) entity.discard(); });
            player.setItemInHand(InteractionHand.MAIN_HAND,oldHand);player.setItemInHand(InteractionHand.OFF_HAND,offHand);
            saved.forEach((pos,state)->level.setBlock(pos,state,Block.UPDATE_ALL));
            machines.clear(); machines.putAll(oldMachines);recipes.clear(); recipes.putAll(oldRecipes);MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkSaveAndGuards(ServerLevel level, TileSparkManaProvider tile,
            net.minecraftforge.common.util.LazyOptional<SparkAttachable> oldCap) {
        tile.take(Long.MAX_VALUE,false);tile.add(4321,false);tile.setControllerPos(tile.getBlockPos().below());
        int before=tile.getCurrentMana();
        require(tile.take(10,true)==10 && tile.add(10,true)==10 && tile.getCurrentMana()==before,"simulation changed mana");
        require(CompletableFuture.supplyAsync(()->tile.take(10,false)==0 && tile.add(10,false)==0).join()
                && tile.getCurrentMana()==before,"async mana mutation");
        var disk=tile.saveWithoutMetadata();var update=tile.getUpdateTag();
        var detached=new TileSparkManaProvider(tile.getType(),tile.getBlockPos(),tile.getBlockState());detached.load(update);
        require(detached.getCurrentMana()==4321 && detached.getControllerPos().equals(tile.getControllerPos()),"update tag lost mana/binding");
        tile.invalidateCaps();require(!oldCap.isPresent(),"spark cap survived invalidation");tile.reviveCaps();
        require(tile.getCapability(BotaniaForgeCapabilities.SPARK_ATTACHABLE).orElseThrow(IllegalStateException::new)==tile,"spark cap did not revive");
        var state=tile.getBlockState();var pos=tile.getBlockPos();level.removeBlockEntity(pos);
        var restored=new TileSparkManaProvider(ManaRegistration.SPARK_INPUT.get(),pos,state);level.setBlockEntity(restored);restored.load(disk);
        require(restored.getCurrentMana()==4321 && restored.getControllerPos().equals(pos.below())
                && tile.take(10,false)==0 && tile.add(10,false)==0,"NBT restore or stale old tile mutation");
        for(int malformed:new int[]{-1,Integer.MAX_VALUE}) {var data=new CompoundTag();data.putInt("mana",malformed);restored.load(data);
            require(restored.getCurrentMana()==(malformed<0?0:1000000),"malformed saved mana not clamped");}
        restored.take(Long.MAX_VALUE,false);restored.receiveMana(Integer.MAX_VALUE);restored.receiveMana(Integer.MAX_VALUE);
        require(restored.getCurrentMana()==1000000,"overflow mint/wrap");
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);level.setBlock(pos,state,Block.UPDATE_ALL);
        require(((TileSparkManaProvider)level.getBlockEntity(pos)).getCurrentMana()==0,"new position inherited stored mana");
    }
    private static void checkOutput(ServerLevel level,BlockPos pos,Map<BlockPos,BlockState> saved,List<ManaSparkEntity> entities) {
        place(level,pos,RegistryBlocks.sparkManaOutput.get().defaultBlockState(),saved);
        place(level,pos.offset(4,0,0),BotaniaBlocks.manaPool.defaultBlockState(),saved);
        var output=(TileSparkManaProvider.Output)level.getBlockEntity(pos);
        var pool=(ManaPoolBlockEntity)level.getBlockEntity(pos.offset(4,0,0));
        initializePool(level, pool);
        output.onLoad();require(BotaniaAPI.instance().getManaNetworkInstance().getAllPoolsInWorld(level).contains(output),"spark output not in native pool network");
        output.onChunkUnloaded();require(!BotaniaAPI.instance().getManaNetworkInstance().getAllPoolsInWorld(level).contains(output),"spark output network unload");
        // Reusing a test instance after Forge unload requires the normal capability revival step.
        require(!output.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER).isPresent(), "output caps survived unload");
        output.reviveCaps(); output.onLoad();
        require(!output.canReceiveManaFromBursts() && output.getAvailableSpaceForMana()==0
                && output.areIncomingTranfersDone() && output.isFull(),"output advertised external receive space");
        output.receiveMana(100);require(output.getCurrentMana()==0 && output.add(10000,false)==10000,"external output fill vs recipe deposit");
        var source=spark(level,pos,entities);var target=spark(level,pool.getBlockPos(),entities);
        source.setNetwork(DyeColor.GREEN); target.setNetwork(DyeColor.GREEN);
        target.setUpgrade(SparkUpgradeType.DOMINANT);
        for(int i=0;i<20;i++){source.tick();target.tick();}
        require(output.getCurrentMana()==0 && pool.getCurrentMana()==10000,"native dominant pool failed to pull recipe mana: output="
                + output.getCurrentMana() + ", pool=" + pool.getCurrentMana() + ", cap=" + pool.getMaxMana()
                + ", sourceLive=" + (source.getAttachedManaReceiver() == output) + ", targetLive=" + (target.getAttachedManaReceiver() == pool)
                + ", sourcePool=" + (source.getAttachedManaReceiver() instanceof vazkii.botania.api.mana.ManaPool)
                + ", removed=" + source.isRemoved() + "/" + target.isRemoved()
                + ", around=" + SparkHelper.getSparksAround(level,target.getX(),target.getY(),target.getZ(),DyeColor.GREEN).size());
        pool.receiveMana(-pool.getCurrentMana());target.setUpgrade(SparkUpgradeType.NONE);source.setUpgrade(SparkUpgradeType.RECESSIVE);output.add(7000,false);
        for(int i=0;i<20;i++){source.tick();target.tick();}
        require(output.getCurrentMana()==0 && pool.getCurrentMana()==7000,"native recessive output failed to export mana");
        // A dominant output spark must not debit a donor into a source-only hatch.
        source.setUpgrade(SparkUpgradeType.DOMINANT);int poolBefore=pool.getCurrentMana();
        for(int i=0;i<20;i++){source.tick();target.tick();}
        require(pool.getCurrentMana()==poolBefore && output.getCurrentMana()==0,"dominant output swallowed donor mana");
    }
    private static void checkRecipes(ServerLevel level,BlockPos origin,Map<BlockPos,BlockState> saved) throws Exception {
        for(boolean factory:List.of(false,true)) for(boolean rate:List.of(false,true)) {
            BlockPos pos=origin.offset(factory?16:0,0,rate?8:0);
            place(level,pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState()
                    .setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING,Direction.NORTH),saved);
            Block[] blocks={RegistryBlocks.itemInputBus.get(),RegistryBlocks.itemOutputBus.get(),RegistryBlocks.sparkManaInput.get(),RegistryBlocks.sparkManaOutput.get()};
            var pattern=new MachinePattern();
            for(int i=0;i<blocks.length;i++){place(level,pos.offset(i+1,0,0),blocks[i].defaultBlockState(),saved);
                pattern.addPart(new MachinePattern.Part(i+1,0,0,net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blocks[i]).toString(),i<3?"in":"out"));}
            String name="spark_mana_job";
            var machine=new DynamicMachine(name,"Spark job",pattern);machine.setMaxThreads(1);machine.setParallelizable(true);machine.setInternalParallelism(2);machine.setMaxParallelism(2);
            MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery",name),machine);
            var controller=(TileController)level.getBlockEntity(pos);controller.setMachineName(name);
            var input=(TileSparkManaProvider)level.getBlockEntity(pos.offset(3,0,0));var output=(TileSparkManaProvider)level.getBlockEntity(pos.offset(4,0,0));
            input.add(1000,false);
            var items=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.offset(1,0,0))).getInventory();
            var products=(IItemHandlerModifiable)((TileItemBus)level.getBlockEntity(pos.offset(2,0,0))).getInventory();items.setStackInSlot(0,new ItemStack(Items.COAL,2));
            var consume=rate?new RequirementMana.Rate(IOType.INPUT,100):new RequirementMana(IOType.INPUT,100);
            var produce=rate?new RequirementMana.Rate(IOType.OUTPUT,40):new RequirementMana(IOType.OUTPUT,40);
            consume.setSelectorTag("in");produce.setSelectorTag("out");
            var recipe=new MachineRecipe(name,"spark_mana_batch",3,List.of(new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem(IOType.INPUT,"minecraft:coal",1),
                    new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem(IOType.OUTPUT,"minecraft:diamond",1),consume,produce),null,false,-1,"");
            MachineReloadListener.getRecipeLoader().getByMachine().put(name,List.of(recipe));
            require(MachineFormation.tryForm(level,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"spark recipe formation");controller.setFormed(true);
            for(int i=0;i<12 && products.getStackInSlot(0).isEmpty();i++){if(factory)FactoryTicker.tick(level,pos,(TileFactoryController)controller);else MachineTicker.tick(level,pos,controller);}
            require(products.getStackInSlot(0).is(Items.DIAMOND) && products.getStackInSlot(0).getCount()==2 && items.getStackInSlot(0).isEmpty()
                    && input.getCurrentMana()==(rate?400:800) && output.getCurrentMana()==(rate?240:80),"actual 2x spark mana one-shot/per-tick recipe");
            var remote=RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe,Map.of()));require(remote.requirements().size()==4,"spark recipe snapshot");
            var context=new MachineContext(level,pos,Direction.NORTH,machine,new MachineVariables(),pattern);
            output.add(Long.MAX_VALUE,false);int before=input.getCurrentMana();
            require(MachineTicker.processPerTick(recipe,List.of(new RequirementMana.Rate(IOType.INPUT,100),new RequirementMana.Rate(IOType.OUTPUT,40)),context,1,false)!=null
                    && input.getCurrentMana()==before,"full spark output debited input mana");
            var wrong = new RequirementMana(IOType.INPUT,1); wrong.setSelectorTag("out");
            require(!wrong.canStart(context),"selector direction failed");
        }
    }
    private static ManaSparkEntity spark(ServerLevel level,BlockPos pos,List<ManaSparkEntity> entities) {
        var spark=new ManaSparkEntity(level);spark.setPos(pos.getX()+0.5,pos.getY()+1.25,pos.getZ()+0.5);
        require(level.addFreshEntity(spark),"native spark spawn");entities.add(spark);return spark;
    }
    private static void initializePool(ServerLevel level, ManaPoolBlockEntity pool) {
        ManaPoolBlockEntity.serverTick(level, pool.getBlockPos(), pool.getBlockState(), pool);
        require(pool.getMaxMana() == 1000000, "native pool initial tick did not initialize capacity");
    }
    private static void cycle(TileSparkManaProvider input,List<ManaSparkEntity> sparks,int ticks) {
        for(int i=0;i<ticks;i++){input.tick();for(var spark:sparks)if(!spark.isRemoved())spark.tick();}
    }
    private static void place(ServerLevel level,BlockPos pos,BlockState state,Map<BlockPos,BlockState> saved){saved.putIfAbsent(pos,level.getBlockState(pos));level.setBlock(pos,state,Block.UPDATE_ALL);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Spark mana: "+message);}
}
