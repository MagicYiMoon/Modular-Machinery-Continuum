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
 * original named above. Modifications made 2026-08 .. 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.base.MachineGroupInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The group-input configuration menu.
 *
 * <h2>No inventory slots of its own</h2>
 *
 * <p>Upstream's {@code ContainerGroupInputConfig} is exactly {@code super(owner, opening)} plus a reference to
 * the {@code MachineGroupInput} it configures (§102.1 — read from the jar, no slot fields). {@code ContainerBase}
 * supplies the player's 3x9 and hotbar, so that is the whole of this screen's slot content. The same conclusion
 * as {@code MenuSmartInterface}, and stated for the same reason: it is the opposite of the buses' and easy to
 * get wrong in either direction.
 *
 * <h2>Panel 176x166</h2>
 *
 * <p>Upstream's {@code GuiContainerGroupInputConfig.setWidthHeight()} is empty (§158), so the vanilla default
 * applies — the rule this port already recorded on {@code MenuSmartInterface}. §91 is why this is stated rather
 * than assumed: the controller screen shipped 47 pixels short because an empty {@code setWidthHeight()} was
 * taken for a value.
 */
public class MenuGroupInputConfig extends AbstractContainerMenu {

    /** Panel size; see the class javadoc. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    private final BlockPos machinePos;

    /** Client-side constructor, invoked from the menu type's factory. */
    public MenuGroupInputConfig(int windowId, Inventory playerInventory, BlockPos machinePos) {
        super(RegistryMenus.GROUP_INPUT_CONFIG.get(), windowId);
        this.machinePos = machinePos;
        // §199: the sync slots are wired HERE, not in a factory.
        //
        // §194 added them to forMachine and reported the screen fixed. It was not: the menu type's factory
        // (RegistryMenus) calls this constructor directly, so forMachine had no callers anywhere in the tree
        // and the slots were never added. The client went on showing 0 after every save -- exactly the symptom
        // §194 set out to fix, and the fix was in a method nothing reaches.
        //
        // That is §194's own defect shape one level up: §194 was "a signature that promises a dependency its
        // body does not take"; this is "a method that exists and is never called". Both read as working code.
        if (playerInventory.player.level().getBlockEntity(machinePos)
                instanceof MachineGroupInput machine) {
            wireGroupSlots(machine);
        }
        addPlayerInventory(playerInventory);
    }

    /**
     * Wires the component's group state to two data slots.
     *
     * <p>{@code get} runs on the server and is what gets sent; {@code set} runs on the client and stores into
     * the field the screen reads. The client cannot reach the block entity's field itself, which is why the
     * value needs a channel at all.
     */
    private void wireGroupSlots(MachineGroupInput machine) {
        this.syncedGroupId = machine.getGroupId();
        this.syncedGroupInput = machine.isGroupInput();
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machine.getGroupId();
            }

            @Override
            public void set(int value) {
                MenuGroupInputConfig.this.syncedGroupId = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return machine.isGroupInput() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                MenuGroupInputConfig.this.syncedGroupInput = value != 0;
            }
        });
    }

    /**
     * Server-side constructor for a group-input component, wiring the component's live state to the client.
     *
     * <p><b>§194: the {@code machine} parameter used to be dropped on the floor.</b> The signature took it and
     * the body ignored it, so the screen had no way to learn the current group id -- and the handler in
     * {@code PktGroupInputConfig} writes that id to the tile correctly. The result, reported from the client,
     * was that the number "does not save": it saved, and the field reopened empty every time, because nothing
     * carried it back.
     *
     * <p>{@link net.minecraft.world.inventory.DataSlot} is the standard channel for this. The client cannot
     * read the tile's field, so the value travels as a syncable slot: {@code get} runs on the server and
     * {@code set} on the client, which stores it where the screen can read it.
     */
    /**
     * @deprecated the menu type's factory calls the constructor directly, so this had no callers and its
     *         DataSlot wiring never ran (§199). Kept as a delegating alias so a caller that finds it gets the
     *         same menu rather than a different one.
     */
    @Deprecated
    public static MenuGroupInputConfig forMachine(int windowId, Inventory playerInventory,
                                                  MachineGroupInput machine, BlockPos pos) {
        return new MenuGroupInputConfig(windowId, playerInventory, pos);
    }

    /** Server-side value, or the client's copy of it once {@link #forMachine} has synced. */
    private int syncedGroupId;
    private boolean syncedGroupInput;

    /** @return the component's group id, as last synced to this side */
    public int getGroupId() {
        return syncedGroupId;
    }

    /** @return whether the component participates in group input, as last synced */
    public boolean isGroupInput() {
        return syncedGroupInput;
    }

    /** Adds the player inventory, which is this screen's <b>entire</b> slot content. */
    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    public BlockPos getMachinePos() {
        return machinePos;
    }

    /** {@inheritDoc} Every slot is a player slot, so shift-click only swaps inventory and hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
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
     * The target must still be a group-input-capable component, and in range.
     *
     * <p>Checking <b>the interface</b> rather than a concrete tile is what makes this menu usable on any
     * component — which is upstream's design: the config screen opens on an existing hatch or bus, not on a
     * block of its own (§157).
     */
    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(machinePos)
                        instanceof hellfirepvp.modularmachinery.common.tiles.base.MachineGroupInput
                && player.distanceToSqr(machinePos.getX() + 0.5, machinePos.getY() + 0.5,
                        machinePos.getZ() + 0.5) <= 64.0;
    }
}
