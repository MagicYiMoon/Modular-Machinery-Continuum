/* SPDX-License-Identifier: GPL-3.0-only
 * Research grouping/hidden prerequisites adapt NECO GuiHyperNetTerminal and task packets, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import java.math.BigDecimal;
import java.util.*;

/** A bounded server-owned page, never a client-supplied research definition or progress write. */
public final class HyperNetResearchTerminal {
    public static final int PAGE_SIZE=9, MAX_PACKET=800*1024;
    public static final String PREFIX="gui.modularmachinery.research.";
    public enum State { RUNNING, AVAILABLE, COMPLETED, LOCKED, UNAVAILABLE }
    public record Row(String id,String name,State state,String progress,String required,String reason,HyperNetResearchIcon previewIcon) {
        public Row(String id,String name,State state,String progress,String required,String reason,String item){this(id,name,state,progress,required,reason,HyperNetResearchIcon.ofItem(item));}
        public String previewItem(){return previewIcon.item();}
        public Row {
            HyperNetResearch.checkedId(id);
            if(name==null||name.length()>256||state==null||reason==null||reason.length()>256)throw new IllegalArgumentException("Invalid research row");
            checkNumber(progress,false);checkNumber(required,true);
            if(previewIcon==null)throw new IllegalArgumentException("Invalid research icon");
        }
    }
    public record Detail(Row row,double tech,String minimum,List<String> dependencies,List<String> descriptions,List<String> unlocks) {
        public Detail {
            if(row==null||!Double.isFinite(tech)||tech<0)throw new IllegalArgumentException("Invalid research detail");
            checkNumber(minimum,true);dependencies=lines(dependencies,256);descriptions=lines(descriptions,1024);unlocks=lines(unlocks,1024);
        }
    }
    /** Last completed server tick. Reading never publishes supply, reconnects or consumes computation. */
    public record Network(long tick,boolean online,double consumed,double offered,int connections,double delivered) {
        public Network {
            if(tick<0||!Double.isFinite(consumed)||consumed<0||!Double.isFinite(offered)||offered<0||connections<0
                    ||!Double.isFinite(delivered)||delivered<0||!online&&(consumed!=0||offered!=0||connections!=0||delivered!=0))throw new IllegalArgumentException("Invalid research network statistics");
        }
        private void write(FriendlyByteBuf b){b.writeVarLong(tick);b.writeBoolean(online);b.writeDouble(consumed);b.writeDouble(offered);b.writeVarInt(connections);b.writeDouble(delivered);}
        private static Network read(FriendlyByteBuf b){return new Network(b.readVarLong(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readDouble());}
    }
    public record Snapshot(long token,int page,int total,String query,List<Row> rows,Detail selected,
                           String task,String taskProgress,boolean awaiting,boolean canCancel,String status,Network network) {
        public Snapshot {
            rows=List.copyOf(rows);
            if(network==null||token<1||page<0||total<0||total>HyperNetResearchRegistry.MAX_RESEARCH||query==null||query.length()>128
                    ||page>=Math.max(1,(total+PAGE_SIZE-1)/PAGE_SIZE)||rows.size()!=Math.min(PAGE_SIZE,total-page*PAGE_SIZE)
                    ||task==null||task.length()>256||status==null||status.length()>256)throw new IllegalArgumentException("Invalid research page");
            if(!task.isEmpty())HyperNetResearch.checkedId(task);checkNumber(taskProgress,false);
            if(selected!=null&&rows.stream().noneMatch(r->r.equals(selected.row())))throw new IllegalArgumentException("Off-page selection");
            if(canCancel&&task.isEmpty())throw new IllegalArgumentException("Empty cancellation");
        }
        public int pages(){return Math.max(1,(total+PAGE_SIZE-1)/PAGE_SIZE);}
        public void write(FriendlyByteBuf b){
            b.writeVarLong(token);b.writeVarInt(page);b.writeVarInt(total);b.writeUtf(query,128);b.writeVarInt(rows.size());rows.forEach(r->writeRow(b,r));
            b.writeBoolean(selected!=null);if(selected!=null){var d=selected;writeRow(b,d.row);b.writeDouble(d.tech);b.writeUtf(d.minimum,1200);
                writeLines(b,d.dependencies,256);writeLines(b,d.descriptions,1024);writeLines(b,d.unlocks,1024);}
            b.writeUtf(task,256);b.writeUtf(taskProgress,1200);b.writeBoolean(awaiting);b.writeBoolean(canCancel);b.writeUtf(status,256);network.write(b);
        }
        public static Snapshot read(FriendlyByteBuf b){
            if(b.readableBytes()>MAX_PACKET)throw new IllegalArgumentException("Oversized research packet");
            long token=b.readVarLong();int page=b.readVarInt(),total=b.readVarInt();String query=b.readUtf(128);int count=b.readVarInt();
            if(count<0||count>PAGE_SIZE)throw new IllegalArgumentException("Unbounded research rows");
            var rows=new ArrayList<Row>();for(int i=0;i<count;i++)rows.add(readRow(b));
            Detail detail=b.readBoolean()?new Detail(readRow(b),b.readDouble(),b.readUtf(1200),readLines(b,256),readLines(b,1024),readLines(b,1024)):null;
            return new Snapshot(token,page,total,query,rows,detail,b.readUtf(256),b.readUtf(1200),b.readBoolean(),b.readBoolean(),b.readUtf(256),Network.read(b));
        }
    }
    public record View(Snapshot data,HyperNetNode station,Object taskIdentity) {}
    private HyperNetResearchTerminal(){}
    private static void checkNumber(String s,boolean positive){
        if(s==null||s.length()>1200)throw new IllegalArgumentException("Oversized research number");
        var n=new BigDecimal(s);if(!HyperNetResearch.supportedProgress(n)||positive&&n.signum()==0)throw new IllegalArgumentException("Invalid research number");
    }
    private static List<String> lines(List<String> value,int max){
        var copy=List.copyOf(value);if(copy.size()>64||copy.stream().anyMatch(s->s.length()>max))throw new IllegalArgumentException("Unbounded research text");return copy;
    }
    private static void writeLines(FriendlyByteBuf b,List<String> v,int max){b.writeVarInt(v.size());v.forEach(s->b.writeUtf(s,max));}
    private static List<String> readLines(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Unbounded research text");var v=new ArrayList<String>();for(int i=0;i<n;i++)v.add(b.readUtf(max));return v;}
    private static void writeRow(FriendlyByteBuf b,Row r){b.writeUtf(r.id,256);b.writeUtf(r.name,256);b.writeEnum(r.state);b.writeUtf(r.progress,1200);b.writeUtf(r.required,1200);b.writeUtf(r.reason,256);r.previewIcon.write(b);}
    private static Row readRow(FriendlyByteBuf b){return new Row(b.readUtf(256),b.readUtf(256),b.readEnum(State.class),b.readUtf(1200),b.readUtf(1200),b.readUtf(256),HyperNetResearchIcon.read(b));}
    public static HyperNetNode center(TileController anchor,Player viewer){
        if(!(anchor instanceof TileHyperNetTerminal terminal)||!terminal.servicePowered()||!terminal.isFormed()||terminal.isStructureCheckRequested())return null;
        var node=anchor.peekHyperNetNode();var definition=HyperNetMonitor.definition(anchor);
        if(node==null||definition==null||!node.matches(definition)||!HyperNetMonitor.canRead(anchor,viewer))return null;
        var center=node.inspectionCenter();return center!=null&&HyperNetMonitor.canRead(center.inspectionOwner(),viewer)?center:null;
    }
    public static View capture(TileController anchor,Player viewer,long token,int requestedPage,String query,String selected){
        var world=(net.minecraft.server.level.ServerLevel)anchor.getLevel();
        if(!world.getServer().isSameThread()||!HyperNetMonitor.canRead(anchor,viewer))throw new IllegalArgumentException("Research access denied");
        var center=center(anchor,viewer);var peers=center==null?List.<HyperNetNode>of():center.inspectionResearchPeers();
        var station=peers.stream().filter(n->n.definition().role()==HyperNetDefinition.Role.RESEARCH_STATION
                &&HyperNetMonitor.canRead(n.inspectionOwner(),viewer)).findFirst().orElse(null);
        var databases=peers.stream().filter(n->n.definition().role()==HyperNetDefinition.Role.DATABASE
                &&HyperNetMonitor.canRead(n.inspectionOwner(),viewer)&&n.status().online()&&n.database().valid()).toList();
        var task=station==null?null:station.researchTask();boolean permitted=station!=null&&station.canManageResearch(viewer.getUUID());
        var centerStatus=center==null?null:center.status();
        boolean network=centerStatus!=null&&centerStatus.online();
        // Upstream sums NetNodeImpl's registered recipe demand plus ResearchStation.consumption.
        // The actual delivered total remains a separate diagnostic, not the source "consumption" label.
        var sourceConsumption=network?BigDecimal.valueOf(centerStatus.demanded()):BigDecimal.ZERO;
        if(network)for(var peer:peers)if(peer.definition().role()==HyperNetDefinition.Role.RESEARCH_STATION){
            var sample=peer.status();if(sample.online())sourceConsumption=sourceConsumption.add(BigDecimal.valueOf(sample.consumed()));
        }
        var metrics=new Network(Math.max(0,world.getGameTime()-1),network,Math.min(Double.MAX_VALUE,sourceConsumption.doubleValue()),
                network?centerStatus.offered():0,network?centerStatus.connections():0,network?centerStatus.consumed():0);
        String status=anchor instanceof TileHyperNetTerminal terminal&&!terminal.serviceFailure().isEmpty()?terminal.serviceFailure()
                :!network?HyperNetResearchStation.PREFIX+"network":station==null?PREFIX+"no_station"
                :!permitted?HyperNetResearchStation.PREFIX+"permission":!task.valid()?HyperNetResearchStation.PREFIX+"invalid_data":"";
        var rows=new ArrayList<Row>();String needle=query.strip().toLowerCase(Locale.ROOT);
        for(var research:HyperNetResearchRegistry.snapshot().values()){
            boolean complete=databases.stream().anyMatch(db->db.database().has(research.id()));
            boolean running=task!=null&&task.valid()&&task.id().equals(research.id());
            boolean dependencies=research.dependencies().stream().allMatch(dep->databases.stream().anyMatch(db->db.database().has(dep)));
            if(research.hidden()&&!dependencies&&!complete&&!running)continue;
            if(!needle.isEmpty()&&!research.id().toLowerCase(Locale.ROOT).contains(needle)&&!research.displayName().toLowerCase(Locale.ROOT).contains(needle))continue;
            BigDecimal progress=running?task.progress():BigDecimal.ZERO;
            for(var db:databases){var p=db.database().progress(research.id());if(p!=null)progress=progress.max(p);}
            if(complete)progress=research.requiredPoints();progress=progress.min(research.requiredPoints());
            String reason=complete?HyperNetResearchStation.PREFIX+"already_complete":!status.isEmpty()?status
                    :!task.id().isEmpty()?HyperNetResearchStation.PREFIX+"busy":!dependencies?HyperNetResearchStation.PREFIX+"dependency"
                    :research.techLevel()>station.definition().maxTechLevel()?HyperNetResearchStation.PREFIX+"tech"
                    :research.minimumPerTick().doubleValue()>center.status().offered()?HyperNetResearchStation.PREFIX+"computation"
                    :databases.stream().noneMatch(db->db.database().room(research.id()))?HyperNetResearchStation.PREFIX+"database":"";
            State state=running?State.RUNNING:complete?State.COMPLETED:!dependencies?State.LOCKED:reason.isEmpty()?State.AVAILABLE:State.UNAVAILABLE;
            rows.add(new Row(research.id(),research.displayName(),state,progress.toString(),research.requiredPoints().toString(),reason,research.previewIcon()));
        }
        // Source groups by prerequisites, not by the temporary availability of power or storage.
        rows.sort(Comparator.comparingInt((Row r)->switch(r.state()){
            case RUNNING->0;case AVAILABLE,UNAVAILABLE->1;case COMPLETED->2;case LOCKED->3;
        }).thenComparingDouble(r->HyperNetResearchRegistry.find(r.id()).techLevel()).thenComparing(Row::id));
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(rows.size()-1)/PAGE_SIZE)));int total=rows.size();
        var visible=List.copyOf(rows.subList(page*PAGE_SIZE,Math.min(total,(page+1)*PAGE_SIZE)));
        var chosen=visible.stream().filter(r->r.id().equals(selected)).findFirst().orElse(visible.isEmpty()?null:visible.get(0));
        Detail detail=null;if(chosen!=null){var r=HyperNetResearchRegistry.find(chosen.id());detail=new Detail(chosen,r.techLevel(),r.minimumPerTick().toString(),r.dependencies(),r.descriptions(),r.unlockDescriptions());}
        return new View(new Snapshot(token,page,total,query,visible,detail,task==null?"":task.id(),task==null?"0":task.progress().toString(),
                task!=null&&task.awaitingCoreCompletion(),permitted&&task.valid()&&!task.id().isEmpty(),status,metrics),station,station==null?null:station.inspectionResearchTaskIdentity());
    }
}
