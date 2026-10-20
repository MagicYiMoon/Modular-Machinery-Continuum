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
package hellfirepvp.modularmachinery.common.registry;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch;
import hellfirepvp.modularmachinery.common.menu.MenuFluidHatch;
import hellfirepvp.modularmachinery.common.menu.MenuItemBus;
import hellfirepvp.modularmachinery.common.menu.MenuFactoryController;
import hellfirepvp.modularmachinery.common.menu.MenuMachineController;
import hellfirepvp.modularmachinery.common.menu.MenuParallelController;
import hellfirepvp.modularmachinery.common.menu.MenuGroupInputConfig;
import hellfirepvp.modularmachinery.common.menu.MenuSmartInterface;
import hellfirepvp.modularmachinery.common.menu.MenuUpgradeBus;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Menu (container) type registry.
 *
 * <p><b>1.12.2 → 1.20.1.</b> This is the single largest API change in the port. Upstream opened a GUI by
 * calling {@code player.openGui(mod, id, world, x, y, z)} with a numeric id and a per-mod
 * {@code IGuiHandler} that constructed both the server-side {@code Container} and the client-side
 * {@code GuiScreen} from the same id.
 *
 * <p>On 1.20.1 those two halves are <b>separate registrations</b>:
 * <ul>
 *   <li>the <b>menu</b> is a registry object ({@link MenuType}) that the server opens and the client
 *       instantiates on receipt — it is the object this class registers;</li>
 *   <li>the <b>screen</b> is <i>not</i> registered at all. The client picks it from
 *       {@code MenuScreens.register(menuType, Screen::new)} during client setup.</li>
 * </ul>
 *
 * <p>That asymmetry is why {@code MenuMachineController} carries a {@code BlockPos}: the menu has to be
 * reconstructible on the client from data the server sends, and the block position is what identifies
 * which machine's controller is being viewed.
 *
 * <p>{@link IForgeMenuType#create} is used rather than a bare {@code MenuType} supplier because the
 * client-side factory needs the {@code FriendlyByteBuf} the server wrote — that is the 1.20.1
 * replacement for the {@code (x, y, z)} arguments {@code openGui} used to pass.
 */
public final class RegistryMenus {

    private RegistryMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Modularmachinery.MODID);

    /**
     * The machine controller's GUI.
     *
     * <p>Carries a {@code BlockPos} through the open packet so the client can find the same controller
     * block entity the server used. Without it the client would have to guess, and a GUI opened on a
     * machine whose controller had since been broken would show another machine's state.
     */
    public static final RegistryObject<MenuType<MenuMachineController>> MACHINE_CONTROLLER =
            MENUS.register("machine_controller", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuMachineController(windowId, inventory, data.readBlockPos())));

    /**
     * An item bus GUI.
     *
     * <p>Carries both a {@code BlockPos} and the bus's <b>slot count</b>. The count is not derivable on
     * the client — the bus's size is a blockstate property, and the chunk may not be loaded when the open
     * packet arrives — and it must match on both sides because {@code AbstractContainerMenu} matches
     * slots by index. A client that guessed wrong would shift every stack.
     */
    public static final RegistryObject<MenuType<MenuItemBus>> ITEM_BUS =
            MENUS.register("item_bus", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuItemBus(windowId, inventory, data.readBlockPos(), data.readVarInt())));

    /**
     * An energy hatch GUI.
     *
     * <p>Carries only a position: unlike the item bus there is no variable-length state the client needs
     * before it can build its half, because the hatch has no slots. Its contents arrive through the
     * normal {@code ContainerData} sync.
     */
    public static final RegistryObject<MenuType<MenuEnergyHatch>> ENERGY_HATCH =
            MENUS.register("energy_hatch", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuEnergyHatch(windowId, inventory, data.readBlockPos())));

    /** A fluid hatch GUI. Position only, for the same reason as the energy hatch. */
    public static final RegistryObject<MenuType<MenuFluidHatch>> FLUID_HATCH =
            MENUS.register("fluid_hatch", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuFluidHatch(windowId, inventory, data.readBlockPos())));

    /**
     * The parallel controller GUI.
     *
     * <p>Carries the position AND the setting (defect A). Position-only was wrong for the same reason it would be
     * wrong on the item bus: the client cannot derive the value (the tile may be in an unloaded chunk, and this
     * JVM has no world at all), and the {@code ContainerData} sync arrives on the first server tick AFTER the
     * window packet — so the screen existed, read its still-zero client store, and showed 0 for a controller
     * that held a real number. The two values travel through
     * {@code MenuParallelController.writeOpenData}, the same method the check decodes.</p>
     */
    public static final RegistryObject<MenuType<MenuParallelController>> PARALLEL_CONTROLLER =
            MENUS.register("parallel_controller", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuParallelController(windowId, inventory, data.readBlockPos(),
                                    data.readVarInt(), data.readVarInt())));

    /** The upgrade bus GUI. The TIER is sent, since it carries both the slot count and the 3-wide grid. */
    public static final RegistryObject<MenuType<MenuUpgradeBus>> UPGRADE_BUS =
            MENUS.register("upgrade_bus", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuUpgradeBus(windowId, inventory, data.readBlockPos(),
                                    data.readVarInt())));

    /** The factory controller GUI. Its panel is 280 wide because the blueprint slot sits at x=255. */
    public static final RegistryObject<MenuType<MenuFactoryController>> FACTORY_CONTROLLER =
            MENUS.register("factory_controller", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuFactoryController(windowId, inventory, data.readBlockPos())));


    /** The smart interface GUI. Carries no inventory slots of its own -- see {@link MenuSmartInterface}. */
    /**
     * The group-input configuration GUI (§159).
     *
     * <p>Carries no inventory slots of its own -- see {@link MenuGroupInputConfig}. Its <b>screen</b> must be
     * registered in {@code ClientSetup} as well: a menu type without a screen binding is a crash the first time
     * a player opens it, and this port's own note at {@code ClientSetup} records that nothing enforces the two
     * halves, because it has already been bitten by it once.
     */
    public static final RegistryObject<MenuType<MenuGroupInputConfig>> GROUP_INPUT_CONFIG =
            MENUS.register("group_input_config", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuGroupInputConfig(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<MenuSmartInterface>> SMART_INTERFACE =

            MENUS.register("smart_interface", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new MenuSmartInterface(windowId, inventory, data.readBlockPos())));

    /** Pack-author recipe editor opened by {@code /modularmachinery gui}. */
    public static final RegistryObject<MenuType<MenuMachineEditor>> MACHINE_EDITOR =
            MENUS.register("machine_editor", () -> IForgeMenuType.create(MenuMachineEditor::new));

}
