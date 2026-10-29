/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine.component;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Explicit optional-mod adapter, corresponding to source MMCE's component proxy. */
public interface MachineComponentProxy {
    boolean isSupported(BlockEntity blockEntity);
    @Nullable ProxiedMachineComponent proxyComponent(BlockEntity blockEntity);
}
