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
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.util;

import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The component-to-comparator map: how much a machine component holds, expressed on vanilla's 0..15 scale.
 *
 * <h2>Upstream's file, and the three bodies that are the specification (rule A)</h2>
 *
 * <p>This is a port of {@code hellfirepvp.modularmachinery.common.util.RedstoneHelper} (upstream U26-U45). Upstream
 * had a single static entry point dispatching on the tile type, and each of the four blocks that wanted a
 * comparator output called it from its own override:</p>
 *
 * <pre>
 *   U M/block/BlockBus.java:136-143            hasComparatorInputOverride / getComparatorInputOverride
 *   U M/block/BlockEnergyHatch.java:105-110    the same pair
 *   U M/block/BlockFluidHatch.java:138-143     the same pair
 *   U M/block/BlockController.java:259-265      its own body, not this helper
 * </pre>
 *
 * <p><b>The arithmetic is upstream's, character for character, and it is three different formulas</b> — which is
 * exactly why the port could not have one:</p>
 *
 * <ol>
 *   <li><b>items</b> — not a fraction of 15 at all. Upstream calls
 *       {@code ItemHandlerHelper.calcRedstoneFromInventory}, whose published body is
 *       {@code floor(sum(count / min(slotLimit, stackMax)) / slots * 14) + (anythingPresent ? 1 : 0)}: the 14 leaves
 *       room for the "+1", so an inventory with a single item still reads <b>1</b> rather than 0, and a
 *       6-slot bus with one full slot reads <b>3</b>, not the 2 that {@code round(15 * 1/6)} gives.</li>
 *   <li><b>fluids</b> — {@code clamp(round(15F * (cur / cap)), 0, 15)}, float math, ROUND not floor: half a tank is
 *       8, not 7.</li>
 *   <li><b>energy</b> — the same expression as fluids, with the division done in {@code double} and then narrowed
 *       to {@code float} before the multiply, which is upstream's own cast chain.</li>
 * </ol>
 *
 * <p>The controller's formula is NOT here: it is not a function of an amount at all
 * ({@code isWorking ? 15 : machineFound ? 1 : 0}) and upstream kept it in the block. The port keeps it in
 * {@link hellfirepvp.modularmachinery.common.block.BlockMachineComponent#getAnalogOutputSignal}, which is the
 * landing site the audit named for the whole family — see that method for the deviation record.</p>
 *
 * <h2>What 0 means</h2>
 *
 * <p><b>0 is not "no comparator"; it is "this component holds nothing quantifiable".</b> A component that is not
 * one of the three families above — a casing, an upgrade bus, a parallel controller, a smart interface — has no
 * amount to report and answers 0, exactly as upstream's {@code return 0} at its last line does. A null block
 * entity answers 0 for the same reason. <b>And 0 is a legitimate reading for a bus/hatch that is genuinely
 * empty</b>, so a comparator next to a machine component cannot distinguish "empty" from "no opinion" — upstream's
 * contract, kept rather than tidied.</p>
 */
public final class RedstoneHelper {

    private RedstoneHelper() {
    }

    /**
     * @param sync the component's block entity, or {@code null} when the position holds none
     * @return the strength a comparator should read from this component, on vanilla's 0..15 scale
     */
    public static int getRedstoneLevel(@Nullable BlockEntity sync) {
        if (sync == null) {
            return 0;
        }
        if (sync instanceof TileItemBus bus) {
            // Forge's own helper, read from its bytecode rather than assumed
            // (forge-1.20.1-47.3.0 mapped jar, ItemHandlerHelper.calcRedstoneFromInventory):
            // floor(sum / slots * 14) + (anySlotNonEmpty ? 1 : 0), with slotLimit clamped by the stack's own max.
            return ItemHandlerHelper.calcRedstoneFromInventory(bus.getInventory());
        } else if (sync instanceof TileFluidTank tank) {
            float cap = tank.getTank().getCapacity();
            float cur = tank.getTank().getFluidAmount();
            return Mth.clamp(Math.round(15F * (cur / cap)), 0, 15);
        } else if (sync instanceof TileEnergyHatch hatch) {
            double cap = hatch.getCapacity();
            double cur = hatch.getStoredEnergy();
            return Mth.clamp(Math.round(15F * (float) (cur / cap)), 0, 15);
        }
        return 0;
    }
}
