/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.common.menu.MenuEStorageController;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Real menu data and vanilla item predicates/placed energy cells, isolated development world only. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EStorageCompletionClientVerification {
    private static boolean active, gallery;
    private static volatile boolean prepared;
    private static int ticks;
    private static final BlockPos BASE=new BlockPos(24,120,0);
    private static final List<ItemStack> items=new ArrayList<>();
    public static boolean active(){return active;}
    public static void begin(){active=true;}
    public static void tick() throws Exception {
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        ticks++;
        if(ticks<=200) {
            var screen=(ScreenEStorageController)mc.screen;var menu=screen.getMenu();var graphs=screen.graphs();
            if(ticks==1) {
                require(graphs.size()==(net.minecraftforge.fml.ModList.get().isLoaded("appmek")?9:7),"source graph option count");
                require(screen.graphCurrent(5)+screen.graphCurrent(6)+screen.graphCurrent(7)==screen.graphCurrent(0),"kind bytes not total");
                for(int i:new int[]{5,6,7})require(screen.graphMaximum(i)==menu.value(MenuEStorageController.CAPACITY),"kind graph must use global capacity");
                require(screen.graphMaximum(8)==menu.energyValue(MenuEStorageController.ENERGY)
                        &&screen.graphCurrent(8)==menu.energyValue(MenuEStorageController.DRAIN),"source drain/stored ratio changed");
            }
            if(ticks%20==1) {
                int index=(ticks-1)/20;
                if(index<graphs.size()) {
                    var graph=graphs.get(index); double scale=mc.getWindow().getGuiScale();
                    double x=(screen.getGuiLeft()+graph.x()+4)*scale, y=(screen.getGuiTop()+graph.y()+4)*scale;
                    org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),x,y);
                    // GLFW may not emit cursor callbacks while the unattended test window is unfocused.
                    // Dispatch the same native MouseHandler input, rather than setting the graph's focus.
                    var move=MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);
                    move.setAccessible(true);move.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x,y);
                }
            }
            if(ticks%20==18) {
                int index=(ticks-1)/20;
                if(index<graphs.size()) {
                    shot("es-final-graph-"+graphs.get(index).id()+"-zh.png");
                    require(screen.focusedGraph()==graphs.get(index).id(),"source hover focus failed: wanted="+graphs.get(index).id()+", actual="+screen.focusedGraph()+", mouse="+mc.mouseHandler.xpos()+","+mc.mouseHandler.ypos());
                }
            }
            if(ticks==195){mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();}
            return;
        }
        if(ticks==225){shot("es-final-graphs-en-small.png");mc.player.closeContainer();server(EStorageCompletionClientVerification::setupCells);return;}
        if(ticks<226||!prepared)return;
        if(ticks==275){verifyCells(false);gallery=true;shot("es-final-energy-five-states.png");}
        if(ticks==295){mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();}
        if(ticks==325){shot("es-final-energy-items-zh.png");server(()->{
            var world=mc.getSingleplayerServer().overworld();
            for(int row=0;row<3;row++)for(int col=0;col<5;col++)((TileEStorageEnergyCell)world.getBlockEntity(BASE.offset(col*2,row*2,0))).extract(Double.MAX_VALUE,false);
        });}
        if(ticks==365){verifyCells(true);shot("es-final-energy-discharged.png");
            Modularmachinery.LOG.info("[MMC][es-completion-client] PASS: source seven/nine graph options and hover, global capacity denominators, drain/stored ratio, real fractional AE menu sync, native pixel text, Chinese/English and 854x480; three energy tiers/five actual world statuses and matching item overrides, invalid NBT clamping, state light and real discharge; inspect screenshots");
            Modularmachinery.LOG.info("[MMC][es-completion-client] OVERALL: PASS");}
        if(ticks==385)mc.stop();
        if(ticks>800)throw new AssertionError("ES completion timeout");
    }
    private static void setupCells(){
        var mc=Minecraft.getInstance();var world=mc.getSingleplayerServer().overworld();
        int row=0;for(int tier:new int[]{4,6,9}) {
            int col=0;for(double fraction:new double[]{0,.05,.5,.7,.9}){
                var block=EStorageRegistration.ENERGY_CELLS.get(tier).get();var pos=BASE.offset(col*2,row*2,0);
                world.setBlockAndUpdate(pos,block.defaultBlockState().setValue(BlockEStorageEnergyCell.FACING,Direction.SOUTH));var cell=(TileEStorageEnergyCell)world.getBlockEntity(pos);
                cell.inject(block.capacity()*fraction,false);var stack=new ItemStack(block);stack.setTag(cell.energyTag());items.add(stack);col++;
            }row++;
        }
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,28.5,125,12,180,13);world.setDayTime(6000);
        prepared=true;
    }
    private static void verifyCells(boolean empty){
        var mc=Minecraft.getInstance();int row=0;
        for(int tier:new int[]{4,6,9}) {
            var fingerprints=new HashSet<String>();int col=0;
            for(var status:BlockEStorageEnergyCell.Status.values()) {
                var pos=BASE.offset(col*2,row*2,0);var state=mc.level.getBlockState(pos);
                var wanted=empty?BlockEStorageEnergyCell.Status.EMPTY:status;
                require(state.getValue(BlockEStorageEnergyCell.STATUS)==wanted,"actual energy state packet not applied");
                require(state.getLightEmission(mc.level,pos)==wanted.ordinal()*2,"source status emission");
                require(mc.level.getBlockEntity(pos).getUpdateTag().isEmpty(),"private stored energy broadcast");
                var model=mc.getBlockRenderer().getBlockModel(state);var fingerprint=fingerprint(model,state);
                if(!empty)fingerprints.add(fingerprint);
                var stack=items.get(row*5+col);var itemModel=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                var gui=itemModel.getTransforms().getTransform(ItemDisplayContext.GUI);
                require(gui.rotation.y()==225&&gui.scale.x()==.625F,"charge-state front GUI transform missing");
                var expected=EStorageRegistration.ENERGY_CELLS.get(tier).get().defaultBlockState().setValue(BlockEStorageEnergyCell.STATUS,status);
                require(fingerprint(itemModel,null).equals(fingerprint(mc.getBlockRenderer().getBlockModel(expected),expected)),"charged item model does not match tier/status");
                col++;
            }
            if(!empty)require(fingerprints.size()==5,"charge states baked identical/missing");
            var block=EStorageRegistration.ENERGY_CELLS.get(tier).get();
            var property=net.minecraft.client.renderer.item.ItemProperties.getProperty(block.asItem(),new ResourceLocation("modularmachinery","estorage_charge"));
            require(property!=null,"energy item predicate missing");
            for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}){
                var item=new ItemStack(block);item.getOrCreateTag().putDouble("energyStored",bad);
                require(property.call(item,mc.level,mc.player,0)==0,"invalid energy selected charged item mesh");
            }
            var spoof=new ItemStack(block);spoof.getOrCreateTag().putDouble("energyStored",block.capacity());spoof.getOrCreateTag().putDouble("maxEnergyStore",Double.MAX_VALUE);
            require(property.call(spoof,mc.level,mc.player,0)==4,"NBT spoofed tier capacity");row++;
        }
    }
    private static String fingerprint(net.minecraft.client.resources.model.BakedModel model,BlockState state){
        var parts=new ArrayList<String>();
        for(int face=-1;face<6;face++)for(var quad:model.getQuads(state,face<0?null:Direction.values()[face],RandomSource.create(0))) {
            require(!quad.getSprite().contents().name().getPath().equals("missingno"),"missing energy texture");
            parts.add(quad.getSprite().contents().name()+Arrays.toString(quad.getVertices()));
        }
        require(!parts.isEmpty(),"empty energy mesh");Collections.sort(parts);return parts.toString();
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        if(!active||!gallery)return;var g=event.getGuiGraphics();var mc=Minecraft.getInstance();
        g.fill(5,5,195,96,0xE0303030);
        int row=0;for(int tier:new int[]{4,6,9}){
            g.drawString(mc.font,SmoothUiFonts.compact(net.minecraft.network.chat.Component.literal("L"+tier)),8,20+row*25,0xFFFFFFFF,false);
            for(int col=0;col<5;col++)g.renderItem(items.get(row*5+col),35+col*30,15+row*25);row++;
        }
    }
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable failure){Modularmachinery.LOG.error("[MMC][es-completion-client] OVERALL: FAIL",failure);Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}});}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][es-completion-client] {}",r.getString()));}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
