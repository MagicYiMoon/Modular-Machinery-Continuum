/* SPDX-License-Identifier: GPL-3.0-only
 * Research task/core/leftover overclock concepts adapt NECO old.research.ResearchStation, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import net.minecraft.nbt.*;
import java.math.*;
import java.util.*;

/** One physical station, one persistent task; no global unlocks or offline work. Server thread only. */
final class HyperNetResearchStation {
    static final String PREFIX="modularmachinery.hypernet.research.";
    private final HyperNetNode node;
    private final TileController owner;
    private final String machine;
    private final double maxTechLevel;
    private long revision=Long.MIN_VALUE, lastBaseTick=-1, lastExtraTick=-1, pendingTick=-1;
    private String id="", pendingId="";
    private UUID provider;
    private CompoundTag network=new CompoundTag();
    private BigDecimal progress=BigDecimal.ZERO;
    private boolean valid=true, achieved;
    private Object identity=new Object();
    HyperNetResearchStation(HyperNetNode node,TileController owner,String machine,double maxTechLevel){
        this.node=node;this.owner=owner;this.machine=machine;this.maxTechLevel=maxTechLevel;
    }
    private void read(){
        if(revision==owner.getHyperNetDataRevision())return;
        revision=owner.getHyperNetDataRevision();valid=true;id="";provider=null;progress=BigDecimal.ZERO;achieved=false;
        identity=new Object();
        network=new CompoundTag();lastBaseTick=lastExtraTick=-1;pendingTick=-1;pendingId="";
        var raw=owner.getHyperNetDataPart("researchTask");if(raw==null)return;
        try {
            if(!(raw instanceof CompoundTag tag)||!tag.getAllKeys().equals(Set.of("version","machine","id","provider","network","progress","achieved","lastBaseTick","lastExtraTick"))
                    ||!tag.contains("version",Tag.TAG_INT)||tag.getInt("version")!=1||!tag.getString("machine").equals(machine)
                    ||!tag.hasUUID("provider")||!tag.contains("progress",Tag.TAG_STRING)||tag.getString("progress").length()>1200
                    ||!tag.contains("achieved",Tag.TAG_BYTE)||!tag.contains("lastBaseTick",Tag.TAG_LONG)||!tag.contains("lastExtraTick",Tag.TAG_LONG)
                    ||!tag.contains("network",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid research task");
            id=HyperNetResearch.checkedId(tag.getString("id"));provider=tag.getUUID("provider");progress=new BigDecimal(tag.getString("progress"));
            network=tag.getCompound("network").copy();
            if(!HyperNetResearch.supportedProgress(progress)||!network.hasUUID("networkOwner")||!network.contains("pos",Tag.TAG_LONG)
                    ||!network.contains("dimension",Tag.TAG_STRING)||!network.contains("machine",Tag.TAG_STRING)
                    ||network.getString("dimension").length()>256||network.getString("machine").length()>256)throw new IllegalArgumentException("Invalid task progress/binding");
            achieved=tag.getBoolean("achieved");lastBaseTick=tag.getLong("lastBaseTick");lastExtraTick=tag.getLong("lastExtraTick");
            if(lastBaseTick< -1||lastExtraTick< -1)throw new IllegalArgumentException("Invalid task clock");
        }catch(RuntimeException e){valid=false;id="";provider=null;progress=BigDecimal.ZERO;achieved=false;}
    }
    private void save(){
        var data=owner.getHyperNetData();
        if(id.isEmpty())data.remove("researchTask");else{
            var tag=new CompoundTag();tag.putInt("version",1);tag.putString("machine",machine);tag.putString("id",id);tag.putUUID("provider",provider);
            tag.put("network",network.copy());tag.putString("progress",progress.toString());tag.putBoolean("achieved",achieved);
            tag.putLong("lastBaseTick",lastBaseTick);tag.putLong("lastExtraTick",lastExtraTick);data.put("researchTask",tag);
        }
        owner.setHyperNetData(data);revision=owner.getHyperNetDataRevision();
    }
    HyperNetNode.ResearchTaskSnapshot snapshot(){read();return new HyperNetNode.ResearchTaskSnapshot(id,progress,achieved,valid);}
    Object identity(){read();return identity;}
    String provide(String name,UUID actor){
        read();if(!node.canManageResearch(actor))return PREFIX+"permission";
        if(!valid)return PREFIX+"invalid_data";
        if(!id.isEmpty())return PREFIX+"busy";
        var research=name==null?null:HyperNetResearchRegistry.find(name);if(research==null)return PREFIX+"unknown";
        if(research.techLevel()>maxTechLevel)return PREFIX+"tech";
        if(!node.researchNetworkAvailable())return PREFIX+"network";
        var databases=node.researchDatabases();
        for(var dep:research.dependencies())if(databases.stream().noneMatch(db->db.database().has(dep)))return PREFIX+"dependency";
        if(databases.stream().anyMatch(db->db.database().has(name)))return PREFIX+"already_complete";
        BigDecimal recovered=BigDecimal.ZERO;HyperNetNode target=null;
        for(var db:databases){
            if(!db.database().room(name))continue;
            var stored=db.database().progress(name);
            if(target==null||stored!=null&&(target.database().progress(name)==null||stored.compareTo(recovered)>0)){target=db;recovered=stored==null?BigDecimal.ZERO:stored;}
        }
        if(target==null)return PREFIX+"database";
        recovered=recovered.min(research.requiredPoints());
        // Existing progress above a lowered definition is already fully earned; do not regress its ledger.
        var stored=target.database().progress(name);
        if(stored==null&&!target.writeResearchProgress(name,recovered))return PREFIX+"database";
        id=name;identity=new Object();provider=actor;network=owner.getHyperNetBinding();progress=recovered;achieved=false;lastBaseTick=lastExtraTick=-1;save();
        if(owner instanceof TileFactoryController factory)factory.requestImmediateRecipeSearch();return null;
    }
    /** Source creative entry fills points; only a normal powered core tick may commit the result. */
    String provideCreative(String name,net.minecraft.server.level.ServerPlayer actor){
        if(actor==null||actor.level()!=owner.getLevel()||!actor.isCreative()||!actor.hasPermissions(2))return PREFIX+"permission";
        String failed=provide(name,actor.getUUID());if(failed!=null)return failed;
        progress=HyperNetResearchRegistry.find(id).requiredPoints();save();
        return null;
    }
    boolean cancel(UUID actor){
        read();if(!valid||!node.canManageResearch(actor)||id.isEmpty())return false;
        id="";identity=new Object();provider=null;progress=BigDecimal.ZERO;achieved=false;network=new CompoundTag();pendingTick=-1;save();
        if(owner instanceof TileFactoryController factory){for(var t:factory.getFactoryRecipeThreadList())if(node.isResearchCore(t))t.abandonRecipe();factory.requestImmediateRecipeSearch();}
        owner.markForUpdate();return true;
    }
    String failure(boolean enforceClock){
        read();if(!valid)return PREFIX+"invalid_data";if(id.isEmpty())return PREFIX+"no_task";
        if(achieved)return null; // Committed research may finish its already-paid core even if the database then stops.
        var research=HyperNetResearchRegistry.find(id);if(research==null)return PREFIX+"unknown";
        if(research.techLevel()>maxTechLevel)return PREFIX+"tech";
        if(!node.researchNetworkAvailable())return PREFIX+"network";
        if(!network.equals(owner.getHyperNetBinding()))return PREFIX+"network_changed";
        var databases=node.researchDatabases();
        if(databases.stream().anyMatch(db->db.database().has(id)))return null;
        if(target(databases)==null)return PREFIX+"database";
        if(enforceClock&&lastBaseTick==owner.getLevel().getGameTime())return PREFIX+"wait_tick";
        var needed=research.requiredPoints().subtract(recovered(databases)).max(BigDecimal.ZERO).min(research.minimumPerTick());
        return enforceClock&&needed.signum()>0&&!node.canResearchCompute(needed)?PREFIX+"computation":null;
    }
    private BigDecimal recovered(List<HyperNetNode> databases){
        var research=HyperNetResearchRegistry.find(id);BigDecimal result=progress;
        for(var db:databases){var n=db.database().progress(id);if(n!=null)result=result.max(n);}
        return result.min(research.requiredPoints());
    }
    private HyperNetNode target(List<HyperNetNode> databases){
        // Prefer an existing progress slot before reserving another database slot.
        for(var db:databases)if(db.database().room(id)&&db.database().progress(id)!=null)return db;
        for(var db:databases)if(db.database().room(id))return db;return null;
    }
    String work(){
        String failed=failure(true);if(failed!=null)return failed;if(achieved)return null;
        var research=HyperNetResearchRegistry.find(id);var databases=node.researchDatabases();
        if(databases.stream().anyMatch(db->db.database().has(id))){progress=research.requiredPoints();achieved=true;save();clearOtherProgress(databases);return null;}
        var target=target(databases);progress=recovered(databases);
        var amount=research.requiredPoints().subtract(progress).max(BigDecimal.ZERO).min(research.minimumPerTick());
        if(amount.signum()>0&&node.request(amount,false).granted()==0)return PREFIX+"computation";
        progress=progress.add(amount).min(research.requiredPoints());lastBaseTick=owner.getLevel().getGameTime();
        // Persist every paid point locally before touching another block's ledger.
        save();flush(target,databases,research);
        if(!achieved){pendingTick=lastBaseTick;pendingId=id;HyperNetLifecycle.researchWorked(node);}return null;
    }
    private void flush(HyperNetNode target,List<HyperNetNode> databases,HyperNetResearch research){
        var stored=target.database().progress(id);
        if(stored==null||stored.compareTo(progress)<0)target.writeResearchProgress(id,progress);
        if(progress.compareTo(research.requiredPoints())>=0&&target.completeResearch(id)){
            achieved=true;pendingTick=-1;save();clearOtherProgress(databases);
            // Notify only after a new result is stored and the local committed marker is saved.
            // The persisted marker prevents repeated notices while the core waits for output space.
            var player=provider==null?null:owner.getLevel().getServer().getPlayerList().getPlayer(provider);
            if(player!=null)hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),
                    new hellfirepvp.modularmachinery.common.network.HyperNetResearchPackets.Complete(research.id(),research.displayName(),research.previewIcon()));
        }
    }
    private void clearOtherProgress(List<HyperNetNode> databases){for(var db:databases)if(db.database().clearProgress(id))db.saveDatabase(db.database());}
    void extra(){
        // No deferred Runnable/world reference list: the weak node lifecycle visits each station at tick end.
        read();long now=owner.getLevel().getGameTime();
        if(pendingTick!=now||!pendingId.equals(id)||lastExtraTick==now||achieved||!node.working())return;
        pendingTick=-1;
        String failed=failure(false);if(failed!=null)return;
        var research=HyperNetResearchRegistry.find(id);var databases=node.researchDatabases();
        if(databases.stream().anyMatch(db->db.database().has(id))){progress=research.requiredPoints();achieved=true;save();clearOtherProgress(databases);alignCore();return;}
        var target=target(databases);if(target==null)return;
        var iface=owner.getSmartInterfaceData("overclocking");float raw=iface==null?1:iface.getValue();
        int overclock=Float.isFinite(raw)?Math.max(1,Math.min(5,(int)raw)):1;
        var amount=research.minimumPerTick().multiply(BigDecimal.valueOf(overclock-1))
                .min(research.requiredPoints().subtract(progress).max(BigDecimal.ZERO)).min(node.availableResearchComputation());
        if(amount.signum()<=0)return;
        if(node.request(amount,false).granted()==0)return;
        progress=progress.add(amount).min(research.requiredPoints());lastExtraTick=now;save();flush(target,databases,research);
        alignCore();
    }
    boolean align(FactoryRecipeThread thread){
        read();if(!valid||id.isEmpty())return false;
        var research=HyperNetResearchRegistry.find(id);if(research==null&&!achieved)return false;
        int duration=thread.getRecipeDuration();
        int ticks=achieved?duration:progress.multiply(BigDecimal.valueOf(duration)).divide(research.requiredPoints(),0,RoundingMode.FLOOR).intValueExact();
        thread.setServiceRecipeProgress(achieved?duration:Math.min(duration-1,ticks));return true;
    }
    private void alignCore(){if(owner instanceof TileFactoryController factory)for(var t:factory.getFactoryRecipeThreadList())if(node.isResearchCore(t)&&t.hasActiveRecipe())align(t);}
    void finished(){read();if(!valid||!achieved)return;id="";identity=new Object();provider=null;progress=BigDecimal.ZERO;achieved=false;network=new CompoundTag();pendingTick=-1;save();}
}
