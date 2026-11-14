/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.TileEFabricatorPatternBus;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class EFabricatorPatternChecks {
    private EFabricatorPatternChecks() { }
    public static void run(TileEFabricatorPatternBus bus) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)});
        var handler = bus.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(AssertionError::new);
        if (!handler.isItemValid(71, pattern) || !handler.insertItem(71, pattern, true).isEmpty() || !handler.getStackInSlot(71).isEmpty()) throw new AssertionError("EF simulated pattern insertion");
        if (!handler.insertItem(71, pattern.copy(), false).isEmpty() || handler.getStackInSlot(71).isEmpty()) throw new AssertionError("EF encoded pattern insertion");
        var display = handler.getStackInSlot(71); display.setTag(null);
        var original = bus.patternData(); bus.load(bus.saveWithoutMetadata());
        if (!bus.readable() || !bus.patternData().equals(original) || !handler.getStackInSlot(71).hasTag()
                || handler.extractItem(71, 1, true).isEmpty() || handler.getStackInSlot(71).isEmpty()) throw new AssertionError("EF physical pattern data changed");
        if (handler.extractItem(71, 1, false).isEmpty() || !handler.getStackInSlot(71).isEmpty()) throw new AssertionError("EF pattern extraction");
        handler.insertItem(71, pattern.copy(), false);
        Modularmachinery.LOG.info("[MMC][ef-patterns-ae] PASS: actual AE encoded pattern, slot 71, simulation/real insertion/extraction, deep-copy and save/reload; stored processing pattern is not advertised as an EF recipe");
    }
}
