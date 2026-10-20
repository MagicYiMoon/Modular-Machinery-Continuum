/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.event.machine;

import hellfirepvp.modularmachinery.common.tiles.TileController;

/** Client-side GeckoLib model selection hook from the source MMEvents API. */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.ControllerModelGetEvent")
public final class ControllerModelGetEvent extends MachineEvent {
    private String modelName;

    public ControllerModelGetEvent(TileController controller, String modelName) {
        super(controller);
        this.modelName = modelName == null ? "" : modelName;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("modelName")
    public String getModelName() { return modelName; }

    @org.openzen.zencode.java.ZenCodeType.Setter("modelName")
    public void setModelName(String modelName) { this.modelName = modelName == null ? "" : modelName; }
}
