/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.item;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A blueprint: an item that names one machine, so it can be carried and dropped into a controller.
 *
 * <h2>Why this exists as a unit of its own</h2>
 *
 * <p>Blueprints were missing from this port entirely, and that absence was <b>blocking three screens</b>,
 * not merely one feature:
 *
 * <ul>
 *   <li>{@code ContainerController} has a blueprint slot at <b>(151, 8)</b>;</li>
 *   <li>{@code ContainerFactoryController} has one at <b>(255, 8)</b>;</li>
 *   <li>and {@code GuiScreenBlueprint} is the preview screen itself.</li>
 * </ul>
 *
 * <p>The slots could not be added without this item, because a slot that accepts nothing is worse than no
 * slot — it invites a player to try. So the item comes first and the slots follow.
 *
 * <h2>The data model</h2>
 *
 * <p>Read from the jar's {@code ItemBlueprint}: a blueprint stores <b>only the machine's key</b>, under the
 * NBT name {@code "dynamicmachine"}. It does not copy the machine definition. That is the right design and
 * worth preserving: the pattern, the parts and the modifiers stay owned by the JSON, so a pack author
 * editing a machine updates every existing blueprint at once, and a blueprint whose machine has been
 * deleted resolves to nothing rather than to a stale copy.
 *
 * <p>{@link #getAssociatedMachine(ItemStack)} therefore does a <b>live lookup</b> through the machine
 * loader on every call, and can return {@code null} — which callers must handle, because it is exactly what
 * happens after a pack removes a machine a player is still holding a blueprint for.
 */
public class ItemBlueprint extends Item {

    /**
     * The NBT key, taken verbatim from the jar rather than chosen.
     *
     * <p>It must match upstream because blueprints are exchanged between worlds and pack versions; a
     * different key would silently orphan every blueprint written by the original mod.
     */
    public static final String DYNAMIC_MACHINE_NBT_KEY = "dynamicmachine";

    public ItemBlueprint() {
        // The source item explicitly uses a stack limit of 16.  Blueprint identity lives in
        // the stack NBT, so identical machine blueprints can be carried together while still
        // remaining independent when a player binds a different machine.
        super(new Item.Properties().stacksTo(16));
    }

    /**
     * @return the machine key this blueprint names, or {@code null} if it names none
     */
    @Nullable
    public static ResourceLocation getAssociatedMachineKey(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DYNAMIC_MACHINE_NBT_KEY)) {
            return null;
        }
        // A malformed value must not throw here. Blueprints are player data and can be edited, so an
        // unparseable key is treated as "names nothing" -- ResourceLocation.tryParse returns null rather
        // than throwing, which keeps a corrupted item from crashing the tooltip or the slot.
        return ResourceLocation.tryParse(tag.getString(DYNAMIC_MACHINE_NBT_KEY));
    }

    /**
     * Resolves this blueprint's machine <b>live</b> from the loader.
     *
     * @return the machine, or {@code null} if the blueprint names none or names one that no longer exists.
     *         A non-null return is not guaranteed and callers must not assume it.
     */
    @Nullable
    public static DynamicMachine getAssociatedMachine(ItemStack stack) {
        ResourceLocation key = getAssociatedMachineKey(stack);
        if (key == null) {
            return null;
        }
        hellfirepvp.modularmachinery.common.machine.MachineLoader loader =
                MachineReloadListener.getLoader();
        hellfirepvp.modularmachinery.common.machine.DynamicMachine byKey = loader.getMachines().get(key);
        if (byKey != null) {
            return byKey;
        }
        // TOLERANT FALLBACK (PLAN section 265): blueprints written BEFORE the namespace fix carry
        // "minecraft:<name>", and a pack's own blueprint could carry any namespace. findByName compares the
        // bare registry name, so the path alone is enough to resolve one. Without this, every blueprint
        // already sitting in a player's inventory would stay unknown forever.
        return loader.findByName(key.getPath());
    }

    public static void setAssociatedMachine(ItemStack stack, DynamicMachine machine) {
        // WITH THE MOD'S NAMESPACE, NOT THE IMPLICIT "minecraft" ONE (PLAN section 265).
        //
        // This read `new ResourceLocation(machine.getRegistryName())`, and getRegistryName() holds the
        // JSON's bare "registryname" -- "test_multiblock". The one-argument ResourceLocation constructor
        // supplies "minecraft" for a missing namespace, so every blueprint made this way claimed
        // "minecraft:test_multiblock".
        //
        // Meanwhile MachineLoader keys its map with new ResourceLocation(MODID, name), so the lookup was
        // "minecraft:test_multiblock" against a table holding "modularmachinery:test_multiblock". Reported by
        // the client as the tooltip reading "未知机器: minecraft:test_multiblock".
        setAssociatedMachine(stack,
                new ResourceLocation(hellfirepvp.modularmachinery.Modularmachinery.MODID,
                        machine.getRegistryName()));
    }

    public static void setAssociatedMachine(ItemStack stack, ResourceLocation key) {
        stack.getOrCreateTag().putString(DYNAMIC_MACHINE_NBT_KEY, key.toString());
    }

    /**
     * Opens the preview when a blueprint is used on a block.
     *
     * <p>The jar's {@code useOn} and {@code use} do <b>exactly the same thing</b> — both resolve the machine
     * from the stack and open {@code BLUEPRINT_PREVIEW}. Upstream needed two overrides because 1.12.2 routes
     * "hit a block" and "air click" through different methods; on 1.20.1 the equivalent pair is
     * {@link #useOn} and {@link #use}.
     *
     * <p><b>A client-side open with no packet.</b> The preview needs only the machine definition, which the
     * client already has from the synced reload listener, and {@code GuiScreenBlueprint} is a plain
     * {@code GuiScreen} with no container — so no menu type and no channel are involved. Contrast the
     * controller screens, which are containers and therefore need §58's packet.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        openPreview(context.getLevel(), context.getPlayer(), context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        openPreview(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Opens the preview for this blueprint's machine, or does nothing.
     *
     * <p>A blueprint naming an unresolvable machine opens <b>nothing</b> rather than an empty preview: the
     * tooltip already reports the machine as unknown (§68), and a blank panel would instead suggest the
     * machine exists but has no parts.
     *
     * <p>Guarded on {@code level.isClientSide} and wrapped in {@code DistExecutor} so the client-only
     * {@code Minecraft} class is never loaded on a dedicated server — a direct reference here would crash
     * server startup with a {@code NoClassDefFoundError}, which is the classic way this mistake surfaces.
     */
    private void openPreview(Level level, @Nullable Player player, ItemStack stack) {
        if (!level.isClientSide || player == null) {
            return;
        }
        DynamicMachine machine = getAssociatedMachine(stack);
        if (machine == null) {
            return;
        }
        // The lambda body references ONLY ClientSetup, by name. It must not name Minecraft, Screen or
        // ScreenBlueprint here -- doing so compiles a synthetic method on THIS class that pulls in
        // net.minecraft.client.gui.screens.Screen, and the dedicated server then fails to load the item.
        // See ClientSetup#openBlueprintPreview.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> hellfirepvp.modularmachinery.client.ClientSetup
                        .openBlueprintPreview(machine));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Names the machine, or says so plainly when it cannot be resolved. A blueprint that silently showed
     * nothing would leave a player holding an item that looks inert and gives no reason why -- and after a
     * pack removes a machine, that reason is the only thing that explains what happened.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        ResourceLocation key = getAssociatedMachineKey(stack);
        if (key == null) {
            tooltip.add(Component.translatable("tooltip.modularmachinery.blueprint.unset")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        DynamicMachine machine = getAssociatedMachine(stack);
        if (machine == null) {
            tooltip.add(Component.translatable("tooltip.modularmachinery.blueprint.missing", key.toString())
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable(machine.getTranslationKey())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
