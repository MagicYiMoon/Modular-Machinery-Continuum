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
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The server-side half of the machine controller GUI.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream's {@code ContainerMachineController} was built directly by its
 * {@code IGuiHandler}. Here the menu is instantiated twice from <b>the same registration</b>: once on
 * the server when the controller is used, and once on the client when the open packet arrives. Both
 * paths run this constructor, which is why it must tolerate the block entity being absent — on the
 * client the chunk may not be loaded yet, and throwing would disconnect the player.
 *
 * <p><b>No item slots.</b> The controller holds no inventory of its own; the machine's items live in its
 * busses and hatches, which have their own GUIs. Registering slots here would let a player move items
 * into a container that does not exist on the server, which desynchronises and voids stacks.
 *
 * <h2>The progress data</h2>
 *
 * <p>{@link ContainerData} is 1.20.1's replacement for the old {@code IInventory} fields that used to
 * carry GUI state: a small {@code int[]} the server keeps authoritatively and syncs to the client, with
 * each index exposed by a getter/setter. It is used here rather than a custom packet because
 * {@code AbstractContainerMenu} already sends it every tick, so there is nothing to hand-roll and no
 * separate sync path to get wrong.
 */
public class MenuMachineController extends AbstractContainerMenu {

    /**
     * Panel size, from the jar's GuiMachineController: it assigns {@code ySize = 213} in its constructor and
     * leaves {@code xSize} at the vanilla 176. See ScreenMachineController for why 166 was wrong.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 213;

    // The player-inventory rows, at UPSTREAM's coordinates rather than vanilla's (§192).
    //
    // Vanilla puts them at 84 and the hotbar at 142, for a 166-tall panel. A 213-tall panel cannot use
    // those: the jar's ContainerController has the rows at 131 and the hotbar at 189, and 189 + 18 + 6 = 213,
    // which is where the height comes from in the first place.
    //
    // MenuUpgradeBus already had these numbers and is the screen the client reported as correct, which is
    // what identified the defect: two 213-tall screens were using a 166-tall screen's layout.
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 131;
    public static final int PLAYER_HOTBAR_Y = 189;

    /**
     * The blueprint slot, at the coordinates the jar's ContainerController uses.
     *
     * <p>From its constructor bytecode: iconst_0 (index), sipush 151 (x), bipush 8 (y). Transcribed rather
     * than chosen -- the panel art draws the well there, and the factory controller uses a different x on a
     * wider panel, so this is per-screen data and not a constant of the mod.
     */
    private static final int BLUEPRINT_SLOT_X = 151;
    private static final int BLUEPRINT_SLOT_Y = 8;

    /** Index of the recipe progress, in ticks elapsed. */
    public static final int DATA_PROGRESS = 0;
    /** Index of the recipe's total duration, in ticks. */
    public static final int DATA_DURATION = 1;
    /** Index of the "machine is formed" flag, 0 or 1. */
    public static final int DATA_FORMED = 2;
    /** Index of the number of bound components, for diagnosing a half-assembled machine. */
    public static final int DATA_COMPONENTS = 3;
    /**
     * Index of the "held stopped by a redstone signal" flag, 0 or 1. See {@code TileController
     * #isRedstoneStopped}.
     */
    public static final int DATA_REDSTONE = 4;
    /**
     * Index of the "this controller's chunk is loaded" flag, 0 or 1. A weaker check than upstream's
     * chunk-unloaded state — see {@code TileController#isStructureChunkLoaded}.
     */
    public static final int DATA_CHUNK = 5;
    /** How many ints this menu syncs. Must match the field list above exactly. */
    public static final int DATA_SIZE = 6;

    private final BlockPos controllerPos;
    private final ContainerData data;

    /**
     * The blueprint slot's inventory. On the server this is the controller's own handler; on the client it
     * is a same-sized placeholder whose contents the server synchronises.
     */
    private final net.minecraftforge.items.IItemHandler blueprintInventory;

    /**
     * Client-side constructor, invoked from the menu type's factory.
     *
     * <p>Delegates to {@link SimpleContainerData} because on this side the values are received from the
     * server rather than read from the world.
     */
    public MenuMachineController(int windowId, Inventory playerInventory, BlockPos controllerPos) {
        this(windowId, playerInventory, controllerPos, new SimpleContainerData(DATA_SIZE),
                new net.minecraftforge.items.ItemStackHandler(1));
    }

    public MenuMachineController(int windowId, Inventory playerInventory, BlockPos controllerPos,
                                 ContainerData data, net.minecraftforge.items.IItemHandler blueprintInventory) {
        super(RegistryMenus.MACHINE_CONTROLLER.get(), windowId);
        this.controllerPos = controllerPos;
        this.data = data;
        this.blueprintInventory = blueprintInventory;
        addDataSlots(data);
        // A one-slot handler either way, so BOTH sides build the same slot list -- AbstractContainerMenu
        // matches slots by index during synchronisation, so a length mismatch on the client corrupts every
        // stack after it. On the client the contents arrive from the server; only the shape has to agree.
        addSlot(new net.minecraftforge.items.SlotItemHandler(
                blueprintInventory, 0, BLUEPRINT_SLOT_X, BLUEPRINT_SLOT_Y));

        addPlayerInventory(playerInventory);
    }

