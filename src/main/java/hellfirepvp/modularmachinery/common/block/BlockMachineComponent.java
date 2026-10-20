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

    /**
     * Spills this component's inventory when a player breaks it (PLAN section 364).
     *
     * <p><b>This was missing entirely, and the loss was silent.</b> Upstream's {@code BlockBus},
     * {@code BlockController} and {@code BlockUpgradeBus} each overrode {@code breakBlock} to spawn their contents;
     * the port had ZERO {@code breakBlock}/{@code playerDestroy}/{@code Containers.drop} overrides in all 125 of its
     * java files, so a full bus, hatch, upgrade bus or controller spilled NOTHING. A controller's BLUEPRINT -- the
     * item a player uses to build the machine -- was among the things destroyed.
     *
     * <p>One override covers every family because they all descend from this class, and the contents are read through
     * the {@code ITEM_HANDLER} capability rather than from a concrete tile type: that is what makes it a single method
     * instead of one per block, and what makes it work for a tile that decides to hold slots later.
     *
     * <p><b>Items only, and that is deliberate.</b> A fluid hatch's tanks could be drained here too, but a broken tank
     * losing its contents is vanilla behaviour and upstream's {@code breakBlock} spawned items only. Turning fluid loss
     * into item duplication would be a worse bug than the one being fixed.
     *
     * <p>The {@code blockEntity} parameter is {@code @Nullable} in the 1.20.1 signature, so the level is consulted as
     * a fallback -- the entity is still present at this point, before removal.
     */
    @Override
    public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos,
                              BlockState state, @Nullable BlockEntity blockEntity,
                              net.minecraft.world.item.ItemStack tool) {
        if (level.isClientSide()) {
            return;
        }
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

    /**
     * {@inheritDoc}
     *
     * <p>The three amount-carrying families go through {@link RedstoneHelper}, which is upstream's own helper;
     * the controller's formula is upstream's {@code BlockController} U266-U270 verbatim:</p>
     *
     * <pre>
     *   working            -> 15
     *   machine found      -> 1
     *   neither            -> 0
     * </pre>
     *
     * <p><b>Two of the three predicates have different names on either side of the port</b>, and the mapping is
     * stated rather than left to be re-derived: upstream's {@code isWorking()} is the port's
     * {@link hellfirepvp.modularmachinery.common.tiles.TileController#isCrafting()} (a recipe is advancing and no
     * hold reason is set — see that method), and upstream's {@code getFoundMachine() != null} is the port's
     * {@link hellfirepvp.modularmachinery.common.tiles.TileController#isFormed()}, because the machine definition a
     * formed controller carries is exactly what upstream's matcher had assigned when it answered non-null.</p>
     *
     * <p><b>1 is the interesting value and it is not decoration:</b> it distinguishes "this block is a controller
     * with a machine and it is idle" from "nothing here", which is the difference a player builds a redstone
     * indicator around.</p>
     */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller) {
            return controller.isCrafting() ? 15 : controller.isFormed() ? 1 : 0;
        }
        return hellfirepvp.modularmachinery.common.util.RedstoneHelper.getRedstoneLevel(be);
    }

    /**
     * THE ONE LIST OF PROPERTY NAMES THAT CARRY A VARIANT, and the third name was added by PLAN 731.
     *
     * <p>It exists because the callers disagreed and that disagreement was itself a defect (PLAN 629): the item
     * read {@code size} AND {@code casing}, the TOP provider read {@code size} ONLY, so a casing variant reached
     * the player as the plain base name with no variant line. The fix was to make every caller read one helper --
     * and then, in PLAN 731, the helper ITSELF turned out to be the short list: the controllers, the upgrade bus
     * and the smart interface declare {@code type}, so every one of them was invisible to the naming, to the
     * pick-block tag and to the item-model predicate. A list that two callers share is only as good as its
     * entries, which is why this is now read by {@link #variantProperty(BlockState)},
     * {@link hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent#getName} and
     * {@code ClientSetup.registerTierPredicates} instead of being spelled out in each.</p>
     *
     * <p>The names are blockstate PROPERTY names, not enum names: {@code size} is an {@code ItemBusSize} on a bus,
     * a {@code FluidHatchSize} on a fluid hatch and an {@code EnergyHatchData} on an energy hatch, and {@code type}
     * is a {@code ParallelControllerData} on a controller, an {@code UpgradeBusData} on an upgrade bus and a
     * {@code SmartInterfaceType} on a smart interface. Every block declares AT MOST ONE of these, so resolving by
     * name is unambiguous -- which is the only reason a name list is safe here at all.</p>
     */
    public static final java.util.List<String> VARIANT_PROPERTY_NAMES =
            java.util.List.of("size", "casing", "type");

    /**
     * The property carrying this block variant, or null when it has none.
     *
     * <p><b>IT EXISTS HERE RATHER THAN IN EACH CALLER BECAUSE THE CALLERS DISAGREED AND THAT WAS THE DEFECT (PLAN
     * 629).</b> The blocks do not use one property name: the buses and hatches declare {@code size}, the casing
     * declares {@code casing} and the controllers, the upgrade bus and the smart interface declare {@code type},
     * which {@link hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent} own javadoc already
     * recorded for the first two. THE ITEM READ BOTH NAMES AND THE TOP PROVIDER READ ONLY {@code size} -- SO A
     * CASING VARIANT REACHED THE PLAYER AS THE PLAIN BASE NAME WITH NO VARIANT LINE, visible only in the world
     * and never in the inventory. TWO CALLERS READING ONE HELPER CANNOT DRIFT APART AGAIN.</p>
     *
     * <p>IT RETURNS THE PROPERTY AND NOT A BOOLEAN so that "does this block have a variant" and "which property is
     * it" cannot be answered differently -- a boolean plus a separate search is exactly how the two callers
     * diverged.</p>
     */
    public static net.minecraft.world.level.block.state.properties.Property<?> variantProperty(
            BlockState state) {
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if (VARIANT_PROPERTY_NAMES.contains(property.getName())) {
                return property;
            }
        }
        return null;
    }

    /**
     * THE SMALL LINE UNDER THE ITEM'S NAME THAT SAYS WHAT THIS VARIANT GIVES YOU (upstream's per-family
     * {@code addInformation} tooltip), resolved from a STATE rather than from a stack.
     *
     * <h2>Why the state is the parameter, and why the ITEM is the caller</h2>
     *
     * <p><b>On 1.12.2 the tier was block METADATA and the tooltip was drawn by the BLOCK.</b> Upstream put
     * {@code addInformation} on {@code BlockBus} (L73), {@code BlockFluidHatch} (L58),
     * {@code BlockParallelController} (L47) and {@code BlockUpgradeBus} (L50), each reading
     * {@code stack.getMetadata()} and then that family's own getter. <b>1.20.1 breaks both halves of that</b>,
     * and both were read out of this port's dependency jar rather than assumed:</p>
     *
     * <ul>
     *   <li>there is no metadata; the tier travels in the stack's {@code BlockStateTag} (PLAN section 270);</li>
     *   <li>{@code Block#appendHoverText} on 1.20.1 is
     *       {@code (ItemStack, BlockGetter, List<Component>, TooltipFlag)} — <b>it is handed the LEVEL as its
     *       {@code BlockGetter} and no {@code BlockState} at all</b>. {@code BlockItem.appendHoverText} is
     *       exactly {@code Item.appendHoverText(...)} followed by
     *       {@code getBlock().appendHoverText(stack, level, tooltip, flag)} (read from the bytecode of
     *       {@code forge-1.20.1-47.3.0_mapped_official_1.20.1.jar}; {@code Block.appendHoverText}'s own body
     *       is a bare {@code return}). So the BLOCK cannot know which tier the stack is.</li>
     * </ul>
     *
     * <p>That is the rule A0 deviation: upstream's way is a BLOCK-side tooltip, and the 1.20.1 signature
     * cannot deliver it, so the tooltip is emitted by the ITEM
     * ({@link hellfirepvp.modularmachinery.common.item.ItemBlockMachineComponent#appendHoverText}), which does
     * hold the stack and therefore the tag. <b>The RULE still lives here</b>, on the shared base, reached
     * through one call — so no family re-implements "which key and which number", which is how PLAN 629 and
     * PLAN 731 drifted.</p>
     *
     * <h2>The argument is the tier's OWN value</h2>
     *
     * <p>{@code tierName} is the serialised constant the tag carries; this method resolves it through
     * {@link #variantProperty(BlockState)} so that the state and the tag are known to agree, then asks the
     * tier ITSELF for its number through {@link hellfirepvp.modularmachinery.common.block.prop.TierTipValue}
     * — never a lookup table, and never a literal. A family whose tier has no single unambiguous number
     * implements nothing and gets nothing; see that interface for the two families that deliberately do not.</p>
     *
     * @param state    the variant state, whose value must be the one {@code tierName} names
     * @param tierName the serialised tier from the stack's {@code BlockStateTag}
     * @return the line, or {@code null} when this block has no variant, no tier tip, or a tag naming another tier
     */
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

    /**
     * PICK-BLOCK CARRIES THE VARIANT WITH IT (PLAN 629).
     *
     * <p>VANILLA PICK-BLOCK PRODUCES A BARE STACK, so the middle-clicked item lost the very thing that names it: ItemBlockMachineComponent reads the tier out of a {@code BlockStateTag} and falls back to the base name when that tag is absent, WHICH IS WHY A PICKED "机械通风口" CAME BACK AS "机器外壳". The tag is written here in exactly the shape that lookup reads -- {@code BlockStateTag} holding the property name and its serialized value.</p>
     *
     * <p>AND IT IS DERIVED FROM THE SAME HELPER THE TOP PROVIDER USES, so the name in the world and the name on the
     * picked item cannot disagree.</p>
     */
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
