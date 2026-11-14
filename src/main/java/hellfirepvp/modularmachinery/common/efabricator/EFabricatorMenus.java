/* SPDX-License-Identifier: GPL-3.0-only; adapts NECO EF menu navigation. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.common.menu.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

public final class EFabricatorMenus {
    private EFabricatorMenus() { }
    public static void controller(ServerPlayer player, TileEFabricatorController owner, boolean search) {
        var session = UUID.randomUUID();
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, ignored) -> search
                ? MenuEFabricatorPatternSearch.forController(id, inv, owner, session)
                : MenuEFabricatorController.forController(id, inv, owner, session), owner.getBlockState().getBlock().getName()),
                b -> { b.writeBlockPos(owner.getBlockPos()); b.writeUUID(session); });
    }
    public static void bus(ServerPlayer player, TileEFabricatorPatternBus bus) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, ignored) -> MenuEFabricatorPatternBus.forBus(id, inv, bus),
                Component.translatable("gui.modularmachinery.ef.pattern_bus")), b -> b.writeBlockPos(bus.getBlockPos()));
    }
}
