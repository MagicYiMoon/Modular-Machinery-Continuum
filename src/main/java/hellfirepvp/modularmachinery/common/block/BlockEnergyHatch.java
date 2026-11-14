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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Base for the energy input/output hatches.
 *
 * <p>Variant space is {@link EnergyHatchData} ({@code size=tiny} … {@code size=ultimate}), matching
 * the shipped {@code blockenergyinputhatch.json} / {@code blockenergyoutputhatch.json}.
 *
 * <p>Note the property name {@code size} is shared with {@link BlockFluidHatch}, but the enum types
 * differ, so a block entity must resolve its variant against a specific block's property rather than
 * by searching the state for a property called {@code "size"}.
 *
 * <p>{@link #SIZE} is {@code static final} because vanilla calls {@code createBlockStateDefinition}
 * from inside the {@code Block} constructor — see {@link BlockVariantComponent}.
 */
public abstract class BlockEnergyHatch extends BlockVariantComponent<EnergyHatchData> {

    public static final EnumProperty<EnergyHatchData> SIZE = EnergyHatchData.createProperty();

    protected BlockEnergyHatch(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, EnergyHatchData.NORMAL));
    }

    @Override
    protected EnumProperty<EnergyHatchData> variantProperty() {
        return SIZE;
    }

    /**
     * UPSTREAM'S TWO ENERGY LINES, UNDER THE NAME (audit row 41).
     *
     * <pre>
     *   U M/block/BlockEnergyInputHatch.java:52-58   (addInformation)
     *       if (EnergyDisplayUtil.displayFETooltip) {
     *           add(GRAY + I18n.format("tooltip.energyhatch.storage",     size.maxEnergy));
     *           add(GRAY + I18n.format("tooltip.energyhatch.in.accept",   size.transferLimit));
     *       }
     *   U M/block/BlockEnergyOutputHatch.java:52-58  the same, with "tooltip.energyhatch.out.transfer"
     * </pre>
     *
     * <p><b>The guard is upstream's FE/RF switch and it is the whole of the FE arm:</b> with
     * {@code display.energy.FE_RF_Tooltip} false, an energy hatch reports no energy figures — which is a real,
     * observable consequence of the config and not a colour change. Both units have multiplier 1, so nothing is
     * converted here; the unit SWITCH's conversion and unit string are on the GUI readout, which is the other place
     * upstream reads {@code EnergyDisplayUtil.type} ({@code GuiContainerEnergyHatch} U57-U64).</p>
     *
     * <p><b>The numbers come from the TIER, through its own config-aware getters</b> — the same rule the other four
     * families follow (see {@link hellfirepvp.modularmachinery.common.block.prop.TierTipValue}), so a hatch whose
     * capacity a server owner edited reports the edited number rather than the jar's default. Upstream read
     * {@code size.maxEnergy} / {@code size.transferLimit}, the enum's own fields, because on 1.12.2 the config had
     * already overwritten them at startup.</p>
     *
     * <p><b>The IC2 and GT sections are NOT emitted, and that is the recorded C-class remainder:</b> upstream
     * guards them on {@code Mods.IC2.isPresent()} / {@code Mods.GREGTECH.isPresent()} and then reports values read
     * off the OTHER mod's hatch ({@code getIC2EnergyTransmission}, {@code getGTEnergyTransferVoltage},
     * {@code getGtAmperage}). The port integrates with neither, so the two sections would be fabricated numbers.
     * Their four keys ({@code tooltip.energyhatch.ic2.*}, {@code .gregtech.*}) therefore stay unread; the two
     * config switches are declared and readable rather than missing. See {@code EnergyDisplayUtil}'s javadoc.</p>
     *
     * <p><b>Deviation (rule A0):</b> upstream's line is {@code MiscUtils.formatDecimal(...)}; the port has no
     * decimal formatter and passes the long straight to {@code %s}. Every other tip line in the port passes its
     * number the same way, so this is one rule for five families rather than a special case for one.</p>
     */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> tipLines(BlockState state, String tierName) {
        java.util.Optional<EnergyHatchData> resolved = SIZE.getValue(tierName);
        if (resolved.isEmpty() || !resolved.get().equals(state.getValue(SIZE))) {
            return java.util.List.of();
        }
        // THE SWITCH, read at the point of use: with FE/RF off, the hatch has no energy lines to add -- upstream's
        // `if (EnergyDisplayUtil.displayFETooltip)` wrapping both lines.
        if (!hellfirepvp.modularmachinery.client.util.EnergyDisplayUtil.displayFETooltip()) {
            return java.util.List.of();
        }
        EnergyHatchData tier = resolved.get();
        return java.util.List.of(
                net.minecraft.network.chat.Component.translatable(
                        "tooltip.energyhatch.storage", tier.getMaxEnergy()),
                net.minecraft.network.chat.Component.translatable(
                        transferTipKey(), tier.getMaxTransfer()));
    }

    /**
     * @return the language key of this direction's transfer line
     *
     * <p>Abstract rather than defaulted, because the two directions have DIFFERENT keys in upstream
     * ({@code .in.accept} / {@code .out.transfer}) and a default would be one direction's sentence printed on the
     * other direction's hatch — the shape of a wrong answer that reads as a right one.</p>
     */
    protected abstract String transferTipKey();

    @Nullable
    @Override
    protected abstract BlockEntity createBlockEntity(EnergyHatchData variant, BlockPos pos, BlockState state);

    /**
     * Ticks the OUTPUT hatch so it can give its buffer to neighbours (PLAN section 383).
     *
     * <p><b>Only the output direction is installed, and that is the difference from {@code BlockBus}.</b> There both
     * directions move items -- one pulls and one pushes -- so that override dispatches twice. Here the input hatch has
     * nothing to do on a tick: energy reaches it through its capability, which is how the machine fills it. Installing a
     * ticker for it would spend a call per tick per hatch to run a method that returns immediately.
     *
     * <p>And note the hatch's own {@code step} is NOT throttled, unlike the item buses' random 5..60 tick walk: upstream
     * pushes every tick, capped by the storage's transfer limit. A power hatch delivering a fraction of its rated
     * throughput because it was given the buses' cadence is exactly the kind of error that looks like a balance problem.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type == hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities.ENERGY_OUTPUT_HATCH.get()) {
            return (tickLevel, pos, tickState, blockEntity) -> {
                if (blockEntity instanceof hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputHatch hatch
                        && tickLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    hatch.step(serverLevel, pos);
                }
            };
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // On the shared abstract parent, so both energy directions and every tier get the GUI from one
        // place -- the same choice made for BlockBus, and for the same reason: per-subclass duplication
        // is how one direction silently ends up without a screen.
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch hatch) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inventory, ignored) ->
                                    hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch
                                            .forHatch(windowId, inventory, hatch),
                            Component.translatable("gui.modularmachinery.energy_hatch")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}