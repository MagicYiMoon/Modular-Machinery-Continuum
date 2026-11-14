/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ECalculatorCell. */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class ItemECalculatorMemory extends Item {
    private final ECalculatorSpecs.Memory specification;
    public ItemECalculatorMemory(ECalculatorSpecs.Memory spec) { super(new Properties().stacksTo(1)); specification = spec; }
    public ECalculatorSpecs.Memory specification() { return specification; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ec.memory", specification.bytes()));
        tooltip.add(Component.translatable("tooltip.ec.memory_tier", specification.minimumTier()));
        tooltip.add(Component.translatable("tooltip.ec.memory_gesture"));
        tooltip.add(Component.translatable("tooltip.ec.development"));
    }
}
