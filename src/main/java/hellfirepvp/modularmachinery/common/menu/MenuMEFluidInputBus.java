/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMEMenus;
import hellfirepvp.modularmachinery.common.tiles.me.MEFluidBus;
import hellfirepvp.modularmachinery.common.tiles.me.MEFluidInputBus;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The server-side half of the ME fluid input bus GUI — port of upstream's {@code ContainerMEFluidInputBus} (101 lines), and the
 * PRODUCER §649(b) found missing: it is what finally calls {@link MEFluidInputBus#setConfiguredFluid}.
 *
 * <h2>What upstream's container actually is (rule A, read at the body)</h2>
 *
 * <pre>
 *   ContainerMEFluidInputBus extends ContainerFluidConfigurable          (upstream C21)
 *     C28   tankSync = new FluidSyncHelper(owner.getTanks(), TANK_SLOT_AMOUNT)
 *     C41   getFluidConfigInventory() returns owner.getConfig()
 *     C50   detectAndSendChanges -> tankSync.sendDiff(this.listeners)
 *     C57   setupConfig -> setupUpgrades (five AE2 upgrade slots at (187, 8+18i))
 *     C36   getHeight() = 231, which is the panel this port's menu declares
 *   and its screen, GuiMEFluidInputBus (78 lines):
 *     U40-U58  nine GuiFluidSlot(configFluids, i, i, 8 + 18i, 35)  -- the CONFIG row
 *     U41-U42  nine GuiFluidTank(fluidTank, i, 9 + i, 8 + 18i, 53, 16, 68)  -- the STORED row
 * </pre>
 *
 * <p>So the player-facing mechanism upstream is: <b>nine slots that hold a configured fluid and are written by clicking
 * them</b> (AE2's {@code GuiFluidSlot} writes through the container), over nine read-only bars showing what each tank holds.</p>
 *
 * <h2>RULE A0 — why this is a vanilla menu, and what that changes</h2>
 *
 * <p><b>(1) Upstream's method:</b> the container extends {@code ContainerFluidConfigurable} and configures slots through
 * {@code IAEFluidTank}, syncing them with {@code FluidSyncHelper}/{@code IFluidSyncContainer}. The screen's nine config slots
 * are AE2's {@code GuiFluidSlot}, whose notifications go back to the server through that same container.</p>
 *
 * <p><b>(2) Evidence it cannot work on 1.20.1 / AE2 15.4.11</b> — listed straight out of the AE2 jar this project compiles
 * against (15.4.11; {@code jar tf appliedenergistics2-forge-15.4.11.jar}): <b>there is no {@code appeng/fluids/} package at
 * all.</b> Every name upstream's container rests on is absent — {@code ContainerFluidConfigurable},
 * {@code FluidSyncHelper}, {@code IFluidSyncContainer}, {@code IAEFluidTank}, {@code IAEFluidInventory},
 * {@code AEFluidInventory}, {@code GuiFluidSlot}, {@code GuiFluidTank}, {@code GuiUpgradeable},
 * {@code SlotRestrictedInput}. The only fluid-named GUI class that survived is
 * {@code appeng/client/gui/style/FluidBlitter}, which blits a fluid sprite and knows nothing about inventories.</p>
 *
 * <p><b>(3) What the port does instead, and why each part is cheaper rather than merely different:</b></p>
 *
 * <pre>
 *   IAEFluidTank config         -> the tile's own FluidStack[] config, which already existed (§649(b))
 *   FluidSyncHelper             -> ContainerData (the port's own convention, cf. MenuFluidHatch)
 *   GuiFluidSlot + its channel  -> a vanilla Slot plus PktMEFluidInputBusConfig
 *   the nine GuiFluidTank bars  -> ContainerData pairs for the tank fluid registry id and amount. The client screen draws
 *                                  the atlas sprite in the same 68-pixel wells; AE2's removed widget is not required.
 *   setupUpgrades (C66-C73)     -> five positioned AE2 upgrade slots, backed by the bus's IUpgradeInventory. The
 *                                  capacity-card association and the tank-capacity callback are registered in the AE2
 *                                  integration class, preserving upstream's 8000 mB × 4^n capacity rule.
 * </pre>
 *
 * <h2>Why the config slots are GHOST slots, and how the two sync directions work</h2>
 *
 * <p>A config slot holds no item: what it holds is a fluid the bus is asked to keep. It is therefore backed by
 * {@link EmptyContainer} — a {@code Container} that stores nothing — and its {@link ConfigSlot#mayPlace} is {@code false}, so
 * no vanilla path (drag, hotbar swap, shift-click, creative pick) can put an item into it. <b>That matters beyond tidiness:</b>
 * a slot backed by a real inventory would silently swallow the player's bucket, and this port has already been bitten once by a
 * GUI slot whose backing store did not exist (§275's {@code Slot 6 not in valid range}).</p>
 *
 * <p>The slot's <b>display</b> value travels server→client through {@link ContainerData} — 9 fluid registry ids and 9 amounts,
 * per {@code MenuFluidHatch}'s rule that a fluid's identity goes over the int channel as its registry id rather than through a
 * second sync path that could disagree. The slot's <b>write</b> travels client→server through
 * {@code PktMEFluidInputBusConfig}, whose server half calls {@link ConfigSlot#configureFrom}.</p>
 */
public class MenuMEFluidInputBus extends AbstractContainerMenu {

    /**
     * Panel size, from upstream's own numbers rather than from this port: {@code GuiMEFluidInputBus} sets
     * {@code ySize = 231} in its constructor (upstream U29) and {@code ContainerMEFluidInputBus.getHeight()} returns the same
     * 231 (upstream C36). The width is the vanilla default, which is what upstream's unused {@code setWidthHeight()} leaves in
     * place. Asserted server-side by {@code MachineSelfTest}: a wrong panel size is a value no automated check used to look at
     * (§91), and it is the one thing about a screen a dedicated server CAN check.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 231;

    /** The nine config slots, one per tank. */
    public static final int CONFIG_SLOTS = MEFluidBus.TANK_SLOT_AMOUNT;

    /** Upstream ContainerMEFluidInputBus exposes five AE2 upgrade slots at x=187, y=8+18*i. */
    public static final int UPGRADE_SLOTS = 5;
    public static final int UPGRADE_X = 187;
    public static final int UPGRADE_Y = 8;

    /** Geometry, straight from upstream's screen: the config row at (8 + 18i, 35). */
    private static final int CONFIG_X = 8;
    private static final int CONFIG_Y = 35;

    // -----------------------------------------------------------------------------------------------------------------
    // THE PLAYER INVENTORY'S ROWS, AND THE 65-PIXEL DEFECT THAT PUT THEM AT 84/142 (fixed 2026-10, §677).
    //
    // These numbers belong to THIS panel, and the panel is 231 tall -- so they are the vanilla offsets from the
    // BOTTOM: PANEL_HEIGHT - 82 for the first of the three rows, PANEL_HEIGHT - 24 for the hotbar, and 18 between
    // rows. That is what every other screen in this mod already reads (MenuMachineController, MenuFactoryController
    // and MenuUpgradeBus: 213 - 82/-24 = 131/189; MenuFluidHatch and MenuItemBus: 166 - 82/-24 = 84/142) -- and
    // 84/142 is that LAST pair, copied into a 231-tall screen. §192's shape, one panel size later.
    //
    // THE EVIDENCE IS THE ART, not the arithmetic: this screen draws the jar's own textures/gui/mefluidinputbus.png
    // at 176x231, and the wells in that image are at rows 149, 167, 185 and 207 (measured pixel by pixel: each well
    // is an 18x18 cell whose top border is a dark run one row above it, 17/18 pixels dark).
    //
    // WHY THE OLD NUMBERS SURVIVED A TEXTURE-BASED CHECK: at rows 84/102/120 the well scan reads 9/9 as well,
    // because the nine stored-tank bars sit on the SAME 18-pixel grid (upstream U41-U42: GuiFluidTank at 8 + 18i,
    // y 53, height 68, i.e. rows 53..120) and their dark separators land exactly on those x positions. The HOTBAR
    // row is what tells a bar from a well: 142 is bare panel fill (0 of 9), as is 84 once the well's own top border
    // is asked for (1/18 dark against 17/18).
    //
    // AND UPSTREAM'S NUMBERS ARE THESE SAME ONES: GuiMEFluidInputBus sets ySize = 231 (U29) and
    // ContainerMEFluidBus.getHeight() returns 231 (C36) -- the container binds the player's slots FROM that height,
    // which is why it overrides getHeight() at all. Upstream's inventory label is drawn at ySize - 96 + 3 = 138
    // (U70), the blank band between the ninth tank bar (ends at y=121) and the first inventory row: a label ABOVE
    // its rows, which 84/142 was not.
    //
    // Written as the subtraction rather than as two literals so the relation cannot drift from PANEL_HEIGHT, which
    // MachineSelfTest pins to 231 against this same texture ("panel sizes match the jar"). Declared PUBLIC, as
    // MenuMachineController/MenuFactoryController declare theirs: the suite reads these to check them against the art.
    // -----------------------------------------------------------------------------------------------------------------

    /** The player inventory's column, its first row's Y, and the hotbar's Y: panel-relative, from {@link #PANEL_HEIGHT}. */
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = PANEL_HEIGHT - 82;
    public static final int PLAYER_HOTBAR_Y = PANEL_HEIGHT - 24;

    /** ContainerData layout: config id/amount for each slot, followed by stored id/amount for each slot. */
    public static final int DATA_SIZE = CONFIG_SLOTS * 4;

    /** Sentinel for "this slot is configured to hold nothing". Zero is a valid registry id, so zero cannot mean absence. */
    public static final int FLUID_NONE = -1;

    /** The tank slot a config slot configures. One to one, as upstream's {@code GuiFluidSlot(configFluids, i, i, ...)}. */
    public static final int DATA_FLUID_ID = 0;
    public static final int DATA_AMOUNT = 1;
    public static final int STORED_DATA_OFFSET = CONFIG_SLOTS * 2;
    public static final int STORED_DATA_FLUID_ID = STORED_DATA_OFFSET;
    public static final int STORED_DATA_AMOUNT = STORED_DATA_OFFSET + 1;

    private final BlockPos busPos;
    private final ContainerData data;
    private final IUpgradeInventory upgrades;

    /** Client-side constructor, invoked from the menu type's factory. The tile is looked up lazily, per side. */
    public MenuMEFluidInputBus(int windowId, Inventory playerInventory, BlockPos busPos) {
        this(windowId, playerInventory, busPos, new SimpleContainerData(DATA_SIZE), clientUpgrades());
    }

    /** Both-sides constructor: the server passes a live view of the tile, the client a buffer the update packet fills. */
    public MenuMEFluidInputBus(int windowId, Inventory playerInventory, BlockPos busPos, ContainerData data) {
        this(windowId, playerInventory, busPos, data, clientUpgrades());
    }

    private MenuMEFluidInputBus(int windowId, Inventory playerInventory, BlockPos busPos,
                                ContainerData data, IUpgradeInventory upgrades) {
        super(RegistryMEMenus.ME_FLUID_INPUT_BUS.get(), windowId);
        this.busPos = busPos;
        this.data = data;
        this.upgrades = upgrades;

        // The nine config slots. EmptyContainer rather than an inventory: see the class javadoc.
        for (int tank = 0; tank < CONFIG_SLOTS; tank++) {
            addSlot(new ConfigSlot(this, new EmptyContainer(), tank, CONFIG_X + 18 * tank, CONFIG_Y));
        }
        addPlayerInventory(playerInventory);
        addUpgradeSlots(upgrades);
        // The slots must be added BEFORE the data slots, because a click resolves against the slot list.
        addDataSlots(new IntContainerData(data));
    }

    /**
     * Builds the server-side menu for a real bus, with a live data source.
     *
     * <p>The data source is extracted into {@link #dataFor} so the self-test can drive exactly the numbers the screen would
     * read, without a client connection — the convention {@code MenuFluidHatch.dataFor} established.</p>
     */
    public static MenuMEFluidInputBus forBus(int windowId, Inventory playerInventory, MEFluidInputBus bus) {
        return new MenuMEFluidInputBus(windowId, playerInventory, bus.getBlockPos(), dataFor(bus), bus.getUpgrades());
    }

    private static IUpgradeInventory clientUpgrades() {
        net.minecraft.world.item.Item machine = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("modularmachinery", "blockmefluidinputbus"));
        return UpgradeInventories.forMachine(machine, UPGRADE_SLOTS, () -> { });
    }

    private void addUpgradeSlots(IUpgradeInventory inventory) {
        IItemHandler handler = inventory.toItemHandler();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new UpgradeSlot(handler, inventory, i, UPGRADE_X, UPGRADE_Y + 18 * i));
        }
    }

    /** @return the five upgrade slots, after the unchanged config/player slot range */
    public int upgradeStartIndex() {
        return CONFIG_SLOTS + 36;
    }

    /** Capacity shown by the fluid bars, derived from the synchronized upgrade inventory. */
    public int capacityForDisplay() {
        int installed = upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
        long capacity = (long) MEFluidBus.TANK_DEFAULT_CAPACITY * (1L << Math.min(installed * 2, 30));
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /** @return a live view of a bus's configuration: one fluid registry id and one amount per slot */
    public static ContainerData dataFor(MEFluidInputBus bus) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                boolean stored = index >= STORED_DATA_OFFSET;
                int local = stored ? index - STORED_DATA_OFFSET : index;
                int tank = local / 2;
                if (tank < 0 || tank >= CONFIG_SLOTS) {
                    return 0;
                }
                FluidStack stack = stored ? bus.getFluidInSlot(tank) : bus.getConfigured(tank);
                if (local % 2 == DATA_FLUID_ID) {
                    // EMPTY must be distinguishable from a real fluid whose registry id is 0 -- hence the sentinel.
                    return stack.isEmpty()
                            ? FLUID_NONE : BuiltInRegistries.FLUID.getId(stack.getFluid());
                }
                return stack.getAmount();
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative, exactly like MenuFluidHatch's.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    private void addPlayerInventory(Inventory playerInventory) {
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

    public BlockPos getBusPos() {
        return busPos;
    }

    /**
     * @return whether {@code menu} is a ME fluid input bus menu looking at {@code pos}
     *
     * <p><b>Why this exists as a named method instead of an {@code instanceof} at the packet:</b> the packet must re-derive its
     * target from the world rather than trust the payload (this channel's rule, stated on
     * {@code ModularmachineryNetwork.PktSetParallelism}), and the target here is "whatever bus the player actually has open".
     * Naming that question in one place is also what removes the packet's need to name the menu TYPE — a reference that would
     * pull the menu type's registry through {@code RegistryMEMenus} into a class that loads whether or not AE2 is present.</p>
     */
    public static boolean isOpenFor(@Nullable net.minecraft.world.inventory.AbstractContainerMenu menu,
                                    BlockPos pos) {
        return menu instanceof MenuMEFluidInputBus busMenu && busMenu.getBusPos().equals(pos);
    }

    /** A positioned AE2 upgrade slot for the custom 1.20.1 menu. */
    public static final class UpgradeSlot extends SlotItemHandler {
        private final IUpgradeInventory upgrades;

        UpgradeSlot(IItemHandler handler, IUpgradeInventory upgrades, int index, int x, int y) {
            super(handler, index, x, y);
            this.upgrades = upgrades;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            return upgrades.getMaxInstalled(stack.getItem()) > upgrades.getInstalledUpgrades(stack.getItem());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** @return the fluid this slot is asked to keep, as the synced data reports it; EMPTY when nothing is configured */
    public FluidStack configuredFluidFor(int slot) {
        if (slot < 0 || slot >= CONFIG_SLOTS) {
            return FluidStack.EMPTY;
        }
        int id = data.get(slot * 2 + DATA_FLUID_ID);
        if (id == FLUID_NONE) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid, data.get(slot * 2 + DATA_AMOUNT));
    }

    /** @return the fluid currently held by a tank, as the menu's synchronized data reports it */
    public FluidStack storedFluidFor(int slot) {
        if (slot < 0 || slot >= CONFIG_SLOTS) {
            return FluidStack.EMPTY;
        }
        int base = STORED_DATA_OFFSET + slot * 2;
        int id = data.get(base);
        if (id == FLUID_NONE) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid, Math.max(0, data.get(base + 1)));
    }

    /**
     * @return this menu's config slot at {@code index}, or {@code null} when the index is not one of the nine
     *
     * <p><b>Why the packet goes through this rather than through its own index arithmetic:</b> the packet's check must be "the
     * player has THIS menu open and the index names a CONFIG slot", and the menu's own slot list is the only thing that knows
     * where its config slots are. A packet that recomputed {@code index < 9} would be a second copy of a layout rule — §629's
     * shape, where two readers of one rule disagreed.</p>
     */
    @Nullable
    public ConfigSlot configSlotAt(int index) {
        if (index < 0 || index >= CONFIG_SLOTS) {
            return null;
        }
        Slot slot = slots.get(index);
        return slot instanceof ConfigSlot config ? config : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>This is where a config slot's click is handled, and the reason is a 1.12.2 → 1.20.1 difference that is easy to
     * miss:</b> upstream's container received the click through AE2's own fluid-slot machinery, and in 1.20.1
     * {@code net.minecraft.world.inventory.Slot} has <b>no</b> click handler at all — {@code javap -p} on the mapped 1.20.1
     * {@code Slot} shows {@code mayPlace}/{@code mayPickup}/{@code getMaxStackSize}/{@code isActive}/… and no click method. The
     * click arrives at the MENU: {@code AbstractContainerMenu.clicked}, which is called on <b>both sides</b> (the client sends
     * {@code ServerboundContainerClickPacket}, the server runs the same method on the same indices).</p>
     *
     * <p>Both halves of the write therefore live on one path: the client sends {@code PktMEFluidInputBusConfig}, and the server's
     * own {@code clicked} call — arriving in the same tick — performs the change. <b>The server does not wait for the packet to
     * do it</b>, which is what makes the configuration a Minecraft interaction rather than a mod-private one; the packet's job
     * is to carry the intended fluid, and it is validated there.</p>
     *
     * <p>Every non-config slot falls through to vanilla, which is why this override names {@link ConfigSlot} rather than
     * reimplementing pickup/place/quick-move for the other 36.</p>
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        ConfigSlot config = configSlotAt(slotId);
        if (config != null && !player.level().isClientSide()) {
            if (!player.containerMenu.equals(this)) {
                // A click for a menu the player no longer has open: refusing here is the server's own copy of the check
                // PktMEFluidInputBusConfig.apply makes, and it is the only one of the two that runs for a real client.
                return;
            }
            config.configureFrom(player);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The config slots accept no items, so a shift-click never targets them: this moves items between the player's own rows,
     * which is all a ghost-slot screen can offer. {@code ConfigSlot.mayPlace} refuses the item anyway, but
     * {@code moveItemStackTo} would burn the whole stack probing each slot, so the range is stated once here.</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (configSlotAt(index) != null) {
            // A ghost slot holds no item; there is nothing to shift-click out of it.
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerMainStart = CONFIG_SLOTS;
        int playerHotbarStart = CONFIG_SLOTS + 27;
        int upgradeStart = upgradeStartIndex();
        if (index >= upgradeStart) {
            if (!moveItemStackTo(stack, playerMainStart, upgradeStart, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < playerHotbarStart) {
            if (!moveItemStackTo(stack, playerHotbarStart, upgradeStart, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, playerMainStart, playerHotbarStart, false)) {
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
        return player.level().getBlockEntity(busPos) instanceof MEFluidInputBus
                && player.distanceToSqr(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5) <= 64.0;
    }

    /**
     * A ghost slot showing one tank's configured fluid, and the place the configuration is actually written.
     *
     * <p>{@code getFluid()} reads the MENU, not the tile: the menu's {@link ContainerData} is the client's copy of the server's
     * configuration, and reaching into the block entity from the client would show the last chunk-sync instead of the value the
     * server just accepted — §629's "two readers of one rule" again.</p>
     */
    public static class ConfigSlot extends Slot {

        /**
         * The menu this slot belongs to.
         *
         * <p><b>Typed as the concrete menu rather than as {@link MenuView}, and that is deliberate:</b> the slot needs the BUS
         * POSITION to find the tile it configures, and a view that exposed only the display value would leave
         * {@link #configureFrom} with no target. A slot that must find its own tile would be the second place that decides what
         * this screen is looking at.</p>
         */
        private final MenuMEFluidInputBus menu;

        /** Which tank this slot configures. Carried explicitly: see {@link #getTank()}. */
        private final int tank;

        ConfigSlot(MenuMEFluidInputBus menu, Container container, int tank, int x, int y) {
            super(container, tank, x, y);
            this.menu = menu;
            this.tank = tank;
        }

        /**
         * @return the tank this slot configures
         *
         * <p><b>Not {@code Slot.getContainerSlot()}.</b> That accessor returns the index into the slot's backing
         * {@code Container} — and this slot's backing container is {@link EmptyContainer}, whose slot count is 0 because it
         * holds nothing. The tank number happens to be passed to {@code super} as well, so today the two agree; naming the
         * field is what keeps them from having to. A slot whose index came from a container that can be resized is exactly the
         * §275 defect ({@code Slot 6 not in valid range - [0,6)}).</p>
         */
        public int getTank() {
            return tank;
        }

        /** @return the fluid configured for this tank, as the synced data reports it, or EMPTY */
        public FluidStack getFluid() {
            return menu.configuredFluidFor(tank);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Never: the slot shows a fluid, it is not a place to put a bucket.
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 0;
        }

        /**
         * The single place a configuration is produced, shared by the tile-click path and the packet path.
         *
         * <h2>The gesture is a FLUID CONTAINER, and this rule is a choice rather than a port</h2>
         *
         * <p>Upstream's config inventory is filled by AE2's fluid-config container, whose rule is "the fluid the player's
         * carried item holds". This port's equivalent is Forge's own
         * {@link net.minecraftforge.fluids.FluidUtil#getFluidContained} — the same call {@code BlockFluidHatch} already uses to
         * hand a bucket to a hatch, so a player learns one gesture for the whole mod: <b>hold the fluid you mean and click its
         * slot</b>. A carried item that contains no fluid (including an empty hand) <b>clears</b> the slot, which is how a
         * configuration is removed and how rule 1 gets its "give it back" branch back.</p>
         *
         * <p><b>The fluid is re-derived from the player's own carried stack and never taken from the packet's value.</b> The
         * packet's decoded fluid is a claim about intent; the carried container is the evidence. A modified client that sent
         * "lava" while holding a water bucket therefore configures water — which is what clicking would have configured anyway —
         * so the two paths cannot disagree, and the packet has nothing to forge beyond the slot index it already names.</p>
         *
         * @return true when the write was accepted
         */
        public boolean configureFrom(Player player) {
            return configureFrom(player, net.minecraftforge.fluids.FluidUtil
                    .getFluidContained(player == null ? ItemStack.EMPTY : player.containerMenu.getCarried())
                    .orElse(FluidStack.EMPTY));
        }

        /**
         * The applied half, split out so the self-test can drive the write with an explicit fluid and a fake player — the
         * convention {@code PktSetParallelism.clampParallelism} established: <b>a rule that can only be exercised by a live
         * client is a rule nobody tests.</b>
         *
         * <p>Every check below is re-derived from the world, not from the caller: the target must still be the tile this menu
         * names, and the player must still be in range. Both are re-read because a click and a packet can each arrive between
         * two server ticks, after the player has walked away or broken the bus.</p>
         *
         * @return true when the write was accepted
         */
        public boolean configureFrom(Player player, FluidStack fluid) {
            if (player == null) {
                return false;
            }
            if (!(player.level().getBlockEntity(menu.getBusPos()) instanceof MEFluidInputBus bus)) {
                return false;   // broken or replaced while the screen was open
            }
            if (player.distanceToSqr(menu.getBusPos().getX() + 0.5, menu.getBusPos().getY() + 0.5,
                    menu.getBusPos().getZ() + 0.5) > 64.0) {
                return false;   // the menu's own stillValid runs once a tick; a click can arrive between ticks
            }
            bus.setConfiguredFluid(tank, fluid);
            return true;
        }
    }

    /**
     * The slice of this menu a config slot reads.
     *
     * <p><b>Declared rather than having {@link ConfigSlot} hold the menu, and the reason is a cycle:</b> a slot would then
     * reference the {@code AbstractContainerMenu} that owns it, so {@code slots.get(i)} would close a loop through the menu —
     * and the interface is what lets a ghost slot be constructed and asked about its display value without a menu at all.</p>
     *
     * <p>It names no AE2 type, and neither does the class that implements it: this is the boundary that keeps the menu's
     * signature out of the ME tile's hierarchy, which §478's guard depends on.</p>
     */
    public interface MenuView {
        /** @return the fluid configured for {@code tank}, or EMPTY */
        FluidStack configuredFluidFor(int tank);
    }

    /**
     * A {@code Container} that holds nothing.
     *
     * <p>A ghost slot needs a backing {@code Container} because vanilla's {@link Slot} requires one, but giving it a real
     * inventory would create a place for items to be lost. Every mutator is a no-op and {@code getMaxStackSize} is zero, so even
     * a path that bypassed {@link ConfigSlot#mayPlace} could store nothing.</p>
     */
    private static final class EmptyContainer implements Container {

        @Override
        public int getContainerSize() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            // NO-OP on purpose: see the class javadoc.
        }

        @Override
        public void setChanged() {
            // NO-OP: there is no state to save.
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            // NO-OP: nothing is ever stored.
        }
    }
}
