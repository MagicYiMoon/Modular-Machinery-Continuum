/* SPDX-License-Identifier: GPL-3.0-only
 * Structure/card/power service adapts NECO TileHyperNetTerminal and old.HyperNetTerminal, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

/** No recipe engine: the author's hypernet_terminal machine JSON supplies only its physical structure. */
public final class TileHyperNetTerminal extends TileController {
    private final ItemStackHandler cards=new ItemStackHandler(1){
        @Override public int getSlotLimit(int slot){return 1;}
        @Override public boolean isItemValid(int slot,ItemStack stack){return HyperNetCardInteractions.isCard(stack);}
        @Override protected void onContentsChanged(int slot){setChanged();cardChanged=true;}
    };
    private long nextStructure,definitionRevision=-1,lastTick=Long.MIN_VALUE,poweredTick=Long.MIN_VALUE;
    private boolean powered,clientPowered,cardChanged=true;
    private String failure="gui.modularmachinery.research.unformed";
    private MachineContext context;
    public TileHyperNetTerminal(BlockPos pos,BlockState state){super(RegistryBlockEntities.HYPERNET_TERMINAL,pos,state);}
    public ItemStackHandler cards(){return cards;}
    // The source terminal has no redstone control; only structure, card and physical power govern it.
    @Override public boolean isRedstoneStopped(){return false;}
    @Override public boolean isWorking(){return isFormed()&&(level!=null&&level.isClientSide?clientPowered:servicePowered());}
    @Override public RecipeThread[] getRecipeThreadList(){return new RecipeThread[0];}
    public boolean servicePowered(){return powered&&level!=null&&level.getGameTime()-poweredTick>=0&&level.getGameTime()-poweredTick<=1;}
    public String serviceFailure(){return failure;}
    public void refreshStructure(){
        if(!(level instanceof ServerLevel world)||isRemoved())return;
        nextStructure=world.getGameTime()+40;definitionRevision=MachineReloadListener.getServerDefinitionRevision();
        acknowledgeStructureCheck();consumeRebindRequest();flushPendingComponentUnbinds();
        var loader=MachineReloadListener.getLoader();var machine=loader.findByName("hypernet_terminal");
        if(machine==null||machine.getHyperNetDefinition()==null||machine.getHyperNetDefinition().role()!=HyperNetDefinition.Role.TERMINAL){
            context=null;setFormed(false);failure="gui.modularmachinery.research.missing_definition";return;
        }
        var facing=getBlockState().getValue(BlockHyperNetTerminal.FACING);
        var result=MachineFormation.tryForm(world,worldPosition,facing,machine,loader);
        setFormed(result.formed());acknowledgeStructureCheck();
        context=result.formed()?new MachineContext(world,worldPosition,facing,machine,loader.getVariables(),getFormedPattern(machine)):null;
        if(!result.formed())failure="gui.modularmachinery.research.unformed";
    }
    @Override public void tick(){
        if(!(level instanceof ServerLevel))return;
        boolean before=powered;serviceTick();if(before!=powered)markForUpdate();
    }
    private void serviceTick(){
        if(!(level instanceof ServerLevel world)||isRemoved()||lastTick==world.getGameTime())return;lastTick=world.getGameTime();
        if(world.getGameTime()>=nextStructure||isStructureCheckRequested()||definitionRevision!=MachineReloadListener.getServerDefinitionRevision())refreshStructure();
        powered=false;
        if(!isFormed()||isStructureCheckRequested()||!isStructureChunkLoaded()||context==null){
            if(!isStructureChunkLoaded())setFormed(false);var node=peekHyperNetNode();if(node!=null)node.disconnect();return;
        }
        var node=getHyperNetNode();if(node==null)return;
        int cost=node.terminalConnected()?2000:200;
        boolean paid=false;
        // Source pays the full amount from one input handler, without partial debits on failure.
        for(var energy:context.internalEnergyStorages(MachineContext.ComponentKind.INPUT))if(energy.extractEnergy(cost,true)==cost){paid=energy.extractEnergy(cost,false)==cost;break;}
        if(!paid){failure="craftcheck.failure.energy.input";node.disconnect();return;}
        powered=true;poweredTick=world.getGameTime();failure="";
        if(cardChanged||world.getGameTime()%20==0){cardChanged=false;
            if(!HyperNetCardInteractions.connectFromCard(this,cards.getStackInSlot(0)))node.disconnect();
        }
        node.tick();
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);clientPowered=tag.getBoolean("hypernetTerminalWorking");powered=false;poweredTick=Long.MIN_VALUE;context=null;nextStructure=0;definitionRevision=-1;lastTick=Long.MIN_VALUE;cardChanged=true;
        cards.setStackInSlot(0,ItemStack.EMPTY);
        if(tag.contains("hypernetTerminalCard",Tag.TAG_COMPOUND)&&tag.getCompound("hypernetTerminalCard").getInt("Size")==1)cards.deserializeNBT(tag.getCompound("hypernetTerminalCard"));
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("hypernetTerminalCard",cards.serializeNBT());}
    @Override public CompoundTag getUpdateTag(){var tag=super.getUpdateTag();tag.remove("hypernetTerminalCard");tag.putBoolean("hypernetTerminalWorking",servicePowered());return tag;}
}
