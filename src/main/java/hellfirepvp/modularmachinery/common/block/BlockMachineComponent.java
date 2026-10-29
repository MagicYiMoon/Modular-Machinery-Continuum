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
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Root class for every Modular Machinery machine component block.
 *
 * <p><b>What changed from 1.12.2.</b> Upstream extended {@code net.minecraft.block.BlockContainer} and
 * overrode {@code hasTileEntity}/{@code createTileEntity} plus their SRG twins
 * ({@code func_149716_u}/{@code func_149915_a}). Both the class and those hooks are gone. On 1.20.1 a
 * block that owns a {@link BlockEntity} implements {@link EntityBlock} and supplies
 * {@code newBlockEntity}; ticking is opted into separately through {@code getTicker}, per
 * block-entity-type.
 *
 * <p>The {@code BlockContainer} constructors also disappeared, so the 1.12.2
 * {@code (Material)} / {@code (Material, MapColor)} pair collapses into a single
 * {@link net.minecraft.world.level.block.state.BlockBehaviour.Properties} carrying material,
 * strength and map colour together.
 *
 * <p>Components with no variant dimension override {@link #newBlockEntity} directly; variant-keyed
 * families go through {@link BlockVariantComponent}, which dispatches on the blockstate property.
 */
public abstract class BlockMachineComponent extends Block implements EntityBlock {

    protected BlockMachineComponent(Properties properties) {
        super(properties);
    }

    /**
     * Default: a component with no block entity.
     *
     * <p>Deliberately not abstract. A casing is a machine component for structure-matching purposes
     * yet owns no block entity at all, and forcing every component to declare a block entity type just
     * to return {@code null} made the hierarchy noisier than the problem warranted.
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    /**
     * Default ticker: none.
     *
     * <p>1.12.2 delivered ticking through {@code ITickable} on the tile, called once per tick by
     * {@code World#updateEntities}, so every tile ticked whether or not it needed to. On 1.20.1 the
     * block decides per block-entity type, so a component that only stores state pays nothing.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return null;
    }

    


















 
    @Override
    public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos,
                              BlockState state, @Nullable BlockEntity blockEntity,
                              net.minecraft.world.item.ItemStack tool) {
        if (level.isClientSide()) {
            return;
        }
        // Keep vanilla block loot/statistics even when the component has no inventory.
        // onRemove may already have ejected a controller blueprint before this callback.
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        BlockEntity be = blockEntity != null ? blockEntity : level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        net.minecraftforge.items.IItemHandler handler = be instanceof hellfirepvp.modularmachinery.common.tiles.base.BreakInventoryProvider provider
                ? provider.getBreakInventory()
                : be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            net.minecraft.world.item.ItemStack inSlot = handler.getStackInSlot(slot);
            if (!inSlot.isEmpty()) {
                Block.popResource(level, pos, inSlot.copy());
            }
        }
    }
    /**
     * A MACHINE COMPONENT READS AS A COMPARATOR INPUT (audit row 39).
     *
     * <p><b>1.20.1's names, verified with {@code javap} against the mapped jar rather than remembered</b> — they
     * are the pair the audit flagged as the porting trap:</p>
     *
     * <pre>
     *   FORGE 1.20.1-47.3.0  BlockBehaviour  (javap -p)
     *     public boolean hasAnalogOutputSignal(net.minecraft.world.level.block.state.BlockState)
     *     public int     getAnalogOutputSignal(BlockState, Level, BlockPos)
     *   and its own default bodies are `iconst_0; ireturn` — false and 0.
     *
     *   1.12.2 upstream used the OTHER pair: hasComparatorInputOverride(IBlockState) /
     *   getComparatorInputOverride(IBlockState, World, BlockPos).  <<< THE DEVIATION (rule A0)
     * </pre>
     *
     * <p>Both names changed, so a port that copied upstream's override names would have compiled nothing at all:
     * the methods would have been new, unconnected members and the comparator would have stayed silent. That is
     * why this pair is stated here with the jar's own signatures beside it.</p>
     *
     * <h2>One landing site for four families, which is a deviation and is recorded as one</h2>
     *
     * <p><b>Upstream put the override on each of the four blocks</b> ({@code BlockBus} U136-U143,
     * {@code BlockEnergyHatch} U105-U110, {@code BlockFluidHatch} U138-U143, {@code BlockController} U259-U265) and
     * reached ONE shared helper for three of them. The port puts the pair here, on the base every one of those four
     * extends, because the audit's landing site is this class and because the dispatch is the whole rule: one
     * {@code instanceof} chain that no family can be missing from. A fifth family that gains an amount adds a
     * branch here and NOT an override it might forget to write.</p>
     *
     * <p>{@code hasAnalogOutputSignal} is true for every component, including the three families that answer 0 — a
     * casing next to a comparator reads 0 instead of the comparator deciding the block is not a signal source.
     * Upstream returned true on its four blocks only. The observable difference is between "0" and "vanilla never
     * asks", and 0 is the honest answer for a component with no amount: see
     * {@link hellfirepvp.modularmachinery.common.util.RedstoneHelper} for why 0 is also what an EMPTY bus reports.</p>
     */
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    





















 
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            return controller.isCrafting() ? 15 : controller.isFormed() ? 1 : 0;
        }
        return hellfirepvp.modularmachinery.common.util.RedstoneHelper.getRedstoneLevel(be);
    }

    

















 
    public static final java.util.List<String> VARIANT_PROPERTY_NAMES =
            java.util.List.of("size", "casing", "type");

    













 
    public static net.minecraft.world.level.block.state.properties.Property<?> variantProperty(
            BlockState state) {
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if (VARIANT_PROPERTY_NAMES.contains(property.getName())) {
                return property;
            }
        }
        return null;
    }

    








































 
    @Nullable
    public net.minecraft.network.chat.Component tipLine(BlockState state, String tierName) {
        return null;
    }

    /**
     * The SAME tip as {@link #tipLine}, for a family whose tip is MORE THAN ONE LINE (audit row 41, the energy
     * hatch).
     *
     * <h2>Why the single-line hook could not carry it</h2>
     *
     * <p>Upstream's energy hatch tooltip is two lines from one body — {@code BlockEnergyInputHatch} U54-U58 emits
     * {@code tooltip.energyhatch.storage} and {@code tooltip.energyhatch.in.accept} under one guard, and the output
     * hatch U54-U58 the storage line plus {@code .out.transfer}. <b>The port's tooltip plumbing was built from the
     * families that emit exactly one number</b> (a bus's slots, a fluid hatch's capacity, a controller's
     * parallelism, an upgrade bus's slots), so {@link #tipLine} returns one {@code Component} and the item adds it.
     * Cramming two facts into one line would have made the energy hatch the only family whose tip does not match
     * upstream's shape, for no reason other than the hook being narrow.</p>
     *
     * <p><b>Default: the single line, or nothing.</b> Every family that exists keeps its behaviour byte for byte —
     * this method is the general form and {@link #tipLine} remains the narrow form the other four implement.</p>
     *
     * @param state    the variant state, whose value must be the one {@code tierName} names
     * @param tierName the serialised tier from the stack's {@code BlockStateTag}
     * @return the lines to add, in upstream's order; empty when this block has no tip
     */
    public java.util.List<net.minecraft.network.chat.Component> tipLines(BlockState state, String tierName) {
        net.minecraft.network.chat.Component line = tipLine(state, tierName);
        return line == null ? java.util.List.of() : java.util.List.of(line);
    }

    /** The serialized name of a property value, in the form the item name lookup expects. */
    private static String variantName(net.minecraft.world.level.block.state.properties.Property<?> property,
                                      BlockState state) {
        Object value = state.getValue(property);
        return value instanceof net.minecraft.util.StringRepresentable sr
                ? sr.getSerializedName()
                : String.valueOf(value).toLowerCase(java.util.Locale.ROOT);
    }

    






 
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(
            BlockState state, net.minecraft.world.phys.HitResult target,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.item.ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        net.minecraft.world.level.block.state.properties.Property<?> variant = variantProperty(state);
        if (variant != null) {
            stack.getOrCreateTagElement("BlockStateTag")
                    .putString(variant.getName(), variantName(variant, state));
        }
        return stack;
    }
}
