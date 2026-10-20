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
 * LICENSE file at the top of the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.block.prop;

/**
 * A component tier whose tooltip line carries ONE NUMBER.
 *
 * <h2>Why an interface, and why on the enum</h2>
 *
 * <p>Upstream renders the tier's own figure under the item's name from a per FAMILY {@code addInformation}
 * override, each of which reads the tier out of block metadata and then reads that family's own getter:
 *
 * <pre>
 *   BlockBus             L74  ItemBusSize.values()[meta]         -> size.getSlotCount()
 *   BlockFluidHatch      L59  FluidHatchSize.values()[meta]      -> size.getSize()
 *   BlockParallelController L49  ParallelControllerData.values()[meta] -> data.getMaxParallelism()
 *   BlockUpgradeBus      L52  UpgradeBusData.values()[meta]      -> data.getMaxUpgradeSlot()
 * </pre>
 *
 * <p>On 1.20.1 the tier is a BLOCKSTATE and the tooltip is drawn from an ITEM, so the figure has to be
 * reached from the stack. Implementing this on the ENUM is what keeps rule D — the tip's argument must
 * come from the tier's own value and not from a lookup table: there is no table to drift, the enum answers
 * with the same field it answers its own gameplay getters with, and a new tier added to {@code values()}
 * cannot be missing from it.</p>
 *
 * <h2>Families that do NOT implement this, and why</h2>
 *
 * <p><b>{@code EnergyHatchData}</b> — upstream does give the energy hatches tooltip lines, but each one is
 * a volume in FE that upstream reads from {@code net.minecraftforge.common.config.Configuration}, not from
 * the enum (see that enum's own javadoc: this port's ladder is an INVENTED x4 series). The tip keys that
 * upstream's lines use ({@code tooltip.energyhatch.storage} / {@code .in.accept} / {@code .out.transfer})
 * also ship in this port's language files but are read by nothing. Reaching them means modelling the config
 * mechanism, which is a separate unit — so an energy hatch emits NO tip line rather than one carrying an
 * unverified figure. Left open and named.</p>
 *
 * <p><b>{@code CasingType}</b> — upstream's {@code BlockCasing} has no {@code addInformation} at all and
 * the six casings differ by texture only. Nothing to report.</p>
 *
 * <p><b>{@code SmartInterfaceType}</b> — upstream's {@code BlockSmartInterface} has no
 * {@code addInformation} either; its variant is a data type, not a capacity.</p>
 */
public interface TierTipValue {

    /**
     * The single number this tier's tooltip line reports: the slot count of a bus, the capacity in mB of a
     * fluid hatch, the parallelism of a controller, the upgrade slots of an upgrade bus.
     *
     * <p>It exists so the tooltip builder and the self-test can both ask the ENUM rather than a parallel
     * table, and so both necessarily read the same field the tier's gameplay getters read.</p>
     */
    int getTipValue();
}
