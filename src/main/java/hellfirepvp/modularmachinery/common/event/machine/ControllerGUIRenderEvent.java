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

/**
 * Lets a handler add lines to the controller GUI (upstream ControllerGUIRenderEvent, 32 lines).
 *
 * <p>THE ONLY ONE OF UPSTREAM THREE CLIENT EVENTS THAT PORTS (PLAN 511). The other two carry
 * <code>@ModOnly("geckolib3")</code> and lean on GeckoLib animation state -- 140 lines of it in the largest -- AND
 * GECKOLIB IS NOT IN THIS PROJECT SCOPE. LEAVING THEM OUT IS NOT A DEVIATION FROM UPSTREAM: <code>@ModOnly</code> IS
 * UPSTREAM OWN OPTIONALITY GATE, so upstream itself treats those two as present-only-when-GeckoLib-is.</p>
 *
 * <p>THIS ONE CARRIES PLAIN STRINGS AND NOTHING ELSE, which is why it can be ported as-is. The upstream
 * ZenGetter/ZenSetter annotations are dropped for the reason recorded at 488.4: a core class must not name a soft
 * dependency, and the port CraftTweaker strategy is the 14.x plugin API rather than script annotations.</p>
 *
 * <p><b>setExtraInfo APPENDS RATHER THAN REPLACES (upstream U30: extraInfo.addAll(...)).</b> A port that assigned
 * would silently drop lines added by an earlier handler. The getter returns an ARRAY rather than the list, so a caller
 * cannot mutate the event state through it -- both details come from the body, not the signature.</p>
 */
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

