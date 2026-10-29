/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.event;

import hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxyRegistry;
import net.minecraftforge.eventbus.api.Event;

/** Posted once to each mod bus during enqueued common setup, before worlds are opened. */
public final class RegisterMachineComponentProxiesEvent extends Event implements net.minecraftforge.fml.event.IModBusEvent {
    private final MachineComponentProxyRegistry registry;
    public RegisterMachineComponentProxiesEvent(MachineComponentProxyRegistry registry) { this.registry = registry; }
    public MachineComponentProxyRegistry getRegistry() { return registry; }
}
