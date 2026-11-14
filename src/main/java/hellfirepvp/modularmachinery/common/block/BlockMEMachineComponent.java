/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base for every Applied Energistics 2 block in this mod, and the single place the AE2 guard lives (PLAN section 429).
 *
 * <h2>Upstream's layer, restored — and this time the layer has content</h2>
 *
 * <p>Upstream has {@code BlockMEMachineComponent} (77 lines) between {@code BlockMachineComponent} and the ME blocks. §427.3 recorded
 * the port's absence of it as an open rule-A0 question: **a deviation needs evidence, and there was none — the port simply never had the
 * layer.**</p>
 *
 * <p><b>Reading its body settled the question in favour of writing it, for a reason that is about THIS port rather than about
 * upstream.</b> Most of upstream's 77 lines are 1.12.2 plumbing with no 1.20.1 counterpart:</p>
 *
 * <pre>
 *   U29-34  super(Material.IRON); setHardness; setResistance; setSoundType; setHarvestLevel; setCreativeTab
 *           -- in 1.20.1 every one of those is BlockBehaviour.Properties, which the CALLER supplies
 *   U38     onBlockActivated(...)      the 1.12.2 name for use(...)
 *   U42     createTileEntity(...)      the 1.12.2 name for newBlockEntity(...)
 *   U46-48  getRenderType -> MODEL     MODEL is already the 1.20.1 default, so the override is a no-op
 *   U50-55  getRenderLayer -> CUTOUT   removed in 1.20.1; render layers moved to the model/blockstate layer
 *   U57-76  handleSettingsTransfer     handled by {@link hellfirepvp.modularmachinery.common.tiles.me.MEBusTiles}
 *                                      for the input/output/fluid buses
 * </pre>
 *
 * <p>**So a faithful copy would be an empty class — except for one thing, and that thing is why this file exists:**</p>
 *
 * <h2>The guard, which was duplicated and must not be</h2>
 *
 * <p>Before this class, {@code BlockMEItemBus} AND {@code BlockMEFluidBus} EACH carried:</p>
 *
 * <pre>
 *   if (!ModList.get().isLoaded("ae2")) return null;
 * </pre>
 *
 * <p>**Two copies of one decision, and §§404 and §411 EACH cost a round because that decision was got wrong once.** **The guard is the
 * most load-bearing line in the ME block family: without it the server dies at REGISTRY time with
 * {@code NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost}, and with it the mod loads cleanly and the block simply has no
 * block entity.**</p>
 *
 * <p>**That duplicated guard is the content of this class, and it is also exactly where upstream put its layer.** A0 and rule B agree
 * here rather than conflicting: the shape upstream has is the shape that removes the duplication.</p>
 *
 * <h2>Why the subclass hook is abstract and the guard is final</h2>
 *
 * <p>A subclass says WHICH tile to build; it may not say WHETHER to guard. **Making {@code newBlockEntity} final is what turns "remember
 * the check" into something a compiler enforces** — a subclass that forgets it does not compile, rather than shipping and killing a server
 * without AE2.</p>
 */
public abstract class BlockMEMachineComponent extends BlockMachineComponent {

    protected BlockMEMachineComponent(Properties properties) {
        super(properties);
    }

    /**
     * Builds this block's grid-connected tile.
     *
     * <p><b>Called only when AE2 is present</b> — see {@link #newBlockEntity}. **And the implementation must reach the tile through
     * {@code MEBusTiles} rather than naming it inline**, for the class-load reason of §413: the JVM's verifier resolves the types a
     * method body names when the class is LINKED, so an inline reference is needed at load time however lazy the call site looks.</p>
     */
    @Nullable
    protected abstract BlockEntity createTile(BlockPos pos, BlockState state);

    /**
     * The single guarded door into the ME tile family.
     *
     * <p><b>FINAL on purpose:</b> this is the one place the AE2 presence check may live, and a subclass override is the way it would
     * otherwise be forgotten.</p>
     */
    @Nullable
    @Override
    public final BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            // The block places, has no block entity, and a structure JSON naming it still resolves -- the same state the six
            // remaining ME placeholders are permanently in.
            return null;
        }
        return createTile(pos, state);
    }
}
