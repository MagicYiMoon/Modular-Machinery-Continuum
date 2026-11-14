/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in disposable world and actual BE packets. Queue snapshots here are fixtures, not native AE orders. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EFabricatorAppearanceClientVerification {
    private static final BlockPos BASE=new BlockPos(0,120,0);
    private static int total,ticks,initialLightWait;
    private static boolean configured;
    private static List<BlockPos> previouslyHidden=List.of();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.efAppearanceTest"))return;
        var mc=Minecraft.getInstance();
        if(++total>6000){fail(new AssertionError("client timeout"));return;}
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        try {
            if(!configured){configured=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
                mc.options.renderDistance().set(6);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
                server(EFabricatorAppearanceClientVerification::setup);return;}
            if(ticks==99&&mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,BASE.east(2))<10) {
                if(++initialLightWait%20==0)Modularmachinery.LOG.info("[MMC][ef-appearance-client] waiting for initial light section: client={}, server={}, client-worker={}, client-state={}, emission={}",
                        mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,BASE.east(2)),world().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,BASE.east(2)),
                        mc.level.getBlockEntity(BASE.east(2)) instanceof TileEFabricatorWorker w?w.appearance():null,mc.level.getBlockState(BASE.east(2)),
                        mc.level.getBlockState(BASE.east(2)).getLightEmission(mc.level,BASE.east(2)));
                require(initialLightWait<300,"initial authoritative light section never arrived");return;
            }
            switch(++ticks) {
                case 100 -> {models();verify(true,EFabricatorAppearance.ON);shot("ef-appearance-01-f4-formed.png");server(()->camera(BASE.east(12),Direction.NORTH));}
                case 130 -> {shot("ef-appearance-02-f6-formed.png");server(()->camera(BASE.east(24),Direction.NORTH));}
                case 160 -> {shot("ef-appearance-03-f9-formed.png");server(()->camera(BASE,Direction.NORTH));}
                case 180 -> server(()->world().setBlockAndUpdate(BASE.above(),Blocks.AIR.defaultBlockState()));
                case 225 -> {verify(false,EFabricatorAppearance.OFF);shot("ef-appearance-04-broken.png");server(()->world().setBlockAndUpdate(BASE.above(),EFabricatorRegistration.CASING.get().defaultBlockState()));}
                case 275 -> {verify(true,EFabricatorAppearance.ON);shot("ef-appearance-05-repaired.png");mc.reloadResourcePacks();}
                case 315 -> {models();verify(true,EFabricatorAppearance.ON);server(()->rotate(Direction.EAST));}
                case 370 -> {verify(true,EFabricatorAppearance.ON);shot("ef-appearance-06-east.png");server(()->rotate(Direction.SOUTH));}
                case 425 -> {verify(true,EFabricatorAppearance.ON);shot("ef-appearance-07-south.png");server(()->rotate(Direction.WEST));}
                case 480 -> {verify(true,EFabricatorAppearance.ON);shot("ef-appearance-08-west.png");server(()->{
                    worker().offerWork(new EFabricatorQueue.Work(1,new ItemStack(Items.DIAMOND),List.of()));worker().refreshAppearance();
                    world().setDayTime(18000);camera(BASE,Direction.WEST);
                });}
                case 510 -> {verify(true,EFabricatorAppearance.RUN);shot("ef-appearance-09-queued-night.png");server(()->{
                    var worker=worker();worker.refreshAppearance();worker.restoreWorkData(new CompoundTag());worker.refreshAppearance();
                    require(worker.appearance()==EFabricatorAppearance.RUN,"source RUN linger missing");
                });}
                case 520 -> verify(true,EFabricatorAppearance.RUN);
                case 555 -> {verify(true,EFabricatorAppearance.ON);shot("ef-appearance-10-idle-night.png");server(()->{
                    // Unknown future data must remain private and select a safe idle state.
                    var future=new CompoundTag();future.putInt("Version",99);future.putString("Private","preserve");
                    worker().restoreWorkData(future);worker().refreshAppearance();
                });}
                case 590 -> {verify(true,EFabricatorAppearance.ON);server(()->{
                    var host=(TileEFabricatorController)world().getBlockEntity(BASE);host.onChunkUnloaded();world().setBlockAndUpdate(BASE,Blocks.AIR.defaultBlockState());
                });}
                case 635 -> {
                    for(var pos:previouslyHidden)require(!BlockModelHider.isHidden(mc.level,pos),"removed host left hidden cell "+pos);
                    shot("ef-appearance-11-removed.png");
                    Modularmachinery.LOG.info("[MMC][ef-appearance-client] PASS: all EF baked block/item variants and four facings, original sprites/full-bright glow, virtual OFF/ON/RUN, actual BE packets/light, source seventeen-cell copied-palette hiding, physical break/repair, resource reload, four world orientations, queued-work linger, future-data privacy and host unload/removal; preloaded fixture work is not a native AE order");
                    Modularmachinery.LOG.info("[MMC][ef-appearance-client] OVERALL: PASS");mc.stop();
                }
            }
        }catch(Throwable error){fail(error);}
    }
    private static void setup() {
        world().setDayTime(6000);world().setWeatherParameters(6000,0,false,false);
        world().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS).set(false,world().getServer());
        for(var tier:EFabricatorSpecs.Tier.values()) {
            var pattern=new MachinePattern();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=0;z<=1;z++)
                if(x!=0||y!=0||z!=0)pattern.addPart(new MachinePattern.Part(x,y,z,"modularmachinery:efabricator_casing"));
            // Visible worker/parallel bank adjacent to the host: all are real bound parts.
            for(int x=2;x<=3;x++) {
                pattern.addPart(new MachinePattern.Part(x,0,0,"modularmachinery:efabricator_worker"));
                pattern.addPart(new MachinePattern.Part(x,1,0,"modularmachinery:efabricator_parallel_proc_l"+tier.level));
            }
            pattern.addPart(new MachinePattern.Part(4,0,0,"modularmachinery:efabricator_pattern_bus"));
            var machine=new DynamicMachine(EFabricatorRegistration.machineName(tier),"EF appearance fixture",pattern);machine.setRequiresBlueprint(false);
            MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery",machine.getRegistryName()),machine);
            build(BASE.east(tier.ordinal()*12),tier,Direction.NORTH);
        }
        var player=Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();camera(BASE,Direction.NORTH);
    }
    private static void build(BlockPos origin,EFabricatorSpecs.Tier tier,Direction facing) {
        var machine=MachineReloadListener.getLoader().findByName(EFabricatorRegistration.machineName(tier));
        world().setBlockAndUpdate(origin,EFabricatorRegistration.CONTROLLERS.get(tier).get().defaultBlockState().setValue(BlockEFabricatorController.FACING,facing));
        for(var part:machine.getPattern().getParts()) {
            var state=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(part.elements())).defaultBlockState();
            if(state.hasProperty(BlockEFabricatorPart.FACING))state=state.setValue(BlockEFabricatorPart.FACING,facing);
            world().setBlockAndUpdate(offset(origin,facing,part.x(),part.y(),part.z()),state);
        }
        var host=(TileEFabricatorController)world().getBlockEntity(origin);host.refreshStructure();require(host.isAssembled(),"fixture not formed "+facing);
    }
    private static void rotate(Direction facing) {
        var old=(TileEFabricatorController)world().getBlockEntity(BASE);var previous=old.getBlockState().getValue(BlockEFabricatorController.FACING);
        var machine=MachineReloadListener.getLoader().findByName(EFabricatorRegistration.machineName(EFabricatorSpecs.Tier.L4));
        world().setBlockAndUpdate(BASE,Blocks.AIR.defaultBlockState());
        for(var part:machine.getPattern().getParts())world().setBlockAndUpdate(offset(BASE,previous,part.x(),part.y(),part.z()),Blocks.AIR.defaultBlockState());
        build(BASE,EFabricatorSpecs.Tier.L4,facing);camera(BASE,facing);
    }
    private static void camera(BlockPos origin,Direction facing) {
        var pos=offset(origin,facing,7,4,-6);
        float yaw=switch(facing){case EAST->135;case SOUTH->225;case WEST->315;default->45;};
        Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0).teleportTo(world(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,yaw,18);
    }
    private static void verify(boolean formed,EFabricatorAppearance expected) {
        var mc=Minecraft.getInstance();var host=(TileEFabricatorController)mc.level.getBlockEntity(BASE);
        require(host!=null&&host.isFormed()==formed,"client formed metadata");var facing=host.getBlockState().getValue(BlockEFabricatorController.FACING);
        var all=formed?host.modelHiddenPositions():previouslyHidden;if(formed)previouslyHidden=all;
        require(all.size()==17,"source hide count");require(!BlockModelHider.isHidden(mc.level,BASE),"host hidden");
        var region=new net.minecraft.client.renderer.chunk.RenderRegionCache().createRegion(mc.level,BASE.offset(-9,-2,-9),BASE.offset(9,2,9),1);
        require(region!=null&&!region.getBlockState(BASE).isAir(),"render region missing host");
        for(var pos:all) {
            require(BlockModelHider.isHidden(mc.level,pos)==formed,"hidden neighbour "+pos);
            if(!formed&&pos.equals(BASE.above()))continue;
            var chunk=mc.level.getChunkAt(pos);var actual=chunk.getSection(chunk.getSectionIndex(pos.getY())).getBlockState(pos.getX()&15,pos.getY()&15,pos.getZ()&15);
            require(!actual.isAir()&&region.getBlockState(pos).isAir()==formed,"copied palette altered physical blocks or ignored hiding");
        }
        var pos=offset(BASE,facing,2,0,0);var worker=(TileEFabricatorWorker)mc.level.getBlockEntity(pos);
        require(worker.appearance()==expected,"wire LED expected "+expected+" got "+worker.appearance());
        require(worker.getUpdateTag().getAllKeys().equals(Set.of("Appearance"))&&worker.queuedCrafts()==0,"private queue leaked");
        require(worker.getBlockState().getValue(EFabricatorAppearance.STATUS)==EFabricatorAppearance.OFF,"cosmetic world palette mutation");
        int light=expected==EFabricatorAppearance.OFF?0:10;
        require(worker.getBlockState().getLightEmission(mc.level,pos)==light,"worker light emission");
        require(mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos)>=light,"light engine metadata not applied at "+pos+": client="+mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos)+", server="+world().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos));
        require(host.getBlockState().getLightEmission(mc.level,BASE)==(formed?10:0),"host light emission");
        fingerprint(worker.getBlockState(),expected);
    }
    private static void models() {
        var mc=Minecraft.getInstance();
        for(var block:EFabricatorRegistration.BLOCKS.getEntries())for(var state:block.get().getStateDefinition().getPossibleStates())fingerprint(state,null);
        for(var facing:Direction.Plane.HORIZONTAL) {
            var state=EFabricatorRegistration.WORKER.get().defaultBlockState().setValue(BlockEFabricatorPart.FACING,facing);var hashes=new HashSet<Long>();
            for(var status:EFabricatorAppearance.values())hashes.add(fingerprint(state,status));
            require(hashes.size()==3,"OFF/ON/RUN quads/UV not distinct "+facing);
        }
        for(var item:EFabricatorRegistration.ITEM_FORMS.values()) {
            var model=mc.getItemRenderer().getModel(new ItemStack(item.get()),null,null,0);
            require(model!=mc.getModelManager().getMissingModel()&&!model.getParticleIcon().contents().name().getPath().equals("missingno"),"missing inventory model");
        }
    }
    private static long fingerprint(BlockState state,EFabricatorAppearance appearance) {
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(state);
        require(model!=mc.getModelManager().getMissingModel(),"missing model "+state);
        var data=appearance==null?ModelData.EMPTY:ModelData.builder().with(EFabricatorAppearance.DATA,appearance).build();long hash=1;int count=0;
        for(int i=-1;i<6;i++)for(var quad:model.getQuads(state,i<0?null:Direction.values()[i],RandomSource.create(5),data,RenderType.cutout())) {
            require(!quad.getSprite().contents().name().getPath().equals("missingno"),"missing sprite "+state);count++;hash=31*hash+Arrays.hashCode(quad.getVertices());
            if(quad.getSprite().contents().name().getPath().contains("/bloom/"))require((quad.getVertices()[6]&0x00F000F0)==0x00F000F0,"glow lost full-bright data "+state);
        }
        require(count>0,"empty geometry "+state);return hash;
    }
    private static TileEFabricatorWorker worker(){var host=(TileEFabricatorController)world().getBlockEntity(BASE);return (TileEFabricatorWorker)world().getBlockEntity(offset(BASE,host.getBlockState().getValue(BlockEFabricatorController.FACING),2,0,0));}
    private static BlockPos offset(BlockPos origin,Direction facing,int x,int y,int z){return StructureMatcher.toWorldOffset(origin,facing,x,y,z);}
    private static net.minecraft.server.level.ServerLevel world(){return Minecraft.getInstance().getSingleplayerServer().overworld();}
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable error){Minecraft.getInstance().execute(()->fail(error));}});}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{});}
    private static void fail(Throwable error){Modularmachinery.LOG.error("[MMC][ef-appearance-client] OVERALL: FAIL",error);Minecraft.getInstance().stop();}
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
