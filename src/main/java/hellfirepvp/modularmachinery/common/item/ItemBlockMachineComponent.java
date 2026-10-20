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
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A machine component whose display name names its tier.
 *
 * <h2>What upstream had, and what replaced it</h2>
 *
 * <p>The jar's {@code ItemBlockCustomName} overrode {@code func_77667_c(ItemStack)} — 1.12.2's
 * {@code getUnlocalizedName(ItemStack)} — and returned a key built from the stack's metadata, so a big input bus
 * read "Big Item Input" and a tiny one read "Tiny Item Input" rather than both reading "Item Input Bus".
 *
 * <p>1.20.1 removed both invariants that made that work: there is no metadata, and a name is no longer an
 * unlocalised key a subclass can compose. What replaced the first is the stack's {@code BlockStateTag} (§270) and
 * what replaced the second is {@link #getName(ItemStack)} returning a {@link Component}.
 *
 * <h2>The keys were already there</h2>
 *
 * <p>This class adds no lang keys, because the port already ships every one of them — the asset conversion
 * brought them across:
 *
 * <pre>
 *   block.modularmachinery.blockinputbus        "Item Input Bus"
 *   block.modularmachinery.blockinputbus.tiny   "Tiny Item Input"
 *   block.modularmachinery.blockinputbus.big    "Big Item Input"   ...
 * </pre>
 *
 * <p>So this is the fourth case this session of the same shape: the mechanism exists, the data exists, and
 * nothing joined them. {@code getDescriptionId()} already returns the base key, so the tier suffix is the whole
 * lookup.
 */
public class ItemBlockMachineComponent extends BlockItem {

    public ItemBlockMachineComponent(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the tier off the stack and returns {@code <base key>.<tier>}. Falls back to the base name whenever
     * the stack carries no tier — an untagged stack from {@code /give}, a stack from a world saved before the
     * tag existed, or a block that has no variants at all.
     *
     * <p><b>Three property names, because the blocks do not agree.</b> The buses and hatches write {@code size};
     * the casing writes {@code casing}; the parallel controller, the upgrade bus and the smart interface write
     * {@code type}. Reading only one would leave the other families showing their base name and look exactly like
     * the feature not working, rather than like part of it working — which is precisely what PLAN 629 was (the
     * list held two of the three names) and what PLAN 731 was for the third: the parallel controller and the
     * upgrade bus had five language keys each and no way to reach any of them.
     *
     * <p><b>The list is {@link BlockMachineComponent#VARIANT_PROPERTY_NAMES}, read and not copied.</b> This class
     * used to spell the names out itself, and a second copy of a name list next to the first is the same defect
     * waiting to happen a third time.
     */
    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTagElement("BlockStateTag");
        if (tag != null) {
            for (String property : hellfirepvp.modularmachinery.common.block.BlockMachineComponent
                    .VARIANT_PROPERTY_NAMES) {
                String tier = tag.getString(property);
                if (!tier.isEmpty()) {
                    return Component.translatable(getDescriptionId() + "." + tier);
                }
            }
        }
        return super.getName(stack);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>THE TIER'S OWN FIGURE, UNDER THE NAME.</b> Upstream's small line — "最多可以同时运行 §a512§7 个相同配方。"
     * on an ultimate parallel controller, "最多可以放置 §a18§7 种不同的升级。" on an ultimate upgrade bus — is
     * added here, because this is the only object on the tooltip path that holds the stack: see
     * {@link hellfirepvp.modularmachinery.common.block.BlockMachineComponent#tipLine} for the 1.20.1
     * signature reading that makes the block unable to do it.</p>
     *
     * <p>This method resolves the STATE and nothing else. Which key the family uses and which number the tier
     * declares both live on the shared base — one rule, one place — so this class cannot drift from the block
     * that places the stack. It is the same split {@link #getName} already uses for the tier's NAME.</p>
     *
     * <p>The tag is read the way vanilla's own {@code updateBlockStateFromTag} reads it, so the state built
     * here is the state the same stack places.</p>
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                @NotNull java.util.List<Component> tooltip,
                                @NotNull net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        Block block = getBlock();
        if (!(block instanceof hellfirepvp.modularmachinery.common.block.BlockMachineComponent component)) {
            // Not one of ours: no variant, no tier, no line. A BlockItem may be constructed over an arbitrary
            // block (the ME placeholders and the modularmagic stand-ins are), and the tip rule lives only on
            // this mod's own shared base.
            return;
        }
        BlockState state = block.defaultBlockState();
        CompoundTag tag = stack.getTagElement("BlockStateTag");
        if (tag == null) {
            return;
        }
        for (String propertyName : tag.getAllKeys()) {
            if (!(tag.get(propertyName) instanceof net.minecraft.nbt.StringTag value)) {
                continue;
            }
            BlockState withTier = setProperty(state, propertyName, value.getAsString());
            if (withTier == null) {
                continue;
            }
            // tipLines, NOT tipLine: the general form whose default is the single line (PLAN/audit row 41). The
            // energy hatch emits upstream's TWO lines here; every other family's list holds the one line it always
            // did, so this loop is the same behaviour for four families and the whole tip for the fifth.
            java.util.List<Component> lines = component.tipLines(withTier, value.getAsString());
            if (!lines.isEmpty()) {
                tooltip.addAll(lines);
                return;
            }
        }
    }

    /**
     * Applies one {@code BlockStateTag} property to a state, or returns {@code null} when this block does not
     * declare it.
     *
     * <p>Written out rather than delegating to {@code StateDefinition} because the loop above must be able to
     * say "this block does not declare that property" and move on: a stack may carry keys for properties this
     * block lost, and {@code setValue} would throw.</p>
     */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, String name, String value) {
        for (net.minecraft.world.level.block.state.properties.Property<?> candidate : state.getProperties()) {
            if (!candidate.getName().equals(name)) {
                continue;
            }
            net.minecraft.world.level.block.state.properties.Property<T> property =
                    (net.minecraft.world.level.block.state.properties.Property<T>) candidate;
            return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(null);
        }
        return null;
    }
}