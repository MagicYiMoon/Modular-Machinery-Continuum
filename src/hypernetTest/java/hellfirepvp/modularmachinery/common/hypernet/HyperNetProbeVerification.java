/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import io.netty.buffer.Unpooled;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.ElementText;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Native TOP transport against an actually connected, paid hardware fixture. */
final class HyperNetProbeVerification {
    static void run(TileController controller,Player player,double expected){
        var world=(ServerLevel)controller.getLevel();var pos=controller.getBlockPos();
        var provider=mcjty.theoneprobe.TheOneProbe.theOneProbeImp.getProviders().stream()
                .filter(p->p instanceof hellfirepvp.modularmachinery.integration.top.MachineProbeProvider).findFirst().orElseThrow();
        var before=controller.getHyperNetData();var status=controller.peekHyperNetNode().status();var info=new ProbeInfo();
        provider.addProbeInfo(ProbeMode.EXTENDED,info,player,world,controller.getBlockState(),new IProbeHitData(){
            public BlockPos getPos(){return pos;} public Vec3 getHitVec(){return Vec3.atCenterOf(pos);}
            public Direction getSideHit(){return Direction.NORTH;}public ItemStack getPickBlock(){return ItemStack.EMPTY;}
        });
        require(before.equals(controller.getHyperNetData())&&status.equals(controller.peekHyperNetNode().status()),"probe changed runtime");
        var texts=texts(info);require(texts.stream().anyMatch(c->c.getContents() instanceof TranslatableContents t
                &&t.getKey().equals("top.modularmachinery.hypernet.consumed")&&t.getArgs()[0].equals(HyperNetTooltips.number(expected))),"actual committed quantity missing");
        if(status.role()==HyperNetDefinition.Role.PROCESSOR)require(texts.stream().anyMatch(c->c.getContents() instanceof TranslatableContents t
                &&t.getKey().equals("top.modularmachinery.hypernet.average_load")&&t.getArgs()[0].equals(HyperNetTooltips.number(controller.peekHyperNetNode().averageLoad()))),"source average load missing");
        if(status.role()!=HyperNetDefinition.Role.PROCESSOR)require(texts.stream().anyMatch(c->c.getContents() instanceof TranslatableContents t
                &&t.getKey().equals("top.modularmachinery.hypernet.demand")&&t.getArgs()[0].equals(HyperNetTooltips.number(status.demanded()))
                &&t.getArgs()[1].equals(status.demandConsumers())),"active demand/thread count missing");
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{
            ProbeInfo.writeElements(info.getElements(),buf);var bytes=new byte[buf.readableBytes()];buf.getBytes(buf.readerIndex(),bytes);
            String wire=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            require(!wire.contains("networkOwner")&&!wire.contains("hypernetData")&&!wire.contains("binding"),"private binding leaked");
            var decoded=new ProbeInfo();decoded.fromBytes(buf);var result=texts(decoded);
            require(buf.readableBytes()==0&&texts.size()==result.size(),"TOP element codec");
            for(int i=0;i<texts.size();i++)require(Component.Serializer.toJson(texts.get(i)).equals(Component.Serializer.toJson(result.get(i))),"TOP translated argument codec");
        }finally{buf.release();}
        if(status.role()==HyperNetDefinition.Role.CENTER)checkRetired(controller,player,provider);
    }
    /** Definition changes can precede the next controller tick. Inspection must leave the old runtime alone. */
    private static void checkRetired(TileController controller,Player player,IProbeInfoProvider provider){
        var world=(ServerLevel)controller.getLevel();var pos=controller.getBlockPos();
        var machine=hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader().findByName(controller.getMachineName());
        var definition=machine.getHyperNetDefinition();var runtime=controller.peekHyperNetNode();var before=controller.getHyperNetData();
        try{
            for(var replacement:java.util.Arrays.asList(null,HyperNetDefinition.parse(com.google.gson.JsonParser.parseString("{\"role\":\"node\"}")))){
                machine.setHyperNetDefinition(replacement);var info=new ProbeInfo();
                provider.addProbeInfo(ProbeMode.EXTENDED,info,player,world,controller.getBlockState(),new IProbeHitData(){
                    public BlockPos getPos(){return pos;}public Vec3 getHitVec(){return Vec3.atCenterOf(pos);}
                    public Direction getSideHit(){return Direction.NORTH;}public ItemStack getPickBlock(){return ItemStack.EMPTY;}
                });
                require(texts(info).stream().noneMatch(c->c.getContents() instanceof TranslatableContents t
                        &&t.getKey().startsWith("top.modularmachinery.hypernet.")),"retired runtime displayed after definition change");
                require(controller.peekHyperNetNode()==runtime&&!runtime.isClosed()&&before.equals(controller.getHyperNetData()),"probe replaced or changed retired runtime");
            }
        }finally{machine.setHyperNetDefinition(definition);}
        hellfirepvp.modularmachinery.Modularmachinery.LOG.info("[MMC][hypernet-top-reload] PASS: disabled and changed-role definitions exclude the old runtime without replacing it or modifying private data");
    }
    private static List<Component> texts(IProbeInfo info){var out=new ArrayList<Component>();for(var e:info.getElements()){
        if(e instanceof IProbeInfo child)out.addAll(texts(child));
        if(e instanceof ElementText){try{var field=e.getClass().getDeclaredField("text");field.setAccessible(true);out.add((Component)field.get(e));}catch(Exception error){throw new AssertionError(error);}}
    }return out;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError("HyperNet TOP: "+message);}
}