    /**
     * Builds the server-side menu for a controller, wiring the live data source.
     *
     * @return the menu, or {@code null} when the position holds no controller
     */
    /**
     * Adds the player inventory, matching upstream's {@code ContainerBase}.
     *
     * <p><b>This was missing.</b> Upstream's {@code ContainerController} extends {@code ContainerBase},
     * whose constructor adds the player's 3x9 inventory and hotbar -- so <i>every</i> Modular Machinery
     * GUI has them. Without it the controller screen cannot be used to move anything, and it is the same
     * gap found on both hatches.
     *
     * <p>Coordinates are upstream's: 3x9 at (8,84), hotbar at (8,142).
     */
    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(playerInventory,
                        column + row * 9 + 9, PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(playerInventory, column,
                    PLAYER_INV_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public static MenuMachineController forController(int windowId, Inventory playerInventory,
                                                      TileController controller) {
        return new MenuMachineController(windowId, playerInventory, controller.getBlockPos(),
                dataFor(controller), controller.getBlueprintInventory());
    }

    /**
     * Builds the synced data source for a controller.
     *
     * <p>Public and separate from {@link #forController} so it can be verified without an
     * {@code Inventory}, which in turn requires a {@code Player} — and a {@code Player} cannot be
     * constructed in a headless server test. The property worth testing lives entirely here.
     */
    public static ContainerData dataFor(TileController controller) {
        return liveData(controller);
    }

    /**
     * Exposes a controller's state through {@link ContainerData}.
     *
     * <p>The getters read the controller <b>every time they are called</b> rather than caching a
     * snapshot. Caching would be the obvious optimisation and is wrong: {@code AbstractContainerMenu}
     * sends this data on a tick, so a snapshot taken at menu-open time would freeze the GUI on whatever
     * the machine was doing when the player opened it.
     */
    private static ContainerData liveData(TileController controller) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> controller.getRecipeTicks();
                    case DATA_DURATION -> controller.getRecipeDuration();
                    case DATA_FORMED -> controller.isFormed() ? 1 : 0;
                    case DATA_COMPONENTS -> controller.getBoundComponentCount();
                    // The two states §109's screen could not name, added in §135. Both read the world live,
                    // like every other case here — a cached flag would freeze the status line.
                    case DATA_REDSTONE -> controller.isRedstoneStopped() ? 1 : 0;
                    case DATA_CHUNK -> controller.isStructureChunkLoaded() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative: the client's writes must not affect the machine, and ignoring
                // them is what keeps a modified client from rewriting another player's machine state.
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

    public int getProgress() {
        return data.get(DATA_PROGRESS);
    }

    public int getDuration() {
        return data.get(DATA_DURATION);
    }

    public boolean isFormed() {
        return data.get(DATA_FORMED) != 0;
    }

    public int getComponents() {
        return data.get(DATA_COMPONENTS);
    }

    /** @return true when a redstone signal is holding the machine stopped */
    public boolean getRedstoneStopped() {
        return data.get(DATA_REDSTONE) != 0;
    }

    /**
     * @return true when the controller's chunk is loaded.
     *
     * <p>Note this is the weaker of the two readings upstream's {@code chunk_unloaded} state covers — it
     * checks the controller's own chunk, not every block of the structure, because the port does not retain
     * the component positions between formations. Documented at the tile, and surfaced here rather than
     * silently presented as the full check.
     */
    public boolean getChunkLoaded() {
        return data.get(DATA_CHUNK) != 0;
    }

    /**
     * @return progress in {@code [0,1]}, or 0 when no recipe is running
     *
     * <p>Guards against a zero duration rather than dividing: a malformed recipe would otherwise throw
     * inside rendering, which crashes the client rather than merely drawing a wrong bar.
     */
    public float getProgressFraction() {
        int duration = getDuration();
        if (duration <= 0) {
            return 0F;
        }
        return Math.min(1F, (float) getProgress() / duration);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always false. The controller has no slots, so there is nothing to shift-click into, and
     * returning true would tell the client a transfer happened when none did.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Upstream's controller adds one blueprint slot plus the player inventory. The blueprint slot is
        // not implemented here (blueprints are absent entirely), so every slot is a player slot and
        // shift-click only moves between the inventory and the hotbar -- which is what upstream's
        // ContainerBase does when the owner contributes nothing to the merge.
        net.minecraft.world.inventory.Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        // Index 0 is the blueprint slot, so the player inventory starts at 1. Shift-clicking a blueprint
        // goes into the slot; anything else just swaps between the inventory and the hotbar.
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 28, 37, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 1, 28, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checked each tick, so breaking the controller or walking out of range closes the GUI instead of
     * leaving it open over a machine that no longer exists.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(controllerPos) instanceof TileController
                && player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                        controllerPos.getZ() + 0.5) <= 64.0;
    }
}
