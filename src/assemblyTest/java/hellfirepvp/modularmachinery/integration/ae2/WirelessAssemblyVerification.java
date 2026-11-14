/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.assembly.AssemblyNetwork;
import hellfirepvp.modularmachinery.common.assembly.MachineAssembly;
import hellfirepvp.modularmachinery.common.assembly.MachineAssemblyManager;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.function.Consumer;

/** Opt-in disposable-server fixture. Uses the real AE grid, planner, CPU and molecular assembler. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class WirelessAssemblyVerification {
    private static ServerLevel level;
    private static MinecraftServer server;
    private static RecordingPlayer player;
    private static final BlockPos BASE = new BlockPos(0, 120, 0);
    private static final BlockPos CTRL = BASE.offset(8, 0, 0);
    private static final CounterStorage storage = new CounterStorage();
    private static IGrid grid;
    private static ItemStack terminal;
    private static int ticks, phase;
    private static MachineAssembly job;
    private static Object oldTimeout;

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("modularmachinery.wirelessAssemblyTest")) return;
        server = event.getServer(); level = server.overworld();
        try {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
            for (int x = -4; x <= 14; x++) for (int z = -1; z <= 2; z++)
                level.setBlock(BASE.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(BASE, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
            var wapState=AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState();
            wapState=appeng.api.orientation.IOrientationStrategy.get(wapState).setFacing(wapState,Direction.EAST);
            level.setBlock(BASE.offset(1,0,0), wapState, 3);
            level.setBlock(BASE.offset(-1,0,0), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState(), 3);
            level.setBlock(BASE.offset(-2,0,0), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), 3);
            level.setBlock(BASE.offset(-3,0,0), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState(), 3);
            level.setBlock(CTRL, RegistryBlocks.blockController.get().defaultBlockState(), 3);
            var provider = (PatternProviderBlockEntity) level.getBlockEntity(BASE.offset(-2,0,0));
            var recipe = (CraftingRecipe) level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("minecraft", "oak_planks")).orElseThrow();
            ItemStack[] inputs = new ItemStack[9]; Arrays.fill(inputs, ItemStack.EMPTY); inputs[0] = new ItemStack(Items.OAK_LOG);
            provider.getLogic().getPatternInv().setItemDirect(0,
                    PatternDetailsHelper.encodeCraftingPattern(recipe, inputs, new ItemStack(Items.OAK_PLANKS,4), false, false));
            player = new RecordingPlayer(level);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setPos(3.5,120,2.5);
            terminal = AEItems.WIRELESS_TERMINAL.stack(1);
            GridLinkables.get(terminal.getItem()).link(terminal, GlobalPos.of(level.dimension(),BASE.offset(1,0,0)));
            ((WirelessTerminalItem) terminal.getItem()).injectAEPower(terminal,10000,Actionable.MODULATE);
            player.getInventory().setItem(8,terminal);
            Modularmachinery.LOG.info("[MMC][wireless-assembly] Fixture placed; waiting for AE grid boot");
        } catch (Throwable ex) { finish(ex); }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        try {
            ticks++;
            if (ticks > 1800) throw new AssertionError("fixture timed out at phase "+phase+", messages="+player.messages);
            if (phase == 0) {
                if (ticks < 100) return;
                var wap = (WirelessAccessPointBlockEntity) level.getBlockEntity(BASE.offset(1,0,0));
                if (!wap.isActive()) {
                    if (ticks % 100 == 0) Modularmachinery.LOG.info("[MMC][wireless-assembly] WAP waiting, grid={}",wap.getGrid());
                    return;
                }
                grid = wap.getGrid();
                grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(storage));
                checkAccessAndRollback();
                storage.set(AEItemKey.of(Items.OAK_LOG),1);
                storage.set(AEItemKey.of(Items.STONE),1);
                storage.set(AEFluidKey.of(Fluids.WATER),1000);
                storage.set(AEItemKey.of(RegistryBlocks.itemInputBus.get()),1);
                player.getInventory().setItem(1,new ItemStack(Items.STONE));
                player.getInventory().setItem(2,new ItemStack(Items.OAK_PLANKS));
                var pattern = new MachinePattern();
                for (int x=1;x<=3;x++) pattern.addPart(new MachinePattern.Part(x,0,0,"minecraft:oak_planks"));
                for (int x=4;x<=5;x++) pattern.addPart(new MachinePattern.Part(x,0,0,"minecraft:stone"));
                pattern.addPart(new MachinePattern.Part(6,0,0,"minecraft:water"));
                pattern.addPart(new MachinePattern.Part(0,0,1,"modularmachinery:blockinputbus"));
                job = MachineAssembly.start(level,CTRL,player,new DynamicMachine("wireless_test","Wireless test",pattern),
                        Direction.NORTH,MachineReloadListener.getLoader());
                require(job != null,"wireless assembly rejected a craftable shortage: "+player.messages);
                require(level.getBlockState(CTRL.offset(1,0,0)).isAir(),"placed blocks before require-all was satisfied");
                MachineAssemblyManager.add(level,CTRL,job);
                phase=1;
                Modularmachinery.LOG.info("[MMC][wireless-assembly] Real two-plank crafting order queued");
            } else if (phase == 1) {
                MachineAssemblyManager.tick(player);
                if (!job.isFinished()) return;
                for(int x=1;x<=3;x++) require(level.getBlockState(CTRL.offset(x,0,0)).is(Blocks.OAK_PLANKS),"crafted plank missing");
                require(level.getBlockState(CTRL.offset(4,0,0)).is(Blocks.STONE)
                        && level.getBlockState(CTRL.offset(5,0,0)).is(Blocks.STONE),"backpack/network stone missing");
                require(level.getBlockState(CTRL.offset(6,0,0)).is(Blocks.WATER),"ME fluid was not placed");
                require(level.getBlockState(CTRL.offset(0,0,1)).is(RegistryBlocks.itemInputBus.get()),"untagged base tier was rejected");
                require(storage.count(AEItemKey.of(Items.OAK_LOG))==0 && storage.count(AEItemKey.of(Items.OAK_PLANKS))==2,
                        "crafting duplicated/consumed the wrong amount: "+storage.values);
                require(storage.count(AEItemKey.of(Items.STONE))==0 && storage.count(AEFluidKey.of(Fluids.WATER))==0,
                        "remote placement did not pay for materials");
                require(player.messages.stream().filter(s->s.contains("message.assembly.ae.requested")).count()==1,
                        "duplicate orders were submitted: "+player.messages);
                require(player.getInventory().countItem(Items.BUCKET)==0,"direct ME fluid extraction created a free bucket");
                checkMissingRaw(); phase=2;
                Modularmachinery.LOG.info("[MMC][wireless-assembly] PASS: real AE crafting, wait/resume, aggregate request, backpack priority, fluid and tier conservation");
            } else if (phase == 2) {
                MachineAssemblyManager.tick(player);
                if (!job.isFinished()) return;
                require(player.messages.stream().anyMatch(s->s.contains("message.assembly.ae.failed")),"missing raw materials were not reported");
                require(level.getBlockState(CTRL.offset(1,0,1)).isAir(),"failed crafting created a free block");
                checkTimeout(); phase=3;
            } else if (phase == 3) {
                MachineAssemblyManager.tick(player);
                if (!job.isFinished()) return;
                require(player.messages.stream().anyMatch(s->s.contains("message.assembly.ae.timeout")),"disconnected job did not time out");
                require(level.getBlockState(CTRL.offset(1,0,1)).isAir(),"disconnected terminal supplied free blocks");
                finish(null);
            }
        } catch (Throwable ex) { finish(ex); }
    }

    private static void checkAccessAndRollback() {
        storage.set(AEItemKey.of(Items.STONE),4);
        AssemblyNetwork source=AssemblyNetwork.open(player);
        require(source!=null,"charged, linked in-range terminal unavailable");
        double before=((WirelessTerminalItem)terminal.getItem()).getAECurrentPower(terminal);
        var budget=source.snapshot();
        for(int i=0;i<4;i++) require(budget.take(new ItemStack(Items.STONE),null),"stock reservation failed");
        require(!budget.take(new ItemStack(Items.STONE),null) && storage.count(AEItemKey.of(Items.STONE))==4,
                "simulation reused/consumed stock");
        ItemStack taggedStone=new ItemStack(Items.STONE);taggedStone.getOrCreateTag().putString("author","different");
        require(!source.canExtract(taggedStone,null),"ordinary block NBT was ignored");
        var payment=source.extract(new ItemStack(Items.STONE),null);
        require(payment!=null && storage.count(AEItemKey.of(Items.STONE))==3,"wireless extraction failed");
        require(((WirelessTerminalItem)terminal.getItem()).getAECurrentPower(terminal)<before,"wireless extraction was free");
        payment.refund().run();
        require(storage.count(AEItemKey.of(Items.STONE))==4,"payment rollback lost items");
        player.setPos(100,120,0);require(AssemblyNetwork.open(player)==null,"wireless range bypassed");
        player.setPos(3.5,120,2.5);
        ((WirelessTerminalItem)terminal.getItem()).extractAEPower(terminal,Double.MAX_VALUE,Actionable.MODULATE);
        require(AssemblyNetwork.open(player)==null,"empty terminal was accepted");
        ((WirelessTerminalItem)terminal.getItem()).injectAEPower(terminal,10000,Actionable.MODULATE);
        player.getInventory().setItem(8,ItemStack.EMPTY);
        require(source.extract(new ItemStack(Items.STONE),null)==null,"removed terminal retained access");
        terminal=terminal.copy();player.getInventory().setItem(8,terminal);
        require(source.available(),"terminal copied by inventory movement could not resume in its original slot");
        storage.set(AEFluidKey.of(Fluids.WATER),1000);
        storage.partialFluid=true;
        require(source.extract(ItemStack.EMPTY,new net.minecraftforge.fluids.FluidStack(Fluids.WATER,1000))==null,
                "partial fluid extraction paid for a full source block");
        require(storage.count(AEFluidKey.of(Fluids.WATER))==500 && player.getInventory().countItem(Items.WATER_BUCKET)==0,
                "partial fluid refund created a full bucket");
        storage.partialFluid=false;
        var reopened=AssemblyNetwork.open(player);
        require(reopened!=null && storage.count(AEFluidKey.of(Fluids.WATER))==1000,"exact partial fluid refund was not restored");
        reopened.close();
        source.close();
        // Real Forge placement protection must restore both the world and exact remote payment.
        BlockPos target=CTRL.offset(1,0,0);
        Consumer<BlockEvent.EntityPlaceEvent> cancel=e->{if(e.getEntity()==player && e.getPos().equals(target))e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(cancel);
        try {
            var pattern=new MachinePattern(); pattern.addPart(new MachinePattern.Part(1,0,0,"minecraft:stone"));
            var assembly=MachineAssembly.start(level,CTRL,player,new DynamicMachine("rollback_test","Rollback",pattern),Direction.NORTH,MachineReloadListener.getLoader());
            require(assembly!=null,"rollback fixture rejected");
            for(int i=0;i<Config.autoAssemblyInterval()+1;i++)assembly.tick();
            require(target != null && level.getBlockState(target).isAir() && storage.count(AEItemKey.of(Items.STONE))==4,
                    "Forge cancellation left a free block or lost remote payment");
            assembly.close();
        } finally {MinecraftForge.EVENT_BUS.unregister(cancel);}
        // Cancel through the actual sneak-right-click handler; other owners cannot cancel it.
        var cancelPattern=new MachinePattern();cancelPattern.addPart(new MachinePattern.Part(1,0,0,"minecraft:stone"));
        var cancelJob=MachineAssembly.start(level,CTRL,player,new DynamicMachine("cancel_test","Cancel",cancelPattern),Direction.NORTH,MachineReloadListener.getLoader());
        require(cancelJob!=null,"cancel fixture rejected");
        MachineAssemblyManager.add(level,CTRL,cancelJob);
        player.getInventory().setItem(0,new ItemStack(Items.STICK));player.setShiftKeyDown(true);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(CTRL),Direction.UP,CTRL,false);
        var stranger=new RecordingPlayer(level);stranger.getInventory().setItem(0,new ItemStack(Items.STICK));stranger.setShiftKeyDown(true);
        hellfirepvp.modularmachinery.common.event.AutoAssemblyEvents.onRightClickBlock(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(stranger,net.minecraft.world.InteractionHand.MAIN_HAND,CTRL,hit));
        require(MachineAssemblyManager.contains(level,CTRL),"another player canceled the owner's job");
        var cancelEvent=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,CTRL,hit);
        hellfirepvp.modularmachinery.common.event.AutoAssemblyEvents.onRightClickBlock(cancelEvent);
        require(cancelEvent.isCanceled() && !MachineAssemblyManager.contains(level,CTRL),"sneak-click did not cancel owned job");
        player.setShiftKeyDown(false);
        Modularmachinery.LOG.info("[MMC][wireless-assembly] PASS: range, battery, terminal removal, reservations, charged extraction and Forge rollback");
    }

    private static void checkTimeout() throws Exception {
        var timeout=timeout();oldTimeout=timeout.get();timeout.set(200);
        storage.set(AEItemKey.of(Items.OAK_PLANKS),0);storage.set(AEItemKey.of(Items.OAK_LOG),1);
        var pattern=new MachinePattern(); pattern.addPart(new MachinePattern.Part(1,0,1,"minecraft:oak_planks"));
        job=MachineAssembly.start(level,CTRL,player,new DynamicMachine("timeout_test","Timeout",pattern),Direction.NORTH,MachineReloadListener.getLoader());
        require(job!=null,"timeout fixture rejected");
        MachineAssemblyManager.add(level,CTRL,job);
        player.getInventory().setItem(8,ItemStack.EMPTY);
    }

    private static void checkMissingRaw() {
        storage.set(AEItemKey.of(Items.OAK_PLANKS),0);storage.set(AEItemKey.of(Items.OAK_LOG),0);
        var pattern=new MachinePattern();pattern.addPart(new MachinePattern.Part(1,0,1,"minecraft:oak_planks"));
        job=MachineAssembly.start(level,CTRL,player,new DynamicMachine("missing_raw_test","Missing raw",pattern),Direction.NORTH,MachineReloadListener.getLoader());
        require(job!=null,"missing-raw calculation fixture rejected");
        MachineAssemblyManager.add(level,CTRL,job);
    }

    private static ForgeConfigSpec.IntValue timeout() throws Exception {
        var field=Config.class.getDeclaredField("AUTO_ASSEMBLY_WAIT_TIMEOUT");field.setAccessible(true);
        return (ForgeConfigSpec.IntValue)field.get(null);
    }

    private static void finish(Throwable error) {
        if (error==null) Modularmachinery.LOG.info("[MMC][wireless-assembly] OVERALL: PASS");
        else Modularmachinery.LOG.error("[MMC][wireless-assembly] OVERALL: FAIL",error);
        if(player!=null)MachineAssemblyManager.removePlayer(player);
        try {if(oldTimeout!=null)timeout().set((Integer)oldTimeout);}catch(Exception ignored){}
        MinecraftServer stopping=server;server=null;if(stopping!=null)stopping.halt(false);
    }

    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}

    private static final class RecordingPlayer extends FakePlayer {
        final List<String> messages=new ArrayList<>();
        RecordingPlayer(ServerLevel level){super(level,new GameProfile(UUID.randomUUID(),"AssemblyTest"));}
        @Override public void displayClientMessage(Component text,boolean overlay){messages.add(Component.Serializer.toJson(text));}
    }

    private static final class CounterStorage implements MEStorage {
        final Map<AEKey,Long> values=new LinkedHashMap<>();
        boolean partialFluid;
        void set(AEKey key,long amount){values.put(key,amount);if(grid!=null)grid.getStorageService().invalidateCache();}
        long count(AEKey key){return values.getOrDefault(key,0L);}
        @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource src){if(partialFluid && key instanceof AEFluidKey)return 0;if(mode==Actionable.MODULATE)set(key,count(key)+amount);return amount;}
        @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource src){long taken=Math.min(count(key),amount);if(mode==Actionable.MODULATE){if(partialFluid && key instanceof AEFluidKey)taken=Math.min(taken,500);set(key,count(key)-taken);}return taken;}
        @Override public void getAvailableStacks(KeyCounter out){values.forEach(out::add);}
        @Override public Component getDescription(){return Component.literal("Assembly fixture storage");}
    }
}
