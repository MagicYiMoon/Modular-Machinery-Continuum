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

import hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.network.UpgradeBusInfoPacket;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The upgrade bus GUI: a grid of upgrade slots plus the player's inventory.
 *
 * <h2>Geometry, read from the jar -- and why it is not the usual shape</h2>
 *
 * <p>Two things about this screen differ from every other container in the mod, and both were read out of
 * {@code ContainerUpgradeBus} rather than assumed:
 *
 * <ol>
 *   <li><b>The upgrade slots wrap every 3 columns.</b> From the jar's {@code addInventorySlots}:
 *       {@code x = 8; y = 17; ... x += 18; if ((index+1) % 3 == 0) { x = 8; y += 18; }}. So this is a
 *       <b>3-wide</b> grid, not the 9-wide chest layout used elsewhere. The largest tier, ULTIMATE with 18
 *       slots, becomes 6 rows ending at y=125.</li>
 *   <li><b>The player inventory is at y=131, not 84</b>, and the hotbar at <b>189</b>, not 142.</li>
 * </ol>
 *
 * <p>Those two facts explain each other: {@code ContainerUpgradeBus} <b>overrides</b>
 * {@code ContainerBase#addPlayerSlots} to push the player inventory down, because the upgrade grid
 * occupies the space above, and grows the panel to <b>213</b> to fit. 125 + 18 = 143, clear of 131; and
 * the hotbar at 189 plus its 18 rows plus the border lands on 213.
 *
 * <p><b>So {@code ContainerBase}'s 84/142 are a default, not a constant.</b> This port originally pressed
 * every container into that shape, which cannot render this GUI correctly at all.
 */
public class MenuUpgradeBus extends AbstractContainerMenu {

    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 213;
    public static final int GRID_SIZE = 18;
    public static int upgradeX(int slot) { return 8 + slot % 3 * 18; }
    public static int upgradeY(int slot) { return 17 + slot / 3 * 18; }

    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 131;
    public static final int PLAYER_HOTBAR_Y = 189;

    private final BlockPos busPos;
    private final UpgradeBusData tier;
    private final int upgradeSlots;
    private final ServerPlayer viewer;
    private final TileUpgradeBus bus;
    private long nextInfoUpdate;
    private List<Component> descriptions = List.of();

    public MenuUpgradeBus(int windowId, Inventory playerInventory, BlockPos busPos, int tierOrdinal, int slotCount) {
        this(windowId, playerInventory, busPos, tierFromOrdinal(tierOrdinal),
                new ItemStackHandler(clampSlots(slotCount)), null);
    }

    private static int clampSlots(int count) { return Math.max(1, Math.min(GRID_SIZE, count)); }

    /**
     * Resolves a tier from its on-wire ordinal, clamping rather than throwing.
     *
     * <p>A malformed packet must not break screen construction on the client, and the server never trusts
     * this value for anything that matters.
     */
    private static UpgradeBusData tierFromOrdinal(int ordinal) {
        UpgradeBusData[] values = UpgradeBusData.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return UpgradeBusData.NORMAL;
        }
        return values[ordinal];
    }

    private MenuUpgradeBus(int windowId, Inventory playerInventory, BlockPos busPos, UpgradeBusData tier,
                           IItemHandler inventory, TileUpgradeBus bus) {
        super(RegistryMenus.UPGRADE_BUS.get(), windowId);
        this.busPos = busPos;
        this.tier = tier;
        this.bus = bus;
        this.viewer = bus != null && playerInventory != null && playerInventory.player instanceof ServerPlayer server ? server : null;
        this.upgradeSlots = clampSlots(inventory.getSlots());

        for (int index = 0; index < upgradeSlots; index++) {
            addSlot(new SlotItemHandler(inventory, index, upgradeX(index), upgradeY(index)));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public static MenuUpgradeBus forBus(int windowId, Inventory playerInventory, TileUpgradeBus bus) {
        return new MenuUpgradeBus(windowId, playerInventory, bus.getBlockPos(), bus.getTier(),
                bus.getUpgradeInventory(), bus);
    }

    public static void writeOpenData(net.minecraft.network.FriendlyByteBuf buffer, TileUpgradeBus bus) {
        buffer.writeBlockPos(bus.getBlockPos());
        buffer.writeVarInt(bus.getTier().ordinal());
        buffer.writeVarInt(bus.getSlots());
    }

    public UpgradeBusData getTier() { return tier; }
    public int getUpgradeSlotCount() { return upgradeSlots; }
    public BlockPos getBusPos() { return busPos; }

    public List<Component> descriptions() { return descriptions; }
    public void setDescriptions(List<Component> lines) { descriptions = List.copyOf(lines); }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer == null || viewer.connection == null || viewer.containerMenu != this || !stillValid(viewer)) return;
        long now = viewer.serverLevel().getGameTime();
        if (now < nextInfoUpdate) return;
        nextInfoUpdate = now + 10;
        var updated = describe(bus);
        if (!updated.equals(descriptions)) {
            descriptions = updated;
            ModularmachineryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new UpgradeBusInfoPacket(containerId, busPos, updated));
        }
    }

    public static List<Component> describe(TileUpgradeBus bus) {
        List<Component> lines = new ArrayList<>();
        var upgrades = bus.getInstalledUpgrades();
        var machineName = bus.getBoundMachineName();
        if (machineName == null || machineName.isBlank() || bus.getControllerPos() == null) {
            lines.add(Component.translatable("gui.upgradebus.bounded.empty"));
        } else {
            var machine = MachineReloadListener.serverDefinitions().machines().findByName(machineName);
            lines.add(Component.translatable("gui.modularmachinery.upgrade_bus.bound_machine",
                    machine == null ? machineName : machine.getLocalizedName()));
            lines.add(Component.literal(bus.getControllerPos().toShortString()));
            for (var upgrade : upgrades) {
                if (!upgrade.getType().isCompatible(machineName)) {
                    lines.add(Component.translatable("gui.upgradebus.incompatible", upgrade.getType().getLocalizedName()));
                }
            }
        }
        lines.add(Component.empty());
        if (upgrades.isEmpty()) lines.add(Component.translatable("gui.modularmachinery.upgrade_bus.no_modules"));
        for (var upgrade : upgrades) {
            if (lines.size() >= UpgradeBusInfoPacket.MAX_LINES) break;
            lines.add(Component.literal(upgrade.getStackSize() + "x ").append(upgrade.getType().getLocalizedName()));
            for (String line : upgrade.getBusGUIDescriptions()) {
                if (lines.size() >= UpgradeBusInfoPacket.MAX_LINES) break;
                lines.add(Component.literal(line.substring(0, Math.min(256, line.length()))));
            }
            if (lines.size() < UpgradeBusInfoPacket.MAX_LINES) lines.add(Component.empty());
        }
        return List.copyOf(lines.subList(0, Math.min(lines.size(), UpgradeBusInfoPacket.MAX_LINES)));
    }

    /** {@inheritDoc} Moves stacks between the upgrade grid and the player inventory in either direction. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int upgradeSlots = getUpgradeSlotCount();

        if (index < upgradeSlots) {
            if (!moveItemStackTo(stack, upgradeSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, upgradeSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(busPos) instanceof TileUpgradeBus current
                && (bus == null || current == bus && current.getTier() == tier && current.getSlots() == upgradeSlots)
                && player.distanceToSqr(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5)
                        <= 64.0;
    }
}
