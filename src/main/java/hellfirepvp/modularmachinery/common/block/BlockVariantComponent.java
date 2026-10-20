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

import hellfirepvp.modularmachinery.common.block.prop.TierTipValue;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Base for machine components whose only blockstate dimension is a variant enum
 * ({@code size=…} for buses and hatches, {@code type=…} for controllers and buses).
 *
 * <p>1.12.2 expressed every one of these variants through block metadata, with
 * {@code BlockVariants#getIdentifierForMeta(int)} producing the model suffix and
 * {@code createTileEntity(World, IBlockState)} constructing the matching tile. On 1.20.1 the variant
 * is an {@link EnumProperty} and the model is chosen by the blockstate JSON, but the block entity is
 * still built per variant — so {@link #createBlockEntity} centralises that dispatch and subclasses
 * only say which variant maps to which block entity.
 *
 * <h2>Why the property is static</h2>
 *
 * <p><b>Vanilla calls {@code createBlockStateDefinition} from inside the {@link Block} constructor</b>
 * ({@code Block.<init>} → {@code StateDefinition.Builder#create}). Any property held in an
 * <i>instance</i> field is therefore still {@code null} at that moment — the subclass constructor body
 * has not run yet. The failure is unambiguous if you hit it:
 *
 * <pre>
 * Caused by: java.lang.NullPointerException: Cannot invoke
 *     "net.minecraft.world.level.block.state.properties.Property.getName()" because "p_61100_" is null
 *     at StateDefinition$Builder.validateProperty
 *     at hellfirepvp...BlockVariantComponent.createBlockStateDefinition
 *     at net.minecraft.world.level.block.Block.&lt;init&gt;
 * </pre>
 *
 * <p>Passing the property through the constructor does not help, because the assignment happens after
 * {@code super(...)} returns while the vanilla call happens during it. The property must therefore be
 * a {@code static final} field of the concrete class, or created by a static method — evaluated during
 * class initialisation, long before any instance exists. Subclasses supply it through
 * {@link #variantProperty()}.
 *
 * <h2>Why the variant is applied at PLACEMENT time and not only by vanilla</h2>
 *
 * <p><b>Vanilla applies a stack's {@code BlockStateTag} AFTER the block is placed, and the block entity has
 * already been built from the default state by then.</b> Read in the 1.20.1 bytecode, in this order:
 * {@code BlockItem.place} calls {@code placeBlock(context, state)} at bytecode 63 -- which is
 * {@code Level.setBlock(pos, state, 11)} ({@code UPDATE_NEIGHBORS|UPDATE_CLIENTS|UPDATE_IMMEDIATE}) -- and
 * only at bytecode 127 calls {@code updateBlockStateFromTag(pos, level, stack, stateReadBack)}, whose own tail
 * is {@code if (newState != state) level.setBlock(pos, newState, 2)} with <b>flag 2 = UPDATE_CLIENTS alone</b>.
 * {@code LevelChunk.setBlockState} consults {@code getBlockEntity(pos, EntityCreationType.CHECK)} first and
 * <b>calls {@code EntityBlock.newBlockEntity(pos, state) only when that returns null</b>; when a block entity
 * is already there it merely assigns {@code BlockEntity.blockState} -- a body that is one {@code putfield} and
 * a {@code return}.</p>
 *
 * <p>So without an override here the sequence is: the block is placed with the DEFAULT variant, the block
 * entity is constructed from that default (which is where a hatch's tank capacity comes from --
 * {@code TileFluidInputHatch} reads {@code state.getValue(SIZE).getCapacity()}), and the variant is corrected
 * afterwards on a block entity that is never rebuilt. <b>The world shows the right tier while the tile keeps
 * the default one</b>, which is the report this override answers ("every fluid hatch is 1000 mB, every energy
 * hatch is 8192 FE" -- 1000 and 8192 being exactly {@code FluidHatchSize.NORMAL} and
 * {@code EnergyHatchData.NORMAL}, i.e. the default state).</p>
 *
 * <p><b>1.12.2 had no such window.</b> Upstream expressed the variant as block METADATA
 * ({@code BlockFluidHatch.getStateFromMeta} -> {@code FluidHatchSize.values()[meta]}), and metadata is part of
 * the state the block is placed WITH, so {@code createTileEntity(World, IBlockState)} was handed the real tier
 * on the one and only call it ever gets. 1.20.1 removed metadata; the {@code BlockStateTag} replaced it, and
 * vanilla's own ordering is what opened this window. This override restores upstream's property -- the tier is
 * known when the tile is built -- through the port's own carrier.</p>
 *
 * @param <T> the variant enum; must be {@link StringRepresentable} so the serialised name matches the
 *            JSON keys in the shipped blockstates
 */
public abstract class BlockVariantComponent<T extends Enum<T> & StringRepresentable>
        extends BlockMachineComponent {

    /** The key vanilla's own {@code updateBlockStateFromTag} reads the tier out of. */
    private static final String BLOCK_STATE_TAG = "BlockStateTag";

    protected BlockVariantComponent(Properties properties) {
        super(properties);
    }

    /**
     * @return the variant property, from a {@code static final} field or static factory; must never
     *         depend on instance state
     */
    protected abstract EnumProperty<T> variantProperty();

    /** @return the variant encoded in the given state */
    public T getVariant(BlockState state) {
        return state.getValue(variantProperty());
    }

    /**
     * {@inheritDoc}
     *
     * <p>THE ONE FAMILY-INDEPENDENT HALF of the tier tip line: resolve the tier the tag names through the
     * BLOCK'S OWN PROPERTY, and hand the resulting constant to {@link TierTipValue} for its own number. The
     * two family-specific halves are {@link #tipKey()} and, for the one family whose tier changes its own
     * wording, {@link #tipLine}. Downstream families therefore carry no code at all.</p>
     *
     * <p><b>The tag's value is resolved through the property and not through {@code values()[ordinal]}</b>,
     * which is what upstream did with metadata: a tag naming a constant this property does not allow yields
     * {@code null} here and emits no line, where an ordinal lookup would have silently emitted a DIFFERENT
     * tier's number. The state is also compared, so a state and a tag that disagree report nothing rather
     * than the tag's number next to the state's model.</p>
     */
    @Nullable
    @Override
    public net.minecraft.network.chat.Component tipLine(BlockState state, String tierName) {
        EnumProperty<T> property = variantProperty();
        Optional<T> resolved = property.getValue(tierName);
        if (resolved.isEmpty()) {
            return null;
        }
        T tier = resolved.get();
        if (!tier.equals(state.getValue(property))) {
            return null;
        }
        if (!(tier instanceof TierTipValue tipping)) {
            return null;
        }
        String key = tipKey();
        return key == null ? null
                : net.minecraft.network.chat.Component.translatable(key, tipping.getTipValue());
    }

    /**
     * The language key of this family's tier tip line, or {@code null} when this family has none.
     *
     * <p>Deliberately a method on the BLOCK and not a field on the enum: the key is one string per FAMILY
     * (both upgrade-bus tiers that exist share one), while the number is one per TIER, and the two belong
     * with the things that have one each.</p>
     */
    @Nullable
    protected String tipKey() {
        return null;
    }

    /**
     * Builds the block entity for a specific variant.
     *
     * @return the block entity, or {@code null} for a variant that owns none
     */
    @Nullable
    protected abstract BlockEntity createBlockEntity(T variant, BlockPos pos, BlockState state);

    @Nullable
    @Override
    public final BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return createBlockEntity(getVariant(state), pos, state);
    }

    /**
     * Returns the state a PLACING STACK asks for, with its {@code BlockStateTag} already applied.
     *
     * <p>This is the port's equivalent of upstream's "the metadata is known when the tile is built" (see the
     * class javadoc): it is the only hook on the placement path that runs BEFORE the block entity exists, and
     * therefore the only place the tier can be applied and still reach {@link #newBlockEntity}. It is
     * deliberately NOT a re-implementation of vanilla's {@code updateBlockStateFromTag}: it applies the ONE
     * property this class owns and leaves every other key to vanilla, which still runs its own routine after
     * placement -- harmlessly, because with the tier already applied vanilla's own rebuilt state IS the state
     * that was placed, so its {@code if (newState != state)} comparison is false and its second
     * {@code setBlock} is skipped altogether.</p>
     *
     * <p>Every path that cannot resolve a value returns {@link #defaultBlockState()}, which is exactly what
     * vanilla's own {@code BlockBehaviour.getStateForPlacement} returns -- so an untagged stack (a hand-placed
     * block from any other source), a tag naming another property, and a tag whose value does not parse all
     * keep the behaviour this port had before the override existed.</p>
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();

        // The carrier, read the way vanilla's own routine reads it: an ItemStack tag compound, absent when the
        // stack came from anywhere other than one of this mod's variant sweeps.
        CompoundTag tag = context.getItemInHand().getTagElement(BLOCK_STATE_TAG);
        if (tag == null) {
            return state;
        }

        // THE PROPERTY IS THIS BLOCK'S OWN, never a lookup by name: `size` is an ItemBusSize on a bus, a
        // FluidHatchSize on a fluid hatch and an EnergyHatchData on an energy hatch, so a name-based search
        // could silently resolve the wrong enum (the trap TileFluidInputHatch's javadoc already records).
        EnumProperty<T> property = variantProperty();
        String key = property.getName();
        if (!tag.contains(key)) {
            return state;
        }

        Optional<T> value = property.getValue(tag.getString(key));
        return value.isPresent() ? state.setValue(property, value.get()) : state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Called from the Block constructor: variantProperty() must not touch instance fields.
        EnumProperty<T> property = variantProperty();
        if (property == null) {
            throw new IllegalStateException(getClass().getName()
                    + " returned a null blockstate property from variantProperty(); it must be a static final field");
        }
        builder.add(property);
    }
}
