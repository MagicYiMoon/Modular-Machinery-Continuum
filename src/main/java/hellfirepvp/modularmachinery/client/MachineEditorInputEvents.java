/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.screen.ScreenMachineEditor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The authoring editor owns its keys, including keys that other mods use to open screens. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, value = Dist.CLIENT)
public final class MachineEditorInputEvents {
    private MachineEditorInputEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof ScreenMachineEditor editor) {
            // Dispatch once ourselves: cancellation skips vanilla dispatch and the Post event,
            // and prevents normal-priority recipe/map/menu shortcuts from taking over the editor.
            event.setCanceled(true);
            editor.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (event.getScreen() instanceof ScreenMachineEditor editor) {
            event.setCanceled(true);
            editor.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        }
    }
}
