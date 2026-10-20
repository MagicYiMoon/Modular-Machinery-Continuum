/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.registry;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The menu types whose CONSTRUCTORS name an Applied Energistics 2 type (PLAN section 478).
 *
 * WHY THIS IS A SEPARATE REGISTRY FROM RegistryMenus: that one is loaded unconditionally, so a menu registered
 * there whose constructor names TileMEPatternProvider would put an AE2-adjacent type in a class that is always
 * loaded. That is the section 452 failure shape exactly -- it COMPILES CLEAN and kills a server without AE2 at
 * REGISTRY time with NoClassDefFoundError: appeng/api/networking/IInWorldGridNodeHost.
 *
 * So this follows RegistryMEBlockEntities: the register is subscribed in the mod constructor ONLY under
 * ModList.get().isLoaded("ae2"). THAT IS THE THIRD FIREWALL BOUNDARY IN THIS PROJECT, and the three are one rule:
 *
 *     METHOD level     MEBusTiles                    a block method body that touches AE2   (413, 452, 459)
 *     REGISTRY level   RegistryMEBlockEntities       a BE type naming AE2                   (404, 411)
 *     REGISTRY level   THIS                          a menu constructor naming AE2           (478)
 *
 * ANY PLACE IN AN UNCONDITIONALLY-LOADED CLASS THAT NAMES AN AE2 TYPE MUST MOVE HERE.
 */
public final class RegistryMEMenus {

    private RegistryMEMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Modularmachinery.MODID);

    /**
     * The ME fluid input bus's own screen (PLAN B3).
     *
     * <p><b>It lives here rather than in {@code RegistryMenus} for the same reason the pattern provider does:</b> its menu class
     * names {@code MEFluidInputBus}, whose hierarchy implements AE2's {@code IInWorldGridNodeHost}. Putting that field in the
     * unconditionally-loaded {@code RegistryMenus} would resolve the ME tile when that class links and kill a server without AE2
     * at REGISTRY time -- the section 452/478 failure shape -- which the guard below exists to prevent.</p>
     *
     * <p>It carries only a position, like the pattern provider: the nine config slots' positions come from the menu itself, so
     * nothing variable-length has to travel in the open packet.</p>
     */
    public static final RegistryObject<MenuType<hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus>>
            ME_FLUID_INPUT_BUS = MENUS.register("me_fluid_input_bus", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus(
                                    windowId, inventory, data.readBlockPos())));

    /**
     * The ME item input bus's own screen (PLAN section 686).
     *
     * <p><b>Why this is the item bus's problem at all, and what was wrong before it existed.</b> Upstream's
     * {@code BlockMEItemInputBus.onBlockActivated} (U49) opens {@code GuiType.ME_ITEM_INPUT_BUS}, and the port's
     * {@code BlockMEItemBus} shipped with no {@code use} at all and NO menu type anywhere — so
     * {@code modularmachinery:blockmeiteminputbus} could not be opened by right-click, and no check could have said
     * otherwise, because "a menu type is registered" and "a block opens a menu" are different facts. That is the gap
     * {@code verifyMEItemInputBusMenu} now measures.</p>
     *
     * <p><b>It lives in this AE2-guarded registry for the same reason the fluid bus's does:</b> its menu class names
     * {@code MEItemInputBus}, whose hierarchy implements AE2's {@code IInWorldGridNodeHost}, and this registry's
     * {@code register} is subscribed only when AE2 is loaded. <b>Note what the lambda does and does not load:</b> the
     * {@code MenuType} is created during the register event, and the factory the lambda builds is invoked only when a
     * menu is actually created — so registering this type does NOT link the menu class, which is why the self-test can
     * ask {@code ME_ITEM_INPUT_BUS.isPresent()} on a server without AE2 and see the guard hold.</p>
     *
     * <p>It carries only a position: the 4x4 config grid's geometry and length are constants of the menu itself, so
     * nothing variable-length has to travel in the open packet — unlike the ordinary item bus, whose tier varies per
     * block and must be sent.</p>
     */
    public static final RegistryObject<MenuType<hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus>>
            ME_ITEM_INPUT_BUS = MENUS.register("me_item_input_bus", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus(
                                    windowId, inventory, data.readBlockPos())));

    /**
     * The AE2 pattern provider own screen.
     *
     * <p>Carries only a position, like the energy and fluid hatches: the pattern inventory lives on the server and
     * reaches the client through the normal menu synchronisation, so nothing variable-length is needed in the open
     * packet.</p>
     */
    public static final RegistryObject<MenuType<hellfirepvp.modularmachinery.common.menu.MenuMEPatternProvider>>
            PATTERN_PROVIDER = MENUS.register("me_pattern_provider", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new hellfirepvp.modularmachinery.common.menu.MenuMEPatternProvider(
                                    windowId, inventory, data.readBlockPos())));

    /**
     * The ME item OUTPUT bus's own screen (AUDIT_COMPONENTS row 11).
     *
     * <p><b>Why this is a new entry rather than a rename of something:</b> upstream has TWO item-bus screens
     * ({@code GuiMEItemInputBus} and {@code GuiMEItemOutputBus}), each with its own container and its own block method
     * opening it, and the port had only the input one — so {@code modularmachinery:blockmeitemoutputbus} could not be
     * opened by right-click at all, and the direction with 36 slots had no way to be looked at or configured.</p>
     *
     * <p><b>It lives in this AE2-guarded registry for the same reason the two above do:</b> its menu class names
     * {@code MEItemOutputBus}, whose hierarchy implements AE2's {@code IInWorldGridNodeHost}, and this registry's
     * {@code register} is subscribed only when AE2 is loaded. <b>What the lambda does and does not load is worth the
     * note the item input bus's entry already carries:</b> the {@code MenuType} is created during the register event and
     * the factory it builds is invoked only when a menu is actually created, so registering this type does NOT link the
     * menu class.</p>
     *
     * <p>It carries only a position: the 9x4 grid's geometry and length are constants of the menu itself, so nothing
     * variable-length has to travel in the open packet — unlike the ordinary item bus, whose tier varies per block.</p>
     */
    public static final RegistryObject<MenuType<hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus>>
            ME_ITEM_OUTPUT_BUS = MENUS.register("me_item_output_bus", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus(
                                    windowId, inventory, data.readBlockPos())));

    /** The ME fluid OUTPUT bus's read-only tank screen. */
    public static final RegistryObject<MenuType<hellfirepvp.modularmachinery.common.menu.MenuMEFluidOutputBus>>
            ME_FLUID_OUTPUT_BUS = MENUS.register("me_fluid_output_bus", () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            new hellfirepvp.modularmachinery.common.menu.MenuMEFluidOutputBus(
                                    windowId, inventory, data.readBlockPos())));

    /** Subscribed by the mod constructor ONLY when AE2 is present. @see RegistryMEBlockEntities for the same shape */
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        MENUS.register(bus);
    }
}
