/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.network;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.BasicRequirementType;
import hellfirepvp.modularmachinery.common.editor.DurabilityEditor;
import hellfirepvp.modularmachinery.common.editor.SmartInterfaceEditor;
import hellfirepvp.modularmachinery.common.editor.FluidTickEditor;
import hellfirepvp.modularmachinery.common.editor.PneumaticEditor;
import hellfirepvp.modularmachinery.common.editor.EnvironmentEditor;
import hellfirepvp.modularmachinery.common.editor.ItemRequirementEditor;
import hellfirepvp.modularmachinery.common.editor.ResourceAmountEditor;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Supplier;

/** Adds a complete, validated requirement in one save. A new recipe is created with its first requirement. */
public record PktMachineEditorRequirementAdd(String recipeId, String type, String resourceId, boolean output,
                                              int amount, float chance, String newMachine, int ticks, String displayName) {
    public static void send(PktMachineEditorRequirementAdd message) {
        ModularmachineryNetwork.CHANNEL.sendToServer(message);
    }

    public static void encode(PktMachineEditorRequirementAdd message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.recipeId, 256);
        buffer.writeUtf(message.type, 16);
        buffer.writeUtf(message.resourceId, 256);
        buffer.writeBoolean(message.output);
        buffer.writeVarInt(message.amount);
        buffer.writeFloat(message.chance);
        buffer.writeUtf(message.newMachine, 256);
        buffer.writeVarInt(message.ticks);
        buffer.writeUtf(message.displayName, 200);
    }

    public static PktMachineEditorRequirementAdd decode(FriendlyByteBuf buffer) {
        return new PktMachineEditorRequirementAdd(buffer.readUtf(256), buffer.readUtf(16), buffer.readUtf(256),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readFloat(), buffer.readUtf(256),
                buffer.readVarInt(), buffer.readUtf(200));
    }

    public static void handle(PktMachineEditorRequirementAdd message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) context.getSender().displayClientMessage(Component.literal("请使用新版编辑器重新提交修改"), true);
        });
        context.setPacketHandled(true);
    }

    static String apply(ServerPlayer player, PktMachineEditorRequirementAdd message) {
        try {
            return save(player, message, BasicRequirementType.fromKey(message.type)
                    .create(message.resourceId, message.output, message.amount, message.chance), -1);
        } catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }

    /** Shared atomic file writer. Group edits validate and merge with the actual source before writing. */
    static String save(ServerPlayer player, PktMachineEditorRequirementAdd message, JsonObject requirement, int replacementIndex) {
        if (!player.hasPermissions(2) || !(player.containerMenu instanceof MenuMachineEditor)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
        try {
            var recipes = MachineReloadListener.getRecipeLoader();
            var root = ModDataHolder.INSTANCE.getRecipeDirectory().getCanonicalFile().toPath();
            Path file;
            JsonObject json;
            boolean creating = !message.newMachine.isBlank();
            if (creating && replacementIndex != -1) throw new IllegalArgumentException("新配方只能添加首个需求");
            if (creating) {
                if (!message.recipeId.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,120}")
                        || recipes.getRecipe(message.recipeId) != null
                        || MachineReloadListener.getLoader().findByName(message.newMachine) == null
                        || message.ticks < 1 || message.ticks > 2_000_000_000 || message.displayName.length() > 200)
                    throw new IllegalArgumentException("新配方信息无效或 ID 已存在");
                file = root.resolve(message.recipeId + ".json").toFile().getCanonicalFile().toPath();
                if (!file.startsWith(root) || Files.exists(file)) throw new IllegalArgumentException("配方文件已存在");
                json = new JsonObject();
                json.addProperty("machine", message.newMachine);
                json.addProperty("registryName", message.recipeId);
                json.addProperty("recipeTime", message.ticks);
                if (!message.displayName.isBlank()) json.addProperty("displayName", MachineNameFormatting.display(message.displayName));
                json.add("requirements", new JsonArray());
            } else {
                if (!recipes.isSourceEditable(message.recipeId)) throw new IllegalArgumentException("该配方只读，请修改其源文件");
                file = new java.io.File(recipes.getSourcePath(message.recipeId)).getCanonicalFile().toPath();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) return "编辑数据无效、来源只读或文件不存在，未保存修改";
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
            if (!json.has("requirements") || !json.get("requirements").isJsonArray())
                throw new IllegalArgumentException("配方需求列表无效");
            var requirements = json.getAsJsonArray("requirements");
            if (!creating && hellfirepvp.modularmachinery.common.editor.CatalystEditor.isCatalyst(requirement)
                    && !json.equals(recipes.getRecipeSource(message.recipeId)))
                throw new IllegalArgumentException("催化剂源文件已发生变化，请重新加载并打开编辑器");
            var smartOptions = creating ? SmartInterfaceEditor.options(MachineReloadListener.getLoader().findByName(message.newMachine))
                    : SmartInterfaceEditor.options(message.recipeId, MachineReloadListener.getLoader(), recipes);
            if ((ItemRequirementEditor.isItem(requirement) || DurabilityEditor.isDurability(requirement) || hellfirepvp.modularmachinery.common.editor.IngredientGroupEditor.isGroup(requirement) || EnvironmentEditor.isEnvironment(requirement) || PneumaticEditor.isPneumatic(requirement) || ResourceAmountEditor.isResource(requirement)) && !creating && !json.equals(recipes.getRecipeSource(message.recipeId)))
                throw new IllegalArgumentException("源配方文件已发生变化，请重新加载并打开编辑器");
            if ((SmartInterfaceEditor.isSmart(requirement) || FluidTickEditor.isRate(requirement)) && !creating) {
                var loaded = recipes.getRecipe(message.recipeId);
                var loadedMachines = new java.util.TreeSet<String>();
                for (String id : recipes.getRecipeNames())
                    if (recipes.getSourcePath(message.recipeId).equals(recipes.getSourcePath(id)))
                        loadedMachines.add(recipes.getRecipe(id).machine());
                var diskMachines = new java.util.TreeSet<String>();
                var owners = json.get("machine");
                if (owners != null && owners.isJsonArray()) owners.getAsJsonArray().forEach(owner -> diskMachines.add(owner.getAsString()));
                else if (owners != null && owners.isJsonPrimitive()) diskMachines.add(owners.getAsString());
                if (!loadedMachines.equals(diskMachines))
                    throw new IllegalArgumentException("源配方所属机器已变化，请重新加载并打开编辑器");
                if (loaded == null || loaded.requirements().size() != requirements.size())
                    throw new IllegalArgumentException("源需求与已加载配方不一致，请重新加载并打开编辑器");
                for (int i = 0; i < requirements.size(); i++) {
                    if (!requirements.get(i).equals(recipes.getRequirementSource(loaded.requirements().get(i))))
                        throw new IllegalArgumentException("源需求与已加载配方不一致，请重新加载并打开编辑器");
                }
            }
            if (replacementIndex >= 0) {
                if (replacementIndex >= requirements.size()) throw new IllegalArgumentException("需求位置已失效，请重新打开编辑器");
                var original = requirements.get(replacementIndex).getAsJsonObject();
                requirements.set(replacementIndex, ItemRequirementEditor.isItem(original) || ItemRequirementEditor.isItem(requirement)
                        ? ItemRequirementEditor.merge(original, requirement)
                        : EnvironmentEditor.isEnvironment(original) || EnvironmentEditor.isEnvironment(requirement)
                        ? EnvironmentEditor.merge(original, requirement)
                        : ResourceAmountEditor.isResource(original) || ResourceAmountEditor.isResource(requirement)
                        ? ResourceAmountEditor.merge(original, requirement)
                        : PneumaticEditor.isPneumatic(original) || PneumaticEditor.isPneumatic(requirement)
                        ? PneumaticEditor.merge(original, requirement)
                        : FluidTickEditor.isRate(original) || FluidTickEditor.isRate(requirement)
                        ? FluidTickEditor.merge(original, requirement)
                        : SmartInterfaceEditor.isSmart(original) || SmartInterfaceEditor.isSmart(requirement)
                        ? SmartInterfaceEditor.merge(original, requirement, smartOptions)
                        : hellfirepvp.modularmachinery.common.editor.FuelEditor.isFuel(original)
                        || hellfirepvp.modularmachinery.common.editor.FuelEditor.isFuel(requirement)
                        ? hellfirepvp.modularmachinery.common.editor.FuelEditor.merge(original, requirement)
                        : DurabilityEditor.isDurability(original) || DurabilityEditor.isDurability(requirement)
                        ? DurabilityEditor.merge(original, requirement)
                        : hellfirepvp.modularmachinery.common.editor.IngredientGroupEditor.merge(original, requirement));
            } else {
                if (replacementIndex != -1) throw new IllegalArgumentException("需求位置无效");
                if (requirements.size() >= 512) throw new IllegalArgumentException("需求数量已达上限");
                if (ItemRequirementEditor.isItem(requirement)) requirement = ItemRequirementEditor.merge(null,requirement);
                else if (EnvironmentEditor.isEnvironment(requirement)) requirement = EnvironmentEditor.merge(null, requirement);
                else if (ResourceAmountEditor.isResource(requirement)) requirement = ResourceAmountEditor.merge(null, requirement);
                else if (PneumaticEditor.isPneumatic(requirement)) requirement = PneumaticEditor.merge(null, requirement);
                else if (FluidTickEditor.isRate(requirement)) requirement = FluidTickEditor.merge(null, requirement);
                else if (SmartInterfaceEditor.isSmart(requirement)) requirement = SmartInterfaceEditor.merge(null, requirement, smartOptions);
                else if (hellfirepvp.modularmachinery.common.editor.IngredientGroupEditor.isGroup(requirement))
                    requirement = hellfirepvp.modularmachinery.common.editor.IngredientGroupEditor.merge(null, requirement);
                else if (DurabilityEditor.isDurability(requirement)) requirement = DurabilityEditor.merge(null, requirement);
                else if (hellfirepvp.modularmachinery.common.editor.FuelEditor.isFuel(requirement))
                    requirement = hellfirepvp.modularmachinery.common.editor.FuelEditor.merge(null, requirement);
                requirements.add(requirement);
            }
            var temp = Files.createTempFile(root, "requirement-", ".mmce.tmp");
            try {
                Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
                if (creating) {
                    // Do not replace an existing author's file, even if it appeared after validation.
                    Files.move(temp, file);
                } else {
                    try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
                }
            } finally { Files.deleteIfExists(temp); }
            player.displayClientMessage(Component.literal("已保存需求，正在重新加载"), true);
            MachineReloadListener.reloadNow();
            return null;
        } catch (IllegalArgumentException invalid) {
            player.displayClientMessage(Component.literal(invalid.getMessage()), true);
            return invalid.getMessage();
        } catch (Exception failure) {
            Modularmachinery.LOG.error("[MMCE] editor could not add requirement to {}", message.recipeId, failure);
            player.displayClientMessage(Component.literal("添加失败：" + failure.getMessage()), true);
            return "保存失败：" + failure.getMessage();
        }
    }
}
