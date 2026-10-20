/* SPDX-License-Identifier: GPL-3.0-only
 * HellFirePvP / Modular Machinery, 2019. Forge 1.20.1 derivative, modified 2026-10.
 * Upstream: https://github.com/HellFirePvP/ModularMachinery
 */
package hellfirepvp.modularmachinery.common.block;

/**
 * Stable source IDs and bundled assets for providers whose optional runtime integration is absent.
 * Botania mana providers subclass this block and install real tiles when Botania is loaded.
 * Blood Magic and Nature's Aura providers also install real tiles with their respective mods.
 * The Astral Sorcery overlays use bundled fallback textures rather than references to missing assets.
 */
public class BlockModularMagicPlaceholder extends BlockMachineComponent {

    private final String providerName;

    public BlockModularMagicPlaceholder(Properties properties, String providerName) {
        super(properties);
        this.providerName = providerName;
    }

    /**
     * @return the upstream provider name this block stands in for, e.g. {@code "manaproviderinput"}.
     *         Retained so tooltips and structure diagnostics can say what is missing and why.
     */
    public String getProviderName() {
        return providerName;
    }
}
