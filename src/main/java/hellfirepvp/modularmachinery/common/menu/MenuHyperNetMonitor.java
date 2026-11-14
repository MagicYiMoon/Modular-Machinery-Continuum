/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.hypernet.HyperNetMonitor;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import java.util.UUID;

/** A separate, slot-free monitor window. The original controller remains its distance/identity anchor. */
public final class MenuHyperNetMonitor extends AbstractContainerMenu {
    public static final int OPEN_BUTTON=90;
    private final BlockPos position;
    private final UUID session;
    private final TileController owner;
    private final Player viewer;
    private final long revision;
    private final String machineName;
    private HyperNetMonitor.Snapshot snapshot;
    private int page;
    private long nextUpdate;
    private long lastUpdate=Long.MIN_VALUE;
    private boolean pageDirty;
    public MenuHyperNetMonitor(int id,Inventory inventory,BlockPos pos,UUID session){this(id,inventory,pos,session,null);}
    private MenuHyperNetMonitor(int id,Inventory inventory,BlockPos pos,UUID session,TileController owner){
        super(RegistryMenus.HYPERNET_MONITOR.get(),id);position=pos.immutable();this.session=session;this.owner=owner;viewer=inventory.player;
        revision=MachineReloadListener.getServerDefinitionRevision();
        machineName=owner==null?null:owner.getMachineName();
    }
    public static MenuHyperNetMonitor forController(int id,Inventory inventory,TileController owner,UUID session){return new MenuHyperNetMonitor(id,inventory,owner.getBlockPos(),session,owner);}
    public UUID session(){return session;}
    public HyperNetMonitor.Snapshot snapshot(){return snapshot;}
    public void accept(UUID session,HyperNetMonitor.Snapshot snapshot){if(owner==null&&this.session.equals(session))this.snapshot=snapshot;}
    public static boolean open(Player viewer,AbstractContainerMenu from,BlockPos pos){
        if(!(viewer instanceof ServerPlayer player)||player.containerMenu!=from||!from.stillValid(player)
                ||!(from instanceof MenuMachineController||from instanceof MenuFactoryController)
                ||!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof TileController owner)
                ||owner.isRemoved())return false;
        var expected=from instanceof MenuMachineController menu?menu.getControllerPos():((MenuFactoryController)from).getControllerPos();
        if(!expected.equals(pos)||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64)return false;
        if(!HyperNetMonitor.canRead(owner,player)){player.displayClientMessage(Component.translatable("message.modularmachinery.hypernet.no_permission"),true);return false;}
        var machine=MachineReloadListener.serverDefinitions().machines().findByName(owner.getMachineName());
        if(machine==null||machine.getHyperNetDefinition()==null)return false;
        var session=UUID.randomUUID();
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p)->forController(id,inventory,owner,session),
                Component.translatable("gui.modularmachinery.hypernet.title")),b->{b.writeBlockPos(pos);b.writeUUID(session);});
        return true;
    }
    @Override public void broadcastChanges(){
        if(owner!=null&&viewer.containerMenu==this&&stillValid(viewer)&&owner.getLevel().getGameTime()>lastUpdate
                &&(owner.getLevel().getGameTime()>=nextUpdate||pageDirty)){
            lastUpdate=owner.getLevel().getGameTime();nextUpdate=lastUpdate+20;pageDirty=false;
            snapshot=HyperNetMonitor.capture(owner,viewer,page);page=snapshot.page();
            var player=(ServerPlayer)viewer;
            ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new PktHyperNetMonitor(containerId,session,snapshot));
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int action){
        if(owner==null||player!=viewer||player.containerMenu!=this||!stillValid(player)||action<0||action>2)return false;
        if(action==2){
            NetworkHooks.openScreen((ServerPlayer)player,new SimpleMenuProvider((id,inventory,p)->owner instanceof TileFactoryController factory
                    ?MenuFactoryController.forController(id,inventory,factory):MenuMachineController.forController(id,inventory,owner),
                    Component.translatable("block.modularmachinery.blockcontroller")),b->b.writeBlockPos(position));
        }else{
            int pages=snapshot==null?1:snapshot.pages();page=Math.max(0,Math.min(pages-1,page+(action==0?-1:1)));
            // Page changes bypass the periodic delay, but not more than once in a server tick.
            pageDirty=true;broadcastChanges();
        }
        return true;
    }
    @Override public boolean stillValid(Player player){
        if(player!=viewer||!player.level().hasChunkAt(position)||!(player.level().getBlockEntity(position) instanceof TileController current)
                ||current.isRemoved()||owner!=null&&(current!=owner||revision!=MachineReloadListener.getServerDefinitionRevision()||!java.util.Objects.equals(machineName,current.getMachineName()))
                ||player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)>64)return false;
        return owner==null||HyperNetMonitor.canRead(owner,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
