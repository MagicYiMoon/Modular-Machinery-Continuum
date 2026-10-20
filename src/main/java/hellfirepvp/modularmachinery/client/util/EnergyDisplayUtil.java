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
package hellfirepvp.modularmachinery.client.util;

import hellfirepvp.modularmachinery.common.data.Config;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The global energy-unit switch: which unit every energy figure in this mod is displayed in (audit row 41).
 *
 * <h2>Where the choice is STORED (read first, before anything was written — audit row 41)</h2>
 *
 * <p><b>It is a CONFIG VALUE, in a file the server owner edits; it is not a client setting and not a per-player
 * choice.</b> Upstream's whole storage mechanism is {@code EnergyDisplayUtil.loadFromConfig} (U30-U36), whose body
 * is four reads out of one section:</p>
 *
 * <pre>
 *   U M/client/util/EnergyDisplayUtil.java
 *     U31  getBoolean("FE_RF_Tooltip",    "display.energy", true, "...")
 *     U32  getBoolean("IC2_EU_Tooltip",   "display.energy", true, "...")
 *     U33  getBoolean("GT_EU_Tooltip",    "display.energy", true, "...")
 *     U35  getString ("Display_Energy_Type", "display.energy", "FE", "...")
 *
 *   and the call site: U M/data/Config.java:68 — Config.load(), immediately after the four tier families.
 * </pre>
 *
 * <p>The section is {@code display.energy}; the type key's own comment lists the four values a player may write
 * ("Available options: 'FE', 'RF', 'IC2_EU', 'GT_EU' - Default: FE"), and the unit reaches the player through
 * {@code EnergyType.getUnlocalizedFormat()} — {@code "tooltip.energy.type." + name().toLowerCase()} — which both
 * language files already ship for all four constants.</p>
 *
 * <h2>What the port changed, and why it had to (rule A0, both parts recorded)</h2>
 *
 * <ol>
 *   <li><b>Public static mutable FIELDS became accessors.</b> Upstream declares
 *       {@code public static boolean displayFETooltip} and assigns the three of them once in
 *       {@code loadFromConfig}, which is a 1.12.2 {@code Configuration} read at startup. 1.20.1 has no
 *       {@code Configuration}; the port's mechanism (see {@link Config}, its own javadoc) declares the values in a
 *       {@code ForgeConfigSpec} and resolves them <b>at the point of use</b>. So the three booleans and the type
 *       are accessors reading {@code ConfigValue#get()}, and the observable result is upstream's: the unit shown
 *       is the unit in the config file. The one difference, stated rather than hidden: a value changed and reloaded
 *       takes effect without a restart, which is a property of where the read happens, not a feature added.</li>
 *   <li><b>{@code Locale.ROOT} in the format key.</b> Upstream is {@code name().toLowerCase()} — the JVM's default
 *       locale. Under a Turkish default locale {@code "IC2_EU".toLowerCase()} is {@code "ıc2_eu"} (dotless ı), a key
 *       neither language file has, and the tooltip would print the raw key. {@code Locale.ROOT} is the same string
 *       upstream produces on every locale that works, and the correct one on the locale where it does not. The port
 *       already applies this rule to its own config keys (Config#key).</li>
 * </ol>
 *
 * <h2>Scope: the FE/RF half is implemented, the IC2/GT VALUE sections are not</h2>
 *
 * <p><b>FE and RF are complete</b> and depend on nothing: both have multiplier 1, and the unit string is a
 * language-file lookup. Selecting either changes the unit shown, with the same numbers — which is exactly what
 * upstream does for those two.</p>
 *
 * <p><b>The {@code IC2_EU} and {@code GT_EU} constants exist with upstream's own multiplier (0.25) and format key,
 * so the switch's own arithmetic and the four documented config values are upstream's. What is NOT implemented is
 * the pair of tooltip SECTIONS upstream adds for those two units</b> — {@code BlockEnergyInputHatch} U59-U66 and
 * U67-U70 read {@code Mods.IC2.isPresent()} / {@code Mods.GREGTECH.isPresent()} and then emit IC2's voltage and
 * transfer or GregTech's voltage, amperage and storage. Those are values read off the OTHER mod's energy hatch
 * (upstream's {@code EnergyHatchData.getGTEnergyTransferVoltage}, {@code getGtAmperage},
 * {@code getIC2EnergyTransmission}), so they are an integration with two mods this port does not integrate with
 * here — recorded as C in the audit for that reason, and not faked with placeholder numbers.</p>
 */
public final class EnergyDisplayUtil {

    private EnergyDisplayUtil() {
    }

    /** @return upstream's {@code displayFETooltip}: whether the FE/RF storage and transfer lines are shown */
    public static boolean displayFETooltip() {
        return Config.displayEnergyFETooltip();
    }

    /** @return upstream's {@code displayIC2EUTooltip}; read, but nothing emits its section yet — see the class javadoc */
    public static boolean displayIC2EUTooltip() {
        return Config.displayEnergyIC2Tooltip();
    }

    /** @return upstream's {@code displayGTEUTooltip}; read, but nothing emits its section yet — see the class javadoc */
    public static boolean displayGTEUTooltip() {
        return Config.displayEnergyGTTooltip();
    }

    /** @return upstream's {@code type}: the unit every energy figure in this mod is displayed in */
    public static EnergyType type() {
        return EnergyType.getType(Config.energyDisplayType());
    }

    /**
     * The unit the switch can be set to. <b>Upstream's four constants with upstream's own multipliers</b> — the
     * multipliers are not decoration: they are what makes {@link #formatEnergyForDisplay} convert a stored FE figure
     * into the selected unit, and they are the same 1 and 0.25 upstream declares.
     */
    public enum EnergyType {

        FE(1),
        RF(1),
        IC2_EU(0.25F),
        GT_EU(0.25F);

        private final double multiplier;
        private final String unlocalizedFormat;

        EnergyType(double multiplier) {
            this.multiplier = multiplier;
            // Upstream's expression, with the locale pinned -- see the class javadoc, deviation 2.
            this.unlocalizedFormat = "tooltip.energy.type." + name().toLowerCase(Locale.ROOT);
        }

        /**
         * Upstream's own parser, body for body: an unknown or malformed config value is <b>FE</b>, silently.
         *
         * <p><b>The silence is upstream's and is kept.</b> A config typo costing the player their FE display is a
         * worse outcome than falling back, and the fallback is the value the key's own comment documents as the
         * default. Nothing is logged because upstream logs nothing here.</p>
         */
        public static EnergyType getType(String configValue) {
            EnergyType type = FE;
            try {
                type = EnergyType.valueOf(configValue);
            } catch (Exception ignored) {
            }
            return type;
        }

        /** @return the given energy figure expressed in this unit, truncated as upstream's {@code lfloor} does */
        public long formatEnergyForDisplay(long energy) {
            return Mth.lfloor(energy * multiplier);
        }

        /** @return the language key of this unit's name, e.g. {@code tooltip.energy.type.fe} */
        public String getUnlocalizedFormat() {
            return unlocalizedFormat;
        }
    }
}
