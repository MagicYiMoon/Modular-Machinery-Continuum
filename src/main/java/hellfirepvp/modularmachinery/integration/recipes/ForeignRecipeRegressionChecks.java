/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.recipes;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Audits real recipe managers; these checks run only in the opt-in server suite. */
public final class ForeignRecipeRegressionChecks {
    public static void run(ServerLevel level) {
        checkExecution(level, new net.minecraft.core.BlockPos(256,230,2288));
        RecipeAdapterRegistry.registerBuiltIns();
        var families = new LinkedHashMap<String,List<String>>();
        families.put("tconstruct",List.of("smeltery_melting","smeltery_alloy"));
        families.put("thermal_expansion",List.of("insolator"));
        families.put("nuclearcraft",List.of("alloy_smelter","chemical_reactor","fluid_infuser","melter"));
        families.put("pneumaticcraft",List.of("pressure_chamber","assembly","refinery"));
        for(var entry:families.entrySet()) for(var family:entry.getValue()) {
            String namespace=entry.getKey().equals("thermal_expansion")?"thermal":entry.getKey();
            var adapter=RecipeAdapterRegistry.get(new ResourceLocation(namespace,family));
            if(!net.minecraftforge.fml.ModList.get().isLoaded(entry.getKey())) {require(adapter==null,"adapter loaded without optional mod: "+family);continue;}
            require(adapter!=null,"missing adapter "+family);
            var first=new ArrayList<>(adapter.createRecipesFor("reload_probe", "adapter_probe",List.of(),List.of()));
            require(!first.isEmpty(),"native recipes all missing/skipped: "+family);
            var other=adapter.createRecipesFor("other_template", "adapter_probe",List.of(),List.of());
            var second=new ArrayList<>(adapter.createRecipesFor("reload_probe", "adapter_probe",List.of(),List.of()));
            require(first.stream().map(MachineRecipe::registryName).toList().equals(second.stream().map(MachineRecipe::registryName).toList()),"unstable reload IDs "+family);
            require(first.stream().map(MachineRecipe::registryName).distinct().count()==first.size(),"duplicate IDs "+family);
            for(var recipe:first) require(recipe.recipeTime()>0 && !recipe.requirements().isEmpty(),"empty native conversion "+family);
            require(Collections.disjoint(first.stream().map(MachineRecipe::registryName).toList(),other.stream().map(MachineRecipe::registryName).toList()),"template ID collision "+family);
            Modularmachinery.LOG.info("[MMC][foreign-recipes] {}:{} verified {} native conversions and stable reload IDs",namespace,family,first.size());
        }
        var tag=ForeignRecipeAdapter.itemInput(Ingredient.of(net.minecraft.tags.ItemTags.LOGS),2);
        require(tag instanceof RequirementItem item && item.getRawId().equals("tag:minecraft:logs") && item.getAmount()==2,"tag/count preservation");
        try {ForeignRecipeAdapter.itemInput(Ingredient.EMPTY,1);throw new AssertionError("empty input accepted");}catch(IllegalArgumentException expected){}
        var output=new ItemStack(Items.DIAMOND,2);output.getOrCreateTag().putString("probe","preserved");
        require(ForeignRecipeAdapter.item(IOType.OUTPUT,output).getAmount()==2,"output count");
        if(net.minecraftforge.fml.ModList.get().isLoaded("thermal_expansion")) {
            var mods=List.of(new RecipeModifier("modularmachinery:item",IOType.OUTPUT,2,1,true),new RecipeModifier("modularmachinery:item",IOType.OUTPUT,3,1,false));
            var scaled=ThermalRecipeAdapter.scaleOutput(output,2.25f,mods);
            require(scaled.size()==2 && ((RequirementItem)scaled.get(0)).getAmount()==24 && ((RequirementItem)scaled.get(1)).getAmount()==6 && ((RequirementItem)scaled.get(1)).getChance()==.5f,"expected chance split/modifier applied once");
        }
        Modularmachinery.LOG.info("[MMC][foreign-recipes] PASS: optional absence, real fixed-version recipe families, nonempty conversions, stable reload IDs, tag/count preservation and Thermal expected-output modifiers");
    }
    private static void checkExecution(ServerLevel level,net.minecraft.core.BlockPos origin) {
        var saved=new LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var machines=hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader().getMachines();var oldMachines=new LinkedHashMap<>(machines);
        var recipes=hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader().getByMachine();var oldRecipes=new LinkedHashMap<>(recipes);
        var source=level.getRecipeManager().byKey(new ResourceLocation("minecraft:iron_ingot_from_smelting_raw_iron")).orElseThrow();
        var adapter=new ForeignRecipeAdapter(new ResourceLocation("modularmachinery:foreign_probe")) {
            protected boolean accepts(Recipe<?> r){return r.getId().equals(source.getId());}
            protected Converted convert(Recipe<?> r,List<RecipeModifier> modifiers) {
                var output=r.getResultItem(level.registryAccess()).copy();output.getOrCreateTag().putString("adapter_probe","kept");
                return new Converted(2,5,List.of(itemInput(r.getIngredients().get(0),1),item(IOType.OUTPUT,output)),
                    List.of(List.of(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,100),new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA,100))));
            }
        };
        var modifiers=List.of(new RecipeModifier("modularmachinery:item",IOType.INPUT,2,1,false),new RecipeModifier("modularmachinery:item",IOType.OUTPUT,3,1,false),new RecipeModifier("modularmachinery:energy",IOType.INPUT,2,1,false),new RecipeModifier("modularmachinery:duration",IOType.INPUT,1,0,false));
        var generated=new ArrayList<>(adapter.createRecipesFor("foreign_job",modifiers,List.of(new RequirementItem(IOType.OUTPUT,"minecraft:gold_nugget",1))));
        require(generated.size()==2,"fluid alternatives not expanded");
        checkLoaderReload(adapter);
        try {
            for(boolean factory:List.of(false,true)) {
                var pos=origin.offset(factory?16:0,0,0);
                var pattern=new hellfirepvp.modularmachinery.common.machine.MachinePattern();
                net.minecraft.world.level.block.Block[] components={hellfirepvp.modularmachinery.common.registry.RegistryBlocks.itemInputBus.get(),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.itemOutputBus.get(),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.fluidInputHatch.get(),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.energyInputHatch.get()};
                var controllerBlock=factory?hellfirepvp.modularmachinery.common.registry.RegistryBlocks.blockFactoryController.get():hellfirepvp.modularmachinery.common.registry.RegistryBlocks.blockController.get();
                saved.put(pos,level.getBlockState(pos));level.setBlock(pos,controllerBlock.defaultBlockState().setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING,net.minecraft.core.Direction.NORTH),3);
                for(int i=0;i<components.length;i++) {
                    var p=pos.offset(i+1,0,0);saved.put(p,level.getBlockState(p));level.setBlock(p,components[i].defaultBlockState(),3);
                    pattern.addPart(new hellfirepvp.modularmachinery.common.machine.MachinePattern.Part(i+1,0,0,net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(components[i]).toString()));
                }
                var machine=new hellfirepvp.modularmachinery.common.machine.DynamicMachine("foreign_job","Foreign job",pattern);machine.setMaxThreads(1);
                machines.put(new ResourceLocation("modularmachinery:foreign_job"),machine);recipes.put("foreign_job",generated);
                var controller=(hellfirepvp.modularmachinery.common.tiles.TileController)level.getBlockEntity(pos);controller.setMachineName("foreign_job");
                require(hellfirepvp.modularmachinery.common.machine.MachineFormation.tryForm(level,pos,net.minecraft.core.Direction.NORTH,machine,hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()).formed(),"foreign fixture formation");controller.setFormed(true);
                var items=(net.minecraftforge.items.IItemHandlerModifiable)((hellfirepvp.modularmachinery.common.tiles.base.TileItemBus)level.getBlockEntity(pos.east())).getInventory();
                var products=(net.minecraftforge.items.IItemHandlerModifiable)((hellfirepvp.modularmachinery.common.tiles.base.TileItemBus)level.getBlockEntity(pos.east(2))).getInventory();
                var tank=level.getBlockEntity(pos.east(3)).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
                var energy=((hellfirepvp.modularmachinery.common.tiles.TileEnergyInputHatch)level.getBlockEntity(pos.east(4))).getEnergyStorage();
                items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,2));tank.fill(new net.minecraftforge.fluids.FluidStack(factory?net.minecraft.world.level.material.Fluids.LAVA:net.minecraft.world.level.material.Fluids.WATER,100),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                energy.receiveEnergy(29,false);
                for(int i=0;i<20;i++){if(factory)hellfirepvp.modularmachinery.common.tiles.FactoryTicker.tick(level,pos,(hellfirepvp.modularmachinery.common.tiles.TileFactoryController)controller);else hellfirepvp.modularmachinery.common.tiles.MachineTicker.tick(level,pos,controller);}
                require(products.getStackInSlot(0).isEmpty(),"short energy completed job");energy.receiveEnergy(1,false);
                for(int i=0;i<120 && products.getStackInSlot(0).isEmpty();i++){((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);if(factory)hellfirepvp.modularmachinery.common.tiles.FactoryTicker.tick(level,pos,(hellfirepvp.modularmachinery.common.tiles.TileFactoryController)controller);else hellfirepvp.modularmachinery.common.tiles.MachineTicker.tick(level,pos,controller);}
                require(items.getStackInSlot(0).isEmpty() && tank.getFluidInTank(0).isEmpty() && energy.getEnergyStored()==0,"foreign input/FE conservation");
                int iron=0,nuggets=0;for(int i=0;i<products.getSlots();i++){var stack=products.getStackInSlot(i);if(stack.is(Items.IRON_INGOT)){iron+=stack.getCount();require(stack.hasTag() && stack.getTag().getString("adapter_probe").equals("kept"),"native NBT output lost");}if(stack.is(Items.GOLD_NUGGET))nuggets+=stack.getCount();}
                require(iron==3 && nuggets==1,"quantity modifier or template addition applied twice");
            }
            Modularmachinery.LOG.info("[MMC][foreign-execution] PASS: real source recipe, water/lava alternatives, ordinary/factory completion, FE shortage/resume, single modifiers, unscaled additions and NBT output conservation");
        } finally {saved.forEach((p,state)->level.setBlock(p,state,3));machines.clear();machines.putAll(oldMachines);recipes.clear();recipes.putAll(oldRecipes);hellfirepvp.modularmachinery.common.machine.MachineReloadListener.refreshRecipes();}
    }
    private static void checkLoaderReload(ForeignRecipeAdapter adapter) {
        var directory=hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var machineLoader=new hellfirepvp.modularmachinery.common.machine.MachineLoader();
        machineLoader.getMachines().put(new ResourceLocation("modularmachinery:foreign_job"),
                new hellfirepvp.modularmachinery.common.machine.DynamicMachine("foreign_job","Probe",new hellfirepvp.modularmachinery.common.machine.MachinePattern()));
        var previous=RecipeAdapterRegistry.register(adapter.getRegistryName(),adapter);
        java.nio.file.Path folder=null;
        try {
            folder=java.nio.file.Files.createTempDirectory(directory,"mmc_foreign_reload_");
            var template=new com.google.gson.JsonObject();template.addProperty("machine","foreign_job");
            template.addProperty("adapter",adapter.getRegistryName().toString());
            var file=folder.resolve("z.adapter.json");java.nio.file.Files.writeString(file,template.toString());
            var loader=new RecipeLoader();loader.load(machineLoader);
            var first=loader.getByMachine().getOrDefault("foreign_job",List.of()).stream().map(MachineRecipe::registryName).toList();
            require(first.size()==2,"loader did not expand foreign template");
            loader.load(machineLoader);
            require(first.equals(loader.getByMachine().get("foreign_job").stream().map(MachineRecipe::registryName).toList()),"actual loader reload changed IDs");
            java.nio.file.Files.writeString(folder.resolve("a.adapter.json"),template.toString());loader.load(machineLoader);
            var expanded=loader.getByMachine().get("foreign_job").stream().map(MachineRecipe::registryName).toList();
            require(expanded.size()==4 && new HashSet<>(expanded).size()==4 && expanded.containsAll(first),"earlier template changed or collided with existing IDs");
            java.nio.file.Files.delete(folder.resolve("a.adapter.json"));loader.load(machineLoader);
            require(first.equals(loader.getByMachine().get("foreign_job").stream().map(MachineRecipe::registryName).toList()),"template removal changed remaining IDs");
            Modularmachinery.LOG.info("[MMC][foreign-reload] PASS: actual file loader, repeated load, earlier template insertion/removal, stable distinct IDs without counter reset");
        } catch(java.io.IOException failure) {throw new IllegalStateException("Foreign reload fixture",failure);}
        finally {
            if(previous==null)RecipeAdapterRegistry.unregister(adapter.getRegistryName(),adapter);else RecipeAdapterRegistry.register(adapter.getRegistryName(),previous);
            if(folder!=null)try {java.nio.file.Files.deleteIfExists(folder.resolve("a.adapter.json"));java.nio.file.Files.deleteIfExists(folder.resolve("z.adapter.json"));java.nio.file.Files.deleteIfExists(folder);}catch(java.io.IOException failure){throw new IllegalStateException("Foreign reload fixture cleanup",failure);}
            RecipeAdapterRegistry.registerDynamicMachineAdapters();
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError("Foreign recipes: "+message);}
}
