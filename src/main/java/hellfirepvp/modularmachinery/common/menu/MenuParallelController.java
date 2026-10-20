/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.TileParallelController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The server-side half of a parallel controller GUI.
 *
 * <p>Unlike the state-only screens (controller, hatches) and the slot screen (item bus), this one shows a
 * <b>setting</b>: how many copies of a recipe the machine may run at once. That difference matters for the
 * sync design — a setting can be changed while the window is open, so the data must be live in both
 * directions and the display must not be the only source of truth.
 *
 * <p>The value is read from the block entity on each access rather than cached, for the same reason as
 * everywhere else: a snapshot taken at window-open would freeze the moment the value changed.
 *
 * <h2>Adjustment, and where it is authorised</h2>
 *
 * <p>The screen adjusts the value through the channel ({@code PktSetParallelism}, sent from the buttons and
 * from the text field) rather than through this menu. That is deliberate and is stated at
 * {@code dataFor}'s {@code set} below: letting the client write through {@code ContainerData} would make the
 * value forgeable, so the write path is a packet whose handler re-derives everything from the world. This
 * javadoc used to claim the opposite — that in-GUI adjustment was unimplemented "because this port has no
 * network channel" — and that claim outlived the channel by several rounds; recorded here rather than
 * quietly deleted, because a stale justification reads exactly like a design decision.
 *
 * <h2>The payload carries the SETTING, not only the position (defect A)</h2>
 *
 * <p><b>Reported:</b> "并行数输入后不会保存，默认为0". The tile was right — {@code PktSetParallelism} reached it,
 * {@code setParallelism} clamped and {@code markForUpdate} marked the chunk dirty, and a save/load kept the
 * number. The screen was what read 0, and the reason is an ORDER in vanilla and Forge rather than a wrong
 * value anywhere:</p>
 *
 * <pre>
 *   Forge 47.3.0 NetworkHooks.openScreen, L208 vs L212 (bytecode read, not recalled):
 *       NetworkConstants.playChannel.sendTo(new PlayMessages.OpenContainer(...), ...)  &lt;- the window packet
 *       player.containerMenu = c; player.initMenu(player.containerMenu);              &lt;- the menu, only after
 *
 *   vanilla 1.20.1 DataSlot.checkAndClearUpdateFlag:
 *       int i = get(); boolean flag = i != prevValue; prevValue = i; return flag;      &lt;- prevValue starts at 0
 *
 *   vanilla 1.20.1 ServerPlayer: containerMenu.broadcastChanges() once per tick
 *   vanilla 1.20.1 DataSlot.forContainer + SimpleContainerData: the client's store starts all-zero
 * </pre>
 *
 * <p>The window packet is sent BEFORE the menu exists on the server, and the values only travel on the FIRST
 * {@code broadcastChanges} after — so on the client the screen is built, and its {@code init()} reads
 * {@code menu.getParallelism()} out of a still-zero {@code SimpleContainerData}, before the server's data
 * arrives. Nothing re-reads the field afterwards, so the number a player had entered appeared as 0 on every
 * reopen while the block held it correctly.</p>
 *
 * <p>The port's own carrier for "what the client cannot derive" is this payload — {@code MenuItemBus} sends
 * its size ordinal and {@code MenuUpgradeBus} its tier ordinal for exactly this reason — so the setting
 * travels in it too, through {@link #writeOpenData}, and the client's data slots are seeded from it rather
 * than from zeros. {@code ContainerData} keeps syncing underneath (one convention, not two): the server's
 * slot stays live and the first server update still reaches the client, it simply agrees.</p>
 */
public class MenuParallelController extends AbstractContainerMenu {
    /**
     * Panel size, from the jar's GUI class. Asserted server-side by MachineSelfTest -- see §91: nine screens
     * carried numerical assertions against the jar and passed on a screen that was visibly broken, because
     * the panel size was treated as an established constant rather than a value to verify.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    public static final int DATA_PARALLELISM = 0;
    public static final int DATA_MAX = 1;
    public static final int DATA_SIZE = 2;

    private final BlockPos controllerPos;
    private final ContainerData data;

    /**
     * Client-side constructor, invoked from the menu type's factory with the values the payload carried.
     *
     * <p>The two numbers seed a {@link SimpleContainerData} — the store the server's data slots write into
     * once they arrive — so the value {@code init()} reads is the one the block holds. Clamping here is
     * deliberate and uses the packet's own rule, matching {@code dataFor}'s server-side source: a payload
     * cannot put a value on screen that the controller could never hold.</p>
     */
    public MenuParallelController(int windowId, Inventory playerInventory, BlockPos controllerPos,
                                  int parallelism, int maxParallelism) {
        this(windowId, playerInventory, controllerPos, seeded(parallelism, maxParallelism));
    }

    private static ContainerData seeded(int parallelism, int maxParallelism) {
        SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
        data.set(DATA_PARALLELISM,
                hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktSetParallelism
                        .clampParallelism(parallelism, maxParallelism));
        data.set(DATA_MAX, maxParallelism);
        return data;
    }

    /**
     * Writes a controller into an open-screen payload, for {@code BlockParallelController.use} and for the
     * check that decodes it back through this menu's own registered factory.
     *
     * <p>ONE WRITER, TWO CALLERS, as {@code MenuUpgradeBus#writeOpenData} does: a check that re-derived the
     * payload itself would agree with the block by luck, and the client half is the half that was broken.</p>
     */
    public static void writeOpenData(net.minecraft.network.FriendlyByteBuf buffer,
                                     TileParallelController controller) {
        buffer.writeBlockPos(controller.getBlockPos());
        buffer.writeVarInt(controller.getParallelism());
        buffer.writeVarInt(controller.getMaxParallelism());
    }

    public MenuParallelController(int windowId, Inventory playerInventory, BlockPos controllerPos,
                                  ContainerData data) {
        super(RegistryMenus.PARALLEL_CONTROLLER.get(), windowId);
        this.controllerPos = controllerPos;
        this.data = data;
        addDataSlots(data);
    }

    public static MenuParallelController forController(int windowId, Inventory playerInventory,
                                                       TileParallelController controller) {
        return new MenuParallelController(windowId, playerInventory, controller.getBlockPos(),
                dataFor(controller));
    }

    /**
     * Builds the synced data source for a parallel controller.
     *
     * <p>Separate from {@link #forController} so it can be verified without an {@code Inventory}, which
     * needs a {@code Player} and therefore cannot be constructed in a headless server test — the same
     * seam added on the other menus.
     */
    public static ContainerData dataFor(TileParallelController controller) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PARALLELISM -> controller.getParallelism();
                    case DATA_MAX -> controller.getMaxParallelism();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative. The setter a future network packet will call lives on the block
                // entity; letting the client write through ContainerData would make the value forgeable.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public int getParallelism() {
        return data.get(DATA_PARALLELISM);
    }

    public int getMaxParallelism() {
        return data.get(DATA_MAX);
    }

    /** @return the configured parallelism as a fraction of the maximum, in {@code [0,1]} */
    public float getFraction() {
        int max = getMaxParallelism();
        if (max <= 0) {
            return 0F;
        }
        return Math.min(1F, (float) getParallelism() / max);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(controllerPos) instanceof TileParallelController
                && player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                        controllerPos.getZ() + 0.5) <= 64.0;
    }
}
