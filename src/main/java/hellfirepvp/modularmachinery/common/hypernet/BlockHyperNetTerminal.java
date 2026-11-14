/* SPDX-License-Identifier: GPL-3.0-only
 * Dedicated terminal block follows NECO BlockHyperNetTerminal, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockHyperNetTerminal extends BlockController {
    public BlockHyperNetTerminal(){super(RegistryBlocks.componentProperties(),"hypernet_terminal");}
    @Override public String getDescriptionId(){return "block.modularmachinery.hypernet_terminal_controller";}
    @Override public net.minecraft.network.chat.MutableComponent getName(){return Component.translatable(getDescriptionId());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TileHyperNetTerminal(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide||type!=RegistryBlockEntities.HYPERNET_TERMINAL.get()?null:(world,pos,block,tile)->((TileHyperNetTerminal)tile).tick();
    }
    @Override public InteractionResult use(BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND||passesControllerInteraction(player,hand))return InteractionResult.PASS;
        if(!world.isClientSide&&world.getBlockEntity(pos) instanceof TileHyperNetTerminal terminal)
            hellfirepvp.modularmachinery.common.menu.MenuHyperNetResearch.open(player,terminal);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level world,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&!world.isClientSide&&world.getBlockEntity(pos) instanceof TileHyperNetTerminal terminal){
            var card=terminal.cards().extractItem(0,1,false);if(!card.isEmpty())popResource(world,pos,card);
        }super.onRemove(state,world,pos,next,moving);
    }
    @Override public boolean canConnectRedstone(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos,net.minecraft.core.Direction side){return false;}
    @Override public boolean hasAnalogOutputSignal(BlockState state){return false;}
}
