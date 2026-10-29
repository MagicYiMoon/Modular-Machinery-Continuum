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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.event.machine;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
















 
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.ControllerGUIRenderEvent")
public class ControllerGUIRenderEvent extends MachineEvent {

    private final List<String> extraInfo = new ArrayList<>();

    public ControllerGUIRenderEvent(TileController controller) {
        super(controller);
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("extraInfo")
    public String[] getExtraInfo() {
        return extraInfo.toArray(new String[0]);
    }

    @org.openzen.zencode.java.ZenCodeType.Method
    public void setExtraInfo(String... info) {
        this.extraInfo.addAll(Arrays.asList(info));
    }
}

