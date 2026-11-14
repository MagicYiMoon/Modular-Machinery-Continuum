/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Read-only loaded-world monitor. The roster is bounded, transient registration history, not a chunk ticket. */
public final class HyperNetMonitor {
    public static final int PAGE_SIZE=6, MAX_MEMBERS=4096;
    public enum Fault {
        ONLINE, UNFORMED, STRUCTURE_PENDING, CHUNKS_UNAVAILABLE, REDSTONE_STOPPED, OVERHEATED,
        CIRCUIT_LOW, MODULES_MISSING, FACTORY_REQUIRED, CORE_MISSING, CORE_STOPPED,
        UNBOUND, WRONG_DIMENSION, CENTER_UNAVAILABLE, BINDING_REJECTED, CENTER_STOPPED,
        CONNECTION_PENDING, SAMPLING, NO_SUPPLY, UNAVAILABLE, RETIRED
    }
    public record Member(BlockPos pos,String machine,String name,String tier,HyperNetDefinition.Role role) {
        public Member {
            if(pos==null||machine==null||name==null||tier==null||role==null||machine.length()>256||name.length()>512||tier.length()>64)
                throw new IllegalArgumentException("Invalid HyperNet member");
            pos=pos.immutable();
        }
    }
    public record Node(Member member,boolean online,Fault fault,String detail,double consumed,double offered,
                       double capacity,double heat,int heatLimit,int durability,int durabilityLimit,double demanded,int demandConsumers) {
        public Node(Member member,boolean online,Fault fault,String detail,double consumed,double offered,
                    double capacity,double heat,int heatLimit,int durability,int durabilityLimit) {
            this(member,online,fault,detail,consumed,offered,capacity,heat,heatLimit,durability,durabilityLimit,0,0);
        }
        public Node {
            if(member==null||fault==null||detail==null||detail.length()>256||member.machine.length()>256
                    ||member.name.length()>512||member.tier.length()>64||!valid(consumed)||!valid(offered)
                    ||!valid(capacity)||!valid(heat)||!valid(demanded)||demandConsumers<0||heatLimit<0||durability<0||durabilityLimit<0||durability>durabilityLimit)
                throw new IllegalArgumentException("Invalid HyperNet monitor node");
        }
    }
    public record Snapshot(long tick,Node local,Node center,boolean networkVisible,int connections,int maxConnections,
                           int total,int page,boolean limited,List<Node> nodes) {
        public Snapshot {
            nodes=List.copyOf(nodes);
            if(tick<0||local==null||connections<0||maxConnections<connections||total<0||total>MAX_MEMBERS
                    ||page<0||page>=Math.max(1,(total+PAGE_SIZE-1)/PAGE_SIZE)||nodes.size()!=Math.min(PAGE_SIZE,Math.max(0,total-page*PAGE_SIZE))
                    ||!networkVisible&&(center!=null||total!=0||connections!=0||maxConnections!=0))
                throw new IllegalArgumentException("Invalid HyperNet monitor page");
        }
        public int pages(){return Math.max(1,(total+PAGE_SIZE-1)/PAGE_SIZE);}
        public void write(FriendlyByteBuf b){
            b.writeVarLong(tick);writeNode(b,local);b.writeBoolean(center!=null);if(center!=null)writeNode(b,center);
            b.writeBoolean(networkVisible);b.writeVarInt(connections);b.writeVarInt(maxConnections);b.writeVarInt(total);
            b.writeVarInt(page);b.writeBoolean(limited);b.writeVarInt(nodes.size());nodes.forEach(n->writeNode(b,n));
        }
        public static Snapshot read(FriendlyByteBuf b){
            long tick=b.readVarLong();var local=readNode(b);var center=b.readBoolean()?readNode(b):null;boolean visible=b.readBoolean();
            int connections=b.readVarInt(),max=b.readVarInt(),total=b.readVarInt(),page=b.readVarInt();boolean limited=b.readBoolean();int size=b.readVarInt();
            if(size<0||size>PAGE_SIZE)throw new IllegalArgumentException("Unbounded HyperNet monitor page");
            var nodes=new ArrayList<Node>();for(int i=0;i<size;i++)nodes.add(readNode(b));
            return new Snapshot(tick,local,center,visible,connections,max,total,page,limited,nodes);
        }
    }
    private HyperNetMonitor(){}
    private static boolean valid(double n){return Double.isFinite(n)&&n>=0;}
    private static String bounded(String s,int max){return s==null?"":s.substring(0,Math.min(max,s.length()));}
    static Member member(TileController owner,DynamicMachine machine){
        return new Member(owner.getBlockPos(),machine.getRegistryName(),bounded(machine.getLocalizedName(),512),
                machine.getDisplayInfo().tier(),machine.getHyperNetDefinition().role());
    }
    public static boolean canRead(TileController owner,Player player){
        var data=owner.getHyperNetData();
        return player.hasPermissions(2)||((owner.getOwner()==null||owner.getOwner().equals(player.getUUID()))
                &&(!data.hasUUID("networkOwner")||data.getUUID("networkOwner").equals(player.getUUID())));
    }
    static TileController loaded(ServerLevel world,BlockPos pos){
        var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!world.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(pos)))return null;
        var entity=chunk.getBlockEntity(pos,LevelChunk.EntityCreationType.CHECK);
        return entity instanceof TileController c&&!c.isRemoved()?c:null;
    }
    static DynamicMachine definition(TileController owner){return MachineReloadListener.serverDefinitions().machines().findByName(owner.getMachineName());}
    public static Snapshot capture(TileController owner,Player viewer,int requestedPage){
        var world=(ServerLevel)owner.getLevel();if(!world.getServer().isSameThread())throw new IllegalStateException("Monitor outside server thread");
        if(!canRead(owner,viewer))throw new IllegalArgumentException("HyperNet monitor access denied");
        var machine=definition(owner);if(machine==null||machine.getHyperNetDefinition()==null)throw new IllegalArgumentException("No HyperNet profile");
        var local=inspect(owner,machine);var runtime=owner.peekHyperNetNode();
        var center=runtime!=null&&runtime.matches(machine)?runtime.inspectionCenter():null;
        boolean visible=center!=null&&canRead(center.inspectionOwner(),viewer);
        var rows=new ArrayList<Node>();Node centerView=null;int connected=0,max=0;boolean limited=false;
        if(visible){
            var host=center.inspectionOwner();centerView=inspect(host,definition(host));max=definition(host).getHyperNetDefinition().maxConnections();
            connected=center.inspectionConnectionCount();limited=center.inspectionRosterLimited();
            for(var registered:center.inspectionMembers()){
                var peer=loaded(world,registered.pos());
                if(peer==null){rows.add(unavailable(registered));continue;}
                var peerMachine=definition(peer);
                // A reused position or a node moved to another network is no longer a member of this view.
                if(peerMachine==null||peerMachine.getHyperNetDefinition()==null||!registered.machine().equals(peer.getMachineName()))continue;
                if(!center.inspectionBindingMatches(peer))continue;
                rows.add(inspect(peer,peerMachine));
            }
        }
        rows.sort(Comparator.comparing((Node n)->n.member.role()).thenComparing(n->n.member.machine())
                .thenComparingInt(n->n.member.pos().getX()).thenComparingInt(n->n.member.pos().getY()).thenComparingInt(n->n.member.pos().getZ()));
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(rows.size()-1)/PAGE_SIZE)));
        return new Snapshot(Math.max(0,world.getGameTime()-1),local,centerView,visible,connected,max,rows.size(),page,limited,
                rows.subList(page*PAGE_SIZE,Math.min(rows.size(),(page+1)*PAGE_SIZE)));
    }
    private static Node unavailable(Member m){return new Node(m,false,Fault.UNAVAILABLE,"",0,0,0,0,0,0,0);}
    static Node inspect(TileController owner,DynamicMachine machine){
        var definition=machine.getHyperNetDefinition();var runtime=owner.peekHyperNetNode();
        Fault fault;String detail="";HyperNetNode.Status status=null;
        if(!owner.isFormed())fault=Fault.UNFORMED;
        else if(owner.isStructureCheckRequested())fault=Fault.STRUCTURE_PENDING;
        else if(!owner.isStructureChunkLoaded())fault=Fault.CHUNKS_UNAVAILABLE;
        else if(owner.isRedstoneStopped())fault=Fault.REDSTONE_STOPPED;
        else if(runtime==null)fault=Fault.SAMPLING;
        else if(!runtime.matches(machine))fault=Fault.RETIRED;
        else {var diagnostic=runtime.inspectionFault();fault=diagnostic.fault();detail=diagnostic.detail();}
        if(runtime!=null&&runtime.matches(machine))status=runtime.status();
        return new Node(member(owner,machine),fault==Fault.ONLINE&&status!=null&&status.online(),fault,bounded(detail,256),
                status==null?0:status.consumed(),status==null?0:status.offered(),
                definition.role()==HyperNetDefinition.Role.CENTER?definition.carrying():runtime!=null&&runtime.matches(machine)?runtime.inspectionCapacity():0,
                status==null?0:status.heat(),definition.thermal()==null?0:definition.thermal().overheatThreshold(),
                status==null?0:status.durability(),definition.circuit()==null?0:definition.circuit().capacity(),
                status==null?0:status.demanded(),status==null?0:status.demandConsumers());
    }
    private static void writeNode(FriendlyByteBuf b,Node n){
        var m=n.member;b.writeBlockPos(m.pos);b.writeUtf(m.machine,256);b.writeUtf(m.name,512);b.writeUtf(m.tier,64);b.writeEnum(m.role);
        b.writeBoolean(n.online);b.writeEnum(n.fault);b.writeUtf(n.detail,256);b.writeDouble(n.consumed);b.writeDouble(n.offered);
        b.writeDouble(n.capacity);b.writeDouble(n.heat);b.writeVarInt(n.heatLimit);b.writeVarInt(n.durability);b.writeVarInt(n.durabilityLimit);
        b.writeDouble(n.demanded);b.writeVarInt(n.demandConsumers);
    }
    private static Node readNode(FriendlyByteBuf b){return new Node(new Member(b.readBlockPos(),b.readUtf(256),b.readUtf(512),b.readUtf(64),b.readEnum(HyperNetDefinition.Role.class)),
            b.readBoolean(),b.readEnum(Fault.class),b.readUtf(256),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readVarInt());}
}
