/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.world.BlockModelHider;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Disposable real world/wire/model fixture. Does not manufacture fake AE crafting tasks. Never shipped. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ECalculatorAppearanceClientVerification {
    private static final BlockPos BASE=new BlockPos(0,120,0);
    private static int total,ticks;
    private static boolean configured,language;
    private static List<BlockPos> previouslyHidden=List.of();
    private static Set<BlockPos> removedCells=Set.of();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.ecAppearanceTest"))return;
        var mc=Minecraft.getInstance();
        if(++total>6000){fail(new AssertionError("client timeout"));return;}
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        try {
            if(!language){language=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}
            if(!configured){configured=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
                mc.options.renderDistance().set(6);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
                server(ECalculatorAppearanceClientVerification::setup);return;}
            switch(++ticks) {
                case 100 -> { models();verify(true);shot("ec-appearance-01-formed-c4.png");server(()->camera(BASE.east(12),Direction.NORTH)); }
                case 125 -> { shot("ec-appearance-02-formed-c6.png");server(()->camera(BASE.east(24),Direction.NORTH)); }
                case 150 -> { shot("ec-appearance-03-formed-c9.png");server(()->camera(BASE,Direction.NORTH)); }
                case 175 -> server(()->world().setBlockAndUpdate(BASE.above(),Blocks.AIR.defaultBlockState()));
                case 220 -> { verify(false);shot("ec-appearance-04-broken.png");server(()->world().setBlockAndUpdate(BASE.above(),ECalculatorRegistration.CASING.get().defaultBlockState())); }
                case 275 -> { verify(true);shot("ec-appearance-05-repaired.png");mc.reloadResourcePacks(); }
                case 310 -> { models();verify(true);shot("ec-appearance-06-reloaded.png");server(()->rotate(Direction.EAST)); }
                case 365 -> { verify(true);shot("ec-appearance-07-east.png");server(()->rotate(Direction.SOUTH)); }
                case 420 -> { verify(true);shot("ec-appearance-08-south.png");server(()->rotate(Direction.WEST)); }
                case 475 -> { verify(true);shot("ec-appearance-09-west.png");server(()->{
                    var host=(TileECalculatorController)world().getBlockEntity(BASE);host.onChunkUnloaded();world().setBlockAndUpdate(BASE,Blocks.AIR.defaultBlockState());
                }); }
                case 520 -> {
                    for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=0;z<=7;z++)
                        require(!BlockModelHider.isHidden(mc.level,offset(BASE,Direction.WEST,x,y,z)),"removed host left hidden cell");
                    shot("ec-appearance-10-removed.png");
                    Modularmachinery.LOG.info("[MMC][ec-appearance-client] PASS: all EC baked variants/facings and nonmissing textures, virtual OFF/ON/RUN selection, real bounded BE metadata and light, unchanged world palettes/private data, measured workers tail, copied chunk hiding, break/repair, resource reload, four world orientations and controller unload/removal");
                    Modularmachinery.LOG.info("[MMC][ec-appearance-client] OVERALL: PASS");mc.stop();
                }
            }
        } catch(Throwable error){fail(error);}
    }
    private static void setup() {
        world().setDayTime(6000);world().setWeatherParameters(6000,0,false,false);
        world().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS).set(false,world().getServer());
        for(int tier:new int[]{4,6,9}) definitions(tier);
        for(int i=0;i<3;i++) build(BASE.east(i*12),new int[]{4,6,9}[i],Direction.NORTH);
        var player=Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();camera(BASE,Direction.NORTH);
    }
    private static void definitions(int tier) {
        var body=new MachinePattern();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=0;z<=1;z++)
            if(x!=0||y!=0||z!=0)body.addPart(new MachinePattern.Part(x,y,z,"modularmachinery:ecalculator_casing"));
        var machine=new DynamicMachine(ECalculatorRegistration.machineName(tier),"EC appearance fixture",body);
        machine.setRequiresBlueprint(false);
        var segment=new MachinePattern();
        segment.addPart(new MachinePattern.Part(0,0,0,"modularmachinery:ecalculator_thread_core_l"+tier));
        segment.addPart(new MachinePattern.Part(0,1,0,"modularmachinery:ecalculator_parallel_proc_l"+tier));
        segment.addPart(new MachinePattern.Part(0,-1,0,"modularmachinery:ecalculator_thread_core_hyper_l"+tier));
        segment.addPart(new MachinePattern.Part(0,0,1,"modularmachinery:ecalculator_transmitter_bus"));
        segment.addPart(new MachinePattern.Part(0,1,1,"modularmachinery:ecalculator_cell_drive"));
        segment.addPart(new MachinePattern.Part(0,-1,1,"modularmachinery:ecalculator_cell_drive"));
        var end=new MachinePattern();end.addPart(new MachinePattern.Part(0,0,0,"modularmachinery:ecalculator_tail_l"+tier));
        for(var p:List.of(new BlockPos(0,-1,1),new BlockPos(0,-1,0),new BlockPos(0,0,1),new BlockPos(0,1,1),new BlockPos(0,1,0)))
            end.addPart(new MachinePattern.Part(p.getX(),p.getY(),p.getZ(),"modularmachinery:ecalculator_casing"));
        var workers=new DynamicPattern("workers");workers.addFace(Direction.SOUTH);workers.setMinSize(2);workers.setMaxSize(2);
        workers.setStructureSizeOffsetStart(new BlockPos(0,0,2));workers.setStructureSizeOffset(new BlockPos(0,0,2));workers.setPattern(segment);workers.setPatternEnd(end);machine.addDynamicPattern(workers);
        MachineReloadListener.getLoader().getMachines().put(new ResourceLocation("modularmachinery",machine.getRegistryName()),machine);
    }
    private static void build(BlockPos origin,int tier,Direction facing) {
        var machine=MachineReloadListener.getLoader().findByName(ECalculatorRegistration.machineName(tier));
        var parts=new ArrayList<>(machine.getPattern().getParts());parts.addAll(machine.getDynamicPatterns().get("workers").instantiate(2).getParts());
        world().setBlockAndUpdate(origin,ECalculatorRegistration.CONTROLLERS.get(tier).get().defaultBlockState().setValue(BlockECalculatorController.FACING,facing));
        for(var part:parts) {
            var pos=offset(origin,facing,part.x(),part.y(),part.z());
            var state=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(part.elements())).defaultBlockState();
            if(state.hasProperty(BlockECalculatorPart.FACING))state=state.setValue(BlockECalculatorPart.FACING,facing);
            world().setBlockAndUpdate(pos,state);
            if(world().getBlockEntity(pos) instanceof TileECalculatorDrive drive)
                drive.insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.values()[tier==4?0:tier==6?1:2]).get()));
        }
        var host=(TileECalculatorController)world().getBlockEntity(origin);host.refreshStructure();require(host.isAssembled(),"fixture not formed "+facing);
        // Login metadata includes bounded measured workers, as on a dedicated server.
        Minecraft.getInstance().execute(()->{
            var client=MachineReloadListener.getLoader().getMachines();
            client.put(new ResourceLocation("modularmachinery",machine.getRegistryName()),machine);
        });
    }
    private static void rotate(Direction facing) {
        var old=(TileECalculatorController)world().getBlockEntity(BASE);var previous=old.getBlockState().getValue(BlockECalculatorController.FACING);
        var machine=MachineReloadListener.getLoader().findByName(ECalculatorRegistration.machineName(4));
        var parts=new ArrayList<>(machine.getPattern().getParts());parts.addAll(machine.getDynamicPatterns().get("workers").instantiate(2).getParts());
        var removed=new HashSet<BlockPos>();
        for(var part:parts)removed.add(offset(BASE,previous,part.x(),part.y(),part.z()));
        for(var part:parts)removed.remove(offset(BASE,facing,part.x(),part.y(),part.z()));
        removedCells=Set.copyOf(removed);
        world().setBlockAndUpdate(BASE,Blocks.AIR.defaultBlockState());
        for(var part:parts)world().setBlockAndUpdate(offset(BASE,previous,part.x(),part.y(),part.z()),Blocks.AIR.defaultBlockState());
        world().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(BASE).inflate(10)).forEach(net.minecraft.world.entity.item.ItemEntity::discard);
        build(BASE,4,facing);camera(BASE,facing);
    }
    private static void camera(BlockPos origin,Direction facing) {
        var pos=offset(origin,facing,6,4,-5);
        float yaw=switch(facing){case EAST->135;case SOUTH->225;case WEST->315;default->45;};
        Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0).teleportTo(world(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,yaw,18);
    }
    private static void verify(boolean formed) {
        var mc=Minecraft.getInstance();var host=(TileECalculatorController)mc.level.getBlockEntity(BASE);
        require(host!=null&&host.isFormed()==formed,"client formed flag");var facing=host.getBlockState().getValue(BlockECalculatorController.FACING);
        for(var pos:removedCells)require(mc.level.getBlockState(pos).isAir(),"removed physical cell remained after rotation "+pos);
        var all=formed?host.modelHiddenPositions():previouslyHidden;
        if(formed)previouslyHidden=all;
        require(all.size()==22,"workers tail sync/hide count: "+all.size());
        for(var pos:all)require(BlockModelHider.isHidden(mc.level,pos)==formed,"source hidden neighbour "+pos);
        require(!BlockModelHider.isHidden(mc.level,BASE),"controller itself hidden");
        var region=new net.minecraft.client.renderer.chunk.RenderRegionCache().createRegion(mc.level,BASE.offset(-9,-2,-9),BASE.offset(9,2,9),1);
        require(region!=null&&!region.getBlockState(BASE).isAir(),"render region missing controller");
        for(var pos:all) {
            if(!formed&&pos.equals(BASE.above()))continue;
            var chunk=mc.level.getChunkAt(pos);var actual=chunk.getSection(chunk.getSectionIndex(pos.getY())).getBlockState(pos.getX()&15,pos.getY()&15,pos.getZ()&15);
            require(!actual.isAir()&&region.getBlockState(pos).isAir()==formed,"copied render palette altered physical blocks or ignored hiding");
        }
        for(int z=2;z<6;z++)for(int y=-1;y<=1;y++) {
            var pos=offset(BASE,facing,0,y,z);var part=(TileECalculatorPart)mc.level.getBlockEntity(pos);var appearance=part.appearance();
            require(appearance.status()==(formed?ECalculatorAppearance.Status.ON:ECalculatorAppearance.Status.OFF),"wire status "+pos);
            require(part.getUpdateTag().getAllKeys().equals(Set.of("Appearance")),"private contents sent to client");
            var state=part.getBlockState();require(!state.hasProperty(ECalculatorAppearance.STATUS)||state.getValue(ECalculatorAppearance.STATUS)==ECalculatorAppearance.Status.OFF,"cosmetics in world palette");
            require(state.getLightEmission(mc.level,pos)==appearance.light(part.specification().kind(),state.getValue(BlockECalculatorPart.MEMORY)),"client light metadata");
            require(mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos)>=appearance.light(part.specification().kind(),state.getValue(BlockECalculatorPart.MEMORY)),"light engine did not apply metadata");
            if(part instanceof TileECalculatorDrive drive)require(!drive.hasMemory()&&state.getValue(BlockECalculatorPart.MEMORY)==1,"private inventory leaked/grade not synced");
            require(fingerprint(state,appearance)!=0,"empty virtual model");
        }
    }
    private static void models() {
        for(var block:ECalculatorRegistration.CONTROLLERS.values())for(var state:block.get().getStateDefinition().getPossibleStates())fingerprint(state,null);
        for(var block:ECalculatorRegistration.PARTS.values()) {
            for(var state:block.get().getStateDefinition().getPossibleStates())fingerprint(state,null);
            if(block.get().specification().normalSlots()+block.get().specification().hyperSlots()>0) {
                var state=block.get().defaultBlockState();var hashes=new HashSet<Long>();
                for(var status:ECalculatorAppearance.Status.values())hashes.add(fingerprint(state,new ECalculatorAppearance(status,ECalculatorAppearance.Link.NONE,ECalculatorAppearance.Tier.NONE)));
                require(hashes.size()==3,"core OFF/ON/RUN model/UV not distinct");
            }
        }
    }
    private static long fingerprint(BlockState state,ECalculatorAppearance appearance) {
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(state);
        require(model!=mc.getModelManager().getMissingModel(),"missing baked model "+state);
        var data=appearance==null?ModelData.EMPTY:ModelData.builder().with(ECalculatorAppearance.DATA,appearance).build();long hash=1;int count=0;
        for(int i=-1;i<6;i++)for(var quad:model.getQuads(state,i<0?null:Direction.values()[i],RandomSource.create(5),data,RenderType.cutout())) {
            require(!quad.getSprite().contents().name().getPath().equals("missingno"),"missing quad "+state);count++;hash=31*hash+Arrays.hashCode(quad.getVertices());
            if(quad.getSprite().contents().name().getPath().contains("/bloom/"))
                require((quad.getVertices()[6]&0x00F000F0)==0x00F000F0,"source glow face lost full-bright data "+state);
        }
        require(count>0,"empty baked geometry "+state);return hash;
    }
    private static BlockPos offset(BlockPos origin,Direction facing,int x,int y,int z){return StructureMatcher.toWorldOffset(origin,facing,x,y,z);}
    private static net.minecraft.server.level.ServerLevel world(){return Minecraft.getInstance().getSingleplayerServer().overworld();}
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable error){Minecraft.getInstance().execute(()->fail(error));}});}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{});}
    private static void fail(Throwable error){Modularmachinery.LOG.error("[MMC][ec-appearance-client] OVERALL: FAIL",error);Minecraft.getInstance().stop();}
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
