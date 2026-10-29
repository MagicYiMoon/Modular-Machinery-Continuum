/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.*;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in source development fixture, never active in normal client/server runs. Use only in a disposable world. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class ClientVisualFixture {
    private static final List<BlockPos> PORTS=new ArrayList<>(), RECEIVERS=new ArrayList<>();
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("modularmachinery.clientVisualTest"))return;
        var root=Commands.literal("mmcvisual").requires(s->s.hasPermission(2));
        root.then(Commands.literal("setup").executes(ctx->{setup(ctx.getSource().getPlayerOrException());return 1;}));
        root.then(Commands.literal("pressure").executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();var pos=new BlockPos(0,101,0);
            player.teleportTo(player.serverLevel(),.5,102,-2,0,20);
            if(net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft") && player.serverLevel().getBlockEntity(pos) instanceof hellfirepvp.modularmachinery.integration.pneumatic.TilePressureHatch tile)
                net.minecraftforge.network.NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->hellfirepvp.modularmachinery.integration.pneumatic.MenuPressureHatch.forTile(id,inv,tile),Component.literal("Pressure / heat fixture")),pos);
            return 1;}));
        for(boolean factory:List.of(false,true))root.then(Commands.literal(factory?"factory":"ordinary").executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();var pos=new BlockPos(factory?4:2,101,0);
            player.teleportTo(player.serverLevel(),pos.getX()+.5,102,-2,0,20);
            if(player.serverLevel().getBlockEntity(pos) instanceof TileController tile)
                net.minecraftforge.network.NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->factory?hellfirepvp.modularmachinery.common.menu.MenuFactoryController.forController(id,inv,(TileFactoryController)tile):hellfirepvp.modularmachinery.common.menu.MenuMachineController.forController(id,inv,tile),Component.literal("Controller performance fixture")),pos);
            return 1;}));
        root.then(Commands.literal("beamclose").executes(ctx->{var player=ctx.getSource().getPlayerOrException();player.teleportTo(player.serverLevel(),17,103,10,0,8);return 1;}));
        event.getDispatcher().register(root);
    }
    private static void setup(ServerPlayer player) {
        ServerLevel level=player.serverLevel();PORTS.clear();RECEIVERS.clear();
        for(int x=-8;x<=24;x++)for(int z=-8;z<=24;z++)level.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
        int n=0;
        for(Direction dir:Direction.values()) {
            var pos=new BlockPos(2+(n%3)*7,dir==Direction.DOWN?106:102,6+(n/3)*9);n++;
            for(int d=1;d<=3;d++)level.setBlock(pos.relative(dir,d),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(pos,RegistryBlocks.energyOutputPort.get().defaultBlockState().setValue(BlockEnergyOutputPort.FACING,dir),3);
            var target=pos.relative(dir,3);level.setBlock(target,RegistryBlocks.energyInputHatch.get().defaultBlockState().setValue(BlockEnergyHatch.SIZE,EnergyHatchData.ULTIMATE),3);
            PORTS.add(pos);RECEIVERS.add(target);
        }
        level.setBlock(new BlockPos(2,101,0),RegistryBlocks.blockController.get().defaultBlockState(),3);
        level.setBlock(new BlockPos(4,101,0),RegistryBlocks.blockFactoryController.get().defaultBlockState(),3);
        for(int x:List.of(2,4))if(level.getBlockEntity(new BlockPos(x,101,0)) instanceof TileController tile)tile.setMachineName("alloy_furnace");
        if(net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft")) {
            var pos=new BlockPos(0,101,0);level.setBlock(pos,RegistryBlocks.pressureInput.get().defaultBlockState(),3);
            var tile=(hellfirepvp.modularmachinery.integration.pneumatic.TilePressureHatch)level.getBlockEntity(pos);tile.air().setPressure(2);tile.heat().setTemperature(500);tile.markForUpdate();
        }
        level.setBlock(new BlockPos(6,101,0),RegistryBlocks.sparkManaInput.get().defaultBlockState(),3);
        level.setBlock(new BlockPos(8,101,0),RegistryBlocks.sparkManaOutput.get().defaultBlockState(),3);
        level.setBlock(new BlockPos(10,101,0),RegistryBlocks.emberInput.get().defaultBlockState(),3);
        level.setDayTime(6000);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.teleportTo(level,10,108,-4,0,20);
        player.sendSystemMessage(Component.literal("Disposable visual fixture: /mmcvisual pressure | ordinary | factory. Six native FE beams south of platform."));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(!Boolean.getBoolean("modularmachinery.clientVisualTest") || event.phase!=TickEvent.Phase.START || !(event.level instanceof ServerLevel level))return;
        for(var pos:PORTS)if(level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof TileEnergyOutputPort port)port.produceInternal(1000,false);
        for(var pos:RECEIVERS)if(level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof TileEnergyInputHatch tile)tile.getEnergyStorage().extractEnergy(Integer.MAX_VALUE,false);
    }
}
