/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetMonitor;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

final class HyperNetMonitorButton {
    private HyperNetMonitorButton(){}
    static Button create(AbstractContainerMenu menu,int x,int y){
        return Button.builder(Component.translatable("gui.modularmachinery.hypernet.button_short"),b->{
            var mc=Minecraft.getInstance();if(mc.gameMode!=null)mc.gameMode.handleInventoryButtonClick(menu.containerId,MenuHyperNetMonitor.OPEN_BUTTON);
        }).bounds(x,y,18,18).tooltip(Tooltip.create(Component.translatable("gui.modularmachinery.hypernet.title"))).build();
    }
    static void update(Button button,BlockPos position){
        var world=Minecraft.getInstance().level;var loader=MachineReloadListener.getLoader();
        var machine=world!=null&&world.getBlockEntity(position) instanceof TileController c&&loader!=null?loader.findByName(c.getMachineName()):null;
        button.visible=button.active=machine!=null&&machine.getHyperNetDefinition()!=null;
    }
}
