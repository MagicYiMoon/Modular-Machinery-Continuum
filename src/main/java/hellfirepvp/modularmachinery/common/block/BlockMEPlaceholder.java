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

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Stands in for one of the Applied Energistics 2 ME bus blocks until the grid unit is written.
 *
 * <h2>Why a placeholder rather than nothing</h2>
 *
 * <p>The eight {@code blockme*} blocks were shipped by the KasumiNova module
 * ({@code github.kasuminova.mmce.common.block.appeng}), and their <b>assets are already present</b> in
 * this port's converted resources — blockstates, block models and item models. In the upstream mod the
 * blocks and their assets were written together; here the assets arrived with the asset conversion and
 * the blocks did not, so the resources currently reference registry ids that <b>do not exist</b>.
 *
 * <p>Registering the blocks:
 * <ul>
 *   <li>makes those assets resolve instead of being dead weight;</li>
 *   <li>makes any pack or structure JSON naming {@code modularmachinery:blockmeiteminputbus} resolvable,
 *       which is the same reason the {@code modularmagic} placeholders exist;</li>
 *   <li>keeps the registry ids <b>stable whether or not AE2 is installed</b>, so a world saved with the
 *       grid unit present still loads if AE2 is later removed — a block that vanishes from the registry
 *       turns into air and takes the machine's contents with it.</li>
 * </ul>
 *
 * <p>Like {@link BlockModularMagicPlaceholder}, this is a real registered
 * {@link BlockMachineComponent}: it places, renders, has a display name, and can stand in a structure
 * pattern. It exposes <b>no capability and no AE2 grid connection</b>, so a machine built with one will
 * assemble and simply never have its requirements satisfied — a far better failure mode than a crash or
 * an unresolvable id. It is deliberately <b>not</b> a stub that throws.
 *
 * <p>When the grid unit lands these are replaced by real {@code IGridConnectedBlockEntity} blocks. The
 * registry ids must not change when that happens, so that existing worlds keep working.
 */
public class BlockMEPlaceholder extends BlockMachineComponent {

    private final String upstreamName;

    public BlockMEPlaceholder(Properties properties, String upstreamName) {
        super(properties);
        this.upstreamName = upstreamName;
    }

    /**
     * @return the upstream KasumiNova block name this stands in for, e.g. {@code "blockmeiteminputbus"}.
     *         Kept so a tooltip and the structure diagnostics can name what is missing.
     */
    public String getUpstreamName() {
        return upstreamName;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Says plainly that the block is inert. Without this an ME bus would look installed and simply
     * never connect to a network, and the player would have no way to tell a missing implementation from
     * a misconfigured one.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.modularmachinery.me_placeholder"));
    }
}
