/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's selected ingredients and upgrade descriptions.
 */
package hellfirepvp.modularmachinery.client.preview;

import hellfirepvp.modularmachinery.common.machine.BlueprintSceneDefinition;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Material picking and tooltip content shared by the blueprint screen and embedded JEI preview. */
public final class BlueprintPreviewDetails {
    private BlueprintPreviewDetails() { }

    public static ItemStack materialStack(BlueprintPreviewScene scene, BlockPos pos, BlockState state) {
        if (state.isAir()) return ItemStack.EMPTY;
        if (state.getBlock() instanceof LiquidBlock) return BlueprintSceneDefinition.materialStack(state);
        try {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                ItemStack picked = state.getBlock().getCloneItemStack(state, hit, scene, pos, player);
                if (!picked.isEmpty()) return picked.copyWithCount(1);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // An external pick hook may require a real ClientLevel. Never attach a preview entity to that world.
        }
        return BlueprintSceneDefinition.materialStack(state);
    }

    public static List<ItemStack> materialStacks(BlueprintPreviewScene scene, BlockPos pos) {
        var cell = scene.definition().cells().get(pos);
        if (cell == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (BlockState state : cell.states()) {
            ItemStack stack = materialStack(scene, pos, state);
            if (!stack.isEmpty() && result.stream().noneMatch(previous -> ItemStack.isSameItemSameTags(previous, stack))) result.add(stack);
        }
        return result;
    }

    public static List<Component> materialTooltip(BlueprintPreviewScene scene, BlockPos pos) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.modularmachinery.blueprint.selected", pos.getX(), pos.getY(), pos.getZ()));
        for (ItemStack stack : materialStacks(scene, pos)) lines.add(stack.getHoverName());
        if (lines.size() == 1) lines.add(Component.translatable("gui.modularmachinery.blueprint.no_material"));
        return lines;
    }

    public static ItemStack representative(BlueprintSceneDefinition.Upgrade upgrade) {
        for (var states : upgrade.cells().values()) for (var state : states) {
            ItemStack stack = BlueprintSceneDefinition.materialStack(state);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static List<Component> upgradeTooltip(BlueprintSceneDefinition definition, int index, boolean enabled) {
        var upgrade = definition.upgrades().get(index);
        List<Component> lines = new ArrayList<>();
        ItemStack icon = representative(upgrade);
        lines.add(upgrade.name() != null && !upgrade.name().isBlank() ? Component.translatable(upgrade.name())
                : icon.isEmpty() ? Component.translatable("gui.modularmachinery.blueprint.upgrades") : icon.getHoverName());
        for (String description : upgrade.descriptions()) {
            for (String line : Component.translatable(description).getString().split("\n")) lines.add(Component.literal(line));
        }
        upgrade.cells().entrySet().stream().limit(4).forEach(entry -> {
            BlockPos pos = entry.getKey();
            var states = entry.getValue();
            lines.add(Component.literal("(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") ")
                    .append(states.isEmpty() ? Component.translatable("gui.modularmachinery.blueprint.no_material")
                    : BlueprintSceneDefinition.materialStack(states.get(0)).getHoverName()).withStyle(ChatFormatting.GRAY));
        });
        if (upgrade.cells().size() > 4) lines.add(Component.translatable("gui.modularmachinery.blueprint.more",
                upgrade.cells().size() - 4).withStyle(ChatFormatting.GRAY));
        for (RecipeModifier modifier : upgrade.modifiers()) {
            String operation = modifier.getOperation() == RecipeModifier.OPERATION_MULTIPLY ? "×"
                    : modifier.getModifier() >= 0 ? "+" : "";
            String target = modifier.getTarget() == null ? "?" : modifier.getTarget();
            if (target.contains(":")) target = target.substring(target.indexOf(':') + 1);
            String key = "gui.modularmachinery.blueprint.modifier." + target;
            Component targetText = net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(target);
            Component io = Component.translatable(modifier.getIOTarget() == null ? "gui.modularmachinery.blueprint.modifier.both"
                    : "gui.modularmachinery.blueprint.modifier." + modifier.getIOTarget().name().toLowerCase(Locale.ROOT));
            lines.add(Component.literal(operation + String.format(Locale.ROOT, "%.4g", modifier.getModifier()) + " ")
                    .append(targetText).append(" · ").append(io).append(modifier.isChance()
                            ? Component.translatable("gui.modularmachinery.blueprint.modifier.chance") : Component.empty()));
        }
        lines.add(Component.translatable(enabled ? "gui.preview.button.toggle_upgrade_display.item.disable_overlay.tip"
                : "gui.preview.button.toggle_upgrade_display.item.enable_overlay.tip").withStyle(ChatFormatting.YELLOW));
        return lines;
    }
}
