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
package hellfirepvp.modularmachinery.common.registry;

import hellfirepvp.modularmachinery.common.block.BlockModularMagicPlaceholder;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for the ModularMagic provider blocks and optional integrations.
 *
 * <p><b>Namespace note.</b> These blocks keep the upstream {@code modularmagic} namespace, not
 * {@code modularmachinery}. Upstream shipped them in a second asset namespace with ids such as
 * {@code modularmagic:blockmanaproviderinput}, and the tooltip/lang keys and the blockstate/model files
 * are all keyed on that namespace. Registering them as {@code modularmachinery:...} would break every
 * addon pack and structure JSON that names them, so a {@code DeferredRegister} is created against the
 * same Forge registry but with the {@code modularmagic} modid — the namespace, not the owning mod,
 * is what {@code DeferredRegister}'s modid argument actually controls.
 *
 * <p>Botania mana, Blood Magic will/life-essence and Nature's Aura providers have working optional integrations.
 * Other providers keep their IDs and assets while their modern API integrations are pending.

 */
public final class RegistryModularMagicBlocks {

    private static final String NAMESPACE = "modularmagic";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, NAMESPACE);

    private static RegistryObject<Block> placeholder(String name) {
        return BLOCKS.register(name, () -> new BlockModularMagicPlaceholder(
                RegistryBlocks.componentProperties(), name));
    }

    // --- Thaumcraft 6 ---
    public static final RegistryObject<Block> blockAspectProviderInput = placeholder("blockaspectproviderinput");
    public static final RegistryObject<Block> blockAspectProviderOutput = placeholder("blockaspectprovideroutput");

    // --- Astral Sorcery ---
    public static final RegistryObject<Block> blockStarlightProviderInput = BLOCKS.register("blockstarlightproviderinput", () -> new hellfirepvp.modularmachinery.common.block.BlockAstralProvider(RegistryBlocks.componentProperties(), 0));
    public static final RegistryObject<Block> blockStarlightProviderOutput = BLOCKS.register("blockstarlightprovideroutput", () -> new hellfirepvp.modularmachinery.common.block.BlockAstralProvider(RegistryBlocks.componentProperties(), 1));
    public static final RegistryObject<Block> blockConstellationProvider = BLOCKS.register("blockconstellationprovider", () -> new hellfirepvp.modularmachinery.common.block.BlockAstralProvider(RegistryBlocks.componentProperties(), 2));

    // --- Nature's Aura ---
    public static final RegistryObject<Block> blockAuraProviderInput = BLOCKS.register("blockauraproviderinput", () -> new hellfirepvp.modularmachinery.common.block.BlockAuraProvider(RegistryBlocks.componentProperties(), true));
    public static final RegistryObject<Block> blockAuraProviderOutput = BLOCKS.register("blockauraprovideroutput", () -> new hellfirepvp.modularmachinery.common.block.BlockAuraProvider(RegistryBlocks.componentProperties(), false));

    // --- Blood Magic ---
    public static final RegistryObject<Block> blockWillProviderInput = blood("blockwillproviderinput", false, true);
    public static final RegistryObject<Block> blockWillProviderOutput = blood("blockwillprovideroutput", false, false);
    public static final RegistryObject<Block> blockLifeEssenceProviderInput = blood("blocklifeessenceproviderinput", true, true);
    public static final RegistryObject<Block> blockLifeEssenceProviderOutput = blood("blocklifeessenceprovideroutput", true, false);

    private static RegistryObject<Block> blood(String id, boolean life, boolean input) {
        return BLOCKS.register(id, () -> new hellfirepvp.modularmachinery.common.block.BlockBloodMagicProvider(RegistryBlocks.componentProperties(), life, input));
    }

    // --- Botania ---
    public static final RegistryObject<Block> blockManaProviderInput = BLOCKS.register("blockmanaproviderinput", () -> new hellfirepvp.modularmachinery.common.block.BlockManaProvider(RegistryBlocks.componentProperties(), true));
    public static final RegistryObject<Block> blockManaProviderOutput = BLOCKS.register("blockmanaprovideroutput", () -> new hellfirepvp.modularmachinery.common.block.BlockManaProvider(RegistryBlocks.componentProperties(), false));

    // --- Other (grid / impetus / rainbow; upstream targets several further mods) ---
    public static final RegistryObject<Block> blockGridProviderInput = placeholder("blockgridproviderinput");
    public static final RegistryObject<Block> blockGridProviderOutput = placeholder("blockgridprovideroutput");
    public static final RegistryObject<Block> blockImpetusProviderInput = placeholder("blockimpetusproviderinput");
    public static final RegistryObject<Block> blockImpetusProviderOutput = placeholder("blockimpetusprovideroutput");
    public static final RegistryObject<Block> blockRainbowProvider = BLOCKS.register("blockrainbowprovider", () -> new hellfirepvp.modularmachinery.common.block.BlockRainbowProvider(RegistryBlocks.componentProperties()));

    private RegistryModularMagicBlocks() {
    }
}
