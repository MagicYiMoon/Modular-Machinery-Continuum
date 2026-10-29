/* SPDX-License-Identifier: GPL-3.0-only
 * Source MMCE SmartInterfaceData script contract adapted to the port's existing provider storage.
 */
package hellfirepvp.modularmachinery.common.util;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.openzen.zencode.java.ZenCodeType;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Guarded view of a provider datum; never a second serialized data model. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.SmartInterfaceData")
public final class SmartInterfaceData {
    private final TileController controller;
    private final TileSmartInterface owner;
    private final TileSmartInterface.Data data;
    private final BlockPos pos;
    private final String machine;
    private final String binding;
    private final String type;
    private final boolean live;
    private final float observation;
    private final BooleanSupplier publishing;

    private SmartInterfaceData(TileController controller, TileSmartInterface owner,
                               TileSmartInterface.Data data, BooleanSupplier publishing) {
        this.controller = controller; this.owner = owner; this.data = data; this.publishing = publishing;
        this.pos = controller.getBlockPos().immutable(); this.binding = controller.getMachineName();
        this.machine = data.getMachineName() == null ? binding : data.getMachineName();
        this.type = data.getKey(); this.observation = (float) data.getNumber();
        this.live = controller.getLevel() instanceof ServerLevel server && server.getServer().isSameThread();
    }

    public static SmartInterfaceData view(TileController controller, TileSmartInterface owner, TileSmartInterface.Data data) {
        return new SmartInterfaceData(controller, owner, data, () -> false);
    }

    /** During publication the new datum exists before formation has finished recording bound positions. */
    public static SmartInterfaceData publication(TileController controller, TileSmartInterface owner,
                                                  TileSmartInterface.Data data, BooleanSupplier publishing) {
        return new SmartInterfaceData(controller, owner, data, publishing);
    }

    @ZenCodeType.Getter("pos") public BlockPos getIPos() { return pos; }
    @ZenCodeType.Getter("parentMachineName") @ZenCodeType.Nullable
    public String getParentMachineName() { return machine; }
    @ZenCodeType.Getter("interfaceType") public String getType() { return type; }
    @ZenCodeType.Getter("value") public float getValue() { return live ? (float) data.getNumber() : observation; }

    @ZenCodeType.Setter("value") public void setValue(float value) {
        if (!canWrite()) return;
        if (!Float.isFinite(value)) throw new IllegalArgumentException("smart interface value must be finite");
        if (Double.compare(data.getNumber(), value) == 0) return;
        data.setNumber(value);
        // Upstream's setter does not re-post SmartInterfaceUpdateEvent. Persist/sync the existing provider.
        owner.markNoUpdateSync();
    }

    private boolean canWrite() {
        return live && owner != null && controller.getLevel() instanceof ServerLevel
                && controller.canChangeRuntimeModifiers() && owner.getLevel() == controller.getLevel()
                && !owner.isRemoved() && owner.getLevel().hasChunkAt(owner.getBlockPos())
                && owner.getLevel().getBlockEntity(owner.getBlockPos()) == owner
                && pos.equals(owner.getControllerPos()) && Objects.equals(binding, controller.getMachineName())
                && (data.getMachineName() == null || Objects.equals(machine, data.getMachineName()))
                && owner.getBoundData().contains(data)
                && (publishing.getAsBoolean() || controller.isFormed() && Objects.equals(machine, binding)
                && controller.getBoundPositions().contains(owner.getBlockPos()));
    }
}
