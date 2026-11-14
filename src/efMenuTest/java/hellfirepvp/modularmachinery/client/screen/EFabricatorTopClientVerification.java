/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.*;
import mcjty.theoneprobe.network.*;
import mcjty.theoneprobe.rendering.OverlayRenderer;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Native TOP packets and renderer with explicit saved work, not a claim of native AE orders. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EFabricatorTopClientVerification {
    private static boolean active;
    private static volatile boolean prepared;
    private static int ticks, frames;
    private static TileEFabricatorController owner;
    private static TileEFabricatorWorker worker;
    private static CompoundTag work;
    private static ProbeMode mode = ProbeMode.NORMAL;
    private static ProbeInfo shown;
    public static boolean active() { return active; }
    public static void begin(TileEFabricatorController controller) {
        active = true; owner = controller; var mc = Minecraft.getInstance(); mc.player.closeContainer();
        mc.options.guiScale().set(2); mc.resizeDisplay(); mcjty.theoneprobe.config.Config.isVisible.set(false);
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                // TOP defaults to needsProbe=3 and downgrades EXTENDED requests without a real probe.
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(mcjty.theoneprobe.items.ModItems.PROBE));
                player.teleportTo((net.minecraft.server.level.ServerLevel)owner.getLevel(), .5, 121, 5.5, 180, 25);
                worker = (TileEFabricatorWorker)owner.getLevel().getBlockEntity(owner.getBlockPos().east());
                hellfirepvp.modularmachinery.integration.top.EFabricatorProbeRegressionChecks.run(
                        (net.minecraft.server.level.ServerLevel)owner.getLevel(), worker, player);
                owner.setModes(true, false); var q = new EFabricatorQueue();
                var items = new Item[]{Items.DIAMOND, Items.APPLE, Items.OAK_PLANKS, Items.CAKE, Items.IRON_INGOT, Items.BUCKET, Items.GOLD_INGOT, Items.EMERALD};
                for (int i = 0; i < 40; i++) {
                    var output = new ItemStack(items[i % items.length]); output.getOrCreateTag().putString("Private", "hidden");
                    q.offer(new EFabricatorQueue.Work(i == 0 ? 2 : 1, output, List.of()), 512);
                }
                work = new CompoundTag(); work.putInt("Version", 1); work.putDouble("Energy", 250000.5); work.put("Queue", q.save());
                worker.restoreWorkData(work); prepared = true;
            } catch (Throwable failure) { fail(failure); }
        });
    }
    public static void tick() throws Exception {
        var mc = Minecraft.getInstance(); if (!prepared || mc.getOverlay() != null) return;
        ticks++; var target = worker.getBlockPos();
        if (ticks % 10 == 0) PacketHandler.INSTANCE.sendToServer(new PacketGetInfo(mc.level.dimension(), target, mode,
                new BlockHitResult(Vec3.atCenterOf(target), Direction.SOUTH, target, false), ItemStack.EMPTY));
        shown = cached(target);
        switch (ticks) {
            case 60 -> { require(has("online") && icons() == 33 && has("queue"), "bound source fields/icons"); shot("ef-top-01-queued-zh.png"); mode = ProbeMode.EXTENDED; }
            case 100 -> { require(prefix("250,000.50 AE"), "extended fractional cache"); shot("ef-top-02-extended-zh.png"); mc.options.languageCode="en_us"; mc.getLanguageManager().setSelected("en_us"); mc.reloadResourcePacks(); }
            case 140 -> { require(has("current") && has("enqueued"), "English native reply"); shot("ef-top-03-extended-en.png"); org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),854,480); }
            case 180 -> { require(icons()==33, "small-window icon bound"); shot("ef-top-04-small-queue.png"); server(() -> owner.setModes(false,false)); }
            case 220 -> { require(prefix("41 / 32"), "downclock hid retained work"); shot("ef-top-05-downclock.png"); server(() -> worker.restoreWorkData(new CompoundTag())); }
            case 260 -> { require(icons()==0 && !has("current") && has("queue"), "empty queue retained icons"); shot("ef-top-06-empty.png"); server(() -> { var bad=new CompoundTag(); bad.putInt("Version",999); worker.restoreWorkData(bad); }); }
            case 300 -> { require(has("unreadable") && !has("queue") && icons()==0, "unknown data masked as idle"); shot("ef-top-07-unknown.png"); server(() -> { worker.restoreWorkData(work); owner.getLevel().setBlockAndUpdate(owner.getBlockPos().east(13),Blocks.AIR.defaultBlockState()); owner.refreshStructure(); }); }
            case 340 -> { require(has("offline") && has("current") && icons()==33, "broken structure lost retained work"); shot("ef-top-08-offline.png"); server(() -> { owner.getLevel().setBlockAndUpdate(owner.getBlockPos().east(13),EFabricatorRegistration.WORKER.get().defaultBlockState()); owner.refreshStructure(); }); }
            case 380 -> {
                require(has("online") && frames>100, "restored structure/native TOP renderer"); shot("ef-top-09-restored.png");
                Modularmachinery.LOG.info("[MMC][ef-top-client] PASS: actual native TOP request/reply/renderer, 33 FIFO entry icons, normal/extended and fractional AE, Chinese/English, 854x480, downclock retention, empty/unknown work, physical break and rebind; saved display fixture, not native AE orders; inspect screenshots");
                Modularmachinery.LOG.info("[MMC][ef-top-client] OVERALL: PASS");
            }
            case 400 -> mc.stop();
        }
        if (ticks>700) throw new AssertionError("TOP fixture timeout");
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        if (!active || shown==null) return;
        try {
            var mc=Minecraft.getInstance();
            require(shown.getWidth()+12<=mc.getWindow().getGuiScaledWidth() && shown.getHeight()+12<=mc.getWindow().getGuiScaledHeight(),"TOP overlay outside window");
            OverlayRenderer.renderOverlay(mcjty.theoneprobe.config.Config.getDefaultOverlayStyle().copy().location(6,-1,6,-1),shown,event.getGuiGraphics()); frames++;
        } catch (Throwable failure) { fail(failure); }
    }
    @SuppressWarnings("unchecked") private static ProbeInfo cached(BlockPos pos) throws Exception {
        var field=OverlayRenderer.class.getDeclaredField("cachedInfo"); field.setAccessible(true);
        var map=(Map<org.apache.commons.lang3.tuple.Pair<?,?>,org.apache.commons.lang3.tuple.Pair<Long,ProbeInfo>>)field.get(null);
        var value=map.get(org.apache.commons.lang3.tuple.Pair.of(Minecraft.getInstance().level.dimension(),pos)); return value==null?null:value.getRight();
    }
    private static List<IElement> flatten(IProbeInfo info) {
        var out=new ArrayList<IElement>(); if(info!=null)for(var e:info.getElements()){out.add(e);if(e instanceof IProbeInfo nested)out.addAll(flatten(nested));}return out;
    }
    private static int icons() { return icons(shown); }
    private static int icons(IProbeInfo info) {
        if (info == null) return 0;
        // Count only EF's labelled sections, independently of TOP's block/harvest header settings.
        if (info.getElements().stream().anyMatch(e -> key(e,"current") || key(e,"enqueued")))
            return (int)flatten(info).stream().filter(e -> e instanceof ElementItemStack).count();
        int count=0; for(var e:info.getElements())if(e instanceof IProbeInfo nested)count+=icons(nested); return count;
    }
    private static boolean prefix(String value) { return flatten(shown).stream().filter(e -> e instanceof ElementProgress).map(e -> (ElementProgress)e).anyMatch(e -> e.getStyle().getPrefix().contains(value)); }
    private static boolean has(String suffix) {
        return flatten(shown).stream().anyMatch(e -> key(e,suffix));
    }
    private static boolean key(IElement element,String suffix) {
        if(element instanceof ElementText)try{var f=element.getClass().getDeclaredField("text");f.setAccessible(true);
            return ((Component)f.get(element)).getContents() instanceof TranslatableContents t && t.getKey().equals("top.modularmachinery.ef."+suffix);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}return false;
    }
    private static void server(Runnable task) { Minecraft.getInstance().getSingleplayerServer().execute(() -> {try{task.run();}catch(Throwable failure){fail(failure);}}); }
    private static void shot(String name) { var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r -> Modularmachinery.LOG.info("[MMC][ef-top-client] {}",r.getString())); }
    private static void require(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
    private static void fail(Throwable failure) { active=false; Modularmachinery.LOG.error("[MMC][ef-top-client] OVERALL: FAIL tick {}",ticks,failure); Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop()); }
}
