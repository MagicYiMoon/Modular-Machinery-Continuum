/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import java.util.*;

/** Physical anchor, authenticated actor, exact menu session and observed-state token for every mutation. */
public final class MenuHyperNetResearch extends AbstractContainerMenu {
    private final BlockPos position;private final UUID session;private final Player viewer;
    private final TileHyperNetTerminal owner;private HyperNetNode center;private final long revision;
    private HyperNetResearchTerminal.View view;private HyperNetResearchTerminal.Snapshot snapshot;
    private HyperNetResearchTerminal.View previousView;private HyperNetNode previousCenter;private long previousTick;
    private long token,lastUpdate=Long.MIN_VALUE,lastRequest=Long.MIN_VALUE,nextUpdate;
    private int page;private String query="",selected="";private boolean dirty;
    public MenuHyperNetResearch(int id,Inventory inventory,BlockPos pos,UUID session){this(id,inventory,pos,session,null);}
    private MenuHyperNetResearch(int id,Inventory inventory,BlockPos pos,UUID session,TileHyperNetTerminal owner){
        super(RegistryMenus.HYPERNET_RESEARCH.get(),id);this.position=pos.immutable();this.session=session;viewer=inventory.player;this.owner=owner;
        center=owner==null?null:HyperNetResearchTerminal.center(owner,viewer);revision=MachineReloadListener.getServerDefinitionRevision();
        // Source positions: player inventory on the lower right, physical card slot just left of its hotbar.
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new net.minecraft.world.inventory.Slot(inventory,x+y*9+9,182+x*18,168+y*18));
        for(int x=0;x<9;x++)addSlot(new net.minecraft.world.inventory.Slot(inventory,x,182+x*18,226));
        var cards=owner==null?new net.minecraftforge.items.ItemStackHandler(1):owner.cards();
        addSlot(new net.minecraftforge.items.SlotItemHandler(cards,0,159,226){
            @Override public boolean mayPlace(ItemStack stack){return HyperNetCardInteractions.isCard(stack);}
            @Override public int getMaxStackSize(){return 1;}
        });
    }
    public static MenuHyperNetResearch forController(int id,Inventory inv,TileHyperNetTerminal owner,UUID session){return new MenuHyperNetResearch(id,inv,owner.getBlockPos(),session,owner);}
    public UUID session(){return session;}public HyperNetResearchTerminal.Snapshot snapshot(){return snapshot;}
    public void accept(UUID id,HyperNetResearchTerminal.Snapshot data){if(owner==null&&session.equals(id)&&(snapshot==null||data.token()>snapshot.token()))snapshot=data;}
    public static boolean open(Player viewer,TileHyperNetTerminal owner){
        if(!(viewer instanceof ServerPlayer player)||owner.isRemoved()||player.level()!=owner.getLevel()||!HyperNetMonitor.canRead(owner,player)
                ||player.distanceToSqr(owner.getBlockPos().getCenter())>64)return false;
        var session=UUID.randomUUID();NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->forController(id,inv,owner,session),
                Component.translatable(HyperNetResearchTerminal.PREFIX+"title")),b->{b.writeBlockPos(owner.getBlockPos());b.writeUUID(session);});return true;
    }
    @Override public boolean stillValid(Player player){
        if(player!=viewer||!player.level().hasChunkAt(position)||!(player.level().getBlockEntity(position) instanceof TileHyperNetTerminal tile)
                ||tile.isRemoved()||player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)>64)return false;
        return owner==null||tile==owner&&revision==MachineReloadListener.getServerDefinitionRevision()&&HyperNetMonitor.canRead(owner,player);
    }
    @Override public void broadcastChanges(){
        if(owner!=null&&viewer.containerMenu==this&&stillValid(viewer)){
            long now=owner.getLevel().getGameTime();if(now>lastUpdate&&(dirty||now>=nextUpdate)){
                lastUpdate=now;nextUpdate=now+15;dirty=false;
                previousView=view;previousCenter=center;previousTick=now;
                center=HyperNetResearchTerminal.center(owner,viewer);
                view=HyperNetResearchTerminal.capture(owner,viewer,++token,page,query,selected);snapshot=view.data();page=snapshot.page();
                selected=snapshot.selected()==null?"":snapshot.selected().row().id();
                if(viewer instanceof ServerPlayer player)ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new HyperNetResearchPackets.Update(containerId,session,snapshot));
            }
        }super.broadcastChanges();
    }
    public boolean request(Player player,HyperNetResearchPackets.Request request){
        if(owner==null||player!=viewer||player.containerMenu!=this||request.container()!=containerId||!session.equals(request.session())||!stillValid(player)||snapshot==null)return false;
        if(request.action()==3&&(!(player instanceof ServerPlayer)||!player.isCreative()||!player.hasPermissions(2)))return false;
        long now=owner.getLevel().getGameTime();if(lastRequest==now)return false;lastRequest=now;
        // One bounded previous observation covers a heartbeat already in flight when the player clicks.
        // Mutation still compares the exact center, station and task identity against live state below.
        var observed=view;var observedCenter=center;
        if(request.token()!=snapshot.token()){
            if(previousView==null||now-previousTick>40||request.token()!=previousView.data().token()){dirty=true;return false;}
            observed=previousView;observedCenter=previousCenter;
        }
        if(request.action()==0){page=request.page();query=request.query();selected=request.selected();dirty=true;broadcastChanges();return true;}
        if(HyperNetResearchTerminal.center(owner,player)!=observedCenter){dirty=true;return false;}
        var fresh=HyperNetResearchTerminal.capture(owner,player,token,page,query,selected);
        if(observed==null||fresh.station()==null||fresh.station()!=observed.station()||fresh.taskIdentity()!=observed.taskIdentity()){dirty=true;return false;}
        var shown=observed.data();
        boolean success;
        if(request.action()==1){
            var row=fresh.data().selected();
            if(row==null||shown.selected()==null||shown.selected().row().state()!=HyperNetResearchTerminal.State.AVAILABLE||!request.selected().equals(shown.selected().row().id())
                    ||!row.row().id().equals(request.selected())||row.row().state()!=HyperNetResearchTerminal.State.AVAILABLE)return false;
            var reason=fresh.station().provideResearchTask(request.selected(),player.getUUID());success=reason==null;
            if(!success)player.displayClientMessage(Component.translatable(reason),true);
        }else if(request.action()==3){
            var row=fresh.data().selected();
            if(row==null||shown.selected()==null||!shown.task().isEmpty()||!fresh.data().task().isEmpty()
                    ||shown.selected().row().state()==HyperNetResearchTerminal.State.COMPLETED
                    ||row.row().state()==HyperNetResearchTerminal.State.COMPLETED
                    ||!request.selected().equals(shown.selected().row().id())||!row.row().id().equals(request.selected()))return false;
            var reason=fresh.station().provideCreativeResearchTask(request.selected(),(ServerPlayer)player);success=reason==null;
            if(!success)player.displayClientMessage(Component.translatable(reason),true);
        }else{
            // A stale screen cannot cancel a newly provided task, even if the station position was reused.
            if(!shown.canCancel()||!fresh.data().canCancel()||!request.selected().equals(shown.task())||!shown.task().equals(fresh.data().task()))return false;
            success=fresh.station().cancelResearchTask(player.getUUID());
        }
        // Invalidate the observed token immediately, even when the one-packet-per-tick limit delays refresh.
        snapshot=null;view=null;previousView=null;previousCenter=null;dirty=true;broadcastChanges();return success;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||player.containerMenu!=this||index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<36){
            if(!moveItemStackTo(stack,36,37,false)&&!(index<27?moveItemStackTo(stack,27,36,false):moveItemStackTo(stack,0,27,false)))return ItemStack.EMPTY;
        }else if(!moveItemStackTo(stack,0,36,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return copy;
    }
}
