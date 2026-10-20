/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.block;

import hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * {@code modularmachinery:blockmefluidinputbus} -- the block form of {@code MEFluidInputBus}, and the way into its configuration
 * GUI.
 *
 * <p>Upstream's class is 59 lines: {@code onBlockActivated} (U28, the GUI), {@code createTileEntity} (U48, the tile) and
 * {@code addInformation} (U54, a tooltip).</p>
 *
 * <h2>What was ported this round, and what is still absent (rule A0)</h2>
 *
 * <pre>
 *   U28-U44  onBlockActivated -> {@link #use} below. Upstream's body has TWO steps: a memory-card settings
 *            transfer, then openGui. The memory-card branch now uses {@code SettingsTransfer}, just like the item
 *            buses, before reaching the GUI. The GUI remains the normal path to {@code MEFluidInputBus.setConfiguredFluid}.
 *   U46-U50  createTileEntity   -> ported earlier, through {@link MEBusTiles}.
 *   U52-U57  addInformation     -> STILL NOT PORTED, and still recorded: a cosmetic tooltip
 *            ({@code tooltip.groupinput.block}) that says the bus can be part of a group input.
 * </pre>
 *
 * <p><b>Why the GUI needed a packet-free path here.</b> The screen is opened on the SERVER and the client builds its half from
 * the menu type, so no client-to-server message is involved in opening it — the only message the feature needs is the
 * configuration write itself, which lives in {@code PktMEFluidInputBusConfig}. Reaching the configuration through a right-click
 * therefore costs exactly one {@code openScreen} call, and nothing has to be synthesised on the client to make it reachable.</p>
 *
 * <p>A held fluid container is deliberately NOT consumed by this method: the ME fluid bus exposes no {@code IFluidHandler}
 * capability (verified by a project-wide search for {@code FLUID_HANDLER_CAPABILITY}, which returns no hit in the ME tiles), so
 * {@code FluidUtil.interactWithFluidHandler} would find nothing to act on and a player holding a bucket would get a silently
 * ignored right-click. The bucket is what CONFIGURES a tank here, and it does that inside the screen.</p>
 */
public class BlockMEFluidInputBus extends BlockMEFluidBus {

    public BlockMEFluidInputBus(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity createTile(BlockPos pos, BlockState state) {
        return MEBusTiles.createFluid(true, pos, state);
    }

    /**
     * Opens the bus's configuration GUI — upstream U40's {@code openGui}, in 1.20.1's shape (PLAN 690).
     *
     * <p><b>This body names nothing but {@link MEBusTiles} and vanilla types, and that is required rather than tidy.</b>
     * It used to name {@code MEFluidInputBus} INLINE, in the {@code instanceof} that finds the bus — and a type named in
     * the method body of an unconditionally-registered block is resolved when the class LINKS (§413). That is the same
     * shape as the {@code NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost} that a right-click on the
     * item bus raised (measured; §690), one block over. The finding here is subtler and was measured rather than
     * assumed: the inline {@code instanceof} did NOT throw, because {@code instanceof} answers false for a NULL operand
     * without resolving the class and without AE2 this position never holds a block entity — so it was a LATENT
     * dependency, one non-null operand away. It is gone because the shape is the defect, not because it detonated.</p>
     *
     * <p><b>And the guard is the second half of the same miss:</b> the AE2 check in
     * {@code BlockMEMachineComponent.newBlockEntity} protects TILE CREATION, while {@code use} is a second
     * unconditional entry point. With AE2 absent the block is now INERT — {@code PASS}, nothing consumed — which is
     * what {@code BlockMEPatternProvider.use} returns in the same situation (§483) and what the item bus returns after
     * §690's fix. Before this round the click was swallowed with {@code SUCCESS} and did nothing.</p>
     *
     * <p>The open step itself lives in {@link MEBusTiles} now, beside {@code openItemInputBusMenu}, so the whole family
     * has ONE convention for a right-click that opens a menu instead of two (§686 recorded the asymmetry and called
     * this side the weaker one).</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            return InteractionResult.PASS;
        }
        if (MEBusTiles.handleItemBusSettingsTransfer(level, pos, player, hand,
                state.getBlock().getDescriptionId())) {
            return InteractionResult.SUCCESS;
        }
        MEBusTiles.openFluidInputBusMenu(level, pos, player);
        return InteractionResult.SUCCESS;
    }
}
