/* SPDX-License-Identifier: GPL-3.0-only
 * MMC 1.1, modified 2026-10. Behavioral coverage of startup controller identities.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.editor.EditorSaveState;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Never registers objects during a test: exercises the identities actually registered at startup. */
public final class DedicatedControllerRegressionChecks {
    private DedicatedControllerRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        require(!DedicatedControllers.entries().isEmpty(), "startup definitions registered no controllers");
        var preferred = DedicatedControllers.entries().stream().filter(DedicatedControllers.Entry::factory)
                .findFirst().orElse(DedicatedControllers.entries().get(0));
        String name = preferred.definition().name();
        var loader = MachineReloadListener.getLoader();
        var machinesBefore = new LinkedHashMap<>(loader.getMachines());
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine();
        var recipesBefore = new LinkedHashMap<>(recipes);
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockinputbus"));
        pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:blockoutputbus"));
        var machine = new DynamicMachine(name, "Dedicated test", pattern);
        var other = new DynamicMachine("dedicated_wrong_identity", "Other", pattern);
        var recipe = new MachineRecipe(name, "dedicated_identity_recipe", 2, List.of(
                new RequirementItem(IOType.INPUT, "minecraft:iron_ingot", 1),
                new RequirementItem(IOType.OUTPUT, "minecraft:gold_nugget", 1)), null, false, 1, "");
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            loader.getMachines().clear();
            loader.getMachines().put(new ResourceLocation("modularmachinery", other.getRegistryName()), other);
            loader.getMachines().put(new ResourceLocation("modularmachinery", name), machine);
            recipes.put(name, List.of(recipe));
            int tested = 0;
            for (var entry : DedicatedControllers.entries()) {
                if (!entry.definition().name().equals(name)) continue;
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (int x = 0; x <= 2; x++) {
                        BlockPos p = StructureMatcher.toWorldOffset(origin, facing, x, 0, 0);
                        before.putIfAbsent(p, level.getBlockState(p));
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    var state = entry.block().get().defaultBlockState().setValue(BlockController.FACING, facing);
                    level.setBlock(origin, state, Block.UPDATE_ALL);
                    var tile = (TileController) level.getBlockEntity(origin);
                    require(tile != null && tile.getType().isValid(state) && (tile instanceof TileFactoryController) == entry.factory(), "block entity type/factory mismatch");
                    require(name.equals(tile.getMachineName()), "placement must bind without a blueprint");
                    checkToolInteraction(level, origin, name);
                    require(state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.is(BlockTags.NEEDS_STONE_TOOL)
                            && new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)
                            && !new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "generated tool tags absent");
                    tile.setMachineName(other.getRegistryName());
                    require(name.equals(tile.getMachineName()), "setMachineName changed a fixed identity");
                    var nbt = tile.saveWithoutMetadata(); nbt.putString("machineName", other.getRegistryName());
                    tile.load(nbt);
                    require(name.equals(tile.getMachineName()), "saved NBT changed a fixed identity");
                    ItemStack wrong = new ItemStack(RegistryItems.BLUEPRINT.get()); ItemBlueprint.setAssociatedMachine(wrong, other);
                    require(!tile.getBlueprintInventory().isItemValid(0, wrong), "foreign blueprint accepted");
                    require(!MachineFormation.tryForm(level, origin, facing, other, loader).formed(), "foreign machine formed");
                    loader.fillMissing(level, origin, facing, machine);
                    machine.setRequiresBlueprint(true);
                    require(!MachineFormation.tryForm(level, origin, facing, machine, loader).formed(), "required blueprint bypassed");
                    ItemStack correct = new ItemStack(RegistryItems.BLUEPRINT.get()); ItemBlueprint.setAssociatedMachine(correct, machine);
                    ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, correct);
                    require(MachineFormation.tryForm(level, origin, facing, machine, loader).formed(), "correct blueprint refused");
                    machine.setRequiresBlueprint(false);
                    ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, ItemStack.EMPTY);
                    tick(level, origin, tile);
                    require(tile.isFormed() && name.equals(tile.getMachineName()), "automatic discovery selected another equal structure");
                    var projection = new WorldProjectionDefinition(machine, pattern, loader.getVariables(), loader.getResolver());
                    require(projection.cells().get(BlockPos.ZERO).matches(level, origin, facing), "dedicated origin marked as a projection error");
                    var otherProjection = new WorldProjectionDefinition(other, pattern, loader.getVariables(), loader.getResolver());
                    require(!otherProjection.cells().get(BlockPos.ZERO).matches(level, origin, facing), "other machine's dedicated origin accepted");
                    var blueprint = new BlueprintSceneDefinition(machine, pattern, loader.getVariables(), loader.getResolver());
                    require(blueprint.cells().get(BlockPos.ZERO).states().get(0).getBlock().asItem() == DedicatedControllers.stack(name, false).getItem(), "preview dedicated identity");
                    var inputPos = StructureMatcher.toWorldOffset(origin, facing, 1, 0, 0);
                    var outputPos = StructureMatcher.toWorldOffset(origin, facing, 2, 0, 0);
                    var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(inputPos)).getInventory();
                    var output = ((TileItemBus) level.getBlockEntity(outputPos)).getInventory();
                    input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
                    for (int i=0; i<15; i++) tick(level, origin, tile);
                    int emitted=0; for(int i=0;i<output.getSlots();i++) if(output.getStackInSlot(i).is(Items.GOLD_NUGGET)) emitted+=output.getStackInSlot(i).getCount();
                    require(emitted == 1 && input.getStackInSlot(0).isEmpty(), "dedicated recipe did not run exactly once");
                    UUID owner=UUID.randomUUID(); tile.setOwner(owner);
                    var drops=Block.getDrops(level.getBlockState(origin), level, origin, tile);
                    require(drops.size()==1 && drops.get(0).is(entry.item().get()) && owner.equals(drops.get(0).getTag().getUUID("owner")), "dedicated loot identity/owner lost");
                    var clone=entry.block().get().getCloneItemStack(state, null, level, origin, null);
                    require(clone.is(entry.item().get()) && owner.equals(clone.getTag().getUUID("owner")), "clone identity/owner lost");
                    // Exercise ServerPlayerGameMode's actual survival removal, not just Block.getDrops.
                    var miner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ControllerMiner"));
                    miner.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    miner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                    ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, correct);
                    var box = new net.minecraft.world.phys.AABB(origin).inflate(3);
                    var previousDrops = new HashSet<UUID>();
                    level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).forEach(e -> previousDrops.add(e.getUUID()));
                    require(miner.gameMode.destroyBlock(origin) && level.getBlockState(origin).isAir(), "survival harvest refused");
                    int harvested=0, blueprints=0;
                    for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
                        if (previousDrops.contains(entity.getUUID())) continue;
                        ItemStack dropped=entity.getItem();
                        if(dropped.is(entry.item().get())) {
                            harvested+=dropped.getCount();
                            require(dropped.hasTag() && owner.equals(dropped.getTag().getUUID("owner")), "harvest lost owner");
                        }
                        if(dropped.is(RegistryItems.BLUEPRINT.get())) blueprints+=dropped.getCount();
                        entity.discard();
                    }
                    require(harvested==1 && blueprints==1, "survival loot/blueprint must each drop once: " + harvested + "/" + blueprints);
                    loader.getMachines().remove(new ResourceLocation("modularmachinery", name));
                    tick(level, origin, tile);
                    require(name.equals(tile.getMachineName()) && !DedicatedControllers.stack(name, entry.factory()).isEmpty(), "removed definition erased registry identity");
                    loader.getMachines().put(new ResourceLocation("modularmachinery", name), machine);
                    tested++;
                }
            }
            require(tested>=4, "no dedicated controllers exercised");
            checkLegacyHarvest(level, origin.offset(4, 0, 4));
            Modularmachinery.LOG.info("[MMC][dedicated-controller-regression] PASS: {} orientation/type combinations, fixed binding, blueprints, projection, real recipes, tools, loot, survival harvest, blueprint recovery, owner, missing definitions", tested);
        } finally {
            before.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_ALL));
            loader.getMachines().clear(); loader.getMachines().putAll(machinesBefore);
            recipes.clear(); recipes.putAll(recipesBefore);
        }
        checkEditor(level, name);
        checkGeneratedModels();
    }

    private static void checkLegacyHarvest(ServerLevel level, BlockPos pos) {
        var original = level.getBlockState(pos);
        var miner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LegacyControllerMiner"));
        miner.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        miner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        try {
            for (var block : List.of(RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(), RegistryBlocks.blockCasing.get())) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
                if (block instanceof BlockController && level.getBlockEntity(pos) instanceof TileController tile) {
                    String machine = MachineReloadListener.getLoader().getMachines().values().iterator().next().getRegistryName();
                    tile.setMachineName(machine);
                    checkToolInteraction(level, pos, machine);
                }
                var box = new net.minecraft.world.phys.AABB(pos).inflate(3);
                var previous = new HashSet<UUID>();
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).forEach(e -> previous.add(e.getUUID()));
                require(miner.getMainHandItem().isCorrectToolForDrops(level.getBlockState(pos)) && miner.gameMode.destroyBlock(pos), "legacy stone-pickaxe harvest refused");
                int count = 0;
                for(var entity : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
                    if(previous.contains(entity.getUUID())) continue;
                    if(entity.getItem().is(block.asItem())) count += entity.getItem().getCount();
                    entity.discard();
                }
                require(count == 1, "legacy component did not drop once: " + block);
            }
        } finally { level.setBlock(pos, original, Block.UPDATE_ALL); }
    }

    private static void checkToolInteraction(ServerLevel level, BlockPos pos, String machine) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ProjectorInteraction"));
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        var state = level.getBlockState(pos);
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            for (var tool : List.of(RegistryItems.itemMachineProjector.get(), RegistryItems.CONSTRUCT_TOOL.get())) {
                player.setItemInHand(hand, new ItemStack(tool));
                require(state.use(level, player, hand, hit) == net.minecraft.world.InteractionResult.PASS,
                        "controller swallowed tool interaction: " + state + "/" + hand + "/" + tool);
                require(player.containerMenu == player.inventoryMenu, "tool click opened a controller menu");
                if (tool == RegistryItems.itemMachineProjector.get()) {
                    require(machine.equals(((TileController) level.getBlockEntity(pos)).getMachineName()), "projector lost binding");
                    require(tool.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit)).consumesAction(),
                            "projector rejected bound controller");
                }
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        player.setShiftKeyDown(true);
        require(state.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit) == net.minecraft.world.InteractionResult.PASS,
                "controller swallowed sneak formation interaction");
    }

    private static void checkEditor(ServerLevel level, String name) throws Exception {
        var loader=MachineReloadListener.getLoader();
        String source=loader.getSourcePath(name);
        require(source!=null,"dedicated fixture needs a JSON source");
        Path file=Path.of(source); String original=Files.readString(file);
        boolean changedFactory = !loader.findByName(name).isHasFactory();
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ControllerEditTest")) {
            @Override public boolean hasPermissions(int permissionLevel){return true;}
        };
        var menu=new MenuMachineEditor(9,player.getInventory(),PktMachineEditorRequest.snapshot()); player.containerMenu=menu;
        try {
            var request=new PktMachineEditorRequest(9,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),new PktMachineEditorControllerSettings(name,0xE85D5D,changedFactory));
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            PktMachineEditorRequest decoded;
            try { PktMachineEditorRequest.encode(request,buffer); decoded=PktMachineEditorRequest.decode(buffer); require(buffer.readableBytes()==0,"settings codec remainder"); }
            finally { buffer.release(); }
            require(decoded.equals(request),"settings codec mismatch");
            var result=PktMachineEditorRequest.process(player,decoded);
            require(result.result().success(),"settings save failed: "+result.result().message());
            var actual=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var expected=JsonParser.parseString(original).getAsJsonObject(); expected.addProperty("color","E85D5D"); expected.addProperty("has-factory",changedFactory);
            require(expected.equals(actual),"settings save changed unrelated machine fields");
            require(DedicatedControllers.color(name)==0xE85D5D && MachineReloadListener.getLoader().findByName(name).isHasFactory()==changedFactory,"reload/color lookup stale");
            var snapshot=MachineLoader.fromSnapshot(MachineReloadListener.getLoader().snapshot());
            require(snapshot.findByName(name).getDefinedColor()==0xE85D5D && snapshot.findByName(name).isHasFactory()==changedFactory,"client snapshot lost appearance");
            String saved=Files.readString(file);
            require(!PktMachineEditorRequest.process(player,decoded).result().success(),"duplicate save accepted");
            for(var bad:List.of(
                    new PktMachineEditorRequest(10,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),new PktMachineEditorControllerSettings(name,1,false)),
                    new PktMachineEditorRequest(9,UUID.randomUUID(),"stale",new PktMachineEditorControllerSettings(name,1,false)),
                    new PktMachineEditorRequest(9,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),new PktMachineEditorControllerSettings(name,-1,false))))
                require(!PktMachineEditorRequest.process(player,bad).result().success(),"invalid request accepted");
            var denied=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ControllerDenied")){
                @Override public boolean hasPermissions(int permissionLevel){return false;}
            };
            denied.containerMenu=new MenuMachineEditor(9,denied.getInventory(),menu.snapshot());
            require(!PktMachineEditorRequest.process(denied,new PktMachineEditorRequest(9,UUID.randomUUID(),EditorSaveState.revision(menu.snapshot()),new PktMachineEditorControllerSettings(name,1,false))).result().success(),"non-op wrote appearance");
            require(saved.equals(Files.readString(file)),"rejected edit changed file");
            Modularmachinery.LOG.info("[MMC][controller-appearance-regression] PASS: envelope codec, two-field JSON edits, reload/snapshot colors, duplicates, permissions, stale windows/revisions, invalid RGB");
        } finally { Files.writeString(file,original); MachineReloadListener.reloadNow(); }
    }

    private static void checkGeneratedModels() throws Exception {
        var sources = new ArrayList<net.minecraft.server.packs.repository.RepositorySource>();
        ControllerResourcePack.addPacks(new net.minecraftforge.event.AddPackFindersEvent(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, sources::add));
        require(sources.size() == 1, "generated client resource source absent");
        var packs = new ArrayList<net.minecraft.server.packs.repository.Pack>();
        sources.get(0).loadPacks(packs::add);
        require(packs.size() == 1, "generated client resource pack metadata invalid");
        try (var resources = packs.get(0).open()) {
            for (var entry : DedicatedControllers.entries()) {
                String id = entry.block().getId().getPath();
                var item = resources.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                        new ResourceLocation("modularmachinery", "models/item/" + id + ".json"));
                var states = resources.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                        new ResourceLocation("modularmachinery", "blockstates/" + id + ".json"));
                require(item != null && states != null, "missing generated models for " + id);
                try (var in = item.get(); var st = states.get()) {
                    var model = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    var variants = JsonParser.parseString(new String(st.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
                    String base = entry.factory() ? "blockfactorycontroller" : "blockcontroller";
                    require(model.get("parent").getAsString().equals("modularmachinery:block/" + base) && variants.size() == 8,
                            "wrong item parent/facing/formed variants: " + id);
                }
            }
        }
        Modularmachinery.LOG.info("[MMC][controller-model-regression] PASS: actual generated pack loading, every dedicated item model and eight facing/formed variants");
    }

    private static void tick(ServerLevel level,BlockPos pos,TileController tile){
        if(tile instanceof TileFactoryController factory)FactoryTicker.tick(level,pos,factory);else MachineTicker.tick(level,pos,tile);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError("Dedicated controllers: "+message);}
}
